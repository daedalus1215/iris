package io.github.daedalus1215.iris

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.daedalus1215.iris.core.BackgroundRemote
import io.github.daedalus1215.iris.core.HttpIrisClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * The remote in the notification shade and on the lock screen, where music apps show up: a
 * media session for the selected Apple TV, kept alive by a foreground service while the setting
 * is on. Its buttons, and the phone's volume keys, work with the app closed.
 */
class MediaControlsService : Service() {
    private val scope = MainScope()
    private lateinit var settings: AppSettings
    private lateinit var remote: BackgroundRemote
    private lateinit var session: MediaSession

    private val buttons = object : MediaSession.Callback() {
        override fun onPlay() = remote.send("play_pause")

        override fun onPause() = remote.send("play_pause")

        override fun onSkipToNext() = remote.send("next")

        override fun onSkipToPrevious() = remote.send("previous")

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (action in BUTTONS) remote.send(action)
        }
    }

    /**
     * The phone's volume keys, while nothing on the phone itself is playing. The TV doesn't say
     * what its volume is, so the slider Android shows just sits in the middle.
     */
    private val volumeKeys = object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
        override fun onAdjustVolume(direction: Int) {
            when (direction) {
                AudioManager.ADJUST_RAISE -> remote.volume("volume_up")
                AudioManager.ADJUST_LOWER -> remote.volume("volume_down")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        remote = BackgroundRemote(
            scope,
            client = { HttpIrisClient(settings.serverUrl, settings.token) },
            deviceId = { settings.selectedDeviceId },
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Media controls", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "Iris").apply {
            setCallback(buttons)
            setPlaybackToRemote(volumeKeys)
            setPlaybackState(playbackState())
            setMetadata(metadata())
            setSessionActivity(openApp())
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        // A tap on one of the notification's buttons (see [button]).
        intent?.action?.takeIf { it in BUTTONS }?.let(remote::send)
        // If Android stops it, the app starts it again the next time it opens.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Always playing, since the TV doesn't say whether it is, and Android only hands the volume
     * keys to a session that's playing. So the middle button always shows pause, and sends
     * play/pause either way. Back and home take the two spare buttons.
     */
    private fun playbackState(): PlaybackState = PlaybackState.Builder()
        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
        .setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT,
        )
        .addCustomAction(PlaybackState.CustomAction.Builder("menu", "Back", R.drawable.ic_arrow_back).build())
        .addCustomAction(PlaybackState.CustomAction.Builder("home", "Home", R.drawable.ic_tv).build())
        .build()

    private fun title(): String = settings.selectedDeviceName ?: getString(R.string.app_name)

    private fun text(): String =
        if (settings.selectedDeviceName == null) "Pick an Apple TV in the app" else "Apple TV"

    /** Android 13 and newer draw the card from this, rather than from the notification. */
    private fun metadata(): MediaMetadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title())
        .putString(MediaMetadata.METADATA_KEY_ARTIST, text())
        .build()

    private fun notification(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_tv)
        .setContentTitle(title())
        .setContentText(text())
        .setContentIntent(openApp())
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setStyle(
            Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2),
        )
        .addAction(button(R.drawable.ic_skip_previous, "Previous", "previous"))
        .addAction(button(R.drawable.ic_play_pause, "Play or pause", "play_pause"))
        .addAction(button(R.drawable.ic_skip_next, "Next", "next"))
        .addAction(button(R.drawable.ic_arrow_back, "Back", "menu"))
        .addAction(button(R.drawable.ic_tv, "Home", "home"))
        .build()

    /** Android 12 and older show these buttons; newer versions take them from the session. */
    private fun button(icon: Int, title: String, command: String): Notification.Action {
        val intent = Intent(this, MediaControlsService::class.java).setAction(command)
        return Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
        ).build()
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(this, 0, MainActivity.intent(this), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val CHANNEL = "media_controls"
        private const val NOTIFICATION_ID = 1

        /** What the buttons may send. */
        private val BUTTONS = setOf("play_pause", "previous", "next", "menu", "home")

        /** Starts or stops the media controls to match the setting. Call while the app is in front. */
        fun sync(context: Context) {
            val intent = Intent(context, MediaControlsService::class.java)
            if (!AppSettings(context).mediaControls) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                // Android refused because the app isn't in front after all; it'll be next time.
            }
        }
    }
}
