package io.github.daedalus1215.iris.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hold-to-repeat, like a held key: one send, a pause, then steady repeats until release.
 * Each send finishes before the next starts, so a slow network can't build a backlog.
 */
class RepeatingPress(
    private val scope: CoroutineScope,
    private val initialDelay: Duration = 400.milliseconds,
    private val interval: Duration = 150.milliseconds,
    private val send: suspend (command: String) -> Unit,
) {
    private var job: Job? = null

    fun press(command: String) {
        job?.cancel()
        // Undispatched, so the first send starts right now and a quick release can't skip it.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { send(command) }
            delay(initialDelay)
            while (isActive) {
                send(command)
                delay(interval)
            }
        }
    }

    fun release() {
        job?.cancel()
        job = null
    }
}
