package com.castbrowse.app

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

object WebReceiverController {

    private const val TAG = "WebReceiverController"

    @Volatile
    var activeMediaUrl: String? = null
        private set

    @Volatile
    var activeMediaTitle: String? = null
        private set

    @Volatile
    var pendingCommand: String? = null
        private set

    @Volatile
    var pendingSeekTime: Double = 0.0
        private set

    @Volatile
    var commandVersion: Long = 0
        private set

    // Track active TV clients by IP and last seen timestamp
    private val connectedClients = ConcurrentHashMap<String, Long>()

    fun recordHeartbeat(clientIp: String) {
        connectedClients[clientIp] = System.currentTimeMillis()
    }

    fun getConnectedReceivers(port: Int = LocalMediaProxy.DEFAULT_PROXY_PORT): List<CastDevice> {
        val now = System.currentTimeMillis()
        // Consider clients active if seen within the last 60 seconds
        return connectedClients.filter { now - it.value < 60_000 }.map { (ip, _) ->
            CastDevice(
                name = "Web Receiver ($ip)",
                ipAddress = ip,
                port = port,
                protocol = CastProtocol.WEB_RECEIVER
            )
        }
    }

    fun play(url: String, title: String) {
        activeMediaUrl = url
        activeMediaTitle = title
        pendingCommand = "play"
        commandVersion++
        CastSessionManager.isMediaPlaying = true
        CastSessionManager.playbackState = 1
        CastSessionManager.playbackPositionSeconds = 0.0
        Log.d(TAG, "WebReceiver: Dispatched play command for '$title' ($url)")
    }

    fun pause() {
        pendingCommand = "pause"
        commandVersion++
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 2
        Log.d(TAG, "WebReceiver: Dispatched pause command")
    }

    fun resume() {
        pendingCommand = "resume"
        commandVersion++
        CastSessionManager.isMediaPlaying = true
        CastSessionManager.playbackState = 1
        Log.d(TAG, "WebReceiver: Dispatched resume command")
    }

    fun stop() {
        pendingCommand = "stop"
        commandVersion++
        activeMediaUrl = null
        activeMediaTitle = null
        CastSessionManager.isMediaPlaying = false
        CastSessionManager.playbackState = 0
        CastSessionManager.playbackPositionSeconds = 0.0
        Log.d(TAG, "WebReceiver: Dispatched stop command")
    }

    fun seek(seconds: Double) {
        pendingCommand = "seek"
        pendingSeekTime = seconds
        commandVersion++
        CastSessionManager.playbackPositionSeconds = seconds
        Log.d(TAG, "WebReceiver: Dispatched seek command to $seconds s")
    }

    @Volatile
    var isLoopEnabled: Boolean = false

    @Volatile
    var aspectRatio: String = "16:9"

    @Volatile
    var audioDelayMs: Int = 0

    @Volatile
    var subtitleUrl: String? = null

    @Volatile
    var subtitleOffsetMs: Int = 0

    fun setSubtitle(url: String?, offsetMs: Int = 0) {
        subtitleUrl = url
        subtitleOffsetMs = offsetMs
        commandVersion++
    }

    fun updateAspectRatio(ratio: String) {
        aspectRatio = ratio
        commandVersion++
    }

    fun setAudioDelay(delayMs: Int) {
        audioDelayMs = delayMs
        commandVersion++
    }

    fun setLoop(enabled: Boolean) {
        isLoopEnabled = enabled
        commandVersion++
    }

    fun getStateJson(): String {
        return buildJsonObject {
            put("url", activeMediaUrl ?: "")
            put("title", activeMediaTitle ?: "")
            put("command", pendingCommand ?: "")
            put("seekTo", pendingSeekTime)
            put("version", commandVersion)
            put("loop", isLoopEnabled)
            put("aspectRatio", aspectRatio)
            put("audioDelayMs", audioDelayMs)
            put("subtitleUrl", subtitleUrl ?: "")
            put("subtitleOffsetMs", subtitleOffsetMs)
        }.toString()
    }

    fun handleProgressUpdate(json: JsonObject) {
        try {
            val currentTime = json["currentTime"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val duration = json["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val state = json["state"]?.jsonPrimitive?.content ?: ""

            CastSessionManager.playbackPositionSeconds = currentTime
            if (duration > 0) {
                CastSessionManager.mediaDurationSeconds = duration
            }
            when (state) {
                "playing" -> {
                    CastSessionManager.isMediaPlaying = true
                    CastSessionManager.playbackState = 1
                }
                "paused" -> {
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.playbackState = 2
                }
                "ended" -> {
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.playbackState = 0
                    CastSessionManager.onPlaybackFinished()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing WebReceiver progress update", e)
        }
    }
}
