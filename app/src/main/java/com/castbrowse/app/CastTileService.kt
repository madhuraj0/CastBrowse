package com.castbrowse.app

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class CastTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        when {
            CastSessionManager.isMediaPlaying -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "CastBrowse"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = CastSessionManager.activeMediaTitle?.take(24) ?: "Playing"
                }
            }
            CastSessionManager.castingDevice != null -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "CastBrowse"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = CastSessionManager.castingDevice?.name ?: "Connected"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "CastBrowse"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Streams & Cast"
                }
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        // Pass intent to MainActivity to check clipboard once the window gains user focus (required on Android 10+)
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_CHECK_CLIPBOARD", true)
        }

        // Launch activity and collapse QS panel

        // 3. Launch activity and collapse QS panel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }
}
