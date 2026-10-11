package io.github.daedalus1215.iris.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatingPressTest {
    @Test
    fun `a quick tap sends once`() = runTest {
        val sent = mutableListOf<Long>()
        val press = RepeatingPress(this) { sent += currentTime }

        press.press("select")
        press.release()
        advanceUntilIdle()

        assertEquals(listOf(0L), sent)
    }

    @Test
    fun `holding repeats after a pause until release`() = runTest {
        val sent = mutableListOf<Long>()
        val press = RepeatingPress(this) { sent += currentTime }

        press.press("up")
        advanceTimeBy(1010)
        press.release()
        advanceUntilIdle()

        assertEquals(listOf(0L, 400L, 550L, 700L, 850L, 1000L), sent)
    }

    @Test
    fun `slow sends never overlap`() = runTest {
        var inFlight = 0
        var maxInFlight = 0
        val press = RepeatingPress(this) {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(300)
            inFlight--
        }

        press.press("up")
        advanceTimeBy(2000)
        press.release()
        advanceUntilIdle()

        assertEquals(1, maxInFlight)
    }

    @Test
    fun `pressing another button stops the first`() = runTest {
        val sent = mutableListOf<String>()
        val press = RepeatingPress(this) { sent += it }

        press.press("up")
        advanceTimeBy(500)
        press.press("down")
        advanceTimeBy(500)
        press.release()
        advanceUntilIdle()

        assertEquals(listOf("up", "up", "down", "down"), sent)
    }
}
