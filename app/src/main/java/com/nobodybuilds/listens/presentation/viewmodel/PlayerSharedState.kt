package com.nobodybuilds.listens.presentation.viewmodel

import android.content.IntentSender
import androidx.media3.session.MediaController
import com.nobodybuilds.listens.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Mutable state that PlayerViewModel shares with the collaborators it was split into.
 * One instance per ViewModel; [scope] is the ViewModel's viewModelScope.
 */
internal class PlayerSharedState(val scope: CoroutineScope) {

    val playerUiState = MutableStateFlow(PlayerUiState())

    // Dedicated queue flow so the player sheet's MiniPlayer branch does not
    // recompose whenever the queue changes. Consumers that actually need the
    // queue (FullPlayer carousel, queue sheet) collect this narrower flow
    // directly, keeping the unrelated subtree stable.
    val queueFlow: StateFlow<ImmutableList<Song>> = playerUiState
        .map { it.currentPlaybackQueue }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = persistentListOf()
        )

    val sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val isSheetVisible = MutableStateFlow(false)
    val selectedSongForInfo = MutableStateFlow<Song?>(null)
    val trackVolume = MutableStateFlow(1.0f)

    val showNoInternetDialog = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Toast Events
    val toastEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // MediaStore write-permission request (needed for metadata editing without MANAGE_EXTERNAL_STORAGE)
    val writePermissionRequest = MutableSharedFlow<IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // MediaStore delete-permission request (for deletion without MANAGE_EXTERNAL_STORAGE)
    val deletePermissionRequest = MutableSharedFlow<IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var mediaController: MediaController? = null
    var pendingPlaybackAction: (() -> Unit)? = null
    var transitionSchedulerJob: Job? = null

    fun sendToast(message: String) {
        scope.launch {
            toastEvents.emit(message)
        }
    }
}
