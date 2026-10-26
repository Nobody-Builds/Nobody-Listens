@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.twilighty.lmusic.presentation.screens

import com.twilighty.lmusic.presentation.navigation.navigateSafely
import com.twilighty.lmusic.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twilighty.lmusic.ui.theme.LocalLMusicDarkTheme
import com.twilighty.lmusic.ui.theme.LocalUseBrandColors
import com.twilighty.lmusic.ui.theme.LMusicStatusBarStyle
import com.twilighty.lmusic.ui.theme.nbPlayerScheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.model.Album
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.presentation.components.ExpressiveScrollBar
import com.twilighty.lmusic.presentation.components.MiniPlayerHeight
import com.twilighty.lmusic.presentation.components.PlaylistBottomSheet
import com.twilighty.lmusic.presentation.components.SmartImage
import com.twilighty.lmusic.presentation.components.SongInfoBottomSheet
import com.twilighty.lmusic.presentation.components.resolveNavBarOccupiedHeight
import com.twilighty.lmusic.presentation.components.subcomps.EnhancedSongListItem
import com.twilighty.lmusic.presentation.navigation.Screen
import com.twilighty.lmusic.presentation.viewmodel.AlbumDetailViewModel
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.viewmodel.PlaylistViewModel
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbCollectionHeader
import com.twilighty.lmusic.presentation.nb.NbDetailAppBar
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbPlayShuffleRow
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbType
import com.twilighty.lmusic.presentation.nb.NbYearBlock
import com.twilighty.lmusic.utils.formatSongCount
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val isDarkTheme = LocalLMusicDarkTheme.current
    val useBrandColors = LocalUseBrandColors.current
    val baseColorScheme = MaterialTheme.colorScheme
    val albumArtUri = uiState.album?.albumArtUriString?.takeIf { it.isNotBlank() }
    val albumColorSchemeFlow = remember(albumArtUri) {
        albumArtUri?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val albumColorSchemePair = albumColorSchemeFlow?.collectAsStateWithLifecycle()?.value
    // Daylight: keep the neutral canvas and take only the accent from the artwork.
    val albumColorScheme = remember(albumColorSchemePair, isDarkTheme, baseColorScheme, useBrandColors) {
        val generated = albumColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
        if (useBrandColors) {
            nbPlayerScheme(generated ?: baseColorScheme, baseColorScheme, isDarkTheme)
        } else {
            generated ?: baseColorScheme
        }
    }
    var headerArtworkLoaded by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    var themeRequestIssued by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    LaunchedEffect(albumArtUri, headerArtworkLoaded, themeRequestIssued) {
        if (!themeRequestIssued && headerArtworkLoaded && albumArtUri != null) {
            themeRequestIssued = true
            playerViewModel.themeStateHolder.ensureAlbumColorScheme(albumArtUri)
        }
    }
    val density = LocalDensity.current

    MaterialTheme(
        colorScheme = albumColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {

        val isMiniPlayerVisible = stablePlayerState.currentSong != null
        val listBottomPadding by animateDpAsState(
            targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 16.dp,
            label = "listBottomPadding"
        )

        LMusicStatusBarStyle(color = MaterialTheme.colorScheme.surface)

        when {
            uiState.isLoading && uiState.album == null -> {
                Box(modifier = Modifier.fillMaxSize().background(Nb.canvas), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }

            uiState.error != null && uiState.album == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Nb.canvas)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error!!,
                        color = Nb.danger,
                        style = NbType.body
                    )
                }
            }

            uiState.album != null -> {
                val album = uiState.album!!
                val songs = uiState.songs
                val songsByDisc = remember(songs) {
                    songs.groupBy { it.discNumber ?: 1 }
                }
                val lazyListState = rememberLazyListState()
                val headerScrolledAway by remember {
                    derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
                }
                val artistIdForLink = remember(songs) { songs.firstOrNull()?.artistId }
                val totalMinutes = remember(songs) { (songs.sumOf { it.duration } / 60_000L).toInt() }
                val genres = remember(songs) {
                    songs.mapNotNull { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }.distinct().take(2)
                }
                val year = remember(album, songs) {
                    album.year.takeIf { it > 0 } ?: songs.firstOrNull { it.year > 0 }?.year ?: 0
                }
                val artRequestSize = remember(density) {
                    with(density) { 148.dp.roundToPx() }.let { Size(it * 2, it * 2) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Nb.canvas)
                ) {
                    NbDetailAppBar(
                        onBack = { navController.popBackStack() },
                        title = if (headerScrolledAway) album.title else null,
                        backContentDescription = stringResource(R.string.auth_cd_back)
                    )
                    Box(Modifier.weight(1f)) {
                        val showScrollBar = lazyListState.canScrollForward || lazyListState.canScrollBackward
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 4.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = listBottomPadding + systemNavBarInset + 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item(key = "album_header", contentType = "header") {
                                AlbumHeader(
                                    album = album,
                                    songsCount = songs.size,
                                    totalMinutes = totalMinutes,
                                    genres = genres,
                                    year = year,
                                    artRequestSize = artRequestSize,
                                    onHeaderArtworkState = { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            headerArtworkLoaded = true
                                        }
                                    },
                                    onArtistClick = artistIdForLink?.let { id ->
                                        { navController.navigateSafely(Screen.ArtistDetail.createRoute(id)); Unit }
                                    },
                                    onPlay = {
                                        songs.firstOrNull()?.let { playerViewModel.showAndPlaySong(it, songs) }
                                    },
                                    onShuffle = {
                                        if (songs.isNotEmpty()) {
                                            val randomSong = songs.random()
                                            playerViewModel.showAndPlaySong(randomSong, songs)
                                        }
                                    },
                                    enabled = songs.isNotEmpty()
                                )
                            }
                            songsByDisc.forEach { (discNumber, discSongs) ->
                                if (songsByDisc.size > 1) {
                                    item(key = "disc_header_$discNumber") {
                                        NbOverline(
                                            text = stringResource(R.string.disc_number_header, discNumber),
                                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                                        )
                                    }
                                }
                                items(
                                    items = discSongs,
                                    key = { song -> "album_song_${song.id}" },
                                    contentType = { "album_song" }
                                ) { song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        showAlbumArt = false,
                                        onMoreOptionsClick = {
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        },
                                        onClick = { playerViewModel.showAndPlaySong(song, songs) }
                                    )
                                }
                            }
                        }

                        if (showScrollBar && headerScrolledAway) {
                            ExpressiveScrollBar(
                                listState = lazyListState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(
                                        top = 12.dp,
                                        bottom = listBottomPadding + systemNavBarInset + 24.dp
                                    )
                            )
                        }
                    }
                }
            }
        }
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo
            val isFavorite = remember(currentSong?.id, favoriteIds) {
                derivedStateOf { currentSong?.let { favoriteIds.contains(it.id) } }
            }.value ?: false

            if (currentSong != null) {
                val removeFromListTrigger = remember(uiState.songs) {
                    {
                        viewModel.update(uiState.songs.filterNot { it.id == currentSong.id })
                    }
                }
                SongInfoBottomSheet(
                    song = currentSong,
                    isFavorite = isFavorite,
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
                            navController.navigateSafelyReplacing(
                                route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                                patternToPop = Screen.GenreDetail.route
                            )
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
                    removeFromListTrigger = removeFromListTrigger
                )
                if (showPlaylistBottomSheet) {
                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = listOf(currentSong),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}


@Composable
private fun AlbumHeader(
    album: Album,
    songsCount: Int,
    totalMinutes: Int,
    genres: List<String>,
    year: Int,
    artRequestSize: Size,
    onHeaderArtworkState: (AsyncImagePainter.State) -> Unit,
    onArtistClick: (() -> Unit)?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        NbCollectionHeader(
            overline = "Album",
            title = album.title,
            titleColor = Nb.accentText,
            subtitle = album.artist.takeIf { it.isNotBlank() },
            onSubtitleClick = onArtistClick,
        ) {
            Box(Modifier.size(148.dp).clip(NbShapes.lg)) {
                NbArt(model = null, title = album.title, size = 148.dp, shape = NbShapes.lg)
                if (!album.albumArtUriString.isNullOrBlank()) {
                    SmartImage(
                        model = album.albumArtUriString,
                        contentDescription = stringResource(R.string.album_cover_for, album.title),
                        shape = NbShapes.lg,
                        targetSize = artRequestSize,
                        allowHardware = true,
                        placeHolderBackgroundColor = Nb.muted,
                        onState = onHeaderArtworkState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (year > 0) NbYearBlock(top = "Year", number = year.toString())
            Column(Modifier.weight(1f)) {
                val minutesText = when {
                    totalMinutes <= 0 -> null
                    totalMinutes < 60 -> "$totalMinutes min"
                    else -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
                }
                Text(
                    text = listOfNotNull(formatSongCount(songsCount), minutesText).joinToString(" · "),
                    style = NbType.label.copy(fontSize = 15.sp),
                    color = Nb.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.joinToString(", "),
                        style = NbType.caption,
                        color = Nb.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        NbPlayShuffleRow(onPlay = onPlay, onShuffle = onShuffle, enabled = enabled)
    }
}
