package com.twilighty.lmusic

import com.twilighty.lmusic.data.model.LibrarySourceFilter
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
        val dao = File("src/main/java/com/twilighty/lmusic/data/database/MusicDao.kt").readText()
        val folders = Regex("""filterMode = 3 AND [^']*LIKE '%([^']*)'""").findAll(dao).map { it.groupValues[1] }.toList()
        assertTrue(folders.isNotEmpty())
        assertEquals(setOf(LibrarySourceFilter.DOWNLOADS_FOLDER_SUFFIX), folders.toSet())
    }
}
