package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel
import com.nobodybuilds.listens.presentation.utils.LocalAppHapticsConfig
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.map
import java.util.RandomAccess
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.nb.NbType

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
    val nobodyListensHapticsEngine = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
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
            nobodyListensHapticsEngine.dragSlot()

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
            Modifier.queueSheetDirectDrag(updatedOnQueueDragStart, updatedOnQueueDrag, updatedOnQueueRelease)
        }

    fun requestSaveAsPlaylist() {
        requestQueueSaveAsPlaylist(
            context = context,
            queue = queue,
            currentQueueSourceName = currentQueueSourceName,
            playlistViewModel = playlistViewModel,
            onRequestSaveAsPlaylist = onRequestSaveAsPlaylist
        )
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
                    QueueSheetSongList(
                        listState = listState,
                        reorderableState = reorderableState,
                        listDragConnection = listDragConnection,
                        isReorderLocked = isReordering || reorderHandleInUse,
                        hasReorderPreview = reorderPreviewOrder != null,
                        displaySongCount = displaySongCount,
                        activeKeys = activeKeys,
                        activeOrder = activeOrder,
                        activeSongSource = activeSongSource,
                        currentSongDisplayIndex = currentSongDisplayIndex,
                        isPlaying = isPlaying,
                        onPlaySong = onPlaySong,
                        onRemoveSong = onRemoveSong,
                        onSongInfoClick = onSongInfoClick,
                        onDragHandleStarted = {
                            draggingSheetFromList = false
                            reorderHandleInUse = true
                            nobodyListensHapticsEngine.dragLift()
                        },
                        onDragHandleStopped = {
                            reorderHandleInUse = false
                            nobodyListensHapticsEngine.dragDrop()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
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

            QueueSheetUndoBar(
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                    )
                    .zIndex(50f)
            )
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
            QueueClearQueueDialog(
                onDismiss = { showClearQueueDialog = false },
                onConfirm = {
                    onClearQueue()
                    showClearQueueDialog = false
                }
            )
        }
    }
}
