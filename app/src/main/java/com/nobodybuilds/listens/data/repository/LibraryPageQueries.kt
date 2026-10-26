package com.nobodybuilds.listens.data.repository

import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.toAlbum
import com.nobodybuilds.listens.data.database.toArtist
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One-shot page and id queries of [MusicRepositoryImpl], read with the cached folder filter. */
internal class LibraryPageQueries(
    private val musicDao: MusicDao,
    private val cachedDirFilter: StateFlow<MusicRepositoryImpl.CachedDirFilter>,
) {

    private fun StorageFilter.toFilterMode(): Int =
        com.nobodybuilds.listens.data.model.LibrarySourceFilter.toFilterMode(this)

    suspend fun getFavoriteSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getRandomSongs(limit, filter.allowedParentDirs, filter.applyFilter).map { it.toSong() }
    }

    suspend fun getRandomSongs(limit: Int, storageFilter: StorageFilter): List<Song> =
        withContext(Dispatchers.IO) {
            val filter = cachedDirFilter.value
            musicDao.getRandomSongsFiltered(
                limit = limit,
                allowedParentDirs = filter.allowedParentDirs,
                applyDirectoryFilter = filter.applyFilter,
                filterMode = storageFilter.toFilterMode()
            ).map { it.toSong() }
        }

    suspend fun getShuffledSongIds(storageFilter: StorageFilter): List<Long> =
        withContext(Dispatchers.IO) {
            val filter = cachedDirFilter.value
            musicDao.getShuffledSongIdsFiltered(
                allowedParentDirs = filter.allowedParentDirs,
                applyDirectoryFilter = filter.applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
        }

    suspend fun getSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = limit,
            offset = offset
        ).map { it.toAlbum() }
    }

    suspend fun getArtistsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toArtist() }
    }

    suspend fun getAllAlbumsOnce(storageFilter: StorageFilter, minTracks: Int): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = SortOption.AlbumTitleAZ.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = Int.MAX_VALUE,
            offset = 0
        ).map { it.toAlbum() }
    }

    suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsWithSongCountsFiltered(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            filterMode = StorageFilter.ALL.toFilterMode()
        ).first().map { it.toArtist() }
    }

    suspend fun getSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.nobodybuilds.listens.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    suspend fun getFavoriteSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.nobodybuilds.listens.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }
}
