package io.github.daedalus1215.iris.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteState(
    val devices: List<Device> = emptyList(),
    val selectedId: String? = null,
    val env: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
) {
    val selected: Device? get() = devices.firstOrNull { it.id == selectedId }
    val canControl: Boolean get() = selected?.canControl == true
}

/** Everything the remote screen does, independent of Android so it can be unit tested. */
class RemoteController(
    private var client: IrisClient,
    private val scope: CoroutineScope,
    selectedId: String? = null,
) {
    private val _state = MutableStateFlow(RemoteState(selectedId = selectedId))
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val repeating = RepeatingPress(scope) { sendNow(it) }

    /** Switch servers (e.g. after the settings change) and reload. */
    fun useClient(client: IrisClient) {
        repeating.release()
        this.client = client
        _state.update { it.copy(devices = emptyList(), env = null, error = null) }
        refresh()
    }

    fun refresh() = load { it.devices() }

    fun scan() = load { it.scan() }

    fun select(id: String) {
        _state.update { it.copy(selectedId = id, error = null) }
    }

    /** One press, e.g. OK or play/pause. */
    fun send(command: String) {
        if (!state.value.canControl) return
        scope.launch { sendNow(command) }
    }

    /** Start of a hold, e.g. an arrow or volume; repeats until [release]. */
    fun press(command: String) {
        if (state.value.canControl) repeating.press(command)
    }

    fun release() = repeating.release()

    private fun load(fetch: suspend (IrisClient) -> List<Device>) {
        val client = client
        scope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val env = try {
                    client.health().env
                } catch (e: IrisException) {
                    null
                }
                val devices = fetch(client)
                _state.update {
                    it.copy(
                        devices = devices,
                        selectedId = pickDevice(it.selectedId, devices),
                        env = env ?: it.env,
                        error = null,
                        loading = false,
                    )
                }
            } catch (e: IrisException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    private suspend fun sendNow(command: String) {
        val device = state.value.selected ?: return
        try {
            client.send(device.id, command)
            if (state.value.error != null) _state.update { it.copy(error = null) }
        } catch (e: IrisException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    companion object {
        /** Keep the current choice if it's still listed; otherwise pick the only device. */
        fun pickDevice(current: String?, devices: List<Device>): String? = when {
            devices.any { it.id == current } -> current
            devices.size == 1 -> devices.single().id
            else -> current
        }
    }
}
