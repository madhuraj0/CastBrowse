package com.castbrowse.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object CastSessionManager {
    var castingDevice by mutableStateOf<CastDevice?>(null)
    var isCasting by mutableStateOf(false)
    var isMediaPlaying by mutableStateOf(false)
    var activeMediaUrl by mutableStateOf<String?>(null)
    var customFcastPort by mutableStateOf(FCastClient.FCAST_DEFAULT_PORT)
    /** Current playback position in seconds, pushed by FCast PlaybackUpdate (opcode 7). */
    var playbackPositionSeconds by mutableStateOf(0.0)
    /** 0=idle, 1=playing, 2=paused, 3=buffering — from FCast PlaybackUpdate (opcode 6). */
    var playbackState by mutableStateOf(0)
    /** Total duration of media in seconds. */
    var mediaDurationSeconds by mutableStateOf(0.0)

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
}
