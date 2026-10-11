package io.github.daedalus1215.iris

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.daedalus1215.iris.core.HttpIrisClient
import io.github.daedalus1215.iris.core.PairingProtocol
import io.github.daedalus1215.iris.core.RemoteController
import io.github.daedalus1215.iris.core.RemoteState
import io.github.daedalus1215.iris.core.TouchPhase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val controller = RemoteController(
        HttpIrisClient(settings.serverUrl, settings.token),
        viewModelScope,
        settings.selectedDeviceId,
    )

    val state: StateFlow<RemoteState> = controller.state
    val serverUrl: String get() = settings.serverUrl
    val token: String get() = settings.token
    var arrowButtons by mutableStateOf(settings.arrowButtons)
        private set

    init {
        viewModelScope.launch {
            state.map { it.selectedId }.distinctUntilChanged().collect { settings.selectedDeviceId = it }
        }
        if (settings.serverUrl.isNotBlank()) controller.refresh()
    }

    fun refresh() = controller.refresh()

    fun scan() = controller.scan()

    fun select(id: String) = controller.select(id)

    fun send(command: String, action: String? = null) = controller.send(command, action)

    fun press(command: String) = controller.press(command)

    fun release() = controller.release()

    fun touch(phase: TouchPhase, x: Int, y: Int) = controller.touch(phase, x, y)

    fun closeTouchpad() = controller.closeTouchpad()

    fun watchKeyboard() = controller.watchKeyboard()

    fun stopWatchingKeyboard() = controller.stopWatchingKeyboard()

    fun type(text: String) = controller.type(text)

    fun openPairing(deviceId: String) = controller.openPairing(deviceId)

    fun closePairing() = controller.closePairing()

    fun startPairing(protocol: PairingProtocol) = controller.startPairing(protocol)

    fun submitPin(pin: String) = controller.submitPin(pin)

    fun saveSettings(serverUrl: String, token: String, arrowButtons: Boolean) {
        settings.serverUrl = serverUrl.trim()
        settings.token = token.trim()
        settings.arrowButtons = arrowButtons
        this.arrowButtons = arrowButtons
        controller.useClient(HttpIrisClient(settings.serverUrl, settings.token))
    }

    override fun onCleared() {
        controller.closeTouchpad()
        controller.stopWatchingKeyboard()
    }
}
