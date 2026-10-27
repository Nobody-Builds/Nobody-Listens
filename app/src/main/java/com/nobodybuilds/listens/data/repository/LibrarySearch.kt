package com.nobodybuilds.listens.data.repository

import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SearchHistoryDao
import com.nobodybuilds.listens.data.database.SearchHistoryEntity
import com.nobodybuilds.listens.data.database.toAlbum
import com.nobodybuilds.listens.data.database.toArtist
import com.nobodybuilds.listens.data.database.toSearchHistoryItem
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import com.nobodybuilds.listens.data.model.SearchResultItem
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Library search and search history for [MusicRepositoryImpl]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LibrarySearch(
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val allowedDirsResolver: AllowedDirsResolver,
) {

    private companion object {
        /** Maximum number of search results to load at once to avoid memory issues with large libraries. */
        private const val SEARCH_RESULTS_LIMIT = 100
    }

    private suspend fun computeAllowedDirs(allowedDirs: Set<String>, blockedDirs: Set<String>) =
        allowedDirsResolver(allowedDirs, blockedDirs)

    // --- Métodos de Búsqueda ---

    fun searchSongs(query: String, titleOnly: Boolean = false): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.searchSongsLimited(
                        query = query,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        limit = SEARCH_RESULTS_LIMIT,
                        titleOnly = titleOnly
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    fun searchAlbums(query: String, minTracks: Int): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchAlbums(query, emptyList(), false, minTracks).map { entities ->
            entities.map { it.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchArtists(query, emptyList(), false).map { entities ->
            entities.map { it.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return playlistPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
    }

    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return combine(
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { (minTracks) ->
            minTracks
        }.flatMapLatest { minTracks ->
            when (filterType) {
                SearchFilterType.ALL -> {
                    combine(
                        searchSongs(query),
                        searchAlbums(query, minTracks),
                        searchArtists(query),
                        playlistsFlow
                    ) { songs, albums, artists, playlists ->
                        mutableListOf<SearchResultItem>().apply {
                            songs.forEach { add(SearchResultItem.SongItem(it)) }
                            albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                            artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                            playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                        }
                    }
                }
                SearchFilterType.SONGS -> searchSongs(query, titleOnly = true).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
                SearchFilterType.ALBUMS -> searchAlbums(query, minTracks).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
                SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
                SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }
}
