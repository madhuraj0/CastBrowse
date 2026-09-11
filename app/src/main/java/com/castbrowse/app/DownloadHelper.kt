package com.castbrowse.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File

data class DownloadItem(
    val id: Long,
    val title: String,
    val url: String,
    val status: Int, // DownloadManager.STATUS_*
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localUri: String?
) {
    val progress: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}

object DownloadHelper {
    private const val TAG = "DownloadHelper"

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
    }

    fun enqueueDownload(
        context: Context,
        url: String,
        suggestedTitle: String = "",
        headers: Map<String, String>? = null
    ): Long {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val rawFilename = if (suggestedTitle.isNotBlank()) {
                suggestedTitle
            } else {
                MediaExtractorClient.extractFilenameFromUrl(url)
            }
            
            // Clean filename and ensure extension
            val safeName = rawFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val filename = if (!safeName.contains(".")) "$safeName.mp4" else safeName

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("CastBrowse Media Download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CastBrowse/$filename")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            // Pass authentication and anti-hotlink headers if available
            headers?.forEach { (key, value) ->
                try {
                    request.addRequestHeader(key, value)
                } catch (e: Exception) {}
            }

            val downloadId = dm.enqueue(request)
            Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            Toast.makeText(context, "Failed to start download: ${e.message}", Toast.LENGTH_LONG).show()
            -1L
        }
    }

    fun getDownloads(context: Context): List<DownloadItem> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val list = mutableListOf<DownloadItem>()

        try {
            dm.query(query)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Media" else "Media"
                    val url = if (uriCol >= 0) cursor.getString(uriCol) ?: "" else ""
                    val status = if (statusCol >= 0) cursor.getInt(statusCol) else DownloadManager.STATUS_FAILED
                    val bytesDownloaded = if (downloadedCol >= 0) cursor.getLong(downloadedCol) else 0L
                    val totalBytes = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                    val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null

                    list.add(
                        DownloadItem(
                            id = id,
                            title = title,
                            url = url,
                            status = status,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            localUri = localUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying downloads", e)
        }

        return list.reversed()
    }

    fun removeDownload(context: Context, id: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(id)
            Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove download", e)
        }
    }

    fun openDownloadedFile(context: Context, item: DownloadItem) {
        try {
            val uriStr = item.localUri ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriStr), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDownloadedFile(context: Context, item: DownloadItem) {
        try {
            val uriStr = item.localUri ?: return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDownloadsFolder(context: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open downloads folder", Toast.LENGTH_SHORT).show()
        }
    }
}
