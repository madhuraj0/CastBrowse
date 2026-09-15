package com.castbrowse.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
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
        onDisconnected: (() -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        castingDevice = device
        activeMediaUrl = mediaUrl
        activeMediaTitle = title

        when (device.protocol) {
            CastProtocol.DLNA -> {
                val controlUrl = device.controlUrl ?: "http://${device.ipAddress}:${device.port}/upnp/control/AVTransport1"
                DlnaClient.play(
                    controlUrl = controlUrl,
                    renderingControlUrl = device.renderingControlUrl,
                    mediaUrl = mediaUrl,
                    title = title,
                    onDisconnected = onDisconnected
                )
            }
            CastProtocol.AIRPLAY -> {
                val targetPort = if (device.port > 0) device.port else AirPlayClient.AIRPLAY_DEFAULT_PORT
                AirPlayClient.play(
                    ipAddress = device.ipAddress,
                    url = mediaUrl,
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
                WebReceiverController.play(mediaUrl, title)
                Result.success(Unit)
            }
            CastProtocol.GOOGLE_CAST -> {
                WebReceiverController.play(mediaUrl, title)
                Result.success(Unit)
            }
            CastProtocol.WEB_RECEIVER -> {
                WebReceiverController.play(mediaUrl, title)
                Result.success(Unit)
            }
            CastProtocol.FCAST -> {
                val targetPort = if (device.port > 0) device.port else customFcastPort
                FCastClient.play(
                    ipAddress = device.ipAddress,
                    url = mediaUrl,
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
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.pause()
            CastProtocol.FCAST -> FCastClient.pause(device.ipAddress, device.port)
        }
    }

    suspend fun resume() = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.resume()
            CastProtocol.AIRPLAY -> AirPlayClient.resume(device.ipAddress, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> WebReceiverController.resume()
            CastProtocol.FCAST -> FCastClient.resume(device.ipAddress, device.port)
        }
    }

    suspend fun seek(seconds: Double) = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.seek(seconds)
            CastProtocol.AIRPLAY -> AirPlayClient.seek(seconds, device.ipAddress, device.port)
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
    }

    suspend fun setVolume(volume: Float) = withContext(Dispatchers.IO) {
        val device = castingDevice ?: return@withContext
        when (device.protocol) {
            CastProtocol.DLNA -> DlnaClient.setVolume(volume)
            CastProtocol.AIRPLAY -> AirPlayClient.setVolume(volume, device.ipAddress, device.port)
            CastProtocol.DIAL, CastProtocol.GOOGLE_CAST, CastProtocol.WEB_RECEIVER -> {
                CastSessionManager.volume = volume
            }
            CastProtocol.FCAST -> FCastClient.setVolume(device.ipAddress, volume, device.port)
        }
    }
}
