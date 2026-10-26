package com.nobodybuilds.listens.presentation.artist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import com.nobodybuilds.listens.presentation.viewmodel.ArtistStoryState
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbEmptyState
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbSectionHeader
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbTile
import com.nobodybuilds.listens.presentation.nb.NbType
import java.time.ZoneId

/**
 * "Your story with <artist>" — built from local listening history.
 * Shown on the artist page between the header and the album sections.
 */
@Composable
fun ArtistStorySection(
    artistName: String,
    state: ArtistStoryState,
    canPlay: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlayArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ArtistStoryState.Loading -> Unit // stays quiet; the section appears once ready
        ArtistStoryState.Empty -> Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NbSectionHeader(title = "Your story with $artistName")
            NbEmptyState(
                title = "No story yet",
                body = "Play something by $artistName to start one.",
            ) {
                if (canPlay) {
                    NbButton(
                        text = "Play",
                        onClick = onPlayArtist,
                        style = NbButtonStyle.Accent,
                        size = NbButtonSize.Small,
                        icon = rememberVectorPainter(Icons.Rounded.PlayArrow)
                    )
                }
            }
        }
        is ArtistStoryState.Ready -> ArtistStoryContent(artistName, state, onPlaySong, modifier)
    }
}

@Composable
private fun ArtistStoryContent(
    artistName: String,
    state: ArtistStoryState.Ready,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier,
) {
    val story = state.story
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val calc = ArtistStoryCalculator

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NbSectionHeader(title = "Your story with $artistName")

        // Hero: total listening time.
        NbTile(background = Nb.accentSoft, modifier = Modifier.fillMaxWidth()) {
            NbOverline("Since ${calc.formatDate(story.firstListenMillis, zone)}", color = Nb.accentText)
            Text(
                calc.formatDuration(story.totalDurationMs),
                style = NbType.display(34.sp),
                color = Nb.text,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                buildString {
                    append(if (story.playCount == 1) "1 play" else "${story.playCount} plays")
                    append(" · ")
                    append(calc.formatShare(story.shareOfAll))
                    append(" of everything you played")
                },
                style = NbType.meta,
                color = Nb.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Facts grid.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryFact(
                label = "First listen",
                value = calc.formatDate(story.firstListenMillis, zone),
                supporting = state.firstSong?.title,
                modifier = Modifier.weight(1f)
            )
            StoryFact(
                label = "Last played",
                value = calc.formatRelative(story.lastPlayedMillis, now, zone).replaceFirstChar { it.uppercase() },
                supporting = null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryFact(
                label = "Your peak",
                value = calc.formatMonth(story.peakMonth),
                supporting = calc.formatDuration(story.peakMonthDurationMs),
                modifier = Modifier.weight(1f)
            )
            StoryFact(
                label = "Share",
                value = calc.formatShare(story.shareOfAll),
                supporting = "of all your listening",
                modifier = Modifier.weight(1f)
            )
        }

        // Your song.
        state.topSong?.let { song ->
            NbCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPlaySong(song) },
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NbArt(
                        model = song.albumArtUriString,
                        title = song.album.ifBlank { song.title },
                        size = 56.dp,
                        shape = NbShapes.md,
                        contentDescription = song.title
                    )
                    Column(Modifier.weight(1f)) {
                        NbOverline("Your song")
                        Text(
                            song.title,
                            style = NbType.title.copy(fontWeight = FontWeight.Bold),
                            color = Nb.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            (if (story.topSongPlays == 1) "1 play" else "${story.topSongPlays} plays") +
                                " · " + calc.formatDuration(story.topSongDurationMs),
                            style = NbType.meta,
                            color = Nb.textMuted,
                            maxLines = 1
                        )
                    }
                    NbIconButton(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${song.title}",
                        onClick = { onPlaySong(song) },
                        style = NbIconButtonStyle.Accent,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                }
            }
        }

        StoryTimeline(months = story.months)

        if (story.milestones.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                story.milestones.forEach { NbStatusChip(text = it, status = NbStatus.Accent) }
            }
        }
    }
}

@Composable
private fun StoryFact(label: String, value: String, supporting: String?, modifier: Modifier = Modifier) {
    NbCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = NbType.caption, color = Nb.textMuted, maxLines = 1)
        Text(value, style = NbType.h3, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            supporting ?: " ",
            style = NbType.caption,
            color = Nb.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoryTimeline(months: List<StoryMonth>) {
    if (months.isEmpty()) return
    val haptics = LocalNobodyListensHaptics.current
    var selected by remember(months) {
        mutableIntStateOf(months.indexOfLast { it.durationMs > 0 }.takeIf { it >= 0 } ?: months.lastIndex)
    }
    val maxMs = remember(months) { months.maxOf { it.durationMs }.coerceAtLeast(1L) }
    val accent = Nb.ink
    val track = Nb.muted
    val sel = months.getOrNull(selected)

    NbCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbOverline("Month by month", modifier = Modifier.weight(1f))
            if (sel != null) {
                Text(
                    "${ArtistStoryCalculator.formatMonth(sel.month)} · " +
                        if (sel.durationMs > 0) ArtistStoryCalculator.formatDuration(sel.durationMs) else "no plays",
                    style = NbType.label,
                    color = Nb.text,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = "Listening per month bar chart" }
                .pointerInput(months) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / months.size
                        val idx = (offset.x / slot).toInt().coerceIn(0, months.lastIndex)
                        if (idx != selected) {
                            selected = idx
                            haptics.selection()
                        }
                    }
                }
        ) {
            val n = months.size
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            val minH = 3.dp.toPx()
            months.forEachIndexed { i, m ->
                val left = i * slot + (slot - barW) / 2f
                // Faint full-height track keeps empty months visible.
                drawRoundRect(color = track, topLeft = Offset(left, 0f), size = Size(barW, size.height), cornerRadius = radius)
                if (m.durationMs > 0) {
                    val h = (size.height * (m.durationMs.toFloat() / maxMs)).coerceAtLeast(minH)
                    drawRoundRect(
                        color = if (i == selected) accent else accent.copy(alpha = 0.55f),
                        topLeft = Offset(left, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = radius
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            months.forEachIndexed { i, m ->
                Text(
                    ArtistStoryCalculator.monthInitial(m.month),
                    style = NbType.caption.copy(fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == selected) Nb.text else Nb.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
