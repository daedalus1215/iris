package io.github.daedalus1215.iris.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The Iris backend's HTTP API (see specs/m1-working-remote.md). */
interface IrisClient {
    suspend fun health(): Health

    suspend fun devices(): List<Device>

    suspend fun scan(): List<Device>

    suspend fun send(deviceId: String, command: String)
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

    override suspend fun send(deviceId: String, command: String) {
        execute("POST", "devices", deviceId, "commands", command)
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

    private suspend fun execute(method: String, vararg segments: String): String {
        val request = Request.Builder()
            .url(url(segments))
            .method(method, if (method == "POST") EMPTY_BODY else null)
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
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

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val json = Json { ignoreUnknownKeys = true }

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            // Scanning for Apple TVs takes the server about 5 seconds.
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
