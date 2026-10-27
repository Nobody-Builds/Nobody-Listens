package com.nobodybuilds.listens.data.service.wear

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Starts a queue the watch asked for. The watch's message service is destroyed as soon as the message is
 * handled, often before a queue is loaded or a player connection is up, so this runs in the app's scope with
 * its own connection on the application context. Releasing a connection bound to the destroyed service
 * crashed the app.
 */
@Singleton
class WatchStartedPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dualPlayerEngine: DualPlayerEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Loads the queue with [load] (songs and start index; null to do nothing) and plays it from [positionMs]. */
    fun play(label: String, positionMs: Long = 0L, load: suspend () -> Pair<List<Song>, Int>?) {
        scope.launch {
            try {
                val (songs, startIndex) = withContext(Dispatchers.IO) { load() } ?: run {
                    Timber.w("$label: nothing to play")
                    return@launch
                }
                val mediaItems = songs.map { MediaItemBuilder.build(it) }.toMutableList()
                mediaItems[startIndex] = dualPlayerEngine.resolveMediaItem(mediaItems[startIndex])
                val controller = connect()
                try {
                    controller.setMediaItems(mediaItems, startIndex, positionMs)
                    controller.prepare()
                    controller.play()
                    // Let the commands reach the session before letting go of it.
                    delay(RELEASE_AFTER_MS)
                } finally {
                    controller.release()
                }
            } catch (e: Exception) {
                Timber.e(e, "$label failed")
            }
        }
    }

    private suspend fun connect(): MediaController = suspendCancellableCoroutine { continuation ->
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
    }

    private companion object {
        const val RELEASE_AFTER_MS = 2_000L
    }
}
