package com.nobodybuilds.listens.data.party

import kotlinx.serialization.Serializable

// Wire format for the party web page. Ids, titles and artists only — never file paths.

@Serializable
internal data class ErrorDto(val error: String, val message: String)

@Serializable
internal data class TokenDto(val token: String)

@Serializable
internal data class JoinDto(val guestId: String, val name: String, val key: String)

@Serializable
internal data class SongDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val requested: Boolean,
)

@Serializable
internal data class SearchDto(val items: List<SongDto>, val page: Int, val hasMore: Boolean, val total: Int)

@Serializable
internal data class AddDto(val result: String, val message: String)

@Serializable
internal data class RequestDto(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val addedBy: String,
    val votes: Int,
    val votedByMe: Boolean,
    val mine: Boolean,
    val staged: Boolean,
)

@Serializable
internal data class NowPlayingDto(
    val songId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
    val isPlaying: Boolean,
    val addedBy: String?,
    val hasArt: Boolean,
)

@Serializable
internal data class MeDto(val id: String, val name: String, val pending: Int, val cooldownMs: Long)

@Serializable
internal data class StateDto(
    val running: Boolean,
    val endsAt: Long,
    val now: Long,
    val guests: Int,
    val approveEach: Boolean,
    val nowPlaying: NowPlayingDto?,
    val upNext: List<RequestDto>,
    val awaiting: List<RequestDto>,
    val me: MeDto?,
)
