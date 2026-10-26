package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.Song
import io.mockk.every
import io.mockk.slot
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
@DisplayName("GetSongUrisForGenre Tests")
class PlayerViewModelGenreTest : PlayerViewModelTestBase() {

    private val song1 = Song(id = "1", title = "Song 1", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover1.png", artistId = 1L, albumId = 1L, contentUriString = "content://dummy/1", duration = 180000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song2 = Song(id = "2", title = "Song 2", artist = "Artist B", genre = "Pop", albumArtUriString = "pop_cover1.png", artistId = 2L, albumId = 2L, contentUriString = "content://dummy/2", duration = 200000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song3 = Song(id = "3", title = "Song 3", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover2.png", artistId = 3L, albumId = 3L, contentUriString = "content://dummy/3", duration = 190000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song4 = Song(id = "4", title = "Song 4", artist = "Artist C", genre = "Jazz", albumArtUriString = "jazz_cover1.png", artistId = 4L, albumId = 4L, contentUriString = "content://dummy/4", duration = 210000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song5 = Song(id = "5", title = "Song 5", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover3.png", artistId = 5L, albumId = 5L, contentUriString = "content://dummy/5", duration = 220000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song6 = Song(id = "6", title = "Song 6", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover4.png", artistId = 6L, albumId = 6L, contentUriString = "content://dummy/6", duration = 230000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song7 = Song(id = "7", title = "Song 7", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover5.png", artistId = 7L, albumId = 7L, contentUriString = "content://dummy/7", duration = 240000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song8 = Song(id = "8", title = "Song 8", artist = "Artist A", genre = "Rock", albumArtUriString = "", artistId = 8L, albumId = 8L, contentUriString = "content://dummy/8", duration = 250000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg") // Empty cover
    private val song9 = Song(id = "9", title = "Song 9", artist = "Artist D", genre = null, albumArtUriString = "null_genre_cover.png", artistId = 9L, albumId = 9L, contentUriString = "content://dummy/9", duration = 260000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg") // Null genre

    private fun setupViewModelWithSongs(songs: List<Song>) {
        _allSongsFlow.value = songs.toImmutableList()
        testDispatcher.scheduler.advanceUntilIdle() // Ensure ViewModel collects the update
        
        // Still mock repository for getMusicByGenre calls
        val genreSlot = slot<String>()
        every { mockMusicRepository.getMusicByGenre(capture(genreSlot)) } answers {
            val genre = genreSlot.captured
            val filtered = songs.filter { it.genre.equals(genre, ignoreCase = true) }
            flowOf(filtered)
        }
    }

    @Test
    fun `single song with genre returns its cover`() = runTest {
        val testSongs = listOf(song1)
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertEquals(listOf("rock_cover1.png"), uris)
    }

    @Test
    fun `multiple songs with same genre return all covers`() = runTest {
        val testSongs = listOf(song1, song3)
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertEquals(listOf("rock_cover1.png", "rock_cover2.png"), uris)
    }

    @Test
    fun `no songs with genre returns empty list`() = runTest {
        val testSongs = listOf(song2, song4) // Pop and Jazz
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertTrue(uris.isEmpty())
    }
    
    @Test
    fun `genre with more than 3 songs returns first 4 valid covers`() = runTest {
         // song1, song3, song5, song6, song7 are all Rock (5 songs)
        val testSongs = listOf(song1, song3, song5, song6, song7)
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertEquals(4, uris.size)
        assertEquals(listOf("rock_cover1.png", "rock_cover2.png", "rock_cover3.png", "rock_cover4.png"), uris)
    }

    @Test
    fun `songs with blank album art are ignored`() = runTest {
        val testSongs = listOf(song1, song8) // song8 has empty cover
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertEquals(listOf("rock_cover1.png"), uris) // Only song1's cover
    }

    @Test
    fun `empty allSongs list returns empty list for any genre`() = runTest {
        setupViewModelWithSongs(emptyList())

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertTrue(uris.isEmpty())
    }

    @Test
    fun `case insensitive genre matching`() = runTest {
        val testSongs = listOf(song1, song3)
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("rOcK").first()
        assertEquals(listOf("rock_cover1.png", "rock_cover2.png"), uris)
    }

    @Test
    fun `genre not found among songs with null genres`() = runTest {
        val testSongs = listOf(song9) // song9 has null genre
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertTrue(uris.isEmpty())
    }
     @Test
    fun `songs with null genre do not match specific genre query`() = runTest {
        val testSongs = listOf(song1, song9)
        setupViewModelWithSongs(testSongs)

        val uris = playerViewModel.getSongUrisForGenre("Rock").first()
        assertEquals(listOf("rock_cover1.png"), uris)
    }
}
