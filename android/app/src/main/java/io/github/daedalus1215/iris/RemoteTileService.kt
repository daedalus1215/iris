package io.github.daedalus1215.iris

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A Quick Settings tile that opens the remote. */
class RemoteTileService : TileService() {
    override fun onStartListening() {
        // A button rather than a switch, so it never shows as on.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::openRemote) else openRemote()
    }

    private fun openRemote() {
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
