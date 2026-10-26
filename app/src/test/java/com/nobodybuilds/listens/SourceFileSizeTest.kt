package com.nobodybuilds.listens

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Project rule: no Kotlin source file over 700 lines. Split by responsibility instead. Runs from the app module. */
class SourceFileSizeTest {

    @Test
    fun `every Kotlin file stays within 700 lines`() {
        val modules = listOf("app", "wear", "shared", "baselineprofile").map { File("../$it/src") }
        val files = modules.filter { it.isDirectory }.flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
        assertTrue(files.size > 100, "found only ${files.size} Kotlin files")
        val tooLong = files.map { it to it.readLines().size }.filter { (_, lines) -> lines > MAX_LINES }
        assertTrue(tooLong.isEmpty(), tooLong.joinToString("\n") { (file, lines) -> "$lines lines: ${file.invariantSeparatorsPath}" })
    }

    private companion object {
        const val MAX_LINES = 700
    }
}
