package io.github.daedalus1215.iris.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The Iris backend's HTTP API (see specs/m1-working-remote.md). */
interface IrisClient {
    suspend fun health(): Health

    suspend fun devices(): List<Device>

    suspend fun scan(): List<Device>

    /** One press. [action] is "hold", "tap" or "double_tap", for buttons that take one. */
    suspend fun send(deviceId: String, command: String, action: String? = null)

    /**
     * Opens the device's touchpad: a connection that carries a finger's moves to the Apple TV.
     * Errors, from the server or the connection, go to [onError] on a background thread.
     * Throws [IrisException] straight away if the server address isn't valid.
     */
    fun openTouchpad(deviceId: String, onError: (IrisException) -> Unit): Touchpad

    /**
     * Watches the device's text field, and types into it through the returned [Keyboard].
     * [watcher]'s callbacks come on a background thread. Throws [IrisException] straight away
     * if the server address isn't valid.
     */
    fun openKeyboard(deviceId: String, watcher: KeyboardWatcher): Keyboard

    /** Starts pairing; the Apple TV shows a PIN. Returns the session to finish it with. */
    suspend fun startPairing(deviceId: String, protocol: PairingProtocol): String

    suspend fun finishPairing(deviceId: String, protocol: PairingProtocol, session: String, pin: String)
}

/** An open touchpad connection to one Apple TV. */
interface Touchpad {
    /** Queues one step of a finger; x and y run 0 to 1000. False once closed: open a new one. */
    fun send(phase: TouchPhase, x: Int, y: Int): Boolean

    fun close()
}

interface KeyboardWatcher {
    /** The text field's state: first when the connection opens, then whenever focus moves. */
    fun onState(state: KeyboardState)

    fun onError(error: IrisException)

    /** The connection closed by itself, e.g. the server lost the Apple TV. Open a new one. */
    fun onClosed()
}

/** An open keyboard connection to one Apple TV. */
interface Keyboard {
    /** Replaces what's typed in the focused text field. False once closed: open a new one. */
    fun setText(text: String): Boolean

    fun close()
}

