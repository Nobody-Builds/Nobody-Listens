package com.nobodybuilds.listens.presentation.smart

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.smart.SongSmartTags
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbActionRow
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbType
import kotlinx.coroutines.launch

/**
 * Smart row for the song options sheet: "Play similar" (song radio) with the song's mood/genre
 * tags as subtitle. Renders nothing unless smart features are on and the song has been analysed.
 */
@Composable
fun SmartSongActions(
    song: Song,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartSongViewModel = hiltViewModel(),
) {
    val enabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val tags by produceState<SongSmartTags?>(initialValue = null, song.id, enabled) {
        value = if (enabled) viewModel.tagsFor(song.id) else null
    }
    val current = tags ?: return
    if (!enabled) return

    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val scope = rememberCoroutineScope()
    var isLoading by remember(song.id) { mutableStateOf(false) }

    if (activity == null) return
    val playerViewModel: PlayerViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val tagLine = (current.moods + current.genres).joinToString(" · ").ifBlank { null }
    NbActionRow(
        text = if (isLoading) "Finding similar songs…" else "Play similar",
        subtitle = tagLine,
        icon = rememberVectorPainter(Icons.Rounded.Radio),
        enabled = !isLoading,
        modifier = modifier,
        trailing = { NbStatusChip(text = "Smart", status = NbStatus.Accent) },
        onClick = {
            scope.launch {
                isLoading = true
                val radio = try {
                    viewModel.radioFor(song)
                } finally {
                    isLoading = false
                }
                if (radio.size <= 1) {
                    Toast.makeText(context, "Not enough analysed songs yet", Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.playSongs(radio, song, "Song radio")
                    onDismiss()
                }
            }
        }
    )
}

/** Small read-only pills for mood and genre tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartTagChips(tags: SongSmartTags, modifier: Modifier = Modifier) {
    val all = tags.moods.map { it to true } + tags.genres.map { it to false }
    if (all.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        all.forEach { (label, isMood) ->
            Text(
                label,
                style = NbType.caption,
                color = if (isMood) Nb.accentText else Nb.textSecondary,
                modifier = Modifier
                    .clip(NbShapes.pill)
                    .background(if (isMood) Nb.accentSoft else Nb.canvas)
                    .then(if (isMood) Modifier else Modifier.border(1.dp, Nb.border, NbShapes.pill))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
