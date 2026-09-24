package com.castbrowse.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CastSessionManager {
    var castingDevice by mutableStateOf<CastDevice?>(null)
    var isCasting by mutableStateOf(false)
    var isMediaPlaying by mutableStateOf(false)
    var activeMediaUrl by mutableStateOf<String?>(null)
    var activeMediaTitle by mutableStateOf<String?>(null)
    var customFcastPort by mutableStateOf(FCastClient.FCAST_DEFAULT_PORT)
    /** Current playback position in seconds */
    var playbackPositionSeconds by mutableStateOf(0.0)
    /** 0=idle, 1=playing, 2=paused, 3=buffering */
    var playbackState by mutableStateOf(0)
    /** Total duration of media in seconds. */
    var mediaDurationSeconds by mutableStateOf(0.0)
    /** Receiver volume level (0.0 to 1.0) */
    var volume by mutableStateOf(0.5f)

    /** Loop/repeat playback continuously */
    var isLoopEnabled by mutableStateOf(false)

    /** Video aspect ratio: 16:9, Fill, Zoom, Original */
    var aspectRatio by mutableStateOf("16:9")

    /** Audio delay in ms (-500ms to +500ms) */
    var audioDelayMs by mutableStateOf(0)

    /** Subtitle sync offset in ms (-2000ms to +2000ms) */
    var subtitleOffsetMs by mutableStateOf(0)

    /** Active external subtitle URL and display name */
    var activeSubtitleUrl by mutableStateOf<String?>(null)
    var activeSubtitleName by mutableStateOf<String?>(null)

    /** Multi-audio stream options */
    var audioTracks by mutableStateOf<List<String>>(emptyList())
    var selectedAudioTrack by mutableStateOf(0)

    /** OLED TV Black Screen Mode (pure #000000 on TV when playing audio) */
    var isOledBlackScreenEnabled by mutableStateOf(true)

    /** Active media type: "video", "audio", "photo" */
    var activeMediaType by mutableStateOf("video")

    /** Photo slideshow state */
    val photoSlideshowList = androidx.compose.runtime.mutableStateListOf<DevicePhotoItem>()
    var currentPhotoIndex by mutableStateOf(0)
    var isSlideshowPlaying by mutableStateOf(false)
    var slideshowIntervalSeconds by mutableStateOf(5)

    /** Continuous playback queue */
    val mediaQueue = androidx.compose.runtime.mutableStateListOf<ExtractedVideo>()

    var appContext: Context? = null
    var onSessionStateChanged: (() -> Unit)? = null

    fun toggleOledBlackScreen(enabled: Boolean) {
        isOledBlackScreenEnabled = enabled
        if (activeMediaType == "audio") {
            WebReceiverController.setBlackScreen(enabled)
        }
    }

    suspend fun castPhoto(photo: DevicePhotoItem, context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext Result.failure(Exception("No active cast device"))
        activeMediaType = "photo"
        val mime = context.contentResolver.getType(photo.uri) ?: "image/jpeg"
        val proxiedUrl = LocalMediaProxy.registerLocalMedia(
            uri = photo.uri,
            title = photo.title,
            mimeType = mime,
            size = photo.size,
            receiverIp = device.ipAddress
        )
        activeMediaUrl = proxiedUrl
        activeMediaTitle = photo.title

        when (device.protocol) {
            CastProtocol.WEB_RECEIVER, CastProtocol.GOOGLE_CAST, CastProtocol.DIAL -> {
                WebReceiverController.showPhoto(proxiedUrl, photo.title)
                Result.success(Unit)
            }
            CastProtocol.DLNA -> {
                val controlUrl = device.controlUrl ?: "http://${device.ipAddress}:${device.port}/upnp/control/AVTransport1"
                DlnaClient.play(
                    controlUrl = controlUrl,
                    renderingControlUrl = device.renderingControlUrl,
                    mediaUrl = proxiedUrl,
                    title = photo.title
                )
            }
            CastProtocol.AIRPLAY -> {
                val photoBytes = try {
                    context.contentResolver.openInputStream(photo.uri)?.use { it.readBytes() }
                } catch (e: Exception) { null }

                if (photoBytes != null) {
                    AirPlayClient.displayPhoto(
                        ipAddress = device.ipAddress,
                        photoData = photoBytes,
                        port = device.port
                    )
                } else {
                    AirPlayClient.play(
                        ipAddress = device.ipAddress,
                        url = proxiedUrl,
                        title = photo.title,
                        port = device.port
                    )
                }
            }
            CastProtocol.ROKU -> {
                RokuClient.play(
                    ipAddress = device.ipAddress,
                    url = proxiedUrl,
                    title = photo.title,
                    port = device.port
                )
            }
            CastProtocol.FCAST -> {
                FCastClient.play(
                    ipAddress = device.ipAddress,
                    url = proxiedUrl,
                    title = photo.title,
                    port = device.port
                )
            }
        }
    }

    fun getRecentIps(context: Context): List<String> {
        val prefs = context.getSharedPreferences("cast_prefs", Context.MODE_PRIVATE)
        val ips = prefs.getString("recent_ips", "") ?: ""
        if (ips.isEmpty()) return emptyList()
        return ips.split(",").filter { it.isNotEmpty() }
    }

    fun saveRecentIp(context: Context, ip: String) {
        val prefs = context.getSharedPreferences("cast_prefs", Context.MODE_PRIVATE)
        val current = getRecentIps(context).toMutableList()
        current.remove(ip)
        current.add(0, ip)
        if (current.size > 5) {
            current.removeAt(current.size - 1)
        }
        prefs.edit().putString("recent_ips", current.joinToString(",")).apply()
    }

    suspend fun play(
        device: CastDevice,
        mediaUrl: String,
        title: String,
        type: String = activeMediaType,
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        LocalMediaProxy.start()
        val proxiedUrl = if (LocalMediaProxy.isProxyUrl(mediaUrl)) {
            mediaUrl
        } else {
            LocalMediaProxy.getProxyUrl(mediaUrl, receiverIp = device.ipAddress)
        }

        castingDevice = device
        activeMediaUrl = proxiedUrl
        activeMediaTitle = title
        activeMediaType = type

        if (type == "audio" && isOledBlackScreenEnabled) {
            WebReceiverController.setBlackScreen(true)
        } else if (type == "video") {
            WebReceiverController.setBlackScreen(false)
        }

        when (device.protocol) {
            CastProtocol.DLNA -> {
                val controlUrl = device.controlUrl ?: "http://${device.ipAddress}:${device.port}/upnp/control/AVTransport1"
                DlnaClient.play(
                    controlUrl = controlUrl,
                    renderingControlUrl = device.renderingControlUrl,
                    mediaUrl = proxiedUrl,
                    title = title,
                    onDisconnected = onDisconnected
                )
            }
            CastProtocol.AIRPLAY -> {
                val targetPort = if (device.port > 0) device.port else AirPlayClient.AIRPLAY_DEFAULT_PORT
                AirPlayClient.play(
                    ipAddress = device.ipAddress,
                    url = proxiedUrl,
                    title = title,
                    port = targetPort,
                    startPositionSeconds = playbackPositionSeconds,
                    onDisconnected = onDisconnected
                )
            }
            CastProtocol.ROKU -> {
                val targetPort = if (device.port > 0) device.port else RokuClient.ROKU_DEFAULT_PORT
                RokuClient.play(
                    ipAddress = device.ipAddress,
                    url = proxiedUrl,
                    title = title,
                    port = targetPort,
                    onDisconnected = onDisconnected
                )
            }
            CastProtocol.DIAL -> {
                val appUrl = device.applicationUrl ?: "http://${device.ipAddress}:${device.port}/apps"
                val localIp = NetworkDiagnostics.getHotspotIp() ?: LocalMediaProxy.getLocalIpAddress()
                val proxyWebReceiverUrl = "http://$localIp:${LocalMediaProxy.proxyPort}/tv"
                DialClient.launchApp(appUrl, "WebBrowser", proxyWebReceiverUrl)
                WebReceiverController.play(proxiedUrl, title)
                Result.success(Unit)
            }
            CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> {
                WebReceiverController.play(proxiedUrl, title)
                Result.success(Unit)
            }
            CastProtocol.FCAST -> {
                val targetPort = if (device.port > 0) device.port else customFcastPort
                FCastClient.play(
                    ipAddress = device.ipAddress,
                    url = proxiedUrl,
                    title = title,
                    port = targetPort,
                    headers = null,
                    onDisconnected = onDisconnected
                )
            }
        }
    }

    suspend fun pause() = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.pause()
            CastProtocol.AIRPLAY -> AirPlayClient.pause(device.ipAddress, device.port)
            CastProtocol.ROKU -> RokuClient.pause(device.ipAddress, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.pause()
            CastProtocol.FCAST -> FCastClient.pause(device.ipAddress, device.port)
        }
    }

    suspend fun resume() = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.resume()
            CastProtocol.AIRPLAY -> AirPlayClient.resume(device.ipAddress, device.port)
            CastProtocol.ROKU -> RokuClient.resume(device.ipAddress, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.resume()
            CastProtocol.FCAST -> FCastClient.resume(device.ipAddress, device.port)
        }
    }

    suspend fun seek(seconds: Double) = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.seek(seconds)
            CastProtocol.AIRPLAY -> AirPlayClient.seek(seconds, device.ipAddress, device.port)
            CastProtocol.ROKU -> RokuClient.seek(device.ipAddress, seconds, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.seek(seconds)
            CastProtocol.FCAST -> FCastClient.seek(device.ipAddress, seconds, device.port)
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val device = castingDevice
        if (device != null) {
            when (device.protocol) {
                CastProtocol.DLNA -> DlnaClient.stop()
                CastProtocol.AIRPLAY -> AirPlayClient.stop(device.ipAddress, device.port)
                CastProtocol.ROKU -> RokuClient.stop(device.ipAddress, device.port)
                CastProtocol.DIAL -> {
                    DialClient.stopApp()
                    WebReceiverController.stop()
                }
                CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.stop()
                CastProtocol.FCAST -> FCastClient.stop(device.ipAddress, device.port)
            }
        }
        isMediaPlaying = false
        playbackState = 0
        playbackPositionSeconds = 0.0
        activeMediaUrl = null
        activeMediaTitle = null
        activeMediaType = "video"
        isSlideshowPlaying = false
        WebReceiverController.setBlackScreen(false)
    }

    suspend fun setVolume(volume: Float) = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.setVolume(volume)
            CastProtocol.AIRPLAY -> AirPlayClient.setVolume(volume, device.ipAddress, device.port)
            CastProtocol.ROKU -> RokuClient.setVolume(device.ipAddress, volume, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> {
                CastSessionManager.volume = volume
            }
            CastProtocol.FCAST -> FCastClient.setVolume(device.ipAddress, volume, device.port)
        }
    }

    suspend fun jump(deltaSeconds: Double) = withContext(Dispatchers.IO) {
        val maxDuration = if (mediaDurationSeconds > 0) mediaDurationSeconds else 86400.0
        val target = (playbackPositionSeconds + deltaSeconds).coerceIn(0.0, maxDuration)
        seek(target)
    }

    suspend fun restart() = withContext(Dispatchers.IO) {
        seek(0.0)
    }

    fun updateAspectRatio(ratio: String) {
        aspectRatio = ratio
        WebReceiverController.updateAspectRatio(ratio)
    }

    fun setAudioDelay(delayMs: Int) {
        audioDelayMs = delayMs
        WebReceiverController.setAudioDelay(delayMs)
    }

    fun setSubtitleOffset(offsetMs: Int) {
        subtitleOffsetMs = offsetMs
        SubtitleManager.updateOffset(offsetMs)
    }

    fun setLoop(enabled: Boolean) {
        isLoopEnabled = enabled
        WebReceiverController.setLoop(enabled)
    }

    fun addToQueue(video: ExtractedVideo) {
        if (mediaQueue.none { it.url == video.url }) {
            mediaQueue.add(video)
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in mediaQueue.indices) {
            mediaQueue.removeAt(index)
        }
    }

    fun clearQueue() {
        mediaQueue.clear()
    }

    suspend fun playNext(): Result<Unit> = withContext(Dispatchers.IO) {
        if (mediaQueue.isEmpty()) return@withContext Result.failure(Exception("Queue is empty"))
        val next = mediaQueue.removeAt(0)
        val dev = castingDevice ?: return@withContext Result.failure(Exception("No active cast device"))
        play(dev, next.url, next.title.ifEmpty { "Media Stream" })
    }

    fun onPlaybackFinished() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val ctx = appContext
            if (ctx != null && activeMediaUrl != null) {
                PlaybackResumeManager.clearPosition(ctx, activeMediaUrl)
            }
            if (isLoopEnabled) {
                restart()
            } else if (mediaQueue.isNotEmpty()) {
                playNext()
            } else {
                stop()
            }
        }
    }
}
