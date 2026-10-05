package io.github.daedalus1215.iris.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class RemoteState(
    val devices: List<Device> = emptyList(),
    val selectedId: String? = null,
    val env: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
    /** Set while the pairing screen is open. */
    val pairing: PairingState? = null,
    /** The selected Apple TV's text field, while the keyboard is watched and connected. */
    val keyboard: KeyboardState? = null,
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

    private var touchpad: Touchpad? = null
    private var touchpadDeviceId: String? = null

    private var watchingKeyboard = false
    private var keyboard: Keyboard? = null
    private var keyboardDeviceId: String? = null
    /** Stands for the open keyboard connection; callbacks from any other are stale. */
    private var keyboardSession: Any? = null
    private var keyboardRetry: Job? = null
    private var keyboardFailures = 0

    /** Switch servers (e.g. after the settings change) and reload. */
    fun useClient(client: IrisClient) {
        repeating.release()
        closeTouchpad()
        closeKeyboard()
        this.client = client
        _state.update { it.copy(devices = emptyList(), env = null, error = null) }
        refresh()
    }

    fun refresh() = load { it.devices() }

    fun scan() = load { it.scan() }

    fun select(id: String) {
        _state.update { it.copy(selectedId = id, error = null) }
        syncKeyboard()
    }

    /** One press, e.g. OK or play/pause. [action] is "hold" for a long press. */
    fun send(command: String, action: String? = null) {
        if (!state.value.canControl) return
        scope.launch { sendNow(command, action) }
    }

    /** Start of a hold, e.g. an arrow or volume; repeats until [release]. */
    fun press(command: String) {
        if (state.value.canControl) repeating.press(command)
    }

    fun release() = repeating.release()

    /**
     * One step of a finger on the touchpad; x and y run 0 to 1000. The touchpad connection
     * opens on first use and stays open for the selected device.
     */
    fun touch(phase: TouchPhase, x: Int, y: Int) {
        val device = state.value.selected?.takeIf { it.canControl } ?: return
        // There's no reply to a touch, so a new drag clears the last error; a failure brings it back.
        if (phase == TouchPhase.PRESS && state.value.error != null) _state.update { it.copy(error = null) }
        if (touchpadDeviceId == device.id && touchpad?.send(phase, x, y) == true) return

        // No connection for this device yet, or it dropped. A drag already under way picks up
        // on the new one with a press; a lone release has nothing left to lift.
        closeTouchpad()
        if (phase == TouchPhase.RELEASE) return
        val pad = try {
            client.openTouchpad(device.id) { e -> _state.update { it.copy(error = e.message) } }
        } catch (e: IrisException) {
            _state.update { it.copy(error = e.message) }
            return
        }
        touchpad = pad
        touchpadDeviceId = device.id
        if (phase == TouchPhase.MOVE) pad.send(TouchPhase.PRESS, x, y)
        pad.send(phase, x, y)
    }

    fun closeTouchpad() {
        touchpad?.close()
        touchpad = null
        touchpadDeviceId = null
    }

    /**
     * Follows the selected device's text field from now on (see [RemoteState.keyboard]),
     * reconnecting with a growing pause when the connection drops. For while the app is in use.
     */
    fun watchKeyboard() {
        watchingKeyboard = true
        syncKeyboard()
    }

    fun stopWatchingKeyboard() {
        watchingKeyboard = false
        closeKeyboard()
    }

    /** Replaces what's typed in the Apple TV's focused text field. */
    fun type(text: String) {
        if (keyboard?.setText(text) == true) {
            _state.update { it.copy(keyboard = it.keyboard?.copy(text = text)) }
        }
    }

    /** Keeps the keyboard connection on the selected device, while watching. */
    private fun syncKeyboard() {
        val device = state.value.selected?.takeIf { it.canControl }
        if (!watchingKeyboard || device == null) {
            closeKeyboard()
        } else if (keyboardDeviceId != device.id) {
            keyboardFailures = 0
            openKeyboard(device.id)
        }
    }

    private fun openKeyboard(deviceId: String) {
        closeKeyboard()
        keyboardDeviceId = deviceId
        val session = Any()
        var watching = false
        // Callbacks come from the network; each one hops onto [scope] and is dropped if this
        // connection has been closed or replaced by then.
        fun later(block: () -> Unit) {
            scope.launch { if (keyboardSession === session) block() }
        }
        val watcher = object : KeyboardWatcher {
            override fun onState(state: KeyboardState) = later {
                watching = true
                keyboardFailures = 0
                _state.update { it.copy(keyboard = state) }
            }

            // Errors before the first state are about watching itself, and the retry covers those.
            override fun onError(error: IrisException) = later {
                if (watching) _state.update { it.copy(error = error.message) }
            }

            override fun onClosed() = later { retryKeyboard(deviceId) }
        }
        keyboardSession = session
        keyboard = try {
            client.openKeyboard(deviceId, watcher)
        } catch (e: IrisException) {
            retryKeyboard(deviceId)
            return
        }
    }

    /** Opens the keyboard again after 1 s, then twice as long each time, up to 30 s. */
    private fun retryKeyboard(deviceId: String) {
        keyboard?.close()
        keyboard = null
        keyboardSession = null
        _state.update { it.copy(keyboard = null) }
        val wait = minOf(KEYBOARD_RETRY_MAX, KEYBOARD_RETRY_MIN * (1 shl minOf(keyboardFailures, 5)))
        keyboardFailures++
        keyboardRetry = scope.launch {
            delay(wait)
            if (watchingKeyboard && keyboardDeviceId == deviceId) openKeyboard(deviceId)
        }
    }

    private fun closeKeyboard() {
        keyboardRetry?.cancel()
        keyboardRetry = null
        keyboard?.close()
        keyboard = null
        keyboardSession = null
        keyboardDeviceId = null
        if (state.value.keyboard != null) _state.update { it.copy(keyboard = null) }
    }

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
                syncKeyboard()
            } catch (e: IrisException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    private suspend fun sendNow(command: String, action: String? = null) {
        val device = state.value.selected ?: return
        try {
            client.send(device.id, command, action)
            if (state.value.error != null) _state.update { it.copy(error = null) }
        } catch (e: IrisException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    companion object {
        private val KEYBOARD_RETRY_MIN = 1.seconds
        private val KEYBOARD_RETRY_MAX = 30.seconds

        /** Keep the current choice if it's still listed; otherwise pick the only device. */
        fun pickDevice(current: String?, devices: List<Device>): String? = when {
            devices.any { it.id == current } -> current
            devices.size == 1 -> devices.single().id
            else -> current
        }
    }
}
