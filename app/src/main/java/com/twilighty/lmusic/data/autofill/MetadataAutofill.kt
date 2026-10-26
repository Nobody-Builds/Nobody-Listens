package com.twilighty.lmusic.data.autofill

import android.graphics.BitmapFactory
import com.twilighty.lmusic.data.model.Song
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * LMusic metadata autofill.
 *
 * Two sources, neither needing an account or API key:
 *  1. Offline guesses from the file itself — filename patterns ("01 - Artist - Title"),
 *     the folder it sits in, and what the other tracks in that folder agree on.
 *  2. MusicBrainz (open music encyclopedia) for rich metadata, plus Cover Art Archive
 *     for artwork. Requests go out only when the user opens autofill.
 */

/** The fields autofill can fill. Values are display strings; blank means "no value". */
data class MetadataFields(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val genre: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val composer: String = "",
)

enum class AutofillField {
    TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, GENRE, TRACK, DISC, COMPOSER;

    fun valueOf(fields: MetadataFields): String = when (this) {
        TITLE -> fields.title
        ARTIST -> fields.artist
        ALBUM -> fields.album
        ALBUM_ARTIST -> fields.albumArtist
        YEAR -> fields.year
        GENRE -> fields.genre
        TRACK -> fields.trackNumber
        DISC -> fields.discNumber
        COMPOSER -> fields.composer
    }
}

fun MetadataFields.withField(field: AutofillField, value: String): MetadataFields = when (field) {
    AutofillField.TITLE -> copy(title = value)
    AutofillField.ARTIST -> copy(artist = value)
    AutofillField.ALBUM -> copy(album = value)
    AutofillField.ALBUM_ARTIST -> copy(albumArtist = value)
    AutofillField.YEAR -> copy(year = value)
    AutofillField.GENRE -> copy(genre = value)
    AutofillField.TRACK -> copy(trackNumber = value)
    AutofillField.DISC -> copy(discNumber = value)
    AutofillField.COMPOSER -> copy(composer = value)
}

enum class AutofillSource { FILES, MUSICBRAINZ }

data class AutofillCandidate(
    val source: AutofillSource,
    val fields: MetadataFields,
    /** 0–100. For [AutofillSource.FILES] this is how much of the guess is backed by data. */
    val confidence: Int,
    val durationMs: Long? = null,
    val releaseType: String? = null,
    val releaseCountry: String? = null,
    val recordingId: String? = null,
    val releaseId: String? = null,
    val releaseGroupId: String? = null,
    val artistId: String? = null,
    /** True once genre/composer/album-artist details have been looked up. */
    val detailed: Boolean = false,
) {
    val thumbnailUrl: String?
        get() = releaseId?.let { "https://coverartarchive.org/release/$it/front-250" }
}

data class CoverArt(val bytes: ByteArray, val mimeType: String) {
    override fun equals(other: Any?): Boolean =
        other is CoverArt && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

class AutofillNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

// ---------------------------------------------------------------------------------------
// Offline guesses
// ---------------------------------------------------------------------------------------

object OfflineMetadataGuesser {

    private val genericFolders = setOf(
        "music", "musics", "downloads", "download", "songs", "audio", "media", "mp3", "flac",
        "sdcard", "emulated", "0", "storage", "documents", "whatsapp audio", "telegram audio",
        "bluetooth", "recordings", "various", "various artists", "unknown", "new folder",
    )

    private val unknownValues = setOf("", "<unknown>", "unknown", "unknown artist", "unknown album", "unknown genre")

    fun isUnknown(value: String?): Boolean = value == null || value.trim().lowercase(Locale.ROOT) in unknownValues

