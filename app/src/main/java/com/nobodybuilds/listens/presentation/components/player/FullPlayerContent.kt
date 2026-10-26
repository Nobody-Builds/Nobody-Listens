package com.nobodybuilds.listens.presentation.components.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import com.nobodybuilds.listens.data.model.Lyrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.presentation.components.LocalMaterialTheme
import com.nobodybuilds.listens.presentation.components.LyricsSheet
import com.nobodybuilds.listens.presentation.components.subcomps.FetchLyricsDialog
import com.nobodybuilds.listens.presentation.viewmodel.LyricsSearchUiState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerSheetState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.utils.LyricsImportSecurity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed

private const val SKIP_COMMAND_GUARD_MS = 96L

private enum class SkipDirection { PREVIOUS, NEXT }

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayerContent(
    currentSong: Song?,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int = -1,
    isShuffleEnabled: Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    allowRealtimeUpdates: Boolean = true,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    repeatModeProvider: () -> Int,
    isShuffleEnabledProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    lyricsProvider: () -> Lyrics? = { null }, 
    // State
    isCastConnecting: Boolean = false,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShowSongOptions: (Song) -> Unit = {}
) {
    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()

    // Single subscription — replaces 11 independent collectAsStateWithLifecycle calls.
    // distinctUntilChanged in the ViewModel ensures this only emits when something
    // actually changed, batching multiple rapid updates into one recomposition.
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val currentSongArtists = fullPlayerSlice.currentSongArtists
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val albumArtQuality = fullPlayerSlice.albumArtQuality
    val playbackAudioMetadata = fullPlayerSlice.audioMetadata
    val showPlayerFileInfo = fullPlayerSlice.showPlayerFileInfo
    val immersiveLyricsEnabled = fullPlayerSlice.immersiveLyricsEnabled
    val immersiveLyricsTimeout = fullPlayerSlice.immersiveLyricsTimeout
    val isImmersiveTemporarilyDisabled = fullPlayerSlice.isImmersiveTemporarilyDisabled
    val isRemotePlaybackActive = fullPlayerSlice.isRemotePlaybackActive
    val selectedRouteName = fullPlayerSlice.selectedRouteName
    val isBluetoothEnabled = fullPlayerSlice.isBluetoothEnabled
    val bluetoothName = fullPlayerSlice.bluetoothName
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val queueGestureBottomExclusion = maxOf(20.dp, navigationBarBottomInset + 8.dp)
    val queueGestureBottomExclusionPx = with(LocalDensity.current) {
        queueGestureBottomExclusion.toPx()
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

    val filePickerLauncher = rememberFullPlayerLyricsImportLauncher(
        playerViewModel = playerViewModel,
        songId = currentSong?.id,
        onImported = {
            showFetchLyricsDialog = false
            showLyricsSheet = true
        }
    )

    // totalDurationValue is derived from stablePlayerState, so it's fine.
    // OPTIMIZATION: Use passed provider instead of collecting flow
    val totalDurationValue = totalDurationProvider()

    val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
    val playerAccentColor = LocalMaterialTheme.current.primary
    val playerOnAccentColor = LocalMaterialTheme.current.onPrimary
    val transportPlayPauseColors = expressivePlayPauseButtonColors(LocalMaterialTheme.current)
    val transportSkipColors = expressiveSkipButtonColors(LocalMaterialTheme.current)
    val transportSkipButtonColors = TransportButtonColors(
        container = playerAccentColor,
        content = playerOnAccentColor
    )
    val progressActiveColor = playerOnBaseColor

    val placeholderColor = playerOnBaseColor.copy(alpha = 0.1f)
    val placeholderOnColor = playerOnBaseColor.copy(alpha = 0.2f)

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE


    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = {
        val lyrics = lyricsProvider()
        if (lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
            // Si no hay letra, mostramos el diálogo para buscar
            showFetchLyricsDialog = true
        } else {
            // Si hay letra, mostramos el sheet directamente
            showLyricsSheet = true
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = song, // Use 'song' which is derived from args/retained
                onConfirm = { forcePick ->
                    // El usuario confirma, iniciamos la búsqueda
                    playerViewModel.fetchLyricsForCurrentSong(forcePick)
                },
                onPickResult = { result ->
                    playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
                },
                onManualSearch = { title, artist ->
                    playerViewModel.searchLyricsManually(title, artist)
                },
                onDismiss = {
                    // El usuario cancela o cierra el diálogo
                    showFetchLyricsDialog = false
                    playerViewModel.resetLyricsSearchState()
                },
                onImport = {
                    filePickerLauncher.launch(com.nobodybuilds.listens.utils.LyricsImportSecurity.pickerMimeTypes())
                }
            )
        }
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            else -> Unit
        }
    }

    val onAlbumSongSelected: (Song) -> Unit = { newSong ->
        playerViewModel.showAndPlaySong(
            song = newSong,
            contextSongs = currentPlaybackQueue,
            queueName = currentQueueSourceName
        )
    }

    val onSongMetadataQueueClick = {
        showSongInfoBottomSheet = true
        onShowQueueClicked()
    }

    val onSongMetadataArtistClick = {
        val resolvedArtistId = currentSongArtists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: song.artistId
        if (currentSongArtists.size > 1) {
            showArtistPicker = true
        } else {
            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
        }
    }

    var pendingCarouselIndex by remember { mutableStateOf<Int?>(null) }
    val currentQueueIndex = remember(song.id, currentMediaItemIndex, currentPlaybackQueue) {
        resolveQueueIndex(
            queue = currentPlaybackQueue,
            songId = song.id,
            currentMediaItemIndex = currentMediaItemIndex
        )
    }
    val skipRequests = remember {
        MutableSharedFlow<SkipDirection>(
            extraBufferCapacity = 16
        )
    }
    val latestQueue by rememberUpdatedState(currentPlaybackQueue)
    val latestSongId by rememberUpdatedState(song.id)
    val latestCurrentQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestRepeatMode by rememberUpdatedState(repeatMode)
    val latestIsRemotePlaybackActive by rememberUpdatedState(isRemotePlaybackActive)
    val latestCurrentPositionProvider by rememberUpdatedState(currentPositionProvider)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    LaunchedEffect(currentQueueIndex, pendingCarouselIndex) {
        if (pendingCarouselIndex == currentQueueIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(pendingCarouselIndex, currentQueueIndex) {
        val targetIndex = pendingCarouselIndex ?: return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        if (pendingCarouselIndex == targetIndex && currentQueueIndex != targetIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(skipRequests) {
        skipRequests.collect { direction ->
            when (direction) {
                SkipDirection.NEXT -> latestOnNext()
                SkipDirection.PREVIOUS -> latestOnPrevious()
            }

            kotlinx.coroutines.delay(SKIP_COMMAND_GUARD_MS)
        }
    }

    fun predictSkipCarouselIndex(direction: SkipDirection): Int? {
        val queueSnapshot = latestQueue
        val baseIndex = pendingCarouselIndex
            ?: latestCurrentQueueIndex
            ?: queueSnapshot.indexOfFirst { it.id == latestSongId }.takeIf { it >= 0 }

        return when (direction) {
            SkipDirection.NEXT -> predictSkipNextCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
            SkipDirection.PREVIOUS -> predictSkipPreviousCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                currentPositionMs = latestCurrentPositionProvider(),
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
        }
    }

    fun requestSkip(direction: SkipDirection) {
        val predictedTargetIndex = predictSkipCarouselIndex(direction)
        if (skipRequests.tryEmit(direction) && predictedTargetIndex != null) {
            pendingCarouselIndex = predictedTargetIndex
        }
    }

    val onNextWithOptimisticCarousel = {
        requestSkip(SkipDirection.NEXT)
        Unit
    }

    val onPreviousWithOptimisticCarousel = {
        requestSkip(SkipDirection.PREVIOUS)
        Unit
    }

    val albumCoverSection: @Composable (Modifier) -> Unit = { modifier ->
        FullPlayerAlbumCoverSection(
            song = song,
            currentPlaybackQueue = currentPlaybackQueue,
            currentMediaItemIndex = currentQueueIndex ?: currentMediaItemIndex,
            carouselStyle = carouselStyle,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            isPlayingProvider = isPlayingProvider,
            playWhenReadyProvider = playWhenReadyProvider,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            albumArtQuality = albumArtQuality,
            requestedScrollIndex = pendingCarouselIndex,
            onSongSelected = onAlbumSongSelected,
            onAlbumClick = { albumSong ->
                playerViewModel.triggerAlbumNavigationFromPlayer(albumSong.albumId)
            },
            isFavoriteProvider = isFavoriteProvider,
            onFavoriteToggle = onFavoriteToggle,
            modifier = modifier
        )
    }

    val playerProgressSection: @Composable () -> Unit = {
        FullPlayerProgressSection(
            song = song,
            playbackMetadataMediaId = playbackAudioMetadata.mediaId,
            playbackMetadataMimeType = playbackAudioMetadata.mimeType,
            playbackMetadataBitrate = playbackAudioMetadata.bitrate,
            playbackMetadataSampleRate = playbackAudioMetadata.sampleRate,
            currentPositionProvider = currentPositionProvider,
            totalDurationValue = totalDurationValue,
            showPlayerFileInfo = showPlayerFileInfo,
            onSeek = onSeek,
            expansionFractionProvider = expansionFractionProvider,
            isPlayingProvider = isPlayingProvider,
            currentSheetState = currentSheetState,
            progressActiveColor = progressActiveColor,
            playerOnBaseColor = playerOnBaseColor,
            allowRealtimeUpdates = allowRealtimeUpdates,
            isSheetDragGestureActive = isSheetDragGestureActive,
            loadingTweaks = loadingTweaks
        )
    }

    val controlsSection: @Composable () -> Unit = {
        FullPlayerControlsSection(
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isPlayingProvider = isPlayingProvider,
            onPrevious = onPreviousWithOptimisticCarousel,
            onPlayPause = onPlayPause,
            onNext = onNextWithOptimisticCarousel,
            transportPlayPauseColors = transportPlayPauseColors,
            transportSkipColors = transportSkipButtonColors,
            isShuffleEnabledProvider = isShuffleEnabledProvider,
            shuffleTransitionInProgress = shuffleTransitionInProgress,
            repeatModeProvider = repeatModeProvider,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            sleepTimerSlot = { slotModifier ->
                PlayerSleepTimerSegment(
                    playerViewModel = playerViewModel,
                    onClick = { showSleepTimerSheet = true },
                    modifier = slotModifier
                )
            }
        )
    }

    val portraitSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = false,
            onLyricsClick = onLyricsClick,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick
        )
    }

    val landscapeSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = true,
            onLyricsClick = onLyricsClick,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fullPlayerQueueSwipeGesture(
            currentSheetState = currentSheetState,
            queueGestureBottomExclusionPx = queueGestureBottomExclusionPx,
            expansionFractionProvider = expansionFractionProvider,
            onQueueDragStart = onQueueDragStart,
            onQueueDrag = onQueueDrag,
            onQueueRelease = onQueueRelease
        ),
        topBar = {
            FullPlayerTopBar(
                isLandscape = isLandscape,
                expansionFractionProvider = expansionFractionProvider,
                isCastConnecting = isCastConnecting,
                isRemotePlaybackActive = isRemotePlaybackActive,
                currentSong = currentSong,
                selectedRouteName = selectedRouteName,
                isBluetoothEnabled = isBluetoothEnabled,
                bluetoothName = bluetoothName,
                playerAccentColor = playerAccentColor,
                playerOnAccentColor = playerOnAccentColor,
                onCollapse = onCollapse,
                onShowCastClicked = onShowCastClicked,
                onQueueClick = {
                    showSongInfoBottomSheet = true
                    onShowQueueClicked()
                },
                onSongOptionsClick = { onShowSongOptions(song) }
            )
        }
    ) { paddingValues ->
        // MD3: 方向变化时先 alpha=0 再淡入新布局，避免双布局同时测量导致错位
        var contentVisible by remember(isLandscape) { mutableStateOf(false) }
        LaunchedEffect(isLandscape) { contentVisible = true }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "orientationAlpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            if (isLandscape) {
                FullPlayerLandscapeContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = landscapeSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection
                )
            } else {
                FullPlayerPortraitContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = portraitSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection
                )
            }
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playbackPositionFlow = playerViewModel.currentPlaybackPosition,
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch(com.nobodybuilds.listens.utils.LyricsImportSecurity.pickerMimeTypes()) },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong?.id?.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            lyricsTextStyle = MaterialTheme.typography.titleLarge,
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showLyricsSheet = false },
            onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            },
            onNext = onNext,
            onPrev = onPrevious,
            immersiveLyricsEnabled = immersiveLyricsEnabled,
            immersiveLyricsTimeout = immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
            onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle
        )
    }

    if (showSleepTimerSheet) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            PlayerSleepTimerSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showSleepTimerSheet = false }
            )
        }
    }

    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && currentSongArtists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            song = song,
            artists = currentSongArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                playerViewModel.triggerArtistNavigationFromPlayer(artist.id)
                showArtistPicker = false
            }
        )
    }
}
