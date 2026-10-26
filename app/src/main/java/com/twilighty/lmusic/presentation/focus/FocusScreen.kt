package com.twilighty.lmusic.presentation.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.focus.FocusConfig
import com.twilighty.lmusic.data.focus.FocusMusicSource
import com.twilighty.lmusic.data.focus.FocusPhase
import com.twilighty.lmusic.data.focus.FocusService
import com.twilighty.lmusic.data.focus.FocusTimerState
import com.twilighty.lmusic.data.model.Song
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCallout
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbChip
import com.twilighty.lmusic.presentation.nb.NbDialog
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbIconButtonStyle
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbSectionHeader
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbStatTile
import com.twilighty.lmusic.presentation.nb.NbTopBar
import com.twilighty.lmusic.presentation.nb.NbType
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale

/**
 * Focus sessions: a calm timer with optional music during focus rounds and a chime at each
 * phase change. [onPlaySongs] plays a song list through the main player (for the Focus mix and
 * the instrumental fallback).
 */
@Composable
fun FocusScreen(
    onBack: () -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val musicSource by viewModel.musicSource.collectAsStateWithLifecycle()
    val sessionMusic by viewModel.sessionMusic.collectAsStateWithLifecycle()
    val smartEnabled by viewModel.smartEnabled.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val exactAlarmNeeded by viewModel.exactAlarmNeeded.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val todayMinutes by viewModel.todayMinutes.collectAsStateWithLifecycle()
    val todayRounds by viewModel.todayRounds.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()

    DisposableEffect(viewModel, onPlaySongs) {
        val player: (List<Song>) -> Unit = { onPlaySongs(it) }
        viewModel.attachSongPlayer(player)
        onDispose { viewModel.attachSongPlayer(null) }
    }

    // Redraw once a second only while this screen shows a running timer.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.isRunning, timer.phaseEndsAt) {
        now = System.currentTimeMillis()
        while (timer.isRunning) {
            delay(1000L - (System.currentTimeMillis() % 1000L))
            now = System.currentTimeMillis()
        }
    }

    var customOpen by remember { mutableStateOf(false) }
    val active = timer.phase.isActive
    val batteryTip = com.twilighty.lmusic.presentation.nb.rememberBatteryTipState()

    Box(Modifier.fillMaxSize().background(Nb.canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = "Focus",
                    kicker = when {
                        timer.phase == FocusPhase.Done -> "Session done"
                        active -> if (timer.isPaused) "Paused" else "In a session"
                        else -> "Calm, timed rounds"
                    },
                    navigationIcon = {
                        NbIconButton(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    },
                )
            }
            if (batteryTip.visible) {
                item(key = "battery_tip") {
                    com.twilighty.lmusic.presentation.nb.BatteryOptimizationTip(batteryTip, Modifier.padding(horizontal = 16.dp))
                }
            }
            notice?.let { text ->
                item(key = "notice") {
                    NbCallout(
                        boldText = "Heads up.",
                        text = text,
                        actionLabel = "OK",
                        onAction = viewModel::dismissNotice,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            if (exactAlarmNeeded && active) {
                item(key = "exact_alarm") {
                    NbCallout(
                        boldText = "Breaks may start late.",
                        text = "Allow precise alarms so breaks start on time",
                        actionLabel = "Allow",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            viewModel.dismissExactAlarmNotice()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            item(key = "ring") {
                FocusRing(
                    timer = timer,
                    config = config,
                    now = now,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            item(key = "dots") {
                val rounds = if (active || timer.phase == FocusPhase.Done) timer.config.rounds else config.rounds
                RoundDots(
                    rounds = rounds,
                    completed = if (active || timer.phase == FocusPhase.Done) timer.completedRounds else 0,
                    current = if (timer.phase == FocusPhase.Focus) timer.round else 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "controls") {
                Controls(
                    timer = timer,
                    onStart = viewModel::start,
                    onToggle = viewModel::togglePause,
                    onSkip = viewModel::skip,
                    onStop = viewModel::stop,
                    onAgain = viewModel::reset,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (active) {
                item(key = "playing") {
                    Text(
                        text = when (sessionMusic) {
                            FocusMusicSource.Silence -> "No music, just the timer"
                            FocusMusicSource.Lofi -> "Lofi plays during focus and softens in breaks"
                            FocusMusicSource.Noise -> "Noise plays during focus"
                            else -> "${sessionMusic.label} plays during focus and pauses in breaks"
                        },
                        style = NbType.meta,
                        color = Nb.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                }
            } else {
                item(key = "settings") {
                    SettingsCard(
                        config = config,
                        musicSource = musicSource,
                        smartEnabled = smartEnabled,
                        onConfig = viewModel::updateConfig,
                        onMusic = viewModel::setMusicSource,
                        onCustom = { customOpen = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            item(key = "noise") {
                com.twilighty.lmusic.presentation.noise.NoiseSection(
                    showFocusOptions = (if (active) sessionMusic else musicSource) == FocusMusicSource.Noise,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "today") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NbSectionHeader(title = "Your focus")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbStatTile(
                            label = "Today",
                            value = "$todayMinutes min",
                            supporting = if (todayRounds == 1) "1 round" else "$todayRounds rounds",
                            modifier = Modifier.weight(1f)
                        )
                        NbStatTile(
                            label = "This week",
                            value = "${week.sumOf { it.minutes }} min",
                            supporting = "${week.count { it.minutes > 0 }} of 7 days",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    WeekChart(week)
                }
            }
        }
    }

    if (customOpen) {
        CustomLengthDialog(
            initial = config.focusMinutes,
            onDismiss = { customOpen = false },
            onSave = { minutes ->
                viewModel.updateConfig { it.copy(focusMinutes = minutes) }
                customOpen = false
            }
        )
    }
}

// ---------------------------------------------------------------- ring

@Composable
private fun FocusRing(timer: FocusTimerState, config: FocusConfig, now: Long, modifier: Modifier = Modifier) {
    val active = timer.phase.isActive
    val remaining = if (active) timer.remainingMs(now) else config.focusMinutes * 60_000L
    val progress by animateFloatAsState(
        targetValue = when {
            active -> timer.progress(now)
            timer.phase == FocusPhase.Done -> 1f
            else -> 0f
        },
        animationSpec = tween(900),
        label = "focusRing"
    )
    val ringColor = if (timer.phase.isBreak) Nb.successText else Nb.ink
    val track = Nb.emphasis
    val phaseLabel = when {
        timer.phase == FocusPhase.Done -> "Done"
        active -> timer.phase.label
        else -> "Ready"
    }
    val roundLabel = when {
        timer.phase == FocusPhase.Done -> "${timer.completedRounds} of ${timer.config.rounds} rounds"
        active -> "Round ${timer.round} of ${timer.config.rounds}"
        else -> "${config.rounds} ${if (config.rounds == 1) "round" else "rounds"}"
    }
    val clock = if (timer.phase == FocusPhase.Done) "${timer.completedRounds * timer.config.focusMinutes} min"
    else FocusService.formatClock(remaining)

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(280.dp)
                .semantics { contentDescription = "$phaseLabel, $clock left, $roundLabel" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (progress > 0f) {
                    drawArc(
                        ringColor, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NbOverline(phaseLabel, color = if (timer.phase.isBreak) Nb.successText else Nb.accentText)
                Text(
                    text = clock,
                    style = NbType.display(if (clock.length > 5) 48.sp else 64.sp)
                        .copy(fontFeatureSettings = "tnum"),
                    color = if (timer.isPaused) Nb.textMuted else Nb.text,
                    maxLines = 1
                )
                Text(roundLabel, style = NbType.meta, color = Nb.textMuted)
            }
        }
    }
}

@Composable
private fun RoundDots(rounds: Int, completed: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        for (i in 1..rounds) {
            val done = i <= completed
            val isCurrent = i == current
            Box(
                Modifier
                    .size(if (isCurrent) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (done) Nb.ink else if (isCurrent) Nb.accentSoft else Nb.emphasis)
                    .then(if (isCurrent) Modifier.border(2.dp, Nb.ink, CircleShape) else Modifier)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun Controls(
    timer: FocusTimerState,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        when {
            timer.phase == FocusPhase.Done -> NbButton(
                text = "Start again", onClick = onAgain, style = NbButtonStyle.Accent, size = NbButtonSize.Large
            )
            timer.phase.isActive -> {
                NbIconButton(
                    imageVector = Icons.Rounded.Close, contentDescription = "Stop session", onClick = onStop,
                    style = NbIconButtonStyle.Outline, size = 52.dp
                )
                NbButton(
                    text = if (timer.isRunning) "Pause" else "Resume",
                    onClick = onToggle,
                    style = NbButtonStyle.Primary,
                    size = NbButtonSize.Large,
                    icon = rememberVectorPainter(if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow),
                    modifier = Modifier.width(160.dp)
                )
                NbIconButton(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = if (timer.phase.isBreak) "Skip break" else "Skip to break",
                    onClick = onSkip,
                    style = NbIconButtonStyle.Outline, size = 52.dp
                )
            }
            else -> NbButton(
                text = "Start focus", onClick = onStart, style = NbButtonStyle.Accent, size = NbButtonSize.Large,
                icon = rememberVectorPainter(Icons.Rounded.PlayArrow),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------- settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(
    config: FocusConfig,
    musicSource: FocusMusicSource,
    smartEnabled: Boolean,
    onConfig: ((FocusConfig) -> FocusConfig) -> Unit,
    onMusic: (FocusMusicSource) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NbCard(modifier = modifier.fillMaxWidth()) {
        NbOverline("Focus length")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val isPreset = config.focusMinutes in FocusConfig.FOCUS_PRESETS
            FocusConfig.FOCUS_PRESETS.forEach { m ->
                NbChip(text = "$m min", selected = config.focusMinutes == m, onClick = { onConfig { it.copy(focusMinutes = m) } })
            }
            NbChip(
                text = if (isPreset) "Custom" else "${config.focusMinutes} min",
                selected = !isPreset,
                onClick = onCustom
            )
        }
        Spacer(Modifier.height(8.dp))
        NbDivider()
        Stepper("Short break", "${config.shortBreakMinutes} min",
            onMinus = { onConfig { it.copy(shortBreakMinutes = it.shortBreakMinutes - 1) } },
            onPlus = { onConfig { it.copy(shortBreakMinutes = it.shortBreakMinutes + 1) } },
            minusEnabled = config.shortBreakMinutes > FocusConfig.MIN_MINUTES,
            plusEnabled = config.shortBreakMinutes < FocusConfig.MAX_BREAK_MINUTES)
        NbDivider()
        Stepper("Long break", "${config.longBreakMinutes} min · every ${config.longBreakEvery} rounds",
            onMinus = { onConfig { it.copy(longBreakMinutes = it.longBreakMinutes - 5) } },
            onPlus = { onConfig { it.copy(longBreakMinutes = it.longBreakMinutes + 5) } },
            minusEnabled = config.longBreakMinutes > 5,
            plusEnabled = config.longBreakMinutes < FocusConfig.MAX_BREAK_MINUTES)
        NbDivider()
        Stepper("Rounds", "${config.rounds}",
            onMinus = { onConfig { it.copy(rounds = it.rounds - 1) } },
            onPlus = { onConfig { it.copy(rounds = it.rounds + 1) } },
            minusEnabled = config.rounds > FocusConfig.MIN_ROUNDS,
            plusEnabled = config.rounds < FocusConfig.MAX_ROUNDS)
        NbDivider()
        Spacer(Modifier.height(14.dp))
        NbOverline("Music during focus")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusMusicSource.entries
                .filter { it != FocusMusicSource.Smart || smartEnabled }
                .forEach { source ->
                    NbChip(text = source.label, selected = musicSource == source, onClick = { onMusic(source) })
                }
        }
        Spacer(Modifier.height(8.dp))
        Text(musicSource.blurb, style = NbType.caption, color = Nb.textMuted)
    }
}

@Composable
private fun Stepper(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title, color = Nb.text)
            Text(value, style = NbType.meta, color = Nb.textMuted)
        }
        NbIconButton(imageVector = Icons.Rounded.Remove, contentDescription = "Less $title".lowercase().replaceFirstChar { it.uppercase() },
            onClick = onMinus, style = NbIconButtonStyle.Tonal, size = 36.dp, iconSize = 18.dp, enabled = minusEnabled)
        NbIconButton(imageVector = Icons.Rounded.Add, contentDescription = "More $title".lowercase().replaceFirstChar { it.uppercase() },
            onClick = onPlus, style = NbIconButtonStyle.Tonal, size = 36.dp, iconSize = 18.dp, enabled = plusEnabled)
    }
}

@Composable
private fun CustomLengthDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf(initial.toFloat().coerceIn(5f, 120f)) }
    NbDialog(
        onDismissRequest = onDismiss,
        title = "Focus length",
        actions = {
            NbButton(text = "Cancel", onClick = onDismiss, style = NbButtonStyle.Quiet)
            NbButton(text = "Save", onClick = { onSave(minutes.toInt()) })
        }
    ) {
        Text("${minutes.toInt()} min", style = NbType.h2, color = Nb.text)
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = 5f..120f,
            steps = 22,
            colors = SliderDefaults.colors(
                thumbColor = Nb.ink,
                activeTrackColor = Nb.ink,
                inactiveTrackColor = Nb.emphasis,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            )
        )
    }
}

// ---------------------------------------------------------------- 7-day chart

@Composable
private fun WeekChart(week: List<FocusDay>) {
    if (week.isEmpty()) return
    val max = (week.maxOf { it.minutes }).coerceAtLeast(25)
    NbCard(modifier = Modifier.fillMaxWidth()) {
        Text("Last 7 days", style = NbType.label, color = Nb.text)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEachIndexed { index, day ->
                val isToday = index == week.lastIndex
                val fraction = day.minutes.toFloat() / max
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics { contentDescription = "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${day.minutes} minutes" },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (day.minutes > 0) fraction.coerceIn(0.06f, 1f) else 0.04f)
                            .clip(NbShapes.sm)
                            .background(if (day.minutes == 0) Nb.emphasis else if (isToday) Nb.ink else Nb.borderStrong)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEachIndexed { index, day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = NbType.caption,
                    color = if (index == week.lastIndex) Nb.text else Nb.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
