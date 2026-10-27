package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import coil.imageLoader
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.LyricsSearchResult
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.telegram.TelegramCacheManager
import com.nobodybuilds.listens.utils.ValidatedLyricsImport
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Keeps the current song's state in sync for PlayerViewModel: artwork and palette, lyrics,
 * repository metadata, and Telegram/YouTube artwork that arrives after playback starts.
 */
@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
internal class CurrentSongStateSync(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val telegramCacheManagerProvider: Lazy<TelegramCacheManager>,
    private val playbackStateHolder: PlaybackStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val themeStateHolder: ThemeStateHolder,
    private val lyricsSourcePreference: StateFlow<LyricsSourcePreference>
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _selectedSongForInfo get() = shared.selectedSongForInfo
    private val _playerUiState get() = shared.playerUiState
    private val _toastEvents get() = shared.toastEvents
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSongArtists: StateFlow<List<Artist>> = stablePlayerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            val idLong = songId?.toLongOrNull()
            if (idLong == null) flowOf(emptyList())
            else musicRepository.getArtistsForSong(idLong)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lyrics load callback for LyricsStateHolder
    val lyricsLoadCallback = object : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = false, lyrics = lyrics)
            }
        }
    }

    private var telegramPlaybackObserversStarted = false

    fun ensureTelegramPlaybackObserversStarted() {
        if (telegramPlaybackObserversStarted) return
        telegramPlaybackObserversStarted = true

        val telegramCacheManager = telegramCacheManagerProvider.get()
        val telegramRepository = musicRepository.telegramRepository

        scope.launch {
            launch {
                telegramCacheManager.embeddedArtUpdated.collect { updatedArtUri ->
                    refreshArtwork(updatedArtUri)
                }
            }

            launch {
                // YouTube songs: the HD cover is looked up after playback starts; swap it in.
                dualPlayerEngine.streamedArtworkUpdates.collect { (songId, artUri) ->
                    val current = playbackStateHolder.stablePlayerState.value.currentSong
                    if (current?.id == songId && current.albumArtUriString != artUri) {
                        playbackStateHolder.updateStablePlayerState { state ->
                            state.copy(currentSong = state.currentSong?.copy(albumArtUriString = artUri))
                        }
                        themeStateHolder.extractAndGenerateColorScheme(artUri.toUri(), artUri, isPreload = false)
                    }
                }
            }

            launch {
                telegramRepository.downloadCompleted.collect {
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    if (currentSong != null && currentSong.contentUriString.startsWith("telegram:")) {
                        val uri = Uri.parse(currentSong.contentUriString)
                        val chatId = uri.host?.toLongOrNull()
                        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()

                        if (chatId != null && messageId != null) {
                            refreshArtwork("telegram_art://$chatId/$messageId")
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshArtwork(updatedArtUri: String) {
        val currentState = playbackStateHolder.stablePlayerState.value
        val currentSong = currentState.currentSong
        // Check if it matches, ignoring query params for comparison
        val currentUriClean = currentSong?.albumArtUriString?.substringBefore('?')
        val updatedUriClean = updatedArtUri.substringBefore('?')
        
        if (currentUriClean == updatedUriClean) {
            Timber.d("PlayerViewModel: Embedded art updated for current song, forcing refresh")
            
            // 1. Invalidate Coil cache for the BASE uri (without params)
            // This ensures next time we load it without params, it's fresh too.
            val baseUri = currentUriClean ?: updatedUriClean
            
            // Remove from Memory Cache
            context.imageLoader.memoryCache?.keys?.forEach { key ->
                if (key.toString().contains(baseUri)) {
                    context.imageLoader.memoryCache?.remove(key)
                }
            }
            // Remove from Disk Cache
            context.imageLoader.diskCache?.remove(baseUri)

            // 2. Extract Colors (using base URI)
            themeStateHolder.extractAndGenerateColorScheme(updatedArtUri.toUri(), updatedArtUri, isPreload = false)
            
            // 3. FORCE UI REFRESH by updating the URI with a version timestamp
            // This forces SmartImage to see a "new" model and reload.
            // We keep the quality param if it exists, or add a version param.
            val newUri = if (updatedArtUri.contains("?")) {
                "$updatedArtUri&v=${System.currentTimeMillis()}"
            } else {
                "$updatedArtUri?v=${System.currentTimeMillis()}"
            }
            
            val updatedSong = currentSong.copy(albumArtUriString = newUri)
            
            // Update State
            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(currentSong = updatedSong)
            }
        }
    }

    /** Starts the current-song observers. Called once from PlayerViewModel's init. */
    fun start() {
        // On cold start, the MediaController connects asynchronously, leaving stablePlayerState.currentSong
        // null until that happens. Pre-load the palette from the persisted snapshot so the mini player
        // has the correct colors immediately on first render, before the controller is ready.
        scope.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val currentItem = if (snapshot.currentMediaId != null) {
                snapshot.items.find { it.mediaId == snapshot.currentMediaId }
            } else {
                snapshot.items.getOrNull(snapshot.currentIndex)
            } ?: return@launch

            val artworkUri = currentItem.artworkUri?.takeIf { it.isNotBlank() } ?: return@launch

            themeStateHolder.extractAndGenerateColorScheme(
                albumArtUriAsUri = artworkUri.toUri(),
                currentSongUriString = artworkUri,
                isPreload = false
            )
        }

        stablePlayerState
            .map { it.currentSong?.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
            .distinctUntilChanged()
            .onEach { artworkUri ->
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = artworkUri?.toUri(),
                    currentSongUriString = artworkUri,
                    isPreload = false
                )
            }
            .launchIn(scope)

        scope.launch {
            lyricsStateHolder.songUpdates.collect { update: Pair<com.nobodybuilds.listens.data.model.Song, com.nobodybuilds.listens.data.model.Lyrics?> ->
                val song = update.first
                val lyrics = update.second
                // Check if this update is relevant to the currently playing song OR the selected song
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    // MERGE FIX: if song comes back empty (e.g. from reset), preserve current metadata
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    val safeSong = if (song.title.isEmpty() && currentSong != null) {
                        currentSong.copy(lyrics = "")
                    } else {
                        song
                    }
                    updateSongInStates(safeSong, lyrics)
                }
                if (_selectedSongForInfo.value?.id == song.id) {
                    val currentSelected = _selectedSongForInfo.value
                    if (song.title.isEmpty() && currentSelected != null) {
                        _selectedSongForInfo.value = currentSelected.copy(lyrics = "")
                    } else {
                        _selectedSongForInfo.value = song
                    }
                }
            }
        }

        lyricsStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(scope)

        scope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId.isNullOrBlank()) flowOf(null)
                    else musicRepository.getSong(songId)
                }
                .collect { repositorySong ->
                    val currentState = playbackStateHolder.stablePlayerState.value
                    val currentSong = currentState.currentSong ?: return@collect
                    if (repositorySong == null || repositorySong.id != currentSong.id) {
                        return@collect
                    }

                    val hydratedSong = currentSong.withRepositoryHydration(repositorySong)
                    val persistedLyrics = parsePersistedLyrics(hydratedSong.lyrics)
                    val shouldApplyPersistedLyrics = currentState.lyrics == null && persistedLyrics != null
                    val shouldRefreshSong = hydratedSong != currentSong
                    val shouldReloadLyrics =
                        !shouldApplyPersistedLyrics &&
                            currentState.lyrics == null &&
                            hydratedSong.improvesLyricsLookupComparedTo(currentSong)

                    if (shouldApplyPersistedLyrics || shouldReloadLyrics) {
                        lyricsStateHolder.cancelLoading()
                    }

                    if (shouldRefreshSong || shouldApplyPersistedLyrics) {
                        updateSongInStates(
                            updatedSong = hydratedSong,
                            newLyrics = if (shouldApplyPersistedLyrics) persistedLyrics else null,
                            isLoadingLyrics = if (shouldApplyPersistedLyrics) false else null
                        )

                        if (_selectedSongForInfo.value?.id == hydratedSong.id) {
                            _selectedSongForInfo.value = hydratedSong
                        }
                    }

                    if (shouldReloadLyrics) {
                        lyricsStateHolder.loadLyricsForSong(hydratedSong, lyricsSourcePreference.value)
                    }
                }
        }
    }

    fun loadLyricsForCurrentSong() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        // Delegate to LyricsStateHolder
        lyricsStateHolder.loadLyricsForSong(currentSong, lyricsSourcePreference.value)
    }

    private fun updateSongInStates(
        updatedSong: Song,
        newLyrics: Lyrics? = null,
        isLoadingLyrics: Boolean? = null
    ) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val updatedQueue = currentQueue.replaceSong(updatedSong)

        if (updatedQueue !== currentQueue) {
            _playerUiState.update { it.copy(currentPlaybackQueue = updatedQueue) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update lyrics if they are explicitly passed
            val finalLyrics = newLyrics ?: state.lyrics
            state.copy(
                currentSong = updatedSong,
                lyrics = if (state.currentSong?.id == updatedSong.id) finalLyrics else state.lyrics,
                isLoadingLyrics = isLoadingLyrics ?: state.isLoadingLyrics
            )
        }
    }

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
        scope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                musicRepository.getSong(song.id).first()
            } ?: return@launch
            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = hydrated
            }
        }
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.fetchLyricsForSong(currentSong, forcePickResults, lyricsSourcePreference.value) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        lyricsStateHolder.searchLyricsManually(title, artist)
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.acceptLyricsSearchResult(result, currentSong)
    }

    fun resetLyricsForCurrentSong() {
        val songId = stablePlayerState.value.currentSong?.id?.toLongOrNull() ?: return
        lyricsStateHolder.resetLyrics(songId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    fun resetAllLyrics() {
        lyricsStateHolder.resetAllLyrics()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport) {
        val currentSong = stablePlayerState.value.currentSong
        lyricsStateHolder.importLyricsFromFile(songId, validatedImport, currentSong)
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }
}
