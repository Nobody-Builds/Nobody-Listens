package com.twilighty.lmusic.presentation.recap

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.R
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbEmptyState
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbType
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// ---------------------------------------------------------------- Entry card on the Stats screen

@Composable
fun RecapEntryCard(onOpen: (RecapPeriod) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.xl)
            .background(Nb.tintPeach)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.recap_overline).uppercase(), style = NbType.overline, color = Nb.accentText)
        Text(stringResource(R.string.recap_entry_title), style = NbType.h3, color = Nb.text)
        Text(stringResource(R.string.recap_entry_body), style = NbType.meta, color = Nb.textMuted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NbButton(
                text = stringResource(R.string.recap_weekly),
                onClick = { onOpen(RecapPeriod.WEEK) },
                style = NbButtonStyle.Primary,
                size = NbButtonSize.Small
            )
            NbButton(
                text = stringResource(R.string.recap_monthly),
                onClick = { onOpen(RecapPeriod.MONTH) },
                style = NbButtonStyle.Secondary,
                size = NbButtonSize.Small
            )
        }
    }
}

// ---------------------------------------------------------------- Full-screen recap

@Composable
fun RecapScreen(
    period: RecapPeriod,
    onClose: () -> Unit,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Nb.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (val s = state) {
            RecapUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Nb.ink, strokeWidth = 2.5.dp)
            RecapUiState.Failed -> EmptyRecap(period, onClose, failed = true)
            is RecapUiState.Ready -> if (s.data.hasListening) RecapPager(s.data, onClose) else EmptyRecap(period, onClose, failed = false)
        }
    }
}

@Composable
private fun EmptyRecap(period: RecapPeriod, onClose: () -> Unit, failed: Boolean) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            NbIconButton(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.recap_close), onClick = onClose)
        }
        Spacer(Modifier.weight(1f))
        NbEmptyState(
            title = stringResource(if (failed) R.string.recap_failed_title else R.string.recap_empty_title),
            body = stringResource(
                if (failed) R.string.recap_failed_body
                else if (period == RecapPeriod.WEEK) R.string.recap_empty_body_week else R.string.recap_empty_body_month
            )
        )
        Spacer(Modifier.weight(1f))
    }
}

private enum class RecapPage { TIME, PLAYS, TOP_SONG, TOP_SONGS, TOP_ARTIST, TOP_ALBUM, RHYTHM, MOODS, STREAK, DISCOVERY, SKIPS, SUMMARY }

private fun pagesFor(data: RecapData): List<RecapPage> = buildList {
    add(RecapPage.TIME)
    add(RecapPage.PLAYS)
    if (data.topSongs.isNotEmpty()) {
        add(RecapPage.TOP_SONG)
        if (data.topSongs.size > 1) add(RecapPage.TOP_SONGS)
    }
    if (data.topArtists.isNotEmpty()) add(RecapPage.TOP_ARTIST)
    if (data.topAlbum != null) add(RecapPage.TOP_ALBUM)
    if (data.busiestDay != null || data.peakHour != null) add(RecapPage.RHYTHM)
    if (!data.moods.isNullOrEmpty()) add(RecapPage.MOODS)
    add(RecapPage.STREAK)
    add(RecapPage.DISCOVERY)
    if (data.startedCount > 0) add(RecapPage.SKIPS)
    add(RecapPage.SUMMARY)
}

