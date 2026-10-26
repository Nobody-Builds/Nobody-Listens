package com.twilighty.lmusic.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.presentation.components.AutoScrollingText
import com.twilighty.lmusic.presentation.components.SmartImage
import com.twilighty.lmusic.presentation.components.subcomps.PlayingEqIcon
import com.twilighty.lmusic.presentation.components.player.AnimatedPlaybackControls
import com.twilighty.lmusic.presentation.viewmodel.PlayerUiState
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.viewmodel.PlaylistViewModel
import com.twilighty.lmusic.presentation.viewmodel.SettingsViewModel
import com.twilighty.lmusic.presentation.viewmodel.StablePlayerState
import com.twilighty.lmusic.presentation.utils.LocalAppHapticsConfig
import com.twilighty.lmusic.presentation.utils.performAppCompatHapticFeedback
import com.twilighty.lmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import com.twilighty.lmusic.presentation.components.scoped.QueueItemDismissGestureHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.RandomAccess
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCheckbox
import com.twilighty.lmusic.presentation.nb.NbChip
import com.twilighty.lmusic.presentation.nb.NbDialog
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbSheetHandle
import com.twilighty.lmusic.presentation.nb.NbTextField
import com.twilighty.lmusic.presentation.nb.NbToast
import com.twilighty.lmusic.presentation.nb.NbType
import com.twilighty.lmusic.presentation.nb.Nb

private data class QueueUndoBarProjection(
    val isVisible: Boolean = false,
    val removedSongTitle: String = ""
)