    /**
     * Builds the best guess from the song's own path plus its folder-mates.
     * [siblings] are other library songs in the same folder (may be empty).
     */
    fun guess(song: Song, siblings: List<Song>): AutofillCandidate {
        val file = File(song.path)
        val baseName = file.nameWithoutExtension
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val parsed = parseFileName(baseName)

        val folder = file.parentFile
        val folderInfo = folder?.name?.let { parseAlbumFolder(it) }
        val grandParent = folder?.parentFile?.name?.trim().orEmpty()
        val grandParentArtist = grandParent.takeIf {
            it.isNotBlank() && it.lowercase(Locale.ROOT) !in genericFolders
        }

        val others = siblings.filter { it.id != song.id }
        fun <T> majority(values: List<T?>): T? = values
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .entries
            .filter { it.value >= max(2, (others.size + 1) / 2) }
            .maxByOrNull { it.value }
            ?.key

        val siblingAlbum = majority(others.map { it.album.takeUnless(::isUnknown) })
        val siblingAlbumArtist = majority(others.map { it.albumArtist?.takeUnless(::isUnknown) })
        val siblingArtist = majority(others.map { it.artist.takeUnless(::isUnknown) })
        val siblingYear = majority(others.map { it.year.takeIf { y -> y in 1000..9999 } })
        val siblingGenre = majority(others.map { it.genre?.takeUnless(::isUnknown) })

        var backed = 0
        var total = 0
        fun pick(vararg options: String?): String {
            total++
            val value = options.firstOrNull { !it.isNullOrBlank() && !isUnknown(it) }?.trim().orEmpty()
            if (value.isNotEmpty()) backed++
            return value
        }

        val fields = MetadataFields(
            title = pick(parsed.title, song.title.takeUnless(::isUnknown)),
            artist = pick(parsed.artist, song.artist.takeUnless(::isUnknown), siblingArtist, folderInfo?.artist, grandParentArtist),
            album = pick(siblingAlbum, song.album.takeUnless(::isUnknown), folderInfo?.album),
            albumArtist = pick(siblingAlbumArtist, song.albumArtist, folderInfo?.artist, siblingArtist, grandParentArtist),
            year = pick(song.year.takeIf { it in 1000..9999 }?.toString(), siblingYear?.toString(), folderInfo?.year),
            genre = pick(song.genre, siblingGenre),
            trackNumber = pick(parsed.track?.toString(), song.trackNumber.takeIf { it > 0 }?.toString()),
            discNumber = song.discNumber?.takeIf { it > 0 }?.toString().orEmpty(),
            composer = "",
        )
        val confidence = if (total == 0) 0 else (backed * 100 / total)
        return AutofillCandidate(
            source = AutofillSource.FILES,
            fields = fields,
            confidence = confidence,
            durationMs = song.duration.takeIf { it > 0 },
            detailed = true,
        )
    }

    data class ParsedName(val track: Int?, val artist: String?, val title: String?)

    // A leading number is a track number only with a separator ("01 -", "3.") or a leading zero,
    // so names like "50 Cent - In Da Club" stay intact.
    private val trackArtistTitle = Regex("^(0\\d{0,2}|\\d{1,3}(?=\\s*[.\\-)]))\\s*[.\\-)]?\\s+(.+?)\\s+[-–—]\\s+(.+)$")
    private val artistTrackTitle = Regex("^(.+?)\\s+[-–—]\\s+(\\d{1,3})\\s+[-–—]\\s+(.+)$")
    private val trackTitle = Regex("^(0\\d{0,2}|\\d{1,3}(?=\\s*[.\\-)]))\\s*[.\\-)]?\\s+(.+)$")
    private val artistTitle = Regex("^(.+?)\\s+[-–—]\\s+(.+)$")

