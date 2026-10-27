package com.nobodybuilds.listens.presentation.folders

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.library.MusicFoldersRepository
import com.nobodybuilds.listens.data.library.QuickAddFolder
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.presentation.viewmodel.FileExplorerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the "Music folders" include-list editor used in setup and in Settings › Library.
 * [rescanOnChange] is off during setup (setup runs a full sync when it finishes).
 */
@HiltViewModel
class MusicFoldersViewModel @Inject constructor(
    private val repository: MusicFoldersRepository,
    userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var rescanOnChange: Boolean = true

    val includedFolders: StateFlow<List<String>> = repository.includedFolders
        .map { it.sortedBy { path -> path.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val includedFolderSet: StateFlow<Set<String>> = repository.includedFolders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val explorer = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = explorer.currentPath
    val currentDirectoryChildren = explorer.currentDirectoryChildren
    val availableStorages = explorer.availableStorages
    val selectedStorageIndex = explorer.selectedStorageIndex
    val isLoadingDirectories = explorer.isLoading
    val isExplorerPriming = explorer.isPrimingExplorer
    val isExplorerReady = explorer.isExplorerReady
    val isCurrentDirectoryResolved = explorer.isCurrentDirectoryResolved

    private var pendingRescan = false

    fun quickAddFolders(): List<QuickAddFolder> = repository.quickAddFolders()

    fun addFolder(path: String) {
        viewModelScope.launch { repository.addFolder(path, rescan = rescanOnChange) }
    }

    fun removeFolder(path: String) {
        viewModelScope.launch {
            if (!repository.removeFolder(path, rescan = rescanOnChange)) showKeepOneToast()
        }
    }

    /** Explorer toggles are batched; the rescan runs once when the picker closes. */
    fun togglePickedFolder(file: File) {
        pendingRescan = true
        viewModelScope.launch {
            if (!repository.toggleFolder(file.absolutePath, rescan = false)) showKeepOneToast()
        }
    }

    private fun showKeepOneToast() {
        Toast.makeText(context, R.string.folders_keep_one, Toast.LENGTH_SHORT).show()
    }

    fun onPickerClosed() {
        if (pendingRescan && rescanOnChange) {
            viewModelScope.launch { repository.rescan() }
        }
        pendingRescan = false
    }

    fun primeExplorer() { explorer.primeExplorerRoot() }
    fun openExplorer() { explorer.openExplorerRoot() }
    fun loadDirectory(file: File) { explorer.loadDirectory(file) }
    fun navigateUp() = explorer.navigateUp()
    fun refreshCurrentDirectory() { explorer.refreshCurrentDirectory() }
    fun selectStorage(index: Int) = explorer.selectStorage(index)
    fun isAtRoot(): Boolean = explorer.isAtRoot()
    fun explorerRoot(): File = explorer.rootDirectory()
}
