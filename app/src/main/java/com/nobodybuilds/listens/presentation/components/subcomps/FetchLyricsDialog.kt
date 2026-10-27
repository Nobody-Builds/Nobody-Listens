package com.nobodybuilds.listens.presentation.components.subcomps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.LyricsSearchResult
import com.nobodybuilds.listens.presentation.viewmodel.LyricsSearchUiState

@Composable
fun FetchLyricsDialog(
    uiState: LyricsSearchUiState,
    currentSong: Song?,
    onConfirm: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    if (uiState is LyricsSearchUiState.Success) return

    var forcePickResults by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp), // Forma muy redondeada (Expressive)
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (uiState) {
                    LyricsSearchUiState.Idle -> {
                        IdleContent(
                            currentSong = currentSong,
                            forcePickResults = forcePickResults,
                            onToggleForcePickResults = { forcePickResults = it },
                            onSearch = { onConfirm(forcePickResults) },
                            onImport = onImport,
                            onCancel = onDismiss
                        )
                    }
                    LyricsSearchUiState.Loading -> {
                        LoadingContent()
                    }
                    is LyricsSearchUiState.PickResult -> {
                        PickResultContent(
                            results = uiState.results,
                            onPickResult = onPickResult,
                            onCancel = onDismiss // Usamos botón cancelar en lugar de X
                        )
                    }
                    is LyricsSearchUiState.NotFound -> {
                        NotFoundContent(
                            message = uiState.message,
                            initialTitle = currentSong?.title.orEmpty(),
                            initialArtist = currentSong?.displayArtist,
                            onManualSearch = { title, artist ->
                                onManualSearch(title, artist)
                            },
                            onCancel = onDismiss
                        )
                    }
                    is LyricsSearchUiState.Error -> {
                        ErrorContent(
                            message = uiState.message,
                            onDismiss = onDismiss
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

///**
// * Diálogo que gestiona la búsqueda de letras para una canción.
// * Muestra diferentes contenidos según el estado de la búsqueda.
// */
//@Composable
//fun FetchLyricsDialog(
//    uiState: LyricsSearchUiState,
//    currentSong: Song?,
//    onConfirm: () -> Unit,
//    onPickResult: (LyricsSearchResult) -> Unit,
//    onDismiss: () -> Unit,
//    onImport: () -> Unit
//) {
//    if (uiState is LyricsSearchUiState.Success) {
//        // Do nothing and don't show the dialog if the state is success
//        return
//    }
//
//    Dialog(onDismissRequest = onDismiss) {
//        Surface(
//            shape = RoundedCornerShape(28.dp),
//            color = MaterialTheme.colorScheme.surfaceContainerHigh,
//            contentColor = MaterialTheme.colorScheme.onSurface
//        ) {
//            Box {
//                when (uiState) {
//                    LyricsSearchUiState.Idle -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            DialogHeader(currentSong = currentSong)
//                            Spacer(modifier = Modifier.height(8.dp))
//                            Text(
//                                text = stringResource(R.string.lyrics_not_found),
//                                style = MaterialTheme.typography.headlineSmall,
//                                textAlign = TextAlign.Center
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.search_lyrics_online_prompt),
//                                style = MaterialTheme.typography.bodyMedium,
//                                textAlign = TextAlign.Center,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            Spacer(modifier = Modifier.height(24.dp))
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                OutlinedButton(
//                                    onClick = onImport,
//                                    modifier = Modifier.weight(1f)
//                                ) {
//                                    Icon(painter = painterResource(R.drawable.rounded_upload_file_24), contentDescription = null, modifier = Modifier.size(18.dp))
//                                    Spacer(modifier = Modifier.width(8.dp))
//                                    Text(stringResource(R.string.import_file))
//                                }
//                                Button(
//                                    onClick = onConfirm,
//                                    modifier = Modifier.weight(1f)
//                                ) {
//                                    Icon(painter = painterResource(R.drawable.rounded_manage_search_24), contentDescription = null, modifier = Modifier.size(18.dp))
//                                    Spacer(modifier = Modifier.width(8.dp))
//                                    Text(stringResource(R.string.search))
//                                }
//                            }
//                        }
//                    }
//                    LyricsSearchUiState.Loading -> {
//                        Column(
//                            modifier = Modifier
//                                .padding(horizontal = 24.dp, vertical = 48.dp)
//                                .fillMaxWidth(),
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.Center
//                        ) {
//                            CircularProgressIndicator()
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.searching_lyrics),
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//                    }
//                    is LyricsSearchUiState.PickResult -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            DialogHeader(currentSong = currentSong)
//                            Spacer(modifier = Modifier.height(16.dp))
//                            ResultContextChip(query = uiState.query)
//                            Spacer(modifier = Modifier.height(10.dp))
//                            Text(
//                                text = stringResource(R.string.found_n_matches_format).format(uiState.results.size),
//                                style = MaterialTheme.typography.headlineSmall,
//                                textAlign = TextAlign.Center
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//
//                            val scrollState = rememberScrollState()
//                            Column(
//                                modifier = Modifier.verticalScroll(scrollState),
//                                horizontalAlignment = Alignment.CenterHorizontally
//                            ) {
//                                uiState.results.forEach { result ->
//                                    if (result != uiState.results.first()) {
//                                        Spacer(modifier = Modifier.height(12.dp))
//                                    }
//
//                                    OutlinedButton (
//                                        modifier = Modifier.fillMaxWidth(),
//                                        shape = RoundedCornerShape(12.dp),
//                                        contentPadding = PaddingValues(12.dp),
//                                        onClick = { onPickResult(result) },
//                                    ) {
//                                        Column(
//                                            modifier = Modifier.fillMaxWidth(),
//                                            horizontalAlignment = Alignment.Start
//                                        ) {
//                                            Text(
//                                                text = result.record.name,
//                                                style = MaterialTheme.typography.bodyMedium,
//                                                fontWeight = FontWeight.Bold,
//                                                color = MaterialTheme.colorScheme.onSurface
//                                            )
//                                            Spacer(modifier = Modifier.height(4.dp))
//                                            Text(
//                                                text = "${result.record.artistName} - ${result.record.albumName}",
//                                                style = MaterialTheme.typography.bodyMedium,
//                                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                                            )
//                                        }
//                                    }
//                                }
//                            }
//
//                            ProviderText(
//                                providerText = stringResource(R.string.lyrics_provided_by),
//                                uri = stringResource(R.string.lrclib_uri),
//                                textAlign = TextAlign.Center,
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .padding(vertical = 16.dp)
//                            )
//                        }
//                    }
//                    is LyricsSearchUiState.Error -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Icon(
//                                imageVector = Icons.Rounded.ErrorOutline,
//                                contentDescription = null,
//                                modifier = Modifier.size(28.dp),
//                                tint = MaterialTheme.colorScheme.error
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.error),
//                                style = MaterialTheme.typography.headlineSmall
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = uiState.message,
//                                style = MaterialTheme.typography.bodyMedium,
//                                textAlign = TextAlign.Center,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            if (uiState.query != null) {
//                                Spacer(modifier = Modifier.height(16.dp))
//                                Text(
//                                    text = stringResource(R.string.searched_for_x_format).format(uiState.query),
//                                    style = MaterialTheme.typography.bodyMedium,
//                                    textAlign = TextAlign.Center,
//                                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                                )
//                            }
//                            Spacer(modifier = Modifier.height(24.dp))
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.End
//                            ) {
//                                TextButton(onClick = onDismiss) {
//                                    Text(stringResource(R.string.ok))
//                                }
//                            }
//                        }
//                    }
//                    is LyricsSearchUiState.Success -> {
//                        // Handled outside, this case should not render the dialog
//                    }
//                }
//
//                // Close button for Idle, PickResult and Error states
//                if (uiState is LyricsSearchUiState.Idle
//                    || uiState is LyricsSearchUiState.PickResult
//                    || uiState is LyricsSearchUiState.Error) {
//                     IconButton(
//                         onClick = onDismiss,
//                         modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
//                     ) {
//                         Icon(
//                             imageVector = Icons.Rounded.Close,
//                             contentDescription = stringResource(R.string.cancel)
//                         )
//                     }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun DialogHeader(currentSong: Song?) {
//    val title = currentSong?.title.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_song_title)
//    val artist = currentSong?.displayArtist.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_artist)
//    val album = currentSong?.album.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_album)
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clip(RoundedCornerShape(18.dp))
//            .padding(horizontal = 4.dp, vertical = 2.dp)
//            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Box(
//            modifier = Modifier
//                .size(48.dp)
//                .clip(RoundedCornerShape(16.dp)),
//            contentAlignment = Alignment.Center
//        ) {
//            Icon(
//                imageVector = Icons.Rounded.MusicNote,
//                contentDescription = null,
//                modifier = Modifier.size(24.dp),
//                tint = MaterialTheme.colorScheme.onSecondaryContainer
//            )
//        }
//        Spacer(modifier = Modifier.width(12.dp))
//        Column(
//            modifier = Modifier.weight(1f)
//        ) {
//            Text(
//                text = title,
//                style = MaterialTheme.typography.titleMedium,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//            Spacer(modifier = Modifier.height(2.dp))
//            Text(
//                text = artist,
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//            Spacer(modifier = Modifier.height(2.dp))
//            Text(
//                text = album,
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//    }
//}
//
//@Composable
//private fun ResultContextChip(query: String) {
//    SuggestionChip(
//        onClick = {},
//        label = { Text(text = query) },
//        icon = {
//            Icon(
//                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
//                contentDescription = null
//            )
//        }
//    )
//}
