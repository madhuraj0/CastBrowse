package com.castbrowse.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
    val size: Long = 0L,
    val albumId: Long = -1L,
    val folderName: String = "",
    val path: String = ""
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
    // LRU Cache for music album art
    private val albumArtCache = object : LruCache<String, ImageBitmap>(80) {}

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

    suspend fun loadAlbumArt(context: Context, item: DeviceAudioItem): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = item.uri.toString()
        albumArtCache.get(key)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Try MediaStore album art URI if albumId > 0
        if (item.albumId > 0) {
            try {
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    item.albumId
                )
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }

        // 2. Try MediaMetadataRetriever embedded picture from file URI
        if (bitmap == null) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, item.uri)
                val picBytes = mmr.embeddedPicture
                if (picBytes != null && picBytes.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size, options)
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false
                    bitmap = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size, options)
                }
                mmr.release()
            } catch (_: Exception) {}
        }

        // 3. On Android 10+ (Q+), try contentResolver.loadThumbnail
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = context.contentResolver.loadThumbnail(item.uri, Size(200, 200), null)
            } catch (_: Exception) {}
        }

        val resolvedBmp = bitmap
        if (resolvedBmp != null) {
            val imageBitmap = resolvedBmp.asImageBitmap()
            albumArtCache.put(key, imageBitmap)
            imageBitmap
        } else {
            null
        }
    }

    private const val PREFS_SCAN_CRITERIA = "audio_scan_criteria"
    const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
    const val KEY_INCLUDED_FOLDERS = "included_folders"
    const val KEY_MIN_DURATION_SEC = "min_duration_sec"

    val DEFAULT_EXCLUDED_FOLDERS = setOf(
        "Ringtones",
        "Notifications",
        "Alarms",
        "Voice Notes",
        "WhatsApp Audio",
        "WhatsApp Voice Notes",
        "Call Recordings",
        "Recordings"
    )

    fun getExcludedFolders(context: Context): Set<String> {
        val sp = context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_EXCLUDED_FOLDERS, null) ?: DEFAULT_EXCLUDED_FOLDERS
    }

    fun setExcludedFolders(context: Context, folders: Set<String>) {
        context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_EXCLUDED_FOLDERS, folders).apply()
    }

    fun addExcludedFolder(context: Context, folder: String) {
        val current = getExcludedFolders(context).toMutableSet()
        current.add(folder)
        setExcludedFolders(context, current)
    }

    fun removeExcludedFolder(context: Context, folder: String) {
        val current = getExcludedFolders(context).toMutableSet()
        current.remove(folder)
        setExcludedFolders(context, current)
    }

    fun getIncludedFolders(context: Context): Set<String> {
        val sp = context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_INCLUDED_FOLDERS, emptySet()) ?: emptySet()
    }

    fun setIncludedFolders(context: Context, folders: Set<String>) {
        context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_INCLUDED_FOLDERS, folders).apply()
    }

    fun getMinDurationSec(context: Context): Int {
        return context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
            .getInt(KEY_MIN_DURATION_SEC, 30)
    }

    fun setMinDurationSec(context: Context, sec: Int) {
        context.getSharedPreferences(PREFS_SCAN_CRITERIA, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MIN_DURATION_SEC, sec).apply()
    }

    suspend fun discoverAudioFolders(context: Context): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val folderCounts = mutableMapOf<String, Int>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    if (path.isNotBlank()) {
                        val folder = try { java.io.File(path).parentFile?.name ?: "" } catch (_: Exception) { "" }
                        if (folder.isNotBlank()) {
                            folderCounts[folder] = (folderCounts[folder] ?: 0) + 1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering audio folders: ${e.message}", e)
        }
        folderCounts.toList().sortedByDescending { it.second }
    }

    suspend fun scanDeviceAudio(context: Context, applyCriteria: Boolean = true): List<DeviceAudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<DeviceAudioItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val excludedFolders = if (applyCriteria) getExcludedFolders(context) else emptySet()
        val includedFolders = if (applyCriteria) getIncludedFolders(context) else emptySet()
        val minDurationMs = if (applyCriteria) getMinDurationSec(context) * 1000L else 0L

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                    val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                    val folderName = if (path.isNotBlank()) {
                        try {
                            java.io.File(path).parentFile?.name ?: ""
                        } catch (_: Exception) { "" }
                    } else ""

                    // Apply criteria filtering
                    if (applyCriteria) {
                        if (minDurationMs > 0 && duration > 0 && duration < minDurationMs) {
                            continue
                        }
                        if (includedFolders.isNotEmpty()) {
                            val matchesInc = includedFolders.any { inc ->
                                folderName.equals(inc, ignoreCase = true) || path.contains("/$inc/", ignoreCase = true)
                            }
                            if (!matchesInc) continue
                        }
                        if (excludedFolders.isNotEmpty()) {
                            val matchesExc = excludedFolders.any { exc ->
                                folderName.equals(exc, ignoreCase = true) || path.contains("/$exc/", ignoreCase = true)
                            }
                            if (matchesExc) continue
                        }
                    }

                    audioList.add(
                        DeviceAudioItem(
                            uri = contentUri,
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            durationMs = duration,
                            size = size,
                            albumId = albumId,
                            folderName = folderName,
                            path = path
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore audio: ${e.message}", e)
        }
        audioList
    }

    suspend fun loadAudiosFromFolder(context: Context, treeUri: Uri): List<DeviceAudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<DeviceAudioItem>()
        try {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: Exception) {}

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
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Audio" else "Audio"
                    val isAudio = mime.startsWith("audio/") ||
                        name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".m4a", ignoreCase = true) ||
                        name.endsWith(".flac", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true) ||
                        name.endsWith(".aac", ignoreCase = true) ||
                        name.endsWith(".ogg", ignoreCase = true)

                    if (isAudio) {
                        val fileDocId = cursor.getString(idCol)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                        val audioItem = getAudioItemFromUri(context, fileUri)
                        audioList.add(audioItem)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning audio from folder: ${e.message}", e)
        }
        audioList
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
