package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.shared.WearTransferProgress

/**
 * Follows one song being saved to the watch, from the transfer list and the saved songs.
 * [update] returns true once it's saved, false once its transfer ended without it, null while it runs.
 * It follows the transfer [requestId] it asked for, or one of the same song already running.
 */
internal class SongSaveWatcher(private val songId: String, private val requestId: String) {

    private var seen = false

    fun update(downloaded: Set<String>, active: Map<String, TransferState>): Boolean? {
        val transfer = active[requestId] ?: active.values.firstOrNull {
            it.songId == songId && it.status == WearTransferProgress.STATUS_TRANSFERRING
        }
        if (transfer != null) seen = true
        return when {
            songId in downloaded -> true
            transfer?.status in ENDED_STATUSES -> false
            // Cancelled transfers are removed rather than marked; a finished one leaves a row.
            transfer == null && seen -> false
            else -> null
        }
    }

    private companion object {
        val ENDED_STATUSES = setOf(WearTransferProgress.STATUS_FAILED, WearTransferProgress.STATUS_CANCELLED)
    }
}
