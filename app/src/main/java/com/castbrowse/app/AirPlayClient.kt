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
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.log10

object AirPlayClient {

    private const val TAG = "AirPlayClient"
    const val AIRPLAY_DEFAULT_PORT = 7000

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var pollJob: Job? = null
    private var activeHost: String? = null
    private var activePort: Int = AIRPLAY_DEFAULT_PORT
    private var activeSessionId: String? = null

    private var sessionSocket: Socket? = null
    private val socketLock = Any()

    private fun closeSessionSocket() {
        synchronized(socketLock) {
            try {
                sessionSocket?.close()
            } catch (e: Exception) {}
            sessionSocket = null
        }
    }

    private var isTargetModernApple: Boolean = false

    /**
     * Sends an HTTP request over the active persistent session socket.
     * AirPlay receivers (especially UxPlay on Android/Linux) associate the active
     * video session with the TCP connection that issued POST /play.
     */
    private fun sendSessionRequest(
        method: String,
        path: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): Pair<Int, ByteArray> = synchronized(socketLock) {
        val socket = sessionSocket ?: return Pair(-1, ByteArray(0))
        return try {
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            val host = activeHost ?: "127.0.0.1"
            val port = activePort
            val sessionId = activeSessionId ?: UUID.randomUUID().toString()

            val headerBuilder = StringBuilder()
            headerBuilder.append("$method $path HTTP/1.1\r\n")
            headerBuilder.append("Host: $host:$port\r\n")
            headerBuilder.append("User-Agent: AirPlay/600.0\r\n")
            headerBuilder.append("X-Apple-Device-Name: CastBrowse\r\n")
            headerBuilder.append("X-Apple-Session-ID: $sessionId\r\n")
            headerBuilder.append("X-Apple-Stream-ID: 1\r\n")
            headerBuilder.append("X-Apple-ProtocolVersion: 1\r\n")
            if (contentType != null) {
                headerBuilder.append("Content-Type: $contentType\r\n")
            }
            val length = body?.size ?: 0
            headerBuilder.append("Content-Length: $length\r\n")
            headerBuilder.append("\r\n")

            out.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
            if (body != null && body.isNotEmpty()) {
                out.write(body)
            }
            out.flush()

            val headerBytes = ByteArrayOutputStream()
            var matched = 0
            var b: Int
            while (inp.read().also { b = it } != -1) {
                headerBytes.write(b)
                if ((matched == 0 || matched == 2) && b == '\r'.code) {
                    matched++
                } else if ((matched == 1 || matched == 3) && b == '\n'.code) {
                    matched++
                    if (matched == 4) break
                } else {
                    matched = 0
                }
            }

            val headerStr = headerBytes.toString("UTF-8")
            val lines = headerStr.lines()
            val statusLine = lines.firstOrNull() ?: ""
            val statusParts = statusLine.split(" ")
            val statusCode = if (statusParts.size >= 2) statusParts[1].toIntOrNull() ?: -1 else -1

            var contentLength = 0
            for (line in lines) {
                val colon = line.indexOf(':')
                if (colon != -1) {
                    val k = line.substring(0, colon).trim()
                    val v = line.substring(colon + 1).trim()
                    if (k.equals("content-length", ignoreCase = true)) {
                        contentLength = v.toIntOrNull() ?: 0
                    }
                }
            }

            val responseBytes = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = inp.read(buf, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                if (read == contentLength) buf else buf.copyOf(read)
            } else {
                ByteArray(0)
            }

            Pair(statusCode, responseBytes)
        } catch (e: Exception) {
            Log.w(TAG, "AirPlay session socket error: ${e.message}")
            Pair(-1, ByteArray(0))
        }
    }



