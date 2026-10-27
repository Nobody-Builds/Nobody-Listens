package com.nobodybuilds.listens.presentation.viewmodel

import kotlin.math.abs
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackStateHolder.Companion.DURATION_MISMATCH_TOLERANCE_MS

/**
 * Tracks playback occurrences of the current song and the position hints tied to them (a paused
 * seek target, a cold-start snapshot), so the UI does not jump back to a stale player position.
 * Owned by [PlaybackStateHolder].
 */
internal class PlaybackPositionHints {
    private var activePositionOccurrenceMediaId: String? = null
    private var activePositionOccurrenceToken: Long = 0L
    private var nextPositionOccurrenceToken: Long = 1L
    private var pausedPositionOverrideMediaId: String? = null
    private var pausedPositionOverrideToken: Long? = null
    private var pausedPositionOverrideMs: Long? = null
    private var coldStartSnapshotMediaId: String? = null
    private var coldStartSnapshotToken: Long? = null
    private var coldStartSnapshotPositionMs: Long? = null

    private fun clearColdStartSnapshot() {
        coldStartSnapshotMediaId = null
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = null
    }

    /**
     * Binds a restored snapshot to the active playback occurrence when possible.
     *
     * If the first occurrence was already activated before the snapshot finished loading, we
     * attach the snapshot to that token so resume still works. If playback has already advanced
     * past the first occurrence, the snapshot is stale and must be discarded.
     */
    fun rememberColdStartSnapshot(mediaId: String, positionMs: Long): Boolean {
        coldStartSnapshotMediaId = mediaId
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = positionMs

        if (nextPositionOccurrenceToken == 1L) {
            return true
        }

        if (
            activePositionOccurrenceToken == 1L &&
            nextPositionOccurrenceToken == 2L &&
            activePositionOccurrenceMediaId == mediaId
        ) {
            coldStartSnapshotToken = activePositionOccurrenceToken
            return true
        }

        clearColdStartSnapshot()
        return false
    }

    /** Returns the position to show, or null when there is no active occurrence for [mediaId]. */
    fun rememberPausedPositionOverride(mediaId: String?, positionMs: Long): Long? {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return null
        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false) ?: return null
        val safePosition = positionMs.coerceAtLeast(0L)
        pausedPositionOverrideMediaId = safeMediaId
        pausedPositionOverrideToken = activeToken
        pausedPositionOverrideMs = safePosition
        return safePosition
    }

    fun clearCurrentPositionHints(mediaId: String? = null) {
        if (mediaId == null || pausedPositionOverrideMediaId == mediaId) {
            pausedPositionOverrideMediaId = null
            pausedPositionOverrideToken = null
            pausedPositionOverrideMs = null
        }
        if (mediaId == null || coldStartSnapshotMediaId == mediaId) {
            clearColdStartSnapshot()
        }
    }

    fun resolveUiPosition(mediaId: String?, reportedPositionMs: Long): Long {
        val safeReportedPosition = reportedPositionMs.coerceAtLeast(0L)
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (safeMediaId == null) {
            return safeReportedPosition
        }

        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false)
            ?: return safeReportedPosition

        val pausedOverride = pausedPositionOverrideMs
            ?.takeIf {
                pausedPositionOverrideMediaId == safeMediaId &&
                    pausedPositionOverrideToken == activeToken
            }
        val coldStartSeed = coldStartSnapshotPositionMs
            ?.takeIf {
                coldStartSnapshotMediaId == safeMediaId &&
                    coldStartSnapshotToken == activeToken
            }
        val preferredPosition = pausedOverride ?: coldStartSeed

        if (preferredPosition == null) {
            return safeReportedPosition
        }

        if (safeReportedPosition <= 0L) {
            return preferredPosition
        }

        val drift = abs(safeReportedPosition - preferredPosition)
        if (drift <= DURATION_MISMATCH_TOLERANCE_MS || safeReportedPosition >= preferredPosition) {
            if (pausedPositionOverrideMediaId == safeMediaId && pausedPositionOverrideToken == activeToken) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
            }
            if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotToken == activeToken) {
                clearColdStartSnapshot()
            }
            return safeReportedPosition
        }

        return preferredPosition
    }

    fun activatePlaybackOccurrence(
        mediaId: String?,
        forceNewOccurrence: Boolean
    ): Long? {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: run {
            activePositionOccurrenceMediaId = null
            activePositionOccurrenceToken = 0L
            if (forceNewOccurrence) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
            }
            return null
        }

        val shouldAdvance =
            forceNewOccurrence ||
                activePositionOccurrenceToken == 0L ||
                activePositionOccurrenceMediaId != safeMediaId

        if (!shouldAdvance) {
            return activePositionOccurrenceToken
        }

        activePositionOccurrenceMediaId = safeMediaId
        activePositionOccurrenceToken = nextPositionOccurrenceToken++

        pausedPositionOverrideMediaId = null
        pausedPositionOverrideToken = null
        pausedPositionOverrideMs = null

        if (coldStartSnapshotToken != null) {
            clearColdStartSnapshot()
        } else if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotPositionMs != null) {
            coldStartSnapshotToken = activePositionOccurrenceToken
        } else if (coldStartSnapshotMediaId != null) {
            clearColdStartSnapshot()
        }

        return activePositionOccurrenceToken
    }
}
