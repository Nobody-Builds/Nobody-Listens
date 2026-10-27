@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbType
import com.nobodybuilds.listens.presentation.nb.NbShapes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.MusicFolder
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.components.PlaylistArtCollage
import com.nobodybuilds.listens.presentation.viewmodel.StablePlayerState
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.utils.formatSongCount
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.flow.first
import com.nobodybuilds.listens.presentation.components.ExpressiveScrollBar
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem

private const val FOLDER_NAVIGATION_ROOT_KEY = "__folder_root__"
private const val FOLDER_NAVIGATION_FORWARD = 1
private const val FOLDER_NAVIGATION_BACKWARD = -1

internal fun resolveFolderNavigationDirection(initialPath: String?, targetPath: String?): Int =
    when {
        initialPath == targetPath -> FOLDER_NAVIGATION_FORWARD
        initialPath == null && targetPath != null -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath == null -> FOLDER_NAVIGATION_BACKWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(initialPath, targetPath) -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(targetPath, initialPath) -> FOLDER_NAVIGATION_BACKWARD
        else -> FOLDER_NAVIGATION_FORWARD
    }

private fun isDescendantFolderPath(ancestorPath: String, candidatePath: String): Boolean {
    val normalizedAncestor = ancestorPath.trimEnd('/')
    val normalizedCandidate = candidatePath.trimEnd('/')
    if (normalizedAncestor == normalizedCandidate) return false
    return normalizedCandidate.startsWith("$normalizedAncestor/")
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFoldersTab(
    folders: ImmutableList<MusicFolder>,
    currentFolder: MusicFolder?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderAsPlaylistClick: (MusicFolder) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    stablePlayerState: StablePlayerState,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isPlaylistView: Boolean = false,
    currentSortOption: SortOption = SortOption.FolderNameAZ,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {}
) {
    // List state moved inside AnimatedContent to prevent state sharing issues during transitions


    AnimatedContent(
        targetState = Pair(isPlaylistView, currentFolder?.path ?: FOLDER_NAVIGATION_ROOT_KEY),
        label = "FolderNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = resolveFolderNavigationDirection(
                initialPath = initialState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY },
                targetPath = targetState.second.takeUnless { it == FOLDER_NAVIGATION_ROOT_KEY }
            )
            val slideIn = slideInHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) -width else width
            } + fadeOut()

            slideIn.togetherWith(slideOut)
        }
    ) { (playlistMode, targetPath) ->
        // Each navigation destination gets its own independant ListState
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
        val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
        var lastHandledFolderSortKey by remember { mutableStateOf(currentSortOption.storageKey) }
        var pendingFolderSortScrollReset by remember { mutableStateOf(false) }

        val flattenedFolders = remember(folders, currentSortOption) {
            sortMusicFoldersByOption(flattenFolders(folders), currentSortOption)
        }

        val isRoot = targetPath == FOLDER_NAVIGATION_ROOT_KEY
        val activeFolder = if (isRoot) null else currentFolder
        val showPlaylistCards = playlistMode && activeFolder == null
        val itemsToShow = remember(activeFolder, folders, flattenedFolders, currentSortOption) {
            when {
                showPlaylistCards -> flattenedFolders
                activeFolder != null -> sortMusicFoldersByOption(activeFolder.subFolders, currentSortOption)
                else -> sortMusicFoldersByOption(folders, currentSortOption)
            }
        }.toImmutableList()

        val songsToShow = remember(activeFolder, currentSortOption) {
            sortSongsForFolderView(activeFolder?.songs ?: emptyList(), currentSortOption)
        }.toImmutableList()
        val currentSongId = stablePlayerState.currentSong?.id
        val currentSongIndexInSongs = remember(songsToShow, currentSongId) {
            currentSongId?.let { songId -> songsToShow.indexOfFirst { it.id == songId } } ?: -1
        }
        val currentSongListIndex = remember(itemsToShow.size, currentSongIndexInSongs) {
            if (currentSongIndexInSongs < 0) -1 else itemsToShow.size + currentSongIndexInSongs
        }
        val locateCurrentSongAction: (() -> Unit)? = remember(currentSongListIndex, listState) {
            if (currentSongListIndex < 0) {
                null
            } else {
                {
                    coroutineScope.launch {
                        listState.animateScrollToItem(currentSongListIndex)
                    }
                }
            }
        }

        LaunchedEffect(locateCurrentSongAction) {
            registerActionCallback(locateCurrentSongAction)
        }

        LaunchedEffect(currentSortOption) {
            val currentSortKey = currentSortOption.storageKey
            if (currentSortKey == lastHandledFolderSortKey) return@LaunchedEffect
            lastHandledFolderSortKey = currentSortKey
            pendingFolderSortScrollReset = true
            listState.scrollToItem(0)
        }

        LaunchedEffect(itemsToShow, songsToShow, pendingFolderSortScrollReset) {
            if (!pendingFolderSortScrollReset) return@LaunchedEffect
            listState.scrollToItem(0)
            pendingFolderSortScrollReset = false
        }

        LaunchedEffect(currentSongListIndex, itemsToShow, songsToShow, listState) {
            if (currentSongListIndex < 0 || songsToShow.isEmpty()) {
                visibilityCallback(false)
                return@LaunchedEffect
            }

            snapshotFlow {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    false
                } else {
                    currentSongListIndex in visibleItems.first().index..visibleItems.last().index
                }
            }
                .distinctUntilChanged()
                .collect { isVisible ->
                    visibilityCallback(!isVisible)
                }
        }

        DisposableEffect(Unit) {
            onDispose {
                visibilityCallback(false)
                registerActionCallback(null)
            }
        }

        val shouldShowLoading = isLoading && itemsToShow.isEmpty() && songsToShow.isEmpty() && isRoot

        Column(modifier = Modifier.fillMaxSize()) {
            when {
                shouldShowLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                itemsToShow.isEmpty() && songsToShow.isEmpty() -> {
                    LibraryExpressiveEmptyState(
                        tabId = LibraryTabId.FOLDERS,
                        storageFilter = StorageFilter.OFFLINE,
                        bottomBarHeight = bottomBarHeight
                    )
                }

                else -> {
                    val foldersPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = foldersPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = foldersPullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp)
                                    .fillMaxSize()
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 26.dp,
                                            topEnd = 26.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap,
                                    top = 0.dp                            )
                            ) {
                                if (showPlaylistCards) {
                                    items(itemsToShow, key = { it.path }, contentType = { "folder_card" }) { folder ->
                                        FolderPlaylistItem(
                                            folder = folder,
                                            onClick = { onFolderAsPlaylistClick(folder) }
                                        )
                                    }
                                } else {
                                    items(itemsToShow, key = { it.path }, contentType = { "folder_list" }) { folder ->
                                        FolderListItem(
                                            folder = folder,
                                            onClick = { onFolderClick(folder.path) }
                                        )
                                    }
                                }

                                items(songsToShow, key = { it.id }, contentType = { "song" }) { song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = stablePlayerState.currentSong?.id == song.id && stablePlayerState.isPlaying,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        onMoreOptionsClick = { onMoreOptionsClick(song) },
                                        isSelected = selectedSongIds.contains(song.id),
                                        selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                        isSelectionMode = isSelectionMode,
                                        onLongPress = { onSongLongPress(song) },
                                        onClick = {
                                            if (isSelectionMode) {
                                                onSongSelectionToggle(song)
                                            } else {
                                                onPlaySong(song, songsToShow)
                                            }
                                        }
                                    )
                                }
                            }

                            // ScrollBar Overlay
                            val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong())
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                listState = listState
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun FolderPlaylistItem(folder: MusicFolder, onClick: () -> Unit) {
    val previewSongs = remember(folder) { folder.collectAllSongs().take(9) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(NbShapes.lg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaylistArtCollage(
            songs = previewSongs,
            modifier = Modifier.size(48.dp).clip(NbShapes.md)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.name, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSongCount(folder.totalSongCount), style = NbType.meta, color = Nb.textMuted)
        }
    }
}

