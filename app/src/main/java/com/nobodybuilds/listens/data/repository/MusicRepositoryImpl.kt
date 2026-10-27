package com.nobodybuilds.listens.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.ArtistImageRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.nobodybuilds.listens.data.database.FavoritesDao
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SearchHistoryDao
import com.nobodybuilds.listens.data.database.TelegramChannelEntity
import com.nobodybuilds.listens.data.database.TelegramDao
import com.nobodybuilds.listens.data.database.toAlbum
import com.nobodybuilds.listens.data.database.toArtist
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.database.TelegramTopicEntity
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Genre
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.model.MusicFolder
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import com.nobodybuilds.listens.data.model.SearchResultItem
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.utils.DirectoryFilterUtils
import com.nobodybuilds.listens.utils.LogUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val telegramDao: TelegramDao,
    private val telegramCacheManagerProvider: Lazy<com.nobodybuilds.listens.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.nobodybuilds.listens.data.telegram.TelegramRepository>,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao,
    private val artistImageRepository: ArtistImageRepository,
    private val folderTreeBuilder: FolderTreeBuilder,
    /** Told about every like change, e.g. to mirror likes of YouTube songs to the account. */
    private val onFavoriteChanged: ((songId: Long, isFavorite: Boolean) -> Unit)? = null,
) : MusicRepository {

    private val directoryScanMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Tracks the active prefetch job so a new flow emission cancels the previous one.
    @Volatile private var prefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchSongId: Long? = null
    override val telegramRepository: com.nobodybuilds.listens.data.telegram.TelegramRepository
        get() = telegramRepositoryProvider.get()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    /** Cached directory filter — recomputed only when allowed/blocked dirs preferences change. */
    data class CachedDirFilter(val allowedParentDirs: List<String> = emptyList(), val applyFilter: Boolean = false)

    private val cachedDirFilter: StateFlow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked ->
        val (dirs, apply) = DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowed,
            blockedDirs = blocked,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
        CachedDirFilter(dirs, apply)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, CachedDirFilter())

    private val pageQueries = LibraryPageQueries(musicDao, cachedDirFilter)
    private val libraryFlows = FilteredLibraryFlows(musicDao, userPreferencesRepository, ::computeAllowedDirs)
    private val search = LibrarySearch(
        musicDao, userPreferencesRepository, playlistPreferencesRepository, searchHistoryDao, ::computeAllowedDirs
    )
    private val genresAndFolders = LibraryGenresAndFolders(
        context, musicDao, userPreferencesRepository, folderTreeBuilder, ::computeAllowedDirs
    )
    private val telegram = TelegramLibraryOperations(
        context, musicDao, telegramDao, telegramCacheManagerProvider, telegramRepositoryProvider, repositoryScope
    )

    private fun List<Artist>.missingImageCandidates(): List<Pair<Long, String>> =
        asSequence()
            .filter { it.effectiveImageUrl.isNullOrBlank() && it.name.isNotBlank() }
            .map { it.id to it.name }
            .distinctBy { (_, name) -> name.trim().lowercase() }
            .toList()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(sortOption: SortOption, storageFilter: com.nobodybuilds.listens.data.model.StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedSongs(sortOption, storageFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
    }

    override suspend fun getFavoriteSongsOnce(storageFilter: StorageFilter): List<Song> {
        return songRepository.getFavoriteSongsOnce(storageFilter)
    }

    override fun getFavoriteSongCountFlow(storageFilter: StorageFilter): Flow<Int> {
        return songRepository.getFavoriteSongCountFlow(storageFilter)
    }

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getSongCount().distinctUntilChanged()
    }

    /**
     * Compute allowed parent directories by subtracting blocked dirs from all known dirs.
     * Returns Pair(allowedDirs, applyFilter) for use with Room DAO filtered queries.
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private fun StorageFilter.toFilterMode(): Int =
        com.nobodybuilds.listens.data.model.LibrarySourceFilter.toFilterMode(this)

    override fun getAlbumById(id: Long): Flow<Album?> {
        return musicDao.getAlbumById(id).map { it?.toAlbum() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getArtists(storageFilter: StorageFilter): Flow<List<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getArtistsWithSongCountsFiltered(
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
                .distinctUntilChanged()
                .map { entities ->
                    val artists = entities.map { it.toArtist() }
                    // Trigger prefetch for missing images (non-blocking)
                    val missingImages = artists.missingImageCandidates()
                    if (missingImages.isNotEmpty()) {
                        // Cancel any in-flight prefetch before starting a new one — the flow
                        // can emit multiple times during sync, and concurrent launches would
                        // create N × artist-count coroutines simultaneously.
                        prefetchJob?.cancel()
                        prefetchJob = repositoryScope.launch {
                            artistImageRepository.prefetchArtistImages(missingImages)
                        }
                    }
                    artists
                }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return musicDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toSong() }.sortedBy { it.trackNumber }
        }.flowOn(Dispatchers.IO)
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        return musicDao.getArtistById(artistId).map { it?.toArtist() }
    }

    override suspend fun getArtistIdByName(name: String): Long? = withContext(Dispatchers.IO) {
        musicDao.getArtistIdByName(name)
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        return musicDao.getArtistsForSong(songId)
            .map { entities -> entities.map { it.toArtist() } }
            .distinctUntilChanged()
            .onEach { artists ->
                val missingImages = artists.missingImageCandidates()
                if (missingImages.isNotEmpty()) {
                    val isNewSong = currentSongArtistPrefetchSongId != songId
                    if (isNewSong) {
                        currentSongArtistPrefetchJob?.cancel()
                        currentSongArtistPrefetchSongId = songId
                    } else if (currentSongArtistPrefetchJob?.isActive == true) {
                        // Room re-emits as artist rows are updated; keep the current song batch
                        // alive so one successful image write does not cancel the remaining fetches.
                        return@onEach
                    }

                    currentSongArtistPrefetchJob = repositoryScope.launch {
                        artistImageRepository.prefetchArtistImages(missingImages)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForArtist(artistId).map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())
        return musicDao.getSongsByIds(longIds, emptyList(), false).map { entities ->
            val songMap = entities.associate { it.id.toString() to it.toSong() }
            // Preserve the requested order
            songIds.mapNotNull { songMap[it] }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    override suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext
        if (isFavorite) {
            favoritesDao.setFavorite(
                com.nobodybuilds.listens.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }
        onFavoriteChanged?.invoke(id, isFavorite)
    }

    override suspend fun getFavoriteSongIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        favoritesDao.getFavoriteSongIdsOnce()
            .map { it.toString() }
            .toSet()
    }

    override fun getFavoriteSongIdsFlow(): Flow<Set<String>> {
        return favoritesDao.getFavoriteSongIds()
            .map { ids -> ids.asSequence().map(Long::toString).toSet() }
            .distinctUntilChanged()
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        setFavoriteStatus(songId, newFav)
        return@withContext newFav
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            combine(
                telegramDao.getSongsByIds(listOf(songId)),
                telegramDao.getAllChannels()
            ) { songs, channels ->
                val channelMap = channels.associateBy { it.chatId }
                songs.firstOrNull()?.let {
                    it.toSong(channelTitle = channelMap[it.chatId]?.title)
                }
            }.flowOn(Dispatchers.IO)
        }
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? {
        return lyricsRepository.getStoredLyrics(song)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun getSongIdByContentUri(contentUri: String): Long? =
        withContext(Dispatchers.IO) {
            musicDao.getSongIdByContentUri(contentUri)
        }

    // --- Folder-filtered library flows (FilteredLibraryFlows) ---
    override fun getAudioFiles(): Flow<List<Song>> = libraryFlows.getAudioFiles()
    override fun getPaginatedAlbums(sortOption: SortOption, storageFilter: StorageFilter, minTracks: Int): Flow<PagingData<Album>> =
        libraryFlows.getPaginatedAlbums(sortOption, storageFilter, minTracks)
    override fun getPaginatedArtists(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Artist>> =
        libraryFlows.getPaginatedArtists(sortOption, storageFilter)
    override suspend fun getFirstPlayableSong(): Song? = libraryFlows.getFirstPlayableSong()
    override fun getAlbums(storageFilter: StorageFilter, minTracks: Int): Flow<List<Album>> =
        libraryFlows.getAlbums(storageFilter, minTracks)
    override suspend fun getAllSongsOnce(): List<Song> = libraryFlows.getAllSongsOnce()
    override fun getDistinctAlbumArtSongs(): Flow<List<Song>> = libraryFlows.getDistinctAlbumArtSongs()
    override fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>> = libraryFlows.getHomeMixPreviewSongs(limit)

    // --- One-shot pages and ids (LibraryPageQueries) ---
    override suspend fun getFavoriteSongsPage(limit: Int, offset: Int, sortOption: SortOption, storageFilter: StorageFilter): List<Song> =
        pageQueries.getFavoriteSongsPage(limit, offset, sortOption, storageFilter)
    override suspend fun getRandomSongs(limit: Int): List<Song> = pageQueries.getRandomSongs(limit)
    override suspend fun getRandomSongs(limit: Int, storageFilter: StorageFilter): List<Song> =
        pageQueries.getRandomSongs(limit, storageFilter)
    override suspend fun getShuffledSongIds(storageFilter: StorageFilter): List<Long> =
        pageQueries.getShuffledSongIds(storageFilter)
    override suspend fun getSongsPage(limit: Int, offset: Int, sortOption: SortOption, storageFilter: StorageFilter): List<Song> =
        pageQueries.getSongsPage(limit, offset, sortOption, storageFilter)
    override suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): List<Album> = pageQueries.getAlbumsPage(limit, offset, sortOption, storageFilter, minTracks)
    override suspend fun getArtistsPage(limit: Int, offset: Int, sortOption: SortOption, storageFilter: StorageFilter): List<Artist> =
        pageQueries.getArtistsPage(limit, offset, sortOption, storageFilter)
    override suspend fun getAllAlbumsOnce(storageFilter: StorageFilter, minTracks: Int): List<Album> =
        pageQueries.getAllAlbumsOnce(storageFilter, minTracks)
    override suspend fun getAllArtistsOnce(): List<Artist> = pageQueries.getAllArtistsOnce()
    override suspend fun getSongIdsSorted(sortOption: SortOption, storageFilter: StorageFilter): List<Long> =
        pageQueries.getSongIdsSorted(sortOption, storageFilter)
    override suspend fun getFavoriteSongIdsSorted(sortOption: SortOption, storageFilter: StorageFilter): List<Long> =
        pageQueries.getFavoriteSongIdsSorted(sortOption, storageFilter)

    // --- Search and search history (LibrarySearch) ---
    override fun searchSongs(query: String, titleOnly: Boolean): Flow<List<Song>> = search.searchSongs(query, titleOnly)
    override fun searchAlbums(query: String, minTracks: Int): Flow<List<Album>> = search.searchAlbums(query, minTracks)
    override fun searchArtists(query: String): Flow<List<Artist>> = search.searchArtists(query)
    override suspend fun searchPlaylists(query: String): List<Playlist> = search.searchPlaylists(query)
    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> =
        search.searchAll(query, filterType)
    override suspend fun addSearchHistoryItem(query: String) = search.addSearchHistoryItem(query)
    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> = search.getRecentSearchHistory(limit)
    override suspend fun deleteSearchHistoryItemByQuery(query: String) = search.deleteSearchHistoryItemByQuery(query)
    override suspend fun clearSearchHistory() = search.clearSearchHistory()

    // --- Genres and folders (LibraryGenresAndFolders) ---
    override fun getMusicByGenre(genreId: String): Flow<List<Song>> = genresAndFolders.getMusicByGenre(genreId)
    override fun getGenres(): Flow<List<Genre>> = genresAndFolders.getGenres()
    override fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> =
        genresAndFolders.getMusicFolders(storageFilter)

    // --- Telegram (TelegramLibraryOperations) ---
    override suspend fun saveTelegramSongs(songs: List<Song>) = telegram.saveTelegramSongs(songs)
    override suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>) =
        telegram.replaceTelegramSongsForChannel(chatId, songs)
    override suspend fun clearTelegramData() = telegram.clearTelegramData()
    override suspend fun saveTelegramChannel(channel: TelegramChannelEntity) = telegram.saveTelegramChannel(channel)
    override fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> = telegram.getAllTelegramChannels()
    override suspend fun deleteTelegramChannel(chatId: Long) = telegram.deleteTelegramChannel(chatId)
    override suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>) =
        telegram.saveTelegramTopics(chatId, topics)
    override suspend fun replaceTopicsForChannel(chatId: Long, freshTopics: List<TelegramTopicEntity>) =
        telegram.replaceTopicsForChannel(chatId, freshTopics)
    override suspend fun getTopicsForChannel(chatId: Long): List<TelegramTopicEntity> = telegram.getTopicsForChannel(chatId)
    override fun getAllTelegramTopics(): Flow<List<TelegramTopicEntity>> = telegram.getAllTelegramTopics()
    override suspend fun replaceTelegramSongsForTopic(chatId: Long, threadId: Long, topicName: String, songs: List<Song>) =
        telegram.replaceTelegramSongsForTopic(chatId, threadId, topicName, songs)
    override fun requestTelegramUnifiedSync() = telegram.requestTelegramUnifiedSync()
}
