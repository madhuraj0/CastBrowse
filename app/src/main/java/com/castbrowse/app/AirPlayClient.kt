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

object AirPlayClient {

    private const val TAG = "AirPlayClient"
    const val AIRPLAY_DEFAULT_PORT = 7000

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private var pollJob: Job? = null
    private var activeHost: String? = null
    private var activePort: Int = AIRPLAY_DEFAULT_PORT

    suspend fun play(
        ipAddress: String,
        url: String,
        title: String,
        port: Int = AIRPLAY_DEFAULT_PORT,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopPolling()
            activeHost = ipAddress
            activePort = if (port > 0) port else AIRPLAY_DEFAULT_PORT

            Log.d(TAG, "AirPlay: Playing '$title' ($url) on $ipAddress:$activePort")

            val body = "Content-Location: $url\r\nStart-Position: 0.0\r\n"
            val request = Request.Builder()
                .url("http://$ipAddress:$activePort/play")
                .addHeader("Content-Type", "text/parameters")
                .addHeader("User-Agent", "MediaControl/1.0")
                .post(body.toRequestBody("text/parameters".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful && response.code != 200) {
                throw Exception("AirPlay /play failed with HTTP ${response.code}: ${response.message}")
            }

            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = 0.0

            startPolling(ipAddress, activePort, onDisconnected)
        }
    }

    suspend fun pause(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val ok = sendRate(host, port, 0.0f)
        if (ok) {
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.playbackState = 2
        }
        ok
    }

    suspend fun resume(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val ok = sendRate(host, port, 1.0f)
        if (ok) {
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
        }
        ok
    }

    suspend fun stop(ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        stopPolling()
        val request = Request.Builder()
            .url("http://$host:$port/stop")
            .addHeader("User-Agent", "MediaControl/1.0")
            .post("".toRequestBody())
            .build()
        val ok = try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        CastSessionManager.playbackPositionSeconds = 0.0
        activeHost = null
        ok
    }

    suspend fun seek(seconds: Double, ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val request = Request.Builder()
            .url("http://$host:$port/scrub?position=$seconds")
            .addHeader("User-Agent", "MediaControl/1.0")
            .post("".toRequestBody())
            .build()
        val ok = try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
        if (ok) {
            CastSessionManager.playbackPositionSeconds = seconds
        }
        ok
    }

    suspend fun setVolume(volume: Float, ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val clamped = volume.coerceIn(0f, 1f)
        val request = Request.Builder()
            .url("http://$host:$port/volume?value=$clamped")
            .addHeader("User-Agent", "MediaControl/1.0")
            .post("".toRequestBody())
            .build()
        val ok = try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
        if (ok) {
            CastSessionManager.volume = clamped
        }
        ok
    }

    private fun sendRate(host: String, port: Int, rate: Float): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://$host:$port/rate?value=$rate")
                .addHeader("User-Agent", "MediaControl/1.0")
                .post("".toRequestBody())
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting rate to $rate", e)
            false
        }
    }

    private fun startPolling(host: String, port: Int, onDisconnected: (() -> Unit)?) {
        stopPolling()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            var consecutiveFails = 0
            while (CastSessionManager.isMediaPlaying) {
                delay(1000)
                try {
                    val request = Request.Builder()
                        .url("http://$host:$port/scrub")
                        .addHeader("User-Agent", "MediaControl/1.0")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            consecutiveFails = 0
                            val body = response.body?.string() ?: ""
                            parseScrubResponse(body)
                        } else {
                            consecutiveFails++
                        }
                    }
                } catch (e: Exception) {
                    consecutiveFails++
                }
                if (consecutiveFails > 10) {
                    Log.w(TAG, "AirPlay: Receiver disconnected")
                    stopPolling()
                    onDisconnected?.invoke()
                    break
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun parseScrubResponse(body: String) {
        val lines = body.lines()
        var pos = 0.0
        var dur = 0.0
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon != -1) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim().toDoubleOrNull() ?: continue
                if (key.equals("position", ignoreCase = true)) {
                    pos = value
                } else if (key.equals("duration", ignoreCase = true)) {
                    dur = value
                }
            }
        }
        CastSessionManager.playbackPositionSeconds = pos
        if (dur > 0) {
            CastSessionManager.mediaDurationSeconds = dur
        }
    }
}
