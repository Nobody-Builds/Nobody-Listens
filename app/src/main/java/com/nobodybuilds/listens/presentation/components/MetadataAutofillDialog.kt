package com.nobodybuilds.listens.presentation.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.autofill.AutofillCandidate
import com.nobodybuilds.listens.data.autofill.AutofillField
import com.nobodybuilds.listens.data.autofill.CoverArt
import com.nobodybuilds.listens.data.autofill.MetadataFields
import com.nobodybuilds.listens.data.autofill.MusicBrainzAutofill
import com.nobodybuilds.listens.data.autofill.OfflineMetadataGuesser
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCallout
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbTextField
import com.nobodybuilds.listens.presentation.nb.NbType

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MetadataAutofillEntryPoint {
    fun musicRepository(): MusicRepository
}

/** What the edit sheet receives when the user taps "Fill fields". */
data class AutofillResult(
    val fields: MetadataFields,
    val selected: Set<AutofillField>,
    val coverArt: CoverArt?,
    val candidate: AutofillCandidate,
)

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Loaded(val candidates: List<AutofillCandidate>) : OnlineState
    data class Failed(val offline: Boolean) : OnlineState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataAutofillDialog(
    song: Song,
    current: MetadataFields,
    onDismiss: () -> Unit,
    onApply: (AutofillResult) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var queryTitle by remember { mutableStateOf(current.title) }
        var queryArtist by remember { mutableStateOf(current.artist) }
        var queryAlbum by remember { mutableStateOf(current.album) }

        var offlineCandidate by remember { mutableStateOf<AutofillCandidate?>(null) }
        var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var selected by remember { mutableStateOf<AutofillCandidate?>(null) }

        fun runSearch() {
            searchJob?.cancel()
            searchJob = scope.launch {
                online = OnlineState.Loading
                online = try {
                    OnlineState.Loaded(
                        MusicBrainzAutofill.search(
                            title = queryTitle,
                            artist = queryArtist,
                            album = queryAlbum,
                            durationMs = song.duration.takeIf { it > 0 }
                        )
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Autofill search failed")
                    OnlineState.Failed(offline = e is java.io.IOException)
                }
            }
        }

        LaunchedEffect(song.id) {
            offlineCandidate = withContext(Dispatchers.IO) {
                runCatching { OfflineMetadataGuesser.guess(song, loadFolderMates(context, song)) }
                    .onFailure { Timber.w(it, "Offline autofill failed") }
                    .getOrNull()
            }
            // Untagged files: search with what the filename and folder suggest.
            offlineCandidate?.fields?.let { guess ->
                if (queryTitle.isBlank()) queryTitle = guess.title
                if (queryArtist.isBlank()) queryArtist = guess.artist
                if (queryAlbum.isBlank()) queryAlbum = guess.album
            }
            if (queryTitle.isNotBlank()) runSearch() else online = OnlineState.Loaded(emptyList())
        }

        val chosen = selected
        if (chosen != null) {
            BackHandler { selected = null }
            CandidateDetail(
                candidate = chosen,
                current = current,
                songHasArt = !song.albumArtUriString.isNullOrBlank(),
                onBack = { selected = null },
                onApply = { result ->
                    onApply(result)
                    onDismiss()
                }
            )
        } else {
            Scaffold(
                topBar = {
                    AutofillTopBar(
                        title = stringResource(R.string.nobody_listens_autofill_title),
                        navIcon = Icons.Rounded.Close,
                        navDescription = stringResource(R.string.nobody_listens_autofill_close),
                        onNav = onDismiss
                    )
                },
                containerColor = Nb.canvas,
                contentWindowInsets = WindowInsets.statusBars
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.nobody_listens_autofill_subtitle),
                            style = NbType.body,
                            color = Nb.textSecondary
                        )
                    }
                    item {
                        NbCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NbTextField(
                                        value = queryTitle,
                                        onValueChange = { queryTitle = it },
                                        label = stringResource(R.string.nobody_listens_autofill_search_title),
                                        modifier = Modifier.weight(1f)
                                    )
                                    NbTextField(
                                        value = queryArtist,
                                        onValueChange = { queryArtist = it },
                                        label = stringResource(R.string.nobody_listens_autofill_search_artist),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                NbTextField(
                                    value = queryAlbum,
                                    onValueChange = { queryAlbum = it },
                                    label = stringResource(R.string.nobody_listens_autofill_search_album),
                                    optional = true
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (offlineCandidate != null && current.title.isBlank()) "Filled in from the filename" else "",
                                        style = NbType.caption,
                                        color = Nb.textMuted,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NbButton(
                                        text = stringResource(R.string.nobody_listens_autofill_search),
                                        onClick = { runSearch() },
                                        enabled = queryTitle.isNotBlank() && online !is OnlineState.Loading,
                                        style = NbButtonStyle.Secondary,
                                        size = NbButtonSize.Small,
                                        icon = rememberVectorPainter(Icons.Rounded.Search)
                                    )
                                }
                            }
                        }
                    }

                    offlineCandidate?.let { guess ->
                        item {
                            AutofillSectionHeader(
                                title = stringResource(R.string.nobody_listens_autofill_from_files),
                                subtitle = stringResource(R.string.nobody_listens_autofill_from_files_desc),
                                badge = { NbStatusChip(text = "Offline", status = NbStatus.Neutral) }
                            )
                        }
                        item {
                            CandidateRow(candidate = guess, highlighted = false, onClick = { selected = guess })
                        }
                    }

                    item {
                        AutofillSectionHeader(
                            title = stringResource(R.string.nobody_listens_autofill_online),
                            subtitle = stringResource(R.string.nobody_listens_autofill_online_desc),
                            badge = { Text("MusicBrainz", style = NbType.caption, color = Nb.textMuted) }
                        )
                    }
                    when (val state = online) {
                        OnlineState.Idle, OnlineState.Loading -> item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Nb.ink)
                                Text(
                                    text = stringResource(R.string.nobody_listens_autofill_searching),
                                    style = NbType.body,
                                    color = Nb.textSecondary
                                )
                            }
                        }
                        is OnlineState.Failed -> item {
                            NbCallout(
                                boldText = "Offline.",
                                text = stringResource(R.string.nobody_listens_autofill_offline_error),
                                actionLabel = stringResource(R.string.nobody_listens_autofill_search),
                                onAction = { runSearch() }
                            )
                        }
                        is OnlineState.Loaded -> {
                            if (state.candidates.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.nobody_listens_autofill_no_results),
                                        style = NbType.body,
                                        color = Nb.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(state.candidates, key = { _, it -> (it.recordingId ?: "") + (it.releaseId ?: "") }) { index, candidate ->
                                    CandidateRow(
                                        candidate = candidate,
                                        highlighted = index == 0 && candidate.confidence >= 80,
                                        onClick = { selected = candidate }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadFolderMates(context: Context, song: Song): List<Song> {
    val folder = File(song.path).parent ?: return emptyList()
    return runCatching {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, MetadataAutofillEntryPoint::class.java)
            .musicRepository()
        repository.getAllSongsOnce().filter { File(it.path).parent == folder }
    }.getOrElse { emptyList() }
}
