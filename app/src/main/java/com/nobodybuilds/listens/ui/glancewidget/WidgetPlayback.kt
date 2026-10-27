package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.model.PlayerInfo
import com.nobodybuilds.listens.data.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Whether [MusicService] is alive in this process. The widget state is persisted, so after the
 * process dies it still says "playing"; widgets use this to show an idle state instead.
 */
object WidgetPlaybackLiveness {
    @Volatile
    var serviceAlive: Boolean = false
}

/**
 * The state a player widget should draw: when the service is gone nothing is playing, so show
 * "Tap to play" (tapping play restores the last queue) instead of an old song marked as playing.
 */
fun PlayerInfo.forDisplay(): PlayerInfo {
    val alive = WidgetPlaybackLiveness.serviceAlive
    return when {
        songTitle.isBlank() -> copy(isPlaying = false, songTitle = "Nobody Listens", artistName = "Tap to play")
        !alive -> copy(
            isPlaying = false,
            songTitle = "Tap to play",
            artistName = songTitle,
            currentPositionMs = 0L,
        )
        else -> this
    }
}

/** A tap that sends [action] to [MusicService] as a foreground-service start (works with the app killed and on the lock screen). */
@androidx.annotation.OptIn(UnstableApi::class)
fun musicServiceAction(context: Context, action: String, songId: Long? = null): Action {
    val intent = Intent(context, MusicService::class.java)
        .setAction(action)
        .putExtra(MusicService.EXTRA_FORCE_FOREGROUND_ON_START, true)
    if (songId != null) intent.putExtra(PlayerActions.EXTRA_SONG_ID, songId)
    return actionStartService(intent, isForegroundService = true)
}

/** Drop-in for the old `actionRunCallback<PlayerControlActionCallback>(params)`. */
@Composable
fun playerControlAction(params: ActionParameters): Action =
    musicServiceAction(
        LocalContext.current,
        params[PlayerActions.key] ?: PlayerActions.PLAY_PAUSE,
        params[PlayerActions.songIdKey],
    )

/**
 * Pushes player state to every player widget. All player widgets share one DataStore file, so the
 * state is written once per push (not once per widget), and pushes are throttled to one per second
 * with the newest state always winning.
 */
object PlayerWidgetUpdater {
    private const val TAG = "PlayerWidgetUpdater"
    private const val MIN_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var latest: PlayerInfo? = null
    private var job: Job? = null
    @Volatile private var lastPushAt = 0L

    /** Every widget that draws [PlayerInfo]. */
    fun playerWidgets(): List<GlanceAppWidget> = listOf(
        NobodyListensGlanceWidget(),
        BarWidget4x1(),
        ControlWidget4x2(),
        GridWidget2x2(),
        NowPlayingWidget2x1(),
        AlbumArtWidget(),
        QueueWidget4x3(),
        LockScreenWidget(),
    )

    fun submit(context: Context, info: PlayerInfo) {
        val app = context.applicationContext
        synchronized(lock) {
            latest = info
            if (job?.isActive == true) return
            job = scope.launch { drain(app) }
        }
    }

    private suspend fun drain(app: Context) {
        while (true) {
            val wait = lastPushAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            val next = synchronized(lock) {
                val n = latest
                latest = null
                if (n == null) job = null
                n
            } ?: return
            push(app, next)
            lastPushAt = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun push(app: Context, info: PlayerInfo) {
        runCatching {
            val manager = GlanceAppWidgetManager(app)
            val targets = playerWidgets()
                .map { it to manager.getGlanceIds(it.javaClass) }
                .filter { it.second.isNotEmpty() }
            if (targets.isEmpty()) return
            updateAppWidgetState(app, PlayerInfoStateDefinition, targets.first().second.first()) { info }
            targets.forEach { (widget, ids) ->
                ids.forEach { id ->
                    runCatching { widget.update(app, id) }
                        .onFailure { Timber.tag(TAG).w(it, "Widget update failed for %s", widget.javaClass.simpleName) }
                }
            }
        }.onFailure { Timber.tag(TAG).w(it, "Widget push failed") }
    }

    /** The service is going away: persist "not playing" so widgets never show a stale playing state. */
    fun markStopped(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            latest = null
            job?.cancel()
            job = null
        }
        scope.launch {
            runCatching {
                PlayerInfoStateDefinition.getDataStore(app, "").updateData { it.copy(isPlaying = false) }
                val manager = GlanceAppWidgetManager(app)
                playerWidgets().forEach { widget ->
                    manager.getGlanceIds(widget.javaClass).forEach { id -> runCatching { widget.update(app, id) } }
                }
            }.onFailure { Timber.tag(TAG).w(it, "markStopped failed") }
        }
    }
}

/**
 * Refreshes the non-player widgets (lofi & focus, stats, recently played). Each kind is throttled
 * and skipped entirely when none is on the home screen.
 */
object ExtraWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<Class<*>, Job>()
    private val lastAt = HashMap<Class<*>, Long>()

    fun requestLofiFocus(context: Context) = request(context, LofiFocusWidget2x2(), 1_000L)
    fun requestStats(context: Context) = request(context, StatsWidget2x2(), 30_000L)
    fun requestRecent(context: Context) = request(context, RecentWidget4x2(), 15_000L)

    /** After a play is recorded: stats and recently played change. */
    fun onListeningRecorded(context: Context) {
        requestStats(context)
        requestRecent(context)
    }

    private val dirty = HashSet<Class<*>>()

    private fun request(context: Context, widget: GlanceAppWidget, minIntervalMs: Long) {
        val app = context.applicationContext
        val key = widget.javaClass
        synchronized(pending) {
            dirty += key
            if (pending[key]?.isActive == true) return
            pending[key] = scope.launch {
                while (true) {
                    val wait = synchronized(pending) { (lastAt[key] ?: 0L) + minIntervalMs } - SystemClock.elapsedRealtime()
                    if (wait > 0) delay(wait)
                    val run = synchronized(pending) {
                        val d = dirty.remove(key)
                        if (!d) pending.remove(key) else lastAt[key] = SystemClock.elapsedRealtime()
                        d
                    }
                    if (!run) return@launch
                    runCatching {
                        GlanceAppWidgetManager(app).getGlanceIds(key).forEach { id -> widget.update(app, id) }
                    }.onFailure { Timber.tag("ExtraWidgetUpdater").w(it, "Update failed for %s", key.simpleName) }
                }
            }
        }
    }
}
