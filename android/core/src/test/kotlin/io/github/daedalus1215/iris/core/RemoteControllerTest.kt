package io.github.daedalus1215.iris.core

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteControllerTest {
    private fun TestScope.loaded(client: FakeClient, selectedId: String? = null): RemoteController {
        val controller = RemoteController(client, this, selectedId)
        controller.refresh()
        advanceUntilIdle()
        return controller
    }

    @Test
    fun `refresh loads devices and the environment, and picks the only device`() = runTest {
        val state = loaded(FakeClient(env = "dev")).state.value

        assertEquals(listOf(BEDROOM), state.devices)
        assertEquals("dev", state.env)
        assertEquals(BEDROOM, state.selected)
        assertTrue(state.canControl)
    }

    @Test
    fun `keeps the saved choice when it's still listed`() = runTest {
        val client = FakeClient(devices = listOf(BEDROOM, DEN_UNPAIRED))

        assertEquals("DEN", loaded(client, selectedId = "DEN").state.value.selectedId)
        assertNull(loaded(client).state.value.selectedId)
    }

    @Test
    fun `sends commands to the selected device`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)

        controller.send("play_pause")
        advanceUntilIdle()

        assertEquals(listOf(BEDROOM.id to "play_pause"), client.sent)
    }

    @Test
    fun `ignores commands for an unpaired device`() = runTest {
        val client = FakeClient(devices = listOf(DEN_UNPAIRED))
        val controller = loaded(client)

        controller.send("up")
        controller.press("down")
        advanceUntilIdle()

        assertFalse(controller.state.value.canControl)
        assertEquals(emptyList(), client.sent)
    }

    @Test
    fun `a failed command shows its error until the next one works`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)

        client.failWith = IrisException("Apple TV unreachable")
        controller.send("up")
        advanceUntilIdle()
        assertEquals("Apple TV unreachable", controller.state.value.error)

        client.failWith = null
        controller.send("up")
        advanceUntilIdle()
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a failed load shows its error`() = runTest {
        val client = FakeClient().apply { failWith = IrisException("Can't reach the Iris server") }

        val state = loaded(client).state.value

        assertEquals("Can't reach the Iris server", state.error)
        assertFalse(state.loading)
    }

    @Test
    fun `holding a button repeats through the selected device`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)

        controller.press("right")
        advanceTimeBy(600)
        controller.release()
        advanceUntilIdle()

        assertEquals(List(3) { BEDROOM.id to "right" }, client.sent)
    }

    @Test
    fun `switching servers reloads from the new one`() = runTest {
        val controller = loaded(FakeClient(env = "local"))

        controller.useClient(FakeClient(devices = listOf(DEN_UNPAIRED), env = "prod"))
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("prod", state.env)
        assertEquals(listOf(DEN_UNPAIRED), state.devices)
    }
}
