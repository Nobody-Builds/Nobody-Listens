package com.nobodybuilds.listens.data.focus

import kotlinx.serialization.Serializable

/** What plays during focus rounds. Breaks are always quiet. */
@Serializable
enum class FocusMusicSource(val label: String, val blurb: String) {
    Smart("Focus mix", "Few vocals, steady energy"),
    Library("Instrumental", "Calm songs from your library"),
    Lofi("Lofi", "Made on your phone"),
    Noise("Noise", "Steady sound, made on your phone"),
    Silence("Silence", "Just the timer");
}

/** Picks instrumental-leaning songs from the library when the Smart Focus mix isn't available. */
object FocusMusicFilter {
    private val genreWords = listOf(
        "classical", "ambient", "instrumental", "jazz", "lofi", "lo-fi", "piano", "soundtrack", "score",
        "new age", "chillhop",
    )

    fun isInstrumentalLeaning(title: String, genre: String?): Boolean {
        val g = genre?.lowercase().orEmpty()
        if (g.isNotBlank() && genreWords.any { it in g }) return true
        return "instrumental" in title.lowercase()
    }
}
