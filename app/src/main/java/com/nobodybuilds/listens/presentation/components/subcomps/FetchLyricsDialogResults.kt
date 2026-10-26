package com.nobodybuilds.listens.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.repository.LyricsSearchResult
import com.nobodybuilds.listens.utils.ProviderText

@Composable
internal fun PickResultContent(
    results: List<LyricsSearchResult>,
    onPickResult: (LyricsSearchResult) -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = stringResource(R.string.found_n_matches_format).format(results.size),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Lista Scrollable Optimizada
    LazyColumn(
        modifier = Modifier.heightIn(max = 350.dp), // Altura máxima dinámica
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(results, key = { it.record.id }) { result ->
            ResultItemCard(result = result, onClick = { onPickResult(result) })
        }

        item {
            ProviderText(
                providerText = stringResource(R.string.lyrics_provided_by),
                uri = stringResource(R.string.lrclib_uri),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Botón Cancelar al final de la lista
    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun ResultItemCard(
    result: LyricsSearchResult,
    onClick: () -> Unit
) {
    val hasSyncedLyrics = !result.record.syncedLyrics.isNullOrEmpty()
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasSyncedLyrics) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasSyncedLyrics) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.record.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasSyncedLyrics) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_synced_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.presentation_batch_g_list_song_artist_album,
                        result.record.artistName,
                        result.record.albumName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NotFoundContent(
    initialTitle: String,
    initialArtist: String?,
    message: String,
    onManualSearch: (String, String?) -> Unit,
    onCancel: () -> Unit
) {
    val normalizedArtist = rememberSaveable(initialArtist) {
        initialArtist
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it.equals("<unknown>", ignoreCase = true) }
            .orEmpty()
    }

    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var artist by rememberSaveable { mutableStateOf(normalizedArtist) }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.lyrics_not_found),
        style = MaterialTheme.typography.headlineSmall
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.lyrics_custom_search_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))

    // Title input
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.song_field_title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.song_field_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    Spacer(Modifier.height(8.dp))

    // Artist input
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.song_field_artist_optional),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it },
            placeholder = { Text(stringResource(R.string.song_field_artist_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    Spacer(Modifier.height(24.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = {
                onManualSearch(
                    title,
                    artist.takeIf { it.isNotBlank() }
                )
            },
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Search, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.search))
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}
