package com.castbrowse.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RokuClient {

    private const val TAG = "RokuClient"
    const val ROKU_DEFAULT_PORT = 8060
    private const val PLAYONROKU_APP_ID = "15985"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var activeHost: String? = null
    private var activePort: Int = ROKU_DEFAULT_PORT

    suspend fun play(
        ipAddress: String,
        url: String,
        title: String,
        port: Int = ROKU_DEFAULT_PORT,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            activeHost = ipAddress
            activePort = if (port > 0) port else ROKU_DEFAULT_PORT

            val format = if (url.contains(".m3u8", ignoreCase = true)) "hls" else "mp4"
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")

            // PlayOnRoku ECP Launch URL
            val launchUrl = "http://$ipAddress:$activePort/launch/$PLAYONROKU_APP_ID?u=$encodedUrl&t=$encodedTitle&videoFormat=$format"
            Log.d(TAG, "Roku: Launching stream via ECP: $launchUrl")

            val request = Request.Builder()
                .url(launchUrl)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful && response.code !in 200..299) {
                // Try input endpoint as fallback if app is already active
                val inputUrl = "http://$ipAddress:$activePort/input/$PLAYONROKU_APP_ID?u=$encodedUrl&t=$encodedTitle&videoFormat=$format"
                val inputReq = Request.Builder()
                    .url(inputUrl)
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                val inputResp = httpClient.newCall(inputReq).execute()
                if (!inputResp.isSuccessful && inputResp.code !in 200..299) {
                    throw Exception("Roku playback failed with HTTP ${response.code}: ${response.message}")
                }
            }

            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = 0.0
        }
    }

    suspend fun pause(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val ok = sendKeypress(host, port, "Play")
        if (ok) {
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.playbackState = 2
        }
        ok
    }

    suspend fun resume(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val ok = sendKeypress(host, port, "Play")
        if (ok) {
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
        }
        ok
    }

    suspend fun stop(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val ok = sendKeypress(host, port, "Home")
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        activeHost = null
        ok
    }

    suspend fun seek(host: String, seconds: Double, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val current = CastSessionManager.playbackPositionSeconds
        val key = if (seconds > current) "Fwd" else "Rev"
        sendKeypress(host, port, key)
    }

    suspend fun setVolume(host: String, volume: Float, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val currentVol = CastSessionManager.volume
        val key = if (volume > currentVol) "VolumeUp" else "VolumeDown"
        val ok = sendKeypress(host, port, key)
        if (ok) {
            CastSessionManager.volume = volume
        }
        ok
    }

    suspend fun sendKeypress(host: String, port: Int, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:$port/keypress/$key"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody())
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Roku keypress error ($key): ${e.message}")
            false
        }
    }
}