    fun parseFileName(name: String): ParsedName {
        val cleaned = name
            .replace(Regex("\\s*[\\[(](official.*?|lyrics?|audio|hq|hd|320 ?kbps|128 ?kbps)[\\])]", RegexOption.IGNORE_CASE), "")
            .trim()
        trackArtistTitle.matchEntire(cleaned)?.let {
            return ParsedName(it.groupValues[1].toIntOrNull(), it.groupValues[2].trim(), it.groupValues[3].trim())
        }
        artistTrackTitle.matchEntire(cleaned)?.let {
            return ParsedName(it.groupValues[2].toIntOrNull(), it.groupValues[1].trim(), it.groupValues[3].trim())
        }
        trackTitle.matchEntire(cleaned)?.let {
            return ParsedName(it.groupValues[1].toIntOrNull(), null, it.groupValues[2].trim())
        }
        artistTitle.matchEntire(cleaned)?.let {
            return ParsedName(null, it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        return ParsedName(null, null, cleaned.ifBlank { null })
    }

    data class AlbumFolder(val artist: String?, val album: String?, val year: String?)

    private val yearInFolder = Regex("[\\[(]?((?:19|20)\\d{2})[\\])]?")

    fun parseAlbumFolder(folderName: String): AlbumFolder? {
        val name = folderName.replace('_', ' ').trim()
        if (name.isBlank() || name.lowercase(Locale.ROOT) in genericFolders) return null
        val year = yearInFolder.find(name)?.groupValues?.get(1)
        val withoutYear = name.replace(yearInFolder, " ").replace(Regex("\\s+"), " ").trim().trim('-', '–', ' ')
        val parts = withoutYear.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        return if (parts.size == 2) {
            AlbumFolder(parts[0].trim().ifBlank { null }, parts[1].trim().ifBlank { null }, year)
        } else {
            AlbumFolder(null, withoutYear.ifBlank { null }, year)
        }
    }
}

// ---------------------------------------------------------------------------------------
// MusicBrainz + Cover Art Archive
// ---------------------------------------------------------------------------------------

object MusicBrainzAutofill {

    private const val BASE = "https://musicbrainz.org/ws/2"
    private const val USER_AGENT = "NobodyListens/1.0 ( https://github.com/Nobody-Builds/Nobody-Listens )"

    // MusicBrainz asks clients to stay at or below one request per second.
    private val rateLock = Mutex()
    private var lastRequestAt = 0L
    private const val MIN_INTERVAL_MS = 1_100L

    /** Searches recordings and returns scored candidates, best first. */
    suspend fun search(
        title: String,
        artist: String,
        album: String,
        durationMs: Long?,
        limit: Int = 6,
    ): List<AutofillCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@withContext emptyList()
        val queries = buildList {
            if (artist.isNotBlank() && album.isNotBlank()) {
                add("recording:\"${lucene(cleanTitle)}\" AND artist:\"${lucene(artist)}\" AND release:\"${lucene(album)}\"")
            }
            if (artist.isNotBlank()) {
                add("recording:\"${lucene(cleanTitle)}\" AND artist:\"${lucene(artist)}\"")
            }
            add("recording:\"${lucene(cleanTitle)}\"")
            // Loose, unquoted fallback for typos and odd spellings.
            // Lower-cased so words like AND / OR / NOT in titles aren't read as operators.
            add(listOf(cleanTitle, artist).filter { it.isNotBlank() }.joinToString(" ") { lucene(it.lowercase(Locale.ROOT)) })
        }.distinct()

        val seen = LinkedHashMap<String, AutofillCandidate>()
        for (query in queries) {
            val url = "$BASE/recording?fmt=json&limit=25&query=" + URLEncoder.encode(query, "UTF-8")
            val json = getJson(url)
            val recordings = json.optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(i) ?: continue
                val candidate = candidateFromRecording(recording, cleanTitle, artist, album, durationMs) ?: continue
                val key = candidate.recordingId + "|" + candidate.releaseId
                val existing = seen[key]
                if (existing == null || existing.confidence < candidate.confidence) seen[key] = candidate
            }
            // Good enough matches found — skip the looser queries.
            if (seen.values.count { it.confidence >= 75 } >= 3) break
        }

        seen.values
            .sortedByDescending { it.confidence }
            .distinctBy { listOf(it.fields.title.lowercase(), it.fields.artist.lowercase(), it.fields.album.lowercase(), it.fields.year).joinToString("|") }
            .take(limit)
    }