    suspend fun play(
        ipAddress: String,
        url: String,
        title: String,
        port: Int = AIRPLAY_DEFAULT_PORT,
        startPositionSeconds: Double = 0.0,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopPolling()
            closeSessionSocket()

            activeHost = ipAddress
            activePort = if (port > 0) port else AIRPLAY_DEFAULT_PORT
            val sessionId = UUID.randomUUID().toString()
            activeSessionId = sessionId

            Log.i(TAG, "AirPlay: Playing '$title' ($url) on $ipAddress:$activePort (session: $sessionId, startPos: $startPositionSeconds)")

            // Step 1: Establish persistent session TCP socket
            val socket = Socket()
            socket.soTimeout = 30000
            socket.connect(InetSocketAddress(ipAddress, activePort), 8000)
            socket.tcpNoDelay = true
            sessionSocket = socket

            // Step 2: Query /server-info on the persistent session socket
            val (infoCode, _) = sendSessionRequest("GET", "/server-info")
            if (infoCode == 403) {
                closeSessionSocket()
                throw Exception(
                    "AirPlay connection forbidden (HTTP 403).\n" +
                    "On your Mac, open System Settings > General > AirDrop & Handoff > AirPlay Receiver:\n" +
                    "• Set 'Allow AirPlay for' to 'Anyone on the same network' or 'Everyone'\n" +
                    "• Turn OFF 'Require password'"
                )
            }
            val isModernApple = (infoCode in 200..299)
            // Start polling with isTargetModernApple = false so scrub-capable targets (Android AirPlay, UxPlay)
            // are queried for real position and duration. Targets that reject /scrub (macOS Monterey+)
            // transition automatically to modern Apple simulation and /server-info keepalives.
            isTargetModernApple = false
            Log.i(TAG, "AirPlay: Target $ipAddress:$activePort /server-info returned $infoCode (modernApple=$isModernApple)")

            var lastErrorCode = 0
            var lastErrorMessage = ""

            fun tryBinaryPlist(): Boolean {
                return try {
                    val bplistBytes = createPlayBinaryPlist(url, startPositionSeconds, sessionId)
                    val (code, respBytes) = sendSessionRequest(
                        method = "POST",
                        path = "/play",
                        contentType = "application/x-apple-binary-plist",
                        body = bplistBytes
                    )
                    lastErrorCode = code
                    lastErrorMessage = String(respBytes, Charsets.UTF_8)
                    if (code in 200..299) {
                        Log.i(TAG, "AirPlay: Binary plist /play succeeded with HTTP $code")
                        true
                    } else if (code == 403) {
                        throw Exception(
                            "AirPlay connection forbidden (HTTP 403).\n" +
                            "On your Mac, open System Settings > General > AirDrop & Handoff > AirPlay Receiver:\n" +
                            "• Set 'Allow AirPlay for' to 'Anyone on the same network' or 'Everyone'\n" +
                            "• Turn OFF 'Require password'"
                        )
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("403") == true) throw e
                    Log.w(TAG, "AirPlay: Binary plist /play attempt failed: ${e.message}")
                    false
                }
            }

            fun tryTextParameters(): Boolean {
                return try {
                    val formattedPos = String.format(java.util.Locale.US, "%.6f", startPositionSeconds)
                    val textBody = "Content-Location: $url\r\nStart-Position: $formattedPos\r\n".toByteArray(Charsets.UTF_8)
                    val (code, respBytes) = sendSessionRequest(
                        method = "POST",
                        path = "/play",
                        contentType = "text/parameters",
                        body = textBody
                    )
                    lastErrorCode = code
                    lastErrorMessage = String(respBytes, Charsets.UTF_8)
                    if (code in 200..299) {
                        Log.i(TAG, "AirPlay: text/parameters /play succeeded with HTTP $code")
                        true
                    } else if (code == 403) {
                        throw Exception(
                            "AirPlay connection forbidden (HTTP 403).\n" +
                            "On your Mac, open System Settings > General > AirDrop & Handoff > AirPlay Receiver:\n" +
                            "• Set 'Allow AirPlay for' to 'Anyone on the same network' or 'Everyone'\n" +
                            "• Turn OFF 'Require password'"
                        )
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("403") == true) throw e
                    Log.w(TAG, "AirPlay: text/parameters /play attempt failed: ${e.message}")
                    false
                }
            }

            // Step 3: Negotiate payload format
            // If modern Apple (macOS Monterey+, tvOS 10.2+), try binary plist first then text/parameters.
            // If legacy or third-party (Android AirPlay, Kodi, UxPlay, AppleTV3), send text/parameters first!
            val playSuccess = if (isModernApple) {
                val ok = tryBinaryPlist()
                if (!ok) {
                    Log.i(TAG, "AirPlay: Falling back to text/parameters for modern target...")
                    tryTextParameters()
                } else true
            } else {
                val ok = tryTextParameters()
                if (!ok) {
                    Log.i(TAG, "AirPlay: Falling back to binary plist for legacy/third-party target...")
                    tryBinaryPlist()
                } else true
            }

            if (!playSuccess) {
                closeSessionSocket()
                throw Exception("AirPlay /play failed with HTTP $lastErrorCode: $lastErrorMessage")
            }

            // Explicitly set rate=1.0 so receiver begins playback immediately
            try {
                sendRate(1.0f)
            } catch (e: Exception) {
                Log.w(TAG, "AirPlay: rate=1 notice: ${e.message}")
            }

            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = startPositionSeconds

            startPolling(onDisconnected)
        }
    }

    suspend fun pause(ipAddress: String? = null, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val ok = sendRate(0.0f)
        if (ok) {
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.playbackState = 2
        }
        ok
    }

    suspend fun resume(ipAddress: String? = null, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val ok = sendRate(1.0f)
        if (ok) {
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
        }
        ok
    }

    suspend fun stop(ipAddress: String? = null, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        stopPolling()
        val (code, _) = sendSessionRequest("POST", "/stop")
        closeSessionSocket()
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        CastSessionManager.playbackPositionSeconds = 0.0
        activeHost = null
        activeSessionId = null
        code in 200..299
    }

    suspend fun seek(seconds: Double, ipAddress: String? = null, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = sendSessionRequest("POST", "/scrub?position=$seconds")
        val ok = code in 200..299
        if (ok) {
            CastSessionManager.playbackPositionSeconds = seconds
        }
        ok
    }

    suspend fun setVolume(volume: Float, ipAddress: String? = null, port: Int = activePort): Boolean = withContext(Dispatchers.IO) {
        val clamped = volume.coerceIn(0f, 1f)
        val db = if (clamped <= 0.001f) -144.0f else (20.0f * log10(clamped)).coerceIn(-30.0f, 0.0f)
        val (code1, _) = sendSessionRequest("POST", "/volume?volume=${String.format(java.util.Locale.US, "%.6f", db)}")
        val ok = if (code1 in 200..299) true else {
            val (code2, _) = sendSessionRequest("POST", "/volume?value=$clamped")
            code2 in 200..299
        }
        if (ok) {
            CastSessionManager.volume = clamped
        }
        ok
    }

    private fun sendRate(rate: Float): Boolean {
        val (code, _) = sendSessionRequest("POST", "/rate?value=$rate")
        return code in 200..299
    }

    private fun startPolling(onDisconnected: (() -> Unit)? = null) {
        stopPolling()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            var consecutiveNetworkFails = 0
            var modernAppleKeepAliveTicks = 0
            while (CastSessionManager.isMediaPlaying && sessionSocket?.isConnected == true) {
                delay(1000)
                try {
                    if (isTargetModernApple) {
                        // Modern Apple receivers (macOS Monterey+, tvOS 10.2+) manage playback via AVPlayer.
                        // Advance position locally to keep UI progress bar responsive.
                        CastSessionManager.playbackPositionSeconds += 1.0
                        modernAppleKeepAliveTicks++

                        // Periodically send GET /server-info keep-alive to keep session alive and detect window close
                        if (modernAppleKeepAliveTicks >= 5) {
                            modernAppleKeepAliveTicks = 0
                            val (code, _) = sendSessionRequest("GET", "/server-info")
                            if (code in 200..299) {
                                consecutiveNetworkFails = 0
                            } else if (code == -1) {
                                consecutiveNetworkFails += 2
                            }
                        }

                        if (consecutiveNetworkFails >= 4) {
                            Log.i(TAG, "AirPlay: Modern Apple receiver session closed by remote")
                            stopPolling()
                            closeSessionSocket()
                            onDisconnected?.invoke()
                            break
                        }
                        continue
                    }

                    val path = "/scrub"
                    val (code, bodyBytes) = sendSessionRequest("GET", path)
                    if (code in 200..299) {
                        consecutiveNetworkFails = 0
                        if (bodyBytes.isNotEmpty()) {
                            parseScrubResponse(String(bodyBytes, Charsets.UTF_8))
                        }
                    } else if (code == 500 || code == 404) {
                        val (infoCode, infoBytes) = sendSessionRequest("GET", "/playback-info")
                        if (infoCode in 200..299) {
                            isTargetModernApple = true
                            consecutiveNetworkFails = 0
                            if (infoBytes.isNotEmpty()) {
                                val magic = if (infoBytes.size >= 8) String(infoBytes, 0, 8, Charsets.US_ASCII) else ""
                                if (magic.startsWith("bplist00")) {
                                    parsePlaybackInfoResponse(infoBytes)
                                } else {
                                    parseScrubResponse(String(infoBytes, Charsets.UTF_8))
                                }
                            }
                        } else if (infoCode == 500 || infoCode == 404) {
                            isTargetModernApple = true
                            consecutiveNetworkFails = 0
                            CastSessionManager.playbackPositionSeconds += 1.0
                        } else {
                            consecutiveNetworkFails++
                        }
                    } else if (code == -1 || code == 403) {
                        consecutiveNetworkFails++
                    }
                } catch (e: Exception) {
                    consecutiveNetworkFails++
                }

                if (consecutiveNetworkFails > 10) {
                    Log.w(TAG, "AirPlay: Receiver socket disconnected, triggering disconnect")
                    stopPolling()
                    closeSessionSocket()
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

    private fun parsePlaybackInfoResponse(bytes: ByteArray) {
        if (bytes.size < 32) return
        try {
            val trailerStart = bytes.size - 32
            val offsetSize = bytes[trailerStart + 6].toInt() and 0xFF
            val refSize = bytes[trailerStart + 7].toInt() and 0xFF
            val numObjects = ByteBuffer.wrap(bytes, trailerStart + 8, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
            val topObject = ByteBuffer.wrap(bytes, trailerStart + 16, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
            val offsetTableOffset = ByteBuffer.wrap(bytes, trailerStart + 24, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()

            if (offsetTableOffset < 0 || offsetTableOffset >= bytes.size || numObjects <= 0) return

            val offsets = IntArray(numObjects)
            for (i in 0 until numObjects) {
                var off = 0
                for (j in 0 until offsetSize) {
                    off = (off shl 8) or (bytes[offsetTableOffset + i * offsetSize + j].toInt() and 0xFF)
                }
                offsets[i] = off
            }

            fun parseString(off: Int): String {
                if (off < 0 || off >= bytes.size) return ""
                val header = bytes[off].toInt() and 0xFF
                val info = header and 0x0F
                var pos = off + 1
                var len = info
                if (info == 0x0F && pos < bytes.size) {
                    val extraHeader = bytes[pos].toInt() and 0xFF
                    val extraSize = 1 shl (extraHeader and 0x0F)
                    pos += 1
                    len = 0
                    for (k in 0 until extraSize) {
                        if (pos + k < bytes.size) {
                            len = (len shl 8) or (bytes[pos + k].toInt() and 0xFF)
                        }
                    }
                    pos += extraSize
                }
                if (pos + len <= bytes.size && len > 0) {
                    return String(bytes, pos, len, Charsets.UTF_8)
                }
                return ""
            }

            fun parseNumber(off: Int): Double {
                if (off < 0 || off >= bytes.size) return 0.0
                val header = bytes[off].toInt() and 0xFF
                val objType = header and 0xF0
                val info = header and 0x0F
                if (objType == 0x10) { // Int
                    val size = 1 shl info
                    var v = 0L
                    for (k in 0 until size) {
                        if (off + 1 + k < bytes.size) {
                            v = (v shl 8) or (bytes[off + 1 + k].toLong() and 0xFF)
                        }
                    }
                    return v.toDouble()
                } else if (objType == 0x20) { // Real
                    val size = 1 shl info
                    if (size == 4 && off + 5 <= bytes.size) {
                        return ByteBuffer.wrap(bytes, off + 1, 4).order(ByteOrder.BIG_ENDIAN).float.toDouble()
                    } else if (size == 8 && off + 9 <= bytes.size) {
                        return ByteBuffer.wrap(bytes, off + 1, 8).order(ByteOrder.BIG_ENDIAN).double
                    }
                }
                return 0.0
            }

            val rootOff = offsets[topObject]
            val rootHeader = bytes[rootOff].toInt() and 0xFF
            if ((rootHeader and 0xF0) == 0xD0) {
                val count = rootHeader and 0x0F
                var pos = rootOff + 1
                val keys = IntArray(count)
                for (i in 0 until count) {
                    var ref = 0
                    for (j in 0 until refSize) {
                        if (pos < bytes.size) {
                            ref = (ref shl 8) or (bytes[pos++].toInt() and 0xFF)
                        }
                    }
                    keys[i] = ref
                }
                val vals = IntArray(count)
                for (i in 0 until count) {
                    var ref = 0
                    for (j in 0 until refSize) {
                        if (pos < bytes.size) {
                            ref = (ref shl 8) or (bytes[pos++].toInt() and 0xFF)
                        }
                    }
                    vals[i] = ref
                }

                for (i in 0 until count) {
                    if (keys[i] in 0 until numObjects && vals[i] in 0 until numObjects) {
                        val keyName = parseString(offsets[keys[i]])
                        if (keyName.equals("position", ignoreCase = true)) {
                            CastSessionManager.playbackPositionSeconds = parseNumber(offsets[vals[i]])
                        } else if (keyName.equals("duration", ignoreCase = true)) {
                            val dur = parseNumber(offsets[vals[i]])
                            if (dur > 0) CastSessionManager.mediaDurationSeconds = dur
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AirPlay: Error parsing playback-info bplist: ${e.message}")
        }
    }

    /**
     * Creates an Apple binary property list (bplist00) containing AirPlay stream parameters:
     *   Content-Location (String), Start-Position (Real 8-byte Double), X-Apple-Session-ID (String), rate (Real 8-byte Double = 1.0).
     *
     * Keys are strictly sorted in lexicographical ASCII order as required by Apple's CFPropertyList binary parser:
     *   'Content-Location' ('C' 67) < 'Start-Position' ('S' 83) < 'X-Apple-Session-ID' ('X' 88) < 'rate' ('r' 114).
     *
     * Fully compatible with modern macOS (Monterey, Ventura, Sonoma, Sequoia) AVPlayer and tvOS receivers.
     */
    fun createPlayBinaryPlist(
        url: String,
        startPosition: Double = 0.0,
        sessionId: String = UUID.randomUUID().toString()
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun writeString(s: String) {
            offsets.add(baos.size())
            val b = s.toByteArray(Charsets.UTF_8)
            val len = b.size
            if (len < 15) {
                baos.write(0x50 or len)
            } else if (len < 256) {
                baos.write(0x5F)
                baos.write(0x10)
                baos.write(len)
            } else {
                baos.write(0x5F)
                baos.write(0x11)
                baos.write((len shr 8) and 0xFF)
                baos.write(len and 0xFF)
            }
            baos.write(b)
        }

        fun writeDouble(d: Double) {
            offsets.add(baos.size())
            baos.write(0x23)
            val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(d).array()
            baos.write(buf)
        }

        // 8-byte header: "bplist00"
        baos.write("bplist00".toByteArray(Charsets.US_ASCII))

        val keys = listOf("Content-Location", "Start-Position", "X-Apple-Session-ID", "rate")
        val count = keys.size

        // Object 0: Dictionary with `count` items
        offsets.add(baos.size())
        baos.write(0xD0 or count)
        for (i in 1..count) baos.write(i)
        for (i in (count + 1)..(2 * count)) baos.write(i)

        // Keys (indices 1..count)
        for (k in keys) {
            writeString(k)
        }

        // Values (indices count+1..2*count)
        writeString(url)
        writeDouble(startPosition)
        writeString(sessionId)
        writeDouble(1.0)

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
        for (i in 0 until 6) baos.write(0)
        baos.write(offsetSize)
        baos.write(1) // ref size = 1
        val numObjsBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(offsets.size.toLong()).array()
        baos.write(numObjsBuf)
        val topObjBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(0L).array()
        baos.write(topObjBuf)
        val tableOffBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(offsetTableOffset.toLong()).array()
        baos.write(tableOffBuf)

        return baos.toByteArray()
    }

    /**
     * Casts a still image / photo slide to the AirPlay receiver using standard PUT /photo.
     */
    suspend fun displayPhoto(
        ipAddress: String,
        photoData: ByteArray,
        port: Int = AIRPLAY_DEFAULT_PORT
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetPort = if (port > 0) port else AIRPLAY_DEFAULT_PORT
            val url = "http://$ipAddress:$targetPort/photo"
            Log.i(TAG, "AirPlay: Displaying photo (${photoData.size} bytes) on $ipAddress:$targetPort")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MediaControl/1.0")
                .addHeader("X-Apple-AssetKey", UUID.randomUUID().toString())
                .addHeader("X-Apple-Transition", "Dissolve")
                .put(photoData.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful && response.code !in 200..299) {
                throw Exception("AirPlay /photo failed with HTTP ${response.code}: ${response.message}")
            }
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
        }
    }
}
