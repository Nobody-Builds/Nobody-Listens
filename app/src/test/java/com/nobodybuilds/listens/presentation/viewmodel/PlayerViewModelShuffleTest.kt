package com.nobodybuilds.listens.presentation.viewmodel

import androidx.media3.common.MediaItem
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.utils.MediaItemBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
@DisplayName("Shuffle Functionality")
class PlayerViewModelShuffleTest : PlayerViewModelTestBase() {

    private val song1 = Song(id = "1", title = "Song 1", artist = "Artist A", genre = "Rock", albumArtUriString = "cover1.png", artistId = 1L, albumId = 1L, contentUriString = "content://dummy/1", duration = 180000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song2 = Song(id = "2", title = "Song 2", artist = "Artist B", genre = "Pop", albumArtUriString = "cover2.png", artistId = 2L, albumId = 2L, contentUriString = "content://dummy/2", duration = 200000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")
    private val song3 = Song(id = "3", title = "Song 3", artist = "Artist C", genre = "Jazz", albumArtUriString = "cover3.png", artistId = 3L, albumId = 3L, contentUriString = "content://dummy/3", duration = 210000L, bitrate = null, sampleRate = null, album = "Album", path = "path", mimeType = "audio/mpeg")

    /**
     * Shuffle-all paths read the Library source filter from LibraryStateHolder, ask the repository
     * for every matching id in random order and resolve them (here from the in-memory cache).
     */
    private fun stubLibrarySource(
        songs: List<Song>,
        filter: StorageFilter = StorageFilter.ALL
    ) {
        coEvery { mockLibraryStateHolder.effectiveStorageFilterNow() } returns filter
        every { mockLibraryStateHolder.allSongsById } returns MutableStateFlow(songs.associateBy { it.id })
        coEvery { mockMusicRepository.getShuffledSongIds(filter) } returns songs.map { it.id.toLong() }
    }

    private fun stubShuffledPlayback(
        songs: List<Song>,
        queueName: String,
        startSong: Song = songs.first()
    ) {
        coEvery {
            mockQueueStateHolder.prepareShuffledQueueSuspending(songs, queueName, true)
        } returns Pair(songs, startSong)

        mockkObject(MediaItemBuilder)
        every { MediaItemBuilder.build(any()) } returns MediaItem.Builder()
            .setMediaId("test")
            .setUri("file:///tmp/test.mp3")
            .build()
        val mockedPlaybackUri = mockk<android.net.Uri>(relaxed = true)
        every { mockedPlaybackUri.scheme } returns "file"
        every { MediaItemBuilder.playbackUri(any<Song>()) } returns mockedPlaybackUri
    }

    @Test
    fun `shuffleAllSongs calls prepareShuffledQueue with random songs at index zero`() = runTest {
        val randomSongs = listOf(song2, song3, song1)
        stubLibrarySource(randomSongs)
        stubShuffledPlayback(randomSongs, "All Songs (Shuffled)", startSong = song2)

        playerViewModel.shuffleAllSongs()
        advanceUntilIdle()

        coVerify { mockMusicRepository.getShuffledSongIds(StorageFilter.ALL) }
        coVerify(exactly = 0) { mockMusicRepository.getRandomSongs(any()) }
        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                randomSongs,
                "All Songs (Shuffled)",
                true
            )
        }
    }

