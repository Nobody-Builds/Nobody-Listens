package com.twilighty.lmusic.data.taste

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** A name with a 0..1 weight (the strongest entry of a list is 1). */
data class WeightedName(val name: String, val weight: Float)

/** A song reference shared in a fingerprint: title + artist only, never ids or paths. */
data class SongRef(val title: String, val artist: String)

/**
 * A listener's taste, small enough to fit in a QR code. Pure data: built by [TasteFingerprintBuilder],
 * serialised by [TasteCodec], compared by [TasteMatcher].
 */
data class TasteFingerprint(
    val name: String,
    val artists: List<WeightedName>,
    val genres: List<WeightedName> = emptyList(),
    val moods: List<WeightedName> = emptyList(),
    val songs: List<SongRef> = emptyList(),
) {
    val hasSmart: Boolean get() = genres.isNotEmpty() || moods.isNotEmpty()
}

/**
 * Builds a fingerprint from aggregated listening data.
 * Artist weights use the square root of listening time so a single obsession doesn't drown the rest.
 */
object TasteFingerprintBuilder {
    const val MAX_ARTISTS = 30
    const val MAX_SONGS = 20
    const val MAX_TAGS = 8

    fun build(
        name: String,
        artistListeningMs: Map<String, Double>,
        topSongs: List<SongRef>,
        genres: Map<String, Float> = emptyMap(),
        moods: Map<String, Float> = emptyMap(),
    ): TasteFingerprint {
        // Merge spellings that normalise to the same artist; keep the most listened spelling.
        val merged = LinkedHashMap<String, Pair<String, Double>>()
        val bestSpelling = HashMap<String, Double>()
        artistListeningMs.forEach { (display, ms) ->
            if (display.isBlank() || ms <= 0.0) return@forEach
            val key = TasteMatcher.normalizeArtist(display)
            if (key.isEmpty()) return@forEach
            val cur = merged[key]
            val keepName = if (cur == null || ms > (bestSpelling[key] ?: 0.0)) {
                bestSpelling[key] = ms
                display.trim()
            } else cur.first
            merged[key] = keepName to ((cur?.second ?: 0.0) + ms)
        }
        val ranked = merged.values
            .map { (n, ms) -> n to kotlin.math.sqrt(ms) }
            .sortedByDescending { it.second }
            .take(MAX_ARTISTS)
        val maxW = ranked.firstOrNull()?.second ?: 1.0
        val artists = ranked.map { (n, w) -> WeightedName(n, (w / maxW).toFloat()) }

        val seen = HashSet<String>()
        val songs = topSongs
            .filter { it.title.isNotBlank() }
            .filter { seen.add(TasteMatcher.songKey(it.title, it.artist)) }
            .take(MAX_SONGS)

        return TasteFingerprint(
            name = name.trim().ifBlank { "Friend" },
            artists = artists,
            genres = normalizeTags(genres),
            moods = normalizeTags(moods),
            songs = songs,
        )
    }

    private fun normalizeTags(tags: Map<String, Float>): List<WeightedName> {
        val top = tags.entries.filter { it.key.isNotBlank() && it.value > 0f }
            .sortedByDescending { it.value }
            .take(MAX_TAGS)
        val max = top.firstOrNull()?.value ?: return emptyList()
        return top.map { WeightedName(it.key, it.value / max) }
    }
}

/**
 * Versioned binary → raw Deflate → text.
 *
 * Two text forms share the "LMT1:" prefix:
 *  - QR form: `LMT1:` + Base45 (RFC 9285). Only uses QR alphanumeric characters, so the QR code stays small.
 *  - Text form: `LMT1:u` + base64url. No spaces or markdown-ish runs, safe to paste in chat apps.
 *
 * Binary v1: [version u8][name str][artists: n u8, (str, weight u8)*][genres …][moods …]
 *            [songs: n u8, (title str, artistRef u8 (index+1 into artists, 0 = inline), [artist str])*]
 * str = [byte length u8][UTF-8 bytes].
 */
object TasteCodec {
    const val PREFIX = "LMT1:"
    private const val TEXT_MARK = 'u'
    private const val VERSION: Int = 1
    private const val MAX_INFLATED = 16 * 1024

