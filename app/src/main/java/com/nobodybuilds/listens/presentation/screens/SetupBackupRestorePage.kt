package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.backup.model.BackupSection
import com.nobodybuilds.listens.data.backup.model.BackupTransferProgressUpdate
import com.nobodybuilds.listens.data.backup.model.RestorePlan
import com.nobodybuilds.listens.presentation.viewmodel.SetupUiState
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupRestorePage(
    uiState: SetupUiState,
    onImportClicked: () -> Unit,
    onSkip: () -> Unit
) {
    val isBusy = uiState.isInspectingBackup || uiState.isRestoringBackup
    val progress = uiState.backupTransferProgress

    PermissionPageLayout(
        title = stringResource(R.string.setup_backup_have_title),
        description = stringResource(R.string.setup_backup_have_description),
        buttonText = when {
            uiState.isInspectingBackup -> stringResource(R.string.setup_inspecting_backup)
            uiState.isRestoringBackup -> stringResource(R.string.setup_restoring_backup)
            else -> stringResource(R.string.setup_import_backup)
        },
        buttonEnabled = !isBusy,
        icons = persistentListOf(
            R.drawable.rounded_upload_file_24,
            R.drawable.rounded_playlist_play_24,
            R.drawable.rounded_settings_24,
            R.drawable.rounded_lyrics_24,
            R.drawable.rounded_monitoring_24
        ),
        onGrantClicked = onImportClicked
    ) {
        AnimatedVisibility(
            visible = uiState.isInspectingBackup || progress != null
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.isInspectingBackup && progress == null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(R.string.setup_checking_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (progress != null) {
                        Text(
                            text = progress.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = progress.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onSkip,
            enabled = !uiState.isRestoringBackup
        ) {
            Text(stringResource(R.string.skip_not_now))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupRestoreDialog(
    plan: RestorePlan,
    inProgress: Boolean,
    progress: BackupTransferProgressUpdate?,
    onDismiss: () -> Unit,
    onSelectionChanged: (Set<BackupSection>) -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val dateText = remember(plan.manifest.createdAt) {
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date(plan.manifest.createdAt))
    }
    val availableModules = remember(plan.availableModules) {
        plan.availableModules.toList().sortedBy { it.ordinal }
    }

    Dialog(
        onDismissRequest = {
            if (!inProgress) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !inProgress,
            dismissOnClickOutside = !inProgress,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !inProgress,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = plan.selectedModules.isNotEmpty() && !inProgress,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                if (inProgress) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.restoring))
                                } else {
                                    Icon(Icons.Rounded.Restore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.restore_selected))
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_restore_backup_title),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = GoogleSansRounded
                        ),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.setup_restore_backup_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.setup_modules_selected_format,
                                    plan.selectedModules.size,
                                    plan.availableModules.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.setup_backup_created_format, dateText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.setup_backup_from_version,
                                    plan.manifest.appVersion.ifEmpty { context.getString(R.string.unknown_version) }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (progress != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = progress.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = progress.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { progress.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (plan.warnings.isNotEmpty()) {
                        plan.warnings.forEach { warning ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(availableModules) { section ->
                            val detail = plan.moduleDetails[section]
                            val selected = section in plan.selectedModules
                            SetupRestoreSectionRow(
                                section = section,
                                detail = detail,
                                selected = selected,
                                enabled = !inProgress,
                                onClick = {
                                    val newSelection = if (selected) {
                                        plan.selectedModules - section
                                    } else {
                                        plan.selectedModules + section
                                    }
                                    onSelectionChanged(newSelection)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRestoreSectionRow(
    section: BackupSection,
    detail: com.nobodybuilds.listens.data.backup.model.ModuleRestoreDetail?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                enabled = enabled
            )

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(section.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = detail?.entryCount?.let { "$it" } ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
