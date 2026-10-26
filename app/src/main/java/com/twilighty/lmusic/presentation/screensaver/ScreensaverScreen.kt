package com.twilighty.lmusic.presentation.screensaver

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.screensaver.BurnIn
import com.twilighty.lmusic.data.screensaver.DriftOffset
import com.twilighty.lmusic.data.screensaver.ScreensaverClockStyle
import com.twilighty.lmusic.data.screensaver.ScreensaverExit
import com.twilighty.lmusic.data.screensaver.ScreensaverSettings
import com.twilighty.lmusic.data.screensaver.WeatherIcon
import com.twilighty.lmusic.data.screensaver.WeatherSnapshot
import com.twilighty.lmusic.data.screensaver.screensaverProgress
import com.twilighty.lmusic.ui.theme.NbBody
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// OLED palette: pure black canvas, low-luminance greys only (no white areas).
private val Black = androidx.compose.ui.graphics.Color(0xFF000000)
private val ClockGrey = androidx.compose.ui.graphics.Color(0xFF8E8E93)
private val TextGrey = androidx.compose.ui.graphics.Color(0xFF6E6E73)
private val DimGrey = androidx.compose.ui.graphics.Color(0xFF4A4A4E)
private val TrackGrey = androidx.compose.ui.graphics.Color(0xFF1A1A1C)
private val LineGrey = androidx.compose.ui.graphics.Color(0xFF3F3F44)

@OptIn(ExperimentalTextApi::class)
private val ThinDisplay = FontFamily(Font(R.font.gilroy_light, FontWeight.ExtraLight))

private fun animationsOff(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/**
 * Screensaver with its state, burn-in protection and auto exit.
 * @param autoExit close when the charger is unplugged or music stays stopped for 10 min
 *   (the Activity; the system Dream handles that itself).
 */
@Composable
fun ScreensaverRoot(
    session: ScreensaverSession,
    autoExit: Boolean,
    onExit: () -> Unit,
    handleBack: Boolean = true,
) {
    val context = LocalContext.current
    val charging by session.charging.collectAsState()
    val nowPlaying by session.nowPlaying.collectAsState()
    val settings by session.settings.collectAsState()
    val weather by session.weather.collectAsState()
    val reduceMotion = remember { animationsOff(context) }

    // 1 s tick while visible (clock, progress line). Stops with the composition.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            elapsed = SystemClock.elapsedRealtime()
            delay(1_000L - (nowMs % 1_000L))
        }
    }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Pixel shift: random walk inside ±8 dp, one step per interval.
    var drift by remember { mutableStateOf(DriftOffset(0f, 0f)) }
    val random = remember { Random(System.nanoTime()) }
    LaunchedEffect(settings.shiftIntervalSec) {
        while (true) {
            delay(settings.shiftIntervalSec * 1_000L)
            drift = BurnIn.nextDrift(drift, random)
        }
    }

    // Auto exit.
    val playing = nowPlaying?.isPlaying == true
    var wasCharging by remember { mutableStateOf(charging) }
    if (charging && !wasCharging) wasCharging = true
    var pausedSince by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playing) { pausedSince = if (playing) null else SystemClock.elapsedRealtime() }
    LaunchedEffect(charging, wasCharging, pausedSince, elapsed, autoExit) {
        if (!autoExit) return@LaunchedEffect
        if (ScreensaverExit.reason(charging, wasCharging, pausedSince, elapsed) != null) onExit()
    }

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, lastInteraction) {
        if (controlsVisible) { delay(6_000L); controlsVisible = false }
    }
    if (handleBack) BackHandler { onExit() }

    val idleMs = elapsed - lastInteraction
    val level = BurnIn.brightnessLevel(idleMs) * BurnIn.breathing(elapsed, reduceMotion = reduceMotion) *
        (if (playing) 1f else 0.8f)

    ScreensaverContent(
        settings = settings,
        nowMs = nowMs,
        nowPlaying = nowPlaying,
        positionMs = nowPlaying?.positionAt(elapsed) ?: 0L,
        weather = weather,
        drift = drift,
        swapped = BurnIn.isSwapped(elapsed - startedAt),
        level = level,
        reduceMotion = reduceMotion,
        controlsVisible = controlsVisible,
        onTap = {
            lastInteraction = SystemClock.elapsedRealtime()
            controlsVisible = !controlsVisible
        },
        onPlayPause = { lastInteraction = SystemClock.elapsedRealtime(); session.playPause() },
        onNext = { lastInteraction = SystemClock.elapsedRealtime(); session.next() },
        onPrevious = { lastInteraction = SystemClock.elapsedRealtime(); session.previous() },
        onExit = onExit,
    )
}

