package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbAvatar
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbSectionHeader
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbType
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import com.nobodybuilds.listens.data.model.SearchResultItem
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.presentation.components.SmartImageListTargetSize
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet
import com.nobodybuilds.listens.presentation.components.PlaylistCover
import com.nobodybuilds.listens.presentation.components.resolveNavBarOccupiedHeight
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.screens.search.components.GenreCategoriesGrid
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem
import androidx.compose.ui.res.stringResource

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults,
                    searchHistory = uiState.searchHistory
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Search debouncing is centralized in SearchStateHolder.
    val searchSourceFilter by remember(playerViewModel) {
        playerViewModel.playerUiState
            // Show the effective filter: "hide local media" means cloud only.
            .map { com.nobodybuilds.listens.data.model.LibrarySourceFilter.effective(it.currentStorageFilter, it.hideLocalMedia) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = com.nobodybuilds.listens.data.model.StorageFilter.ALL)

    LaunchedEffect(searchQuery, currentFilter, searchSourceFilter) {
        playerViewModel.performSearch(searchQuery)
    }
    val searchResults = searchUiState.searchResults
    // YouTube chip: show online YouTube results instead of the library while it is on.
    var youTubeSearch by rememberSaveable { mutableStateOf(false) }
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalNobodyListensDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Daylight header: "Search" h1 + settings, then the rounded muted search field.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = statusBarTopInset + 12.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search),
                        style = NbType.h1,
                        color = Nb.text,
                        modifier = Modifier.weight(1f)
                    )
                    NbIconButton(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd),
                        onClick = { navController.navigateSafely(Screen.Settings.route) },
                        style = NbIconButtonStyle.Tonal
                    )
                }
                NbSearchField(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        playerViewModel.updateSearchQuery(it)
                    },
                    onSearch = { query ->
                        if (query.isNotBlank()) {
                            playerViewModel.onSearchQuerySubmitted(query)
                        }
                        keyboardController?.hide()
                    },
                    onClear = {
                        searchQuery = ""
                        playerViewModel.updateSearchQuery("")
                    },
                    placeholder = stringResource(R.string.search_placeholder),
                    focusRequester = searchInputFocusRequester
                )
            }

            val showGenreBrowse by remember(searchQuery) { derivedStateOf { searchQuery.isBlank() } }
            AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    Column(Modifier.fillMaxSize()) {
                    val recentSearches = searchUiState.searchHistory
                    if (recentSearches.isNotEmpty()) {
                        Column(Modifier.padding(top = 10.dp)) {
                            NbSectionHeader(
                                title = stringResource(R.string.recent_searches),
                                actionLabel = stringResource(R.string.clear_all),
                                onAction = { playerViewModel.clearSearchHistory() },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(recentSearches, key = { "recent_${it.id ?: it.query}" }) { item ->
                                    NbRecentSearchChip(
                                        text = item.query,
                                        onClick = {
                                            searchQuery = item.query
                                            playerViewModel.updateSearchQuery(item.query)
                                        },
                                        onRemove = { playerViewModel.deleteSearchHistoryItem(item.query) }
                                    )
                                }
                            }
                        }
                    }
                    GenreCategoriesGrid(
                        genres = genres,
                        onGenreClick = { genre ->
                            Timber.tag("SearchScreen")
                                .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                            val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                            navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenreId))
                        },
                        playerViewModel = playerViewModel,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                        }
                        SearchSourceChips(
                            current = searchSourceFilter,
                            youTubeSelected = youTubeSearch,
                            onSelect = {
                                youTubeSearch = false
                                playerViewModel.setStorageFilter(it)
                            },
                            onYouTube = { youTubeSearch = !youTubeSearch },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (youTubeSearch) {
                            com.nobodybuilds.listens.presentation.ytmusic.YouTubeMusicSearchResults(
                                query = searchQuery,
                                playerViewModel = playerViewModel,
                                currentPlayingSongId = stablePlayerState.currentSong?.id,
                                isPlaying = stablePlayerState.isPlaying,
                                onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                onSuggestionPick = { suggestion ->
                                    searchQuery = suggestion
                                    playerViewModel.updateSearchQuery(suggestion)
                                },
                            )
                        } else Crossfade(
                            targetState = searchResults.isEmpty(),
                            animationSpec = tween(durationMillis = 190),
                            label = "search_results_fade"
                        ) { isEmpty ->
                            if (isEmpty) {
                                EmptySearchResults(
                                    searchQuery = searchQuery,
                                    colorScheme = colorScheme
                                )
                            } else {
                                SearchResultsList(
                                    results = searchResults,
                                    searchQuery = searchQuery,
                                    playerViewModel = playerViewModel,
                                    onItemSelected = {
                                        if (searchQuery.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                                        }
                                    },
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    NbSectionHeader(
        title = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.clear_all))
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    NbEmptyState(
        title = if (searchQuery.isNotBlank()) {
            stringResource(R.string.search_no_results_for_query, searchQuery)
        } else {
            stringResource(R.string.search_nothing_found)
        },
        body = stringResource(R.string.search_try_different_or_filters),
        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Search),
        modifier = Modifier.padding(top = 16.dp)
    )
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
            onItemSelected()
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.SONGS -> "Songs"
                            SearchFilterType.ALBUMS -> "Albums"
                            SearchFilterType.ARTISTS -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRoute(item.artist.id),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    NbSearchResultRow(
        title = album.title,
        subtitle = listOf("Album", album.artist).filter { it.isNotBlank() }.joinToString(" · "),
        onOpenClick = onOpenClick,
        onPlayClick = onPlayClick,
        playContentDescription = stringResource(R.string.cd_play_album),
        leading = {
            NbArt(
                model = album.albumArtUriString,
                title = album.title,
                size = 56.dp,
                shape = NbShapes.md,
                contentDescription = "Album art: ${album.title}"
            )
        }
    )
}

@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    NbSearchResultRow(
        title = artist.name,
        subtitle = "Artist · ${formatSongCount(artist.songCount)}",
        onOpenClick = onOpenClick,
        onPlayClick = onPlayClick,
        playContentDescription = "Play artist",
        accentPlay = true,
        leading = { NbAvatar(model = artist.effectiveImageUrl, name = artist.name, size = 56.dp) }
    )
}

