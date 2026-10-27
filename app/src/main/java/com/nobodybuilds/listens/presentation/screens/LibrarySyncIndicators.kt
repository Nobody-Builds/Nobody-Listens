@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.data.worker.SyncProgress
import com.nobodybuilds.listens.presentation.components.SyncProgressBar
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * Slim, non-intrusive indicator for sync work that should not keep the list pulled
 * down: automatic startup syncs, background maintenance, and manual refreshes after
 * the short pull-to-refresh confirmation window. It sits just below
 * [LibraryActionRow] and collapses to zero height when not active.
 *
 * Distinct from [LibrarySyncOverlay], which is reserved for initial empty-library
 * loads. The parent screen also gates this indicator off while the pull spinner is
 * visible, so the two feedback channels do not compete.
 */
@Composable
internal fun LibraryInlineSyncIndicator(
    visible: Boolean,
    syncManager: com.nobodybuilds.listens.data.worker.SyncManager
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(180)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(160))
    ) {
        // Collected inside this subtree so progress ticks don't recompose the
        // parent screen — same pattern as LibrarySyncOverlay.
        val syncProgress by syncManager.syncProgress
            .collectAsStateWithLifecycle(initialValue = SyncProgress())

        val phaseLabel = when (syncProgress.phase) {
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                stringResource(R.string.sync_scanning)
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                stringResource(R.string.sync_processing)
            SyncProgress.SyncPhase.SCANNING_LRC ->
                stringResource(R.string.library_background_sync_lyrics)
            SyncProgress.SyncPhase.CLEANING_CACHE ->
                stringResource(R.string.library_background_sync_cache)
            SyncProgress.SyncPhase.SYNCING_CLOUD ->
                stringResource(R.string.library_background_sync_cloud)
            else ->
                stringResource(R.string.sync_in_progress)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * P1-1: Isolated sync/loading overlay composable.
 *
 * By collecting [SyncManager.syncProgress] HERE instead of in the parent [LibraryScreen],
 * only this small subtree recomposes on every progress tick (e.g., file count updates
 * during a library scan). The rest of [LibraryScreen] — including the Scaffold, pager,
 * and all tab content — remains unaffected during sync.
 */
@Composable
internal fun LibrarySyncOverlay(syncManager: com.nobodybuilds.listens.data.worker.SyncManager) {
    val syncProgress by syncManager.syncProgress
        .collectAsStateWithLifecycle(initialValue = SyncProgress())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                if (syncProgress.hasProgress && syncProgress.isRunning) {
                    // Show progress bar with file count when we have progress info
                    SyncProgressBar(
                        syncProgress = syncProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Show indeterminate loading indicator when scanning starts
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.syncing_library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
