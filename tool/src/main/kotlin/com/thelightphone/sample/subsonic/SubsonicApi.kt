package com.thelightphone.sample.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
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

// Spike diagnostic (spike/bandcamp-500-http-stack): widened these three from `private` to
// `internal` and gave authQuery/randomSalt/md5Hex below the same treatment, purely so
// SubsonicSpikeHarness can reuse this exact salt/token/URL logic instead of duplicating it.
// Revert to `private` if this branch's diagnostic code is ever kept past the spike.
internal const val SUBSONIC_BASE_URL = "https://bandcamp.com/api/subsonic/rest"
internal const val SUBSONIC_API_VERSION = "1.16.1"
internal const val SUBSONIC_CLIENT_NAME = "light-stream"

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

internal class SubsonicRequestFailedException(message: String) : Exception(message)

@Serializable
internal data class SubsonicArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
)

@Serializable
internal data class SubsonicArtistIndexDto(
    val artist: List<SubsonicArtistDto> = emptyList(),
)

@Serializable
internal data class SubsonicArtistsContainerDto(
    val index: List<SubsonicArtistIndexDto> = emptyList(),
)

@Serializable
internal data class SubsonicArtistsResponseBody(
    val status: String,
    val error: SubsonicError? = null,
    val artists: SubsonicArtistsContainerDto? = null,
)

@Serializable
internal data class SubsonicArtistsResponseWrapper(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicArtistsResponseBody,
)

@Serializable
internal data class SubsonicAlbumDto(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String = "",
    val coverArt: String? = null,
    val songCount: Int = 0,
)

@Serializable
internal data class SubsonicArtistDetailDto(
    val id: String,
    val name: String,
    val album: List<SubsonicAlbumDto> = emptyList(),
)

@Serializable
internal data class SubsonicArtistResponseBody(
    val status: String,
    val error: SubsonicError? = null,
    val artist: SubsonicArtistDetailDto? = null,
)

@Serializable
internal data class SubsonicArtistResponseWrapper(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicArtistResponseBody,
)

@Serializable
internal data class SubsonicSongDto(
    val id: String,
    val title: String,
    val artist: String = "",
    val albumId: String = "",
    val track: Int? = null,
    val duration: Int = 0,
)

@Serializable
internal data class SubsonicAlbumDetailDto(
    val id: String,
    val name: String,
    val artist: String = "",
    val song: List<SubsonicSongDto> = emptyList(),
)

@Serializable
internal data class SubsonicAlbumResponseBody(
    val status: String,
    val error: SubsonicError? = null,
    val album: SubsonicAlbumDetailDto? = null,
)

@Serializable
internal data class SubsonicAlbumResponseWrapper(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicAlbumResponseBody,
)

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
        val response = client.get("$SUBSONIC_BASE_URL/ping?${authQuery(username, password)}")
        ensureSuccess(response)

        // Bandcamp's Subsonic endpoint wraps the payload in a top-level "subsonic-response" key.
        val wrapped: SubsonicPingResponseWrapper = response.body()
        ensureOk(wrapped.subsonicResponse.status, wrapped.subsonicResponse.error)
    }

    suspend fun getArtists(username: String, password: String): Result<List<SubsonicArtistDto>> = runCatching {
        val response = client.get("$SUBSONIC_BASE_URL/getArtists?${authQuery(username, password)}")
        ensureSuccess(response)

        val wrapped: SubsonicArtistsResponseWrapper = response.body()
        val body = wrapped.subsonicResponse
        ensureOk(body.status, body.error)
        body.artists?.index.orEmpty().flatMap { it.artist }
    }

    suspend fun getArtist(username: String, password: String, artistId: String): Result<SubsonicArtistDetailDto> = runCatching {
        val response = client.get(
            "$SUBSONIC_BASE_URL/getArtist?${authQuery(username, password)}&id=${URLEncoder.encode(artistId, UTF_8.name())}",
        )
        ensureSuccess(response)

        val wrapped: SubsonicArtistResponseWrapper = response.body()
        val body = wrapped.subsonicResponse
        ensureOk(body.status, body.error)
        body.artist ?: throw SubsonicRequestFailedException("Artist not found.")
    }

    suspend fun getAlbum(username: String, password: String, albumId: String): Result<SubsonicAlbumDetailDto> = runCatching {
        val response = client.get(
            "$SUBSONIC_BASE_URL/getAlbum?${authQuery(username, password)}&id=${URLEncoder.encode(albumId, UTF_8.name())}",
        )
        ensureSuccess(response)

        val wrapped: SubsonicAlbumResponseWrapper = response.body()
        val body = wrapped.subsonicResponse
        ensureOk(body.status, body.error)
        body.album ?: throw SubsonicRequestFailedException("Album not found.")
    }

    fun streamUrl(username: String, password: String, trackId: String): String =
        "$SUBSONIC_BASE_URL/stream?${authQuery(username, password)}&id=${URLEncoder.encode(trackId, UTF_8.name())}"

    fun close() {
        client.close()
    }

    internal fun authQuery(username: String, password: String, clientName: String = SUBSONIC_CLIENT_NAME): String {
        val salt = randomSalt()
        val token = md5Hex(password + salt)
        return "u=${URLEncoder.encode(username, UTF_8.name())}" +
            "&t=$token" +
            "&s=$salt" +
            "&v=$SUBSONIC_API_VERSION" +
            "&c=$clientName" +
            "&f=json"
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(500)
            val headers = response.headers.entries()
                .joinToString("; ") { (name, values) -> "$name=${values.joinToString(",")}" }
            throw SubsonicRequestFailedException(
                "Subsonic HTTP ${response.status.value}: $body [headers: $headers]",
            )
        }
    }

    private fun ensureOk(status: String, error: SubsonicError?) {
        if (status != "ok") {
            throw SubsonicRequestFailedException(error?.message ?: "Subsonic request failed.")
        }
    }
}

internal fun randomSalt(length: Int = 12): String {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }.take(length)
}

internal fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