/** Anything that went wrong talking to the server, with a message fit to show the user. */
class IrisException(message: String, val status: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

class HttpIrisClient(
    baseUrl: String,
    private val token: String? = null,
    private val http: OkHttpClient = defaultHttpClient,
) : IrisClient {
    private val base: HttpUrl? = baseUrl.trim().toHttpUrlOrNull()
    private val displayUrl = baseUrl.trim()

    override suspend fun health(): Health = decode(execute("GET", "health"))

    override suspend fun devices(): List<Device> = decode(execute("GET", "devices"))

    override suspend fun scan(): List<Device> = decode(execute("POST", "devices", "scan"))

    override suspend fun send(deviceId: String, command: String, action: String?) {
        execute(
            "POST", "devices", deviceId, "commands", command,
            jsonBody = action?.let { json.encodeToString(CommandBody(it)) },
        )
    }

    override fun openTouchpad(deviceId: String, onError: (IrisException) -> Unit): Touchpad =
        WebSocketTouchpad(http, authorized(url(arrayOf("devices", deviceId, "touch"))), displayUrl, onError)

    override fun openKeyboard(deviceId: String, watcher: KeyboardWatcher): Keyboard =
        WebSocketKeyboard(http, authorized(url(arrayOf("devices", deviceId, "keyboard"))), displayUrl, watcher)

    override suspend fun startPairing(deviceId: String, protocol: PairingProtocol): String =
        decode<PairingStarted>(execute("POST", "devices", deviceId, "pairing", protocol.apiName)).session

    override suspend fun finishPairing(
        deviceId: String,
        protocol: PairingProtocol,
        session: String,
        pin: String,
    ) {
        execute(
            "POST", "devices", deviceId, "pairing", protocol.apiName, "pin",
            jsonBody = json.encodeToString(PinBody(session, pin)),
        )
    }

    private fun url(segments: Array<out String>): HttpUrl {
        val base = base ?: throw IrisException(
            if (displayUrl.isEmpty()) "No Iris server address set" else "Not a valid server address: $displayUrl",
        )
        return base.newBuilder()
            .addPathSegment("api")
            .apply { segments.forEach { addPathSegment(it) } }
            .build()
    }

    private fun authorized(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
        .build()

    private suspend fun execute(method: String, vararg segments: String, jsonBody: String? = null): String {
        val body = jsonBody?.toRequestBody(JSON) ?: EMPTY_BODY
        val request = authorized(url(segments))
            .newBuilder()
            .method(method, if (method == "POST") body else null)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw IrisException(
                            errorDetail(body) ?: "Server error ${response.code}",
                            response.code,
                        )
                    }
                    body
                }
            } catch (e: IOException) {
                throw IrisException("Can't reach the Iris server at $displayUrl", cause = e)
            }
        }
    }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw IrisException("Unexpected response from the Iris server", cause = e)
        } catch (e: IllegalArgumentException) {
            throw IrisException("Unexpected response from the Iris server", cause = e)
        }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private const val NORMAL_CLOSURE = 1000

        /** FastAPI errors look like {"detail": "..."}; validation errors have a list instead. */
        private fun errorDetail(body: String): String? =
            try {
                val detail = json.parseToJsonElement(body).jsonObject["detail"]
                (detail as? JsonPrimitive)?.takeIf { it.isString }?.content
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }

        /** Why a WebSocket didn't open: no server, or the server said no. */
        private fun openFailure(what: String, displayUrl: String, response: Response?, t: Throwable) =
            when (val code = response?.code) {
                null -> IrisException("Can't reach the Iris server at $displayUrl", cause = t)
                401 -> IrisException("missing or wrong token", code)
                else -> IrisException("The server refused the $what ($code)", code)
            }

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            // Scanning for Apple TVs takes the server about 5 seconds.
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Touch events over a WebSocket, in order. OkHttp queues sends until the socket opens, so
     * the first touch needn't wait. A socket that drops after opening closes quietly, and the
     * next touch opens a new one; only a failure to open is reported, plus the server's errors.
     */
    private class WebSocketTouchpad(
        http: OkHttpClient,
        request: Request,
        private val displayUrl: String,
        private val onError: (IrisException) -> Unit,
    ) : Touchpad {
        @Volatile private var closed = false
        @Volatile private var opened = false
        @Volatile private var closedHere = false

        private val socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                }

                // The server only ever sends errors: {"detail": "...", "status": 409}.
                override fun onMessage(webSocket: WebSocket, text: String) {
                    onError(IrisException(errorDetail(text) ?: "Touchpad error"))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed = true
                    webSocket.close(NORMAL_CLOSURE, null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed = true
                    if (opened || closedHere) return
                    onError(openFailure("touchpad", displayUrl, response, t))
                }
            },
        )

        override fun send(phase: TouchPhase, x: Int, y: Int): Boolean =
            !closed && socket.send("""{"phase":"${phase.apiName}","x":$x,"y":$y}""")

        override fun close() {
            closedHere = true
            closed = true
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    /**
     * The keyboard over a WebSocket. The server sends the text field's state, and errors as
     * {"detail": ...}; [setText] sends {"text": ...}. OkHttp queues sends until the socket opens.
     */
    private class WebSocketKeyboard(
        http: OkHttpClient,
        request: Request,
        private val displayUrl: String,
        private val watcher: KeyboardWatcher,
    ) : Keyboard {
        @Volatile private var closed = false
        @Volatile private var opened = false
        @Volatile private var closedHere = false

        private val socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val detail = errorDetail(text)
                    if (detail != null) {
                        watcher.onError(IrisException(detail))
                        return
                    }
                    try {
                        watcher.onState(json.decodeFromString<KeyboardState>(text))
                    } catch (e: SerializationException) {
                        watcher.onError(IrisException("Unexpected keyboard message from the Iris server", cause = e))
                    } catch (e: IllegalArgumentException) {
                        watcher.onError(IrisException("Unexpected keyboard message from the Iris server", cause = e))
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed = true
                    webSocket.close(NORMAL_CLOSURE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closedHere) watcher.onClosed()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed = true
                    if (closedHere) return
                    if (!opened) watcher.onError(openFailure("keyboard", displayUrl, response, t))
                    watcher.onClosed()
                }
            },
        )

        override fun setText(text: String): Boolean =
            !closed && socket.send(json.encodeToString(KeyboardText(text)))

        override fun close() {
            closedHere = true
            closed = true
            socket.close(NORMAL_CLOSURE, null)
        }
    }
}

@Serializable
private data class KeyboardText(val text: String)

@Serializable
private data class CommandBody(val action: String)

@Serializable
private data class PairingStarted(val session: String)

@Serializable
private data class PinBody(val session: String, val pin: String)