@Composable
fun FolderListItem(folder: MusicFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(NbShapes.lg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(NbShapes.md).background(Nb.muted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_folder_24),
                contentDescription = stringResource(R.string.presentation_batch_d_cd_folder),
                modifier = Modifier.size(24.dp),
                tint = Nb.text
            )
        }
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSongCount(folder.totalSongCount), style = NbType.meta, color = Nb.textMuted)
        }
    }
}

private fun flattenFolders(folders: List<MusicFolder>): List<MusicFolder> {
    return folders.flatMap { folder ->
        val current = if (folder.songs.isNotEmpty()) listOf(folder) else emptyList()
        current + flattenFolders(folder.subFolders)
    }
}

private fun sortMusicFoldersByOption(folders: List<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
    return when (sortOption) {
        SortOption.FolderNameAZ -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderNameZA -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSongCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSongCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountAsc -> folders.sortedWith(
            compareBy<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        SortOption.FolderSubdirCountDesc -> folders.sortedWith(
            compareByDescending<MusicFolder> { it.totalSubFolderCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path }
        )
        else -> folders.sortedWith(
            compareBy<MusicFolder> { it.name.lowercase() }
                .thenBy { it.path }
        )
    }
}

private fun sortSongsForFolderView(songs: List<Song>, sortOption: SortOption): List<Song> {
    return when (sortOption) {
        SortOption.FolderNameZA -> songs.sortedWith(
            compareByDescending<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        else -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
    }
}

private fun MusicFolder.collectAllSongs(): List<Song> {
    return songs + subFolders.flatMap { it.collectAllSongs() }
}
