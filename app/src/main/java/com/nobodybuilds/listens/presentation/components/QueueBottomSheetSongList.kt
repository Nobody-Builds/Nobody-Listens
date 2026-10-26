package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.subcomps.PlayingEqIcon
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import com.nobodybuilds.listens.presentation.components.scoped.QueueItemDismissGestureHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbType
import androidx.compose.foundation.lazy.LazyListState
import sh.calvin.reorderable.ReorderableLazyListState

@Composable
fun QueuePlaylistSongItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean? = null,
    isDragging: Boolean,
    onRemoveClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    isReorderModeEnabled: Boolean,
    onMoreOptionsClick: (song: Song) -> Unit,
    isDragHandleVisible: Boolean,
    isRemoveButtonVisible: Boolean,
    enableSwipeToDismiss: Boolean = false,
    swipeStateIdentity: Long = 0L,
    onDismissSong: () -> Unit = {},
    isFromPlaylist: Boolean
) {
    val colors = MaterialTheme.colorScheme

    val hapticView = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val dismissScope = rememberCoroutineScope()
    val dismissEnabled = enableSwipeToDismiss && !isDragging
    val density = LocalDensity.current

    val dismissOffsetAnimatable = remember(swipeStateIdentity) { Animatable(0f) }
    var itemWidthPx by remember { mutableStateOf(0f) }

    val dismissHandler = remember(swipeStateIdentity, dismissEnabled, itemWidthPx) {
        if (dismissEnabled && itemWidthPx > 0f) {
            QueueItemDismissGestureHandler(
                scope = dismissScope,
                density = density,
                hapticView = hapticView,
                appHapticsConfig = appHapticsConfig,
                offsetAnimatable = dismissOffsetAnimatable,
                itemWidthPx = itemWidthPx,
                onDismiss = onDismissSong
            )
        } else null
    }

    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val currentOffsetPx = dismissOffsetAnimatable.value
    val revealWidthPx = (-currentOffsetPx).coerceAtLeast(0f)
    val revealProgress = if (density.density > 0f) {
        (revealWidthPx / (56.dp.value * density.density)).coerceIn(0f, 1f)
    } else 0f

    val dismissBackgroundColor by animateColorAsState(
        targetValue = if (isSwipeTargeted) colors.errorContainer else colors.errorContainer.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor"
    )
    val dismissIconAlpha by animateFloatAsState(
        targetValue = revealProgress * if (isSwipeTargeted) 1f else 0.88f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconAlpha"
    )
    val dismissIconScale by animateFloatAsState(
        targetValue = if (isSwipeTargeted) 1.08f else 0.95f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconScale"
    )

    var surfaceHeightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val measuredWidth = coordinates.size.width.toFloat()
                if (measuredWidth != itemWidthPx) itemWidthPx = measuredWidth
            }
    ) {
        if (revealWidthPx > 0f && surfaceHeightPx > 0f) {
            val revealWidthDp = with(density) { revealWidthPx.toDp() }
            val surfaceHeightDp = with(density) { surfaceHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .height(surfaceHeightDp)
                    .width(revealWidthDp)
                    .clip(CircleShape)
                    .background(dismissBackgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = stringResource(R.string.presentation_batch_e_cd_dismiss_song),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            alpha = dismissIconAlpha
                            scaleX = dismissIconScale
                            scaleY = dismissIconScale
                        },
                    tint = colors.onErrorContainer
                )
            }
        }

        Row(
            modifier = Modifier
                .graphicsLayer { translationX = currentOffsetPx }
                .onGloballyPositioned { coordinates ->
                    val h = coordinates.size.height.toFloat()
                    if (h != surfaceHeightPx) surfaceHeightPx = h
                }
                .fillMaxWidth()
                .background(if (isDragging) colors.surfaceContainerLow else colors.surface)
                .then(
                    if (isDragging) Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    else Modifier
                )
                .clickable(
                    enabled = currentOffsetPx == 0f
                ) {
                    onClick()
                }
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dismissGestureModifier = if (dismissEnabled && dismissHandler != null) {
                Modifier.pointerInput(swipeStateIdentity, dismissHandler) {
                    detectHorizontalDragGestures(
                        onDragStart = { dismissHandler.onDragStart() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dismissHandler.onHorizontalDrag(dragAmount)
                        },
                        onDragEnd = { dismissHandler.onDragEnd() },
                        onDragCancel = { dismissHandler.onDragCancel() }
                    )
                }
            } else Modifier

            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(dismissGestureModifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NbArt(
                    model = song.albumArtUriString,
                    title = song.album.ifBlank { song.title },
                    size = 48.dp,
                    shape = RoundedCornerShape(10.dp),
                    contentDescription = stringResource(R.string.cd_album_art_for_title, song.title)
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (isCurrentSong) colors.tertiary else colors.onSurface,
                        style = NbType.title
                    )
                    Text(
                        song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = NbType.meta,
                        color = colors.onSurfaceVariant
                    )
                }

                if (isCurrentSong && isPlaying != null) {
                    PlayingEqIcon(
                        modifier = Modifier.size(width = 16.dp, height = 14.dp),
                        color = colors.tertiary,
                        isPlaying = isPlaying
                    )
                }
            }

            if (isFromPlaylist) {
                IconButton(
                    onClick = { onMoreOptionsClick(song) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(
                            R.string.presentation_batch_e_more_options_for_song,
                            song.title
                        ),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isRemoveButtonVisible && !enableSwipeToDismiss) {
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(R.drawable.rounded_close_24),
                        contentDescription = stringResource(R.string.presentation_batch_e_cd_remove_from_playlist),
                        tint = colors.error
                    )
                }
            }

            AnimatedVisibility(visible = isDragHandleVisible) {
                dragHandle()
            }
        }
    }
}

