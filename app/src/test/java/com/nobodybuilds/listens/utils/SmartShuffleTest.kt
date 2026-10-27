package com.nobodybuilds.listens.utils

import com.nobodybuilds.listens.data.library.MusicFolderPaths
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.update.VersionComparator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SmartShuffleTest {

    @Test
    fun order_keepsEverySongAndStartsWithStartSong() {
        val songs = buildSongs(50, artists = 5)
        val start = songs[17]

        val result = SmartShuffle.order(songs, emptySet(), start, Random(1))

        assertEquals(songs.size, result.size)
        assertEquals(start.id, result.first().id)
        assertEquals(songs.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun order_pushesRecentlyPlayedSongsToTheEnd() {
        val songs = buildSongs(40, artists = 8)
        val recent = songs.take(10).map { it.id }.toSet()

        val result = SmartShuffle.order(songs, recent, startSong = null, random = Random(7))

        val tail = result.takeLast(recent.size).map { it.id }.toSet()
        assertEquals(recent, tail)
        assertTrue(result.dropLast(recent.size).none { it.id in recent })
    }

    @Test
    fun order_avoidsSameArtistBackToBackWhenPossible() {
        // 3 artists, evenly sized: a perfect spread always exists.
        val songs = buildSongs(60, artists = 3)

        repeat(20) { seed ->
            val result = SmartShuffle.order(songs, emptySet(), songs[0], Random(seed))
            result.zipWithNext().forEach { (a, b) ->
                assertFalse(a.artist == b.artist, "Seed $seed placed ${a.artist} twice in a row")
            }
        }
    }

    @Test
    fun order_isStillRandom() {
        val songs = buildSongs(30, artists = 10)
        val first = SmartShuffle.order(songs, emptySet(), null, Random(1)).map { it.id }
        val second = SmartShuffle.order(songs, emptySet(), null, Random(2)).map { it.id }
        assertTrue(first != second)
    }

    @Test
    fun anchored_keepsAnchorInPlaceAndLosesNothing() {
        val songs = buildSongs(25, artists = 4)
        val recent = setOf(songs[3].id, songs[20].id)

        val result = SmartShuffle.anchored(songs, anchorIndex = 12, recentlyPlayedIds = recent, startAtZero = false, random = Random(3))

        assertEquals(songs.size, result.size)
        assertEquals(songs[12].id, result[12].id)
        assertEquals(songs.map { it.id }.toSet(), result.map { it.id }.toSet())
        // Recently played songs never play right after the current one.
        assertTrue(result[13].id !in recent)
    }

    @Test
    fun anchored_startAtZeroPutsAnchorFirst() {
        val songs = buildSongs(10, artists = 2)
        val result = SmartShuffle.anchored(songs, anchorIndex = 6, recentlyPlayedIds = emptySet(), startAtZero = true, random = Random(5))
        assertEquals(songs[6].id, result[0].id)
        assertEquals(songs.size, result.size)
    }

    @Test
    fun order_handlesEmptyAndSingle() {
        assertTrue(SmartShuffle.order(emptyList(), emptySet(), null).isEmpty())
        val one = buildSongs(1, artists = 1)
        assertEquals(one, SmartShuffle.order(one, setOf(one[0].id), one[0]))
    }

    @Test
    fun versionComparator_comparesSemantically() {
        assertTrue(VersionComparator.isNewer("v1.2.10", "1.2.9"))
        assertTrue(VersionComparator.isNewer("2.0", "1.9.9"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-beta.2"))
        assertFalse(VersionComparator.isNewer("1.0.0-beta.1", "1.0.0"))
        // CI non-release builds count as their base version.
        assertEquals(0, VersionComparator.compare("1.2.0-build.57", "1.2.0"))
        assertFalse(VersionComparator.isNewer("1.2.0", "1.2.0-build.57"))
        assertTrue(VersionComparator.isNewer("1.2.1", "1.2.0-build.57"))
    }

    @Test
    fun musicFolderPaths_topLevelFolders() {
        val tops = MusicFolderPaths.topLevelFolders(
            listOf(
                "/storage/emulated/0/Music/Artist/Album",
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Download",
                "/storage/emulated/0",
                "/storage/1234-ABCD/Songs/Old",
                "/storage/emulated/0/Android/data/com.example/cache",
            )
        )
        assertEquals(
            setOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Download",
                "/storage/emulated/0",
                "/storage/1234-ABCD/Songs",
                "/storage/emulated/0/Android/data/com.example/cache",
            ),
            tops
        )
    }

    private fun buildSongs(count: Int, artists: Int): List<Song> = List(count) { index ->
        val artist = index % artists
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = "Artist $artist",
            artistId = artist.toLong(),
            album = "Album",
            albumId = 1L,
            path = "/tmp/song-$index.mp3",
            contentUriString = "content://lmusic/song/$index",
            albumArtUriString = null,
            duration = 180_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
    }
}
