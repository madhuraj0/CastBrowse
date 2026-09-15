package com.castbrowse.app

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceAudioItem(
    val uri: Uri,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val durationMs: Long = 0L,
    val size: Long = 0L
)

data class DevicePhotoItem(
    val uri: Uri,
    val title: String,
    val size: Long = 0L
)

object MediaHubManager {
    private const val TAG = "MediaHubManager"

    // LRU Cache for photo thumbnails to keep scrolling fast and light
    private val thumbnailCache = object : LruCache<String, ImageBitmap>(80) {}

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val sec = (ms / 1000) % 60
        val min = (ms / (1000 * 60)) % 60
        val hrs = ms / (1000 * 60 * 60)
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, min, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    suspend fun getAudioItemFromUri(context: Context, uri: Uri): DeviceAudioItem = withContext(Dispatchers.IO) {
        var title = "Audio Track"
        var size = 0L
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var durationMs = 0L

        // 1. Check OpenableColumns
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) title = cursor.getString(nameIdx) ?: title
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            title = uri.lastPathSegment ?: "Audio Track"
        }

        // 2. Extract ID3 tags using MediaMetadataRetriever
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            val tagTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val tagArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val tagAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val tagDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!tagTitle.isNullOrBlank()) title = tagTitle
            if (!tagArtist.isNullOrBlank()) artist = tagArtist
            if (!tagAlbum.isNullOrBlank()) album = tagAlbum
            if (!tagDuration.isNullOrBlank()) durationMs = tagDuration.toLongOrNull() ?: 0L
            mmr.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read audio metadata for $uri: ${e.message}")
        }

        DeviceAudioItem(
            uri = uri,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            size = size
        )
    }

    suspend fun getPhotoItemFromUri(context: Context, uri: Uri): DevicePhotoItem = withContext(Dispatchers.IO) {
        var title = "Photo"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) title = cursor.getString(nameIdx) ?: title
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            title = uri.lastPathSegment ?: "Photo"
        }
        DevicePhotoItem(uri = uri, title = title, size = size)
    }

    suspend fun loadPhotosFromFolder(context: Context, treeUri: Uri): List<DevicePhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<DevicePhotoItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    if (mime.startsWith("image/")) {
                        val fileDocId = cursor.getString(idCol)
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Photo" else "Photo"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                        photos.add(DevicePhotoItem(uri = fileUri, title = name, size = size))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading photos from folder $treeUri: ${e.message}", e)
        }
        photos
    }

    suspend fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        thumbnailCache.get(key)?.let { return@withContext it }

        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                } catch (e: Exception) {
                    decodeSampledBitmapFromUri(context.contentResolver, uri, 160, 160)
                }
            } else {
                decodeSampledBitmapFromUri(context.contentResolver, uri, 160, 160)
            }

            if (bitmap != null) {
                val imageBitmap = bitmap.asImageBitmap()
                thumbnailCache.put(key, imageBitmap)
                imageBitmap
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromUri(cr: ContentResolver, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
