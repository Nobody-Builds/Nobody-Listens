package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.lazy.LazyRow
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbSectionHeader
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import com.nobodybuilds.listens.data.model.SearchResultItem
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet
import com.nobodybuilds.listens.presentation.components.resolveNavBarOccupiedHeight
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.screens.search.components.GenreCategoriesGrid
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
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
    // YouTube and JioSaavn chips: show that service's results instead of the library while one is on.
    var onlineSearch by rememberSaveable { mutableStateOf<OnlineSearch?>(null) }
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
                            online = onlineSearch,
                            onSelect = {
                                onlineSearch = null
                                playerViewModel.setStorageFilter(it)
                            },
                            onOnline = { service -> onlineSearch = service.takeIf { it != onlineSearch } },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val onSuggestionPick: (String) -> Unit = { suggestion ->
                            searchQuery = suggestion
                            playerViewModel.updateSearchQuery(suggestion)
                        }
                        if (onlineSearch == OnlineSearch.YOUTUBE) {
                            com.nobodybuilds.listens.presentation.ytmusic.YouTubeMusicSearchResults(
                                query = searchQuery,
                                playerViewModel = playerViewModel,
                                currentPlayingSongId = stablePlayerState.currentSong?.id,
                                isPlaying = stablePlayerState.isPlaying,
                                onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                onSuggestionPick = onSuggestionPick,
                            )
                        } else if (onlineSearch == OnlineSearch.JIOSAAVN) {
                            com.nobodybuilds.listens.presentation.jiosaavn.JioSaavnSearchResults(
                                query = searchQuery,
                                playerViewModel = playerViewModel,
                                currentPlayingSongId = stablePlayerState.currentSong?.id,
                                isPlaying = stablePlayerState.isPlaying,
                                onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                onSuggestionPick = onSuggestionPick,
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
