package io.github.daedalus1215.iris.core

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class HttpIrisClientTest {
    private val server = MockWebServer()

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun stop() = server.close()

    private fun client(token: String? = null) = HttpIrisClient(server.url("/").toString(), token)

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build(),
    )

    @Test
    fun `parses the device list and ignores unknown fields`() = runTest {
        respond(DEVICES_JSON)

        assertEquals(listOf(BEDROOM), client().devices())
        assertEquals(listOf("api", "devices"), server.takeRequest().url.pathSegments)
    }

    @Test
    fun `sends a command for the device with the token`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        client(token = "s3cret").send("AA:BB:CC:00:00:01", "up")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            listOf("api", "devices", "AA:BB:CC:00:00:01", "commands", "up"),
            request.url.pathSegments,
        )
        assertEquals("Bearer s3cret", request.headers["Authorization"])
    }

    @Test
    fun `no token means no auth header`() = runTest {
        respond("""{"status":"ok","env":"dev","version":"0.1.0"}""")

        assertEquals("dev", client().health().env)
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `works without a trailing slash on the server address`() = runTest {
        respond(DEVICES_JSON)

        HttpIrisClient(server.url("/").toString().trimEnd('/')).devices()

        assertEquals(listOf("api", "devices"), server.takeRequest().url.pathSegments)
    }

    @Test
    fun `server errors carry the detail message and status`() = runTest {
        respond("""{"detail":"'Bedroom' isn't paired yet"}""", code = 409)

        val error = assertFailsWith<IrisException> { client().send("x", "up") }

        assertEquals("'Bedroom' isn't paired yet", error.message)
        assertEquals(409, error.status)
    }

    @Test
    fun `validation errors without a text detail get a generic message`() = runTest {
        respond("""{"detail":[{"msg":"bad pin"}]}""", code = 422)

        val error = assertFailsWith<IrisException> { client().devices() }

        assertEquals("Server error 422", error.message)
    }

    @Test
    fun `a garbled response is an IrisException`() = runTest {
        respond("<html>proxy error</html>")

        assertFailsWith<IrisException> { client().devices() }
    }

    @Test
    fun `an unreachable server is an IrisException`() = runTest {
        val error = assertFailsWith<IrisException> { HttpIrisClient("http://127.0.0.1:1").devices() }

        assertEquals("Can't reach the Iris server at http://127.0.0.1:1", error.message)
    }

    @Test
    fun `no server address yet is an IrisException`() = runTest {
        val error = assertFailsWith<IrisException> { HttpIrisClient("  ").devices() }

        assertEquals("No Iris server address set", error.message)
    }

    @Test
    fun `a bad server address is an IrisException`() = runTest {
        val error = assertFailsWith<IrisException> { HttpIrisClient("not a url").devices() }

        assertEquals("Not a valid server address: not a url", error.message)
    }

    @Test
    fun `starting pairing returns the session`() = runTest {
        respond("""{"session":"abc123"}""")

        assertEquals("abc123", client().startPairing("AA:BB:CC:00:00:01", PairingProtocol.COMPANION))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            listOf("api", "devices", "AA:BB:CC:00:00:01", "pairing", "companion"),
            request.url.pathSegments,
        )
    }

    @Test
    fun `finishing pairing sends the session and PIN as JSON`() = runTest {
        respond("""{"paired":true}""")

        client().finishPairing("AA:BB:CC:00:00:01", PairingProtocol.AIRPLAY, "abc123", "0042")

        val request = server.takeRequest()
        assertEquals(
            listOf("api", "devices", "AA:BB:CC:00:00:01", "pairing", "airplay", "pin"),
            request.url.pathSegments,
        )
        assertEquals("""{"session":"abc123","pin":"0042"}""", request.body?.utf8())
        assertEquals(true, request.headers["Content-Type"]?.startsWith("application/json"))
    }

    @Test
    fun `a long press sends its action as JSON`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        client().send("AA:BB:CC:00:00:01", "select", action = "hold")

        assertEquals("""{"action":"hold"}""", server.takeRequest().body?.utf8())
    }

    /** A server end of a WebSocket that records what arrives, greets, and may hang up. */
    private class SocketServer(
        private vararg val greetings: String,
        private val hangUp: Boolean = false,
    ) : WebSocketListener() {
        val received = LinkedBlockingQueue<String>()
        val closed = CountDownLatch(1)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            greetings.forEach { webSocket.send(it) }
            if (hangUp) webSocket.close(1011, "lost the connection to the Apple TV")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
        }

        // Finish the closing handshake: MockWebServer won't shut down around a half-closed socket.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.countDown()
        }
    }

    private fun Touchpad.closeAndWait(server: SocketServer) {
        close()
        assertTrue(server.closed.await(5, TimeUnit.SECONDS), "the touchpad socket didn't close")
    }

    /** Collects what a keyboard socket reports, in order. */
    private class Watcher : KeyboardWatcher {
        val events = LinkedBlockingQueue<Any>()

        override fun onState(state: KeyboardState) {
            events += state
        }

        override fun onError(error: IrisException) {
            events += error.message.orEmpty()
        }

        override fun onClosed() {
            events += "closed"
        }
    }

    private fun <T> LinkedBlockingQueue<T>.next(): T? = poll(5, TimeUnit.SECONDS)

    @Test
    fun `the touchpad streams events in order to the device's socket, with the token`() {
        val touchServer = SocketServer()
        server.enqueue(MockResponse.Builder().webSocketUpgrade(touchServer).build())

        val pad = client(token = "s3cret").openTouchpad("AA:BB:CC:00:00:01") { throw it }
        pad.send(TouchPhase.PRESS, 300, 500)
        pad.send(TouchPhase.MOVE, 450, 520)
        pad.send(TouchPhase.RELEASE, 700, 500)

        assertEquals("""{"phase":"press","x":300,"y":500}""", touchServer.received.next())
        assertEquals("""{"phase":"move","x":450,"y":520}""", touchServer.received.next())
        assertEquals("""{"phase":"release","x":700,"y":500}""", touchServer.received.next())
        val request = server.takeRequest()
        assertEquals(listOf("api", "devices", "AA:BB:CC:00:00:01", "touch"), request.url.pathSegments)
        assertEquals("Bearer s3cret", request.headers["Authorization"])
        pad.closeAndWait(touchServer)
        assertFalse(pad.send(TouchPhase.PRESS, 0, 0))
    }

    @Test
    fun `the server's touchpad errors reach onError`() {
        val touchServer = SocketServer("""{"detail":"'Den' isn't paired yet","status":409}""")
        server.enqueue(MockResponse.Builder().webSocketUpgrade(touchServer).build())
        val errors = LinkedBlockingQueue<IrisException>()

        val pad = client().openTouchpad("DEN") { errors += it }

        assertEquals("'Den' isn't paired yet", errors.next()?.message)
        pad.closeAndWait(touchServer)
    }

    @Test
    fun `a refused touchpad reports why, and stops taking events`() {
        respond("""{"detail":"missing or wrong token"}""", code = 401)
        val errors = LinkedBlockingQueue<IrisException>()

        val pad = client().openTouchpad("AA:BB:CC:00:00:01") { errors += it }

        val error = errors.next()
        assertEquals("missing or wrong token", error?.message)
        assertEquals(401, error?.status)
        assertFalse(pad.send(TouchPhase.PRESS, 0, 0))
    }

    @Test
    fun `the keyboard reports the text field and types into it, with the token`() {
        val keyboardServer = SocketServer("""{"focused":true,"text":"sta"}""")
        server.enqueue(MockResponse.Builder().webSocketUpgrade(keyboardServer).build())
        val watcher = Watcher()

        val keyboard = client(token = "s3cret").openKeyboard("AA:BB:CC:00:00:01", watcher)
        keyboard.setText("star wars")

        assertEquals(KeyboardState(focused = true, text = "sta"), watcher.events.next())
        assertEquals("""{"text":"star wars"}""", keyboardServer.received.next())
        val request = server.takeRequest()
        assertEquals(listOf("api", "devices", "AA:BB:CC:00:00:01", "keyboard"), request.url.pathSegments)
        assertEquals("Bearer s3cret", request.headers["Authorization"])
        keyboard.close()
        assertTrue(keyboardServer.closed.await(5, TimeUnit.SECONDS), "the keyboard socket didn't close")
        assertFalse(keyboard.setText("more"))
        assertNull(watcher.events.poll(200, TimeUnit.MILLISECONDS), "closing it here isn't reported")
    }

    @Test
    fun `the server's keyboard errors and hang-ups are reported`() {
        val keyboardServer = SocketServer(
            """{"detail":"'Den' isn't paired yet","status":409}""",
            hangUp = true,
        )
        server.enqueue(MockResponse.Builder().webSocketUpgrade(keyboardServer).build())
        val watcher = Watcher()

        client().openKeyboard("DEN", watcher)

        assertEquals("'Den' isn't paired yet", watcher.events.next())
        assertEquals("closed", watcher.events.next())
    }

    @Test
    fun `a keyboard that can't open reports why, then closes`() {
        respond("""{"detail":"missing or wrong token"}""", code = 401)
        val watcher = Watcher()

        client().openKeyboard("AA:BB:CC:00:00:01", watcher)

        assertEquals("missing or wrong token", watcher.events.next())
        assertEquals("closed", watcher.events.next())
    }
}
