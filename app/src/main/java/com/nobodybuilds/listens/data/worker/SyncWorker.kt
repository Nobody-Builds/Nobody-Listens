package com.nobodybuilds.listens.data.worker

import android.content.ContentResolver
import android.content.Context
import android.os.Trace // Import Trace
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.NeteaseDao
import com.nobodybuilds.listens.data.database.TelegramDao // Added
import com.nobodybuilds.listens.data.navidrome.NavidromeRepository
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.LyricsRepository
import com.nobodybuilds.listens.utils.AlbumArtCacheManager
import com.nobodybuilds.listens.utils.DirectoryRuleResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SyncMode {
    INCREMENTAL,
    FULL,
    REBUILD
}

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val musicDao: MusicDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val lyricsRepository: LyricsRepository,
        private val telegramDao: TelegramDao,
        private val neteaseDao: NeteaseDao,
        private val navidromeRepository: NavidromeRepository
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver
    private var minSongDurationMs: Int = 10000
    private var minTracksPerAlbum: Int = 1

    private val songReader = MediaStoreSongReader(
        applicationContext, contentResolver, musicDao, userPreferencesRepository
    ) { minSongDurationMs }
    private val fileScanner = MediaStoreFileScanner(
        applicationContext, contentResolver, userPreferencesRepository
    ) { minSongDurationMs }
    private val cloudSync = CloudLibrarySync(
        musicDao, userPreferencesRepository, telegramDao, neteaseDao, navidromeRepository
    )

    override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                Trace.beginSection("SyncWorker.doWork")
                try {
                    val syncModeName =
                            inputData.getString(INPUT_SYNC_MODE) ?: SyncMode.INCREMENTAL.name
                    val syncMode = SyncMode.valueOf(syncModeName)
                    val forceMetadata = inputData.getBoolean(INPUT_FORCE_METADATA, false)

                    Timber.tag(TAG)
                        .i("Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)...")
                    val startTime = System.currentTimeMillis()

                    val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
                    val artistWordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
                    val extractArtistsFromTitle = userPreferencesRepository.extractArtistsFromTitleFlow.first()
                    val groupByAlbumArtist =
                            userPreferencesRepository.groupByAlbumArtistFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.artistSettingsRescanRequiredFlow.first()
                    val directoryRulesVersion =
                        userPreferencesRepository.getDirectoryRulesVersion()
                    val lastAppliedDirectoryRulesVersion =
                        userPreferencesRepository.getLastAppliedDirectoryRulesVersion()
                    val directoryRulesChanged =
                        directoryRulesVersion != lastAppliedDirectoryRulesVersion

                    // Feature: Directory Filtering
                    val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
                    val directoryResolver = DirectoryRuleResolver(allowedDirs, blockedDirs)
                    
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    // Smart Duration Filtering
                    minSongDurationMs = userPreferencesRepository.getMinSongDuration()
                    minTracksPerAlbum = userPreferencesRepository.minTracksPerAlbumFlow.first()

                    Timber.tag(TAG)
                        .d(
                            "Artist delimiters=$artistDelimiters, groupByAlbumArtist=$groupByAlbumArtist, " +
                                "rescanRequired=$rescanRequired, directoryRulesChanged=$directoryRulesChanged " +
                                "(current=$directoryRulesVersion, applied=$lastAppliedDirectoryRulesVersion)"
                        )

                    // --- MEDIA SCAN PHASE ---
                    // OPT #8: Filesystem walk cooldown.
                    // triggerMediaScanForNewFiles() calls File.walkTopDown() on the external storage
                    // root, which can touch thousands of inodes and cause noticeable I/O.
                    // For INCREMENTAL syncs we skip it if we already ran within the last hour
                    // (the MediaStore daemon itself picks up new files quickly via inotify).
                    // FULL and REBUILD always run it unconditionally.
                    // Media scan for un-indexed files is slow. Run only if forced, or once every 12 hours.
                    val mediaScanCooldownMs = 12L * 60L * 60L * 1000L // 12 hours
                    val timeSinceLastScan = System.currentTimeMillis() - lastSyncTimestamp
                    val forceFilesystemScan = forceMetadata
                    val shouldRunMediaScan = forceFilesystemScan || 
                            (lastSyncTimestamp > 0L && timeSinceLastScan >= mediaScanCooldownMs)
                    if (shouldRunMediaScan) {
                        fileScanner.triggerMediaScanForNewFiles(directoryResolver)
                    } else {
                        Timber.tag(TAG).d(
                            "Skipping filesystem walk — forceMetadata=$forceMetadata, lastSyncTimestamp=${lastSyncTimestamp}"
                        )
                    }

                    // --- DELETION PHASE ---
                    // Detect and remove deleted songs efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    if (syncMode != SyncMode.REBUILD) {
                        // Only compare MediaStore-backed songs; cloud sources are excluded.
                        val localSongIds = musicDao.getAllMediaStoreSongIds().toHashSet()
                        val mediaStoreIds = fileScanner.fetchMediaStoreIds(directoryResolver)

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localSongIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Timber.tag(TAG)
                                .i("Found ${deletedIds.size} deleted songs. Removing from database...")
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                musicDao.deleteSongsByIds(chunk.toList())
                                musicDao.deleteCrossRefsBySongIds(chunk.toList())
                            }
                        } else {
                            Timber.tag(TAG).d("No deleted songs found.")
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    val isFreshInstall = musicDao.getSongCount().first() == 0

                    // If REBUILD or FULL or RescanRequired or Fresh Install -> Fetch EVERYTHING
                    // (timestamp = 0)
                    // If INCREMENTAL -> Fetch only changes since lastSyncTimestamp
                    val fetchTimestamp =
                            if (syncMode == SyncMode.INCREMENTAL &&
                                            !rescanRequired &&
                                            !directoryRulesChanged &&
                                            !isFreshInstall
                            ) {
                                lastSyncTimestamp /
                                        1000 // Convert to seconds for MediaStore comparison
                            } else {
                                0L
                            }

                    Timber.tag(TAG)
                        .i("Fetching music from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 songs or ~5% of library
                    val progressBatchSize = 50

                    val songsToInsert =
                            songReader.fetchMusicFromMediaStore(
                                    fetchTimestamp,
                                    forceMetadata,
                                    directoryResolver,
                                    syncMode == SyncMode.REBUILD,
                                    progressBatchSize
                            ) { current, total, phaseOrdinal ->
                                setProgress(
                                        workDataOf(
                                                PROGRESS_CURRENT to current,
                                                PROGRESS_TOTAL to total,
                                                PROGRESS_PHASE to phaseOrdinal
                                        )
                                )
                            }

                    Timber.tag(TAG)
                        .i("Fetched ${songsToInsert.size} new/modified songs from MediaStore.")

                    // --- PROCESSING PHASE ---
                    val anySongsFetched = songsToInsert.isNotEmpty()
                    if (anySongsFetched) {

                        val allExistingArtists =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    musicDao.getAllArtistsListRaw()
                                }
                        val allExistingAlbums =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    musicDao.getAllAlbumsList(emptyList(), false, 0)
                                }

                        val existingArtistMetadata =
                                allExistingArtists.associate { it.id to (it.imageUrl to it.customImageUri) }
                        
                        // Load all existing artist IDs to ensure stability across incremental syncs
                        val existingArtistIdMap = allExistingArtists.associate { it.name to it.id }.toMutableMap()
                        val maxArtistId = musicDao.getMaxArtistId() ?: 0L

                        Timber.tag(TAG)
                            .i("Processing ${songsToInsert.size} songs for upsert. Hash: ${songsToInsert.hashCode()}")

                        val (correctedSongs, albums, artists, crossRefs) =
                                preProcessAndDeduplicateWithMultiArtist(
                                        songs = songsToInsert,
                                        artistDelimiters = artistDelimiters,
                                        wordDelimiters = artistWordDelimiters,
                                        extractFromTitle = extractArtistsFromTitle,
                                        groupByAlbumArtist = groupByAlbumArtist,
                                        existingArtistMetadata = existingArtistMetadata,
                                        existingAlbums = allExistingAlbums,
                                        existingArtistIdMap = existingArtistIdMap,
                                        initialMaxArtistId = maxArtistId
                                )

                        // Use incrementalSyncMusicData for all modes except REBUILD
                        // Even for FULL sync, we can just upsert the values
                        if (syncMode == SyncMode.REBUILD) {
                            // Keep clear + insert in one transaction to avoid partial clears
                            // if this worker gets cancelled/replaced mid-rebuild.
                            musicDao.rebuildMusicDataWithCrossRefs(
                                    correctedSongs,
                                    albums,
                                    artists,
                                    crossRefs
                            )
                        } else {
                            // incrementalSyncMusicData handles upserts efficiently
                            // processing deleted songs was already handled at the start
                            musicDao.incrementalSyncMusicData(
                                    songs = correctedSongs,
                                    albums = albums,
                                    artists = artists,
                                    crossRefs = crossRefs,
                                    deletedSongIds = emptyList() // Already handled
                            )
                        }

                        // Clear the rescan required flag
                        userPreferencesRepository.clearArtistSettingsRescanRequired()

                        userPreferencesRepository.markDirectoryRulesVersionApplied(
                            directoryRulesVersion
                        )
                    }

                    // Always update last sync timestamp if we reached this point successfully,
                    // even if no new songs were found in MediaStore. This prevents the sync
                    // from being re-triggered on every app launch when the library is already up to date.
                    userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                    val endTime = System.currentTimeMillis()
                    Timber.tag(TAG)
                        .i("Synchronization finished successfully in ${endTime - startTime}ms.")

                    // Count total songs for the output
                    val totalSongs = musicDao.getSongCount().first()

                    // --- LRC SCANNING PHASE ---
                    val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                    if (autoScanLrc) {
                        Timber.tag(TAG)
                            .i("Auto-scan LRC files enabled. Starting scan phase in chunks...")

                        // Get ALL media store song IDs to scan in manageable chunks
                        val mediaStoreSongIds = musicDao.getAllMediaStoreSongIds()
                        val totalToScan = mediaStoreSongIds.size
                        var totalScannedCount = 0

                        mediaStoreSongIds.chunked(1000).forEach { idBatch ->
                            val batchEntities = musicDao.getSongsByIdsListSimple(idBatch)
                            val batchSongs =
                                    batchEntities.map { entity ->
                                        Song(
                                                id = entity.id.toString(),
                                                title = entity.title,
                                                artist = entity.artistName,
                                                artistId = entity.artistId,
                                                album = entity.albumName,
                                                albumId = entity.albumId,
                                                path = entity.filePath,
                                                contentUriString = entity.contentUriString,
                                                albumArtUriString = entity.albumArtUriString,
                                                duration = entity.duration,
                                                lyrics = entity.lyrics,
                                                dateAdded = entity.dateAdded,
                                                trackNumber = entity.trackNumber,
                                                year = entity.year,
                                                mimeType = entity.mimeType,
                                                bitrate = entity.bitrate,
                                                sampleRate = entity.sampleRate
                                        )
                                    }

                            val batchScannedCount =
                                    lyricsRepository.scanAndAssignLocalLrcFiles(batchSongs) {
                                            current,
                                            total ->
                                        // Progress within the current batch
                                        val overallCurrent = totalScannedCount + current
                                        setProgress(
                                                workDataOf(
                                                        PROGRESS_CURRENT to overallCurrent,
                                                        PROGRESS_TOTAL to totalToScan,
                                                        PROGRESS_PHASE to
                                                                SyncProgress.SyncPhase.SCANNING_LRC
                                                                        .ordinal
                                                )
                                        )
                                    }
                            totalScannedCount += idBatch.size
                            Log.d(
                                    TAG,
                                    "LRC Scan: Processed batch of ${idBatch.size}, total assigned so far: $batchScannedCount"
                            )
                        }

                        Log.i(TAG, "LRC Scan finished for $totalToScan songs.")
                    }

                    // Clean orphaned album art cache files
                    setProgress(
                        workDataOf(
                            PROGRESS_PHASE to SyncProgress.SyncPhase.CLEANING_CACHE.ordinal
                        )
                    )
                    val allSongIds = musicDao.getAllSongIds().toSet()
                    AlbumArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)

                    // Sync cloud songs into the unified songs table.
                    // OPT #7: Guard each source to avoid opening Room transactions when
                    // the user hasn't configured that cloud provider.
                    val hasTelegramChannels = telegramDao.getAllChannels().first().isNotEmpty()
                    val neteaseCount = neteaseDao.getNeteaseCount()
                    // For Navidrome, we only do network sync if SYNC_THRESHOLD_MS (24h) threshold has passed.
                    val navidromeNeedsNetworkSync = navidromeRepository.isLoggedIn && 
                        (System.currentTimeMillis() - navidromeRepository.lastFullSyncTime >= NavidromeRepository.SYNC_THRESHOLD_MS)
                    
                    val needsActiveCloudSync = hasTelegramChannels ||
                        neteaseCount > 0 ||
                        navidromeNeedsNetworkSync

                    if (needsActiveCloudSync) {
                        setProgress(
                            workDataOf(
                                PROGRESS_PHASE to SyncProgress.SyncPhase.SYNCING_CLOUD.ordinal
                            )
                        )
                    }

                    if (hasTelegramChannels) {
                        cloudSync.syncTelegramData()
                    } else {
                        Log.d(TAG, "Skipping Telegram sync — no channels configured.")
                    }

                    // syncNeteaseData already has an internal isEmpty guard; a lightweight
                    // count check here avoids even calling into the function.
                    if (neteaseCount > 0) {
                        cloudSync.syncNeteaseData()
                    } else {
                        Log.d(TAG, "Skipping Netease sync — no songs in local cache.")
                    }

                    if (navidromeRepository.isLoggedIn) {
                        cloudSync.syncNavidromeData()
                    } else {
                        Log.d(TAG, "Skipping Navidrome sync — not logged in.")
                    }

                    // Recalculate total
                    val finalTotalSongs = musicDao.getSongCount().first()

                    Result.success(workDataOf(OUTPUT_TOTAL_SONGS to finalTotalSongs.toLong()))
                } catch (e: Exception) {
                    Log.e(TAG, "Error during MediaStore synchronization", e)
                    Result.failure()
                } finally {
                    Trace.endSection() // End SyncWorker.doWork
                }
            }

    companion object {
        const val WORK_NAME = "com.nobodybuilds.listens.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata"
        const val INPUT_SYNC_MODE = "input_sync_mode"

        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PHASE = "progress_phase"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"

        fun invalidateGenreCache() = MediaStoreGenreCache.invalidateGenreCache()

        fun startUpSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_FORCE_METADATA to deepScan,
                                        INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name
                                )
                        )
                        .build()

        fun incrementalSyncWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name))
                        .build()

        // Full rescans and rebuilds do heavy bulk writes to Room + the album art cache.
        // Requiring non-critical storage prevents partial/corrupt syncs when the device is
        // nearly full. Not applied to incremental/startup sync so the library still appears
        // immediately when the user opens the app.
        private val heavySyncConstraints: Constraints =
                Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()

        fun fullSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_SYNC_MODE to SyncMode.FULL.name,
                                        INPUT_FORCE_METADATA to deepScan
                                )
                        )
                        .setConstraints(heavySyncConstraints)
                        .build()

        fun rebuildDatabaseWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.REBUILD.name))
                        .setConstraints(heavySyncConstraints)
                        .build()
    }
}
