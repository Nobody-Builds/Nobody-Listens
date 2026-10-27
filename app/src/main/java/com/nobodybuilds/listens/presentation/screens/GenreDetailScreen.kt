package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.presentation.components.ExpressiveScrollBar
import com.nobodybuilds.listens.presentation.components.GenreSortBottomSheet
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.components.extractFastScrollGlyph
import com.nobodybuilds.listens.presentation.screens.QuickFillDialog
import com.nobodybuilds.listens.presentation.viewmodel.GenreDetailListItem
import com.nobodybuilds.listens.presentation.viewmodel.GenreDetailViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SortOption
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

// --- Data Models & Helpers ---

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    decodedGenreId: String = java.net.URLDecoder.decode(genreId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel(),
    playlistViewModel: com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val libraryGenres by playerViewModel.genres.collectAsStateWithLifecycle()
    
    // Defer heavy list rendering until navigation transition settles
    var isTransitionFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        isTransitionFinished = true
    }

    val density = LocalDensity.current
    val darkMode = LocalNobodyListensDarkTheme.current

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val minTopBarHeight = 58.dp + statusBarHeight // Reduced by 6dp from 64.dp
    val maxTopBarHeight = 200.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }
    val showScrollBar by remember {
        derivedStateOf {
            collapseFraction > 0.95f &&
                (lazyListState.canScrollForward || lazyListState.canScrollBackward)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                // If scrolling up (content going down) and list is not at top, don't expand yet
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                // Make sure we consume scroll only if we actually resized the bar
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val currentHeight = topBarHeight.value
                if (currentHeight > minTopBarHeightPx && currentHeight < maxTopBarHeightPx) {
                    // Decide target based on proximity and velocity
                    val targetHeight = if (available.y > 500f) {
                        maxTopBarHeightPx // Flinging down -> Expand
                    } else if (available.y < -500f) {
                        minTopBarHeightPx // Flinging up -> Collapse
                    } else {
                        // Snap to nearest
                        if (currentHeight > (minTopBarHeightPx + maxTopBarHeightPx) / 2) maxTopBarHeightPx else minTopBarHeightPx
                    }
                    
                    coroutineScope.launch {
                        topBarHeight.animateTo(
                            targetValue = targetHeight,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow) 
                        )
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    // Colors
    val defaultContainer = MaterialTheme.colorScheme.surfaceVariant
    val defaultOnContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val themeGenre = uiState.genre
    val themeColor = remember(themeGenre, decodedGenreId, darkMode, defaultContainer, defaultOnContainer) {
        if (themeGenre != null) {
            com.nobodybuilds.listens.ui.theme.GenreThemeUtils.getGenreThemeColor(
                genre = themeGenre,
                isDark = darkMode,
                fallbackGenreId = decodedGenreId
            )
        } else {
            com.nobodybuilds.listens.ui.theme.GenreThemeColor(
                defaultContainer,
                defaultOnContainer
            )
        }
    }
    
    val startColor = themeColor.container
    val contentColor = themeColor.onContainer
    
    // Optimization: Calculate a fast display name for the title while the full Genre object is loading.
    // This prevents the "Genre" placeholder from flashing during the navigation transition.
    val initialDisplayName = remember(decodedGenreId) {
        decodedGenreId
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
    val genreDisplayName = themeGenre?.name ?: uiState.genre?.name ?: initialDisplayName
    val genreShuffleLabel = stringResource(R.string.presentation_batch_b_genre_shuffle_label, genreDisplayName)
    val genreFastScrollLabelProvider = remember(uiState.flattenedItems, uiState.sortOption) {
        { index: Int ->
            genreFastScrollLabel(
                items = uiState.flattenedItems,
                index = index,
                sortOption = uiState.sortOption
            )
        }
    }
    
    val toastAddedToQueue = stringResource(R.string.toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.toast_playing_next)

    // FAB Logic
    var showSortSheet by remember { mutableStateOf(false) }
    var showSongOptionsSheet by remember { mutableStateOf<Song?>(null) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var showQuickFillDialog by remember { mutableStateOf(false) }

    val isUnknownGenre = remember(decodedGenreId) {
        decodedGenreId.equals("unknown", ignoreCase = true) || decodedGenreId.equals("unknown genre", ignoreCase = true)
    }
    
    val customGenres by playerViewModel.customGenres.collectAsStateWithLifecycle()
    val customGenreIcons by playerViewModel.customGenreIcons.collectAsStateWithLifecycle()
    val genrePaletteStyle by playerViewModel.albumArtPaletteStyle.collectAsStateWithLifecycle(
        initialValue = AlbumArtPaletteStyle.default
    )
    val isMiniPlayerVisible = stablePlayerState.currentSong != null
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + systemNavBarInset + 16.dp else systemNavBarInset + 16.dp,
        label = "fabPadding"
    )

    // Capture Neutral Colors from the App Theme (before overriding)
    val baseColorScheme = MaterialTheme.colorScheme

    // Dynamic Theme
    val genreColorScheme = remember(themeGenre, decodedGenreId, darkMode, genrePaletteStyle) {
        com.nobodybuilds.listens.ui.theme.GenreThemeUtils.getGenreDetailColorScheme(
            genre = themeGenre,
            fallbackGenreId = decodedGenreId,
            isDark = darkMode,
            paletteStyle = genrePaletteStyle
        )
    }

    MaterialTheme(colorScheme = genreColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background) // Uses new theme background
        ) {
            // Optimization: Cache Dp conversions
            val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

            // Optimization: Use fixed padding and offset instead of dynamic contentPadding 
            // to avoid triggered remeasures of the entire list on every pixel of scroll.
            
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = minTopBarHeight + 8.dp, // FIXED padding
                    start = 8.dp,
                    end = if (showScrollBar) 24.dp else 8.dp,
                    bottom = fabBottomPadding + 148.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        // Offset the entire list down by the current "expansion" of the top bar
                        val extraHeight = (topBarHeight.value - minTopBarHeightPx).roundToInt()
                        IntOffset(0, extraHeight)
                    }
            ) {
                // Optimization: Limit rendered items during the navigation transition 
                // to ensure the slide-in animation remains smooth.
                val displayItems = if (isTransitionFinished || uiState.flattenedItems.size < 20) {
                    uiState.flattenedItems
                } else {
                    uiState.flattenedItems.take(20)
                }

                items(
                    items = displayItems,
                    key = { it.key },
                    contentType = { it::class } // CRITICAL: Content type for optimized recycling
                ) { item ->
                    when (item) {
                        is GenreDetailListItem.ArtistHeader -> {
                            GenreArtistHeader(item.artistName, item.artistImageUrl)
                        }
                        is GenreDetailListItem.AlbumHeader -> {
                            GenreAlbumHeader(
                                album = item.album,
                                useArtistStyle = item.useArtistStyle,
                                onSongClick = { song ->
                                    playerViewModel.showAndPlaySong(song, uiState.sortedSongs, genreDisplayName)
                                }
                            )
                        }
                        is GenreDetailListItem.SongItem -> {
                            GenreSongItemWrapper(
                                item = item,
                                stablePlayerState = stablePlayerState,
                                onSongClick = { song ->
                                    playerViewModel.showAndPlaySong(song, uiState.sortedSongs, genreDisplayName)
                                },
                                onMoreOptionsClick = { song -> showSongOptionsSheet = song }
                            )
                        }
                        is GenreDetailListItem.Spacer -> {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(item.heightDp.dp)
                                    .run {
                                        if (item.useSurfaceBackground) background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                                        else this
                                    }
                            )
                        }
                        is GenreDetailListItem.Divider -> {
                             Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                            }
                        }
                    }
                }
            }

            // Only show scrollbar when the top bar is mostly collapsed to avoid visual conflict
            if (showScrollBar) {
                ExpressiveScrollBar(
                    listState = lazyListState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = minTopBarHeight + 12.dp, // Stable padding for performance
                            bottom = fabBottomPadding + 112.dp // Stable padding
                        ),
                    dragLabelProvider = genreFastScrollLabelProvider
                )
            }

            // Collapsible Top Bar with Gradient (On Top of List, High Z-Index)
            // This ensures the gradient is ON TOP of the scrolling content, so content scrolls BEHIND it.
            GenreCollapsibleTopBar(
                title = genreDisplayName,
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackPressed = { navController.popBackStack() },
                startColor = startColor,
                contentColor = contentColor,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                collapsedContentColor = MaterialTheme.colorScheme.onSurface
            )
        
            // FAB
            Box(
                 modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabBottomPadding + 26.dp, end = 16.dp)
                    .zIndex(10f) // Ensure FAB is above everything
            ) {
                 MediumFloatingActionButton(
                    onClick = { showSortSheet = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = AbsoluteSmoothCornerShape(24.dp, 60)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_options),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        
            // Sorting/Options Bottom Sheet
            if (showSortSheet) {
                GenreSortBottomSheet(
                    onDismiss = { showSortSheet = false },
                    currentSort = uiState.sortOption,
                    onSortSelected = {
                        viewModel.updateSortOption(it)
                        showSortSheet = false
                    },
                    onShuffle = {
                        if (uiState.songs.isNotEmpty()) {
                            playerViewModel.showAndPlaySong(uiState.sortedSongs.random(), uiState.sortedSongs, genreShuffleLabel)
                            showSortSheet = false
                        }
                    },
                    headerContent = if (isUnknownGenre) {
                        {
                            Button(
                                onClick = {
                                    showSortSheet = false
                                    showQuickFillDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(16.dp, 60)
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.genre_quick_fill),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else null
                )
            }

            // Quick Fill Dialog
            // QuickFillDialog with Base Theme (Independent of Genre Theme)
            MaterialTheme(colorScheme = baseColorScheme) {
                QuickFillDialog(
                    visible = showQuickFillDialog,
                    songs = uiState.songs,
                    customGenres = customGenres,
                    customGenreIcons = customGenreIcons,
                    onDismiss = { showQuickFillDialog = false },
                    onApply = { songs, genre ->
                        playerViewModel.batchEditGenre(songs, genre)
                        showQuickFillDialog = false
                    },
                    onAddCustomGenre = { genre, iconRes ->
                        playerViewModel.addCustomGenre(genre, iconRes)
                    }
                )
            }
        
            // Song Options Bottom Sheet
            showSongOptionsSheet?.let { song ->
                val isFavorite = favoriteSongIds.contains(song.id)

                MaterialTheme(
                    colorScheme = genreColorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    SongInfoBottomSheet(
                        song = song,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            playerViewModel.toggleFavoriteSpecificSong(song)
                        },
                        onDismiss = { showSongOptionsSheet = null },
                        onPlaySong = {
                            playerViewModel.showAndPlaySong(song, uiState.sortedSongs, genreDisplayName)
                            showSongOptionsSheet = null
                        },
                        onAddToQueue = {
                            playerViewModel.addSongToQueue(song)
                            showSongOptionsSheet = null
                            playerViewModel.sendToast(toastAddedToQueue)
                        },
                        onAddNextToQueue = {
                            playerViewModel.addSongNextToQueue(song)
                            showSongOptionsSheet = null
                            playerViewModel.sendToast(toastPlayingNext)
                        },
                        onAddToPlayList = {
                            showPlaylistBottomSheet = true
                        },
                        onDeleteFromDevice = playerViewModel::deleteFromDevice,
                        onNavigateToAlbum = {
                            navController.navigateSafelyReplacing(
                                route = com.nobodybuilds.listens.presentation.navigation.Screen.AlbumDetail.createRoute(song.albumId),
                                patternToPop = com.nobodybuilds.listens.presentation.navigation.Screen.AlbumDetail.route
                            )
                            showSongOptionsSheet = null
                        },
                        onNavigateToArtist = {
                            navController.navigateSafelyReplacing(
                                route = com.nobodybuilds.listens.presentation.navigation.Screen.ArtistDetail.createRoute(song.artistId),
                                patternToPop = com.nobodybuilds.listens.presentation.navigation.Screen.ArtistDetail.route
                            )
                            showSongOptionsSheet = null
                        },
                        onNavigateToGenre = {
                            song.genre?.let {
                                navController.navigateSafelyReplacing(
                                    route = com.nobodybuilds.listens.presentation.navigation.Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                                    patternToPop = com.nobodybuilds.listens.presentation.navigation.Screen.GenreDetail.route
                                )
                            }
                            showSongOptionsSheet = null
                        },
                        onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                            playerViewModel.editSongMetadata(
                                song,
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
                            playerViewModel.generateAiMetadata(song, fields)
                        },
                        removeFromListTrigger = {}
                    )
                }

                if (showPlaylistBottomSheet) {
                    com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = listOf(song),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = 0.dp, // Or calculate if needed
                        playerViewModel = playerViewModel
                    )
                }
            }
        
            // Loading/Error States
            if (uiState.isLoadingSongs) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun genreFastScrollLabel(
    items: List<GenreDetailListItem>,
    index: Int,
    sortOption: SortOption
): String? {
    if (items.isEmpty()) return null

    val clampedIndex = index.coerceIn(0, items.lastIndex)
    for (candidateIndex in clampedIndex downTo 0) {
        val label = items[candidateIndex].fastScrollLabel(sortOption)
        if (!label.isNullOrBlank()) {
            return label
        }
    }

    return null
}

private fun GenreDetailListItem.fastScrollLabel(sortOption: SortOption): String? =
    when (sortOption) {
        SortOption.ARTIST -> when (this) {
            is GenreDetailListItem.ArtistHeader -> extractFastScrollGlyph(artistName)
            is GenreDetailListItem.AlbumHeader -> extractFastScrollGlyph(album.songs.firstOrNull()?.artist)
            is GenreDetailListItem.SongItem -> extractFastScrollGlyph(song.artist)
            is GenreDetailListItem.Spacer,
            is GenreDetailListItem.Divider -> null
        }

        SortOption.ALBUM -> when (this) {
            is GenreDetailListItem.ArtistHeader -> null
            is GenreDetailListItem.AlbumHeader -> extractFastScrollGlyph(album.name)
            is GenreDetailListItem.SongItem -> extractFastScrollGlyph(song.album)
            is GenreDetailListItem.Spacer,
            is GenreDetailListItem.Divider -> null
        }

        SortOption.TITLE -> when (this) {
            is GenreDetailListItem.ArtistHeader -> null
            is GenreDetailListItem.AlbumHeader -> null
            is GenreDetailListItem.SongItem -> extractFastScrollGlyph(song.title)
            is GenreDetailListItem.Spacer,
            is GenreDetailListItem.Divider -> null
        }
    }
