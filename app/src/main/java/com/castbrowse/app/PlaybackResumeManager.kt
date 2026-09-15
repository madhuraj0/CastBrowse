package com.castbrowse.app

import android.content.Context
import android.content.SharedPreferences

object PlaybackResumeManager {

    private const val PREFS_NAME = "castbrowse_playback_resume"
    private const val MIN_RESUME_THRESHOLD_SECONDS = 15.0

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the last played timestamp for a specific media URL or identifier.
     */
    fun savePosition(context: Context, url: String?, positionSeconds: Double, durationSeconds: Double = 0.0) {
        if (url.isNullOrBlank()) return
        // Do not save if position is too early or near the very end (within 15s of completion)
        if (positionSeconds < MIN_RESUME_THRESHOLD_SECONDS) return
        if (durationSeconds > 0 && (durationSeconds - positionSeconds) < 15.0) {
            clearPosition(context, url)
            return
        }

        getPrefs(context).edit().putFloat(url, positionSeconds.toFloat()).apply()
    }

    /**
     * Retrieves the saved timestamp (in seconds) for a media URL. Returns 0.0 if none.
     */
    fun getSavedPosition(context: Context, url: String?): Double {
        if (url.isNullOrBlank()) return 0.0
        val pos = getPrefs(context).getFloat(url, 0f).toDouble()
        return if (pos >= MIN_RESUME_THRESHOLD_SECONDS) pos else 0.0
    }

    /**
     * Clears the saved resume timestamp when video finishes.
     */
    fun clearPosition(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        getPrefs(context).edit().remove(url).apply()
    }

    /**
     * Formats seconds into mm:ss or hh:mm:ss for display.
     */
    fun formatTimestamp(seconds: Double): String {
        val totalSecs = seconds.toInt()
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    fun formatTime(seconds: Double): String = formatTimestamp(seconds)
}
