package com.twilighty.lmusic.presentation.nb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.lmusic.data.model.Artist
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.model.SortOption
import com.twilighty.lmusic.data.repository.MusicRepository
import com.twilighty.lmusic.data.stats.PlaybackStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A "Made for you" mix shown on Home. Rule-based mixes are computed locally today;
 * AI-generated mixes can be appended later with the same model.
 */
data class MadeForYouMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
)

private val Context.nbHomeDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_home")

/** Small Home-only preferences (kept out of UserPreferencesRepository). */
@Singleton
class NbHomePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val showClassicSectionsKey = booleanPreferencesKey("show_classic_home_sections")

    /** When on, Home also shows the old album collage and the Daily Mix section. Default off. */
    val showClassicSections: Flow<Boolean> =
        context.nbHomeDataStore.data.map { it[showClassicSectionsKey] ?: false }

    suspend fun setShowClassicSections(enabled: Boolean) {
        context.nbHomeDataStore.edit { it[showClassicSectionsKey] = enabled }
    }
}

/** Used by Settings › Appearance for the "Show classic Home sections" switch. */
@HiltViewModel
class NbHomeSettingsViewModel @Inject constructor(
    private val preferences: NbHomePreferences,
) : ViewModel() {
    val showClassicSections: StateFlow<Boolean> = preferences.showClassicSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShowClassicSections(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowClassicSections(enabled) }
    }
}

/** A top artist resolved to a library artist (so Home can open the artist page). */
data class NbHomeArtist(
    val id: Long,
    val name: String,
    val imageModel: String?,
    val playCount: Int,
)

@HiltViewModel
class NbHomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackStatsRepository: PlaybackStatsRepository,
    preferences: NbHomePreferences,
    private val smartRepository: com.twilighty.lmusic.data.smart.SmartRepository,
    private val libraryStateHolder: com.twilighty.lmusic.presentation.viewmodel.LibraryStateHolder,
) : ViewModel() {

    /** Library source filter used for the last refresh; a change forces a refresh. */
    private var lastSourceFilter: com.twilighty.lmusic.data.model.StorageFilter? = null

    init {
        viewModelScope.launch {
            libraryStateHolder.currentStorageFilter.collect { filter ->
                if (lastSourceFilter != null && lastSourceFilter != filter) refreshIfStale(force = true)
            }
        }
    }

    /** On-device smart mixes (empty while the experimental smart layer is off). */
    private val _smartMixes = MutableStateFlow<List<MadeForYouMix>>(emptyList())
    val smartMixes: StateFlow<List<MadeForYouMix>> = _smartMixes.asStateFlow()

    val showClassicSections: StateFlow<Boolean> = preferences.showClassicSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _recentlyAdded = MutableStateFlow<List<Song>>(emptyList())
    val recentlyAdded: StateFlow<List<Song>> = _recentlyAdded.asStateFlow()

    private val _favourites = MutableStateFlow<List<Song>>(emptyList())
    val favourites: StateFlow<List<Song>> = _favourites.asStateFlow()

    private val _forgottenFavourites = MutableStateFlow<List<Song>>(emptyList())
    val forgottenFavourites: StateFlow<List<Song>> = _forgottenFavourites.asStateFlow()

    private val _topArtists = MutableStateFlow<List<NbHomeArtist>>(emptyList())
    val topArtists: StateFlow<List<NbHomeArtist>> = _topArtists.asStateFlow()

    private var lastRefreshAt = 0L
    private var refreshJob: Job? = null
    private var lastArtistNames: List<String>? = null
    private var artistJob: Job? = null

    /** Called when Home becomes visible; throttled so returning to Home is free. */
    fun refreshIfStale(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        if (!force && lastRefreshAt != 0L && now - lastRefreshAt < REFRESH_INTERVAL_MS) return
        lastRefreshAt = now
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // "Recently added" follows the Library source filter (phone / cloud / all).
                val sourceFilter = libraryStateHolder.effectiveStorageFilterNow()
                lastSourceFilter = libraryStateHolder.currentStorageFilter.value
                _recentlyAdded.value = musicRepository.getSongsPage(
                    limit = MIX_SIZE,
                    offset = 0,
                    sortOption = SortOption.SongDateAdded,
                    storageFilter = sourceFilter
                )
            }.onFailure { Timber.w(it, "Home: recently added failed") }
            runCatching {
                _favourites.value = musicRepository.getFavoriteSongsPage(
                    limit = MIX_SIZE,
                    offset = 0,
                    sortOption = SortOption.LikedSongDateLiked
                )
            }.onFailure { Timber.w(it, "Home: favourites failed") }
            runCatching { _forgottenFavourites.value = computeForgottenFavourites() }
                .onFailure { Timber.w(it, "Home: forgotten favourites failed") }
            runCatching {
                val mixes = smartRepository.mixes()
                _smartMixes.value = mixes.mapNotNull { mix ->
                    val songs = smartRepository.songsFor(mix.songIds)
                    if (songs.isEmpty()) null
                    else MadeForYouMix(id = "smart_${mix.id}", title = mix.title, subtitle = mix.subtitle, songs = songs)
                }
            }.onFailure { Timber.w(it, "Home: smart mixes failed") }
        }
    }

    /** Songs played at least [MIN_PLAYS] times whose last play is 30+ days ago. */
    private suspend fun computeForgottenFavourites(): List<Song> {
        val history = playbackStatsRepository.loadPlaybackHistory(limit = 5_000)
        if (history.isEmpty()) return emptyList()
        val cutoff = System.currentTimeMillis() - FORGOTTEN_AFTER_MS
        data class Agg(var count: Int = 0, var last: Long = 0L)
        val bySong = HashMap<String, Agg>()
        history.forEach { entry ->
            val agg = bySong.getOrPut(entry.songId) { Agg() }
            agg.count++
            if (entry.timestamp > agg.last) agg.last = entry.timestamp
        }
        val ids = bySong.entries
            .filter { it.value.count >= MIN_PLAYS && it.value.last in 1 until cutoff }
            .sortedByDescending { it.value.count }
            .take(MIX_SIZE)
            .map { it.key }
        if (ids.isEmpty()) return emptyList()
        val songsById = musicRepository.getSongsByIds(ids).first().associateBy { it.id }
        return ids.mapNotNull { songsById[it] }
    }

    /** Resolves the stats' top artist names to library artists (id + image). */
    fun setTopArtistNames(names: List<String>) {
        val trimmed = names.filter { it.isNotBlank() }.distinct().take(MAX_ARTISTS)
        if (trimmed == lastArtistNames) return
        lastArtistNames = trimmed
        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                trimmed.mapNotNull { name ->
                    runCatching {
                        val id = musicRepository.getArtistIdByName(name) ?: return@runCatching null
                        val artist: Artist? = musicRepository.getArtistById(id).first()
                        NbHomeArtist(
                            id = id,
                            name = artist?.name ?: name,
                            imageModel = artist?.customImageUri?.takeIf { it.isNotBlank() }
                                ?: artist?.imageUrl?.takeIf { it.isNotBlank() },
                            playCount = 0
                        )
                    }.getOrNull()
                }
            }
            _topArtists.value = resolved
        }
    }

    companion object {
        private const val MIX_SIZE = 40
        private const val MIN_PLAYS = 3
        private const val MAX_ARTISTS = 10
        private const val FORGOTTEN_AFTER_MS = 30L * 24 * 60 * 60 * 1000
        private const val REFRESH_INTERVAL_MS = 10L * 60 * 1000
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupHomeDataStore: DataStore<Preferences> get() = nbHomeDataStore