    /**
     * Fills in what the search result can't carry: album artist and release details,
     * genre (recording first, then release group) and composer (via the work).
     */
    suspend fun loadDetails(candidate: AutofillCandidate): AutofillCandidate = withContext(Dispatchers.IO) {
        if (candidate.source != AutofillSource.MUSICBRAINZ || candidate.detailed) return@withContext candidate
        var fields = candidate.fields
        var artistId = candidate.artistId

        candidate.recordingId?.let { recordingId ->
            runCatching {
                val recording = getJson("$BASE/recording/$recordingId?fmt=json&inc=genres+tags+work-rels+artist-credits")
                pickGenre(recording)?.let { genre -> if (fields.genre.isBlank()) fields = fields.copy(genre = genre) }
                artistId = artistId ?: recording.optJSONArray("artist-credit")?.optJSONObject(0)
                    ?.optJSONObject("artist")?.optString("id")?.takeIf { it.isNotBlank() }

                val workId = recording.optJSONArray("relations")?.let { relations ->
                    (0 until relations.length())
                        .mapNotNull { relations.optJSONObject(it) }
                        .firstOrNull { it.optString("type") == "performance" }
                        ?.optJSONObject("work")?.optString("id")
                }
                if (!workId.isNullOrBlank()) {
                    val work = getJson("$BASE/work/$workId?fmt=json&inc=artist-rels")
                    val composers = work.optJSONArray("relations")?.let { relations ->
                        (0 until relations.length())
                            .mapNotNull { relations.optJSONObject(it) }
                            .filter { it.optString("type") == "composer" }
                            .mapNotNull { it.optJSONObject("artist")?.optString("name")?.takeIf { n -> n.isNotBlank() } }
                            .distinct()
                    }.orEmpty()
                    if (composers.isNotEmpty()) fields = fields.copy(composer = composers.joinToString(", "))
                }
            }.onFailure { Timber.w(it, "Autofill: recording details failed") }
        }

        candidate.releaseId?.let { releaseId ->
            runCatching {
                val release = getJson("$BASE/release/$releaseId?fmt=json&inc=artist-credits+genres+release-groups")
                creditName(release.optJSONArray("artist-credit"))?.let { fields = fields.copy(albumArtist = it) }
                if (fields.genre.isBlank()) pickGenre(release)?.let { fields = fields.copy(genre = it) }
                if (fields.genre.isBlank()) {
                    release.optJSONObject("release-group")?.let { group -> pickGenre(group) }
                        ?.let { fields = fields.copy(genre = it) }
                }
            }.onFailure { Timber.w(it, "Autofill: release details failed") }
        }

        if (fields.genre.isBlank()) {
            candidate.releaseGroupId?.let { groupId ->
                runCatching {
                    val group = getJson("$BASE/release-group/$groupId?fmt=json&inc=genres+tags")
                    pickGenre(group)?.let { fields = fields.copy(genre = it) }
                }.onFailure { Timber.w(it, "Autofill: release-group details failed") }
            }
        }

        candidate.copy(fields = fields, artistId = artistId, detailed = true)
    }

    /** Front cover from Cover Art Archive (release first, then its release group). */
    suspend fun loadCoverArt(candidate: AutofillCandidate, size: Int = 1200): CoverArt? = withContext(Dispatchers.IO) {
        val urls = listOfNotNull(
            candidate.releaseId?.let { "https://coverartarchive.org/release/$it/front-$size" },
            candidate.releaseGroupId?.let { "https://coverartarchive.org/release-group/$it/front-$size" },
        )
        for (url in urls) {
            val art = runCatching { download(url) }.getOrNull() ?: continue
            if (art.bytes.isNotEmpty()) return@withContext art
        }
        null
    }

    // -- parsing ---------------------------------------------------------------------------

