package io.github.daedalus1215.iris.core

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
