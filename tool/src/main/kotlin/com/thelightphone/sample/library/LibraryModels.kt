package com.thelightphone.sample.library

/**
 * Shapes from docs/DATA_SCHEMA.md. Both data sources (Subsonic now, local sync
 * later) translate their own native format into these so the rest of the app
 * never needs to know which source a track came from.
 */
enum class TrackSource { BANDCAMP, LOCAL }

data class Artist(
    val id: String,
    val name: String,
    val albumIds: List<String> = emptyList(),
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val trackIds: List<String> = emptyList(),
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val trackNumber: Int?,
    val durationSeconds: Int,
    val streamUrl: String,
    val source: TrackSource = TrackSource.BANDCAMP,
)
