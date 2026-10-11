package io.github.daedalus1215.iris.core

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackgroundRemoteTest {
    @Test
    fun `sends each press to the device picked at the time`() = runTest {
        val client = FakeClient()
        var picked: String? = BEDROOM.id
        val remote = BackgroundRemote(this, { client }, { picked })

        remote.send("play_pause")
        advanceUntilIdle()
        picked = "DEN"
        remote.send("next")
        advanceUntilIdle()

        assertEquals(listOf(BEDROOM.id to "play_pause", "DEN" to "next"), client.sent)
    }

    @Test
    fun `uses the server set at the time of the press`() = runTest {
        val first = FakeClient()
        val second = FakeClient()
        var client = first
        val remote = BackgroundRemote(this, { client }, { BEDROOM.id })

        remote.send("home")
        advanceUntilIdle()
        client = second
        remote.send("menu")
        advanceUntilIdle()

        assertEquals(listOf(BEDROOM.id to "home"), first.sent)
        assertEquals(listOf(BEDROOM.id to "menu"), second.sent)
    }

    @Test
    fun `keeps the last failure until a press gets through`() = runTest {
        val client = FakeClient().apply { failWith = IrisException("Can't reach the Iris server at http://iris") }
        val remote = BackgroundRemote(this, { client }, { BEDROOM.id })

        remote.send("next")
        advanceUntilIdle()
        assertEquals("Can't reach the Iris server at http://iris", remote.error.value)

        client.failWith = null
        remote.send("next")
        advanceUntilIdle()
        assertNull(remote.error.value)
    }

    @Test
    fun `asks for a device when none is picked`() = runTest {
        val client = FakeClient()
        val remote = BackgroundRemote(this, { client }, { null })

        remote.send("play_pause")
        advanceUntilIdle()

        assertEquals(emptyList(), client.sent)
        assertEquals("Pick an Apple TV in Iris first", remote.error.value)
    }

    @Test
    fun `clearing forgets the last failure`() = runTest {
        val remote = BackgroundRemote(this, { FakeClient() }, { null })

        remote.send("play_pause")
        advanceUntilIdle()
        remote.clearError()

        assertNull(remote.error.value)
    }
}
