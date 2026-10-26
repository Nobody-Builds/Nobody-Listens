package com.nobodybuilds.listens.data.jiosaavn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses JioSaavn's JSON. Text arrives HTML-escaped (`&quot;`), numbers arrive as
 * strings or numbers depending on the call, and images in the smallest size.
 */
object JioSaavnParsers {
    private val json = Json { ignoreUnknownKeys = true }

    /** (code, message) of an error reply, or null when the call worked. */
    fun errorOf(raw: String): Pair<String?, String?>? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        val error = root.obj("error") ?: return null
        return error.str("code") to error.str("msg")
    }

    /** Home feed: the launch modules in JioSaavn's order (trending, charts, new releases…). */
    fun parseLaunch(raw: String): List<JioSaavnShelf> {
        val root = json.parseToJsonElement(raw).jsonObject
        val modules = root.obj("modules") ?: return emptyList()
        return modules.entries
            .sortedBy { (_, module) -> (module as? JsonObject)?.str("position")?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { (key, module) ->
                val title = (module as? JsonObject)?.str("title")?.let(::decode).orEmpty()
                val items = root[key].let { it as? JsonObject }?.get("data") ?: root[key]
                shelf(title, items.asArray())
            }
    }

    fun parseSearchSongs(raw: String): List<JioSaavnTrack> =
        json.parseToJsonElement(raw).jsonObject["results"].asArray().mapNotNull { (it as? JsonObject)?.let(::parseTrack) }

    /** Album, playlist or artist search results. */
    fun parseSearchCollections(raw: String, kind: JioSaavnItemKind): List<JioSaavnCollection> =
        json.parseToJsonElement(raw).jsonObject["results"].asArray().mapNotNull { element ->
            (element as? JsonObject)?.let { parseCollection(it, kind) }
        }

    /** Song names, albums and artists for what is being typed. */
    fun parseSuggestions(raw: String): List<String> {
        val root = json.parseToJsonElement(raw).jsonObject
        return listOf("topquery", "songs", "albums", "artists")
            .flatMap { key -> root.obj(key)?.get("data").asArray() }
            .mapNotNull { (it as? JsonObject)?.str("title")?.let(::decode)?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase() }
    }

    fun parseSongs(raw: String): List<JioSaavnTrack> =
        songObjects(raw).mapNotNull(::parseTrack)

    /** Streaming details of each song in a `song.getDetails` reply, by song id. */
    fun parseMedia(raw: String): Map<String, JioSaavnMedia> =
        songObjects(raw).mapNotNull { song ->
            val id = song.str("id") ?: return@mapNotNull null
            val info = song.obj("more_info") ?: return@mapNotNull null
            val plain = info.str("encrypted_media_url").orEmpty()
            id to JioSaavnMedia(
                encryptedUrl = plain,
                has320 = info.str("320kbps") == "true",
                drmOnly = plain.isBlank() && !info.str("encrypted_drm_media_url").isNullOrBlank(),
            )
        }.toMap()

    fun parseAlbum(raw: String): JioSaavnPage {
        val root = json.parseToJsonElement(raw).jsonObject
        val title = root.str("title")?.let(::decode)
        val cover = artwork(root.str("image"))
        val tracks = root["list"].asArray().mapNotNull { (it as? JsonObject)?.let(::parseTrack) }.map { track ->
            track.copy(album = track.album.ifBlank { title.orEmpty() }, thumbnailUrl = track.thumbnailUrl ?: cover)
        }
        return JioSaavnPage(shelves = listOf(JioSaavnShelf(title = "", tracks = tracks)), title = title)
    }

    fun parsePlaylist(raw: String): JioSaavnPage {
        val root = json.parseToJsonElement(raw).jsonObject
        val tracks = root["list"].asArray().mapNotNull { (it as? JsonObject)?.let(::parseTrack) }
        return JioSaavnPage(
            shelves = listOf(JioSaavnShelf(title = "", tracks = tracks)),
            title = root.str("title")?.let(::decode),
            totalSongs = root.str("list_count")?.toIntOrNull(),
        )
    }

    /** Artist page: top songs, albums, singles and the artist's playlists. */
    fun parseArtist(raw: String): JioSaavnPage {
        val root = json.parseToJsonElement(raw).jsonObject
        val shelves = listOfNotNull(
            shelf("Top songs", root["topSongs"].asArray()),
            shelf("Albums", root["topAlbums"].asArray()),
            shelf("Singles", root["singles"].asArray()),
            shelf("Playlists", root["dedicated_artist_playlist"].asArray()),
            shelf("Featured in", root["featured_artist_playlist"].asArray()),
            shelf("Similar artists", root["similarArtists"].asArray()),
        )
        return JioSaavnPage(shelves = shelves, title = root.str("name")?.let(::decode))
    }

    fun parseStationId(raw: String): String? = (json.parseToJsonElement(raw) as? JsonObject)?.str("stationid")

    /** Radio songs come keyed "0", "1", … each wrapping a song. */
    fun parseStationSongs(raw: String): List<JioSaavnTrack> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        return root.entries
            .filter { (key, _) -> key.toIntOrNull() != null }
            .sortedBy { (key, _) -> key.toInt() }
            .mapNotNull { (_, value) -> (value as? JsonObject)?.obj("song")?.let(::parseTrack) }
    }

    fun parseLyrics(raw: String): JioSaavnLyrics? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val text = root.str("lyrics")?.takeIf { it.isNotBlank() } ?: return null
        return JioSaavnLyrics(
            text = decode(text.replace(BREAK_REGEX, "\n")).trim(),
            copyright = root.str("lyrics_copyright")?.let(::decode)?.takeIf { it.isNotBlank() },
        )
    }

    /** The signed stream URL from `song.generateAuthToken`, with the bitrate its file name carries. */
    fun parseAuthToken(raw: String, songId: String): JioSaavnStream? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val url = root.str("auth_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        return JioSaavnStream(id = songId, url = url, bitrate = bitrateOf(url) ?: 0)
    }

    /** 320000 for `…_320.mp4`, the way JioSaavn names its files. */
    fun bitrateOf(url: String): Int? =
        BITRATE_REGEX.find(url.substringBefore("?"))?.groupValues?.get(1)?.toIntOrNull()?.times(1000)

    /** The signed-in user's name, or null when the reply carries none. */
    fun parseAccountName(raw: String): String? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val user = root.obj("data") ?: root
        val name = listOfNotNull(user.str("firstname"), user.str("lastname")).joinToString(" ").trim()
        return name.ifBlank { user.str("username") ?: user.str("email") }?.let(::decode)?.takeIf { it.isNotBlank() }
    }

    /** Ids saved in the account library, by kind ("song", "album", "playlist"). */
    fun parseLibraryIds(raw: String): Map<String, List<String>> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
        val source = root.obj("data") ?: root
        return source.mapValues { (_, value) ->
            value.asArray().mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> item.str("id")
                    else -> null
                }
            }
        }.filterValues { it.isNotEmpty() }
    }

    /** Every playlist or album object anywhere in a reply, for the account library's varying shapes. */
    fun parseAnyCollections(raw: String, kind: JioSaavnItemKind): List<JioSaavnCollection> {
        val type = kind.name.lowercase()
        val found = mutableListOf<JsonObject>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> if (element.str("type") == type && element.str("id") != null) found += element
                    else element.values.forEach(::walk)
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(json.parseToJsonElement(raw))
        return found.mapNotNull { parseCollection(it, kind) }.distinctBy { it.id }
    }

    /** jiosaavn.com images come at 150x150 or 50x50; the same file exists at 500x500. */
    fun artwork(url: String?, size: Int = 500): String? =
        url?.takeIf { it.isNotBlank() }
            ?.replace(SIZE_REGEX, "${size}x$size")
            ?.replace("http://", "https://")

    /** Decodes the HTML entities JioSaavn leaves in titles and lyrics. */
    fun decode(text: String): String =
        ENTITY_REGEX.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)?.let(::codePoint)
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED_ENTITIES[entity]
            } ?: match.value
        }

    private fun codePoint(value: Int): String? =
        runCatching { String(Character.toChars(value)) }.getOrNull()

    private fun songObjects(raw: String): List<JsonObject> {
        val root = json.parseToJsonElement(raw)
        val songs = (root as? JsonObject)?.get("songs") ?: root
        return when (songs) {
            is JsonArray -> songs.mapNotNull { it as? JsonObject }
            // Keyed by id when asked for several, or a single song for some link lookups.
            is JsonObject -> if (songs.str("type") == "song") listOf(songs) else songs.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
    }

    /** Songs and collections of a list, as one shelf; null when nothing in it can be shown. */
    private fun shelf(title: String, items: List<JsonElement>): JioSaavnShelf? {
        val tracks = mutableListOf<JioSaavnTrack>()
        val collections = mutableListOf<JioSaavnCollection>()
        items.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            when (item.str("type")) {
                "song" -> parseTrack(item)?.let(tracks::add)
                "album" -> parseCollection(item, JioSaavnItemKind.ALBUM)?.let(collections::add)
                "playlist" -> parseCollection(item, JioSaavnItemKind.PLAYLIST)?.let(collections::add)
                "artist" -> parseCollection(item, JioSaavnItemKind.ARTIST)?.let(collections::add)
            }
        }
        if (tracks.isEmpty() && collections.isEmpty()) return null
        return JioSaavnShelf(title = title, tracks = tracks, playlists = collections)
    }

    private fun parseTrack(item: JsonObject): JioSaavnTrack? {
        val id = item.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val title = item.str("title")?.let(::decode)?.takeIf { it.isNotBlank() }
            ?: item.str("song")?.let(::decode)
            ?: return null
        val info = item.obj("more_info")
        val primary = info?.obj("artistMap")?.get("primary_artists").asArray()
            .mapNotNull { (it as? JsonObject)?.str("name")?.let(::decode) }
        val artist = primary.joinToString(", ").ifBlank {
            // Without an artist map the subtitle reads "Artist 1, Artist 2 - Album".
            item.str("subtitle")?.let(::decode)?.substringBeforeLast(" - ").orEmpty()
        }
        return JioSaavnTrack(
            id = id,
            title = title,
            artist = artist,
            album = info?.str("album")?.let(::decode).orEmpty(),
            thumbnailUrl = artwork(item.str("image")),
            durationMs = (info?.str("duration")?.toLongOrNull() ?: 0L) * 1000L,
            albumId = info?.str("album_id")?.takeIf { it.isNotBlank() },
            year = item.str("year").orEmpty(),
        )
    }

    private fun parseCollection(item: JsonObject, kind: JioSaavnItemKind): JioSaavnCollection? {
        val id = item.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val title = (item.str("title") ?: item.str("name"))?.let(::decode)?.takeIf { it.isNotBlank() } ?: return null
        val info = item.obj("more_info")
        val subtitle = item.str("subtitle")?.let(::decode)?.takeIf { it.isNotBlank() }
            ?: info?.str("song_count")?.takeIf { it.isNotBlank() }?.let { "$it songs" }
            ?: item.str("description")?.let(::decode)
            ?: when (kind) {
                JioSaavnItemKind.ALBUM -> "Album"
                JioSaavnItemKind.ARTIST -> "Artist"
                JioSaavnItemKind.PLAYLIST -> "Playlist"
            }
        return JioSaavnCollection(
            id = id,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = artwork(item.str("image")),
            kind = kind,
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    /** Strings and numbers both, since JioSaavn sends counts either way. */
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()

    // Not \b: artist images end "_150x150.jpg", and "_" counts as a word character.
    private val SIZE_REGEX = Regex("""(?<!\d)(?:50x50|150x150)(?!\d)""")
    private val BITRATE_REGEX = Regex("""_(\d{2,3})\.mp4$""")
    private val BREAK_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val ENTITY_REGEX = Regex("""&(#x?[0-9A-Fa-f]+|[a-zA-Z]+);""")
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
    )
}
