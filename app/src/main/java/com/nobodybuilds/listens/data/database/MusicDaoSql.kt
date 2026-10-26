package com.nobodybuilds.listens.data.database

// Shared projections and FTS helpers for the MusicDao query interfaces.

internal val SONG_SEARCH_QUERY_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")
internal const val EMPTY_SONG_SEARCH_MATCH_QUERY = "lmusicemptyquery*"

internal fun buildSongTitleSearchMatchQuery(query: String): String {
    val tokens = SONG_SEARCH_QUERY_TOKEN_REGEX
        .findAll(query)
        .map { it.value.trim() }
        .filter { it.isNotEmpty() }
        .take(6)
        .toList()

    if (tokens.isEmpty()) return EMPTY_SONG_SEARCH_MATCH_QUERY

    return tokens.joinToString(separator = " AND ") { "title:${it}*" }
}

internal fun buildSongSearchMatchQuery(query: String): String {
    val tokens = SONG_SEARCH_QUERY_TOKEN_REGEX
        .findAll(query)
        .map { it.value.trim() }
        .filter { it.isNotEmpty() }
        .take(6)
        .toList()

    if (tokens.isEmpty()) return EMPTY_SONG_SEARCH_MATCH_QUERY

    return tokens.joinToString(separator = " AND ") { "${it}*" }
}

internal const val SONG_DETAIL_PROJECTION = """
    songs.id AS id,
    songs.title AS title,
    songs.artist_name AS artist_name,
    songs.artist_id AS artist_id,
    songs.album_artist AS album_artist,
    songs.album_name AS album_name,
    songs.album_id AS album_id,
    songs.content_uri_string AS content_uri_string,
    songs.album_art_uri_string AS album_art_uri_string,
    songs.duration AS duration,
    songs.genre AS genre,
    songs.file_path AS file_path,
    songs.parent_directory_path AS parent_directory_path,
    songs.is_favorite AS is_favorite,
    COALESCE(song_lyrics.content, songs.lyrics) AS lyrics,
    songs.track_number AS track_number,
    songs.disc_number AS disc_number,
    songs.year AS year,
    songs.date_added AS date_added,
    songs.mime_type AS mime_type,
    songs.bitrate AS bitrate,
    songs.sample_rate AS sample_rate,
    songs.telegram_chat_id AS telegram_chat_id,
    songs.telegram_file_id AS telegram_file_id,
    songs.artists_json AS artists_json,
    songs.source_type AS source_type
"""

// Projection for list queries: excludes lyrics to prevent CursorWindow overflow (2MB limit)
// when loading large libraries. Lyrics are only needed for the Now Playing screen (getSongById).
internal const val SONG_LIST_PROJECTION = """
    id, title, artist_name, artist_id, album_artist, album_name, album_id,
    content_uri_string, album_art_uri_string, duration, genre, file_path,
    parent_directory_path, is_favorite, NULL AS lyrics, track_number, disc_number,
    year, date_added, mime_type, bitrate, sample_rate, telegram_chat_id,
    telegram_file_id, artists_json, source_type
"""

data class DeviceCapabilitySongRow(
    val filePath: String,
    val contentUriString: String,
    val mimeType: String?,
    val duration: Long,
    val bitrate: Int?,
    val sampleRate: Int?,
    val sourceType: Int
)
