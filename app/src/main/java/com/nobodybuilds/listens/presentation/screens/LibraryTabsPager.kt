@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistUiState
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.data.model.toLibraryTabIdOrNull
import android.net.Uri
import androidx.compose.foundation.pager.PagerState
import androidx.paging.compose.LazyPagingItems
import com.nobodybuilds.listens.data.model.Playlist

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun LibraryTabsPager(
    screenState: LibraryScreenState,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    pagerState: PagerState,
    tabTitles: List<String>,
    isCompactNavigation: Boolean,
    bottomBarHeightDp: Dp,
    playerUiState: LibraryScreenPlayerProjection,
    playlistUiState: PlaylistUiState,
    visiblePlaylists: List<Playlist>,
    allSongsLazyPagingItems: LazyPagingItems<Song>,
    albumsLazyPagingItems: LazyPagingItems<Album>,
    artistsLazyPagingItems: LazyPagingItems<Artist>,
    favoritePagingItems: LazyPagingItems<Song>,
    isLibraryLoading: Boolean,
    hasCurrentSong: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    stableOnMoreOptionsClick: (Song) -> Unit,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (Song) -> Unit,
    onSongSelectionToggle: (Song) -> Unit,
    isAlbumSelectionMode: Boolean,
    selectedAlbumIds: Set<Long>,
    onAlbumLongPress: (Album) -> Unit,
    onAlbumSelectionToggle: (Album) -> Unit,
    getAlbumSelectionIndex: (Long) -> Int?,
    isPlaylistSelectionMode: Boolean,
    selectedPlaylistIds: Set<String>,
    onPlaylistLongPress: (Playlist) -> Unit,
    onPlaylistSelectionToggle: (Playlist) -> Unit
) {
    var songsShowLocateButton by screenState.songsShowLocateButton
    var likedShowLocateButton by screenState.likedShowLocateButton
    var foldersShowLocateButton by screenState.foldersShowLocateButton
    var songsLocateAction by screenState.songsLocateAction
    var likedLocateAction by screenState.likedLocateAction
    var foldersLocateAction by screenState.foldersLocateAction
    var showPlaylistMultiSelectionSheet by screenState.showPlaylistMultiSelectionSheet
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        pageSpacing = 0.dp,
        beyondViewportPageCount = 1, // Pre-load adjacent tabs to reduce lag when switching
        key = { it }
    ) { page ->
        val tabIndex = resolveTabIndex(
            page = page,
            tabCount = tabTitles.size,
            compactMode = isCompactNavigation
        )
        when (tabTitles.getOrNull(tabIndex)?.toLibraryTabIdOrNull()) {
            LibraryTabId.SONGS -> {
                LibrarySongsTab(
                    songs = allSongsLazyPagingItems,
                    isLoading = isLibraryLoading,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeightDp,
                    onMoreOptionsClick = stableOnMoreOptionsClick,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                        allSongsLazyPagingItems.refresh()
                    },
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                    onLocateCurrentSongVisibilityChanged = { songsShowLocateButton = it },
                    onRegisterLocateCurrentSongAction = { songsLocateAction = it },
                    sortOption = playerUiState.currentSongSortOption,
                    storageFilter = playerUiState.currentStorageFilter,
                    hasCurrentSong = hasCurrentSong
                )
            }
            LibraryTabId.ALBUMS -> {
                val isLoading = playerUiState.isLoadingLibraryCategories

                val stableOnAlbumClick: (Long) -> Unit = remember(navController) {
                    { albumId: Long ->
                        navController.navigateSafelyReplacing(
                            route = Screen.AlbumDetail.createRoute(albumId),
                            patternToPop = Screen.AlbumDetail.route
                        )
                    }
                }
                LibraryAlbumsTab(
                    albums = albumsLazyPagingItems,
                    isLoading = isLoading,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeightDp,
                    isListView = playerUiState.isAlbumsListView,
                    currentAlbumSortOption = playerUiState.currentAlbumSortOption,
                    onAlbumClick = stableOnAlbumClick,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    isSelectionMode = isAlbumSelectionMode,
                    selectedAlbumIds = selectedAlbumIds,
                    onAlbumLongPress = onAlbumLongPress,
                    onAlbumSelectionToggle = onAlbumSelectionToggle,
                    getSelectionIndex = getAlbumSelectionIndex,
                    storageFilter = playerUiState.currentStorageFilter
                )
            }

            LibraryTabId.ARTISTS -> {
                val isLoading = playerUiState.isLoadingLibraryCategories

                LibraryArtistsTab(
                    artists = artistsLazyPagingItems,
                    isLoading = isLoading,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeightDp,
                    currentArtistSortOption = playerUiState.currentArtistSortOption,
                    onArtistClick = { artistId ->
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRoute(artistId),
                            patternToPop = Screen.ArtistDetail.route
                        )
                    },
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    storageFilter = playerUiState.currentStorageFilter
                )
            }

            LibraryTabId.PLAYLISTS -> {
                LibraryPlaylistsTab(
                    playlistUiState = playlistUiState,
                    filteredPlaylists = visiblePlaylists,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeightDp,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    // Playlist multi-selection
                    isSelectionMode = isPlaylistSelectionMode,
                    selectedPlaylistIds = selectedPlaylistIds,
                    onPlaylistLongPress = onPlaylistLongPress,
                    onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                    onPlaylistOptionsClick = { showPlaylistMultiSelectionSheet = true }
                )
            }

            LibraryTabId.LIKED -> {
                LibraryFavoritesTab(
                    favoriteSongs = favoritePagingItems,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeightDp,
                    onMoreOptionsClick = stableOnMoreOptionsClick,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                        favoritePagingItems.refresh()
                    },
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                    sortOption = playerUiState.currentFavoriteSortOption,
                    onLocateCurrentSongVisibilityChanged = { likedShowLocateButton = it },
                    onRegisterLocateCurrentSongAction = { likedLocateAction = it },
                    storageFilter = playerUiState.currentStorageFilter,
                    hasCurrentSong = hasCurrentSong
                )
            }

            LibraryTabId.FOLDERS -> {
                val folders = playerUiState.musicFolders
                val currentFolder = playerUiState.currentFolder
                val isLoading = playerUiState.isLoadingLibraryCategories
                val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
                val defaultFolderName = stringResource(R.string.presentation_batch_d_folder_name_fallback)

                LibraryFoldersTab(
                    folders = folders,
                    currentFolder = currentFolder,
                    isLoading = isLoading,
                    bottomBarHeight = bottomBarHeightDp,
                    stablePlayerState = stablePlayerState,
                    onNavigateBack = { playerViewModel.navigateBackFolder() },
                    onFolderClick = { folderPath -> playerViewModel.navigateToFolder(folderPath) },
                    onFolderAsPlaylistClick = { folder ->
                        val encodedPath = Uri.encode(folder.path)
                        navController.navigateSafelyReplacing(
                            route = Screen.PlaylistDetail.createRoute(
                                "${PlaylistViewModel.FOLDER_PLAYLIST_PREFIX}$encodedPath"
                            ),
                            patternToPop = Screen.PlaylistDetail.route
                        )
                    },
                    onPlaySong = { song, queue ->
                        playerViewModel.showAndPlaySong(
                            song,
                            queue,
                            currentFolder?.name ?: defaultFolderName
                        )
                    },
                    onMoreOptionsClick = stableOnMoreOptionsClick,
                    isPlaylistView = playerUiState.isFoldersPlaylistView,
                    currentSortOption = playerUiState.currentFolderSortOption,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = playerViewModel.multiSelectionStateHolder::getSelectionIndex,
                    onLocateCurrentSongVisibilityChanged = { foldersShowLocateButton = it },
                    onRegisterLocateCurrentSongAction = { foldersLocateAction = it }
                )
            }

            null -> Unit
        }
    }
}
