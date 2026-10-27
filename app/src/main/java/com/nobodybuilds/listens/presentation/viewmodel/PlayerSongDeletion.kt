package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes song files from the device for PlayerViewModel (one song or a selection), through
 * the MediaStore delete request on Android 11+ or a confirmation dialog, and drops them from
 * the queue and library.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
internal class PlayerSongDeletion(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val playbackStateHolder: PlaybackStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val songRemovalStateHolder: SongRemovalStateHolder,
    private val multiSelectionStateHolder: MultiSelectionStateHolder,
    private val favoritesActions: PlayerFavoritesActions
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val _toastEvents get() = shared.toastEvents
    private val _deletePermissionRequest get() = shared.deletePermissionRequest
    private val mediaController: MediaController? get() = shared.mediaController

    private var pendingDeleteSong: Song? = null
    private var pendingDeleteCallback: ((Boolean) -> Unit)? = null

    /**
     * Deletes all selected songs from device with confirmation.
     * Shows a single confirmation dialog for all songs.
     */
    private var pendingBatchDeleteSongs: List<Song>? = null
    private var pendingBatchDeleteSkippedCount: Int = 0
    private var pendingBatchDeleteOnComplete: (() -> Unit)? = null

    fun deleteSelectedFromDevice(activity: Activity, songs: List<Song>, onComplete: () -> Unit) {
        scope.launch {
            // Filter out currently playing song
            val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
            val deletableSongs = songs.filter { it.id != currentSongId }

            if (deletableSongs.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.player_cannot_delete_currently_playing))
                return@launch
            }

            val skippedCount = songs.size - deletableSongs.size

            // On Android 11+, use system batch delete dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = deletableSongs.mapNotNull { song ->
                    song.id.toLongOrNull()?.let { id ->
                        com.nobodybuilds.listens.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.nobodybuilds.listens.utils.MediaStorePermissionHelper
                        .createDeleteRequestIntentSender(activity, uris)
                    if (intentSender != null) {
                        pendingBatchDeleteSongs = deletableSongs
                        pendingBatchDeleteSkippedCount = skippedCount
                        pendingBatchDeleteOnComplete = onComplete
                        _deletePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            // Fallback for older Android or non-MediaStore songs
            val confirmed = showMultiDeleteConfirmation(activity, deletableSongs.size)
            if (!confirmed) {
                onComplete()
                return@launch
            }

            var successCount = 0
            deletableSongs.forEach { song ->
                val success = metadataEditStateHolder.deleteSong(song)
                if (success) {
                    removeFromMediaControllerQueue(song.id)
                    removeSong(song)
                    successCount++
                }
            }

            when {
                successCount == deletableSongs.size && skippedCount == 0 ->
                    _toastEvents.emit(
                        context.resources.getQuantityString(R.plurals.n_files_deleted, successCount, successCount),
                    )
                successCount == deletableSongs.size && skippedCount > 0 ->
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_batch_delete_files_deleted_skipped_format,
                            successCount,
                            skippedCount,
                        ),
                    )
                successCount > 0 ->
                    _toastEvents.emit(
                        context.getString(
                            R.string.player_batch_delete_partial_format,
                            successCount,
                            deletableSongs.size,
                        ),
                    )
                else ->
                    _toastEvents.emit(context.getString(R.string.player_delete_files_failed))
            }

            multiSelectionStateHolder.clearSelection()
            onComplete()
        }
    }

    private suspend fun showMultiDeleteConfirmation(activity: Activity, count: Int): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(
                        context.resources.getQuantityString(
                            R.plurals.delete_songs_confirmation_title,
                            count,
                            count,
                        ),
                    )
                    .setMessage(context.getString(R.string.delete_songs_permanent_message))
                    .setPositiveButton(context.getString(R.string.delete_action)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(context.getString(R.string.cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}){
        scope.launch {
            // Failsafe: Prevent deleting the currently playing song
            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                _toastEvents.emit(context.getString(R.string.player_cannot_delete_currently_playing))
                onResult(false)
                return@launch
            }

            // On Android 11+, use the system delete confirmation dialog via MediaStore.createDeleteRequest()
            // which both confirms AND handles deletion in one step (no MANAGE_EXTERNAL_STORAGE needed).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val songLongId = song.id.toLongOrNull()
                val intentSender = if (songLongId != null && songLongId > 0) {
                    com.nobodybuilds.listens.utils.MediaStorePermissionHelper
                        .createDeleteRequestForSong(activity, songLongId)
                } else null
                if (intentSender != null) {
                    pendingDeleteSong = song
                    pendingDeleteCallback = onResult
                    _deletePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            // Fallback for older Android or files not in MediaStore
            val userConfirmed = songRemovalStateHolder.showDeleteConfirmation(activity, song)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }

            val success = songRemovalStateHolder.deleteSongFile(song)
            if (success) {
                _toastEvents.emit(context.getString(R.string.player_file_deleted))
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                onResult(true)
            } else {
                _toastEvents.emit(context.getString(R.string.player_delete_file_not_found))
                onResult(false)
            }
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean) {
        // Handle batch delete
        val batchSongs = pendingBatchDeleteSongs
        if (batchSongs != null) {
            val skippedCount = pendingBatchDeleteSkippedCount
            val onComplete = pendingBatchDeleteOnComplete
            pendingBatchDeleteSongs = null
            pendingBatchDeleteSkippedCount = 0
            pendingBatchDeleteOnComplete = null
            scope.launch {
                if (granted) {
                    // System already deleted the files — clean up library
                    batchSongs.forEach { song ->
                        removeFromMediaControllerQueue(song.id)
                        removeSong(song)
                    }
                    val count = batchSongs.size
                    if (skippedCount > 0) {
                        _toastEvents.emit(
                            context.getString(
                                R.string.player_batch_delete_files_deleted_skipped_format,
                                count,
                                skippedCount,
                            ),
                        )
                    } else {
                        _toastEvents.emit(
                            context.resources.getQuantityString(R.plurals.n_files_deleted, count, count),
                        )
                    }
                } else {
                    _toastEvents.emit(context.getString(R.string.player_deletion_cancelled))
                }
                multiSelectionStateHolder.clearSelection()
                onComplete?.invoke()
            }
            return
        }

        // Handle single delete
        val song = pendingDeleteSong ?: return
        val callback = pendingDeleteCallback
        pendingDeleteSong = null
        pendingDeleteCallback = null
        scope.launch {
            if (granted) {
                // The system already deleted the file — just clean up the library
                _toastEvents.emit(context.getString(R.string.player_file_deleted))
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    suspend fun removeSong(song: Song) {
        favoritesActions.toggleFavoriteSpecificSong(song, true)
        playbackStateHolder.setCurrentPosition(0L)
        _playerUiState.update { currentState ->
            currentState.copy(
                currentPlaybackQueue = currentState.currentPlaybackQueue.removeSongById(song.id),
                currentQueueSourceName = ""
            )
        }
        _isSheetVisible.value = false
        songRemovalStateHolder.removeSongFromLibrary(song)
    }

    private fun removeFromMediaControllerQueue(songId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == songId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }
}
