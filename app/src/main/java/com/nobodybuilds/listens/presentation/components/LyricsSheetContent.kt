package com.nobodybuilds.listens.presentation.components

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import com.nobodybuilds.listens.presentation.components.SmartImage
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.data.model.SyncedLine
import com.nobodybuilds.listens.utils.ProviderText
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.nobodybuilds.listens.presentation.components.subcomps.PlayingEqIcon

@Composable
private fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    isPlaying: Boolean
) {
    if (song == null) return

    val albumShape = CircleShape

    // Helper state to stop rotation when paused, but we want it to pause in place?
    // Using infiniteTransition.animateFloat will reset on recomposition if spec changes or stops.
    // For a realistic vinyl pause, we need a manual Animatable that loops.
    // But for simplicity requested: "Animate the cover art to rotate... when music is playing".
    // If we just use conditional Modifier.graphicsLayer rotation, it might jump.
    // Let's use a simpler approach: if isPlaying, rotate.
    
    // Better approach for pausing rotation in place is non-trivial without a dedicated running time state.
    // Given the constraints, I will use a simple AnimatedVisibility or just let it reset, OR
    // use a monotonic clock if possible.
    // Let's stick to infinite transition for running, and maybe 0f for static?
    // Actually, user said "simulate a vinyl record". This implies continuous storage of rotation?
    // I'll try to implement continuous rotation.
    
    val currentRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Spin forever. 8s per revolution halves the effective per-second animation work
            // vs the original 4s cadence — visually still clearly a rotating "vinyl", but
            // drives fewer Compose invalidations during long listening sessions.
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        } else {
             currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = albumShape,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(albumShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false) // Allow shrinking if content is small
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.7f),
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )
    }
}

/** Asks whether to save the synced or the plain lyrics to a file. */
@Composable
internal fun LyricsSaveDialog(
    hasSynced: Boolean,
    hasPlain: Boolean,
    onDismiss: () -> Unit,
    onSave: (synced: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_lyrics_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.save_lyrics_dialog_message))
                Spacer(modifier = Modifier.height(16.dp))
                if (hasSynced) {
                    FilledTonalButton(
                        onClick = { onSave(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_synced_lyrics))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (hasPlain) {
                    OutlinedButton(
                        onClick = { onSave(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_plain_lyrics))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Track header, the synced / plain / loading lyrics, and the top and bottom fades. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsSheetContent(
    currentSong: Song?,
    lyrics: Lyrics?,
    isPlaying: Boolean,
    isLoadingLyrics: Boolean,
    showSyncedLyrics: Boolean?,
    syncedListState: LazyListState,
    staticListState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    previewSeekPositionMs: Long?,
    scaledTextStyle: TextStyle,
    lyricsTextStyle: TextStyle,
    onSyncedLineClick: (SyncedLine) -> Unit,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    resolvedAutoscrollSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean,
    animatedLyricsBlurEnabled: Boolean,
    animatedLyricsBlurStrength: Float,
    immersiveMode: Boolean,
    lyricsAlignment: String,
    showLyricsTranslation: Boolean,
    showLyricsRomanization: Boolean,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    backgroundColor: Color,
    onBackgroundColor: Color,
    containerColor: Color,
    accentColor: Color,
    lyricHighlightColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        // Track Info Header (Fixed at top)
        AnimatedContent(
            targetState = currentSong,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + 
                 scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                .togetherWith(fadeOut(animationSpec = tween(300)))
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(2f)
                .wrapContentWidth(),
            label = "headerAnimation"
        ) { song ->
            LyricsTrackInfo(
                song = song,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                    )
                    .background(
                        color = backgroundColor,
                        shape = CircleShape
                    )
                    .wrapContentWidth()
                    .animateContentSize(), // Animate width changes
                backgroundColor = backgroundColor, // Distinct solid background
                contentColor = onBackgroundColor,
                isPlaying = isPlaying
            )
        }

        when (showSyncedLyrics) {
            null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 110.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                ) {
                    item(key = "loader_or_empty") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingLyrics) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = context.resources.getString(R.string.loading_lyrics),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearWavyProgressIndicator(
                                        trackColor = accentColor.copy(alpha = 0.4f),
                                        color = accentColor,
                                        modifier = Modifier.width(100.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            true -> {
                lyrics?.synced?.let { synced ->
                    SyncedLyricsList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(top = 130.dp, bottom = 100.dp),
                        lines = synced,
                        listState = syncedListState,
                        playbackPositionFlow = playbackPositionFlow,
                        lyricsSyncOffset = lyricsSyncOffset,
                        positionOverrideMs = previewSeekPositionMs,
                        accentColor = lyricHighlightColor,
                        textStyle = scaledTextStyle,
                        onLineClick = onSyncedLineClick,
                        highlightZoneFraction = highlightZoneFraction,
                        highlightOffsetDp = highlightOffsetDp,
                        autoscrollAnimationSpec = resolvedAutoscrollSpec,
                        useAnimatedLyrics = useAnimatedLyrics,
                        animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                        animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                        immersiveMode = immersiveMode,
                        lyricsAlignment = lyricsAlignment,
                        showTranslation = showLyricsTranslation,
                        showRomanization = showLyricsRomanization,
                        footer = {
                            if (lyrics?.areFromRemote == true) {
                                item(key = "provider_text") {
                                    ProviderText(
                                        providerText = context.resources.getString(R.string.lyrics_provided_by),
                                        uri = context.resources.getString(R.string.lrclib_uri),
                                        textAlign = TextAlign.Center,
                                        accentColor = lyricHighlightColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            false -> {
                lyrics?.plain?.let { plain ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = staticListState,
                        contentPadding = PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            top = 130.dp,
                            bottom = 24.dp
                        )
                    ) {
                        itemsIndexed(
                            items = plain,
                            key = { index, line -> "$index-$line" }
                        ) { _, line ->
                            PlainLyricsLine(
                                line = line,
                                style = lyricsTextStyle,
                                lyricsAlignment = lyricsAlignment,
                                showTranslation = if (hasTranslatedLyrics) showLyricsTranslation else true,
                                showRomanization = if (hasRomanizedLyrics) showLyricsRomanization else true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Top Gradient for fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(containerColor, Color.Transparent)
                    )
                )
        )

        // Bottom Gradient for fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, containerColor)
                    )
                )
        )
    }
}
