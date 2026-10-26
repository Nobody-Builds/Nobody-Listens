package com.nobodybuilds.listens.presentation.ytmusic.dashboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicPlaylist
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicShelf
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
internal fun ShelfList(
    shelves: List<YouTubeMusicShelf>,
    cardShape: AbsoluteSmoothCornerShape,
    contentPadding: PaddingValues,
    onPlay: (YouTubeMusicShelf, YouTubeMusicTrack) -> Unit,
    onOpenPlaylist: (YouTubeMusicPlaylist) -> Unit,
    onDownload: (YouTubeMusicTrack) -> Unit,
    onWatch: (YouTubeMusicTrack) -> Unit,
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
            items(shelf.playlists.distinctBy { it.playlistId }, key = { "pl-$index-${it.playlistId}" }) { playlist ->
                MediaCard(
                    title = playlist.title,
                    subtitle = playlist.subtitle.ifBlank { "Playlist" },
                    thumbnailUrl = playlist.thumbnailUrl,
                    cardShape = cardShape,
                    onClick = { onOpenPlaylist(playlist) },
                )
            }
            items(shelf.tracks.distinctBy { it.videoId }, key = { "tr-$index-${it.videoId}" }) { track ->
                MediaCard(
                    title = track.title,
                    subtitle = track.artist.ifBlank { "YouTube Music" },
                    thumbnailUrl = track.thumbnailUrl,
                    cardShape = cardShape,
                    onClick = { onPlay(shelf, track) },
                    onDownload = { onDownload(track) },
                    onWatch = { onWatch(track) },
                )
            }
        }
    }
}

@Composable
internal fun PlaylistTracks(
    tracks: List<YouTubeMusicTrack>,
    loading: Boolean,
    cardShape: AbsoluteSmoothCornerShape,
    contentPadding: PaddingValues,
    onPlay: (YouTubeMusicTrack) -> Unit,
    onDownload: (YouTubeMusicTrack) -> Unit,
    onWatch: (YouTubeMusicTrack) -> Unit,
    onDownloadAll: () -> Unit,
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
        items(tracks.distinctBy { it.videoId }, key = { it.videoId }) { track ->
            MediaCard(
                title = track.title,
                subtitle = track.artist.ifBlank { "YouTube Music" },
                thumbnailUrl = track.thumbnailUrl,
                cardShape = cardShape,
                onClick = { onPlay(track) },
                onDownload = { onDownload(track) },
                onWatch = { onWatch(track) },
                trailingPlay = true,
            )
        }
    }
}

@Composable
private fun MediaCard(
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
