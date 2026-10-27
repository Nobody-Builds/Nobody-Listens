package com.nobodybuilds.listens.presentation.components.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.presentation.components.AlbumCarouselSection
import com.nobodybuilds.listens.presentation.viewmodel.PlayerSheetState
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.ImmutableList

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun FullPlayerAlbumCoverSection(
    song: Song,
    currentPlaybackQueue: ImmutableList<Song>,
    currentMediaItemIndex: Int,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    placeholderColor: Color,
    placeholderOnColor: Color,
    albumArtQuality: AlbumArtQuality,
    requestedScrollIndex: Int?,
    onSongSelected: (Song) -> Unit,
    onAlbumClick: (Song) -> Unit,
    isFavoriteProvider: () -> Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayAlbumCarousel
    val shouldApplyPausedScale = !isPlayingProvider() && !playWhenReadyProvider()

    // Living artwork: breathes in time with the song's tempo while playing and expanded.
    val livingArtworkViewModel: LivingArtworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val livingArtworkEnabled by livingArtworkViewModel.enabled.collectAsStateWithLifecycle()
    val livingArtworkTempo by livingArtworkViewModel.tempoBpm.collectAsStateWithLifecycle()
    LaunchedEffect(song.id, livingArtworkEnabled) {
        if (livingArtworkEnabled) livingArtworkViewModel.loadTempo(song.id)
    }
    val livingArtworkActive = livingArtworkEnabled &&
        currentSheetState == PlayerSheetState.EXPANDED &&
        !isSheetDragGestureActive &&
        (isPlayingProvider() || playWhenReadyProvider())
    val livingArtwork = rememberLivingArtworkState(
        active = livingArtworkActive,
        tempoBpm = livingArtworkTempo
    )

    // Double-tap the cover to like (Settings › Behavior › Gestures can turn it off).
    val gestureSettings: com.nobodybuilds.listens.presentation.gestures.GestureSettingsViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val doubleTapToLike by gestureSettings.doubleTapToLike.collectAsStateWithLifecycle()
    val haptics = LocalNobodyListensHaptics.current
    val heartPop = rememberHeartPopState()
    val onCoverDoubleTap: (Song) -> Unit = {
        val nowLiked = !isFavoriteProvider()
        if (nowLiked) haptics.heartbeat() else haptics.like(false)
        heartPop.pop(liked = nowLiked)
        onFavoriteToggle()
    }
    val albumArtScale by animateFloatAsState(
        targetValue = if (shouldApplyPausedScale) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "AlbumArtScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Artwork: about 72-75% of a phone's width, never more than 300dp, and never taller
        // than the space available (landscape).
        val heightCap = if (constraints.hasBoundedHeight) maxHeight - 16.dp else MAX_ART_SIZE
        val artSize = minOf(maxWidth * 0.82f, MAX_ART_SIZE, heightCap).coerceAtLeast(120.dp)
        val carouselHeight = when (carouselStyle) {
            CarouselStyle.NO_PEEK -> artSize
            CarouselStyle.ONE_PEEK -> minOf(maxWidth * 0.8f, artSize)
            CarouselStyle.TWO_PEEK -> minOf(maxWidth * 0.6f, artSize)
            else -> minOf(maxWidth * 0.8f, artSize)
        }
        // A no-peek carousel shows one square page, so it is as wide as it is tall and centred.
        val carouselWidthModifier = if (carouselStyle == CarouselStyle.NO_PEEK || currentPlaybackQueue.size <= 1) {
            Modifier.width(carouselHeight)
        } else {
            Modifier.fillMaxWidth()
        }

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
            switchOnDragRelease = loadingTweaks.switchOnDragRelease,
            isSheetDragGestureActive = isSheetDragGestureActive,
            sharedBoundsModifier = Modifier.fillMaxWidth().height(carouselHeight),
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.08f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(
                        Modifier
                            .height(carouselHeight)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AlbumPlaceholder(
                            height = carouselHeight,
                            color = placeholderColor,
                            onColor = placeholderOnColor,
                            modifier = carouselWidthModifier.graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                        )
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumCarouselSection(
                currentSong = song,
                queue = currentPlaybackQueue,
                expansionFraction = 1f,
                currentMediaItemIndex = currentMediaItemIndex,
                requestedScrollIndex = requestedScrollIndex,
                onSongSelected = { newSong ->
                    if (newSong.id != song.id) {
                        onSongSelected(newSong)
                    }
                },
                onAlbumClick = onAlbumClick,
                onAlbumDoubleTap = if (doubleTapToLike) onCoverDoubleTap else null,
                carouselStyle = carouselStyle,
                modifier = Modifier
                    .then(carouselWidthModifier)
                    .height(carouselHeight)
                    .graphicsLayer {
                        livingArtwork.apply(this, albumArtScale)
                    },
                albumArtQuality = albumArtQuality
            )
            HeartPopOverlay(
                state = heartPop,
                modifier = Modifier.align(Alignment.Center)
            )
            }
        }
    }
}

