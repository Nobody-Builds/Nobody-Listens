package com.nobodybuilds.listens.presentation.viewmodel

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nobodybuilds.listens.data.model.Genre
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val HOME_MIX_PREVIEW_LIMIT = 48

/** Library lists PlayerViewModel exposes: paged songs, genres, home mix preview, counts. */
internal class PlayerLibraryFlows(
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    private val libraryStateHolder: LibraryStateHolder
) {
    /**
     * Paginated songs for efficient display in LibraryScreen.
     * Uses Paging 3 for memory-efficient loading of large libraries.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val paginatedSongs: Flow<PagingData<Song>> = libraryStateHolder.songsPagingFlow
        .cachedIn(scope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistPickerSongs: Flow<PagingData<Song>> = libraryStateHolder.currentSongSortOption
        .flatMapLatest { sortOption ->
            musicRepository.getPaginatedSongs(
                sortOption = sortOption,
                storageFilter = com.nobodybuilds.listens.data.model.StorageFilter.ALL
            )
        }
        .cachedIn(scope)

    // Genres StateFlow - delegated to LibraryStateHolder
    val genres: StateFlow<ImmutableList<Genre>> = libraryStateHolder.genres
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val paletteRegenerationTargets: StateFlow<List<Song>> = musicRepository.getDistinctAlbumArtSongs()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = musicRepository.getHomeMixPreviewSongs(
        limit = HOME_MIX_PREVIEW_LIMIT
    ).map { it.toImmutableList() }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val songCountFlow: StateFlow<Int> = musicRepository.getSongCountFlow()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