@Composable
private fun RecapPager(data: RecapData, onClose: () -> Unit) {
    val pages = pagesFor(data)
    val pagerState = rememberPagerState { pages.size }
    com.twilighty.lmusic.presentation.haptics.HapticPagerEffect(
        pagerState = pagerState,
        onSettledPage = { page, haptics -> if (page == pages.lastIndex) haptics.celebration() }
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.recap_share_chooser)

    Column(Modifier.fillMaxSize()) {
        // Story progress + close
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(NbShapes.pill)
                        .background(if (index <= pagerState.currentPage) Nb.ink else Nb.emphasis)
                )
            }
            Spacer(Modifier.width(4.dp))
            NbIconButton(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.recap_close), onClick = onClose)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { index ->
            val layer = rememberGraphicsLayer()
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                ) {
                    RecapCard(page = pages[index], data = data)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    NbButton(
                        text = stringResource(R.string.recap_share),
                        icon = rememberVectorPainter(Icons.Rounded.IosShare),
                        style = NbButtonStyle.Secondary,
                        size = NbButtonSize.Medium,
                        onClick = {
                            scope.launch { shareLayer(context, layer, "recap_${data.period.routeValue}_${pages[index].name.lowercase()}", shareTitle) }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun shareLayer(context: Context, layer: GraphicsLayer, name: String, chooserTitle: String) {
    runCatching {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "recaps").apply { mkdirs() }
            File(dir, "$name.png").also { out ->
                FileOutputStream(out).use { stream ->
                    // Hardware bitmaps can't be compressed directly.
                    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
                    soft.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Recap: share failed") }
}

// ---------------------------------------------------------------- Cards

private data class CardColors(val background: Color, val text: Color, val muted: Color)

@Composable
private fun colorsFor(page: RecapPage): CardColors = when (page) {
    RecapPage.TIME, RecapPage.SUMMARY -> CardColors(Nb.accent, Nb.onAccent, Nb.onAccent.copy(alpha = 0.78f))
    RecapPage.PLAYS, RecapPage.STREAK -> CardColors(Nb.tintLavender, Nb.text, Nb.textMuted)
    RecapPage.TOP_SONG, RecapPage.TOP_ALBUM -> CardColors(Nb.ink, Nb.onInk, Nb.onInk.copy(alpha = 0.7f))
    RecapPage.TOP_SONGS, RecapPage.SKIPS -> CardColors(Nb.tintPeach, Nb.text, Nb.textMuted)
    RecapPage.TOP_ARTIST, RecapPage.DISCOVERY -> CardColors(Nb.tintMint, Nb.text, Nb.textMuted)
    RecapPage.RHYTHM -> CardColors(Nb.muted, Nb.text, Nb.textMuted)
    RecapPage.MOODS -> CardColors(Nb.tintLavender, Nb.text, Nb.textMuted)
}

@Composable
private fun RecapCard(page: RecapPage, data: RecapData) {
    val colors = colorsFor(page)
    Column(
        Modifier
            .fillMaxSize()
            .clip(NbShapes.xxl)
            .background(colors.background)
            .padding(24.dp)
    ) {
        Text(periodLabel(data).uppercase(), style = NbType.overline, color = colors.muted)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                RecapPage.TIME -> TimeCard(data, colors)
                RecapPage.PLAYS -> PlaysCard(data, colors)
                RecapPage.TOP_SONG -> TopSongCard(data, colors)
                RecapPage.TOP_SONGS -> TopSongsCard(data, colors)
                RecapPage.TOP_ARTIST -> TopArtistCard(data, colors)
                RecapPage.TOP_ALBUM -> TopAlbumCard(data, colors)
                RecapPage.RHYTHM -> RhythmCard(data, colors)
                RecapPage.MOODS -> MoodsCard(data, colors)
                RecapPage.STREAK -> StreakCard(data, colors)
                RecapPage.DISCOVERY -> DiscoveryCard(data, colors)
                RecapPage.SKIPS -> SkipsCard(data, colors)
                RecapPage.SUMMARY -> SummaryCard(data, colors)
            }
        }
        Text(stringResource(R.string.recap_watermark), style = NbType.caption, color = colors.muted)
    }
}

@Composable
private fun periodLabel(data: RecapData): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(data.startMillis).atZone(zone).format(formatter)
    val end = Instant.ofEpochMilli(data.endMillis).atZone(zone).format(formatter)
    val name = stringResource(if (data.period == RecapPeriod.WEEK) R.string.recap_period_week else R.string.recap_period_month)
    return "$name · $start – $end"
}

@Composable
private fun BigNumber(text: String, color: Color, size: Int = 64) {
    Text(text, style = NbType.display(size.sp), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Kicker(text: String, color: Color) {
    Text(text, style = NbType.h3, color = color)
}

@Composable
private fun Body(text: String, color: Color) {
    Text(text, style = NbType.bodyLarge, color = color)
}

@Composable
private fun ColumnScope.TimeCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_time_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    BigNumber(formatDuration(data.totalDurationMs), colors.text, 72)
    Spacer(Modifier.height(8.dp))
    val change = data.changePercent
    Body(
        when {
            change == null -> stringResource(R.string.recap_time_first)
            change > 0 -> stringResource(R.string.recap_time_up, change, previousLabel(data.period))
            change < 0 -> stringResource(R.string.recap_time_down, abs(change), previousLabel(data.period))
            else -> stringResource(R.string.recap_time_same, previousLabel(data.period))
        },
        colors.muted
    )
}

@Composable
private fun previousLabel(period: RecapPeriod): String =
    stringResource(if (period == RecapPeriod.WEEK) R.string.recap_previous_week else R.string.recap_previous_month)

@Composable
private fun ColumnScope.PlaysCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_plays_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    BigNumber(data.plays.toString(), colors.text, 72)
    Body(stringResource(R.string.recap_plays_label), colors.muted)
    Spacer(Modifier.height(20.dp))
    BigNumber(data.uniqueSongs.toString(), colors.text, 48)
    Body(stringResource(R.string.recap_unique_label), colors.muted)
}

@Composable
private fun ColumnScope.TopSongCard(data: RecapData, colors: CardColors) {
    val song = data.topSongs.first()
    Kicker(stringResource(R.string.recap_top_song_kicker), colors.text)
    Spacer(Modifier.height(20.dp))
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        NbArt(model = song.albumArtUri, title = song.title, size = side, shape = NbShapes.xl)
    }
    Spacer(Modifier.height(20.dp))
    Text(song.title, style = NbType.h2, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(song.artist, style = NbType.title, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    Body(stringResource(R.string.recap_song_stat, song.playCount, formatDuration(song.totalDurationMs)), colors.muted)
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String, art: String?, artKey: String, colors: CardColors) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(rank.toString(), style = NbType.h2, color = colors.text, modifier = Modifier.width(26.dp))
        NbArt(model = art, title = artKey, size = 52.dp, shape = NbShapes.md)
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = NbType.meta, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ColumnScope.TopSongsCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_top_songs_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    data.topSongs.forEachIndexed { index, song ->
        RankRow(index + 1, song.title, song.artist, song.albumArtUri, song.title, colors)
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.TopArtistCard(data: RecapData, colors: CardColors) {
    val top = data.topArtists.first()
    Kicker(stringResource(R.string.recap_top_artist_kicker), colors.text)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NbArt(model = data.topArtistArt, title = top.artist, size = 96.dp, shape = androidx.compose.foundation.shape.CircleShape)
        Column(Modifier.weight(1f)) {
            Text(top.artist, style = NbType.h1, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.recap_artist_stat, top.playCount, formatDuration(top.totalDurationMs)), style = NbType.meta, color = colors.muted)
        }
    }
    Spacer(Modifier.weight(1f))
    if (data.topArtists.size > 1) {
        Text(stringResource(R.string.recap_top_artists_label).uppercase(), style = NbType.overline, color = colors.muted)
        Spacer(Modifier.height(4.dp))
        data.topArtists.forEachIndexed { index, artist ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString(), style = NbType.h3, color = colors.text, modifier = Modifier.width(28.dp))
                Text(artist.artist, style = NbType.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatDuration(artist.totalDurationMs), style = NbType.meta, color = colors.muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.TopAlbumCard(data: RecapData, colors: CardColors) {
    val album = data.topAlbum ?: return
    Kicker(stringResource(R.string.recap_top_album_kicker), colors.text)
    Spacer(Modifier.height(20.dp))
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        NbArt(model = album.albumArtUri, title = album.album, size = side, shape = NbShapes.xl)
    }
    Spacer(Modifier.height(20.dp))
    Text(album.album, style = NbType.h2, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Body(stringResource(R.string.recap_album_stat, album.uniqueSongs, formatDuration(album.totalDurationMs)), colors.muted)
}

@Composable
private fun ColumnScope.RhythmCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_rhythm_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    data.busiestDay?.let { day ->
        Text(stringResource(R.string.recap_busiest_day).uppercase(), style = NbType.overline, color = colors.muted)
        BigNumber(day.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())), colors.text, 48)
        Body(
            stringResource(R.string.recap_busiest_day_stat, day.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())), formatDuration(data.busiestDayDurationMs)),
            colors.muted
        )
        Spacer(Modifier.height(24.dp))
    }
    data.peakHour?.let { hour ->
        Text(stringResource(R.string.recap_peak_time).uppercase(), style = NbType.overline, color = colors.muted)
        BigNumber(stringResource(partOfDay(hour)), colors.text, 48)
        Body(stringResource(R.string.recap_peak_hour, formatHour(hour)), colors.muted)
    }
    Spacer(Modifier.weight(1f))
}

