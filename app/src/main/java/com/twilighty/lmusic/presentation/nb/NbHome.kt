package com.twilighty.lmusic.presentation.nb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.data.stats.PlaybackStatsRepository
import com.twilighty.lmusic.utils.formatListeningDurationCompact
import java.util.Calendar

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }
}

/** Home's header: kicker greeting, "Listen", and the quick actions. */
@Composable
fun NbHomeHeader(
    onSettingsClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onSourcesClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPartyClick: (() -> Unit)? = null,
    onLofiClick: (() -> Unit)? = null,
) {
    val greeting = remember { greetingForNow() }
    NbTopBar(
        title = "Listen",
        kicker = greeting,
        modifier = modifier.statusBarsPadding().padding(top = 8.dp),
        actions = {
            NbIconButton(
                painter = painterResource(R.drawable.round_newspaper_24),
                contentDescription = "What's new",
                onClick = onWhatsNewClick
            )
            NbIconButton(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = "Music sources",
                onClick = onSourcesClick
            )
            if (onLofiClick != null) {
                NbIconButton(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Headphones,
                    contentDescription = "Lofi",
                    onClick = onLofiClick
                )
            }
            if (onPartyClick != null) {
                NbIconButton(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Celebration,
                    contentDescription = "Party queue",
                    onClick = onPartyClick
                )
            }
            NbIconButton(
                painter = painterResource(R.drawable.rounded_settings_24),
                contentDescription = "Settings",
                onClick = onSettingsClick,
                style = NbIconButtonStyle.Tonal
            )
        }
    )
}

