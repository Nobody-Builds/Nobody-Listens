package com.nobodybuilds.listens.data.party

/**
 * In-memory, read-only search over the host's library for party guests: title, artist and
 * album, every word must match, paginated. Pure Kotlin so it can be unit-tested.
 */
class PartyLibraryIndex(entries: List<Entry>) {

    data class Entry(val song: PartySong, val dateAdded: Long)

    data class Page(val items: List<PartySong>, val page: Int, val hasMore: Boolean, val total: Int)

    private val songs: List<PartySong> = entries.map { it.song }
    private val byId: Map<String, PartySong> = songs.associateBy { it.id }
    private val titles: Array<String> = Array(songs.size) { normalize(songs[it].title) }
    private val artists: Array<String> = Array(songs.size) { normalize(songs[it].artist) }
    private val haystacks: Array<String> = Array(songs.size) {
        normalize("${songs[it].title} ${songs[it].artist} ${songs[it].album}")
    }
    private val recent: List<PartySong> = entries.sortedByDescending { it.dateAdded }.map { it.song }

    val size: Int get() = songs.size

    operator fun get(id: String): PartySong? = byId[id]

    fun search(query: String, page: Int, pageSize: Int = 20): Page {
        val safePage = page.coerceAtLeast(0)
        val size = pageSize.coerceIn(1, 50)
        val q = normalize(query.take(80))
        val results: List<PartySong> = if (q.isBlank()) {
            recent
        } else {
            val words = q.split(' ').filter { it.isNotBlank() }
            val matches = ArrayList<Pair<Int, Int>>() // (rank, index)
            for (i in songs.indices) {
                val hay = haystacks[i]
                if (words.all { hay.contains(it) }) {
                    val rank = when {
                        titles[i].startsWith(q) -> 0
                        titles[i].contains(q) -> 1
                        artists[i].startsWith(q) -> 2
                        artists[i].contains(q) -> 3
                        else -> 4
                    }
                    matches += rank to i
                }
            }
            matches.sortWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { titles[it.second] })
            matches.map { songs[it.second] }
        }
        val from = (safePage * size).coerceAtMost(results.size)
        val to = (from + size).coerceAtMost(results.size)
        return Page(results.subList(from, to), safePage, to < results.size, results.size)
    }

    companion object {
        fun normalize(s: String): String = s.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}
