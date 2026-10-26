package com.nobodybuilds.listens.data.stream

import com.nobodybuilds.listens.data.jiosaavn.JioSaavnCollection
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnItemKind
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicItemKind
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicPlaylist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Importing a YouTube Music or JioSaavn playlist or album as your own playlist. */
class StreamingPlaylistsTest {

    private val repository = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val playlists = StreamingPlaylists(repository)

    @Test
    fun `the first import creates the playlist with the songs in order`() = runTest {
        coEvery { repository.getPlaylistsOnce() } returns emptyList()

        playlists.importOrUpdate("ytmusic_PL1", "Road trip", listOf(-3L, -1L, -2L), source = "YTMUSIC")

        coVerify(exactly = 1) {
            repository.createPlaylist(name = "Road trip", songIds = listOf("-3", "-1", "-2"), customId = "ytmusic_PL1", source = "YTMUSIC")
        }
        coVerify(exactly = 0) { repository.updatePlaylist(any()) }
    }

    @Test
    fun `importing again refreshes the same playlist instead of adding a copy`() = runTest {
        val earlier = Playlist(id = "jiosaavn_album_42", name = "Old name", songIds = listOf("-1"), source = "JIOSAAVN")
        coEvery { repository.getPlaylistsOnce() } returns listOf(earlier)
        val updated = slot<Playlist>()
        coEvery { repository.updatePlaylist(capture(updated)) } returns Unit

        playlists.importOrUpdate("jiosaavn_album_42", "New name", listOf(-1L, -5L), source = "JIOSAAVN")

        coVerify(exactly = 0) { repository.createPlaylist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals("jiosaavn_album_42", updated.captured.id)
        assertEquals("New name", updated.captured.name)
        assertEquals(listOf("-1", "-5"), updated.captured.songIds)
    }

    @Test
    fun `import ids are stable per service and never mix albums with playlists`() {
        val ytPlaylist = YouTubeMusicPlaylist(playlistId = "PLabc", title = "Mix")
        assertEquals("ytmusic_PLabc", StreamingPlaylists.youTubeMusicId(ytPlaylist))
        assertEquals(StreamingPlaylists.youTubeMusicId(ytPlaylist), StreamingPlaylists.youTubeMusicId(ytPlaylist.copy(title = "Renamed")))

        val album = JioSaavnCollection(id = "123", title = "Album", kind = JioSaavnItemKind.ALBUM)
        val playlist = JioSaavnCollection(id = "123", title = "Playlist", kind = JioSaavnItemKind.PLAYLIST)
        assertEquals("jiosaavn_album_123", StreamingPlaylists.jioSaavnId(album))
        assertEquals("jiosaavn_playlist_123", StreamingPlaylists.jioSaavnId(playlist))
        assertNotEquals(StreamingPlaylists.jioSaavnId(album), StreamingPlaylists.jioSaavnId(playlist))
    }

    @Test
    fun `playlists and albums can be imported, artists cannot`() {
        assertTrue(YouTubeMusicPlaylist("PL1", "p").importable)
        assertTrue(YouTubeMusicPlaylist("MPREb_1", "a", kind = YouTubeMusicItemKind.ALBUM).importable)
        assertFalse(YouTubeMusicPlaylist("UC1", "artist", kind = YouTubeMusicItemKind.ARTIST).importable)
        assertTrue(JioSaavnCollection("1", "p", kind = JioSaavnItemKind.PLAYLIST).importable)
        assertTrue(JioSaavnCollection("1", "a", kind = JioSaavnItemKind.ALBUM).importable)
        assertFalse(JioSaavnCollection("1", "artist", kind = JioSaavnItemKind.ARTIST).importable)
    }
}
