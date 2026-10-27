package com.nobodybuilds.listens.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Trace // Import Trace
import android.provider.MediaStore
import android.util.Log
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.media.AudioMetadataReader
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.utils.AlbumArtUtils
import com.nobodybuilds.listens.utils.AudioMetaUtils.getAudioMetadata
import com.nobodybuilds.listens.utils.DirectoryRuleResolver
import com.nobodybuilds.listens.utils.LocalArtworkUri
import com.nobodybuilds.listens.utils.buildLocalAudioSelection
import com.nobodybuilds.listens.utils.normalizeMetadataTextOrEmpty
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Reads new and changed songs from MediaStore (plus file tags) into [SongEntity] rows for [SyncWorker]. */
internal class MediaStoreSongReader(
    private val applicationContext: Context,
    private val contentResolver: ContentResolver,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val minSongDurationProvider: () -> Int,
) {

    private companion object {
        private const val TAG = "SyncWorker"
    }

    private val minSongDurationMs: Int get() = minSongDurationProvider()

    private suspend fun fetchGenreMap() = MediaStoreGenreCache.fetchGenreMap(contentResolver)

    /** Raw data extracted from cursor - lightweight class for fast iteration */
    private data class RawSongData(
            val id: Long,
            val albumId: Long,
            val artistId: Long,
            val filePath: String,
            val mimeType: String?,
            val title: String,
            val artist: String,
            val album: String,
            val albumArtist: String?,
            val duration: Long,
            val trackNumber: Int,
            val discNumber: Int?,
            val year: Int,
            val dateModified: Long,
            val genre: String?
    )

    private fun isSongUnchanged(raw: RawSongData, existing: SongEntity?): Boolean {
        if (existing == null) return false

        val lastSlash = raw.filePath.lastIndexOf('/')
        val parentDir = if (lastSlash > 0) raw.filePath.substring(0, lastSlash) else ""
        val existingDateModifiedSeconds = TimeUnit.MILLISECONDS.toSeconds(existing.dateAdded)

        return existing.filePath == raw.filePath &&
            existing.parentDirectoryPath == parentDir &&
            existing.title == raw.title &&
            existing.artistName == raw.artist &&
            existing.albumName == raw.album &&
            existing.albumId == raw.albumId &&
            existing.artistId == raw.artistId &&
            existing.duration == raw.duration &&
            existing.trackNumber == raw.trackNumber &&
            existing.discNumber == raw.discNumber &&
            existing.year == raw.year &&
            existingDateModifiedSeconds == raw.dateModified
    }

    suspend fun fetchMusicFromMediaStore(
            sinceTimestamp: Long, // Seconds
            forceMetadata: Boolean,
            directoryResolver: DirectoryRuleResolver,
            isRebuild: Boolean,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchMusicFromMediaStore")

        val deepScan = forceMetadata
        val genreMap = fetchGenreMap() // Load genres upfront

        val projectionList = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATE_MODIFIED
        )

        // API 30+ supports GENRE in the main audio table
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val projection = projectionList.toTypedArray()

        val (baseSelection, baseArgs) = buildLocalAudioSelection(minSongDurationMs)
        val selectionBuilder = StringBuilder(baseSelection)
        val selectionArgsList = baseArgs.toMutableList()

        // Incremental selection
        if (sinceTimestamp > 0) {
            selectionBuilder.append(
                    " AND (${MediaStore.Audio.Media.DATE_MODIFIED} > ? OR ${MediaStore.Audio.Media.DATE_ADDED} > ?)"
            )
            selectionArgsList.add(sinceTimestamp.toString())
            selectionArgsList.add(sinceTimestamp.toString())
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()

        // Phase 1: Fast cursor iteration to collect raw data
        val rawDataList = mutableListOf<RawSongData>()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val totalCount = cursor.count
                    onProgress(0, totalCount, SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal)

                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val dateModifiedCol =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    } else -1

                    while (cursor.moveToNext()) {
                        try {
                            val data = cursor.getString(dataCol) ?: continue
                            val lastSlash = data.lastIndexOf('/')
                            if (lastSlash > 0) {
                                val normalizedParent = data.substring(0, lastSlash)
                                if (directoryResolver.isBlocked(normalizedParent)) {
                                    continue
                                }
                            }
                        } catch (e: Exception) {
                            // Proceed on error
                        }

                        rawDataList.add(
                                RawSongData(
                                        id = cursor.getLong(idCol),
                                        albumId = cursor.getLong(albumIdCol),
                                        artistId = cursor.getLong(artistIdCol),
                                        filePath = cursor.getString(dataCol) ?: "",
                                        mimeType = if (mimeTypeCol >= 0) cursor.getString(mimeTypeCol) else null,
                                        title =
                                                cursor.getString(titleCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Title" },
                                        artist =
                                                cursor.getString(artistCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Artist" },
                                        album =
                                                cursor.getString(albumCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Album" },
                                        albumArtist =
                                                if (albumArtistCol >= 0)
                                                        cursor.getString(albumArtistCol)
                                                                ?.normalizeMetadataTextOrEmpty()
                                                                ?.takeIf { it.isNotBlank() }
                                                else null,
                                        duration = cursor.getLong(durationCol),
                                        trackNumber = cursor.getInt(trackCol) % 1000,
                                        discNumber = (cursor.getInt(trackCol) / 1000).takeIf { it > 0 },
                                        year = cursor.getInt(yearCol),
                                        dateModified = cursor.getLong(dateModifiedCol),
                                        genre = if (genreCol >= 0) cursor.getString(genreCol) else null
                                )
                        )
                    }
                }

        if (rawDataList.isEmpty()) {
            Log.i(TAG, "MediaStore cursor produced 0 raw songs after directory filtering")
            Trace.endSection()
            return emptyList()
        }

        // Phase 2: Identify changed songs and merge with existing data in chunks
        val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
        val artistWordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
        val rawSongCount = rawDataList.size
        val songsToProcess = if (isRebuild) {
             rawDataList.toList()
        } else {
            // Find existing data for these songs to avoid unnecessary reprocessing
            // and to preserve user edits.
            val results = mutableListOf<RawSongData>()
            
            rawDataList.chunked(500).forEach { batch ->
                val ids = batch.map { it.id }
                val existingMap = musicDao.getSongsByIdsListSimple(ids).associateBy { it.id }
                
                batch.forEach { raw ->
                    val existing = existingMap[raw.id]
                    if (!isSongUnchanged(raw, existing)) {
                        results.add(raw)
                    }
                }
            }
            results
        }

        // rawDataList is no longer needed — release its memory before the processing phase,
        // which may allocate large existingMap objects and metadata ByteArrays.
        rawDataList.clear()

        val totalCount = songsToProcess.size
        Log.i(
            TAG,
            "MediaStore raw=$rawSongCount, songsToProcess=$totalCount, isRebuild=$isRebuild"
        )
        if (totalCount == 0) {
            Trace.endSection()
            return emptyList()
        }

        // Phase 3: Parallel processing of songs with metadata merging
        onProgress(0, totalCount, SyncProgress.SyncPhase.PROCESSING_FILES.ordinal)
        val processedCount = AtomicInteger(0)
        val concurrencyLimit = 4 // Reduced concurrency to save memory
        val semaphore = Semaphore(concurrencyLimit)

        // Process batches sequentially so each batch's existingMap can be GC'd before the next
        // batch is loaded. The semaphore still limits concurrency within each batch.
        val songs = mutableListOf<SongEntity>()
        for (batch in songsToProcess.chunked(200)) {
            val ids = batch.map { it.id }
            val existingMap = if (isRebuild) emptyMap() else musicDao.getSongsByIdsListSimple(ids).associateBy { it.id }
            val batchResults = coroutineScope {
                batch.map { raw ->
                    async {
                        semaphore.withPermit {
                            val localSong = existingMap[raw.id]
                            val mediaStoreSong =
                                processSongData(
                                    raw = raw,
                                    genreMap = genreMap,
                                    deepScan = deepScan,
                                    forceAlbumArtRefresh = deepScan || localSong != null
                                )

                            val song = if (localSong != null) {
                                // Preserve user-edited fields
                                mediaStoreSong.copy(
                                    dateAdded = localSong.dateAdded,
                                    lyrics = localSong.lyrics,
                                    title = if (localSong.title.isNotBlank() && localSong.title != mediaStoreSong.title) localSong.title else mediaStoreSong.title,
                                    artistName = choosePreferredArtistName(
                                        localArtistName = localSong.artistName,
                                        mediaStoreArtistName = mediaStoreSong.artistName,
                                        artistDelimiters = artistDelimiters,
                                        wordDelimiters = artistWordDelimiters
                                    ),
                                    albumName = if (localSong.albumName.isNotBlank() && localSong.albumName != mediaStoreSong.albumName) localSong.albumName else mediaStoreSong.albumName,
                                    genre = localSong.genre ?: mediaStoreSong.genre,
                                    trackNumber = if (localSong.trackNumber != 0) localSong.trackNumber else mediaStoreSong.trackNumber,
                                    discNumber = localSong.discNumber ?: mediaStoreSong.discNumber,
                                    albumArtUriString = mediaStoreSong.albumArtUriString
                                )
                            } else {
                                mediaStoreSong
                            }

                            val count = processedCount.incrementAndGet()
                            if (count % progressBatchSize == 0 || count == totalCount) {
                                onProgress(count, totalCount, SyncProgress.SyncPhase.PROCESSING_FILES.ordinal)
                            }
                            song
                        }
                    }
                }.awaitAll()
            }
            songs.addAll(batchResults)
        }

        Trace.endSection()
        return songs
    }

    /**
     * Checks if a metadata field from MediaStore is a default/unknown placeholder.
     * MediaStore uses `<unknown>` for unreadable fields, and our normalization
     * may fall back to `"Unknown Artist"` / `"Unknown Album"` etc.
     */
    private fun isDefaultMetadata(value: String): Boolean {
        val lower = value.trim().lowercase()
        return lower.isEmpty() ||
            lower == "<unknown>" ||
            lower == "unknown" ||
            lower == "unknown artist" ||
            lower == "unknown album"
    }

    /**
     * Process a single song's raw data into a SongEntity. This is the CPU/IO intensive work that
     * benefits from parallelization.
     */
    private suspend fun processSongData(
            raw: RawSongData,
            genreMap: Map<Long, String>,
            deepScan: Boolean,
            forceAlbumArtRefresh: Boolean
    ): SongEntity {
        val parentDir = java.io.File(raw.filePath).parent ?: ""
        val contentUriString =
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)
                        .toString()

        var albumArtUriString =
                AlbumArtUtils.getAlbumArtUri(
                        applicationContext,
                        raw.filePath,
                        raw.id,
                        forceAlbumArtRefresh
                )
        val audioMetadata =
                if (deepScan) getAudioMetadata(musicDao, raw.id, raw.filePath, true) else null

        var title = raw.title
        var artist = raw.artist
        var album = raw.album
        var albumArtist = resolveAlbumArtist(
            rawAlbumArtist = raw.albumArtist,
            metadataAlbumArtist = null
        )
        var trackNumber = raw.trackNumber
        var discNumber = raw.discNumber
        var year = raw.year
        var genre: String? = genreMap[raw.id] ?: raw.genre // Use mapped genre as default, or direct genre from main cursor

        val shouldAugmentMetadata =
                deepScan ||
                        raw.filePath.endsWith(".wav", true) ||
                        raw.filePath.endsWith(".opus", true) ||
                        raw.filePath.endsWith(".ogg", true) ||
                        raw.filePath.endsWith(".oga", true) ||
                        raw.filePath.endsWith(".aiff", true) ||
                        // Fallback: if MediaStore returned default/missing metadata,
                        // try TagLib+JAudioTagger to read actual tags from the file.
                        // MediaStore uses "<unknown>" for unreadable fields;
                        // our normalization may produce "Unknown Artist"/"Unknown Album".
                        isDefaultMetadata(raw.artist) ||
                        isDefaultMetadata(raw.album)

        if (shouldAugmentMetadata) {
            val file = java.io.File(raw.filePath)
            if (file.exists()) {
                try {
                    AudioMetadataReader.read(file)?.let { meta ->
                        if (!meta.title.isNullOrBlank()) title = meta.title
                        if (!meta.artist.isNullOrBlank()) artist = meta.artist
                        if (!meta.album.isNullOrBlank()) album = meta.album
                        albumArtist = resolveAlbumArtist(
                            rawAlbumArtist = albumArtist,
                            metadataAlbumArtist = meta.albumArtist
                        )
                        if (!meta.genre.isNullOrBlank()) genre = meta.genre
                        if (meta.trackNumber != null) trackNumber = meta.trackNumber
                        if (meta.discNumber != null) discNumber = meta.discNumber
                        if (meta.year != null) year = meta.year

                        meta.artwork?.let { art ->
                            albumArtUriString = LocalArtworkUri.buildSongUri(raw.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read metadata via TagLib for ${raw.filePath}", e)
                }
            }
        }

        return SongEntity(
                id = raw.id,
                title = title,
                artistName = artist,
                artistId = raw.artistId,
                albumArtist = albumArtist,
                albumName = album,
                albumId = raw.albumId,
                contentUriString = contentUriString,
                albumArtUriString = albumArtUriString,
                duration = raw.duration,
                genre = genre,
                filePath = raw.filePath,
                parentDirectoryPath = parentDir,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                dateAdded =
                        raw.dateModified.let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds)
                            else System.currentTimeMillis()
                        },
                mimeType = audioMetadata?.mimeType ?: raw.mimeType,
                sampleRate = audioMetadata?.sampleRate,
                bitrate = audioMetadata?.bitrate,
                sourceType = SourceType.LOCAL
        )
    }
}
