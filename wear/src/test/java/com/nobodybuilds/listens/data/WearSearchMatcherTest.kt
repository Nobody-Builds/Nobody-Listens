package com.nobodybuilds.listens.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearSearchMatcherTest {

    @Test
    fun `every word must appear somewhere in the song`() {
        assertTrue(WearSearchMatcher.matches("arijit kesariya", "Kesariya", "Arijit Singh", "Brahmastra"))
        assertTrue(WearSearchMatcher.matches("brahm", "Kesariya", "Arijit Singh", "Brahmastra"))
        assertFalse(WearSearchMatcher.matches("arijit tum", "Kesariya", "Arijit Singh", "Brahmastra"))
    }

    @Test
    fun `case and accents are ignored`() {
        assertTrue(WearSearchMatcher.matches("BEYONCE", "Halo", "Beyoncé", ""))
        assertTrue(WearSearchMatcher.matches("sigur ros", "Hoppípolla", "Sigur Rós", "Takk..."))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertFalse(WearSearchMatcher.matches("   ", "Halo", "Beyoncé", ""))
    }
}
