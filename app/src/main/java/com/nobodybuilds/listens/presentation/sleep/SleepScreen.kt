package com.nobodybuilds.listens.presentation.sleep

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.sleep.SleepSession
import com.nobodybuilds.listens.data.sleep.SleepSource
import com.nobodybuilds.listens.presentation.noise.NoiseSection
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCallout
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbIconButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbSegmented
import com.nobodybuilds.listens.presentation.nb.NbTopBar
import com.nobodybuilds.listens.presentation.nb.NbType
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val TIMER_CHOICES = listOf(15, 30, 45, 60, 90)
private val FADE_CHOICES = listOf(5, 10, 15, 20)

/**
 * The Sleep space: noise, lofi or a calm mix with a sleep timer whose volume lowers slowly over
 * the last minutes. "Dim" shows a near-black screen (tap to wake). Playback carries on with the
 * screen off. [onPlaySongs] plays a list through the main player (calm mix).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepScreen(
    onBack: () -> Unit,
    onPlaySongs: (List<Song>) -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    var customOpen by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }

    // Refresh the countdown only while this screen shows a running timer.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val endsAt = session?.endsAt
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(if (dimmed) 30_000L else 1_000L)
        }
    }
    // The session ended (timer ran out): wake the screen state so it may turn off normally.
    LaunchedEffect(session) { if (session == null) dimmed = false }

    Box(Modifier.fillMaxSize().background(Nb.canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = "Sleep",
                    kicker = if (session != null) "Winding down" else "Drift off slowly",
                    navigationIcon = {
                        NbIconButton(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    },
                )
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
            val running = session
            if (running != null) {
                item(key = "running") {
                    RunningCard(
                        session = running,
                        now = now,
                        fadeMinutes = settings.fadeMinutes,
                        preparing = preparing,
                        onDim = { dimmed = true },
                        onExtend = { viewModel.extend(10) },
                        onStop = viewModel::stop,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                item(key = "setup") {
                    NbCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        NbOverline("Sound")
                        Spacer(Modifier.height(10.dp))
                        NbSegmented(
                            options = SleepSource.entries.map { it.label },
                            selectedIndex = settings.source.ordinal,
                            onSelect = { viewModel.setSource(SleepSource.entries[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(settings.source.blurb, style = NbType.caption, color = Nb.textMuted)
                        Spacer(Modifier.height(16.dp))
                        NbOverline("Sleep timer")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIMER_CHOICES.forEach { m ->
                                NbChip(text = "$m min", selected = settings.minutes == m, onClick = { viewModel.setMinutes(m) })
                            }
                            val custom = settings.minutes !in TIMER_CHOICES
                            NbChip(text = if (custom) "${settings.minutes} min" else "Custom", selected = custom, onClick = { customOpen = true })
                        }
                        Spacer(Modifier.height(16.dp))
                        NbOverline("Lower the volume over the last")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FADE_CHOICES.forEach { m ->
                                NbChip(text = "$m min", selected = settings.fadeMinutes == m, onClick = { viewModel.setFadeMinutes(m) })
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        NbButton(
                            text = "Start",
                            onClick = { viewModel.start(onPlaySongs) },
                            style = NbButtonStyle.Accent,
                            size = NbButtonSize.Large,
                            icon = rememberVectorPainter(Icons.Rounded.PlayArrow),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (settings.source == SleepSource.Noise) {
                item(key = "noise") {
                    NoiseSection(showPlayButton = false, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "tip") {
                Text(
                    "Keeps playing with the screen off. Use Dim to keep a dark clock on the nightstand.",
                    style = NbType.caption,
                    color = Nb.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
        }
    }

    if (customOpen) {
        CustomTimerDialog(
            initial = settings.minutes,
            onDismiss = { customOpen = false },
            onSave = { viewModel.setMinutes(it); customOpen = false },
        )
    }
    if (dimmed && session != null) {
        DimScreen(session = session!!, now = now, onWake = { dimmed = false })
    }
}

private fun remainingText(endsAt: Long?, now: Long): String {
    if (endsAt == null) return "Starting…"
    val ms = (endsAt - now).coerceAtLeast(0L)
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun RunningCard(
    session: SleepSession,
    now: Long,
    fadeMinutes: Int,
    preparing: Boolean,
    onDim: () -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val endsAt = session.endsAt
    val fading = endsAt != null && endsAt - now <= fadeMinutes * 60_000L
    NbCard(modifier = modifier.fillMaxWidth()) {
        NbOverline(session.source.label)
        Spacer(Modifier.height(6.dp))
        val clock = if (preparing) "Finding calm songs…" else remainingText(endsAt, now)
        Text(
            clock,
            style = NbType.display(if (clock.length > 7) 32.sp else 56.sp).copy(fontFeatureSettings = "tnum"),
            color = Nb.text,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "$clock left" }
        )
        Text(
            when {
                endsAt == null -> "Setting the timer"
                fading -> "Getting quieter…"
                else -> "Then the volume lowers over $fadeMinutes min"
            },
            style = NbType.meta,
            color = Nb.textMuted
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NbButton(
                text = "Dim",
                onClick = onDim,
                style = NbButtonStyle.Primary,
                icon = rememberVectorPainter(Icons.Rounded.Bedtime),
                modifier = Modifier.weight(1f)
            )
            NbButton(text = "+10 min", onClick = onExtend, style = NbButtonStyle.Secondary, enabled = endsAt != null)
            NbIconButton(
                imageVector = Icons.Rounded.Close, contentDescription = "Stop", onClick = onStop,
                style = NbIconButtonStyle.Outline, size = 44.dp
            )
        }
    }
}

/** Near-black, minimal: a dim clock and the time left. Tap anywhere to wake. */
@Composable
private fun DimScreen(session: SleepSession, now: Long, onWake: () -> Unit) {
    Dialog(
        onDismissRequest = onWake,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w ->
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                w.attributes = w.attributes.apply { screenBrightness = 0.01f; dimAmount = 0f }
            }
            onDispose {
                window?.let { w ->
                    w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
            }
        }
        val clock = remember(now / 60_000L) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake)
                .semantics { contentDescription = "Dimmed. Tap to wake" },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clock, style = NbType.display(56.sp), color = Color(0xFF3A3A3C))
                Spacer(Modifier.height(8.dp))
                val left = session.endsAt?.let { ((it - now).coerceAtLeast(0L) + 59_999L) / 60_000L }
                Text(
                    if (left != null) "${session.source.label} · $left min left" else session.source.label,
                    style = NbType.meta,
                    color = Color(0xFF2C2C2E)
                )
                Spacer(Modifier.height(40.dp))
                Text("Tap to wake", style = NbType.caption, color = Color(0xFF242426))
            }
        }
    }
}

@Composable
private fun CustomTimerDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf(initial.toFloat().coerceIn(5f, 180f)) }
    NbDialog(
        onDismissRequest = onDismiss,
        title = "Sleep timer",
        actions = {
            NbButton(text = "Cancel", onClick = onDismiss, style = NbButtonStyle.Quiet)
            NbButton(text = "Save", onClick = { onSave(minutes.toInt()) })
        }
    ) {
        Text("${minutes.toInt()} min", style = NbType.h2, color = Nb.text)
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = 5f..180f,
            steps = 34,
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
