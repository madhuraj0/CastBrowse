package com.castbrowse.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service that holds a partial WakeLock and high-performance WifiLock
 * while media is casting to an FCast receiver. This prevents Android Doze mode and
 * screen-lock power savings from dropping the TCP socket and media proxy stream.
 */
class CastPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var currentTitle: String = "Media Stream"
    private var currentDeviceName: String = "FCast Receiver"
    private var currentIp: String = ""
    private var currentPort: Int = FCastClient.FCAST_DEFAULT_PORT
    private var currentUrl: String = ""

    companion object {
        private const val TAG = "CastPlaybackService"
        const val CHANNEL_ID = "cast_playback_channel"
        const val NOTIFICATION_ID = 2048

        const val ACTION_START = "com.castbrowse.app.action.START_CAST"
        const val ACTION_STOP = "com.castbrowse.app.action.STOP_CAST"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.castbrowse.app.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_UPDATE_STATE = "com.castbrowse.app.action.UPDATE_STATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_URL = "extra_url"

        fun start(
            context: Context,
            title: String,
            deviceName: String,
            ip: String,
            port: Int,
            url: String
        ) {
            val intent = Intent(context, CastPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context) {
            val intent = Intent(context, CastPlaybackService::class.java).apply {
                action = ACTION_UPDATE_STATE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating CastPlaybackService state: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CastPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed stopping CastPlaybackService: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeAndWifiLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Media Stream"
                currentDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "FCast Receiver"
                currentIp = intent.getStringExtra(EXTRA_IP) ?: ""
                currentPort = intent.getIntExtra(EXTRA_PORT, FCastClient.FCAST_DEFAULT_PORT)
                currentUrl = intent.getStringExtra(EXTRA_URL) ?: ""

                startForegroundWithNotification()
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                handleTogglePlayPause()
            }
            ACTION_UPDATE_STATE -> {
                updateNotification()
            }
            ACTION_STOP -> {
                handleStop()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handleTogglePlayPause() {
        val ip = currentIp.ifEmpty { CastSessionManager.castingDevice?.ipAddress ?: return }
        val port = currentPort
        serviceScope.launch(Dispatchers.IO) {
            if (CastSessionManager.isMediaPlaying) {
                FCastClient.pause(ip, port)
                CastSessionManager.isMediaPlaying = false
                CastSessionManager.playbackState = 2
            } else {
                FCastClient.resume(ip, port)
                CastSessionManager.isMediaPlaying = true
                CastSessionManager.playbackState = 1
            }
            updateNotification()
        }
    }

    private fun handleStop() {
        val ip = currentIp.ifEmpty { CastSessionManager.castingDevice?.ipAddress ?: "" }
        val port = currentPort
        serviceScope.launch(Dispatchers.IO) {
            if (ip.isNotEmpty()) {
                FCastClient.stop(ip, port)
            }
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.activeMediaUrl = null
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, CastControlActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = CastSessionManager.isMediaPlaying
        val toggleActionIntent = Intent(this, CastPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopActionIntent = Intent(this, CastPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Resume"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("Casting to $currentDeviceName • ${if (isPlaying) "Playing" else "Paused"}")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(playPauseIcon, playPauseText, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun acquireWakeAndWifiLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CastBrowse:CastPlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours safety timeout
            }
            Log.d(TAG, "WakeLock acquired for background casting")
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring WakeLock: ${e.message}")
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "CastBrowse:CastWifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "WifiLock acquired for uninterrupted Wi-Fi streaming")
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring WifiLock: ${e.message}")
        }
    }

    private fun releaseWakeAndWifiLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing WakeLock: ${e.message}")
        }
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "WifiLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing WifiLock: ${e.message}")
        }
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FCast Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active FCast streaming session controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeAndWifiLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
        Log.d(TAG, "CastPlaybackService destroyed")
    }
}