private fun partOfDay(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.recap_mornings
    in 12..16 -> R.string.recap_afternoons
    in 17..21 -> R.string.recap_evenings
    else -> R.string.recap_late_nights
}

private fun formatHour(hour: Int): String {
    val h = hour % 12
    return "${if (h == 0) 12 else h} ${if (hour < 12) "AM" else "PM"}"
}

@Composable
private fun ColumnScope.StreakCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_streak_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    BigNumber(data.longestStreakDays.toString(), colors.text, 96)
    Body(
        androidx.compose.ui.res.pluralStringResource(R.plurals.recap_streak_days, data.longestStreakDays, data.longestStreakDays),
        colors.muted
    )
    Spacer(Modifier.height(12.dp))
    Body(stringResource(R.string.recap_active_days, data.activeDays, data.period.days.toInt()), colors.muted)
}

@Composable
private fun ColumnScope.DiscoveryCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_discovery_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    BigNumber(data.newSongCount.toString(), colors.text, 72)
    Body(stringResource(R.string.recap_new_songs), colors.muted)
    Spacer(Modifier.height(20.dp))
    BigNumber(data.newArtistCount.toString(), colors.text, 48)
    Body(stringResource(R.string.recap_new_artists), colors.muted)
}

@Composable
private fun ColumnScope.MoodsCard(data: RecapData, colors: CardColors) {
    val moods = data.moods.orEmpty().entries.sortedByDescending { it.value }.take(5)
    Kicker("Your moods", colors.text)
    Spacer(Modifier.weight(1f))
    moods.firstOrNull()?.let { top ->
        BigNumber(top.key, colors.text, 56)
        Body("${(top.value * 100).roundToInt()}% of your listening", colors.muted)
    }
    Spacer(Modifier.height(20.dp))
    moods.drop(1).forEach { (mood, share) ->
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(mood, style = NbType.title, color = colors.text, modifier = Modifier.weight(1f))
            Text("${(share * 100).roundToInt()}%", style = NbType.title, color = colors.muted)
        }
    }
    data.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
        Spacer(Modifier.height(16.dp))
        Body("Sounds like: " + genres.take(3).joinToString(", "), colors.muted)
    }
}

