package com.nobodybuilds.listens.data.model

import com.nobodybuilds.listens.data.database.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibrarySourceFilterTest {

    private fun song(id: String, uri: String = "content://media/external/audio/media/$id") =
        Song.emptySong().copy(id = id, title = "Song $id", contentUriString = uri)

    private val local = song("1")
    private val telegram = song("-2", "telegram://123/456").copy(telegramFileId = 456, telegramChatId = 123L)
    private val netease = song("3", "netease://789")
    private val gdrive = song("4").copy(gdriveFileId = "abc")
    private val navidrome = song("5", "navidrome://x")
    private val jellyfin = song("6").copy(jellyfinId = "j")
    private val qq = song("7", "qqmusic://m")
    private val ytmusic = song("8", "ytmusic://track/dQw4w9WgXcQ")
    private val all = listOf(local, telegram, netease, gdrive, navidrome, jellyfin, qq, ytmusic)

    @Test
    fun `local songs are only those without cloud ids or cloud schemes`() {
        assertTrue(LibrarySourceFilter.isLocal(local))
        all.drop(1).forEach { assertFalse(LibrarySourceFilter.isLocal(it), it.id) }
    }

    @Test
    fun `source type of a song mirrors the source_type column`() {
        assertEquals(SourceType.LOCAL, LibrarySourceFilter.sourceTypeOf(local))
        assertEquals(SourceType.TELEGRAM, LibrarySourceFilter.sourceTypeOf(telegram))
        assertEquals(SourceType.NETEASE, LibrarySourceFilter.sourceTypeOf(netease))
        assertEquals(SourceType.GDRIVE, LibrarySourceFilter.sourceTypeOf(gdrive))
        assertEquals(SourceType.NAVIDROME, LibrarySourceFilter.sourceTypeOf(navidrome))
        assertEquals(SourceType.JELLYFIN, LibrarySourceFilter.sourceTypeOf(jellyfin))
        assertEquals(SourceType.QQMUSIC, LibrarySourceFilter.sourceTypeOf(qq))
        assertEquals(SourceType.YTMUSIC, LibrarySourceFilter.sourceTypeOf(ytmusic))
    }

    @Test
    fun `filter keeps phone only, cloud only or everything`() {
        assertEquals(listOf(local), LibrarySourceFilter.filterSongs(StorageFilter.OFFLINE, all))
        assertEquals(all.drop(1), LibrarySourceFilter.filterSongs(StorageFilter.ONLINE, all))
        assertEquals(all, LibrarySourceFilter.filterSongs(StorageFilter.ALL, all))
    }

    @Test
    fun `source type predicate matches the SQL filter modes`() {
        assertTrue(LibrarySourceFilter.matchesSourceType(StorageFilter.OFFLINE, SourceType.LOCAL))
        assertFalse(LibrarySourceFilter.matchesSourceType(StorageFilter.OFFLINE, SourceType.TELEGRAM))
        assertTrue(LibrarySourceFilter.matchesSourceType(StorageFilter.ONLINE, SourceType.JELLYFIN))
        assertFalse(LibrarySourceFilter.matchesSourceType(StorageFilter.ONLINE, SourceType.LOCAL))
        assertEquals(0, LibrarySourceFilter.toFilterMode(StorageFilter.ALL))
        assertEquals(1, LibrarySourceFilter.toFilterMode(StorageFilter.OFFLINE))
        assertEquals(2, LibrarySourceFilter.toFilterMode(StorageFilter.ONLINE))
    }

    @Test
    fun `hide local media forces cloud only`() {
        assertEquals(StorageFilter.ONLINE, LibrarySourceFilter.effective(StorageFilter.OFFLINE, hideLocalMedia = true))
        assertEquals(StorageFilter.ONLINE, LibrarySourceFilter.effective(StorageFilter.ALL, hideLocalMedia = true))
        assertEquals(StorageFilter.OFFLINE, LibrarySourceFilter.effective(StorageFilter.OFFLINE, hideLocalMedia = false))
    }

    @Test
    fun `chip cycle visits every filter`() {
        var f = StorageFilter.ALL
        val seen = mutableSetOf<StorageFilter>()
        repeat(StorageFilter.entries.size) { f = LibrarySourceFilter.next(f); seen += f }
        assertEquals(StorageFilter.entries.toSet(), seen)
        assertEquals(StorageFilter.ALL, f)
    }

    @Test
    fun `downloaded keeps only phone files saved from YouTube Music`() {
        val downloaded = local.copy(path = "/storage/emulated/0/Music/Nobody Listens/YouTube Music/Artist - Song.opus")
        val otherLocal = local.copy(path = "/storage/emulated/0/Music/Album/Song.mp3")
        assertTrue(LibrarySourceFilter.matchesSong(StorageFilter.DOWNLOADED, downloaded))
        assertFalse(LibrarySourceFilter.matchesSong(StorageFilter.DOWNLOADED, otherLocal))
        assertFalse(LibrarySourceFilter.matchesSong(StorageFilter.DOWNLOADED, ytmusic))
        assertEquals(3, LibrarySourceFilter.toFilterMode(StorageFilter.DOWNLOADED))
    }

    @Test
    fun `search results drop songs, albums and artists from the other source`() {
        val localAlbum = Album.empty().copy(id = 10L, title = "Local", songCount = 1)
        val cloudAlbum = localAlbum.copy(id = 11L, title = "Cloud")
        val results = listOf(
            SearchResultItem.SongItem(local),
            SearchResultItem.SongItem(telegram),
            SearchResultItem.AlbumItem(localAlbum),
            SearchResultItem.AlbumItem(cloudAlbum),
        )
        val phone = LibrarySourceFilter.filterSearchResults(StorageFilter.OFFLINE, results, setOf(10L), null)
        assertEquals(listOf(results[0], results[2]), phone)
        val unloaded = LibrarySourceFilter.filterSearchResults(StorageFilter.OFFLINE, results, null, null)
        assertEquals(listOf(results[0], results[2], results[3]), unloaded)
        assertEquals(results, LibrarySourceFilter.filterSearchResults(StorageFilter.ALL, results, emptySet(), emptySet()))
    }
}
