package io.github.daedalus1215.iris

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.daedalus1215.iris.ui.RemoteScreen
import io.github.daedalus1215.iris.ui.theme.IrisTheme

// Android 17 (API 37) requires this to reach anything on the home network, including the Iris server.
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val ANDROID_17 = 37

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()
    private var volumeKeyHeld = false
    private var localNetworkAllowed by mutableStateOf(true)

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[ACCESS_LOCAL_NETWORK] ?: return@registerForActivityResult
            localNetworkAllowed = granted
            if (granted) viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        localNetworkAllowed = hasLocalNetworkPermission()
        if (savedInstanceState == null) {
            val missing = listOfNotNull(
                ACCESS_LOCAL_NETWORK.takeUnless { localNetworkAllowed },
                // The media controls live in a notification.
                Manifest.permission.POST_NOTIFICATIONS.takeIf { needsNotificationPermission() },
            )
            if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
        }
        setContent {
            IrisTheme {
                RemoteScreen(
                    viewModel = viewModel,
                    localNetworkAllowed = localNetworkAllowed,
                    onAllowLocalNetwork = ::askForLocalNetwork,
                    whenUnlocked = ::whenUnlocked,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted it in the system settings while we were in the background.
        val allowed = hasLocalNetworkPermission()
        if (allowed && !localNetworkAllowed) viewModel.refresh()
        localNetworkAllowed = allowed
    }

    // While the app is open, a text field on the TV brings up the phone's keyboard. The media
    // controls start here too, as Android only lets an app start them while it's in front: after
    // a restart, they're back once the app has been opened.
    override fun onStart() {
        super.onStart()
        viewModel.watchKeyboard()
        MediaControlsService.sync(this)

    }

    // Nothing to touch or type while the app is in the background; the next drag reopens the touchpad.
    override fun onStop() {
        super.onStop()
        viewModel.closeTouchpad()
        viewModel.stopWatchingKeyboard()
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < ANDROID_17 ||
            checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun needsNotificationPermission(): Boolean =
        viewModel.mediaControls &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * Runs [action] once the phone is unlocked: straight away, or after asking to unlock. For what
     * shouldn't be open to anyone holding the phone, while the remote shows over the lock screen.
     */
    private fun whenUnlocked(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceLocked) {
            action()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            },
        )
    }

    /** Ask again if Android still allows it; otherwise open this app's settings page. */
    private fun askForLocalNetwork() {
        if (shouldShowRequestPermissionRationale(ACCESS_LOCAL_NETWORK)) {
            requestPermissions.launch(arrayOf(ACCESS_LOCAL_NETWORK))
        } else {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
    }

    // While the app is open, the phone's volume buttons change the TV's volume.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val command = volumeCommand(keyCode)
        if (command == null || !viewModel.state.value.canControl) {
            return super.onKeyDown(keyCode, event)
        }
        if (event.repeatCount == 0) {
            volumeKeyHeld = true
            viewModel.press(command)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeCommand(keyCode) == null || !volumeKeyHeld) {
            return super.onKeyUp(keyCode, event)
        }
        volumeKeyHeld = false
        viewModel.release()
        return true
    }

    private fun volumeCommand(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> "volume_up"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "volume_down"
        else -> null
    }

    companion object {
        /** Opens the remote the way the launcher does: back where it was, if it's open already. */
        fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
