package com.nobodybuilds.listens.data.library

import android.os.Environment
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.worker.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A folder the user can add with one tap during setup / in settings. */
data class QuickAddFolder(val label: String, val path: String)

/**
 * The "music folders" include list. Only songs inside these folders are scanned.
 * Excluded subfolders keep living in the existing blocked-directories storage.
 */
@Singleton
class MusicFoldersRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val syncManager: SyncManager,
    private val noticePreferences: MusicFoldersNoticePreferences,
) {
    val includedFolders: Flow<Set<String>> = userPreferencesRepository.includedDirectoriesFlow

    private val editMutex = Mutex()

    /** Music and Downloads on internal storage, only when they exist. */
    fun quickAddFolders(): List<QuickAddFolder> {
        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull() ?: return emptyList()
        return listOf(
            "Music" to Environment.DIRECTORY_MUSIC,
            "Downloads" to Environment.DIRECTORY_DOWNLOADS,
        ).mapNotNull { (label, dirName) ->
            val dir = File(root, dirName)
            if (dir.isDirectory) QuickAddFolder(label, normalize(dir.absolutePath)) else null
        }
    }

    /** Adds [path]. Returns false when it is already covered by an included folder. */
    suspend fun addFolder(path: String, rescan: Boolean): Boolean = editMutex.withLock { addLocked(path, rescan) }

    private suspend fun addLocked(path: String, rescan: Boolean): Boolean {
        val normalized = normalize(path)
        val current = includedFolders.first()
        if (current.any { isSameOrInside(normalized, it) }) return false
        // A parent replaces any included folders inside it.
        val updated = current.filterNot { isSameOrInside(it, normalized) }.toSet() + normalized
        userPreferencesRepository.updateIncludedDirectories(updated)
        if (rescan) syncManager.forceRefresh()
        return true
    }

    /**
     * Removes [path]. Returns false (and changes nothing) when it would leave no music folders
     * after setup is done, since an empty include list would hide the whole library.
     */
    suspend fun removeFolder(path: String, rescan: Boolean): Boolean = editMutex.withLock { removeLocked(path, rescan) }

    private suspend fun removeLocked(path: String, rescan: Boolean): Boolean {
        val normalized = normalize(path)
        val current = includedFolders.first()
        val updated = current.filterNot { it.equals(normalized, ignoreCase = true) }.toSet()
        if (updated.size == current.size) return true
        if (updated.isEmpty() && userPreferencesRepository.initialSetupDoneFlow.first()) return false
        userPreferencesRepository.updateIncludedDirectories(updated)
        if (rescan) syncManager.forceRefresh()
        return true
    }

    /**
     * Toggles a folder picked in the explorer: removes it when included, adds it otherwise.
     * Returns false when the removal was refused because it was the last folder.
     */
    suspend fun toggleFolder(path: String, rescan: Boolean): Boolean = editMutex.withLock {
        val normalized = normalize(path)
        val current = includedFolders.first()
        if (current.any { it.equals(normalized, ignoreCase = true) }) {
            removeLocked(normalized, rescan)
        } else {
            addLocked(normalized, rescan)
            true
        }
    }

    fun rescan() = syncManager.forceRefresh()

    /**
     * One-time migration for installs that finished setup with the old "exclude" model:
     * build the include list from the top-level folders that currently hold songs so nothing
     * disappears. Leaves the flag unset when there is nothing to derive from yet.
     */
    suspend fun migrateIfNeeded(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            if (userPreferencesRepository.includedDirectoriesMigratedFlow.first()) return@runCatching
            if (userPreferencesRepository.includedDirectoriesFlow.first().isNotEmpty()) {
                userPreferencesRepository.setIncludedDirectoriesMigrated(true)
                return@runCatching
            }
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) return@runCatching

            val parents = musicDao.getDistinctParentDirectories()
            val roots = MusicFolderPaths.topLevelFolders(parents)
            if (roots.isEmpty()) return@runCatching

            Timber.i("Migrating to music folders include list: $roots")
            userPreferencesRepository.updateIncludedDirectories(roots)
            userPreferencesRepository.setIncludedDirectoriesMigrated(true)
            noticePreferences.setShowFoldersMigratedNotice(true)
        }.onFailure { Timber.w(it, "Music folders migration failed") }
    }

    private fun normalize(path: String): String = MusicFolderPaths.normalize(path)

    private fun isSameOrInside(path: String, root: String): Boolean = MusicFolderPaths.isSameOrInside(path, root)
}

/** Pure path helpers for the include list (kept free of Android types for unit tests). */
object MusicFolderPaths {
    fun normalize(path: String): String = File(path).absolutePath.trimEnd('/').ifEmpty { "/" }

    fun isSameOrInside(path: String, root: String): Boolean {
        val p = path.trimEnd('/')
        val r = root.trimEnd('/')
        return p.equals(r, ignoreCase = true) || p.startsWith("$r/", ignoreCase = true)
    }

    /**
     * Maps song parent directories to the top-level folder of their storage volume,
     * e.g. /storage/emulated/0/Music/Artist/Album -> /storage/emulated/0/Music.
     * Songs sitting in a volume root keep the root itself. Songs under Android/data (app folders
     * such as Telegram's) keep their own parent folder, so the rest of Android/data stays out.
     */
    fun topLevelFolders(parentDirs: Collection<String>): Set<String> {
        val result = linkedSetOf<String>()
        parentDirs.forEach { raw ->
            if (raw.isBlank()) return@forEach
            val segments = raw.split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return@forEach
            val volumeDepth = when {
                segments[0] == "storage" && segments.getOrNull(1) == "emulated" -> 3
                segments[0] == "storage" -> 2
                segments[0] == "sdcard" -> 1
                else -> {
                    result += "/" + segments.joinToString("/")
                    return@forEach
                }
            }
            if (segments.size < volumeDepth) return@forEach
            val top = segments.getOrNull(volumeDepth)
            if (top == "Android" && segments.getOrNull(volumeDepth + 1) == "data") {
                result += "/" + segments.joinToString("/")
                return@forEach
            }
            val take = if (top != null) volumeDepth + 1 else volumeDepth
            result += "/" + segments.take(take).joinToString("/")
        }
        return result
    }
}
