package com.castbrowse.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service integrating Android MediaSession for system media controls
 * (lock screen, notification slider, Quick Settings carousel) while holding partial
 * WakeLock and high-performance WifiLock to protect continuous streaming.
 */
class CastPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null

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
        const val ACTION_REWIND = "com.castbrowse.app.action.REWIND"
        const val ACTION_FAST_FORWARD = "com.castbrowse.app.action.FAST_FORWARD"
        const val ACTION_SKIP_NEXT = "com.castbrowse.app.action.SKIP_NEXT"
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
        CastSessionManager.appContext = applicationContext
        LocalMediaProxy.init(applicationContext)
        LocalMediaProxy.start()
        createNotificationChannel()
        acquireWakeAndWifiLocks()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "CastBrowsePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    handleResume()
                }

                override fun onPause() {
                    handlePause()
                }

                override fun onSeekTo(pos: Long) {
                    handleSeek(pos / 1000.0)
                }

                override fun onFastForward() {
                    handleJump(30.0)
                }

                override fun onRewind() {
                    handleJump(-30.0)
                }

                override fun onSkipToNext() {
                    handleSkipNext()
                }

                override fun onStop() {
                    handleStop()
                }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                LocalMediaProxy.start()
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
            ACTION_REWIND -> {
                handleJump(-10.0)
            }
            ACTION_FAST_FORWARD -> {
                handleJump(10.0)
            }
            ACTION_SKIP_NEXT -> {
                handleSkipNext()
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

        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(2000)
                if (CastSessionManager.isMediaPlaying) {
                    updateNotification()
                }
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handleTogglePlayPause() {
        serviceScope.launch(Dispatchers.IO) {
            if (CastSessionManager.isMediaPlaying) {
                CastSessionManager.pause()
            } else {
                CastSessionManager.resume()
            }
            updateNotification()
        }
    }

    private fun handleResume() {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.resume()
            updateNotification()
        }
    }

    private fun handlePause() {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.pause()
            updateNotification()
        }
    }

    private fun handleSeek(seconds: Double) {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.seek(seconds)
            updateNotification()
        }
    }

    private fun handleJump(deltaSeconds: Double) {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.jump(deltaSeconds)
            updateNotification()
        }
    }

    private fun handleSkipNext() {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.playNext()
            updateNotification()
        }
    }

    private fun handleStop() {
        serviceScope.launch(Dispatchers.IO) {
            CastSessionManager.stop()
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
        val posMs = (CastSessionManager.playbackPositionSeconds * 1000).toLong()
        val durMs = (CastSessionManager.mediaDurationSeconds * 1000).toLong()

        // Periodically record resume timestamp
        PlaybackResumeManager.savePosition(
            this,
            CastSessionManager.activeMediaUrl,
            CastSessionManager.playbackPositionSeconds,
            CastSessionManager.mediaDurationSeconds
        )

        // 1. Sync native MediaSession state & metadata
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setState(state, posMs, 1.0f)
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                (if (CastSessionManager.mediaQueue.isNotEmpty()) PlaybackState.ACTION_SKIP_TO_NEXT else 0L)
            )
            .build()
        mediaSession?.setPlaybackState(playbackState)

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "CastBrowse • $currentDeviceName")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, maxOf(0L, durMs))
            .build()
        mediaSession?.setMetadata(metadata)

        // 2. PendingIntents for notification action buttons
        val rwPending = PendingIntent.getService(
            this, 10,
            Intent(this, CastPlaybackService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPausePending = PendingIntent.getService(
            this, 11,
            Intent(this, CastPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ffPending = PendingIntent.getService(
            this, 12,
            Intent(this, CastPlaybackService::class.java).apply { action = ACTION_FAST_FORWARD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPending = PendingIntent.getService(
            this, 13,
            Intent(this, CastPlaybackService::class.java).apply { action = ACTION_SKIP_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, 14,
            Intent(this, CastPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Resume"

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("Casting to $currentDeviceName • ${if (isPlaying) "Playing" else "Paused"}")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Attach system MediaStyle
        val mediaStyle = Notification.MediaStyle()
        mediaSession?.let {
            mediaStyle.setMediaSession(it.sessionToken)
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        }
        builder.style = mediaStyle

        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_rew,
                "-10s",
                rwPending
            ).build()
        )
        builder.addAction(
            Notification.Action.Builder(
                playPauseIcon,
                playPauseText,
                playPausePending
            ).build()
        )
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_ff,
                "+10s",
                ffPending
            ).build()
        )
        if (CastSessionManager.mediaQueue.isNotEmpty()) {
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextPending
                ).build()
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPending
                ).build()
            )
        }

        return builder.build()
    }

    private fun acquireWakeAndWifiLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CastBrowse:CastPlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
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
                "Cast Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active streaming session controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
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
