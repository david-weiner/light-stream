package com.thelightphone.sample.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.text.Charsets.UTF_8
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SUBSONIC_BASE_URL = "https://bandcamp.com/api/subsonic/rest"
private const val SUBSONIC_API_VERSION = "1.16.1"
private const val SUBSONIC_CLIENT_NAME = "light-stream"

@Serializable
internal data class SubsonicPingResponseWrapper(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicResponseBody,
)

@Serializable
internal data class SubsonicResponseBody(
    val status: String,
    val error: SubsonicError? = null,
)

@Serializable
internal data class SubsonicError(
    val code: Int,
    val message: String,
)

internal class SubsonicPingFailedException(message: String) : Exception(message)

internal class SubsonicApi {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun ping(username: String, password: String): Result<Unit> = runCatching {
        val salt = randomSalt()
        val token = md5Hex(password + salt)
        val response = client.get(
            "$SUBSONIC_BASE_URL/ping" +
                "?u=${URLEncoder.encode(username, UTF_8.name())}" +
                "&t=$token" +
                "&s=$salt" +
                "&v=$SUBSONIC_API_VERSION" +
                "&c=$SUBSONIC_CLIENT_NAME" +
                "&f=json",
        )

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            throw SubsonicPingFailedException("Subsonic HTTP ${response.status.value}: $body")
        }

        // Bandcamp's Subsonic endpoint wraps the payload in a top-level "subsonic-response" key.
        val wrapped: SubsonicPingResponseWrapper = response.body()
        val body = wrapped.subsonicResponse
        if (body.status != "ok") {
            throw SubsonicPingFailedException(
                body.error?.message ?: "Login failed.",
            )
        }
    }

    fun close() {
        client.close()
    }
}

private fun randomSalt(length: Int = 12): String {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }.take(length)
}

private fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