@Composable
fun ScreensaverContent(
    settings: ScreensaverSettings,
    nowMs: Long,
    nowPlaying: ScreensaverNowPlaying?,
    positionMs: Long,
    weather: WeatherSnapshot?,
    drift: DriftOffset,
    swapped: Boolean,
    level: Float,
    reduceMotion: Boolean,
    controlsVisible: Boolean,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val driftSpec = if (reduceMotion) snap<Float>() else tween<Float>(durationMillis = 4_000)
    val dx by animateFloatAsState(drift.x, driftSpec, label = "driftX")
    val dy by animateFloatAsState(drift.y, driftSpec, label = "driftY")
    val levelAnim by animateFloatAsState(level, if (reduceMotion) snap() else tween(1_000), label = "level")
    val is24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val timeText = remember(nowMs / 60_000L, is24h) {
        SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(nowMs))
    }
    val dateText = remember(nowMs / 60_000L) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(nowMs))
    }
    val large = settings.clockStyle == ScreensaverClockStyle.LARGE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 32.dp)
                .graphicsLayer {
                    translationX = dx * density
                    translationY = dy * density
                    alpha = levelAnim
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            val clock: @Composable () -> Unit = {
                ClockBlock(timeText, dateText, weather.takeIf { settings.showWeather }, large)
            }
            val music: @Composable () -> Unit = {
                NowPlayingBlock(nowPlaying, positionMs, settings.showArtwork && large)
            }
            Box(Modifier.weight(1.25f).fillMaxHeight(), contentAlignment = if (swapped) Alignment.CenterEnd else Alignment.CenterStart) {
                if (swapped) music() else clock()
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = if (swapped) Alignment.CenterStart else Alignment.CenterEnd) {
                if (swapped) clock() else music()
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(if (reduceMotion) snap() else tween(200)),
            exit = fadeOut(if (reduceMotion) snap() else tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
                ControlButton(
                    if (nowPlaying?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (nowPlaying?.isPlaying == true) "Pause" else "Play",
                    onPlayPause,
                    big = true
                )
                ControlButton(Icons.Rounded.SkipNext, "Next", onNext)
                Spacer(Modifier.width(24.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, LineGrey, RoundedCornerShape(999.dp))
                        .clickable(onClick = onExit)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = TextGrey, modifier = Modifier.size(18.dp))
                    Text("Exit", style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Medium, fontSize = 15.sp), color = TextGrey)
                }
            }
        }
    }
}

@Composable
private fun ClockBlock(time: String, date: String, weather: WeatherSnapshot?, large: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (large) 4.dp else 6.dp)) {
        Text(
            text = time,
            style = if (large) {
                TextStyle(fontFamily = ThinDisplay, fontWeight = FontWeight.ExtraLight, fontSize = 128.sp, lineHeight = 128.sp, letterSpacing = (-0.02).em)
            } else {
                TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 76.sp)
            },
            color = ClockGrey,
            maxLines = 1
        )
        Text(
            text = date,
            style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = if (large) 20.sp else 16.sp),
            color = TextGrey,
            maxLines = 1
        )
        if (weather != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(weatherIcon(weather.condition.icon), contentDescription = null, tint = DimGrey, modifier = Modifier.size(20.dp))
                val parts = listOfNotNull(weather.temperatureText, weather.condition.label, weather.city)
                Text(
                    parts.joinToString("  ·  "),
                    style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 16.sp),
                    color = TextGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBlock(np: ScreensaverNowPlaying?, positionMs: Long, showArt: Boolean) {
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (np == null) {
            Text("Nothing playing", style = TextStyle(fontFamily = NbBody, fontSize = 16.sp), color = DimGrey)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showArt && np.artwork != null) {
                // Small and dimmed (~50 %) so the cover never becomes a bright static block.
                val dim = remember {
                    ColorMatrix().apply { setToScale(0.5f, 0.5f, 0.5f, 1f) }
                }
                AsyncImage(
                    model = np.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(dim),
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    np.title,
                    style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Medium, fontSize = 18.sp),
                    color = TextGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (np.isPlaying) np.artist else "Music paused",
                    style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 15.sp),
                    color = DimGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Thin progress line.
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(TrackGrey)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(screensaverProgress(positionMs, np.durationMs))
                    .background(LineGrey)
            )
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit, big: Boolean = false) {
    val size = if (big) 60.dp else 48.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, LineGrey, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = TextGrey, modifier = Modifier.size(if (big) 30.dp else 24.dp))
    }
}

private fun weatherIcon(icon: WeatherIcon): ImageVector = when (icon) {
    WeatherIcon.CLEAR -> Icons.Rounded.WbSunny
    WeatherIcon.CLEAR_NIGHT -> Icons.Rounded.NightsStay
    WeatherIcon.PARTLY_CLOUDY -> Icons.Rounded.WbCloudy
    WeatherIcon.CLOUDY -> Icons.Rounded.Cloud
    WeatherIcon.FOG -> Icons.Rounded.Dehaze
    WeatherIcon.DRIZZLE -> Icons.Rounded.Grain
    WeatherIcon.RAIN -> Icons.Rounded.Umbrella
    WeatherIcon.SNOW -> Icons.Rounded.AcUnit
    WeatherIcon.THUNDER -> Icons.Rounded.FlashOn
    WeatherIcon.UNKNOWN -> Icons.Rounded.Cloud
}