/** The reorderable queue list with its scroll bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueSheetSongList(
    listState: LazyListState,
    reorderableState: ReorderableLazyListState,
    listDragConnection: NestedScrollConnection,
    isReorderLocked: Boolean,
    hasReorderPreview: Boolean,
    displaySongCount: Int,
    activeKeys: List<Long>,
    activeOrder: List<Int>,
    activeSongSource: List<Song>,
    currentSongDisplayIndex: Int,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onDragHandleStarted: () -> Unit,
    onDragHandleStopped: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isReorderLocked) {
                        Modifier
                    } else {
                        Modifier.nestedScroll(listDragConnection)
                    }
                ),
            userScrollEnabled = !isReorderLocked,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
            )
        ) {
            item("queue_top_spacer") {
                Spacer(modifier = Modifier.height(2.dp))
            }

            items(
                count = displaySongCount,
                key = { index -> activeKeys[index] },
                contentType = { "queue_song" }
            ) { index ->
                if (index >= activeOrder.size || index >= activeKeys.size) return@items
                val queueIndex = activeOrder[index]
                if (queueIndex !in activeSongSource.indices) return@items
                val itemStableKey = activeKeys[index]
                val song = activeSongSource[queueIndex]
                val canReorder = index > currentSongDisplayIndex
                ReorderableItem(
                    state = reorderableState,
                    key = itemStableKey,
                    enabled = canReorder,
                    animateItemModifier = when {
                        isReorderLocked || hasReorderPreview -> Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        else -> Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 140),
                            fadeOutSpec = tween(durationMillis = 120),
                            placementSpec = tween(durationMillis = 180)
                        )
                    }
                ) { isDragging ->
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.015f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "scaleAnimation"
                    )

                    QueuePlaylistSongItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        onClick = { onPlaySong(song) },
                        song = song,
                        isCurrentSong = index == currentSongDisplayIndex,
                        isPlaying = isPlaying,
                        isDragging = isDragging,
                        onRemoveClick = { onRemoveSong(song.id) },
                        isReorderModeEnabled = false,
                        isDragHandleVisible = canReorder,
                        isRemoveButtonVisible = false,
                        enableSwipeToDismiss = canReorder,
                        swipeStateIdentity = itemStableKey,
                        onDismissSong = { onRemoveSong(song.id) },
                        isFromPlaylist = true,
                        onMoreOptionsClick = { onSongInfoClick(song) },
                        dragHandle = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .draggableHandle(
                                        onDragStarted = { onDragHandleStarted() },
                                        onDragStopped = { onDragHandleStopped() }
                                    )
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = stringResource(R.string.presentation_batch_b_reorder_song),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        ExpressiveScrollBar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    top = 24.dp,
                    end = 14.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp
                )
        )
    }
}
