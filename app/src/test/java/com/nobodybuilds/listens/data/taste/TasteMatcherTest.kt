package com.nobodybuilds.listens.data.taste

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteMatcherTest {

    private fun fp(vararg artists: Pair<String, Float>, songs: List<SongRef> = emptyList(),
                   genres: List<WeightedName> = emptyList(), moods: List<WeightedName> = emptyList()) =
        TasteFingerprint("X", artists.map { WeightedName(it.first, it.second) }, genres, moods, songs)

    @Test
    fun normalizesArtistNames() {
        assertEquals("beatles", TasteMatcher.normalizeArtist("The Beatles"))
        assertEquals("beyonce", TasteMatcher.normalizeArtist("Beyoncé feat. Jay-Z"))
        assertEquals("daft punk", TasteMatcher.normalizeArtist("Daft Punk (feat. Pharrell)"))
        assertEquals("ac dc", TasteMatcher.normalizeArtist("AC/DC"))
        assertEquals("simon and garfunkel", TasteMatcher.normalizeArtist("Simon & Garfunkel"))
        assertEquals("the", TasteMatcher.normalizeArtist("The"))
        assertEquals("a r rahman", TasteMatcher.normalizeArtist("A.R. Rahman ft. Someone"))
    }

    @Test
    fun normalizesTitles() {
        assertEquals("yesterday", TasteMatcher.normalizeTitle("Yesterday - Remastered 2009"))
        assertEquals("get lucky", TasteMatcher.normalizeTitle("Get Lucky (feat. Pharrell Williams) [Radio Edit]"))
        assertEquals("intro", TasteMatcher.normalizeTitle("(Intro)"))
    }

    @Test
    fun identicalTastesScore100() {
        val a = fp("Radiohead" to 1f, "Björk" to 0.6f, "Portishead" to 0.3f,
            genres = listOf(WeightedName("rock", 1f)), moods = listOf(WeightedName("sad", 1f)))
        val m = TasteMatcher.match(a, a.copy(name = "Y"), emptyList())
        assertEquals(100, m.percent)
        assertEquals("Soulmates", m.label)
    }

    @Test
    fun fuzzyNamesStillMatch() {
        val a = fp("The Beatles" to 1f, "Beyoncé" to 0.5f)
        val b = fp("beatles" to 1f, "Beyonce feat. Jay-Z" to 0.5f)
        assertEquals(100, TasteMatcher.match(a, b, emptyList()).percent)
    }

    @Test
    fun nothingSharedIsLowButNotZero() {
        val a = fp("Radiohead" to 1f, "Björk" to 0.5f)
        val b = fp("Taylor Swift" to 1f, "Drake" to 0.5f)
        val p = TasteMatcher.match(a, b, emptyList()).percent
        assertTrue(p in 5..15, "was $p")
        assertEquals("Opposites attract", TasteMatcher.labelFor(p))
    }

    @Test
    fun nothingSharedWithSimilarMoodsStaysLow() {
        val moods = listOf(WeightedName("happy", 1f), WeightedName("calm", 0.6f))
        val a = fp("Radiohead" to 1f, moods = moods, genres = listOf(WeightedName("rock", 1f)))
        val b = fp("Drake" to 1f, moods = moods.reversed(), genres = listOf(WeightedName("hip hop", 1f)))
        val p = TasteMatcher.match(a, b, emptyList()).percent
        assertTrue(p in 5..20, "was $p")
    }

    @Test
    fun partialOverlapLandsInTheMiddle() {
        val a = fp("A" to 1f, "B" to 0.8f, "C" to 0.6f, "D" to 0.4f)
        val b = fp("A" to 1f, "B" to 0.5f, "E" to 0.9f, "F" to 0.7f)
        val p = TasteMatcher.match(a, b, emptyList()).percent
        assertTrue(p in 40..85, "was $p")
    }

    @Test
    fun scoreGrowsWithOverlap() {
        val a = fp("A" to 1f, "B" to 1f, "C" to 1f, "D" to 1f)
        val one = TasteMatcher.match(a, fp("A" to 1f, "X" to 1f, "Y" to 1f, "Z" to 1f), emptyList()).percent
        val two = TasteMatcher.match(a, fp("A" to 1f, "B" to 1f, "Y" to 1f, "Z" to 1f), emptyList()).percent
        val three = TasteMatcher.match(a, fp("A" to 1f, "B" to 1f, "C" to 1f, "Z" to 1f), emptyList()).percent
        assertTrue(one < two && two < three, "$one $two $three")
    }

    @Test
    fun sharedArtistsRankedByMinWeight() {
        val a = fp("A" to 1f, "B" to 0.9f, "C" to 0.2f)
        val b = fp("A" to 0.1f, "B" to 0.8f, "C" to 1f)
        assertEquals(listOf("B", "C", "A"), TasteMatcher.sharedArtists(a, b).map { it.name })
    }

    @Test
    fun resolvesSongsAgainstLibrary() {
        val library = listOf(
            LibrarySongKey("1", "Yesterday (Remastered 2009)", listOf("The Beatles")),
            LibrarySongKey("2", "Get Lucky", listOf("Daft Punk", "Pharrell Williams")),
            LibrarySongKey("3", "Creep", listOf("Radiohead")),
        )
        val songs = listOf(
            SongRef("Yesterday", "Beatles"),
            SongRef("Get Lucky (feat. Pharrell Williams)", "Daft Punk feat. Pharrell Williams"),
            SongRef("Creep", "Stone Temple Pilots"),
            SongRef("Unknown song", "Nobody"),
        )
        val (found, missing) = TasteMatcher.resolveSongs(songs, library)
        assertEquals(listOf("1", "2"), found)
        assertEquals(listOf("Creep", "Unknown song"), missing.map { it.title })
    }

    @Test
    fun roundTripThroughCodecKeepsScore() {
        val a = fp("Radiohead" to 1f, "Björk" to 0.6f)
        val b = fp("Radiohead" to 0.7f, "Massive Attack" to 1f)
        val direct = TasteMatcher.match(a, b, emptyList()).percent
        val viaQr = TasteMatcher.match(a, TasteCodec.decode(TasteCodec.encodeForQr(b)), emptyList()).percent
        assertTrue(kotlin.math.abs(direct - viaQr) <= 1)
    }
}
