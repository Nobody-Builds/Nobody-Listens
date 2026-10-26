package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.worker.SyncProgress
import com.nobodybuilds.listens.presentation.viewmodel.LyricsRefreshProgress

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        activeOperationLabel: String? = null,
        onFullSync: () -> Unit,
        onRebuild: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_library_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Full Rescan button
            FilledTonalButton(
                    onClick = onFullSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.presentation_batch_f_full_rescan))
            }
             
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rebuild Database button - full width, destructive action
            OutlinedButton(
                    onClick = onRebuild,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.presentation_batch_f_rebuild_database))
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                val phaseLabel = activeOperationLabel ?: syncPhaseLabel(syncProgress.phase)
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.presentation_batch_f_sync_progress_detailed,
                                phaseLabel,
                                (syncProgress.progress * 100).toInt(),
                                syncProgress.currentCount,
                                syncProgress.totalCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.presentation_batch_f_sync_progress_indeterminate,
                                phaseLabel
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun syncPhaseLabel(phase: SyncProgress.SyncPhase): String =
        stringResource(
                when (phase) {
                    SyncProgress.SyncPhase.IDLE -> R.string.presentation_batch_f_sync_phase_preparing
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                            R.string.presentation_batch_f_sync_phase_reading_mediastore
                    SyncProgress.SyncPhase.PROCESSING_FILES ->
                            R.string.presentation_batch_f_sync_phase_processing_tracks
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                            R.string.presentation_batch_f_sync_phase_saving_db
                    SyncProgress.SyncPhase.SCANNING_LRC -> R.string.presentation_batch_f_sync_phase_scanning_lrc
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                            R.string.presentation_batch_f_sync_phase_cleaning_cache
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                            R.string.presentation_batch_f_sync_phase_syncing_cloud
                    SyncProgress.SyncPhase.COMPLETING -> R.string.presentation_batch_f_sync_phase_completing
                }
        )

@Composable
fun RefreshLyricsItem(
        isRefreshing: Boolean,
        progress: LyricsRefreshProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            painter = painterResource(id = R.drawable.rounded_lyrics_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_lyrics_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.presentation_batch_f_refresh_lyrics_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.presentation_batch_f_cd_refresh_lyrics),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isRefreshing && progress.hasProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = stringResource(
                            R.string.presentation_batch_f_refresh_lyrics_processing,
                            progress.currentCount,
                            progress.totalSongs
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(primaryActionLabel)
            }

            // Secondary Action (Optional)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}
