package com.thelightphone.sample.subsonic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Owns Bandcamp/Subsonic auth: credential storage and the login/ping check.
 * Callers only ever see success/failure, never the stored credentials themselves.
 */
class SubsonicClient(dataStore: DataStore<Preferences>) {
    private val credentialStore = SubsonicCredentialStore(dataStore)
    private val api = SubsonicApi()

    suspend fun checkStoredLogin(): SubsonicClientLoginResult {
        val credentials = credentialStore.load() ?: return SubsonicClientLoginResult.NoStoredLogin
        val result = api.ping(credentials.username, credentials.password)
        return if (result.isSuccess) {
            SubsonicClientLoginResult.Success(credentials.username)
        } else {
            SubsonicClientLoginResult.NoStoredLogin
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        val result = api.ping(username, password)
        if (result.isSuccess) {
            credentialStore.save(SubsonicCredentials(username, password))
        }
        return result
    }

    suspend fun logout() {
        credentialStore.clear()
    }

    fun close() {
        api.close()
    }
}

sealed class SubsonicClientLoginResult {
    data object NoStoredLogin : SubsonicClientLoginResult()
    data class Success(val username: String) : SubsonicClientLoginResult()
}
