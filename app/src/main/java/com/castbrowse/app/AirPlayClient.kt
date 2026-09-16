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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.roundToInt

object AirPlayClient {

    private const val TAG = "AirPlayClient"
    const val AIRPLAY_DEFAULT_PORT = 7000

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var pollJob: Job? = null
    private var activeHost: String? = null
    private var activePort: Int = AIRPLAY_DEFAULT_PORT
    private var activeSessionId: String? = null

    /**
     * Sends a pre-flight GET /info request to wake up the receiver, verify
     * AirPlay service availability, and check for permission restrictions.
     */
    private fun preflightCheck(host: String, port: Int): Result<Unit> {
        return runCatching {
            val request = Request.Builder()
                .url("http://$host:$port/info")
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("Connection", "close")
                .get()
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                // If /info fails to connect or times out, try /server-info
                val fallbackReq = Request.Builder()
                    .url("http://$host:$port/server-info")
                    .addHeader("User-Agent", "MediaControl/1.0")
                    .addHeader("Connection", "close")
                    .get()
                    .build()
                httpClient.newCall(fallbackReq).execute()
            }

            response.use { resp ->
                if (resp.code == 403) {
                    throw Exception(
                        "AirPlay device rejected connection (HTTP 403 Forbidden). " +
                        "If casting to a Mac or Apple TV, please check System Settings > General > " +
                        "AirDrop & Handoff > AirPlay Receiver and ensure 'Allow AirPlay for' is set to " +
                        "'Anyone on the same network' or 'Everyone' without a password."
                    )
                }
            }
        }
    }

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
            val sessionId = UUID.randomUUID().toString()
            activeSessionId = sessionId

            Log.d(TAG, "AirPlay: Playing '$title' ($url) on $ipAddress:$activePort (session: $sessionId)")

            // Step 1: Pre-flight check / wake-up
            try {
                preflightCheck(ipAddress, activePort).getOrThrow()
            } catch (e: Exception) {
                if (e.message?.contains("403 Forbidden") == true) {
                    throw e
                }
                Log.w(TAG, "AirPlay: Pre-flight check notice: ${e.message}")
            }

            // Step 2: Try modern Apple binary plist format first (required by macOS Monterey+, tvOS 10.2+)
            var playSuccess = false
            var lastErrorCode = 0
            var lastErrorMessage = ""

            try {
                val bplistBytes = createPlayBinaryPlist(url, 0.0)
                val bplistRequest = Request.Builder()
                    .url("http://$ipAddress:$activePort/play")
                    .addHeader("Content-Type", "application/x-apple-binary-plist")
                    .addHeader("User-Agent", "MediaControl/1.0")
                    .addHeader("X-Apple-Session-ID", sessionId)
                    .addHeader("Connection", "close")
                    .post(bplistBytes.toRequestBody("application/x-apple-binary-plist".toMediaType()))
                    .build()

                httpClient.newCall(bplistRequest).execute().use { response ->
                    lastErrorCode = response.code
                    lastErrorMessage = response.message
                    if (response.isSuccessful || response.code == 200) {
                        playSuccess = true
                        Log.d(TAG, "AirPlay: Binary plist /play succeeded with HTTP ${response.code}")
                    } else if (response.code == 403) {
                        throw Exception(
                            "AirPlay connection forbidden (HTTP 403). If casting to Mac or Apple TV, " +
                            "ensure 'Allow AirPlay for' is set to 'Anyone on the same network' or 'Everyone' " +
                            "in macOS System Settings > AirDrop & Handoff."
                        )
                    }
                    Unit
                }
            } catch (e: Exception) {
                if (e.message?.contains("403") == true) throw e
                Log.w(TAG, "AirPlay: Binary plist /play attempt failed: ${e.message}, trying text/parameters fallback...")
            }

            // Step 3: Fallback to legacy text/parameters if binary plist was rejected
            if (!playSuccess) {
                val textBody = "Content-Location: $url\r\nStart-Position: 0.0\r\n"
                val textRequest = Request.Builder()
                    .url("http://$ipAddress:$activePort/play")
                    .addHeader("Content-Type", "text/parameters")
                    .addHeader("User-Agent", "MediaControl/1.0")
                    .addHeader("X-Apple-Session-ID", sessionId)
                    .addHeader("Connection", "close")
                    .post(textBody.toRequestBody("text/parameters".toMediaType()))
                    .build()

                httpClient.newCall(textRequest).execute().use { response ->
                    lastErrorCode = response.code
                    lastErrorMessage = response.message
                    if (response.isSuccessful || response.code == 200) {
                        playSuccess = true
                        Log.d(TAG, "AirPlay: text/parameters /play succeeded with HTTP ${response.code}")
                    } else if (response.code == 403) {
                        throw Exception(
                            "AirPlay connection forbidden (HTTP 403). If casting to Mac or Apple TV, " +
                            "ensure 'Allow AirPlay for' is set to 'Anyone on the same network' or 'Everyone' " +
                            "in macOS System Settings > AirDrop & Handoff."
                        )
                    }
                    Unit
                }
            }

            if (!playSuccess) {
                throw Exception("AirPlay /play failed with HTTP $lastErrorCode: $lastErrorMessage")
            }

            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = 0.0

            startPolling(ipAddress, activePort, sessionId, onDisconnected)
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
        val builder = Request.Builder()
            .url("http://$host:$port/stop")
            .addHeader("User-Agent", "MediaControl/1.0")
            .addHeader("Connection", "close")
            .post("".toRequestBody())
        activeSessionId?.let { builder.addHeader("X-Apple-Session-ID", it) }

        val ok = try {
            httpClient.newCall(builder.build()).execute().use { it.isSuccessful || it.code == 200 }
        } catch (e: Exception) {
            false
        }
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        CastSessionManager.playbackPositionSeconds = 0.0
        activeHost = null
        activeSessionId = null
        ok
    }

    suspend fun seek(seconds: Double, ipAddress: String? = activeHost, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val host = ipAddress ?: activeHost ?: return@withContext false
        val builder = Request.Builder()
            .url("http://$host:$port/scrub?position=$seconds")
            .addHeader("User-Agent", "MediaControl/1.0")
            .addHeader("Connection", "close")
            .post("".toRequestBody())
        activeSessionId?.let { builder.addHeader("X-Apple-Session-ID", it) }

        val ok = try {
            httpClient.newCall(builder.build()).execute().use { it.isSuccessful || it.code == 200 }
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

        // Convert linear 0.0..1.0 to AirPlay dB scale (-30.00 dB to 0.00 dB)
        val db = if (clamped <= 0.001f) -144.0f else (20.0f * log10(clamped)).coerceIn(-30.0f, 0.0f)

        val ok = sendVolumeParam(host, port, "volume", String.format(java.util.Locale.US, "%.6f", db)) ||
                 sendVolumeParam(host, port, "value", clamped.toString())

        if (ok) {
            CastSessionManager.volume = clamped
        }
        ok
    }

    private fun sendVolumeParam(host: String, port: Int, paramName: String, value: String): Boolean {
        return try {
            val builder = Request.Builder()
                .url("http://$host:$port/volume?$paramName=$value")
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("Connection", "close")
                .post("".toRequestBody())
            activeSessionId?.let { builder.addHeader("X-Apple-Session-ID", it) }
            httpClient.newCall(builder.build()).execute().use { it.isSuccessful || it.code == 200 }
        } catch (e: Exception) {
            false
        }
    }

    private fun sendRate(host: String, port: Int, rate: Float): Boolean {
        return try {
            val builder = Request.Builder()
                .url("http://$host:$port/rate?value=$rate")
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("Connection", "close")
                .post("".toRequestBody())
            activeSessionId?.let { builder.addHeader("X-Apple-Session-ID", it) }
            httpClient.newCall(builder.build()).execute().use { it.isSuccessful || it.code == 200 }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting rate to $rate", e)
            false
        }
    }

    private fun startPolling(host: String, port: Int, sessionId: String, onDisconnected: (() -> Unit)?) {
        stopPolling()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            var consecutiveNetworkFails = 0
            while (CastSessionManager.isMediaPlaying) {
                delay(1000)
                try {
                    val builder = Request.Builder()
                        .url("http://$host:$port/scrub")
                        .addHeader("User-Agent", "MediaControl/1.0")
                        .addHeader("X-Apple-Session-ID", sessionId)
                        .addHeader("Connection", "close")
                        .get()

                    val response = httpClient.newCall(builder.build()).execute()
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            consecutiveNetworkFails = 0
                            val body = resp.body?.string() ?: ""
                            parseScrubResponse(body)
                        } else if (resp.code == 404 || resp.code == 403) {
                            // Receiver terminated session
                            consecutiveNetworkFails++
                        } else {
                            // 500 or other transient buffering state - do not treat as network failure
                            consecutiveNetworkFails = 0
                        }
                    }
                } catch (e: IOException) {
                    consecutiveNetworkFails++
                } catch (e: Exception) {
                    // Non-network exceptions
                }

                // Disconnect only if receiver network socket is consistently unreachable (15+ seconds)
                if (consecutiveNetworkFails > 15) {
                    Log.w(TAG, "AirPlay: Receiver unreachable for 15s, triggering disconnect")
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

    /**
     * Creates an Apple binary property list (bplist00) containing:
     *   Content-Location: $url
     *   Start-Position: $startPosition
     *
     * Fully compatible with modern macOS and tvOS AirPlay receivers.
     */
    fun createPlayBinaryPlist(url: String, startPosition: Double = 0.0): ByteArray {
        val urlBytes = url.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        // 8-byte header: "bplist00"
        baos.write("bplist00".toByteArray(Charsets.US_ASCII))

        // Object 0: Dictionary of 2 key/value pairs
        // Type 0xD0 | count (2) => 0xD2
        // Object refs: key0=obj1, key1=obj2, val0=obj3, val1=obj4
        offsets.add(baos.size())
        baos.write(0xD2)
        baos.write(1)
        baos.write(2)
        baos.write(3)
        baos.write(4)

        // Object 1: ASCII String "Content-Location" (16 chars: 0x5F, 0x10, 16)
        offsets.add(baos.size())
        baos.write(0x5F)
        baos.write(0x10)
        baos.write(16)
        baos.write("Content-Location".toByteArray(Charsets.US_ASCII))

        // Object 2: ASCII String "Start-Position" (14 chars: 0x5E)
        offsets.add(baos.size())
        baos.write(0x5E)
        baos.write("Start-Position".toByteArray(Charsets.US_ASCII))

        // Object 3: UTF-8 String URL
        offsets.add(baos.size())
        val uLen = urlBytes.size
        if (uLen < 15) {
            baos.write(0x50 or uLen)
        } else if (uLen < 256) {
            baos.write(0x5F)
            baos.write(0x10)
            baos.write(uLen)
        } else {
            baos.write(0x5F)
            baos.write(0x11)
            baos.write((uLen shr 8) and 0xFF)
            baos.write(uLen and 0xFF)
        }
        baos.write(urlBytes)

        // Object 4: Real / 8-byte IEEE-754 double (0x23)
        offsets.add(baos.size())
        baos.write(0x23)
        val doubleBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        doubleBuf.putDouble(startPosition)
        baos.write(doubleBuf.array())

        // Offset Table
        val offsetTableOffset = baos.size()
        val offsetSize = if (offsetTableOffset < 256) 1 else 2
        for (off in offsets) {
            if (offsetSize == 1) {
                baos.write(off and 0xFF)
            } else {
                baos.write((off shr 8) and 0xFF)
                baos.write(off and 0xFF)
            }
        }

        // 32-byte Trailer
        // 6 unused bytes
        for (i in 0 until 6) baos.write(0)
        baos.write(offsetSize) // offset int size (1 or 2)
        baos.write(1)          // object ref size (1)
        // 8 bytes: number of objects (5)
        for (i in 0 until 7) baos.write(0)
        baos.write(offsets.size)
        // 8 bytes: top object index (0)
        for (i in 0 until 8) baos.write(0)
        // 8 bytes: offset table offset
        val trailerBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        trailerBuf.putLong(offsetTableOffset.toLong())
        baos.write(trailerBuf.array())

        return baos.toByteArray()
    }
}
