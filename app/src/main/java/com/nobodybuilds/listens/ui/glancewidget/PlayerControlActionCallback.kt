package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.service.MusicService
import timber.log.Timber

class PlayerControlActionCallback : ActionCallback {
    private val TAG = "PlayerControlCallback"

    @OptIn(UnstableApi::class)
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[PlayerActions.key]
        Timber.tag(TAG).d("onAction received: $action for glanceId: $glanceId")

        if (action == null) {
            Timber.tag(TAG).w("Action key not found in parameters.")
            return
        }

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
            if (action == PlayerActions.PLAY_FROM_QUEUE) {
                val songId = parameters[PlayerActions.songIdKey]
                if (songId != null) {
                    putExtra("song_id", songId)
                } else {
                    Timber.tag(TAG).w("PLAY_FROM_QUEUE action received but no songId found.")
                    return // No hacer nada si no hay ID de canción
                }
            }
        }

        // Kept for widgets drawn by older versions; new widgets use [musicServiceAction] directly.
        serviceIntent.putExtra(MusicService.EXTRA_FORCE_FOREGROUND_ON_START, true)
        try {
            context.startForegroundService(serviceIntent)
            Timber.tag(TAG).d("Service intent sent for action: $action")
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            Timber.tag(TAG).w(e, "Cannot start foreground service from background for action $action")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting service for action $action: ${e.message}")
        }
    }
}

object PlayerActions {
    val key = ActionParameters.Key<String>("playerActionKey_v1")
    val songIdKey = ActionParameters.Key<Long>("songIdKey_v1")
    const val PLAY_PAUSE = "com.nobodybuilds.listens.ACTION_WIDGET_PLAY_PAUSE"
    const val NEXT = "com.nobodybuilds.listens.ACTION_WIDGET_NEXT"
    const val PREVIOUS = "com.nobodybuilds.listens.ACTION_WIDGET_PREVIOUS"
    const val FAVORITE = "com.nobodybuilds.listens.ACTION_WIDGET_FAVORITE"
    const val PLAY_FROM_QUEUE = "com.nobodybuilds.listens.ACTION_WIDGET_PLAY_FROM_QUEUE"
    const val SHUFFLE = "com.nobodybuilds.listens.ACTION_WIDGET_SHUFFLE"
    const val REPEAT = "com.nobodybuilds.listens.ACTION_WIDGET_REPEAT"
    /** Plays a song by id: jumps to it when it is in the queue, otherwise plays it next. */
    const val PLAY_SONG = "com.nobodybuilds.listens.ACTION_WIDGET_PLAY_SONG"
    const val EXTRA_SONG_ID = "song_id"

    /** Actions [com.nobodybuilds.listens.data.service.MusicService] handles for widgets. */
    val ALL = setOf(PLAY_PAUSE, NEXT, PREVIOUS, FAVORITE, PLAY_FROM_QUEUE, SHUFFLE, REPEAT, PLAY_SONG)
}