    @Test
    fun `playRandomSong calls prepareShuffledQueue with startAtZero`() = runTest {
        val randomSongs = listOf(song3, song1, song2)
        stubLibrarySource(randomSongs)
        stubShuffledPlayback(randomSongs, "All Songs (Shuffled)", startSong = song3)

        playerViewModel.playRandomSong()
        advanceUntilIdle()

        coVerify { mockMusicRepository.getShuffledSongIds(StorageFilter.ALL) }
        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                randomSongs,
                "All Songs (Shuffled)",
                true
            )
        }
    }

    @Test
    fun `shuffleFavoriteSongs calls prepareShuffledQueue with startAtZero`() = runTest {
        val favoriteSongs = listOf(song1, song3)
        // Phone-only filter must reach the favourites query.
        coEvery { mockLibraryStateHolder.effectiveStorageFilterNow() } returns StorageFilter.OFFLINE
        coEvery { mockMusicRepository.getFavoriteSongsOnce(StorageFilter.OFFLINE) } returns favoriteSongs
        stubShuffledPlayback(favoriteSongs, "Liked Songs (Shuffled)", startSong = song1)

        playerViewModel.shuffleFavoriteSongs()
        advanceUntilIdle()

        coVerify { mockMusicRepository.getFavoriteSongsOnce(StorageFilter.OFFLINE) }
        coVerify(exactly = 0) { mockMusicRepository.getFavoriteSongsOnce(StorageFilter.ALL) }
        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                favoriteSongs,
                "Liked Songs (Shuffled)",
                true
            )
        }
    }

    @Test
    fun `shuffleRandomAlbum calls prepareShuffledQueue with startAtZero`() = runTest {
        val album = Album(
            id = 77L,
            title = "Album Roulette",
            artist = "Artist A",
            year = 2024,
            dateAdded = 0L,
            albumArtUriString = null,
            songCount = 3
        )
        every { mockLibraryStateHolder.albums } returns MutableStateFlow(persistentListOf(album))
        every { mockMusicRepository.getSongsForAlbum(album.id) } returns flowOf(listOf(song1, song2, song3))
        coEvery { mockLibraryStateHolder.effectiveStorageFilterNow() } returns StorageFilter.ALL
        stubShuffledPlayback(listOf(song1, song2, song3), album.title, startSong = song2)

        playerViewModel.shuffleRandomAlbum()
        advanceUntilIdle()

        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                listOf(song1, song2, song3),
                "Album Roulette",
                true
            )
        }
    }

    @Test
    fun `shuffleRandomArtist calls prepareShuffledQueue with startAtZero`() = runTest {
        val artist = Artist(id = 88L, name = "Artist Roulette", songCount = 3)
        every { mockLibraryStateHolder.artists } returns MutableStateFlow(persistentListOf(artist))
        every { mockMusicRepository.getSongsForArtist(artist.id) } returns flowOf(listOf(song3, song2, song1))
        coEvery { mockLibraryStateHolder.effectiveStorageFilterNow() } returns StorageFilter.ALL
        stubShuffledPlayback(listOf(song3, song2, song1), artist.name, startSong = song3)

        playerViewModel.shuffleRandomArtist()
        advanceUntilIdle()

        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                listOf(song3, song2, song1),
                "Artist Roulette",
                true
            )
        }
    }

    @Test
    fun `shuffleRandomAlbum drops cloud songs when the Library shows phone only`() = runTest {
        val album = Album(id = 78L, title = "Mixed Album", artist = "Artist A", year = 2024, dateAdded = 0L, albumArtUriString = null, songCount = 2)
        val cloudSong = song2.copy(id = "-9", contentUriString = "telegram://1/9", telegramFileId = 9)
        every { mockLibraryStateHolder.albums } returns MutableStateFlow(persistentListOf(album))
        every { mockMusicRepository.getSongsForAlbum(album.id) } returns flowOf(listOf(song1, cloudSong))
        coEvery { mockLibraryStateHolder.effectiveStorageFilterNow() } returns StorageFilter.OFFLINE
        stubShuffledPlayback(listOf(song1), album.title, startSong = song1)

        playerViewModel.shuffleRandomAlbum()
        advanceUntilIdle()

        coVerify(timeout = 2_000) { mockQueueStateHolder.prepareShuffledQueueSuspending(listOf(song1), "Mixed Album", true) }
    }

    @Test
    fun `triggerShuffleAllFromTile uses startAtZero when library is already loaded`() = runTest {
        val songs = listOf(song1, song2, song3)
        _allSongsFlow.value = songs.toImmutableList()
        // The tile shuffles the whole source-filtered library from the repository ids.
        stubLibrarySource(songs, StorageFilter.OFFLINE)
        stubShuffledPlayback(songs, "All Songs (Shuffled)", startSong = song1)

        playerViewModel.triggerShuffleAllFromTile()
        advanceUntilIdle()

        coVerify { mockMusicRepository.getShuffledSongIds(StorageFilter.OFFLINE) }
        coVerify(exactly = 0) { mockMusicRepository.getRandomSongs(any()) }
        coVerify(timeout = 2_000) {
            mockQueueStateHolder.prepareShuffledQueueSuspending(
                songs,
                "All Songs (Shuffled)",
                true
            )
        }
    }
}
