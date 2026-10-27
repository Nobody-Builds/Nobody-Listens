@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.utils.formatListeningDurationCompact
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote

@Composable
internal fun TopArtistsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val supportingColor = contentColor.copy(alpha = 0.76f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Consistent with Hero
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_section_top_artists),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = contentColor
            )
            val artists = summary?.topArtists.orEmpty()
            if (artists.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.presentation_batch_g_stats_empty_no_artists_title),
                    subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_artists_subtitle)
                )
            } else {
                val maxDuration = artists.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    artists.forEachIndexed { index, artistSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ArtistAvatar(
                                    name = artistSummary.artist,
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            R.string.presentation_batch_g_stats_ranked_artist,
                                            index + 1,
                                            artistSummary.artist
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.presentation_batch_g_stats_plays_tracks,
                                            artistSummary.playCount,
                                            artistSummary.uniqueSongs
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = supportingColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(artistSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = supportingColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (artistSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = contentColor.copy(alpha = 0.18f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistAvatar(
    name: String,
    containerColor: Color,
    contentColor: Color
) {
    val placeholder = stringResource(R.string.presentation_batch_g_stats_avatar_placeholder)
    val initials = remember(name, placeholder) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercaseChar().toString() }
            .ifBlank { placeholder }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun TopAlbumsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val supportingColor = contentColor.copy(alpha = 0.76f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Consistent with Hero
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_stats_section_top_albums),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = contentColor
            )
            val albums = summary?.topAlbums.orEmpty()
            if (albums.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Rounded.Album,
                    title = stringResource(R.string.presentation_batch_g_stats_empty_no_albums_title),
                    subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_albums_subtitle)
                )
            } else {
                val maxDuration = albums.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    albums.forEachIndexed { index, albumSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SmartImage(
                                    model = albumSummary.albumArtUri,
                                    contentDescription = albumSummary.album,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            R.string.presentation_batch_g_stats_ranked_album,
                                            index + 1,
                                            albumSummary.album
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.presentation_batch_g_stats_plays_tracks,
                                            albumSummary.playCount,
                                            albumSummary.uniqueSongs
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = supportingColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(albumSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = supportingColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (albumSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = contentColor.copy(alpha = 0.18f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SongStatsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val songs = summary?.songs.orEmpty()
            var showAll by rememberSaveable(songs) { mutableStateOf(songs.size <= 8) }
            val displayedSongs = remember(songs, showAll) {
                if (showAll || songs.size <= 8) songs else songs.take(8)
            }
            val maxDuration = songs.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
            val positions = remember(songs) { songs.mapIndexed { index, song -> song.songId to index }.toMap() }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_g_stats_tracks_in_range_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = stringResource(R.string.presentation_batch_g_stats_tracks_in_range_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor
                )
            }

            if (songs.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.presentation_batch_g_stats_empty_no_tracks_title),
                    subtitle = stringResource(R.string.presentation_batch_g_stats_empty_no_tracks_subtitle)
                )
            } else {
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displayedSongs.forEach { songSummary ->
                        val position = positions[songSummary.songId] ?: songs.indexOf(songSummary)
                        val accentColor = when {
                            position == 0 -> MaterialTheme.colorScheme.primary
                            position < 3 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        val accentOnColor = when {
                            position == 0 -> MaterialTheme.colorScheme.onPrimary
                            position < 3 -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onTertiary
                        }
                        val rowContainerColor = when {
                            position == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            position < 3 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = rowContainerColor
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CategoryRankBadge(
                                        rank = position + 1,
                                        accentColor = accentColor,
                                        accentOnColor = accentOnColor,
                                        highlighted = position == 0
                                    )
                                    SmartImage(
                                        model = songSummary.albumArtUri,
                                        contentDescription = songSummary.title,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = songSummary.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = contentColor
                                        )
                                        Text(
                                            text = songSummary.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = supportingColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.presentation_batch_g_stats_n_plays,
                                                songSummary.playCount
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = supportingColor
                                        )
                                    }
                                    Text(
                                        text = formatListeningDurationCompact(songSummary.totalDurationMs),
                                        style = rememberStatsMetricValueStyle(compact = true),
                                        color = contentColor
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { (songSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = accentColor,
                                    trackColor = supportingColor.copy(alpha = 0.20f)
                                )
                            }
                        }
                    }
                }

                if (songs.size > 8) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = if (showAll) {
                                stringResource(R.string.presentation_batch_g_stats_collapse_tracks)
                            } else {
                                stringResource(R.string.presentation_batch_g_stats_show_all_tracks)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
