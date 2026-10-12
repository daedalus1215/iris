package io.github.daedalus1215.iris.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presses from outside the app, like the media controls in the notification shade. Each press
 * goes to the device picked in the app, through the server set in the app, both read at the
 * time of the press, so they follow the app without being told.
 */
class BackgroundRemote(
    private val scope: CoroutineScope,
    private val client: () -> IrisClient,
    private val deviceId: () -> String?,
    private val volumeInterval: Duration = 150.milliseconds,
) {
    private val _error = MutableStateFlow<String?>(null)
    private var volume: Job? = null

    /** Why the last press failed, until one gets through. There's no screen to show it on otherwise. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** One press, e.g. play/pause. */
    fun send(command: String) {
        scope.launch { sendNow(command) }
    }

    /**
     * A volume key. A held key repeats faster than the TV keeps up with, so while one press is
     * on its way, and for [volumeInterval] after, more are dropped: the pace of the app's held
     * buttons, with no backlog.
     */
    fun volume(command: String) {
        if (volume?.isActive == true) return
        volume = scope.launch {
            sendNow(command)
            delay(volumeInterval)
        }
    }

    /** Forget the last failure, e.g. after the settings change. */
    fun clearError() {
        _error.value = null
    }

    private suspend fun sendNow(command: String) {
        val id = deviceId()
        if (id == null) {
            _error.value = "Pick an Apple TV in Iris first"
            return
        }
        try {
            client().send(id, command)
            _error.value = null
        } catch (e: IrisException) {
            _error.value = e.message
        }
    }
}
