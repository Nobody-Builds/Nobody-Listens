package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.nobodybuilds.listens.data.model.Song
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.data.media.CoverArtUpdate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSongSheet(
    visible: Boolean,
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200))
            ) {
                EditSongContent(
                    song = song,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}
