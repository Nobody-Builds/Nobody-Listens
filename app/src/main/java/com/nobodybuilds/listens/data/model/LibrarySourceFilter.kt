package com.nobodybuilds.listens.data.model

import com.nobodybuilds.listens.data.database.SourceType

/**
 * Single definition of the Library "source" filter (phone only / cloud only / both).
 *
 * The persisted choice lives in UserPreferencesRepository (lastStorageFilter) and the live value in
 * LibraryStateHolder. Every list, search and shuffle path resolves the *effective* filter through
 * [effective] and tests membership through [matchesSourceType] / [matchesSong], which mirror the SQL
 * predicate used by MusicDao (`source_type = 0` is local, anything else is cloud).
 */
object LibrarySourceFilter {

    /** "Hide local media" forces cloud-only, the same as the Library lists. */
    fun effective(selected: StorageFilter, hideLocalMedia: Boolean): StorageFilter =
        if (hideLocalMedia) StorageFilter.ONLINE else selected

    /** Integer mode passed to MusicDao queries (`:filterMode`). */
    fun toFilterMode(filter: StorageFilter): Int = when (filter) {
        StorageFilter.ALL -> 0
        StorageFilter.OFFLINE -> 1
        StorageFilter.ONLINE -> 2
        StorageFilter.DOWNLOADED -> 3
    }

    /** Folders the YouTube Music and JioSaavn downloaders save into; match MusicDao's `filterMode = 3`. */
    val DOWNLOADS_FOLDER_SUFFIXES = listOf("/Nobody Listens/YouTube Music", "/Nobody Listens/JioSaavn")

    fun isDownloaded(song: Song): Boolean {
        val folder = song.path.substringBeforeLast('/')
        return isLocal(song) && DOWNLOADS_FOLDER_SUFFIXES.any(folder::endsWith)
    }

    fun isLocalSourceType(sourceType: Int): Boolean = sourceType == SourceType.LOCAL

    fun matchesSourceType(filter: StorageFilter, sourceType: Int): Boolean = when (filter) {
        StorageFilter.ALL -> true
        StorageFilter.OFFLINE -> isLocalSourceType(sourceType)
        StorageFilter.ONLINE -> !isLocalSourceType(sourceType)
        // Downloads are phone files; the folder check needs the song, see [matchesSong].
        StorageFilter.DOWNLOADED -> isLocalSourceType(sourceType)
    }

    /** Source type of an in-memory [Song], using cloud ids first and the content URI scheme as fallback. */
    fun sourceTypeOf(song: Song): Int = when {
        song.telegramFileId != null || song.telegramChatId != null -> SourceType.TELEGRAM
        song.neteaseId != null -> SourceType.NETEASE
        song.gdriveFileId != null -> SourceType.GDRIVE
        song.qqMusicMid != null -> SourceType.QQMUSIC
        song.navidromeId != null -> SourceType.NAVIDROME
        song.jellyfinId != null -> SourceType.JELLYFIN
        else -> SourceType.fromContentUri(song.contentUriString)
    }

    fun isLocal(song: Song): Boolean = isLocalSourceType(sourceTypeOf(song))

    fun matchesSong(filter: StorageFilter, song: Song): Boolean = when (filter) {
        StorageFilter.ALL -> true
        StorageFilter.DOWNLOADED -> isDownloaded(song)
        else -> matchesSourceType(filter, sourceTypeOf(song))
    }

    fun <T> filterSongs(filter: StorageFilter, songs: List<T>, songOf: (T) -> Song): List<T> =
        if (filter == StorageFilter.ALL) songs else songs.filter { matchesSong(filter, songOf(it)) }

    fun filterSongs(filter: StorageFilter, songs: List<Song>): List<Song> =
        filterSongs(filter, songs) { it }

    /**
     * Applies the source filter to search results. Songs are checked directly; albums/artists are
     * kept only when they appear in the Library's (already source-filtered) album/artist lists.
     * A null id set means "not loaded yet" and leaves that kind unfiltered. Playlists always stay.
     */
    fun filterSearchResults(
        filter: StorageFilter,
        results: List<SearchResultItem>,
        allowedAlbumIds: Set<Long>?,
        allowedArtistIds: Set<Long>?
    ): List<SearchResultItem> {
        if (filter == StorageFilter.ALL) return results
        return results.filter { item ->
            when (item) {
                is SearchResultItem.SongItem -> matchesSong(filter, item.song)
                is SearchResultItem.AlbumItem -> allowedAlbumIds == null || item.album.id in allowedAlbumIds
                is SearchResultItem.ArtistItem -> allowedArtistIds == null || item.artist.id in allowedArtistIds
                is SearchResultItem.PlaylistItem -> true
            }
        }
    }

    /** Next value when the Library/Search chip cycles. Matches PlayerViewModel.toggleStorageFilter. */
    fun next(filter: StorageFilter): StorageFilter = when (filter) {
        StorageFilter.ALL -> StorageFilter.ONLINE
        StorageFilter.ONLINE -> StorageFilter.OFFLINE
        StorageFilter.OFFLINE -> StorageFilter.DOWNLOADED
        StorageFilter.DOWNLOADED -> StorageFilter.ALL
    }
}

/**
 * Which songs a shuffle should draw from. Pure so it can be unit tested:
 * the whole (filtered) library, never a fixed-size random sample.
 */
object ShuffleSourceSelector {

    /** Ids already filtered by SQL; drop duplicates while keeping order, no cap. */
    fun selectIds(filteredIds: List<Long>): List<Long> =
        if (filteredIds.size < 2) filteredIds else LinkedHashSet(filteredIds).toList()

    /** For lists that come from a query that ignores the source filter (album/artist songs). */
    fun selectSongs(filter: StorageFilter, songs: List<Song>): List<Song> =
        LibrarySourceFilter.filterSongs(filter, songs)
}
