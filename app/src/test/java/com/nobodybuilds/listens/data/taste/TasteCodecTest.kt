package com.nobodybuilds.listens.data.taste

import com.nobodybuilds.listens.data.party.PartyQr
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TasteCodecTest {

    private fun sample(artists: Int = 30, songs: Int = 20, nameLen: Int = 12): TasteFingerprint {
        val a = (0 until artists).map { WeightedName("Artist number $it ${"x".repeat(it % 7)}".trim(), 1f - it / 40f) }
        return TasteFingerprint(
            name = "Abhiram".padEnd(nameLen, 'z'),
            artists = a,
            genres = listOf(WeightedName("Indie rock", 1f), WeightedName("Electronic", 0.5f)),
            moods = listOf(WeightedName("Calm", 1f), WeightedName("Energetic", 0.25f)),
            songs = (0 until songs).map { SongRef("Song title $it (Remastered)", a[it % artists].name) },
        )
    }

    @Test
    fun base45RoundTripsRandomBytes() {
        val rnd = Random(42)
        repeat(50) { n ->
            val data = ByteArray(n) { rnd.nextInt(256).toByte() }
            val enc = Base45.encode(data)
            assertEquals(Base45.encodedLength(n), enc.length)
            assertArrayEquals(data, Base45.decodeOrNull(enc))
        }
    }

    @Test
    fun base45MatchesRfcVectors() {
        assertEquals("BB8", Base45.encode("AB".toByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".toByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".toByteArray()))
        assertEquals("ietf!", String(Base45.decodeOrNull("QED8WEX0")!!))
        assertEquals(null, Base45.decodeOrNull("GGW"))
    }

    @Test
    fun qrFormRoundTrips() {
        val fp = sample()
        val code = TasteCodec.encodeForQr(fp)
        assertTrue(code.startsWith("LMT1:"))
        val back = TasteCodec.decode(code)
        assertEquals(fp.name, back.name)
        assertEquals(fp.artists.map { it.name }, back.artists.map { it.name })
        fp.artists.zip(back.artists).forEach { (a, b) -> assertEquals(a.weight, b.weight, 1f / 255f + 1e-4f) }
        assertEquals(fp.songs, back.songs)
        assertEquals(fp.genres.map { it.name }, back.genres.map { it.name })
        assertEquals(fp.moods.map { it.name }, back.moods.map { it.name })
    }

    @Test
    fun textFormRoundTripsInsideAMessage() {
        val fp = sample(artists = 10, songs = 5)
        val code = TasteCodec.encodeForText(fp)
        assertTrue(code.startsWith("LMT1:u"))
        assertTrue(code.none { it.isWhitespace() })
        val message = "Let's compare music taste on LMusic:\n\n$code\n"
        assertEquals(fp.songs, TasteCodec.decode(message).songs)
    }

    @Test
    fun songsWithUnknownArtistsAreStoredInline() {
        val fp = TasteFingerprint("A", listOf(WeightedName("Known", 1f)), songs = listOf(SongRef("x", "Known"), SongRef("y", "Other")))
        assertEquals(fp.songs, TasteCodec.decode(TasteCodec.encodeForQr(fp)).songs)
    }

    @Test
    fun unicodeAndLongNamesSurvive() {
        val fp = TasteFingerprint(
            name = "ശ്രീ 🎧",
            artists = listOf(WeightedName("Beyoncé", 1f), WeightedName("坂本龍一", 0.5f), WeightedName("🎸".repeat(40), 0.2f)),
        )
        val back = TasteCodec.decode(TasteCodec.encodeForQr(fp))
        assertEquals("ശ്രീ 🎧", back.name)
        assertEquals("坂本龍一", back.artists[1].name)
        assertTrue(back.artists[2].name.toByteArray().size <= 64)
        assertTrue(back.artists[2].name.isNotEmpty() && back.artists[2].name.replace("🎸", "").isEmpty())
    }

    @Test
    fun hugeFingerprintIsTruncatedToFitQr() {
        val long = TasteFingerprint(
            name = "N",
            artists = (0 until 30).map { i -> WeightedName((0 until 60).map { 'a' + ((it * 7 + i * 13) % 26) }.joinToString(""), 1f) },
            songs = (0 until 20).map { i -> SongRef((0 until 80).map { 'a' + ((it * 11 + i * 3) % 26) }.joinToString(""), "Inline artist $i ${"q".repeat(40)}") },
        )
        val code = TasteCodec.encodeForQr(long)
        assertTrue(code.length <= TasteCodec.DEFAULT_MAX_QR_CHARS, "length ${code.length}")
        val back = TasteCodec.decode(code)
        assertTrue(back.artists.isNotEmpty())
        // QR version ≤ 25 (117 modules) at error correction M.
        val matrix = PartyQr.matrix(code, margin = 0)
        assertTrue(matrix.width <= 117, "modules ${matrix.width}")
    }

    @Test
    fun typicalFingerprintMakesASmallQr() {
        val code = TasteCodec.encodeForQr(sample())
        assertTrue(PartyQr.matrix(code, margin = 0).width <= 117)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(TasteCodec.InvalidCodeException::class.java) { TasteCodec.decode("hello") }
        assertThrows(TasteCodec.InvalidCodeException::class.java) { TasteCodec.decode("LMT1:ABC") }
        assertThrows(TasteCodec.InvalidCodeException::class.java) { TasteCodec.decode("LMT1:uAAAA") }
        assertThrows(TasteCodec.InvalidCodeException::class.java) { TasteCodec.decode("LMT1:") }
    }

    @Test
    fun builderNormalisesWeightsAndMergesSpellings() {
        val fp = TasteFingerprintBuilder.build(
            name = "  ",
            artistListeningMs = mapOf("The Beatles" to 400.0, "Beatles" to 100.0, "Radiohead" to 100.0) +
                (0 until 40).associate { "Filler $it" to 1.0 },
            topSongs = (0 until 30).map { SongRef("t$it", "a") } + SongRef("t1", "a"),
            moods = mapOf("calm" to 0.6f, "happy" to 0.3f),
        )
        assertEquals("Friend", fp.name)
        assertEquals("The Beatles", fp.artists.first().name)
        assertEquals(1f, fp.artists.first().weight)
        assertEquals(TasteFingerprintBuilder.MAX_ARTISTS, fp.artists.size)
        assertEquals(TasteFingerprintBuilder.MAX_SONGS, fp.songs.size)
        assertEquals(0.5f, fp.moods[1].weight, 1e-4f)
    }
}
