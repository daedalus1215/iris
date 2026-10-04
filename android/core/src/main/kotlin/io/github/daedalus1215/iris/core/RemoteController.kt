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
    /** Set while the pairing screen is open. */
    val pairing: PairingState? = null,
) {
    val selected: Device? get() = devices.firstOrNull { it.id == selectedId }
    val canControl: Boolean get() = selected?.canControl == true
    val pairingDevice: Device? get() = pairing?.let { p -> devices.firstOrNull { it.id == p.deviceId } }
}

data class PairingState(
    val deviceId: String,
    /** The protocol being paired right now, if any. */
    val protocol: PairingProtocol? = null,
    /** Set once the PIN is showing on the TV; the PIN is sent with it. */
    val session: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val awaitingPin: Boolean get() = session != null
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

    fun openPairing(deviceId: String) {
        _state.update { it.copy(pairing = PairingState(deviceId)) }
    }

    fun closePairing() {
        _state.update { it.copy(pairing = null) }
    }

    /** Asks the Apple TV to show a PIN for [protocol]. */
    fun startPairing(protocol: PairingProtocol) {
        val pairing = state.value.pairing ?: return
        if (pairing.busy) return
        updatePairing { it.copy(protocol = protocol, session = null, busy = true, error = null) }
        scope.launch {
            try {
                val session = client.startPairing(pairing.deviceId, protocol)
                updatePairing { it.copy(session = session, busy = false) }
            } catch (e: IrisException) {
                updatePairing { it.copy(protocol = null, busy = false, error = e.message) }
            }
        }
    }

    /** Sends the PIN the TV is showing, then reloads so the device shows as paired. */
    fun submitPin(pin: String) {
        val pairing = state.value.pairing ?: return
        val protocol = pairing.protocol ?: return
        val session = pairing.session ?: return
        if (pairing.busy) return
        updatePairing { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                client.finishPairing(pairing.deviceId, protocol, session, pin)
                updatePairing { it.copy(protocol = null, session = null, busy = false) }
                refresh()
            } catch (e: IrisException) {
                // The server ends the session after a failed attempt, so the next try starts over.
                updatePairing { it.copy(protocol = null, session = null, busy = false, error = e.message) }
            }
        }
    }

    private fun updatePairing(change: (PairingState) -> PairingState) {
        _state.update { it.copy(pairing = it.pairing?.let(change)) }
    }

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
