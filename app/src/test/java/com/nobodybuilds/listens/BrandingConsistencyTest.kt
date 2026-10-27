package com.nobodybuilds.listens

import com.nobodybuilds.listens.data.model.LibrarySourceFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Guards for the Nobody Listens rebrand that the compiler can't check. Unit tests run from the app module. */
class BrandingConsistencyTest {

    @Test
    fun `generated logo vectors stay under the aapt2 string limit`() {
        // aapt2 stores strings with a 15-bit length; a longer pathData is corrupted and the launcher silently
        // falls back to the default Android icon.
        val drawables = File("../shared/src/main/res/drawable").listFiles { f -> f.name.startsWith("nl_mark") }!!
        assertEquals(2, drawables.size)
        for (file in drawables) {
            val longest = Regex("""pathData="([^"]*)"""").findAll(file.readText()).maxOf { it.groupValues[1].length }
            assertTrue(longest < 32_767, "${file.name}: pathData of $longest characters")
        }
    }

    @Test
    fun `downloaded filter queries match the downloads folder`() {
        // MusicDao's queries are split across several interfaces in the database package.
        val dao = File("src/main/java/com/nobodybuilds/listens/data/database").listFiles { f -> f.extension == "kt" }!!
            .map { it.readText() }
            .filter { "filterMode = 3" in it }
            .joinToString("\n")
        // Each `filterMode = 3` clause must list exactly the downloads folders (YouTube Music, JioSaavn).
        val clauses = dao.lines().filter { "filterMode = 3" in it }
        assertTrue(clauses.isNotEmpty())
        for (clause in clauses) {
            val folders = Regex("""LIKE '%([^']*)'""").findAll(clause).map { it.groupValues[1] }.toSet()
            assertEquals(LibrarySourceFilter.DOWNLOADS_FOLDER_SUFFIXES.toSet(), folders, clause.trim())
        }
    }
}
