package io.github.daedalus1215.iris

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.daedalus1215.iris.ui.RemoteScreen
import io.github.daedalus1215.iris.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()
    private var volumeKeyHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrisTheme {
                RemoteScreen(viewModel)
            }
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
}
