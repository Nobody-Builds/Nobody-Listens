package com.nobodybuilds.listens.data.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object YouTubeMusicParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePlayer(raw: String): YouTubeMusicPlayerPayload {
        val root = json.parseToJsonElement(raw).jsonObject
        val status = root.obj("playabilityStatus")
        val details = root.obj("videoDetails")
        val streaming = root.obj("streamingData")
        val lengthSeconds = details?.str("lengthSeconds")?.toLongOrNull()
        val formats = streaming?.get("adaptiveFormats").asArray().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            if (item.str("mimeType")?.startsWith("audio/") != true) return@mapNotNull null
            YouTubeMusicAudioFormat(
                itag = item.str("itag")?.toIntOrNull() ?: item.primitive("itag")?.intOrNull ?: 0,
                mimeType = item.str("mimeType").orEmpty(),
                bitrate = item.primitive("bitrate")?.intOrNull ?: item.str("bitrate")?.toIntOrNull() ?: 0,
                sampleRate = item.str("audioSampleRate")?.toIntOrNull(),
                contentLength = item.str("contentLength")?.toLongOrNull(),
                url = item.str("url"),
                signatureCipher = item.str("signatureCipher") ?: item.str("cipher"),
                approxDurationMs = item.str("approxDurationMs")?.toLongOrNull()
                    ?: item.primitive("approxDurationMs")?.longOrNull,
            )
        }
        return YouTubeMusicPlayerPayload(
            status = status?.str("status") ?: "ERROR",
            reason = status?.str("reason"),
            videoId = details?.str("videoId"),
            title = details?.str("title"),
            author = details?.str("author"),
            durationMs = lengthSeconds?.times(1000L),
            expiresInSeconds = streaming?.str("expiresInSeconds")?.toIntOrNull()
                ?: streaming?.primitive("expiresInSeconds")?.intOrNull,
            formats = formats,
            hlsManifestUrl = streaming?.str("hlsManifestUrl")?.takeIf { it.startsWith("https://") },
        )
    }

    /** Video rows from a plain youtube.com search. */
    fun parseVideoSearch(raw: String): List<YouTubeMusicTrack> {
        val renderers = mutableListOf<JsonObject>()
        collectVideoRenderers(json.parseToJsonElement(raw), renderers)
        return renderers.mapNotNull { video ->
            val videoId = video.str("videoId")?.takeIf { YouTubeMusicFormats.isValidVideoId(it) } ?: return@mapNotNull null
            val title = video["title"]?.let(::firstRun) ?: return@mapNotNull null
            YouTubeMusicTrack(
                videoId = videoId,
                title = title,
                artist = video["ownerText"]?.let(::firstRun).orEmpty(),
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = video.obj("lengthText")?.str("simpleText")?.let(::parseDuration) ?: 0L,
            )
        }.distinctBy { it.videoId }
    }

    private fun collectVideoRenderers(element: JsonElement, into: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                (element["videoRenderer"] as? JsonObject)?.let { into += it; return }
                element.values.forEach { collectVideoRenderers(it, into) }
            }
            is JsonArray -> element.forEach { collectVideoRenderers(it, into) }
            else -> Unit
        }
    }

    /** Radio / up-next rows from the `next` endpoint. */
    fun parseRadio(raw: String): YouTubeMusicRadioPage {
        val root = json.parseToJsonElement(raw)
        val rows = mutableListOf<JsonObject>()
        collectByKey(root, "playlistPanelVideoRenderer", rows)
        val tracks = rows.mapNotNull { row ->
            val videoId = row.str("videoId")?.takeIf { YouTubeMusicFormats.isValidVideoId(it) } ?: return@mapNotNull null
            val title = row["title"]?.let(::firstRun) ?: return@mapNotNull null
            val byline = row["longBylineText"]?.let { joinRuns(it) }.orEmpty()
            // "Artist • Album • 2022"
            val names = namesOnly(byline.split("•").map { it.trim() })
            val ids = browseIds(row["longBylineText"])
            YouTubeMusicTrack(
                videoId = videoId,
                title = title,
                artist = names.getOrNull(0).orEmpty(),
                album = names.getOrNull(1).orEmpty(),
                thumbnailUrl = largestThumbnail(row["thumbnail"] ?: row),
                durationMs = row["lengthText"]?.let(::firstRun)?.let(::parseDuration) ?: 0L,
                artistId = ids.firstOrNull { it.startsWith("UC") },
                albumId = ids.firstOrNull { it.startsWith("MPRE") },
            )
        }.distinctBy { it.videoId }
        val continuation = findObject(root, "nextRadioContinuationData")?.str("continuation")
            ?: findObject(root, "nextContinuationData")?.str("continuation")
        return YouTubeMusicRadioPage(
            tracks = tracks,
            continuation = continuation,
            lyricsBrowseId = browseIds(root).firstOrNull { it.startsWith("MPLY") },
        )
    }

    fun parseSuggestions(raw: String): List<String> {
        val items = mutableListOf<JsonObject>()
        collectByKey(json.parseToJsonElement(raw), "searchSuggestionRenderer", items)
        return items.mapNotNull { it["suggestion"]?.let(::firstRun) }.distinct()
    }

    /** Plain lyrics from a song's lyrics page (browse id MPLY…), or null when it has none. */
    fun parseLyrics(raw: String): YouTubeMusicLyrics? {
        val shelf = findObject(json.parseToJsonElement(raw), "musicDescriptionShelfRenderer") ?: return null
        val text = shelf["description"]?.let { joinRuns(it) }?.takeIf { it.isNotBlank() } ?: return null
        return YouTubeMusicLyrics(text = text, source = shelf["footer"]?.let { joinRuns(it) }?.takeIf { it.isNotBlank() })
    }

    /** Heading of an artist or album page. */
    fun parsePageTitle(raw: String): String? {
        val root = json.parseToJsonElement(raw)
        val header = findObject(root, "musicImmersiveHeaderRenderer")
            ?: findObject(root, "musicVisualHeaderRenderer")
            ?: findObject(root, "musicResponsiveHeaderRenderer")
            ?: findObject(root, "musicDetailHeaderRenderer")
            ?: return null
        return header["title"]?.let(::firstRun)
    }

    /** Album page header: title, main artist and the square cover. */
    fun parseAlbumHeader(raw: String): Triple<String?, String?, String?> {
        val header = findObject(json.parseToJsonElement(raw), "musicResponsiveHeaderRenderer")
            ?: findObject(json.parseToJsonElement(raw), "musicDetailHeaderRenderer")
            ?: return Triple(null, null, null)
        val title = header["title"]?.let(::firstRun)
        val artist = (header["straplineTextOne"] ?: header["subtitle"])?.let { joinRuns(it) }
            ?.split("•")?.map { it.trim() }?.firstOrNull { it.isNotBlank() && !isMetaText(it) }
        return Triple(title, artist, largestThumbnail(header["thumbnail"] ?: header))
    }

    private fun collectByKey(element: JsonElement, key: String, into: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                (element[key] as? JsonObject)?.let { into += it; return }
                element.values.forEach { collectByKey(it, key, into) }
            }
            is JsonArray -> element.forEach { collectByKey(it, key, into) }
            else -> Unit
        }
    }

    private fun findObject(element: JsonElement, key: String): JsonObject? = when (element) {
        is JsonObject -> (element[key] as? JsonObject) ?: element.values.firstNotNullOfOrNull { findObject(it, key) }
        is JsonArray -> element.firstNotNullOfOrNull { findObject(it, key) }
        else -> null
    }

    /** Every browseEndpoint id under [element], in order. */
    private fun browseIds(element: JsonElement?): List<String> {
        val ids = mutableListOf<String>()
        fun walkIds(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    node.obj("browseEndpoint")?.str("browseId")?.let(ids::add)
                    node.values.forEach(::walkIds)
                }
                is JsonArray -> node.forEach(::walkIds)
                else -> Unit
            }
        }
        element?.let(::walkIds)
        return ids.distinct()
    }

    fun parseBrowse(raw: String): YouTubeMusicBrowsePage {
        val root = json.parseToJsonElement(raw)
        val shelves = mutableListOf<YouTubeMusicShelf>()
        val looseTracks = mutableListOf<YouTubeMusicTrack>()
        val loosePlaylists = mutableListOf<YouTubeMusicPlaylist>()
        walk(root, null, shelves, looseTracks, loosePlaylists)
        // Search now returns one flat list of mixed rows: keep songs ahead of albums and playlists.
        // The same artist or song can appear twice in that list (top result and a later row).
        if (looseTracks.isNotEmpty()) shelves += YouTubeMusicShelf(title = "", tracks = looseTracks.distinctBy { it.videoId })
        if (loosePlaylists.isNotEmpty()) shelves += YouTubeMusicShelf(title = "", playlists = loosePlaylists.distinctBy { it.playlistId })
        return YouTubeMusicBrowsePage(
            shelves = shelves.filter { it.tracks.isNotEmpty() || it.playlists.isNotEmpty() },
            continuation = findContinuation(root),
        )
    }

    private fun walk(
        element: JsonElement,
        shelf: ShelfBuilder?,
        shelves: MutableList<YouTubeMusicShelf>,
        looseTracks: MutableList<YouTubeMusicTrack>,
        loosePlaylists: MutableList<YouTubeMusicPlaylist>,
    ) {
        when (element) {
            is JsonObject -> {
                element["musicCarouselShelfRenderer"]?.let { renderer ->
                    collectShelf(renderer, shelves, looseTracks, loosePlaylists)
                    return
                }
                element["musicShelfRenderer"]?.let { renderer ->
                    collectShelf(renderer, shelves, looseTracks, loosePlaylists)
                    return
                }
                element["musicPlaylistShelfRenderer"]?.let { renderer ->
                    collectShelf(renderer, shelves, looseTracks, loosePlaylists)
                    return
                }
                element["musicCardShelfRenderer"]?.let { renderer ->
                    collectTopResultCard(renderer.jsonObject, shelf, shelves, looseTracks, loosePlaylists)
                    return
                }
                element["musicResponsiveListItemRenderer"]?.let { renderer ->
                    val item = renderer.jsonObject
                    parseTrack(item)?.let { track ->
                        if (shelf != null) shelf.tracks += track else looseTracks += track
                    } ?: parsePlaylist(item)?.let { playlist ->
                        if (shelf != null) shelf.playlists += playlist else loosePlaylists += playlist
                    }
                    return
                }
                element["musicTwoRowItemRenderer"]?.let { renderer ->
                    val item = renderer.jsonObject
                    parseTrack(item)?.let { track ->
                        if (shelf != null) shelf.tracks += track else looseTracks += track
                    } ?: parsePlaylist(item)?.let { playlist ->
                        if (shelf != null) shelf.playlists += playlist else loosePlaylists += playlist
                    }
                    return
                }
                element.values.forEach { child ->
                    walk(child, shelf, shelves, looseTracks, loosePlaylists)
                }
            }
            is JsonArray -> element.forEach { child ->
                walk(child, shelf, shelves, looseTracks, loosePlaylists)
            }
            else -> Unit
        }
    }

    private fun parseTrack(item: JsonObject): YouTubeMusicTrack? {
        val videoId = findString(item, "videoId") ?: return null
        if (!YouTubeMusicFormats.isValidVideoId(videoId)) return null
        val columns = item["flexColumns"].asArray().mapNotNull { column ->
            firstRun(column)
        }.filter { it.isNotBlank() }
        val title = columns.firstOrNull()
            ?: item["title"]?.let(::firstRun)
            ?: return null
        // "Song • Arijit Singh • Album", "Sony Music • 88M views • 3:18": keep only names.
        val detailParts = columns.drop(1).flatMap { it.split("•") }.map { it.trim() }
        val rest = namesOnly(detailParts)
        val rawSubtitleParts = item["subtitle"]?.let { joinRuns(it) }.orEmpty()
            .split("•")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val subtitleParts = namesOnly(rawSubtitleParts)
        val durationMs = detailParts.firstNotNullOfOrNull { parseDuration(it) }
            ?: rawSubtitleParts.firstNotNullOfOrNull { parseDuration(it) }
            ?: 0L
        return YouTubeMusicTrack(
            videoId = videoId,
            title = title,
            artist = rest.getOrNull(0).orEmpty().ifBlank { subtitleParts.getOrNull(0).orEmpty() },
            album = rest.getOrNull(1).orEmpty().ifBlank { subtitleParts.getOrNull(1).orEmpty() },
            thumbnailUrl = largestThumbnail(item),
            durationMs = durationMs,
            artistId = browseIds(item["flexColumns"]).firstOrNull { it.startsWith("UC") },
            albumId = browseIds(item["flexColumns"]).firstOrNull { it.startsWith("MPRE") },
        )
    }

    /** Search's top-result card. Its song rows leave out the artist when the card is that artist's. */
    private fun collectTopResultCard(
        card: JsonObject,
        shelf: ShelfBuilder?,
        shelves: MutableList<YouTubeMusicShelf>,
        looseTracks: MutableList<YouTubeMusicTrack>,
        loosePlaylists: MutableList<YouTubeMusicPlaylist>,
    ) {
        val tracks = shelf?.tracks ?: looseTracks
        val start = tracks.size
        card.values.forEach { child -> walk(child, shelf, shelves, looseTracks, loosePlaylists) }
        val isArtistCard = card["subtitle"]?.let { joinRuns(it) }.orEmpty().startsWith("Artist")
        val artist = card["title"]?.let(::firstRun)?.takeIf { isArtistCard } ?: return
        for (i in start until tracks.size) {
            if (tracks[i].artist.isBlank()) tracks[i] = tracks[i].copy(artist = artist)
        }
    }

    private fun collectShelf(
        renderer: JsonElement,
        shelves: MutableList<YouTubeMusicShelf>,
        looseTracks: MutableList<YouTubeMusicTrack>,
        loosePlaylists: MutableList<YouTubeMusicPlaylist>,
    ) {
        val builder = ShelfBuilder(shelfTitle(renderer))
        val contents = renderer.jsonObject["contents"] ?: renderer.jsonObject["items"]
        contents.asArray().forEach { child ->
            walk(child, builder, shelves, looseTracks, loosePlaylists)
        }
        shelves += builder.build()
    }

    private fun parsePlaylist(item: JsonObject): YouTubeMusicPlaylist? {
        val browseId = findString(item, "browseId")
        // Artists open their page; plain channels (profiles) and radio menus are skipped.
        if (browseId?.startsWith("UC") == true) {
            if (findString(item, "pageType") != "MUSIC_PAGE_TYPE_ARTIST") return null
            val name = item["title"]?.let(::firstRun)
                ?: item["flexColumns"].asArray().firstNotNullOfOrNull { firstRun(it) }
                ?: return null
            return YouTubeMusicPlaylist(
                playlistId = browseId,
                title = name,
                subtitle = "Artist",
                thumbnailUrl = largestThumbnail(item),
                kind = YouTubeMusicItemKind.ARTIST,
            )
        }
        val playlistId = when {
            browseId?.startsWith("VL") == true -> browseId.removePrefix("VL")
            !browseId.isNullOrBlank() -> browseId
            else -> findString(item, "playlistId")
        } ?: return null
        if (playlistId.isBlank()) return null
        val title = item["title"]?.let(::firstRun)
            ?: item["flexColumns"].asArray().firstNotNullOfOrNull { firstRun(it) }
            ?: return null
        val subtitle = item["subtitle"]?.let { joinRuns(it) }.orEmpty()
        return YouTubeMusicPlaylist(
            playlistId = playlistId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = largestThumbnail(item),
            kind = if (playlistId.startsWith("MPRE")) YouTubeMusicItemKind.ALBUM else YouTubeMusicItemKind.PLAYLIST,
        )
    }

    private fun shelfTitle(renderer: JsonElement): String {
        val header = renderer.jsonObject["header"]
        return header?.let { firstRun(it) }.orEmpty()
    }

    private fun findContinuation(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> {
                element.obj("nextContinuationData")?.str("continuation")
                    ?: element.obj("continuationCommand")?.str("token")
                    ?: element.values.firstNotNullOfOrNull { findContinuation(it) }
            }
            is JsonArray -> element.firstNotNullOfOrNull { findContinuation(it) }
            else -> null
        }
    }

    private fun findString(element: JsonElement, key: String): String? {
        return when (element) {
            is JsonObject -> element.str(key) ?: element.values.firstNotNullOfOrNull { findString(it, key) }
            is JsonArray -> element.firstNotNullOfOrNull { findString(it, key) }
            else -> null
        }
    }

    private fun largestThumbnail(element: JsonElement): String? {
        val urls = mutableListOf<String>()
        collectThumbnailUrls(element, urls)
        return urls.lastOrNull { it.startsWith("http") }
    }

    private fun collectThumbnailUrls(element: JsonElement, into: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("thumbnails") || element.containsKey("thumbnail")) {
                    element["url"]?.jsonPrimitive?.contentOrNull?.let(into::add)
                }
                element.forEach { (key, value) ->
                    if (key == "url" && element.containsKey("width")) {
                        value.jsonPrimitive.contentOrNull?.let(into::add)
                    } else {
                        collectThumbnailUrls(value, into)
                    }
                }
            }
            is JsonArray -> element.forEach { collectThumbnailUrls(it, into) }
            else -> Unit
        }
    }

    private fun firstRun(element: JsonElement): String? = joinRuns(element).takeIf { it.isNotBlank() }

    private fun joinRuns(element: JsonElement): String {
        val texts = mutableListOf<String>()
        collectRuns(element, texts)
        return texts.joinToString("").trim()
    }

    private fun collectRuns(element: JsonElement, into: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                val runs = element["runs"]
                if (runs is JsonArray && element.containsKey("runs")) {
                    runs.forEach { run ->
                        (run as? JsonObject)?.str("text")?.let(into::add)
                    }
                    return
                }
                element.values.forEach { collectRuns(it, into) }
            }
            is JsonArray -> element.forEach { collectRuns(it, into) }
            else -> Unit
        }
    }

    private fun parseDuration(text: String): Long? {
        if (!text.matches(DURATION_TEXT)) return null
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        val seconds = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return seconds * 1000L
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()

    private class ShelfBuilder(val title: String) {
        val tracks = mutableListOf<YouTubeMusicTrack>()
        val playlists = mutableListOf<YouTubeMusicPlaylist>()
        fun build() = YouTubeMusicShelf(
            title,
            tracks.distinctBy { it.videoId },
            playlists.distinctBy { it.playlistId },
        )
    }

    private val DURATION_TEXT = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
    // "88M views", "24 crore views", "1.2 lakh plays": a number, an optional scale word, a unit.
    // "88M views", "6.7K plays", "24 crore views", "1.2 lakh plays": a number, an optional scale, a unit.
    // Only known scale words, so names like "69 Love Songs" stay.
    private val COUNT_TEXT = Regex(
        """[\d.,]+\s*[KMB]?\s*(?:(?:lakh|crore|thousand|million|billion)\s+)?""" +
            """(views?|plays?|likes?|songs?|subscribers?|monthly audience|watching)""",
        RegexOption.IGNORE_CASE,
    )

    // Indian locales write counts as "14 crore plays"; the JVM's \s doesn't match those spaces.
    private val NO_BREAK_SPACES = Regex("""[  ]""")
    private val TYPE_LABELS = setOf("Song", "Video", "Episode", "Single", "Album", "EP", "Podcast")

    // Upload dates on video and episode rows: "Aug 20, 2021", "2021".
    private val DATE_TEXT = Regex("""(?:[A-Z][a-z]{2} \d{1,2}, )?\d{4}""")

    private val YEAR_TEXT = Regex("""\d{4}""")

    /**
     * The name parts of a byline. A bare year only counts as a date in the last slot
     * ("Artist • Album • 2014"), so an album called "1989" is kept.
     */
    private fun namesOnly(parts: List<String>): List<String> = parts.filterIndexed { index, part ->
        part.isNotBlank() && (!isMetaText(part) || (part.matches(YEAR_TEXT) && index < parts.lastIndex))
    }

    private fun isMetaText(text: String): Boolean =
        text in TYPE_LABELS || text.matches(DURATION_TEXT) ||
            text.replace(NO_BREAK_SPACES, " ").matches(COUNT_TEXT) || text.matches(DATE_TEXT)
}
