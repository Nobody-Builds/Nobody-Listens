@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.mutableStateOf
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.MusicFolder
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.presentation.viewmodel.PlayerUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.runtime.Stable

/**
 * Sheet, dialog, album-selection and locate-button state of [LibraryScreen], shared with the
 * composables it is split into. Each entry is the state the screen used to `remember` inline;
 * read and write them through local delegates (`var x by screenState.x`).
 */
@Stable
internal class LibraryScreenState {
    val showSongInfoBottomSheet = mutableStateOf(false)
    val showPlaylistBottomSheet = mutableStateOf(false)
    val playlistSheetSongs = mutableStateOf<List<Song>>(emptyList())
    val showWatchTransferDialog = mutableStateOf(false)
    val showCreatePlaylistDialog = mutableStateOf(false)
    val showPlaylistCreationTypeDialog = mutableStateOf(false)
    val showCreateAiPlaylistDialog = mutableStateOf(false)
    val aiGenerationRequestedFromDialog = mutableStateOf(false)
    val showReorderTabsSheet = mutableStateOf(false)
    val showTabSwitcherSheet = mutableStateOf(false)
    val showMultiSelectionSheet = mutableStateOf(false)
    val selectedAlbums = mutableStateOf<List<Album>>(emptyList())
    val showAlbumMultiSelectionSheet = mutableStateOf(false)
    val songsShowLocateButton = mutableStateOf(false)
    val likedShowLocateButton = mutableStateOf(false)
    val foldersShowLocateButton = mutableStateOf(false)
    val songsLocateAction = mutableStateOf<(() -> Unit)?>(null)
    val likedLocateAction = mutableStateOf<(() -> Unit)?>(null)
    val foldersLocateAction = mutableStateOf<(() -> Unit)?>(null)
    val showPlaylistMultiSelectionSheet = mutableStateOf(false)
    val showMergePlaylistDialog = mutableStateOf(false)
    val pendingMergePlaylistIds = mutableStateOf(emptyList<String>())
}

internal data class LibraryScreenPlayerProjection(
    val currentFolder: MusicFolder? = null,
    val folderSourceRootPath: String = "",
    val folderSource: FolderSource = FolderSource.INTERNAL,
    val isFoldersPlaylistView: Boolean = false,
    val currentStorageFilter: StorageFilter = StorageFilter.ALL,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongDateLiked,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val isAlbumsListView: Boolean = false,
    val isSdCardAvailable: Boolean = false,
    val musicFolders: ImmutableList<MusicFolder> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = true,
    val isGeneratingAiMetadata: Boolean = false,
    val isSyncingLibrary: Boolean = false,
    val isLoadingInitialSongs: Boolean = true,
    val hideLocalMedia: Boolean = false
)

internal fun PlayerUiState.toLibraryScreenProjection(): LibraryScreenPlayerProjection =
    LibraryScreenPlayerProjection(
        currentFolder = currentFolder,
        folderSourceRootPath = folderSourceRootPath,
        folderSource = folderSource,
        isFoldersPlaylistView = isFoldersPlaylistView,
        currentStorageFilter = currentStorageFilter,
        currentSongSortOption = currentSongSortOption,
        currentAlbumSortOption = currentAlbumSortOption,
        currentArtistSortOption = currentArtistSortOption,
        currentFavoriteSortOption = currentFavoriteSortOption,
        currentFolderSortOption = currentFolderSortOption,
        isAlbumsListView = isAlbumsListView,
        isSdCardAvailable = isSdCardAvailable,
        musicFolders = musicFolders,
        isLoadingLibraryCategories = isLoadingLibraryCategories,
        isGeneratingAiMetadata = isGeneratingAiMetadata,
        isSyncingLibrary = isSyncingLibrary,
        isLoadingInitialSongs = isLoadingInitialSongs,
        hideLocalMedia = hideLocalMedia
    )