/** The accent "Your mix" tile with a cover strip and an inverse play button. */
@Composable
fun NbMixTile(
    songs: List<Song>,
    isShuffleEnabled: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Nb.accent
    val onAccent = Nb.onAccent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.xxl)
            .background(accent)
            .clickable(onClick = onPlay)
            .padding(20.dp)
    ) {
        Text("TODAY", style = NbType.overline, color = onAccent.copy(alpha = 0.78f))
        Spacer(Modifier.height(4.dp))
        Text("Your mix", style = NbType.display(36.sp), color = onAccent)
        Spacer(Modifier.height(4.dp))
        val artists = remember(songs) {
            songs.asSequence().map { it.displayArtist }.filter { it.isNotBlank() }.distinct().take(3).toList()
        }
        Text(
            text = if (artists.isEmpty()) "${songs.size} songs from your library"
            else "${artists.joinToString(", ")} and more · ${songs.size} songs",
            style = NbType.meta,
            color = onAccent.copy(alpha = 0.86f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(56.dp)) {
                songs.take(4).forEachIndexed { index, song ->
                    NbArt(
                        model = song.albumArtUriString,
                        title = song.album.ifBlank { song.title },
                        size = 52.dp,
                        shape = NbShapes.md,
                        modifier = Modifier
                            .offset(x = (index * 38).dp)
                            .border(2.dp, accent, NbShapes.md)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(onAccent)
                    .clickable(role = Role.Button, onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isShuffleEnabled) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                    contentDescription = "Play your mix",
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/** Horizontal row of recently played covers (148dp cards). */
@Composable
fun NbRecentlyPlayedRow(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) = NbSongCoverRow(
    title = "Recently played",
    songs = songs,
    currentSongId = currentSongId,
    onSongClick = onSongClick,
    actionLabel = "See all",
    onAction = onSeeAll,
    keyPrefix = "recent",
    modifier = modifier
)

/** A section with a header and a horizontal row of 148dp song covers (title 15/700, meta muted). */
@Composable
fun NbSongCoverRow(
    title: String,
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    keyPrefix: String = title,
) {
    Column(modifier) {
        NbSectionHeader(
            title = title,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { "${keyPrefix}_${it.id}" }) { song ->
                NbCoverCard(
                    artModel = song.albumArtUriString,
                    artKey = song.album.ifBlank { song.title },
                    title = song.title,
                    meta = song.displayArtist,
                    highlighted = song.id == currentSongId,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

/** Flat cover card: square art (r16), title 15/700, muted meta. Used on Home, Library and Search. */
@Composable
fun NbCoverCard(
    artModel: Any?,
    artKey: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 148.dp,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(size)
            .clip(NbShapes.lg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        NbArt(model = artModel, title = artKey, size = size, shape = NbShapes.lg)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = NbType.title.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = if (highlighted) Nb.accentText else Nb.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            meta,
            style = NbType.caption,
            color = Nb.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "Continue listening": the current (or last) song with art, title, artist, a thin
 * progress line and a play / pause button. [progress] is read in the draw phase only,
 * so position updates don't recompose the card.
 */
@Composable
fun NbContinueListeningCard(
    song: Song,
    isPlaying: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Nb.ink
    val track = Nb.emphasis
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .background(Nb.canvas)
            .border(1.dp, Nb.border, NbShapes.lg)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NbArt(
                model = song.albumArtUriString,
                title = song.album.ifBlank { song.title },
                size = 56.dp,
                shape = NbShapes.md
            )
            Column(Modifier.weight(1f)) {
                NbOverline(if (isPlaying) "Now playing" else "Continue listening")
                Text(song.title, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.displayArtist, style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Nb.ink)
                    .clickable(role = Role.Button, onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Resume",
                    tint = Nb.onInk,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .height(4.dp)
                .drawBehind {
                    val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(color = track, cornerRadius = radius)
                    val fraction = progress().coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = accent,
                            size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                            cornerRadius = radius
                        )
                    }
                }
        )
    }
}

private val mixTintIndex = listOf(0, 1, 2, 3)

/** "Made for you": horizontal row of mix cards. Tapping a card plays the mix. */
@Composable
fun NbMadeForYouRow(
    mixes: List<MadeForYouMix>,
    onMixClick: (MadeForYouMix) -> Unit,
    modifier: Modifier = Modifier,
    // TODO: AI mixes appended here — pass generated mixes and they render after the rule-based ones.
    extraMixes: List<MadeForYouMix> = emptyList(),
) {
    val all = remember(mixes, extraMixes) { (mixes + extraMixes).filter { it.songs.isNotEmpty() } }
    if (all.isEmpty()) return
    Column(modifier) {
        NbSectionHeader(title = "Made for you", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(all.size, key = { "mfy_${all[it].id}" }) { index ->
                NbMixCard(mix = all[index], tint = index % mixTintIndex.size, onClick = { onMixClick(all[index]) })
            }
        }
    }
}

@Composable
private fun NbMixCard(mix: MadeForYouMix, tint: Int, onClick: () -> Unit) {
    val background = when (tint) {
        0 -> Nb.tintPeach
        1 -> Nb.tintLavender
        2 -> Nb.tintMint
        else -> Nb.muted
    }
    val covers = remember(mix.songs) {
        mix.songs.asSequence().distinctBy { it.albumId }.take(4).toList()
            .let { if (it.size >= 4) it else mix.songs.take(4) }
    }
    Column(
        modifier = Modifier
            .width(164.dp)
            .clip(NbShapes.xl)
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (rowIndex in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (colIndex in 0 until 2) {
                        val song = covers.getOrNull(rowIndex * 2 + colIndex)
                        if (song != null) {
                            NbArt(
                                model = song.albumArtUriString,
                                title = song.album.ifBlank { song.title },
                                size = 68.dp,
                                shape = NbShapes.sm
                            )
                        } else {
                            Box(Modifier.size(68.dp).clip(NbShapes.sm).background(Nb.emphasis))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(mix.title, style = NbType.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mix.subtitle, style = NbType.caption, color = Nb.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "Your top artists": round avatars (artist image or initials). */
@Composable
fun NbTopArtistsRow(
    artists: List<NbHomeArtist>,
    onArtistClick: (NbHomeArtist) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (artists.isEmpty()) return
    Column(modifier) {
        NbSectionHeader(
            title = "Your top artists",
            actionLabel = if (onSeeAll != null) "Stats" else null,
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(artists, key = { "top_artist_${it.id}" }) { artist ->
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .clip(NbShapes.lg)
                        .clickable { onArtistClick(artist) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NbAvatar(model = artist.imageModel, name = artist.name, size = 88.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        artist.name,
                        style = NbType.label,
                        color = Nb.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Round artist avatar: image if there is one, otherwise the Daylight colour + centred initials. */
@Composable
fun NbAvatar(model: Any?, name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val hasImage = model != null && (model !is String || model.isNotBlank())
    if (hasImage) {
        NbArt(model = model, title = name, size = size, shape = CircleShape, modifier = modifier)
    } else {
        val color = remember(name) { nbArtColor(name) }
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                nbInitials(name),
                color = com.twilighty.lmusic.ui.theme.Daylight.Paper,
                style = NbType.display((size.value * 0.3f).sp),
                maxLines = 1
            )
        }
    }
}

/** "This week" — listening time, plays and the top artist as Daylight stat tiles. */
@Composable
fun NbWeekStats(
    summary: PlaybackStatsRepository.PlaybackStatsSummary,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        val title = when (summary.range) {
            com.twilighty.lmusic.data.stats.StatsTimeRange.DAY -> "Today"
            com.twilighty.lmusic.data.stats.StatsTimeRange.WEEK -> "This week"
            com.twilighty.lmusic.data.stats.StatsTimeRange.MONTH -> "This month"
            com.twilighty.lmusic.data.stats.StatsTimeRange.YEAR -> "This year"
            else -> "Your listening"
        }
        NbSectionHeader(title = title, actionLabel = "Stats", onAction = onOpenStats)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NbStatTile(
                label = "Listening",
                value = formatListeningDurationCompact(summary.totalDurationMs),
                supporting = "${summary.activeDays} active days",
                modifier = Modifier.weight(1f),
                onClick = onOpenStats
            )
            NbStatTile(
                label = "Plays",
                value = summary.totalPlayCount.toString(),
                supporting = "${summary.uniqueSongs} songs",
                modifier = Modifier.weight(1f),
                onClick = onOpenStats
            )
        }
        val topArtist = summary.topArtists.firstOrNull()
        val topSong = summary.topSongs.firstOrNull()
        if (topArtist != null || topSong != null) {
            Spacer(Modifier.height(12.dp))
            NbCard(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                if (topArtist != null) {
                    NbOverline("Top artist")
                    Text(topArtist.artist, style = NbType.h3, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${topArtist.playCount} plays", style = NbType.meta, color = Nb.textMuted)
                }
                if (topArtist != null && topSong != null) {
                    Spacer(Modifier.height(12.dp))
                    NbDivider()
                    Spacer(Modifier.height(12.dp))
                }
                if (topSong != null) {
                    NbOverline("On repeat")
                    Text(topSong.title, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${topSong.artist} · ${topSong.playCount} plays", style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

