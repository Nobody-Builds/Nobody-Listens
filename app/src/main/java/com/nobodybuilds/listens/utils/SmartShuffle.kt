package com.nobodybuilds.listens.utils

import com.nobodybuilds.listens.data.model.Song
import kotlin.random.Random

/**
 * Smart shuffle: random order that
 *  1. pushes songs played recently (about the last 2 hours) to the end,
 *  2. avoids two songs by the same artist back to back when it can,
 *  3. stays uniformly random otherwise.
 *
 * Pure and deterministic for a given [Random], so it can be unit tested.
 */
object SmartShuffle {

    /** How far ahead we look for a different artist before accepting a repeat (keeps it O(n)). */
    private const val ARTIST_LOOKAHEAD = 256

    /** Window used by callers to decide what "recently played" means. */
    const val RECENT_WINDOW_MS: Long = 2L * 60L * 60L * 1000L

    /**
     * Returns [songs] in smart-shuffled order. When [startSong] is in the list it comes first.
     * Duplicate entries are kept.
     */
    fun order(
        songs: List<Song>,
        recentlyPlayedIds: Set<String>,
        startSong: Song?,
        random: Random = Random.Default,
    ): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val startIndex = startSong?.let { s -> songs.indexOfFirst { it.id == s.id } } ?: -1
        val order = orderIndices(songs, recentlyPlayedIds, startIndex, random)
        return order.map { songs[it] }
    }

    /**
     * Anchored variant used by the player's shuffle toggle. The song at [anchorIndex] keeps its
     * position (or moves to 0 when [startAtZero]); the songs that play next after it follow the
     * smart order and the most "recent" ones end up before it, i.e. at the very end of the cycle.
     */
    fun anchored(
        songs: List<Song>,
        anchorIndex: Int,
        recentlyPlayedIds: Set<String>,
        startAtZero: Boolean,
        random: Random = Random.Default,
    ): List<Song> {
        if (songs.size <= 1) return songs.toList()
        val anchor = anchorIndex.coerceIn(0, songs.lastIndex)
        val order = orderIndices(songs, recentlyPlayedIds, anchor, random) // anchor first
        if (startAtZero) return order.map { songs[it] }

        val rest = order.subList(1, order.size)
        val afterCount = songs.size - 1 - anchor
        val result = arrayOfNulls<Song>(songs.size)
        result[anchor] = songs[anchor]
        // Songs that play next: positions after the anchor take the head of the smart order.
        for (i in 0 until afterCount) result[anchor + 1 + i] = songs[rest[i]]
        // The tail (recently played ones last) wraps to the positions before the anchor.
        for (i in 0 until anchor) result[i] = songs[rest[afterCount + i]]
        return result.map { it!! }
    }

    /** Core on indices: returns a permutation of [songs] indices, [startIndex] first when >= 0. */
    internal fun orderIndices(
        songs: List<Song>,
        recentlyPlayedIds: Set<String>,
        startIndex: Int,
        random: Random,
    ): List<Int> {
        val fresh = ArrayList<Int>(songs.size)
        val recent = ArrayList<Int>()
        songs.indices.forEach { i ->
            if (i == startIndex) return@forEach
            if (songs[i].id in recentlyPlayedIds) recent += i else fresh += i
        }
        fresh.shuffleInPlace(random)
        recent.shuffleInPlace(random)

        val result = ArrayList<Int>(songs.size)
        var lastArtist: String? = null
        if (startIndex in songs.indices) {
            result += startIndex
            lastArtist = artistKey(songs[startIndex])
        }
        lastArtist = spreadArtists(fresh, songs, lastArtist, random)
        spreadArtists(recent, songs, lastArtist, random)
        result += fresh
        result += recent
        return result
    }

    /** Above this many distinct artists the cheap swap pass is used (adjacency is rare anyway). */
    private const val BUCKET_ARTIST_LIMIT = 64

    /**
     * Reorders [items] (already shuffled) so neighbours have different artists where possible.
     * Returns the artist of the last item.
     */
    private fun spreadArtists(items: MutableList<Int>, songs: List<Song>, previousArtist: String?, random: Random): String? {
        if (items.isEmpty()) return previousArtist
        val distinct = HashSet<String>()
        for (i in items) {
            distinct += artistKey(songs[i])
            if (distinct.size > BUCKET_ARTIST_LIMIT) return spreadBySwapping(items, songs, previousArtist)
        }
        return spreadByBuckets(items, songs, previousArtist, random)
    }

    /**
     * Exact pass for few artists: repeatedly picks a random song (weighted by how many songs each
     * artist has left) whose artist differs from the previous one, but places the dominant artist
     * first whenever waiting would make a back-to-back pair unavoidable later.
     */
    private fun spreadByBuckets(items: MutableList<Int>, songs: List<Song>, previousArtist: String?, random: Random): String? {
        val buckets = LinkedHashMap<String, ArrayDeque<Int>>()
        for (i in items) buckets.getOrPut(artistKey(songs[i])) { ArrayDeque() }.addLast(i)
        val keys = buckets.keys.toList()
        val queues = keys.map { buckets.getValue(it) }
        val counts = IntArray(keys.size) { queues[it].size }
        var last = keys.indexOf(previousArtist)
        var remaining = items.size
        var out = 0
        while (remaining > 0) {
            var maxIdx = -1
            for (a in counts.indices) if (maxIdx < 0 || counts[a] > counts[maxIdx]) maxIdx = a
            val pick = if (maxIdx != last && counts[maxIdx] * 2 > remaining) {
                maxIdx
            } else {
                val available = remaining - (if (last >= 0) counts[last] else 0)
                if (available <= 0) {
                    last // only the previous artist is left: unavoidable
                } else {
                    var r = random.nextInt(available)
                    var chosen = -1
                    for (a in counts.indices) {
                        if (a == last || counts[a] == 0) continue
                        if (r < counts[a]) { chosen = a; break }
                        r -= counts[a]
                    }
                    chosen
                }
            }
            items[out++] = queues[pick].removeFirst()
            counts[pick]--
            remaining--
            last = pick
        }
        return keys[last]
    }

    /** Cheap pass: when the next item repeats the previous artist, swap in the nearest different one. */
    private fun spreadBySwapping(items: MutableList<Int>, songs: List<Song>, previousArtist: String?): String? {
        var last = previousArtist
        for (i in items.indices) {
            val artist = artistKey(songs[items[i]])
            if (last != null && artist == last) {
                val limit = minOf(items.size, i + 1 + ARTIST_LOOKAHEAD)
                var swapWith = -1
                for (j in i + 1 until limit) {
                    if (artistKey(songs[items[j]]) != last) {
                        swapWith = j
                        break
                    }
                }
                if (swapWith >= 0) {
                    val tmp = items[i]
                    items[i] = items[swapWith]
                    items[swapWith] = tmp
                }
            }
            last = artistKey(songs[items[i]])
        }
        return last
    }

    internal fun artistKey(song: Song): String {
        val name = song.artist.trim().lowercase()
        return if (name.isNotEmpty()) name else "#${song.artistId}"
    }

    private fun MutableList<Int>.shuffleInPlace(random: Random) {
        for (i in lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            if (i != j) {
                val tmp = this[i]
                this[i] = this[j]
                this[j] = tmp
            }
        }
    }
}

/**
 * Process-wide switch read by [QueueUtils] so every shuffle path picks up smart shuffle
 * without threading a setting through each caller. The "Smart shuffle" setting is on by
 * default; [enabled] starts false (plain Fisher-Yates) only until the app mirrors that setting
 * into it at startup, and goes back to false when the user turns the setting off.
 */
object SmartShuffleRuntime {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var recentlyPlayedIds: Set<String> = emptySet()

    /** Refreshes [recentlyPlayedIds] from the play history; installed by the app. */
    @Volatile
    var refreshRecent: (suspend () -> Unit)? = null
}
