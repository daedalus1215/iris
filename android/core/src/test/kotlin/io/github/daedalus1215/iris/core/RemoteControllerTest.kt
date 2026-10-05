package io.github.daedalus1215.iris.core

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    fun `a long press sends the hold action`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)

        controller.send("select", action = "hold")
        advanceUntilIdle()

        assertEquals(listOf(BEDROOM.id to "select (hold)"), client.sent)
    }

    @Test
    fun `a drag streams over one touchpad connection, kept between drags`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)

        controller.touch(TouchPhase.PRESS, 300, 500)
        controller.touch(TouchPhase.MOVE, 450, 500)
        controller.touch(TouchPhase.RELEASE, 600, 500)
        controller.touch(TouchPhase.PRESS, 500, 500)

        val pad = client.touchpads.single()
        assertEquals(BEDROOM.id, pad.deviceId)
        assertEquals(
            listOf(
                Triple(TouchPhase.PRESS, 300, 500),
                Triple(TouchPhase.MOVE, 450, 500),
                Triple(TouchPhase.RELEASE, 600, 500),
                Triple(TouchPhase.PRESS, 500, 500),
            ),
            pad.events,
        )
    }

    @Test
    fun `a dropped touchpad reopens, and a drag under way starts again with a press`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.touch(TouchPhase.PRESS, 300, 500)

        client.touchpads.single().closed = true
        controller.touch(TouchPhase.MOVE, 400, 500)

        assertEquals(2, client.touchpads.size)
        assertEquals(
            listOf(Triple(TouchPhase.PRESS, 400, 500), Triple(TouchPhase.MOVE, 400, 500)),
            client.touchpads[1].events,
        )
    }

    @Test
    fun `a release on a dropped touchpad doesn't open a new one`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.touch(TouchPhase.PRESS, 300, 500)

        client.touchpads.single().closed = true
        controller.touch(TouchPhase.RELEASE, 400, 500)

        assertEquals(1, client.touchpads.size)
    }

    @Test
    fun `switching devices moves the touchpad to the new one`() = runTest {
        val den = DEN_UNPAIRED.copy(paired = Paired(companion = true, airplay = false))
        val client = FakeClient(devices = listOf(BEDROOM, den))
        val controller = loaded(client, selectedId = BEDROOM.id)
        controller.touch(TouchPhase.PRESS, 500, 500)

        controller.select(den.id)
        controller.touch(TouchPhase.PRESS, 500, 500)

        assertEquals(listOf(BEDROOM.id, den.id), client.touchpads.map { it.deviceId })
        assertTrue(client.touchpads[0].closed)
    }

    @Test
    fun `touchpad errors show, and the next drag clears them`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.touch(TouchPhase.PRESS, 500, 500)

        client.touchpads.single().onError(IrisException("Apple TV unreachable"))
        assertEquals("Apple TV unreachable", controller.state.value.error)

        controller.touch(TouchPhase.PRESS, 500, 500)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `the touchpad is ignored for an unpaired device`() = runTest {
        val client = FakeClient(devices = listOf(DEN_UNPAIRED))
        val controller = loaded(client)

        controller.touch(TouchPhase.PRESS, 500, 500)

        assertEquals(emptyList(), client.touchpads)
    }

    @Test
    fun `the keyboard follows the selected device's text field once its devices load`() = runTest {
        val client = FakeClient()
        val controller = RemoteController(client, this)

        controller.watchKeyboard()
        assertEquals(emptyList(), client.keyboards)
        controller.refresh()
        advanceUntilIdle()
        val keyboard = client.keyboards.single()
        keyboard.watcher.onState(KeyboardState(focused = true, text = "sta"))
        runCurrent()

        assertEquals(BEDROOM.id, keyboard.deviceId)
        assertEquals(KeyboardState(focused = true, text = "sta"), controller.state.value.keyboard)
    }

    @Test
    fun `typing replaces the text on the TV`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.watchKeyboard()
        client.keyboards.single().watcher.onState(KeyboardState(focused = true, text = ""))
        runCurrent()

        controller.type("s")
        controller.type("st")

        assertEquals(listOf("s", "st"), client.keyboards.single().typed)
        assertEquals("st", controller.state.value.keyboard?.text)
    }

    @Test
    fun `a dropped keyboard reconnects, waiting longer each time until it works`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.watchKeyboard()

        client.keyboards[0].watcher.onClosed()
        runCurrent()
        assertTrue(client.keyboards[0].closed)
        advanceTimeBy(999)
        assertEquals(1, client.keyboards.size)
        advanceTimeBy(2)
        assertEquals(2, client.keyboards.size)

        client.keyboards[1].watcher.onClosed()
        runCurrent()
        advanceTimeBy(1999)
        assertEquals(2, client.keyboards.size)
        advanceTimeBy(2)
        assertEquals(3, client.keyboards.size)

        // A state means it works again, so the next drop waits the shortest time.
        client.keyboards[2].watcher.onState(KeyboardState(focused = false))
        client.keyboards[2].watcher.onClosed()
        runCurrent()
        assertNull(controller.state.value.keyboard)
        advanceTimeBy(1001)
        assertEquals(4, client.keyboards.size)
    }

    @Test
    fun `switching devices moves the keyboard, and the old one's news is ignored`() = runTest {
        val den = DEN_UNPAIRED.copy(paired = Paired(companion = true, airplay = false))
        val client = FakeClient(devices = listOf(BEDROOM, den))
        val controller = loaded(client, selectedId = BEDROOM.id)
        controller.watchKeyboard()
        val old = client.keyboards.single()

        controller.select(den.id)
        old.watcher.onState(KeyboardState(focused = true, text = "late"))
        old.watcher.onClosed()
        advanceUntilIdle()

        assertTrue(old.closed)
        assertEquals(listOf(BEDROOM.id, den.id), client.keyboards.map { it.deviceId })
        assertNull(controller.state.value.keyboard)
    }

    @Test
    fun `keyboard errors show only once it's watching`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.watchKeyboard()
        val keyboard = client.keyboards.single()

        keyboard.watcher.onError(IrisException("Apple TV unreachable"))
        runCurrent()
        assertNull(controller.state.value.error)

        keyboard.watcher.onState(KeyboardState(focused = true, text = ""))
        keyboard.watcher.onError(IrisException("Apple TV unreachable"))
        runCurrent()
        assertEquals("Apple TV unreachable", controller.state.value.error)
    }

    @Test
    fun `stopping closes the keyboard and forgets the text field`() = runTest {
        val client = FakeClient()
        val controller = loaded(client)
        controller.watchKeyboard()
        client.keyboards.single().watcher.onState(KeyboardState(focused = true, text = "x"))
        runCurrent()

        controller.stopWatchingKeyboard()
        controller.type("ignored")

        assertTrue(client.keyboards.single().closed)
        assertEquals(emptyList(), client.keyboards.single().typed)
        assertNull(controller.state.value.keyboard)
    }

    @Test
    fun `the keyboard isn't watched for an unpaired device`() = runTest {
        val client = FakeClient(devices = listOf(DEN_UNPAIRED))
        val controller = loaded(client)

        controller.watchKeyboard()

        assertEquals(emptyList(), client.keyboards)
    }

    @Test
    fun `switching servers reloads from the new one`() = runTest {
        val old = FakeClient(env = "local")
        val controller = loaded(old)

        controller.touch(TouchPhase.PRESS, 500, 500)
        controller.useClient(FakeClient(devices = listOf(DEN_UNPAIRED), env = "prod"))
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("prod", state.env)
        assertEquals(listOf(DEN_UNPAIRED), state.devices)
        assertTrue(old.touchpads.single().closed)
    }

    @Test
    fun `pairing shows a PIN, takes it, and the device becomes controllable`() = runTest {
        val controller = loaded(FakeClient(devices = listOf(DEN_UNPAIRED)))
        assertFalse(controller.state.value.canControl)

        controller.openPairing("DEN")
        controller.startPairing(PairingProtocol.COMPANION)
        advanceUntilIdle()
        val waiting = controller.state.value.pairing!!
        assertTrue(waiting.awaitingPin)
        assertEquals(PairingProtocol.COMPANION, waiting.protocol)

        controller.submitPin("1234")
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(PairingState("DEN"), state.pairing)
        assertTrue(state.pairingDevice!!.isPaired(PairingProtocol.COMPANION))
        assertFalse(state.pairingDevice!!.isPaired(PairingProtocol.AIRPLAY))
        assertTrue(state.canControl)
    }

    @Test
    fun `a wrong PIN shows why and starts over`() = runTest {
        val controller = loaded(FakeClient(devices = listOf(DEN_UNPAIRED)))

        controller.openPairing("DEN")
        controller.startPairing(PairingProtocol.COMPANION)
        advanceUntilIdle()
        controller.submitPin("9999")
        advanceUntilIdle()

        val pairing = controller.state.value.pairing!!
        assertEquals("pairing failed, check the PIN", pairing.error)
        assertFalse(pairing.awaitingPin)
        assertNull(pairing.protocol)
        assertFalse(controller.state.value.canControl)
    }

    @Test
    fun `a failure to start pairing is shown`() = runTest {
        val client = FakeClient(devices = listOf(DEN_UNPAIRED))
        val controller = loaded(client)

        client.failWith = IrisException("Apple TV unreachable")
        controller.openPairing("DEN")
        controller.startPairing(PairingProtocol.AIRPLAY)
        advanceUntilIdle()

        assertEquals("Apple TV unreachable", controller.state.value.pairing!!.error)
        assertFalse(controller.state.value.pairing!!.busy)
    }

    @Test
    fun `closing the pairing screen forgets it`() = runTest {
        val controller = loaded(FakeClient(devices = listOf(DEN_UNPAIRED)))

        controller.openPairing("DEN")
        controller.closePairing()

        assertNull(controller.state.value.pairing)
    }
}