private fun PlayerUiState.toQueueUndoBarProjection(): QueueUndoBarProjection =
    QueueUndoBarProjection(
        isVisible = showQueueItemUndoBar,
        removedSongTitle = lastRemovedQueueSong?.title.orEmpty()
    )

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    queue: List<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    currentMediaItemIndex: Int = -1,
    repeatMode: Int,
    isShuffleOn: Boolean,
    onDismiss: () -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    activeTimerValueDisplay: androidx.compose.runtime.State<String?>,
    activeTimerDurationMinutes: androidx.compose.runtime.State<Int?>,
    playCount: androidx.compose.runtime.State<Float>,
    isEndOfTrackTimerActive: androidx.compose.runtime.State<Boolean>,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (count: Int) -> Unit,
    onRequestSaveAsPlaylist: (
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 10.dp,
    shape: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }

    val infrequentPlayerState by viewModel.stablePlayerState.collectAsStateWithLifecycle()

    val albumColorSchemePair by viewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val albumColorScheme = remember(albumColorSchemePair, isDark) {
        albumColorSchemePair?.let { pair -> if (isDark) pair.dark else pair.light }
    }

    val isPlaying = infrequentPlayerState.isPlaying

    // Use the real player index from MediaController if available to resolve duplicates.
    // Fall back to ID search only if index is invalid (-1).
    val currentSongIndex = remember(queue, currentSongId, currentMediaItemIndex) {
        if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == currentSongId) {
            currentMediaItemIndex
        } else {
            queue.indexOfFirst { it.id == currentSongId }
        }
    }

    // Read show queue history preference
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val showQueueHistory = settingsState.showQueueHistory

    // Offset to convert display indices to queue indices when history is hidden.
    val queueIndexOffset = if (showQueueHistory || currentSongIndex < 0) 0 else currentSongIndex

    // Show full queue including history (Apple Music style) OR only from current song.
    // Use subList when possible to avoid copying large queues.
    val displaySongs = remember(queue, queueIndexOffset) {
        if (queueIndexOffset == 0) {
            queue
        } else if (queue is RandomAccess) {
            queue.subList(queueIndexOffset, queue.size)
        } else {
            queue.drop(queueIndexOffset)
        }
    }

    // Calculate the display index of the current song (depends on whether we show history or not).
    val currentSongDisplayIndex = remember(currentSongIndex, queueIndexOffset) {
        if (currentSongIndex < 0) -1 else currentSongIndex - queueIndexOffset
    }

    val listState = rememberLazyListState()
    val displaySongCount = displaySongs.size

    // Local order used only while previewing a drag reorder.
    var reorderPreviewOrder by remember { mutableStateOf<List<Int>?>(null) }
    var reorderPreviewKeys by remember { mutableStateOf<List<Long>?>(null) }
    var reorderPreviewBaseQueue by remember { mutableStateOf<List<Song>?>(null) }
    var pendingReorderExpectedIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingReorderGraceUpdates by remember { mutableIntStateOf(0) }

    // Stable keys for queue rows to prevent state recycling glitches on remove/reorder.
    var committedDisplaySongIds by remember { mutableStateOf(displaySongs.map { it.id }) }
    var committedDisplayKeys by remember { mutableStateOf(List(displaySongCount) { it.toLong() }) }
    var nextStableQueueItemKey by remember { mutableLongStateOf(displaySongCount.toLong()) }

    // Track queue order by content (not list identity) to avoid clearing preview
    // when upstream emits equivalent list instances during drag.
    var reorderPreviewQueueSignature by remember { mutableStateOf<Int?>(null) }
    val displaySongsSignature = remember(displaySongs, queueIndexOffset) {
        (queueIndexOffset * 31) + System.identityHashCode(displaySongs)
    }
    val defaultDisplayOrder = remember(displaySongCount, queueIndexOffset) {
        List(displaySongCount) { queueIndexOffset + it }
    }
    val defaultDisplayKeys = remember(displaySongCount, queueIndexOffset) {
        List(displaySongCount) { (queueIndexOffset + it).toLong() }
    }
    val activeOrder = reorderPreviewOrder ?: defaultDisplayOrder
    val activeKeys = reorderPreviewKeys
        ?: committedDisplayKeys.takeIf { it.size == displaySongCount }
        ?: defaultDisplayKeys
    val activeSongSource = reorderPreviewBaseQueue ?: queue
    val activeKeyToLocalIndex = remember(activeKeys) {
        HashMap<Long, Int>(activeKeys.size).apply {
            activeKeys.forEachIndexed { index, stableKey ->
                put(stableKey, index)
            }
        }
    }

    fun remapCommittedKeysForDisplay(newSongs: List<Song>) {
        // Fast path: common queue-skip case where display list is just a suffix of previous display list.
        if (committedDisplaySongIds.isNotEmpty() && newSongs.isNotEmpty()) {
            val firstNewId = newSongs.first().id
            val startIndex = committedDisplaySongIds.indexOf(firstNewId)
            if (startIndex >= 0 && startIndex + newSongs.size <= committedDisplaySongIds.size) {
                var suffixMatches = true
                for (i in newSongs.indices) {
                    if (committedDisplaySongIds[startIndex + i] != newSongs[i].id) {
                        suffixMatches = false
                        break
                    }
                }
                if (suffixMatches) {
                    committedDisplaySongIds = committedDisplaySongIds.subList(startIndex, startIndex + newSongs.size).toList()
                    committedDisplayKeys = committedDisplayKeys.subList(startIndex, startIndex + newSongs.size).toList()
                    return
                }
            }
        }

        val reusableKeysBySongId = mutableMapOf<String, ArrayDeque<Long>>()
        committedDisplaySongIds.forEachIndexed { index, songId ->
            val key = committedDisplayKeys.getOrNull(index) ?: return@forEachIndexed
            reusableKeysBySongId.getOrPut(songId) { ArrayDeque() }.addLast(key)
        }

        var nextKey = nextStableQueueItemKey
        val newKeys = ArrayList<Long>(newSongs.size)
        newSongs.forEach { song ->
            val bucket = reusableKeysBySongId[song.id]
            val reusedKey = if (bucket != null && bucket.isNotEmpty()) bucket.removeFirst() else null
            if (reusedKey != null) {
                newKeys.add(reusedKey)
            } else {
                newKeys.add(nextKey)
                nextKey++
            }
        }

        committedDisplaySongIds = newSongs.map { it.id }
        committedDisplayKeys = newKeys
        nextStableQueueItemKey = nextKey
    }

    // Reset local reorder preview only when the queue truly changes to something new.
    LaunchedEffect(displaySongsSignature, queueIndexOffset) {
        val expectedIds = pendingReorderExpectedIds

        if (expectedIds != null) {
            val currentDisplayIds = displaySongs.map { it.id }
            if (currentDisplayIds == expectedIds) {
                reorderPreviewKeys
                    ?.takeIf { it.size == displaySongs.size }
                    ?.let { previewKeys ->
                        committedDisplaySongIds = currentDisplayIds
                        committedDisplayKeys = previewKeys
                    }
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                remapCommittedKeysForDisplay(displaySongs)
                reorderPreviewQueueSignature = displaySongsSignature
                return@LaunchedEffect
            }

            if (reorderPreviewOrder != null && pendingReorderGraceUpdates > 0) {
                pendingReorderGraceUpdates -= 1
                reorderPreviewQueueSignature = displaySongsSignature
                return@LaunchedEffect
            }

            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
        }

        if (reorderPreviewQueueSignature != null && reorderPreviewQueueSignature != displaySongsSignature) {
            // Queue data changed from external source - safe to clear preview
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
        }
        remapCommittedKeysForDisplay(displaySongs)
        reorderPreviewQueueSignature = displaySongsSignature
    }

    // --- REORDER STATE ---
    val lmusicHapticsEngine = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    val updatedReorderHandleInUse by rememberUpdatedState(reorderHandleInUse)

    fun mapKeyToLocalIndex(key: Any?, keyToLocalIndex: Map<Long, Int>): Int? {
        val stableKey = key as? Long ?: return null
        return keyToLocalIndex[stableKey]
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (reorderPreviewOrder == null) {
                reorderPreviewBaseQueue = queue
            }
            val currentOrder = activeOrder
            val currentKeys = activeKeys

            val fromLocalIndex = mapKeyToLocalIndex(from.key, activeKeyToLocalIndex) ?: return@rememberReorderableLazyListState
            val toLocalIndex = mapKeyToLocalIndex(to.key, activeKeyToLocalIndex) ?: return@rememberReorderableLazyListState
            if (fromLocalIndex == toLocalIndex) return@rememberReorderableLazyListState
            lmusicHapticsEngine.dragSlot()

            reorderPreviewOrder = currentOrder.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            reorderPreviewKeys = currentKeys.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromLocalIndex
            }
            lastMovedTo = toLocalIndex
        },
    )
    val isReordering by remember {
        derivedStateOf { reorderableState.isAnyItemDragging }
    }
    val updatedIsReordering by rememberUpdatedState(isReordering)
    // ----------------------

    // Only jump to current song when the actual current song changes (e.g. track skip).
    // This prevents annoying jumps when adding/removing other items in the queue.
    var isFirstScrollByCurrentSongId by remember(currentSongId) { mutableStateOf(true) }

    LaunchedEffect(currentSongId) {
        if (!isReordering && !reorderHandleInUse && currentSongDisplayIndex >= 0 && currentSongDisplayIndex < displaySongCount) {
            val firstVisible = listState.firstVisibleItemIndex
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            if (currentSongDisplayIndex !in firstVisible..lastVisible) {
                if (isFirstScrollByCurrentSongId || Math.abs(currentSongDisplayIndex - firstVisible) > 20) {
                    listState.scrollToItem(currentSongDisplayIndex)
                } else {
                    listState.animateScrollToItem(currentSongDisplayIndex)
                }
            }
            isFirstScrollByCurrentSongId = false
        }
    }

    val canDragSheetFromList by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val updatedCanDragSheet by rememberUpdatedState(canDragSheetFromList)
    var draggingSheetFromList by remember { mutableStateOf(false) }
    var listDragAccumulated by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val updatedOnQueueDragStart by rememberUpdatedState(onQueueDragStart)
    val updatedOnQueueDrag by rememberUpdatedState(onQueueDrag)
    val updatedOnQueueRelease by rememberUpdatedState(onQueueRelease)

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val fromIndex = lastMovedFrom
            val toIndex = lastMovedTo

            lastMovedFrom = null
            lastMovedTo = null

            if (fromIndex != null && toIndex != null) {
                // Convert display indices to queue indices by adding the offset
                val fromQueueIndex = fromIndex + queueIndexOffset
                val toQueueIndex = toIndex + queueIndexOffset

                val fromWithinQueue = fromQueueIndex in queue.indices
                val toWithinQueue = toQueueIndex in queue.indices

                if (fromWithinQueue && toWithinQueue && fromQueueIndex != toQueueIndex) {
                    val previewBase = reorderPreviewBaseQueue ?: queue
                    val expectedIds = reorderPreviewOrder
                        ?.mapNotNull { previewBase.getOrNull(it)?.id }
                        ?.takeIf { it.size == displaySongCount }
                    pendingReorderExpectedIds = expectedIds
                    pendingReorderGraceUpdates = if (expectedIds != null) 6 else 0
                    // Keep reorderPreviewOrder alive so items don't snap back
                    // while we wait for the new queue data to propagate.
                    onReorder(fromQueueIndex, toQueueIndex)
                    return@LaunchedEffect
                }
            }

            // Only clear preview if no valid reorder was dispatched
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
        }
    }

    val useLightweightQueueListShape by remember {
        derivedStateOf {
            listState.isScrollInProgress ||
                draggingSheetFromList ||
                isReordering ||
                reorderHandleInUse
        }
    }
    val queueListShape = remember(useLightweightQueueListShape) {
        if (useLightweightQueueListShape) {
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        } else {
            AbsoluteSmoothCornerShape(
                cornerRadiusTR = 26.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = 26.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 60
            )
        }
    }

    fun finalizeListDrag(velocity: Float = 0f) {
        if (draggingSheetFromList) {
            updatedOnQueueRelease(listDragAccumulated, velocity)
            draggingSheetFromList = false
            listDragAccumulated = 0f
        }
    }

    val listDragConnection = remember(updatedCanDragSheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && available.y < 0f) {
                    finalizeListDrag()
                    return Offset.Zero
                }

                if (draggingSheetFromList) {
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return available
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList && available.y < 0f) {
                    finalizeListDrag(available.y)
                    return Velocity.Zero
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    updatedOnQueueRelease(listDragAccumulated, available.y)
                    draggingSheetFromList = false
                    listDragAccumulated = 0f
                    return available
                }
                return Velocity.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && source == NestedScrollSource.UserInput && available.y != 0f) {
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList) return available.also { finalizeListDrag(available.y) }
                return Velocity.Zero
            }
        }
    }

    val directSheetDragModifier =
        if (updatedIsReordering || updatedReorderHandleInUse) {
            Modifier
        } else {
            Modifier.pointerInput(updatedOnQueueDragStart, updatedOnQueueDrag, updatedOnQueueRelease) {
                var dragTotal = 0f
                val dragVelocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        dragVelocityTracker.resetTracking()
                        updatedOnQueueDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                        updatedOnQueueDrag(dragAmount)
                    },
                    onDragEnd = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    },
                    onDragCancel = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    }
                )
            }
        }

    fun requestSaveAsPlaylist() {
        val res = context.resources
        val defaultName = if (currentQueueSourceName.isNotBlank()) {
            res.getString(
                R.string.presentation_batch_e_queue_named_suffix,
                currentQueueSourceName
            )
        } else {
            res.getString(R.string.presentation_batch_e_queue_current)
        }
        onRequestSaveAsPlaylist(
            queue,
            defaultName
        ) { name, selectedIds ->
            val orderedSelection = queue
                .filter { selectedIds.contains(it.id) }
                .map { it.id }
            if (orderedSelection.isNotEmpty()) {
                playlistViewModel.createPlaylist(
                    name = name,
                    songIds = orderedSelection,
                    isQueueGenerated = true
                )
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = 0.dp,
        color = colors.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column {
                val headerTopPadding = WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + 10.dp

                QueueHeaderSection(
                    isPlaying = isPlaying,
                    queueSourceName = currentQueueSourceName,
                    queueCount = displaySongCount,
                    topPadding = headerTopPadding,
                    onPrevious = { viewModel.previousSong() },
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.nextSong() },
                    onClearClick = { showClearQueueDialog = true },
                    onSaveClick = { requestSaveAsPlaylist() },
                    colorScheme = albumColorScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(directSheetDragModifier)
                )

                if (displaySongCount == 0) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.presentation_batch_e_queue_empty),
                            style = NbType.body,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isReordering || reorderHandleInUse) {
                                        Modifier
                                    } else {
                                        Modifier.nestedScroll(listDragConnection)
                                    }
                                ),
                            userScrollEnabled = !(isReordering || reorderHandleInUse),
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
                                        isReordering || reorderHandleInUse || reorderPreviewOrder != null -> Modifier.animateItem(
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
                                                        onDragStarted = {
                                                            draggingSheetFromList = false
                                                            reorderHandleInUse = true
                                                            lmusicHapticsEngine.dragLift()
                                                        },
                                                        onDragStopped = {
                                                            reorderHandleInUse = false
                                                            lmusicHapticsEngine.dragDrop()
                                                        }
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
            }

            val isTimerActiveDerived = remember {
                derivedStateOf { activeTimerValueDisplay.value != null }
            }
            QueueControlsToolbar(
                isShuffleOn = isShuffleOn,
                repeatMode = repeatMode,
                isTimerActive = isTimerActiveDerived,
                timerLabel = activeTimerValueDisplay.value,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onTimerClick = { showTimerOptions = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(directSheetDragModifier)
            )

            val queueUndoBarState by remember(viewModel) {
                viewModel.playerUiState
                    .map { it.toQueueUndoBarProjection() }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = QueueUndoBarProjection())
            AnimatedVisibility(
                visible = queueUndoBarState.isVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                    )
                    .zIndex(50f),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                NbToast(
                    text = "${queueUndoBarState.removedSongTitle} ${stringResource(R.string.presentation_batch_e_removed)}",
                    actionLabel = stringResource(R.string.action_undo),
                    onAction = { viewModel.undoRemoveSongFromQueue() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (showTimerOptions) {
            TimerOptionsBottomSheet(
                onPlayCounter = onPlayCounter,
                activeTimerValueDisplay = activeTimerValueDisplay.value,
                activeTimerDurationMinutes = activeTimerDurationMinutes.value,
                playCount = playCount.value,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive.value,
                onDismiss = { showTimerOptions = false },
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelCountedPlay = onCancelCountedPlay,
                onCancelTimer = onCancelTimer
            )
        }

        if (showClearQueueDialog) {
            NbDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = stringResource(R.string.presentation_batch_e_clear_queue_dialog_title),
                body = stringResource(R.string.presentation_batch_e_clear_queue_dialog_message),
                actions = {
                    NbButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showClearQueueDialog = false },
                        style = NbButtonStyle.Secondary
                    )
                    NbButton(
                        text = stringResource(R.string.presentation_batch_b_clear),
                        onClick = {
                            onClearQueue()
                            showClearQueueDialog = false
                        },
                        style = NbButtonStyle.Danger
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueToolbarMenuButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current

    Surface(
        modifier = modifier
            .widthIn(min = 184.dp, max = 260.dp)
            .heightIn(min = 48.dp)
            .wrapContentWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.tap()
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 8.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun QueueHeaderSection(
    isPlaying: Boolean,
    queueSourceName: String,
    queueCount: Int,
    topPadding: Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit,
    colorScheme: ColorScheme? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding - 6.dp, bottom = 8.dp)
    ) {
        NbSheetHandle(Modifier.align(Alignment.CenterHorizontally))
        QueueHeader(
            queueSourceName = queueSourceName,
            queueCount = queueCount,
            onClearClick = onClearClick,
            onSaveClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp)
        )
    }
}

@Composable
private fun QueueHeader(
    queueSourceName: String,
    queueCount: Int,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Up next",
                style = NbType.h2,
                color = MaterialTheme.colorScheme.onSurface
            )
            val countText = when {
                queueCount <= 0 -> stringResource(R.string.presentation_batch_e_queue_subtitle_empty)
                else -> pluralStringResource(
                    R.plurals.presentation_batch_e_queue_tracks_lined_up,
                    queueCount,
                    queueCount
                )
            }
            val source = queueSourceName.ifBlank { stringResource(R.string.presentation_batch_e_queue_source_fallback) }
            Text(
                text = "$countText · $source",
                style = NbType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (queueCount > 0) {
            NbButton(
                text = "Save",
                onClick = onSaveClick,
                style = NbButtonStyle.Quiet,
                size = NbButtonSize.Small
            )
            NbButton(
                text = stringResource(R.string.presentation_batch_b_clear),
                onClick = onClearClick,
                style = NbButtonStyle.Quiet,
                size = NbButtonSize.Small
            )
        }
    }
}

@Composable
private fun QueueControlsToolbar(
    isShuffleOn: Boolean,
    repeatMode: Int,
    isTimerActive: androidx.compose.runtime.State<Boolean>,
    timerLabel: String?,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        NbDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NbChip(
                text = stringResource(R.string.shortcut_shuffle_short),
                selected = isShuffleOn,
                onClick = onToggleShuffle,
                icon = rememberVectorPainter(Icons.Rounded.Shuffle)
            )
            NbChip(
                text = if (repeatMode == Player.REPEAT_MODE_ONE) "Repeat one" else "Repeat",
                selected = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onToggleRepeat,
                icon = rememberVectorPainter(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                )
            )
            Spacer(Modifier.weight(1f))
            NbChip(
                text = if (isTimerActive.value) timerLabel ?: "Timer" else "Timer",
                selected = isTimerActive.value,
                onClick = onTimerClick,
                icon = rememberVectorPainter(Icons.Rounded.Timer)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveQueueAsPlaylistSheet(
    songs: List<Song>,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val animatedAlbumCornerRadius = 60.dp
    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    var playlistName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultName, selection = TextRange(defaultName.length)))
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedSongIds = remember(songs) {
        mutableStateMapOf<String, Boolean>().apply {
            songs.forEach { put(it.id, true) }
        }
    }

    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }
    }

    val hasSelection by remember {
        derivedStateOf { selectedSongIds.any { it.value } }
    }
    val allSelected by remember {
        derivedStateOf { selectedSongIds.isNotEmpty() && selectedSongIds.all { it.value } }
    }

    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    BackHandler(onBack = { onDismiss() })

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { onDismiss() }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = stringResource(R.string.presentation_batch_e_save_as_playlist_sheet_title),
                                style = NbType.h2,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            NbButton(
                                text = if (allSelected) {
                                    stringResource(R.string.presentation_batch_e_deselect_all)
                                } else {
                                    stringResource(R.string.presentation_batch_b_select_all)
                                },
                                onClick = {
                                    val target = !allSelected
                                    selectedSongIds.keys.forEach { selectedSongIds[it] = target }
                                },
                                style = NbButtonStyle.Secondary,
                                size = NbButtonSize.Small,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NbTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = stringResource(R.string.presentation_batch_e_playlist_name_label),
                                singleLine = true,
                                fieldModifier = Modifier.focusRequester(focusRequester)
                            )
                            NbTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = stringResource(R.string.presentation_batch_e_search_songs_to_include_placeholder),
                                singleLine = true,
                                leading = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailing = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear_search),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                        NbDivider()
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.ime)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NbDivider()
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.presentation_batch_e_n_songs_selected,
                                        selectedSongIds.count { it.value },
                                        selectedSongIds.count { it.value }
                                    ),
                                    style = NbType.label,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (playlistName.text.isNotBlank()) {
                                        stringResource(
                                            R.string.presentation_batch_e_save_as_format,
                                            playlistName.text
                                        )
                                    } else {
                                        stringResource(R.string.presentation_batch_e_enter_playlist_name)
                                    },
                                    style = NbType.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            NbButton(
                                text = stringResource(R.string.action_save),
                                onClick = {
                                    if (hasSelection) {
                                        val finalName =
                                            playlistName.text.ifBlank { defaultName }
                                        val chosenIds = selectedSongIds
                                            .filterValues { it }
                                            .keys
                                        onConfirm(finalName, chosenIds)
                                        onDismiss()
                                    }
                                },
                                enabled = hasSelection,
                                size = NbButtonSize.Large,
                                icon = rememberVectorPainter(Icons.Rounded.Check)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.presentation_batch_e_no_songs_match_query,
                                        searchQuery
                                    ),
                                    style = NbType.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredSongs, key = { it.id }) { song ->
                            val checked = selectedSongIds[song.id] ?: false
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedSongIds[song.id] = !checked
                                    }
                                    .heightIn(min = 64.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NbCheckbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedSongIds[song.id] = isChecked
                                    }
                                )
                                NbArt(
                                    model = song.albumArtUriString,
                                    title = song.album.ifBlank { song.title },
                                    size = 44.dp,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, style = NbType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.displayArtist,
                                        style = NbType.meta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