@Composable
private fun ColumnScope.SkipsCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(R.string.recap_skips_kicker), colors.text)
    Spacer(Modifier.weight(1f))
    BigNumber("${(data.skipRate * 100).roundToInt()}%", colors.text, 96)
    Body(stringResource(R.string.recap_skip_stat, data.skipCount, data.startedCount), colors.muted)
    Spacer(Modifier.height(12.dp))
    Body(
        stringResource(if (data.skipRate < 0.2f) R.string.recap_skip_low else if (data.skipRate < 0.45f) R.string.recap_skip_mid else R.string.recap_skip_high),
        colors.muted
    )
}

@Composable
private fun ColumnScope.SummaryCard(data: RecapData, colors: CardColors) {
    Kicker(stringResource(if (data.period == RecapPeriod.WEEK) R.string.recap_summary_week else R.string.recap_summary_month), colors.text)
    Spacer(Modifier.weight(1f))
    val rows = listOfNotNull(
        stringResource(R.string.recap_sum_time) to formatDuration(data.totalDurationMs),
        stringResource(R.string.recap_sum_plays) to data.plays.toString(),
        stringResource(R.string.recap_sum_songs) to data.uniqueSongs.toString(),
        data.topSongs.firstOrNull()?.let { stringResource(R.string.recap_sum_top_song) to it.title },
        data.topArtists.firstOrNull()?.let { stringResource(R.string.recap_sum_top_artist) to it.artist },
        data.topAlbum?.let { stringResource(R.string.recap_sum_top_album) to it.album },
        stringResource(R.string.recap_sum_streak) to androidx.compose.ui.res.pluralStringResource(R.plurals.recap_streak_days, data.longestStreakDays, data.longestStreakDays),
        stringResource(R.string.recap_sum_new) to data.newSongCount.toString(),
    )
    rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NbType.meta, color = colors.muted, modifier = Modifier.weight(0.45f))
            Text(value, style = NbType.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.55f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.muted.copy(alpha = 0.25f)))
    }
    Spacer(Modifier.weight(1f))
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        totalMinutes > 0 -> "${minutes}m"
        else -> "${ms / 1000}s"
    }
}
