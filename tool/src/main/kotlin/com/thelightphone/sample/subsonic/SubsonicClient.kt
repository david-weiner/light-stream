package com.thelightphone.sample.subsonic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.thelightphone.sample.library.Album
import com.thelightphone.sample.library.Artist
import com.thelightphone.sample.library.Track
import com.thelightphone.sample.library.TrackSource

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

    suspend fun getArtists(): Result<List<Artist>> = withStoredCredentials { username, password ->
        api.getArtists(username, password).map { artists ->
            artists.map { Artist(id = it.id, name = it.name) }
        }
    }

    suspend fun getAlbums(artistId: String): Result<List<Album>> = withStoredCredentials { username, password ->
        api.getArtist(username, password, artistId).map { artist ->
            artist.album.map { Album(id = it.id, title = it.name, artist = it.artist) }
        }
    }

    suspend fun getTracks(albumId: String): Result<List<Track>> = withStoredCredentials { username, password ->
        api.getAlbum(username, password, albumId).map { album ->
            album.song.map {
                Track(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    albumId = it.albumId.ifEmpty { albumId },
                    trackNumber = it.track,
                    durationSeconds = it.duration,
                    streamUrl = api.streamUrl(username, password, it.id),
                    source = TrackSource.BANDCAMP,
                )
            }
        }
    }

    fun close() {
        api.close()
    }

    private suspend fun <T> withStoredCredentials(block: suspend (String, String) -> Result<T>): Result<T> {
        val credentials = credentialStore.load()
            ?: return Result.failure(IllegalStateException("Not logged in."))
        return block(credentials.username, credentials.password)
    }
}

sealed class SubsonicClientLoginResult {
    data object NoStoredLogin : SubsonicClientLoginResult()
    data class Success(val username: String) : SubsonicClientLoginResult()
}
