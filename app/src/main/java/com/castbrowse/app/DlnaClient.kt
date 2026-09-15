package com.castbrowse.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object DlnaClient {

    private const val TAG = "DlnaClient"
    private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private var pollJob: Job? = null
    private var activeControlUrl: String? = null
    private var activeRenderingUrl: String? = null

    suspend fun play(
        controlUrl: String,
        renderingControlUrl: String?,
        mediaUrl: String,
        title: String,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopPolling()
            activeControlUrl = controlUrl
            activeRenderingUrl = renderingControlUrl

            Log.d(TAG, "DLNA: Setting AVTransportURI to $mediaUrl (title: $title)")
            val setUriSuccess = setAVTransportUri(controlUrl, mediaUrl, title)
            if (!setUriSuccess) {
                throw Exception("Failed to set AVTransport URI on DLNA receiver")
            }

            Log.d(TAG, "DLNA: Sending Play command")
            val playSuccess = sendSoapAction(
                url = controlUrl,
                serviceType = AV_TRANSPORT_SERVICE,
                actionName = "Play",
                args = mapOf("InstanceID" to "0", "Speed" to "1")
            )
            if (!playSuccess) {
                throw Exception("Failed to execute Play action on DLNA receiver")
            }

            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = 0.0

            startPolling(controlUrl, onDisconnected)
        }
    }

    suspend fun pause(controlUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = controlUrl ?: activeControlUrl ?: return@withContext false
        val ok = sendSoapAction(
            url = targetUrl,
            serviceType = AV_TRANSPORT_SERVICE,
            actionName = "Pause",
            args = mapOf("InstanceID" to "0")
        )
        if (ok) {
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.playbackState = 2
        }
        ok
    }

    suspend fun resume(controlUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = controlUrl ?: activeControlUrl ?: return@withContext false
        val ok = sendSoapAction(
            url = targetUrl,
            serviceType = AV_TRANSPORT_SERVICE,
            actionName = "Play",
            args = mapOf("InstanceID" to "0", "Speed" to "1")
        )
        if (ok) {
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
        }
        ok
    }

    suspend fun stop(controlUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = controlUrl ?: activeControlUrl ?: return@withContext false
        stopPolling()
        val ok = sendSoapAction(
            url = targetUrl,
            serviceType = AV_TRANSPORT_SERVICE,
            actionName = "Stop",
            args = mapOf("InstanceID" to "0")
        )
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        CastSessionManager.playbackPositionSeconds = 0.0
        activeControlUrl = null
        activeRenderingUrl = null
        ok
    }

    suspend fun seek(seconds: Double, controlUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = controlUrl ?: activeControlUrl ?: return@withContext false
        val targetTime = formatTime(seconds.toLong())
        Log.d(TAG, "DLNA: Seeking to $targetTime")
        val ok = sendSoapAction(
            url = targetUrl,
            serviceType = AV_TRANSPORT_SERVICE,
            actionName = "Seek",
            args = mapOf(
                "InstanceID" to "0",
                "Unit" to "REL_TIME",
                "Target" to targetTime
            )
        )
        if (ok) {
            CastSessionManager.playbackPositionSeconds = seconds
        }
        ok
    }

    suspend fun setVolume(volume: Float, renderingControlUrl: String? = activeRenderingUrl): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = renderingControlUrl ?: activeRenderingUrl ?: return@withContext false
        val level = (volume.coerceIn(0f, 1f) * 100).roundToInt()
        Log.d(TAG, "DLNA: Setting volume to $level")
        val ok = sendSoapAction(
            url = targetUrl,
            serviceType = RENDERING_CONTROL_SERVICE,
            actionName = "SetVolume",
            args = mapOf(
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredVolume" to level.toString()
            )
        )
        if (ok) {
            CastSessionManager.volume = volume
        }
        ok
    }

    private suspend fun setAVTransportUri(controlUrl: String, mediaUrl: String, title: String): Boolean {
        val mimeType = when {
            mediaUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            mediaUrl.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
            mediaUrl.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            mediaUrl.contains(".m4a", ignoreCase = true) -> "audio/mp4"
            mediaUrl.contains(".webm", ignoreCase = true) -> "video/webm"
            mediaUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            else -> "video/mp4"
        }
        val didlMetadata = buildDidlMetadata(mediaUrl, title, mimeType)
        return sendSoapAction(
            url = controlUrl,
            serviceType = AV_TRANSPORT_SERVICE,
            actionName = "SetAVTransportURI",
            args = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to didlMetadata
            )
        )
    }

    private fun buildDidlMetadata(url: String, title: String, mimeType: String): String {
        val escapedTitle = escapeXml(title)
        val escapedUrl = escapeXml(url)
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="0" parentID="-1" restricted="1">
                <dc:title>$escapedTitle</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res>
              </item>
            </DIDL-Lite>
        """.trimIndent().trim()
    }

    private fun sendSoapAction(
        url: String,
        serviceType: String,
        actionName: String,
        args: Map<String, String>
    ): Boolean {
        return try {
            val argsXml = buildString {
                for ((key, value) in args) {
                    append("<$key>${escapeXml(value)}</$key>")
                }
            }
            val soapBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:$actionName xmlns:u="$serviceType">
                      $argsXml
                    </u:$actionName>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .addHeader("SOAPAction", "\"$serviceType#$actionName\"")
                .post(soapBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    Log.w(TAG, "SOAP action $actionName failed with code ${response.code}: ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SOAP action $actionName to $url", e)
            false
        }
    }

    private fun startPolling(controlUrl: String, onDisconnected: (() -> Unit)?) {
        stopPolling()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            var consecutiveFailures = 0
            while (CastSessionManager.isMediaPlaying) {
                delay(1000)
                val posInfo = getPositionInfo(controlUrl)
                if (posInfo != null) {
                    consecutiveFailures = 0
                    CastSessionManager.playbackPositionSeconds = posInfo.first
                    if (posInfo.second > 0) {
                        CastSessionManager.mediaDurationSeconds = posInfo.second
                    }
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures > 10) {
                        Log.w(TAG, "DLNA: Lost connection to receiver")
                        stopPolling()
                        onDisconnected?.invoke()
                        break
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun getPositionInfo(controlUrl: String): Pair<Double, Double>? {
        return try {
            val soapBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:GetPositionInfo xmlns:u="$AV_TRANSPORT_SERVICE">
                      <InstanceID>0</InstanceID>
                    </u:GetPositionInfo>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .addHeader("SOAPAction", "\"$AV_TRANSPORT_SERVICE#GetPositionInfo\"")
                .post(soapBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val relTimeStr = extractXmlTag(body, "RelTime")
                val durationStr = extractXmlTag(body, "TrackDuration")
                val relTime = relTimeStr?.let { parseTime(it) } ?: 0.0
                val duration = durationStr?.let { parseTime(it) } ?: 0.0
                Pair(relTime, duration)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = xml.indexOf(startTag)
        if (start == -1) return null
        val end = xml.indexOf(endTag, start + startTag.length)
        if (end == -1) return null
        return xml.substring(start + startTag.length, end).trim()
    }

    private fun parseTime(timeStr: String): Double {
        return try {
            val parts = timeStr.split(":")
            when (parts.size) {
                3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                else -> timeStr.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun formatTime(seconds: Long): String {
        val s = (seconds % 60).coerceAtLeast(0)
        val m = ((seconds / 60) % 60).coerceAtLeast(0)
        val h = (seconds / 3600).coerceAtLeast(0)
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
