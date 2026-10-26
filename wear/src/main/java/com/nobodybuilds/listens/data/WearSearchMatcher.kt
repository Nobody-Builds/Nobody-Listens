package com.nobodybuilds.listens.data

import java.text.Normalizer

/** Matches songs on the watch: every word of the query must appear in a field, ignoring case and accents. */
object WearSearchMatcher {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    fun matches(query: String, vararg fields: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val text = fields.joinToString(" ") { normalize(it) }
        return words.all { it in text }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()
}
