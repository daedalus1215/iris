package io.github.daedalus1215.iris

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.daedalus1215.iris.core.HttpIrisClient
import io.github.daedalus1215.iris.core.RemoteController
import io.github.daedalus1215.iris.core.RemoteState
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

    init {
        viewModelScope.launch {
            state.map { it.selectedId }.distinctUntilChanged().collect { settings.selectedDeviceId = it }
        }
        if (settings.serverUrl.isNotBlank()) controller.refresh()
    }

    fun refresh() = controller.refresh()

    fun scan() = controller.scan()

    fun select(id: String) = controller.select(id)

    fun send(command: String) = controller.send(command)

    fun press(command: String) = controller.press(command)

    fun release() = controller.release()

    fun saveSettings(serverUrl: String, token: String) {
        settings.serverUrl = serverUrl.trim()
        settings.token = token.trim()
        controller.useClient(HttpIrisClient(settings.serverUrl, settings.token))
    }
}
