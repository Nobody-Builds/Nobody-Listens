package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.net.Uri
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.backup.BackupManager
import com.nobodybuilds.listens.data.backup.model.BackupHistoryEntry
import com.nobodybuilds.listens.data.backup.model.BackupOperationType
import com.nobodybuilds.listens.data.backup.model.BackupSection
import com.nobodybuilds.listens.data.backup.model.BackupTransferProgressUpdate
import com.nobodybuilds.listens.data.backup.model.RestoreResult
import com.nobodybuilds.listens.data.worker.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Backup export, inspection and restore behind [SettingsViewModel]. */
internal class SettingsBackupActions(
    private val backupManager: BackupManager,
    private val syncManager: SyncManager,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val context: Context
) {
    private val _dataTransferEvents = MutableSharedFlow<String>()
    val dataTransferEvents: SharedFlow<String> = _dataTransferEvents.asSharedFlow()

    private val _dataTransferProgress = MutableStateFlow<BackupTransferProgressUpdate?>(null)
    val dataTransferProgress: StateFlow<BackupTransferProgressUpdate?> = _dataTransferProgress.asStateFlow()

    fun exportAppData(uri: Uri, sections: Set<BackupSection>) {
        if (sections.isEmpty() || uiState.value.isDataTransferInProgress) return
        scope.launch {
            uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.EXPORT,
                step = 0,
                totalSteps = 1,
                title = context.getString(R.string.backup_progress_preparing_backup),
                detail = context.getString(R.string.backup_progress_starting_backup_task),
            )
            val result = backupManager.export(uri, sections) { progress ->
                _dataTransferProgress.value = progress
            }
            result.fold(
                onSuccess = { _dataTransferEvents.emit(context.getString(R.string.data_exported_successfully)) },
                onFailure = {
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.export_failed_format,
                            it.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
                },
            )
            delay(300)
            uiState.update { it.copy(isDataTransferInProgress = false) }
            _dataTransferProgress.value = null
        }
    }

    fun inspectBackupFile(uri: Uri) {
        if (uiState.value.isInspectingBackup) return
        scope.launch {
            uiState.update { it.copy(isInspectingBackup = true, backupValidationErrors = emptyList(), restorePlan = null) }
            val result = backupManager.inspectBackup(uri)
            result.fold(
                onSuccess = { plan ->
                    uiState.update { it.copy(restorePlan = plan, isInspectingBackup = false) }
                },
                onFailure = { error ->
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.backup_invalid_format,
                            error.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
                    uiState.update { it.copy(isInspectingBackup = false) }
                }
            )
        }
    }

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) {
        uiState.update { state ->
            state.restorePlan?.let { plan ->
                state.copy(restorePlan = plan.copy(selectedModules = selectedModules))
            } ?: state
        }
    }

    fun restoreFromPlan(uri: Uri) {
        val plan = uiState.value.restorePlan ?: return
        if (plan.selectedModules.isEmpty() || uiState.value.isDataTransferInProgress) return
        scope.launch {
            uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.IMPORT,
                step = 0,
                totalSteps = 1,
                title = context.getString(R.string.backup_progress_preparing_restore),
                detail = context.getString(R.string.backup_progress_starting_task),
            )
            val result = backupManager.restore(uri, plan) { progress ->
                _dataTransferProgress.value = progress
            }
            when (result) {
                is RestoreResult.Success -> {
                    _dataTransferEvents.emit(context.getString(R.string.data_restored_successfully))
                    syncManager.sync()
                }
                is RestoreResult.PartialFailure -> {
                    val failedNames = result.failed.entries.joinToString { "${it.key.label}: ${it.value}" }
                    _dataTransferEvents.emit(
                        context.getString(R.string.restore_partial_unresolved_format, failedNames),
                    )
                    if (result.succeeded.isNotEmpty() || !result.rolledBack) {
                        syncManager.sync()
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _dataTransferEvents.emit(context.getString(R.string.restore_failed_format, result.error))
                }
            }
            delay(300)
            uiState.update { it.copy(isDataTransferInProgress = false, restorePlan = null) }
            _dataTransferProgress.value = null
        }
    }

    fun clearRestorePlan() {
        uiState.update { it.copy(restorePlan = null, backupValidationErrors = emptyList()) }
    }

    fun removeBackupHistoryEntry(entry: BackupHistoryEntry) {
        scope.launch {
            backupManager.removeBackupHistoryEntry(entry.uri)
        }
    }
}
