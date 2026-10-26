package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.database.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors likes on streamed YouTube songs to the signed-in YouTube account.
 * Phone songs never leave the device, and nothing happens while signed out.
 */
@Singleton
class YouTubeMusicAccountSync @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val musicDao: MusicDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called after a like changes locally; the local like never waits for YouTube. */
    fun onFavoriteChanged(songId: Long, isFavorite: Boolean) {
        if (!repository.isLoggedIn) return
        scope.launch {
            val contentUri = musicDao.getSongsByIdsListSimple(listOf(songId)).firstOrNull()?.contentUriString ?: return@launch
            val videoId = YouTubeMusicUris.videoIdFrom(contentUri) ?: return@launch
            if (!repository.setLiked(videoId, isFavorite)) Timber.w("Could not sync the like of $videoId to YouTube")
        }
    }
}
