package com.castbrowse.app

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object SubtitleManager {

    private const val TAG = "SubtitleManager"

    @Volatile
    var rawSubtitleText: String? = null
        private set

    @Volatile
    var activeSubtitleName: String? = null
        private set

    /**
     * Loads a subtitle file (.srt or .vtt) from an Android content Uri,
     * normalizes it to standard WebVTT, and mounts it into LocalMediaProxy.
     */
    fun loadSubtitle(context: Context, uri: Uri, offsetMs: Int = 0): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return false
            val text = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }

            val fileName = getFileName(context, uri) ?: "subtitles.vtt"
            activeSubtitleName = fileName
            rawSubtitleText = text

            val vtt = convertToVtt(text, offsetMs)
            LocalMediaProxy.activeSubtitleContent = vtt
            val localIp = NetworkDiagnostics.getHotspotIp() ?: LocalMediaProxy.getLocalIpAddress()
            val subUrl = "http://$localIp:${LocalMediaProxy.proxyPort}/subtitles.vtt"
            CastSessionManager.activeSubtitleUrl = subUrl
            CastSessionManager.activeSubtitleName = fileName
            WebReceiverController.setSubtitle(subUrl, offsetMs)
            Log.d(TAG, "Subtitles mounted: $fileName at $subUrl (offset: ${offsetMs}ms)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading subtitles from $uri", e)
            false
        }
    }

    /**
     * Re-applies a new sync offset (+/- ms) to the loaded subtitles.
     */
    fun updateOffset(offsetMs: Int) {
        val raw = rawSubtitleText ?: return
        val vtt = convertToVtt(raw, offsetMs)
        LocalMediaProxy.activeSubtitleContent = vtt
        WebReceiverController.setSubtitle(CastSessionManager.activeSubtitleUrl, offsetMs)
    }

    /**
     * Clears any active external subtitle.
     */
    fun clearSubtitles() {
        rawSubtitleText = null
        activeSubtitleName = null
        LocalMediaProxy.activeSubtitleContent = null
        CastSessionManager.activeSubtitleUrl = null
        CastSessionManager.activeSubtitleName = null
        WebReceiverController.setSubtitle(null, 0)
    }

    /**
     * Converts raw SRT or VTT content to standard WebVTT format,
     * applying the specified time offset in milliseconds.
     */
    fun convertToVtt(content: String, offsetMs: Int = 0): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")

        val lines = content.replace("\r\n", "\n").replace("\r", "\n").lines()
        val timeRegex = Regex("(\\d{1,2}:\\d{2}:\\d{2}[,\\.]\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,\\.]\\d{3})")

        for (line in lines) {
            val match = timeRegex.find(line)
            if (match != null) {
                val startStr = match.groupValues[1]
                val endStr = match.groupValues[2]

                val startMs = parseTimestampToMs(startStr) + offsetMs
                val endMs = parseTimestampToMs(endStr) + offsetMs

                val adjStart = formatMsToVtt(maxOf(0, startMs))
                val adjEnd = formatMsToVtt(maxOf(0, endMs))

                sb.append(line.replace(match.value, "$adjStart --> $adjEnd")).append("\n")
            } else if (!line.startsWith("WEBVTT", ignoreCase = true)) {
                sb.append(line).append("\n")
            }
        }
        return sb.toString()
    }

    private fun parseTimestampToMs(ts: String): Long {
        return try {
            val parts = ts.replace(',', '.').split(":")
            val hours = parts[0].toLong()
            val mins = parts[1].toLong()
            val secParts = parts[2].split(".")
            val secs = secParts[0].toLong()
            val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            (hours * 3600 + mins * 60 + secs) * 1000 + millis
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatMsToVtt(ms: Long): String {
        val totalSecs = ms / 1000
        val millis = ms % 1000
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d:%02d.%03d", hrs, mins, secs, millis)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {}
        }
        return uri.path?.substringAfterLast('/')
    }
}
