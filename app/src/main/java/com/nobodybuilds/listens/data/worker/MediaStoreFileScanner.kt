package com.nobodybuilds.listens.data.worker

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.utils.DirectoryRuleResolver
import com.nobodybuilds.listens.utils.buildLocalAudioSelection
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Finds audio files MediaStore has not indexed yet, and lists the ids MediaStore still has, for [SyncWorker]. */
internal class MediaStoreFileScanner(
    private val applicationContext: Context,
    private val contentResolver: ContentResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val minSongDurationProvider: () -> Int,
) {

    private companion object {
        private const val TAG = "SyncWorker"
    }

    private val minSongDurationMs: Int get() = minSongDurationProvider()

    /**
     * Triggers a media scan ONLY for new files that are not yet in MediaStore.
     * This is a fast, incremental scan optimized for pull-to-refresh.
     * It compares filesystem files with MediaStore entries and only scans the difference.
     */
    suspend fun triggerMediaScanForNewFiles(directoryResolver: DirectoryRuleResolver) {
        withContext(Dispatchers.IO) {
            val externalRoot = Environment.getExternalStorageDirectory()
            val allowedSet =
                userPreferencesRepository.allowedDirectoriesFlow.first().map { File(it) }.toSet()
            Log.i(TAG, "Starting media scan for new files. Explicit includes: ${allowedSet.size}")

            // Get all file paths currently in MediaStore
            val mediaStorePaths = fetchMediaStoreFilePaths()
            Log.d(TAG, "MediaStore has ${mediaStorePaths.size} known files")

            val scanRoots =
                collectPreferredScanRoots(
                    externalRoot = externalRoot,
                    mediaStorePaths = mediaStorePaths,
                    explicitAllowedRoots = allowedSet,
                    directoryResolver = directoryResolver
                )

            if (scanRoots.isEmpty()) {
                Log.d(TAG, "No eligible roots found for media scan")
                return@withContext
            }

            // Collect audio files from filesystem that are NOT in MediaStore
            val audioExtensions =
                    setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma", "aiff")
            val newFilesToScan = linkedSetOf<String>()

            scanRoots.forEach { root ->
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        if (dir.isHidden || name.startsWith(".")) return@onEnter false
                        val path = dir.absolutePath
                        if (directoryResolver.isBlocked(path)) return@onEnter false

                        if (File(dir, ".nomedia").exists()) {
                            val isAllowed = allowedSet.any { allowed -> 
                                allowed.absolutePath == path || allowed.absolutePath.startsWith("$path/")
                            }
                            if (!isAllowed) return@onEnter false
                        }

                        // Default Skip Rules (System Folders)
                        val isSystemFolder = (name == "Android" || name == "data" || name == "obb")
                        if (isSystemFolder) {
                            // Check if this specific folder is Explicitly Allowed or is a Parent of an allowed folder
                            // e.g. if Allowed is "Android/media", we MUST enter "Android".
                            // e.g. if Allowed is "Android" (root), we MUST enter "Android".
                            val isAllowed = allowedSet.any { allowed -> 
                                allowed.absolutePath == path || allowed.absolutePath.startsWith("$path/")
                            }
                            
                            if (!isAllowed) {
                                // Apply strict skipping for Android/data and Android/obb if not allowed
                                val parent = dir.parentFile
                                if (name == "Android" && parent?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) return@onEnter false
                                if (parent?.name == "Android" && (name == "data" || name == "obb")) return@onEnter false
                            }
                        }
                        true
                    }
                    .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                    .filter { it.absolutePath !in mediaStorePaths } // Only new files
                    .forEach { newFilesToScan.add(it.absolutePath) }
            }

            if (newFilesToScan.isEmpty()) {
                Log.d(TAG, "No new audio files found - MediaStore is up to date")
                return@withContext
            }

            Log.i(TAG, "Found ${newFilesToScan.size} NEW audio files to scan")

            // Scan only the new files
            val latch = CountDownLatch(1)
            var scannedCount = 0

            MediaScannerConnection.scanFile(
                applicationContext, 
                newFilesToScan.toTypedArray(),
                null
            ) { _, _ ->
                scannedCount++
                if (scannedCount >= newFilesToScan.size) {
                    latch.countDown()
                }
            }

            // Wait for scan to complete (max 15 seconds)
            val completed = latch.await(15, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Media scan timeout after scanning $scannedCount/${newFilesToScan.size} files")
            } else {
                Log.i(TAG, "Media scan completed for ${newFilesToScan.size} new files")
            }
        }
    }

    private fun collectPreferredScanRoots(
        externalRoot: File,
        mediaStorePaths: Set<String>,
        explicitAllowedRoots: Set<File>,
        directoryResolver: DirectoryRuleResolver
    ): List<File> {
        val candidates = linkedSetOf<File>()

        // 1) Explicitly allowed roots always get priority.
        explicitAllowedRoots.forEach { candidates.add(it) }

        // 2) Common user-facing media directories.
        listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_AUDIOBOOKS,
            Environment.DIRECTORY_RINGTONES,
            Environment.DIRECTORY_NOTIFICATIONS
        ).forEach { dirName ->
            candidates.add(Environment.getExternalStoragePublicDirectory(dirName))
        }

        // 3) Top-level buckets already used by current MediaStore entries.
        mediaStorePaths.forEach { mediaPath ->
            val parent = File(mediaPath).parentFile ?: return@forEach
            val parentPath = parent.absolutePath
            if (parentPath.startsWith("${externalRoot.absolutePath}/")) {
                val relative = parentPath.removePrefix(externalRoot.absolutePath).trimStart('/')
                if (relative.isNotEmpty()) {
                    val topLevel = relative.substringBefore('/')
                    if (topLevel.isNotEmpty()) {
                        candidates.add(File(externalRoot, topLevel))
                    }
                }
            } else {
                candidates.add(parent)
            }
        }

        val existingRoots =
            candidates
                .map { it.absoluteFile }
                .distinctBy { it.absolutePath }
                .filter { it.exists() && it.isDirectory }
                .filterNot { directoryResolver.isBlocked(it.absolutePath) }

        if (existingRoots.isEmpty()) return emptyList()
        return pruneNestedRoots(existingRoots)
    }

    private fun pruneNestedRoots(roots: List<File>): List<File> {
        val normalizedRoots = roots.distinctBy { it.absolutePath.trimEnd('/') }
        val kept = mutableListOf<File>()
        normalizedRoots
            .sortedBy { it.absolutePath.length }
            .forEach { candidate ->
                val candidatePath = candidate.absolutePath.trimEnd('/')
                val coveredByParent = kept.any { root ->
                    val rootPath = root.absolutePath.trimEnd('/')
                    candidatePath == rootPath || candidatePath.startsWith("$rootPath/")
                }
                if (!coveredByParent) {
                    kept.add(candidate)
                }
            }
        return kept
    }

    /**
     * Fetches all IDs currently available in MediaStore to identify deleted songs.
     */
    fun fetchMediaStoreIds(directoryResolver: DirectoryRuleResolver): Set<Long> {
        val ids = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = buildLocalAudioSelection(minSongDurationMs)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val data = cursor.getString(dataCol)
                if (data != null) {
                    val parentPath = File(data).parent
                    if (parentPath != null && directoryResolver.isBlocked(File(parentPath).absolutePath)) {
                        continue
                    }
                }
                ids.add(cursor.getLong(idCol))
            }
        }
        return ids
    }

    /**
     * Fetches all file paths currently known to MediaStore.
     * Used to identify new files that need scanning.
     */
    private fun fetchMediaStoreFilePaths(): Set<String> {
        val paths = HashSet<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = buildLocalAudioSelection(minSongDurationMs)
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol >= 0) {
                while (cursor.moveToNext()) {
                    cursor.getString(dataCol)?.let { paths.add(it) }
                }
            }
        }
        return paths
    }
}
