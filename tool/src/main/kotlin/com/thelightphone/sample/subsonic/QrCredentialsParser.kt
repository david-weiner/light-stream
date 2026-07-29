package com.thelightphone.sample.subsonic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class QrCredentialsPayload(val username: String, val password: String)

/**
 * Parses the QR payload produced by web/pair.html: plain JSON with "username" and
 * "password" keys, not the otpauth:// format the SDK's Authenticator example parses
 * from the same camera component — see docs/ARCHITECTURE.md.
 */
object QrCredentialsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): Result<SubsonicCredentials> = runCatching {
        val decoded = json.decodeFromString<QrCredentialsPayload>(payload)
        require(decoded.username.isNotBlank() && decoded.password.isNotBlank()) {
            "QR code is missing a username or password."
        }
        SubsonicCredentials(username = decoded.username, password = decoded.password)
    }
}
