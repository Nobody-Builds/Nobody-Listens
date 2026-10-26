package com.nobodybuilds.listens.presentation.streaming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stream.StreamingCollection
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.stream.StreamingTrack
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * The screen of a streaming service: Feed, Library and Search tabs, and playlist,
 * album and artist pages. [toSong] turns a track into the library song the player
 * queues; [onWatch] adds a video button to tracks when the service has video.
 */

@Composable
internal fun SignInPrompt(serviceName: String, body: String, onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Sign in to $serviceName",
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                fontFamily = GoogleSansRounded,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSignIn) {
                Text("Sign in", fontFamily = GoogleSansRounded)
            }
        }
    }
}

@Composable
internal fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing here yet",
                fontFamily = GoogleSansRounded,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reload your feed or search for something to play.",
                fontFamily = GoogleSansRounded,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun <T : StreamingTrack, C : StreamingCollection> ShelfList(
    shelves: List<StreamingShelf<T, C>>,
    serviceName: String,
    cardShape: AbsoluteSmoothCornerShape,
    contentPadding: PaddingValues,
    onPlay: (StreamingShelf<T, C>, T) -> Unit,
    onOpen: (C) -> Unit,
    onDownload: (T) -> Unit,
    onWatch: ((T) -> Unit)?,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Keys include the shelf's index: untitled shelves (search, headerless sections) can share items.
        shelves.forEachIndexed { index, shelf ->
            if (shelf.title.isNotBlank()) {
                item(key = "title-$index-${shelf.title}") {
                    Text(
                        shelf.title,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(shelf.playlists.distinctBy { it.key }, key = { "pl-$index-${it.key}" }) { collection ->
                MediaCard(
                    title = collection.title,
                    subtitle = collection.subtitle.ifBlank { "Playlist" },
                    thumbnailUrl = collection.thumbnailUrl,
                    cardShape = cardShape,
                    onClick = { onOpen(collection) },
                )
            }
            items(shelf.tracks.distinctBy { it.key }, key = { "tr-$index-${it.key}" }) { track ->
                MediaCard(
                    title = track.title,
                    subtitle = track.artist.ifBlank { serviceName },
                    thumbnailUrl = track.thumbnailUrl,
                    cardShape = cardShape,
                    onClick = { onPlay(shelf, track) },
                    onDownload = { onDownload(track) },
                    onWatch = onWatch?.let { watch -> { watch(track) } },
                )
            }
        }
    }
}

@Composable
internal fun <T : StreamingTrack> PageTracks(
    tracks: List<T>,
    serviceName: String,
    loading: Boolean,
    cardShape: AbsoluteSmoothCornerShape,
    contentPadding: PaddingValues,
    onPlay: (T) -> Unit,
    onDownload: (T) -> Unit,
    onWatch: ((T) -> Unit)?,
    onDownloadAll: () -> Unit,
    onImport: (() -> Unit)?,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Songs",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.titleMedium
                )
                if (tracks.isNotEmpty()) {
                    if (onImport != null) {
                        TextButton(onClick = onImport) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Import", fontFamily = GoogleSansRounded)
                        }
                    }
                    TextButton(onClick = onDownloadAll) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Download all", fontFamily = GoogleSansRounded)
                    }
                }
            }
        }
        if (loading && tracks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        // Tracks can be flattened from several shelves, so the same song may appear twice.
        items(tracks.distinctBy { it.key }, key = { it.key }) { track ->
            MediaCard(
                title = track.title,
                subtitle = track.artist.ifBlank { serviceName },
                thumbnailUrl = track.thumbnailUrl,
                cardShape = cardShape,
                onClick = { onPlay(track) },
                onDownload = { onDownload(track) },
                onWatch = onWatch?.let { watch -> { watch(track) } },
                trailingPlay = true,
            )
        }
    }
}

@Composable
internal fun MediaCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    cardShape: AbsoluteSmoothCornerShape,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onWatch: (() -> Unit)? = null,
    trailingPlay: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    SmartImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingPlay) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            if (onWatch != null) {
                IconButton(onClick = onWatch) {
                    Icon(
                        Icons.Rounded.SmartDisplay,
                        contentDescription = "Watch video",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Download highest quality",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** The service's suggestions for the text being typed, in the search field's style. */
@Composable
fun StreamingSearchSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
