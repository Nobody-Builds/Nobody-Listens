@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.twilighty.lmusic.presentation.screens

import com.twilighty.lmusic.presentation.navigation.navigateSafely
import com.twilighty.lmusic.presentation.navigation.navigateSafelyReplacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.lmusic.ui.theme.LocalLMusicDarkTheme
import com.twilighty.lmusic.ui.theme.LocalUseBrandColors
import com.twilighty.lmusic.ui.theme.LMusicStatusBarStyle
import com.twilighty.lmusic.ui.theme.nbPlayerScheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.presentation.components.ExpressiveScrollBar
import com.twilighty.lmusic.presentation.components.MiniPlayerHeight
import com.twilighty.lmusic.presentation.components.PlaylistBottomSheet
import com.twilighty.lmusic.presentation.components.SmartImage
import com.twilighty.lmusic.presentation.components.SongInfoBottomSheet
import com.twilighty.lmusic.presentation.components.resolveNavBarOccupiedHeight
import com.twilighty.lmusic.presentation.navigation.Screen
import com.twilighty.lmusic.presentation.viewmodel.ArtistDetailViewModel
import com.twilighty.lmusic.presentation.artist.ArtistStorySection
import com.twilighty.lmusic.presentation.viewmodel.ArtistAlbumSection
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.viewmodel.PlaylistViewModel
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbAvatarHeader
import com.twilighty.lmusic.presentation.nb.NbDetailAppBar
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbIconButtonStyle
import com.twilighty.lmusic.presentation.nb.NbPlayShuffleRow
import com.twilighty.lmusic.presentation.nb.NbSectionHeader
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbType
import com.twilighty.lmusic.utils.formatSongCount
import com.twilighty.lmusic.presentation.components.subcomps.EnhancedSongListItem
import kotlinx.coroutines.delay
import coil.size.Size
import androidx.compose.ui.res.stringResource
import com.twilighty.lmusic.R

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val storyState by viewModel.story.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    
    // Optimization: Defer heavy list rendering until navigation transition settles
    var isTransitionFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        isTransitionFinished = true
    }

    val lazyListState = rememberLazyListState()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val isDarkTheme = LocalLMusicDarkTheme.current
    val useBrandColors = LocalUseBrandColors.current
    val baseColorScheme = MaterialTheme.colorScheme

    // --- Dynamic color palette from pre-warmed ViewModel state ---
    // artistColorScheme is set by the ViewModel BEFORE isLoading becomes false,
    // so the very first composition already has the correct palette — no flash.
    // Daylight keeps the neutral canvas and uses the image colour only as the accent.
    val artistColorSchemePair by viewModel.artistColorScheme.collectAsStateWithLifecycle()
    val artistColorScheme = remember(artistColorSchemePair, isDarkTheme, baseColorScheme, useBrandColors) {
        val generated = artistColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
        if (useBrandColors) nbPlayerScheme(generated ?: baseColorScheme, baseColorScheme, isDarkTheme)
        else generated ?: baseColorScheme
    }

    // --- Image picker for custom artist image ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setCustomImage(it) }
    }

    LaunchedEffect(Unit) {
        playerViewModel.collapsePlayerSheet()
    }

    val headerScrolledAway by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    // Wrap in dynamic theme derived from the artist's image
    MaterialTheme(
        colorScheme = artistColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
    LMusicStatusBarStyle(color = MaterialTheme.colorScheme.surface)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val artistForBar = uiState.artist
            NbDetailAppBar(
                onBack = { navController.popBackStack() },
                title = if (headerScrolledAway) artistForBar?.name else null,
                backContentDescription = stringResource(R.string.auth_cd_back),
                actions = {
                    if (artistForBar != null) {
                        ArtistImageMenuButton(
                            hasCustomImage = !artistForBar.customImageUri.isNullOrBlank(),
                            onChangeImage = { imagePickerLauncher.launch("image/*") },
                            onClearCustomImage = { viewModel.clearCustomImage() }
                        )
                    }
                }
            )
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading && uiState.artist == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                }
                uiState.error != null && uiState.artist == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = Nb.danger,
                            style = NbType.body
                        )
                    }
                }
                uiState.artist != null -> {
                    val artist = uiState.artist!!
                    val songs = uiState.songs

                    val albumSections = uiState.albumSections
                    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
                    LaunchedEffect(albumSections) {
                        val currentKeys = albumSections.map { it.collapseKey() }.toSet()
                        currentKeys.forEach { key ->
                            if (expandedSections[key] == null) {
                                expandedSections[key] = true
                            }
                        }
                        expandedSections.keys
                            .filterNot { it in currentKeys }
                            .forEach { staleKey -> expandedSections.remove(staleKey) }
                    }

                    val showScrollBar by remember {
                        derivedStateOf {
                            headerScrolledAway &&
                                (lazyListState.canScrollForward || lazyListState.canScrollBackward)
                        }
                    }
                    val albumCount = albumSections.count { it.songs.isNotEmpty() }
                    val avatarSizePx = remember(density) { with(density) { 128.dp.roundToPx() } * 2 }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                        )
                    ) {
                        item(key = "artist_header", contentType = "artist_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                NbAvatarHeader(
                                    title = artist.name,
                                    caption = buildString {
                                        if (albumCount > 0) {
                                            append(if (albumCount == 1) "1 album" else "$albumCount albums")
                                            append(" · ")
                                        }
                                        append(formatSongCount(songs.size))
                                        append(" in your library")
                                    }
                                ) {
                                    val displayUrl = uiState.effectiveImageUrl?.takeIf { it.isNotBlank() }
                                    Box(Modifier.size(128.dp).clip(CircleShape)) {
                                        NbArt(model = null, title = artist.name, size = 128.dp, shape = CircleShape)
                                        if (displayUrl != null) {
                                            SmartImage(
                                                model = displayUrl,
                                                contentDescription = artist.name,
                                                shape = CircleShape,
                                                targetSize = Size(avatarSizePx, avatarSizePx),
                                                allowHardware = true,
                                                placeHolderBackgroundColor = Nb.muted,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                NbPlayShuffleRow(
                                    playLabel = "Play all",
                                    enabled = songs.isNotEmpty(),
                                    onPlay = {
                                        songs.firstOrNull()?.let { playerViewModel.showAndPlaySong(it, songs, artist.name) }
                                    },
                                    onShuffle = {
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongsShuffled(
                                                songs,
                                                artist.name,
                                                startAtZero = true
                                            )
                                        }
                                    }
                                )
                                ArtistStorySection(
                                    artistName = artist.name,
                                    state = storyState,
                                    canPlay = songs.isNotEmpty(),
                                    onPlaySong = { song -> playerViewModel.showAndPlaySong(song, songs, artist.name) },
                                    onPlayArtist = {
                                        songs.firstOrNull()?.let { playerViewModel.showAndPlaySong(it, songs, artist.name) }
                                    }
                                )
                                if (albumCount > 0) {
                                    NbSectionHeader(title = if (albumCount == 1) "Album" else "Albums")
                                }
                            }
                        }

                        albumSections.forEachIndexed { index, section ->
                            if (section.songs.isEmpty()) return@forEachIndexed

                            val sectionKey = section.collapseKey()
                            val isExpanded = expandedSections[sectionKey] ?: true
                            val sectionSongs = if (isTransitionFinished) section.songs else section.songs.take(5)

                            item(
                                key = "${sectionKey}_header",
                                contentType = "artist_section_header"
                            ) {
                                Column {
                                    if (index > 0) NbDivider(Modifier.padding(vertical = 4.dp))
                                    CollapsibleAlbumSectionHeader(
                                        section = section,
                                        isExpanded = isExpanded,
                                        onToggleExpanded = {
                                            expandedSections[sectionKey] = !isExpanded
                                        },
                                        onPlayAlbum = {
                                            section.songs.firstOrNull()?.let { firstSong ->
                                                playerViewModel.showAndPlaySong(firstSong, section.songs)
                                            }
                                        }
                                    )
                                }
                            }

                            if (isExpanded) {
                                itemsIndexed(
                                    items = sectionSongs,
                                    key = { songIndex, song -> "${sectionKey}_song_${song.id}_$songIndex" },
                                    contentType = { _, _ -> "artist_section_song" }
                                ) { songIndex, song ->
                                    ArtistAlbumSectionSongItem(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(durationMillis = 180),
                                            fadeOutSpec = tween(durationMillis = 120),
                                            placementSpec = tween(durationMillis = 200)
                                        ),
                                        song = song,
                                        songIndex = songIndex,
                                        songCount = section.songs.size,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        onSongClick = {
                                            playerViewModel.showAndPlaySong(song, section.songs)
                                        },
                                        onMoreOptionsClick = {
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }
                            }

                            item(
                                key = "${sectionKey}_footer",
                                contentType = "artist_section_footer"
                            ) {
                                Spacer(
                                    modifier = Modifier.height(
                                        if (index == albumSections.lastIndex) 24.dp else 8.dp
                                    )
                                )
                            }
                        }
                    }

                    if (showScrollBar) {
                        ExpressiveScrollBar(
                            listState = lazyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    top = 12.dp,
                                    bottom = MiniPlayerHeight + systemNavBarInset + 8.dp
                                )
                        )
                    }
                }
            }
        }
        }
    } // End Surface

    // Bottom sheets inherit the artist's dynamic color palette — same approach as AlbumDetailScreen
    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            currentSong?.let { favoriteIds.contains(it.id) } ?: false
        }

        if (currentSong != null) {
            val removeFromListTrigger = remember(uiState.songs) {
                {
                    viewModel.removeSongFromAlbumSection(currentSong.id)
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
                    showPlaylistBottomSheet = true
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
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
    } // End MaterialTheme
}

private fun ArtistAlbumSection.collapseKey(): String = "artist_album_${albumId}_${title}"

@Composable
private fun ArtistImageMenuButton(
    hasCustomImage: Boolean,
    onChangeImage: () -> Unit,
    onClearCustomImage: () -> Unit,
) {
    var showImageMenu by remember { mutableStateOf(false) }
    Box {
        NbIconButton(
            imageVector = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.presentation_batch_d_edit_artist_image_cd),
            onClick = { showImageMenu = true }
        )
        DropdownMenu(
            expanded = showImageMenu,
            onDismissRequest = { showImageMenu = false },
            shape = NbShapes.md,
            containerColor = Nb.canvas,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Nb.border)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.presentation_batch_d_change_photo), style = NbType.body, color = Nb.text) },
                leadingIcon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = Nb.textSecondary) },
                onClick = {
                    showImageMenu = false
                    onChangeImage()
                }
            )
            if (hasCustomImage) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.presentation_batch_d_reset_to_default), style = NbType.body, color = Nb.danger) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Nb.danger) },
                    onClick = {
                        showImageMenu = false
                        onClearCustomImage()
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsibleAlbumSectionHeader(
    section: ArtistAlbumSection,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAlbum: () -> Unit
) {
    val expandIconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "ArtistAlbumExpandRotation"
    )
    val subtitle = remember(section.year, section.songs.size) {
        buildString {
            section.year?.takeIf { it > 0 }?.let {
                append(it.toString())
                append(" · ")
            }
            append(formatSongCount(section.songs.size))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .clickable(onClick = onToggleExpanded)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NbArt(
            model = section.albumArtUriString,
            title = section.title,
            size = 56.dp,
            shape = NbShapes.md,
            contentDescription = section.title
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = NbType.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Nb.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = NbType.meta,
                color = Nb.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NbIconButton(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.presentation_batch_d_cd_play_title, section.title),
            onClick = onPlayAlbum,
            style = NbIconButtonStyle.Tonal,
            size = 40.dp,
            iconSize = 22.dp
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (isExpanded) {
                stringResource(R.string.presentation_batch_d_cd_collapse_title, section.title)
            } else {
                stringResource(R.string.presentation_batch_d_cd_expand_title, section.title)
            },
            tint = Nb.textMuted,
            modifier = Modifier.graphicsLayer {
                rotationZ = expandIconRotation
            }
        )
    }
}

@Composable
private fun ArtistAlbumSectionSongItem(
    modifier: Modifier = Modifier,
    song: Song,
    songIndex: Int,
    songCount: Int,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onMoreOptionsClick: () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        EnhancedSongListItem(
            modifier = Modifier.fillMaxWidth(),
            song = song,
            isCurrentSong = isCurrentSong,
            isPlaying = isPlaying,
            showAlbumArt = false,
            onMoreOptionsClick = { onMoreOptionsClick() },
            onClick = onSongClick
        )
    }
}