@Composable
internal fun FullPlayerProgressSection(
    song: Song,
    playbackMetadataMediaId: String?,
    playbackMetadataMimeType: String?,
    playbackMetadataBitrate: Int?,
    playbackMetadataSampleRate: Int?,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    showPlayerFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    progressActiveColor: Color,
    playerOnBaseColor: Color,
    allowRealtimeUpdates: Boolean,
    isSheetDragGestureActive: Boolean,
    loadingTweaks: FullPlayerLoadingTweaks
) {
    val isMetadataForCurrentSong = playbackMetadataMediaId == song.id
    val audioMimeType = if (isMetadataForCurrentSong) {
        playbackMetadataMimeType ?: song.mimeType
    } else {
        song.mimeType
    }
    val audioBitrate = if (isMetadataForCurrentSong) {
        playbackMetadataBitrate ?: song.bitrate
    } else {
        song.bitrate
    }
    val audioSampleRate = if (isMetadataForCurrentSong) {
        playbackMetadataSampleRate ?: song.sampleRate
    } else {
        song.sampleRate
    }

    PlayerProgressBarSection(
        songId = song.id,
        currentPositionProvider = currentPositionProvider,
        totalDurationValue = totalDurationValue,
        songDurationHintMs = song.duration,
        audioMimeType = audioMimeType,
        audioBitrate = audioBitrate,
        audioSampleRate = audioSampleRate,
        showAudioFileInfo = showPlayerFileInfo,
        onSeek = onSeek,
        expansionFractionProvider = expansionFractionProvider,
        isPlayingProvider = isPlayingProvider,
        currentSheetState = currentSheetState,
        activeTrackColor = progressActiveColor,
        inactiveTrackColor = playerOnBaseColor.copy(alpha = 0.2f),
        thumbColor = progressActiveColor,
        timeTextColor = playerOnBaseColor,
        allowRealtimeUpdates = allowRealtimeUpdates,
        isSheetDragGestureActive = isSheetDragGestureActive,
        loadingTweaks = loadingTweaks
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullPlayerSongMetadataSection(
    song: Song,
    currentSongArtists: List<Artist>,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isLandscape: Boolean,
    onLyricsClick: () -> Unit,
    playerOnBaseColor: Color,
    playerViewModel: PlayerViewModel,
    gradientEdgeColor: Color,
    chipColor: Color,
    chipContentColor: Color,
    onQueueClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.20f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(70.dp))
            } else {
                MetadataPlaceholder(
                    expansionFractionProvider = expansionFractionProvider,
                    color = placeholderColor,
                    onColor = placeholderOnColor,
                    showQueueButtons = isLandscape
                )
            }
        }
    ) {
        SongMetadataDisplaySection(
            modifier = Modifier
                .padding(start = 0.dp),
            onClickLyrics = onLyricsClick,
            song = song,
            currentSongArtists = currentSongArtists,
            expansionFractionProvider = expansionFractionProvider,
            textColor = playerOnBaseColor,
            artistTextColor = playerOnBaseColor.copy(alpha = 0.7f),
            playerViewModel = playerViewModel,
            gradientEdgeColor = gradientEdgeColor,
            chipColor = chipColor,
            chipContentColor = chipContentColor,
            showQueueButton = isLandscape,
            onClickQueue = onQueueClick,
            onClickArtist = onArtistClick
        )
    }
}

@Composable
internal fun FullPlayerPortraitContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                horizontal = 24.dp,
                vertical = 0.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        albumCoverSection(Modifier)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.align(Alignment.Start)) {
                songMetadataSection()
            }
            playerProgressSection()
        }

        controlsSection()
    }
}

@Composable
internal fun FullPlayerLandscapeContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                horizontal = 24.dp,
                vertical = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        albumCoverSection(
            Modifier
                .fillMaxHeight()
                .weight(1f)
        )
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(
                    horizontal = 0.dp,
                    vertical = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            songMetadataSection()
            playerProgressSection()
            controlsSection()
        }
    }
}

private val MAX_ART_SIZE = 300.dp
