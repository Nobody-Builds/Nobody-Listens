package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.focus.FocusController
import com.nobodybuilds.listens.data.focus.FocusPhase
import com.nobodybuilds.listens.data.focus.FocusRepository
import com.nobodybuilds.listens.data.focus.FocusService
import com.nobodybuilds.listens.data.focus.FocusTimerState
import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LofiFocusWidgetEntryPoint {
    fun focusRepository(): FocusRepository
    fun focusController(): FocusController
    fun lofiController(): LofiController
}

private data class LofiFocusData(
    val lofiPlaying: Boolean,
    val lofiLoaded: Boolean,
    val focus: FocusTimerState,
    val focusMinutes: Int,
)

/** 2x2 Lofi & focus: play/pause lofi, start or pause a focus session, live time left. */
class LofiFocusWidget2x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, LofiFocusWidgetEntryPoint::class.java)
        }.getOrNull()
        val initial = runCatching { ep?.let { load(it) } }
            .onFailure { Timber.w(it, "Lofi & focus widget: load failed") }
            .getOrNull() ?: LofiFocusData(false, false, FocusTimerState(), 25)
        provideContent {
            // Live while the Glance session is open: later controller changes recompose this
            // instead of showing the state read when the session started.
            val focus by produceState(initial.focus) {
                ep?.focusController()?.state?.drop(1)?.collect { value = it }
            }
            val lofi by produceState(initial.lofiPlaying to initial.lofiLoaded) {
                ep?.lofiController()?.state?.drop(1)?.collect { value = it.isPlaying to (it.source != null) }
            }
            val data = initial.copy(lofiPlaying = lofi.first, lofiLoaded = lofi.second, focus = focus)
            GlanceTheme(colors = NbGlanceColors) { Content(context, data) }
        }
    }

    private suspend fun load(ep: LofiFocusWidgetEntryPoint): LofiFocusData {
        val lofi = ep.lofiController()
        // Cheap and async: makes a later "play" use the saved lofi settings in a cold process.
        runCatching { lofi.ensureLoaded() }
        val repo = ep.focusRepository()
        val live = ep.focusController().state.value
        val focus = when {
            live.phase.isActive -> live
            else -> runCatching { repo.loadActive() }.getOrNull()
                ?.takeIf { it.timer.phase.isActive && System.currentTimeMillis() - it.savedAt < STALE_MS }
                ?.timer ?: FocusTimerState()
        }
        val minutes = runCatching { repo.config.first().focusMinutes }.getOrDefault(25)
        val lofiState = lofi.state.value
        return LofiFocusData(lofiState.isPlaying, lofiState.source != null, focus, minutes)
    }

    @Composable
    private fun Content(context: Context, data: LofiFocusData) {
        val focus = data.focus
        val now = System.currentTimeMillis()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(NbWidget.Radius)
                .padding(14.dp)
                .clickable(NbWidget.openApp()),
        ) {
            WidgetOverline(
                when {
                    focus.phase == FocusPhase.Focus -> "Focus · round ${focus.round} of ${focus.config.rounds}"
                    focus.phase.isBreak -> focus.phase.label
                    else -> "Lofi & focus"
                }
            )
            Spacer(GlanceModifier.height(4.dp))
            val remaining = focus.remainingMs(now)
            if (focus.isRunning && remaining > 0L) {
                val rv = RemoteViews(context.packageName, R.layout.widget_focus_chronometer).apply {
                    setChronometer(R.id.focus_chronometer, SystemClock.elapsedRealtime() + remaining, null, true)
                    setChronometerCountDown(R.id.focus_chronometer, true)
                }
                AndroidRemoteViews(remoteViews = rv)
            } else {
                val ms = when {
                    focus.isPaused -> focus.pausedRemainingMs
                    focus.isRunning -> 0L // phase just ended; the controller publishes the next one
                    else -> data.focusMinutes * 60_000L
                }
                Text(
                    text = FocusService.formatClock(ms),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            WidgetMeta(
                when {
                    focus.isPaused -> "Paused"
                    focus.isRunning -> "left"
                    data.lofiPlaying -> "Lofi is playing"
                    else -> "Ready to focus"
                },
                size = 11.sp,
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetCircleButton(
                    icon = if (data.lofiPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_headphones_24,
                    contentDescription = if (data.lofiPlaying) "Pause lofi" else "Play lofi",
                    action = lofiAction(context, data),
                    size = 44.dp,
                    background = if (data.lofiPlaying) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.surfaceVariant,
                    tint = if (data.lofiPlaying) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSurface,
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetCircleButton(
                    icon = when {
                        focus.isRunning -> R.drawable.rounded_pause_24
                        focus.isPaused -> R.drawable.rounded_play_arrow_24
                        else -> R.drawable.rounded_timer_24
                    },
                    contentDescription = when {
                        focus.isRunning -> "Pause focus"
                        focus.isPaused -> "Resume focus"
                        else -> "Start focus"
                    },
                    action = focusAction(context, focus),
                    size = 44.dp,
                    background = GlanceTheme.colors.primary,
                    tint = GlanceTheme.colors.onPrimary,
                )
            }
        }
    }

    private fun lofiAction(context: Context, data: LofiFocusData): Action {
        val action = when {
            data.lofiPlaying -> LofiService.ACTION_PAUSE
            data.lofiLoaded -> LofiService.ACTION_RESUME
            else -> LofiService.ACTION_GENERATE
        }
        return actionStartService(Intent(context, LofiService::class.java).setAction(action), isForegroundService = true)
    }

    private fun focusAction(context: Context, focus: FocusTimerState): Action {
        val action = if (focus.phase.isActive) FocusService.ACTION_TOGGLE else FocusService.ACTION_QUICK_START
        return actionStartService(Intent(context, FocusService::class.java).setAction(action), isForegroundService = true)
    }

    private companion object {
        const val STALE_MS = 12 * 60 * 60 * 1000L
    }
}

class LofiFocusWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LofiFocusWidget2x2()
}