    private fun candidateFromRecording(
        recording: JSONObject,
        wantedTitle: String,
        wantedArtist: String,
        wantedAlbum: String,
        wantedDurationMs: Long?,
    ): AutofillCandidate? {
        val recordingId = recording.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = recording.optString("title").takeIf { it.isNotBlank() } ?: return null
        val credits = recording.optJSONArray("artist-credit")
        val artist = creditName(credits).orEmpty()
        val artistId = credits?.optJSONObject(0)?.optJSONObject("artist")?.optString("id")?.takeIf { it.isNotBlank() }
        val length = recording.optLong("length", 0L).takeIf { it > 0 }
        val mbScore = recording.optInt("score", 50)

        val releases = recording.optJSONArray("releases") ?: JSONArray()
        val release = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .maxByOrNull { releaseRank(it, wantedAlbum) }

        val releaseTitle = release?.optString("title").orEmpty()
        val date = release?.optString("date").orEmpty()
        val group = release?.optJSONObject("release-group")
        val primaryType = group?.optString("primary-type").orEmpty()
        val media = release?.optJSONArray("media")?.optJSONObject(0)
        val track = media?.optJSONArray("track")?.optJSONObject(0)
        val trackNumber = track?.optString("number")?.takeIf { it.isNotBlank() }
            ?: media?.optInt("track-offset", -1)?.takeIf { it >= 0 }?.let { (it + 1).toString() }
        val discNumber = media?.optInt("position", 0)?.takeIf { it > 0 }?.toString()
        val releaseArtist = creditName(release?.optJSONArray("artist-credit"))

        val fields = MetadataFields(
            title = title,
            artist = artist,
            album = releaseTitle,
            albumArtist = releaseArtist ?: artist,
            year = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(),
            genre = "",
            trackNumber = trackNumber?.filter { it.isDigit() }.orEmpty(),
            discNumber = discNumber.orEmpty(),
            composer = "",
        )
        val confidence = AutofillScoring.score(
            wantedTitle = wantedTitle,
            wantedArtist = wantedArtist,
            wantedAlbum = wantedAlbum,
            wantedDurationMs = wantedDurationMs,
            title = title,
            artist = artist,
            album = releaseTitle,
            durationMs = length,
            searchScore = mbScore,
        )
        return AutofillCandidate(
            source = AutofillSource.MUSICBRAINZ,
            fields = fields,
            confidence = confidence,
            durationMs = length,
            releaseType = primaryType.ifBlank { null },
            releaseCountry = release?.optString("country")?.ifBlank { null },
            recordingId = recordingId,
            releaseId = release?.optString("id")?.ifBlank { null },
            releaseGroupId = group?.optString("id")?.ifBlank { null },
            artistId = artistId,
        )
    }

    /** Prefers the release matching the wanted album, then official studio albums, then oldest. */
    private fun releaseRank(release: JSONObject, wantedAlbum: String): Double {
        var rank = 0.0
        if (wantedAlbum.isNotBlank()) rank += 4.0 * AutofillScoring.similarity(wantedAlbum, release.optString("title"))
        if (release.optString("status").equals("Official", ignoreCase = true)) rank += 1.5
        val group = release.optJSONObject("release-group")
        when (group?.optString("primary-type")?.lowercase(Locale.ROOT)) {
            "album" -> rank += 1.2
            "ep" -> rank += 0.8
            "single" -> rank += 0.5
        }
        val secondary = group?.optJSONArray("secondary-types")
        if (secondary != null && secondary.length() > 0) rank -= 1.0 // compilations, live, soundtrack…
        val year = release.optString("date").take(4).toIntOrNull()
        if (year != null) rank += (2100 - year) / 1000.0 // slight preference for the original release
        return rank
    }

    private fun creditName(credits: JSONArray?): String? {
        if (credits == null || credits.length() == 0) return null
        val builder = StringBuilder()
        for (i in 0 until credits.length()) {
            val credit = credits.optJSONObject(i) ?: continue
            val name = credit.optString("name").ifBlank { credit.optJSONObject("artist")?.optString("name").orEmpty() }
            builder.append(name)
            builder.append(credit.optString("joinphrase"))
        }
        return builder.toString().trim().ifBlank { null }
    }