    /** Base45 characters kept at or below this (v25-M holds 1455 alphanumeric characters). */
    const val DEFAULT_MAX_QR_CHARS = 1400

    class InvalidCodeException(message: String) : IllegalArgumentException(message)

    // ------------------------------------------------------------ public API

    /** QR payload, truncating lists until it fits [maxChars]. */
    fun encodeForQr(fp: TasteFingerprint, maxChars: Int = DEFAULT_MAX_QR_CHARS): String {
        val fitted = fit(fp) { PREFIX.length + Base45.encodedLength(compress(toBinary(it)).size) <= maxChars }
        return PREFIX + Base45.encode(compress(toBinary(fitted)))
    }

    /** Text form for sharing in messages. Same truncation as the QR form so both carry the same data. */
    fun encodeForText(fp: TasteFingerprint, maxChars: Int = DEFAULT_MAX_QR_CHARS): String {
        val fitted = fit(fp) { PREFIX.length + Base45.encodedLength(compress(toBinary(it)).size) <= maxChars }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compress(toBinary(fitted)))
        return PREFIX + TEXT_MARK + b64
    }

    /** Finds a code inside arbitrary text (a pasted message, a scanned QR) and decodes it. */
    fun decode(text: String): TasteFingerprint {
        val start = text.indexOf(PREFIX)
        if (start < 0) throw InvalidCodeException("That isn't a Nobody Listens taste code")
        val body = text.substring(start + PREFIX.length)
        val decoded: ByteArray? = if (body.startsWith(TEXT_MARK)) {
            val b64 = body.drop(1).takeWhile { (it.isLetterOrDigit() && it.code < 128) || it == '-' || it == '_' }
            runCatching { Base64.getUrlDecoder().decode(b64) }.getOrNull()
        } else {
            val line = body.substringBefore('\n').trimEnd('\r')
            Base45.decodeOrNull(line) ?: Base45.decodeOrNull(line.trimEnd())
        }
        val bytes = decoded ?: throw InvalidCodeException("This code looks damaged")
        val raw = inflate(bytes) ?: throw InvalidCodeException("This code looks damaged")
        return fromBinary(raw)
    }

    // ------------------------------------------------------------ fitting

    /** Drops the least important entries until [fits] is true. Always terminates. */
    internal fun fit(fp: TasteFingerprint, fits: (TasteFingerprint) -> Boolean): TasteFingerprint {
        var cur = fp
        var guard = 0
        while (!fits(cur) && guard++ < 200) {
            cur = when {
                cur.songs.size > 8 -> cur.copy(songs = cur.songs.dropLast(1))
                cur.artists.size > 15 -> cur.copy(artists = cur.artists.dropLast(1))
                cur.genres.size > 3 || cur.moods.size > 3 ->
                    cur.copy(genres = cur.genres.take(3), moods = cur.moods.take(3))
                cur.songs.isNotEmpty() -> cur.copy(songs = cur.songs.dropLast(1))
                cur.artists.size > 1 -> cur.copy(artists = cur.artists.dropLast(1))
                cur.genres.isNotEmpty() || cur.moods.isNotEmpty() -> cur.copy(genres = emptyList(), moods = emptyList())
                else -> return cur
            }
        }
        return cur
    }

    // ------------------------------------------------------------ binary

    internal fun toBinary(fp: TasteFingerprint): ByteArray {
        val out = ByteArrayOutputStream(1024)
        out.write(VERSION)
        writeStr(out, fp.name, 48)
        writeWeighted(out, fp.artists.take(255), 64)
        writeWeighted(out, fp.genres.take(255), 32)
        writeWeighted(out, fp.moods.take(255), 32)
        val artistIndex = HashMap<String, Int>()
        fp.artists.take(254).forEachIndexed { i, a -> artistIndex.putIfAbsent(a.name.lowercase(), i) }
        val songs = fp.songs.take(255)
        out.write(songs.size)
        songs.forEach { s ->
            writeStr(out, s.title, 80)
            val idx = artistIndex[s.artist.lowercase()]
            if (idx != null) {
                out.write(idx + 1)
            } else {
                out.write(0)
                writeStr(out, s.artist, 64)
            }
        }
        return out.toByteArray()
    }

    internal fun fromBinary(bytes: ByteArray): TasteFingerprint {
        val r = Reader(bytes)
        val version = r.u8()
        if (version != VERSION) throw InvalidCodeException("This code is from a newer Nobody Listens — update the app")
        val name = r.str()
        val artists = r.weighted()
        val genres = r.weighted()
        val moods = r.weighted()
        val songCount = r.u8()
        val songs = List(songCount) {
            val title = r.str()
            val ref = r.u8()
            val artist = if (ref == 0) r.str() else artists.getOrNull(ref - 1)?.name
                ?: throw InvalidCodeException("This code looks damaged")
            SongRef(title, artist)
        }
        return TasteFingerprint(name.ifBlank { "Friend" }, artists, genres, moods, songs)
    }

    private fun writeWeighted(out: ByteArrayOutputStream, list: List<WeightedName>, maxBytes: Int) {
        out.write(list.size)
        list.forEach {
            writeStr(out, it.name, maxBytes)
            out.write((it.weight.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255))
        }
    }

    /** Writes [s] as UTF-8, cut at a character boundary so it is at most [maxBytes] bytes. */
    private fun writeStr(out: ByteArrayOutputStream, s: String, maxBytes: Int) {
        var t = s.trim()
        var b = t.toByteArray(Charsets.UTF_8)
        while (b.size > maxBytes && t.isNotEmpty()) {
            t = t.dropLast(1)
            if (t.isNotEmpty() && Character.isHighSurrogate(t.last())) t = t.dropLast(1)
            b = t.toByteArray(Charsets.UTF_8)
        }
        out.write(b.size)
        out.write(b)
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        fun u8(): Int {
            if (i >= b.size) throw InvalidCodeException("This code looks damaged")
            return b[i++].toInt() and 0xFF
        }
        fun str(): String {
            val len = u8()
            if (i + len > b.size) throw InvalidCodeException("This code looks damaged")
            val s = String(b, i, len, Charsets.UTF_8)
            i += len
            return s
        }
        fun weighted(): List<WeightedName> = List(u8()) { WeightedName(str(), u8() / 255f) }
    }

    // ------------------------------------------------------------ deflate

    internal fun compress(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            d.setInput(data)
            d.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(1024)
            while (!d.finished()) {
                val n = d.deflate(buf)
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            d.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inf = Inflater(true)
        return try {
            // nowrap mode wants one extra dummy byte at the end of the input.
            inf.setInput(data + 0)
            val out = ByteArrayOutputStream(data.size * 3)
            val buf = ByteArray(1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED) return null
            }
            if (!inf.finished()) null else out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }
}

