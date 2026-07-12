package com.castbrowse.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object FCastClient {

    private const val TAG = "FCastClient"
    const val FCAST_DEFAULT_PORT = 46899

    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null
    private var listenJob: Job? = null

    /**
     * Casts a media URL to an FCast receiver.
     * Establishes a persistent socket connection, performs protocol v3 negotiation,
     * sends Initial message, and sends the Play message.
     */
    suspend fun play(
        ipAddress: String,
        url: String,
        title: String,
        port: Int = FCAST_DEFAULT_PORT,
        headers: Map<String, String>? = null,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Close any existing session
            disconnect()

            Log.d(TAG, "Connecting to $ipAddress:$port")
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, port), 5000)
            
            val outputStream = socket.getOutputStream()
            activeSocket = socket
            activeOutputStream = outputStream

            // 1. Send Version message (Opcode 11)
            val versionJson = buildJsonObject {
                put("version", 3)
            }.toString()
            writeCommandPacket(outputStream, 11, versionJson)
            Log.d(TAG, "Sent Version handshake")

            // 2. Send Initial message (Opcode 14)
            val initialJson = buildJsonObject {
                put("displayName", "CastBrowse Android")
                put("appName", "CastBrowse")
                put("appVersion", "1.0.0")
            }.toString()
            writeCommandPacket(outputStream, 14, initialJson)
            Log.d(TAG, "Sent Initial handshake")

            // Start a reader thread to consume receiver updates, handle Pings and keep connection alive
            listenJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val inputStream = socket.getInputStream()
                    val headerBuffer = ByteArray(5)
                    while (socket.isConnected && !socket.isClosed) {
                        var offset = 0
                        while (offset < 5) {
                            val read = inputStream.read(headerBuffer, offset, 5 - offset)
                            if (read == -1) throw java.io.EOFException("EOF reading packet header")
                            offset += read
                        }
                        
                        val size = ((headerBuffer[0].toInt() and 0xFF) or
                                    ((headerBuffer[1].toInt() and 0xFF) shl 8) or
                                    ((headerBuffer[2].toInt() and 0xFF) shl 16) or
                                    ((headerBuffer[3].toInt() and 0xFF) shl 24))
                        val opcode = headerBuffer[4].toInt() and 0xFF
                        
                        val bodySize = size - 1
                        val bodyStr = if (bodySize > 0) {
                            val bodyBytes = ByteArray(bodySize)
                            var bodyOffset = 0
                            while (bodyOffset < bodySize) {
                                val read = inputStream.read(bodyBytes, bodyOffset, bodySize - bodyOffset)
                                if (read == -1) throw java.io.EOFException("EOF reading packet body")
                                bodyOffset += read
                            }
                            String(bodyBytes, Charsets.UTF_8)
                        } else {
                            ""
                        }
                        
                        Log.d(TAG, "FCast: Received opcode=$opcode, body='$bodyStr'")
                        
                        when (opcode) {
                            // Ping → Pong
                            12 -> activeOutputStream?.let { out ->
                                writeCommandPacket(out, 13)
                                Log.d(TAG, "FCast: Responded to Ping with Pong")
                            }
                            // PlaybackUpdate (6) — receiver pushes current position/state/duration
                            6 -> try {
                                val json = org.json.JSONObject(bodyStr)
                                val state = json.optInt("state", -1)
                                val time = json.optDouble("time", -1.0)
                                val duration = json.optDouble("duration", -1.0)
                                if (state >= 0) {
                                    CastSessionManager.playbackState = state
                                    CastSessionManager.isMediaPlaying = state == 1
                                }
                                if (time >= 0) {
                                    CastSessionManager.playbackPositionSeconds = time
                                }
                                if (duration >= 0) {
                                    CastSessionManager.mediaDurationSeconds = duration
                                }
                                Log.d(TAG, "FCast: PlaybackUpdate state=$state time=$time duration=$duration")
                            } catch (e: Exception) {
                                Log.w(TAG, "FCast: Failed to parse PlaybackUpdate: ${e.message}")
                            }
                            // VolumeUpdate (7) — receiver pushes updated volume
                            7 -> try {
                                val json = org.json.JSONObject(bodyStr)
                                val volume = json.optDouble("volume", -1.0)
                                if (volume >= 0.0) {
                                    Log.d(TAG, "FCast: VolumeUpdate volume=$volume")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "FCast: Failed to parse VolumeUpdate: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "FCast: Reader loop exception: ${e.message}")
                } finally {
                    onDisconnected?.invoke()
                    disconnect()
                }
            }

            // 3. Send Play command packet (Opcode 1)
            val container = getContainerType(url) ?: "video/mp4"
            val playJson = buildJsonObject {
                put("container", container)
                put("url", url)
                if (headers != null && headers.isNotEmpty()) {
                    putJsonObject("headers") {
                        headers.forEach { (key, value) ->
                            put(key, value)
                        }
                    }
                }
                putJsonObject("metadata") {
                    put("type", 0) // Generic metadata
                    put("title", title)
                }
            }.toString()

            writeCommandPacket(outputStream, 1, playJson)
            Log.d(TAG, "Sent Play command: $playJson")

            // Optimistically mark as playing immediately
            CastSessionManager.isMediaPlaying = true
            CastSessionManager.playbackState = 1
            CastSessionManager.playbackPositionSeconds = 0.0

            // Poll receiver for live position/state every 3s (opcode 8 = RequestPlaybackStatus)
            CoroutineScope(Dispatchers.IO).launch {
                while (activeSocket?.isConnected == true && activeSocket?.isClosed == false) {
                    delay(3000)
                    try {
                        activeOutputStream?.let { writeCommandPacket(it, 8) }
                    } catch (e: Exception) {
                        break
                    }
                }
            }

            Unit
        }.onFailure { e ->
            Log.e(TAG, "FCast play failed to connect/write to $ipAddress:$port", e)
            disconnect()
        }
    }

    /**
     * Pauses the media stream on the active connection (Opcode 2).
     */
    suspend fun pause(ipAddress: String, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        return sendOrFallbackCommand(ipAddress, 2, "", port)
    }

    /**
     * Resumes the media stream on the active connection (Opcode 3).
     */
    suspend fun resume(ipAddress: String, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        return sendOrFallbackCommand(ipAddress, 3, "", port)
    }

    /**
     * Stops the media stream (Opcode 4) and closes connection.
     */
    suspend fun stop(ipAddress: String, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        val result = sendOrFallbackCommand(ipAddress, 4, "", port)
        disconnect()
        return result
    }

    /**
     * Seeks to a specific timestamp in seconds (Opcode 5).
     */
    suspend fun seek(ipAddress: String, timeInSeconds: Double, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        val jsonPayload = buildJsonObject {
            put("time", timeInSeconds)
        }.toString()
        return sendOrFallbackCommand(ipAddress, 5, jsonPayload, port)
    }

    /**
     * Sets the volume (0.0 to 1.0) on the receiver (Opcode 8).
     */
    suspend fun setVolume(ipAddress: String, volumeValue: Float, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        val jsonPayload = buildJsonObject {
            put("volume", volumeValue.toDouble())
        }.toString()
        return sendOrFallbackCommand(ipAddress, 8, jsonPayload, port)
    }

    /**
     * Sets the playback speed factor on the receiver (Opcode 10).
     */
    suspend fun setSpeed(ipAddress: String, speedValue: Double, port: Int = FCAST_DEFAULT_PORT): Result<Unit> {
        val jsonPayload = buildJsonObject {
            put("speed", speedValue)
        }.toString()
        return sendOrFallbackCommand(ipAddress, 10, jsonPayload, port)
    }

    fun disconnect() {
        try {
            listenJob?.cancel()
        } catch (e: Exception) {}
        listenJob = null
        try {
            activeOutputStream?.close()
        } catch (e: Exception) {}
        activeOutputStream = null
        try {
            activeSocket?.close()
        } catch (e: Exception) {}
        activeSocket = null
    }

    private suspend fun sendOrFallbackCommand(ipAddress: String, opcode: Int, jsonPayload: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val stream = activeOutputStream
        if (stream != null && activeSocket?.isConnected == true && activeSocket?.isClosed == false) {
            runCatching {
                writeCommandPacket(stream, opcode, jsonPayload)
            }.onFailure { e ->
                Log.w(TAG, "Failed writing to persistent stream, retrying with new socket", e)
                writeOneShot(ipAddress, opcode, jsonPayload, port)
            }
        } else {
            writeOneShot(ipAddress, opcode, jsonPayload, port)
        }
    }

    private fun writeCommandPacket(outputStream: OutputStream, opcode: Int, jsonPayload: String = "") {
        val payloadBytes = if (jsonPayload.isNotEmpty()) jsonPayload.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val size = payloadBytes.size + 1

        val header = ByteArray(5)
        header[0] = (size and 0xFF).toByte()
        header[1] = ((size shr 8) and 0xFF).toByte()
        header[2] = ((size shr 16) and 0xFF).toByte()
        header[3] = ((size shr 24) and 0xFF).toByte()
        header[4] = opcode.toByte()

        outputStream.write(header)
        if (payloadBytes.isNotEmpty()) {
            outputStream.write(payloadBytes)
        }
        outputStream.flush()
    }

    private fun writeOneShot(ipAddress: String, opcode: Int, jsonPayload: String, port: Int): Result<Unit> {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 4000)
                val out = socket.getOutputStream()
                
                // One-shot connection also requires version and initial exchange
                val versionJson = buildJsonObject { put("version", 3) }.toString()
                writeCommandPacket(out, 11, versionJson)
                
                val initialJson = buildJsonObject {
                    put("displayName", "CastBrowse Android")
                    put("appName", "CastBrowse")
                    put("appVersion", "1.0.0")
                }.toString()
                writeCommandPacket(out, 14, initialJson)
                
                writeCommandPacket(out, opcode, jsonPayload)
            }
        }.onFailure { e ->
            Log.e(TAG, "One-shot FCast command failed to $ipAddress:$port opcode=$opcode", e)
        }
    }

    private fun getContainerType(url: String): String? {
        var targetUrl = url
        if (url.contains("/proxy?") && url.contains("url=")) {
            try {
                val uri = java.net.URI(url)
                val query = uri.query ?: ""
                val urlParam = query.split("&")
                    .firstOrNull { it.startsWith("url=") }
                    ?.substringAfter("url=")
                if (urlParam != null) {
                    targetUrl = java.net.URLDecoder.decode(urlParam, "UTF-8")
                }
            } catch (e: Exception) {
                val idx = url.indexOf("url=")
                if (idx != -1) {
                    val rawVal = url.substring(idx + 4).substringBefore("&")
                    try {
                        targetUrl = java.net.URLDecoder.decode(rawVal, "UTF-8")
                    } catch (ex: Exception) {}
                }
            }
        }

        val cleanUrl = targetUrl.substringBefore("?").substringBefore("#").lowercase()
        return when {
            cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".m3u") -> "application/x-mpegURL"
            cleanUrl.endsWith(".mpd") -> "application/dash+xml"
            cleanUrl.endsWith(".mp4") -> "video/mp4"
            cleanUrl.endsWith(".webm") -> "video/webm"
            cleanUrl.endsWith(".mkv") -> "video/x-matroska"
            cleanUrl.contains(".m3u8") || cleanUrl.contains(".m3u") -> "application/x-mpegURL"
            cleanUrl.contains(".mpd") -> "application/dash+xml"
            cleanUrl.contains(".mp4") -> "video/mp4"
            cleanUrl.contains(".webm") -> "video/webm"
            cleanUrl.contains(".mkv") -> "video/x-matroska"
            else -> null
        }
    }
}
