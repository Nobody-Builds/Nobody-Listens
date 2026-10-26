package com.nobodybuilds.listens.data.service.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.data.focus.FocusController
import com.nobodybuilds.listens.data.focus.FocusMusicSource
import com.nobodybuilds.listens.data.focus.FocusRepository
import com.nobodybuilds.listens.data.lofi.LofiController
import com.nobodybuilds.listens.data.lofi.LofiPreferences
import com.nobodybuilds.listens.data.lofi.LofiSource
import com.nobodybuilds.listens.data.noise.NoiseController
import com.nobodybuilds.listens.data.smart.SmartRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Quick Settings tiles for Focus, Lofi and Noise. Each one only observes state while the Quick
// Settings panel shows it (onStartListening … onStopListening), so they cost nothing otherwise.
// A tile click is a user action: the foreground service is started right from onClick.

/**
 * Process-level scope for click work: SystemUI may unbind (destroy) the tile right after a click,
 * which must not cancel a start that is still loading settings.
 */
internal object TileClickScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Shared plumbing: a main-thread scope for listening (cancelled with the tile). */
abstract class NobodyListensSoundTile : TileService() {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch { listen() }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Collects state and calls [render] until cancelled. */
    protected abstract suspend fun listen()

    protected fun render(active: Boolean, subtitle: String?) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = subtitle
        tile.updateTile()
    }

    /** Android refused the service start: open the app instead. */
    protected fun openApp(requestCode: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivityAndCollapseCompat(intent, requestCode) }
    }

    /** Runs [block] now, or after unlocking when the device is locked and [needsUnlock]. */
    protected fun runClick(needsUnlock: Boolean = false, block: () -> Unit) {
        if (needsUnlock && isLocked) unlockAndRun { block() } else block()
    }
}

/** Starts or stops a focus session with the last settings; shows the minutes left. */
@AndroidEntryPoint
class FocusTileService : NobodyListensSoundTile() {
    @Inject lateinit var controller: FocusController
    @Inject lateinit var repository: FocusRepository
    @Inject lateinit var smart: SmartRepository

    override fun onStartListening() {
        runCatching { controller.ensureRestored() }
        super.onStartListening()
    }

    override suspend fun listen() = coroutineScope {
        // State changes come from the flow; the minute count only needs a refresh while visible.
        launch {
            while (true) {
                delay(30_000L)
                renderFocus()
            }
        }
        controller.state.collect { renderFocus() }
    }

    private fun renderFocus() {
        val s = controller.state.value
        if (!s.phase.isActive) {
            render(false, "Start a session")
            return
        }
        val minutes = ((s.remainingMs(System.currentTimeMillis()) + 59_999L) / 60_000L).toInt()
        val sub = when {
            s.isPaused -> "Paused · $minutes min left"
            s.phase.isBreak -> "Break · $minutes min left"
            else -> "$minutes min left"
        }
        render(true, sub)
    }

    override fun onClick() {
        super.onClick()
        runClick {
            if (controller.state.value.phase.isActive) {
                controller.stop()
                return@runClick
            }
            TileClickScope.launch {
                val config = runCatching { repository.config.first() }.getOrNull() ?: return@launch
                var source = runCatching { repository.musicSource.first() }.getOrDefault(FocusMusicSource.Lofi)
                if (source == FocusMusicSource.Smart && !runCatching { smart.isEnabled.first() }.getOrDefault(false)) {
                    source = FocusMusicSource.Library
                }
                runCatching { controller.start(config, source) }
                    .onFailure { Timber.tag("FocusTile").w(it, "Focus start failed"); openApp(REQUEST_FOCUS) }
                renderFocus()
            }
        }
    }

    private companion object { const val REQUEST_FOCUS = 1101 }
}

/** Plays or pauses the lofi generator with the last mood. */
@AndroidEntryPoint
class LofiTileService : NobodyListensSoundTile() {
    @Inject lateinit var controller: LofiController
    @Inject lateinit var preferences: LofiPreferences

    override suspend fun listen() {
        controller.state.collect { st ->
            val on = st.source == LofiSource.Generator && st.isPlaying
            render(on, if (on) controller.settings.value.mood.label else if (st.source == LofiSource.Generator) "Paused" else null)
        }
    }

    override fun onClick() {
        super.onClick()
        runClick {
            val st = controller.state.value
            when {
                st.source == LofiSource.Generator && st.isPlaying -> controller.pause()
                st.source == LofiSource.Generator -> if (!controller.resume()) openApp(REQUEST_LOFI)
                else -> TileClickScope.launch {
                    val saved = runCatching { preferences.loadSettings() }.getOrNull()
                    controller.ensureLoaded()
                    if (!controller.start(saved ?: controller.settings.value)) openApp(REQUEST_LOFI)
                }
            }
        }
    }

    private companion object { const val REQUEST_LOFI = 1102 }
}

/** Plays or pauses the last noise preset. */
@AndroidEntryPoint
class NoiseTileService : NobodyListensSoundTile() {
    @Inject lateinit var controller: NoiseController

    override suspend fun listen() {
        controller.state.collect { st ->
            val s = controller.settings.value
            val label = s.presetName ?: s.summary
            render(st.active, when {
                st.active && !st.sounding -> "Waits for music"
                st.active -> label
                else -> null
            })
        }
    }

    override fun onClick() {
        super.onClick()
        runClick {
            if (controller.state.value.active) {
                controller.pause()
                return@runClick
            }
            TileClickScope.launch {
                controller.loadNow()
                // Always on its own from the tile: "under music" with no music would stay silent.
                val ok = if (controller.isPausedSolo) controller.resume() else controller.playSolo()
                if (!ok) openApp(REQUEST_NOISE)
            }
        }
    }

    private companion object { const val REQUEST_NOISE = 1103 }
}
