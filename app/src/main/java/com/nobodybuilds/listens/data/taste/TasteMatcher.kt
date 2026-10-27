package com.nobodybuilds.listens.data.taste

import java.text.Normalizer
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A library song as the matcher sees it (no Android types, so it stays unit-testable). */
data class LibrarySongKey(val id: String, val title: String, val artists: List<String>)

data class SharedArtist(val name: String, val weight: Float)

data class TasteMatch(
    val percent: Int,
    val label: String,
    val artistSimilarity: Float,
    val genreSimilarity: Float?,
    val moodSimilarity: Float?,
    /** Artists you both listen to, strongest common ground first. */
    val sharedArtists: List<SharedArtist>,
    /** Friend's top songs that are in your library (library song ids, in the friend's order). */
    val songsInLibrary: List<String>,
    /** Friend's top songs you don't have. */
    val songsMissing: List<SongRef>,
)

/** Pure matching logic for taste fingerprints. */
object TasteMatcher {

    /** Score for two listeners who share nothing. */
    const val FLOOR = 8

    private val combining = Regex("\\p{M}+")
    private val bracketFeat = Regex("[(\\[{][^)\\]}]*\\b(feat\\.?|ft\\.?|featuring|with)\\s[^)\\]}]*[)\\]}]")
    private val trailingFeat = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s.*$")
    private val brackets = Regex("[(\\[{][^)\\]}]*[)\\]}]")
    private val dashSuffix = Regex("\\s+-\\s+.*\\b(remaster(ed)?|live|version|edit|mix|mono|stereo|demo)\\b.*$")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")

    private fun base(s: String): String =
        combining.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    /** "The Beatles feat. X" → "beatles"; "AC/DC" → "ac dc"; "Beyoncé" → "beyonce". */
    fun normalizeArtist(name: String): String {
        var t = base(name)
        t = bracketFeat.replace(t, " ")
        t = trailingFeat.replace(t, "")
        t = t.replace("&", " and ")
        t = nonWord.replace(t, " ")
        t = spaces.replace(t, " ").trim()
        if (t.startsWith("the ") && t.length > 4) t = t.removePrefix("the ")
        return t
    }

    /** "Song (feat. X) [Remastered 2011]" → "song"; keeps the title when it is only brackets. */
    fun normalizeTitle(title: String): String {
        var t = base(title)
        t = bracketFeat.replace(t, " ")
        t = trailingFeat.replace(t, "")
        val noBrackets = brackets.replace(t, " ")
        if (noBrackets.isNotBlank()) t = noBrackets
        t = dashSuffix.replace(t, "")
        t = t.replace("&", " and ")
        t = nonWord.replace(t, " ")
        return spaces.replace(t, " ").trim()
    }

    fun songKey(title: String, artist: String): String = normalizeTitle(title) + "|" + normalizeArtist(artist)

    fun labelFor(percent: Int): String = when {
        percent >= 85 -> "Soulmates"
        percent >= 65 -> "Great match"
        percent >= 40 -> "Some common ground"
        else -> "Opposites attract"
    }

