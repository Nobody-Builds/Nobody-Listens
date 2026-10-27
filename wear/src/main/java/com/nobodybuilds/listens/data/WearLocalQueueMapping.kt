package com.nobodybuilds.listens.data

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nobodybuilds.listens.shared.WearLibraryItem

/**
 * Maps the watch player's queue to the visible queue: the current song first, then what plays next.
 */
internal fun buildWearLocalQueueState(
    player: ExoPlayer?,
    rawCurrentIndex: Int,
    currentQueueSongIds: List<String>,
    currentQueueItemsById: Map<String, WearQueueSong>,
): WearLocalQueueState {
    val visibleQueueIndices = when {
        player == null -> {
            if (rawCurrentIndex in currentQueueSongIds.indices) {
                (rawCurrentIndex until currentQueueSongIds.size).toList()
            } else {
                currentQueueSongIds.indices.toList()
            }
        }

        rawCurrentIndex !in 0 until player.mediaItemCount -> {
            (0 until player.mediaItemCount).toList()
        }

        else -> buildVisibleQueueIndices(player, rawCurrentIndex)
    }

    val queueItems = visibleQueueIndices.mapNotNull { index ->
        val mediaItem = player?.getMediaItemAt(index)
        val songId = mediaItem?.mediaId ?: currentQueueSongIds.getOrNull(index) ?: return@mapNotNull null
        val queueItem = currentQueueItemsById[songId]
        val title = queueItem?.title
            ?: mediaItem?.mediaMetadata?.title?.toString()
            ?: return@mapNotNull null
        val artist = queueItem?.artist ?: mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val album = queueItem?.album ?: mediaItem?.mediaMetadata?.albumTitle?.toString().orEmpty()
        val subtitle = when {
            index == rawCurrentIndex -> {
                val supportingText = artist.ifBlank { album }
                if (supportingText.isBlank()) {
                    "Playing on watch"
                } else {
                    "Playing · $supportingText"
                }
            }

            artist.isNotBlank() -> artist
            else -> album
        }

        WearLibraryItem(
            id = index.toString(),
            title = title,
            subtitle = subtitle,
            type = WearLibraryItem.TYPE_SONG,
        )
    }

    val resolvedCurrentIndex = if (rawCurrentIndex in currentQueueSongIds.indices && queueItems.isNotEmpty()) {
        0
    } else {
        -1
    }

    return WearLocalQueueState(
        items = queueItems,
        currentIndex = resolvedCurrentIndex,
    )
}

private fun buildVisibleQueueIndices(player: ExoPlayer, currentIndex: Int): List<Int> {
    if (currentIndex !in 0 until player.mediaItemCount) {
        return (0 until player.mediaItemCount).toList()
    }

    val visibleIndices = mutableListOf(currentIndex)
    val visited = hashSetOf(currentIndex)
    val timeline = player.currentTimeline
    var nextIndex = timeline.getNextWindowIndex(
        currentIndex,
        Player.REPEAT_MODE_OFF,
        player.shuffleModeEnabled,
    )

    while (nextIndex != C.INDEX_UNSET && visited.add(nextIndex)) {
        visibleIndices += nextIndex
        nextIndex = timeline.getNextWindowIndex(
            nextIndex,
            Player.REPEAT_MODE_OFF,
            player.shuffleModeEnabled,
        )
    }

    return visibleIndices
}
