package com.nobodybuilds.listens.presentation.covers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.health.LibraryHealthAnalyzer
import com.nobodybuilds.listens.presentation.nb.NbActionRow

/**
 * "Make a cover" row for the song options sheet. Shows only for a song on this device
 * that has no artwork; opens the cover studio full screen for that song.
 */
@Composable
fun CoverSongActions(song: Song) {
    if (!song.albumArtUriString.isNullOrBlank() || !LibraryHealthAnalyzer.isLocal(song)) return
    var open by rememberSaveable(song.id) { mutableStateOf(false) }
    NbActionRow(
        text = "Make a cover",
        subtitle = "Pattern artwork in Daylight colours",
        icon = rememberVectorPainter(Icons.Rounded.Palette),
        onClick = { open = true }
    )
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CoverStudioScreen(
                onBack = { open = false },
                songId = song.id,
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(key = "cover_studio_${song.id}")
            )
        }
    }
}