    fun cosine(a: Map<String, Float>, b: Map<String, Float>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var dot = 0.0
        a.forEach { (k, v) -> b[k]?.let { dot += v.toDouble() * it } }
        val na = sqrt(a.values.sumOf { it.toDouble() * it })
        val nb = sqrt(b.values.sumOf { it.toDouble() * it })
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (na * nb)).toFloat().coerceIn(0f, 1f)
    }

    private fun vector(list: List<WeightedName>, normalize: (String) -> String): Map<String, Float> {
        val m = LinkedHashMap<String, Float>()
        list.forEach { w ->
            val k = normalize(w.name)
            if (k.isNotEmpty()) m[k] = (m[k] ?: 0f) + w.weight
        }
        return m
    }

    private fun tagKey(s: String) = nonWord.replace(base(s), " ").trim()

    /**
     * Compatibility of [mine] and [theirs], 0–100. Identical tastes score 100; nothing in common ≈ [FLOOR].
     * Artists carry the score; genre/mood mixes (when both have them) only nudge it, after removing the
     * baseline similarity any two listeners have.
     */
    fun score(mine: TasteFingerprint, theirs: TasteFingerprint): Triple<Int, Float, Pair<Float?, Float?>> {
        val artist = cosine(vector(mine.artists, ::normalizeArtist), vector(theirs.artists, ::normalizeArtist))
        val genre = if (mine.genres.isNotEmpty() && theirs.genres.isNotEmpty())
            cosine(vector(mine.genres, ::tagKey), vector(theirs.genres, ::tagKey)) else null
        val mood = if (mine.moods.isNotEmpty() && theirs.moods.isNotEmpty())
            cosine(vector(mine.moods, ::tagKey), vector(theirs.moods, ::tagKey)) else null

        // Concave curve: sharing a third of your listening already feels like a real match.
        val artistPart = artist.toDouble().pow(0.6)
        var wArtist = 1.0
        var smart = 0.0
        if (genre != null) {
            wArtist -= 0.15
            smart += 0.15 * ((genre - 0.3) / 0.7).coerceIn(0.0, 1.0)
        }
        if (mood != null) {
            wArtist -= 0.10
            smart += 0.10 * ((mood - 0.6) / 0.4).coerceIn(0.0, 1.0)
        }
        val raw = (wArtist * artistPart + smart).coerceIn(0.0, 1.0)
        val percent = (FLOOR + (100 - FLOOR) * raw).roundToInt().coerceIn(0, 100)
        return Triple(percent, artist, genre to mood)
    }

    fun sharedArtists(mine: TasteFingerprint, theirs: TasteFingerprint): List<SharedArtist> {
        val theirsByKey = HashMap<String, Float>()
        theirs.artists.forEach { w ->
            val k = normalizeArtist(w.name)
            if (k.isNotEmpty()) theirsByKey[k] = maxOf(theirsByKey[k] ?: 0f, w.weight)
        }
        val seen = HashSet<String>()
        return mine.artists.mapNotNull { w ->
            val k = normalizeArtist(w.name)
            val other = theirsByKey[k] ?: return@mapNotNull null
            if (!seen.add(k)) return@mapNotNull null
            SharedArtist(w.name, minOf(w.weight, other))
        }.sortedByDescending { it.weight }
    }

    /** Splits [songs] into ids found in [library] and the ones that are missing. */
    fun resolveSongs(songs: List<SongRef>, library: List<LibrarySongKey>): Pair<List<String>, List<SongRef>> {
        val byTitle = HashMap<String, MutableList<Pair<LibrarySongKey, List<String>>>>()
        library.forEach { s ->
            val t = normalizeTitle(s.title)
            if (t.isEmpty()) return@forEach
            byTitle.getOrPut(t) { mutableListOf() } += s to s.artists.map(::normalizeArtist).filter { it.isNotEmpty() }
        }
        val found = ArrayList<String>()
        val missing = ArrayList<SongRef>()
        songs.forEach { ref ->
            val candidates = byTitle[normalizeTitle(ref.title)].orEmpty()
            val artist = normalizeArtist(ref.artist)
            val hit = candidates.firstOrNull { (_, artists) -> artist in artists }
                ?: candidates.firstOrNull { (_, artists) ->
                    artist.isNotEmpty() && artists.any { a -> a.length >= 3 && (artist.contains(a) || a.contains(artist)) }
                }
            if (hit != null && hit.first.id !in found) found += hit.first.id else if (hit == null) missing += ref
        }
        return found to missing
    }

    fun match(mine: TasteFingerprint, theirs: TasteFingerprint, library: List<LibrarySongKey>): TasteMatch {
        val (percent, artist, smart) = score(mine, theirs)
        val (found, missing) = resolveSongs(theirs.songs, library)
        return TasteMatch(
            percent = percent,
            label = labelFor(percent),
            artistSimilarity = artist,
            genreSimilarity = smart.first,
            moodSimilarity = smart.second,
            sharedArtists = sharedArtists(mine, theirs),
            songsInLibrary = found,
            songsMissing = missing,
        )
    }
}