/** Base45 (RFC 9285): uses exactly the QR alphanumeric character set. */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encodedLength(byteCount: Int): Int = (byteCount / 2) * 3 + (byteCount % 2) * 2

    fun encode(data: ByteArray): String {
        val sb = StringBuilder(encodedLength(data.size))
        var i = 0
        while (i + 1 < data.size) {
            val n = (data[i].toInt() and 0xFF) * 256 + (data[i + 1].toInt() and 0xFF)
            sb.append(ALPHABET[n % 45]).append(ALPHABET[(n / 45) % 45]).append(ALPHABET[n / 2025])
            i += 2
        }
        if (i < data.size) {
            val n = data[i].toInt() and 0xFF
            sb.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return sb.toString()
    }

    fun decodeOrNull(text: String): ByteArray? {
        if (text.length % 3 == 1) return null
        val out = ByteArrayOutputStream(text.length * 2 / 3)
        var i = 0
        while (i < text.length) {
            val c = ALPHABET.indexOf(text[i])
            val d = ALPHABET.indexOf(text.getOrElse(i + 1) { '?' })
            if (c < 0 || d < 0) return null
            if (i + 2 < text.length) {
                val e = ALPHABET.indexOf(text[i + 2])
                if (e < 0) return null
                val n = c + d * 45 + e * 2025
                if (n > 0xFFFF) return null
                out.write(n shr 8)
                out.write(n and 0xFF)
                i += 3
            } else {
                val n = c + d * 45
                if (n > 0xFF) return null
                out.write(n)
                i += 2
            }
        }
        return out.toByteArray()
    }
}
