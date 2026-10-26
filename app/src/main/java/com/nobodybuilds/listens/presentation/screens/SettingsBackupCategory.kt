package com.nobodybuilds.listens.presentation.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.backup.model.BackupSection
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Backup & restore: the info notice and the export and import entry points. */
@Composable
internal fun SettingsBackupCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    exportSections: Set<BackupSection>,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    if (!uiState.backupInfoDismissed) {
        BackupInfoNoticeCard(
            onDismiss = { settingsViewModel.setBackupInfoDismissed(true) }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    SettingsSubsection(title = stringResource(R.string.setcat_create_backup)) {
        ActionSettingsItem(
            title = stringResource(R.string.setcat_export_backup_title),
            subtitle = stringResource(
                R.string.setcat_export_backup_subtitle,
                buildBackupSelectionSummary(context, exportSections)
            ),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_save_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            primaryActionLabel = stringResource(R.string.setcat_select_export),
            onPrimaryAction = onExportClick,
            enabled = !uiState.isDataTransferInProgress
        )
    }

    SettingsSubsection(
        title = stringResource(R.string.setcat_restore_backup_section),
        addBottomSpace = false
    ) {
        ActionSettingsItem(
            title = stringResource(R.string.setcat_import_backup_title),
            subtitle = stringResource(R.string.setcat_import_backup_subtitle),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            primaryActionLabel = stringResource(R.string.setcat_select_restore),
            onPrimaryAction = onImportClick,
            enabled = !uiState.isDataTransferInProgress
        )
    }
}

private fun buildBackupSelectionSummary(context: Context, selected: Set<BackupSection>): String {
    if (selected.isEmpty()) return context.getString(R.string.backup_summary_none)
    val total = BackupSection.entries.size
    return if (selected.size == total) {
        context.getString(R.string.backup_summary_all)
    } else {
        context.getString(R.string.backup_summary_partial, selected.size, total)
    }
}

@Composable
private fun BackupInfoNoticeCard(
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_upload_file_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_how_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.backup_how_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = stringResource(R.string.cd_close_notice),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
