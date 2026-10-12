package io.github.daedalus1215.iris

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A Quick Settings tile that opens the remote. */
class RemoteTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            // A button rather than a switch, so it never shows as on.
            state = Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            // The TV it controls, in place of Android's "Off", which reads as if Iris were off.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = AppSettings(this@RemoteTileService).selectedDeviceName ?: "Remote"
            }
            updateTile()
        }
    }

    // On the lock screen too: the remote shows over it, without unlocking.
    override fun onClick() {
        val intent = MainActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            // Android 14 and newer only take a PendingIntent.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