@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    NbSearchResultRow(
        title = playlist.name,
        subtitle = "Playlist · ${formatSongCount(playlist.songIds.size)}",
        onOpenClick = onOpenClick,
        onPlayClick = onPlayClick,
        playContentDescription = "Play playlist",
        leading = {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 56.dp
            )
        }
    )
}

/** Flat Daylight result row: leading art, title, muted meta, round play button. */
@Composable
private fun NbSearchResultRow(
    title: String,
    subtitle: String,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit,
    playContentDescription: String,
    accentPlay: Boolean = false,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(NbShapes.lg)
            .clickable(onClick = onOpenClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NbIconButton(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = playContentDescription,
            onClick = onPlayClick,
            style = if (accentPlay) NbIconButtonStyle.Accent else NbIconButtonStyle.Tonal,
            size = 40.dp,
            iconSize = 22.dp
        )
    }
}

/** Rounded (16dp) muted search field with a search icon and a clear button. */
@Composable
private fun NbSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
) {
    val textColor = Nb.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(NbShapes.lg)
            .background(Nb.muted)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_search_icon),
            tint = Nb.textMuted,
            modifier = Modifier.size(20.dp)
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = NbType.body.copy(fontSize = 16.sp), color = Nb.textMuted, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = NbType.body.copy(fontSize = 16.sp, color = textColor),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Nb.ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        if (query.isNotEmpty()) {
            NbIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_clear_search_query),
                onClick = onClear,
                size = 36.dp,
                iconSize = 18.dp
            )
        }
    }
}

/** Recent search as a Daylight chip; the small x removes it from history. */
@Composable
private fun NbRecentSearchChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(NbShapes.pill)
            .background(Nb.canvas)
            .border(1.dp, Nb.borderStrong, NbShapes.pill)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = Nb.textMuted, modifier = Modifier.size(16.dp))
        Text(text, style = NbType.label, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = Nb.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    NbChip(
        text = filterType.name.lowercase().replaceFirstChar { it.titlecase() },
        selected = filterType == currentFilter,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        modifier = modifier
    )
}

/** Phone / Cloud / All — the same source filter as the Library, so results match what Library shows. */
@Composable
private fun SearchSourceChips(
    current: com.nobodybuilds.listens.data.model.StorageFilter,
    youTubeSelected: Boolean,
    onSelect: (com.nobodybuilds.listens.data.model.StorageFilter) -> Unit,
    onYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            com.nobodybuilds.listens.data.model.StorageFilter.OFFLINE to "Phone",
            com.nobodybuilds.listens.data.model.StorageFilter.ONLINE to "Cloud",
            com.nobodybuilds.listens.data.model.StorageFilter.ALL to "All"
        ).forEach { (filter, label) ->
            NbChip(
                text = label,
                selected = !youTubeSelected && current == filter,
                onClick = { if (youTubeSelected || current != filter) onSelect(filter) }
            )
        }
        // Online search, only while chosen: library results stay offline by default.
        NbChip(
            text = "YouTube",
            selected = youTubeSelected,
            onClick = onYouTube
        )
    }
}