    private fun pickGenre(entity: JSONObject): String? {
        fun best(array: JSONArray?): String? {
            if (array == null) return null
            return (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .filter { it.optInt("count", 0) > 0 }
                .maxByOrNull { it.optInt("count", 0) }
                ?.optString("name")
                ?.takeIf { it.isNotBlank() }
        }
        val raw = best(entity.optJSONArray("genres")) ?: return null
        return raw.split(' ', '-').joinToString(" ") { word ->
            if (word.lowercase(Locale.ROOT) in setOf("r&b", "edm", "uk", "us", "dj")) {
                word.uppercase(Locale.ROOT)
            } else {
                word.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
            }
        }.replace("Hip Hop", "Hip-Hop")
    }

    private fun lucene(value: String): String =
        value.trim().replace(Regex("([+\\-!(){}\\[\\]^\"~*?:\\\\/&|])"), "\\\\$1")

    // -- HTTP ------------------------------------------------------------------------------

    private suspend fun getJson(url: String): JSONObject {
        repeat(3) { attempt ->
            throttle()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        return JSONObject(body)
                    }
                    503, 429 -> delay(1_500L * (attempt + 1)) // rate limited: back off and retry
                    HttpURLConnection.HTTP_NOT_FOUND -> return JSONObject()
                    else -> throw AutofillNetworkException("MusicBrainz returned HTTP $code")
                }
            } catch (e: AutofillNetworkException) {
                throw e
            } catch (e: IOException) {
                if (attempt == 2) throw AutofillNetworkException("Couldn't reach MusicBrainz", e)
                delay(800L)
            } finally {
                connection.disconnect()
            }
        }
        throw AutofillNetworkException("MusicBrainz is busy, try again in a moment")
    }

    private suspend fun throttle() {
        rateLock.withLock {
            val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun download(url: String): CoverArt? {
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    current = connection.getHeaderField("Location")?.let { URL(URL(current), it).toString() } ?: return null
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0) return null
                val mime = options.outMimeType ?: connection.contentType ?: "image/jpeg"
                return CoverArt(bytes, mime)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }
}

// ---------------------------------------------------------------------------------------
// Scoring
// ---------------------------------------------------------------------------------------

object AutofillScoring {

    fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase(Locale.ROOT)
            .replace(Regex("[\\[(][^\\])]*(feat|ft\\.|remaster|version|edit|mix|live|explicit)[^\\])]*[\\])]"), " ")
            .replace(Regex("\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), " ")
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    /** 0–1 similarity: the better of token overlap and edit-distance ratio. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val tokensX = x.split(' ').toSet()
        val tokensY = y.split(' ').toSet()
        val jaccard = tokensX.intersect(tokensY).size.toDouble() / tokensX.union(tokensY).size
        val containment = if (x.contains(y) || y.contains(x)) 0.85 else 0.0
        val distance = levenshtein(x, y)
        val ratio = 1.0 - distance.toDouble() / max(x.length, y.length)
        return maxOf(jaccard, ratio, containment)
    }

    fun score(
        wantedTitle: String,
        wantedArtist: String,
        wantedAlbum: String,
        wantedDurationMs: Long?,
        title: String,
        artist: String,
        album: String,
        durationMs: Long?,
        searchScore: Int,
    ): Int {
        var weighted = 0.0
        var weights = 0.0
        fun add(weight: Double, value: Double) {
            weighted += weight * value
            weights += weight
        }
        add(0.45, similarity(wantedTitle, title))
        if (wantedArtist.isNotBlank()) add(0.30, similarity(wantedArtist, artist))
        if (wantedAlbum.isNotBlank() && album.isNotBlank()) add(0.10, similarity(wantedAlbum, album))
        if (wantedDurationMs != null && wantedDurationMs > 0 && durationMs != null) {
            val diff = abs(wantedDurationMs - durationMs) / 1000.0
            val durationScore = when {
                diff <= 3 -> 1.0
                diff >= 25 -> 0.0
                else -> 1.0 - (diff - 3) / 22.0
            }
            add(0.15, durationScore)
        }
        val local = if (weights > 0) weighted / weights else 0.0
        val combined = 0.9 * local + 0.1 * (searchScore.coerceIn(0, 100) / 100.0)
        return (combined * 100).roundToInt().coerceIn(0, 100)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
