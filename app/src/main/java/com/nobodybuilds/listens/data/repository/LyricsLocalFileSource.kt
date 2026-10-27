package com.nobodybuilds.listens.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.kyant.taglib.TagLib
import com.nobodybuilds.listens.data.database.LyricsDao
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.utils.LogUtils
import com.nobodybuilds.listens.utils.LyricsImportSecurity
import com.nobodybuilds.listens.utils.LyricsImportValidationResult
import com.nobodybuilds.listens.utils.LyricsUtils
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Lyrics from the phone: lyrics files beside the song, and lyrics embedded in its tags. */
internal class LyricsLocalFileSource(
    private val context: Context,
    private val lyricsDao: LyricsDao,
) {

    companion object {
        private const val TAG = "LyricsRepository"
        private const val LOG_OWNER = "LyricsRepositoryImpl"
    }

    /**
     * Find local .lrc file next to the music file (matching Rhythm)
     */
    suspend fun findLocalLyricsFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@withContext null
            val songNameWithoutExt = songFile.nameWithoutExtension

            if (directory.exists()) {
                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val lyricsFile = File(directory, "$songNameWithoutExt.$extension")
                    if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(lyricsFile)
                    if (validated != null) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE: ${lyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }

                val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")

                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val alternativeLyricsFile = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                    if (!alternativeLyricsFile.exists() || !alternativeLyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(alternativeLyricsFile)
                    if (validated != null) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE (alt pattern): ${alternativeLyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for local lyrics file", e)
        }
        return@withContext null
    }

    private fun readValidatedLocalLyrics(file: File): com.nobodybuilds.listens.utils.ValidatedLyricsImport? {
        return when (val validation = LyricsImportSecurity.validateLocalLyricsFile(file)) {
            is LyricsImportValidationResult.Valid -> validation.value
            is LyricsImportValidationResult.Invalid -> null
        }
    }

    /**
     * Load embedded lyrics from audio file metadata
     */
    suspend fun loadEmbeddedLyricsFromMetadata(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // Skip embedded lyrics for Telegram songs (not supported yet/streamed)
        if (song.contentUriString.startsWith("telegram://") || song.contentUriString.isEmpty()) {
            return@withContext null
        }

        // Then try to read from file metadata
        return@withContext try {
            val uri = song.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(LOG_OWNER, "Could not create temp file from URI: ${song.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val propertyMap = metadata?.propertyMap
                    val lyricsField = propertyMap?.get("LYRICS")?.firstOrNull()
                        ?: propertyMap?.get("UNSYNCEDLYRICS")?.firstOrNull()

                    if (!lyricsField.isNullOrBlank()) {
                        val parsedLyrics = LyricsUtils.parseLyrics(lyricsField)
                        if (parsedLyrics.isValid()) {
                            Log.d(TAG, "===== FOUND EMBEDDED LYRICS =====")
                            parsedLyrics.copy(areFromRemote = false)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(LOG_OWNER, e, "Error reading lyrics from file metadata")
            null
        }
    }

    suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(LOG_OWNER, "Starting bulk scan for .lrc files for ${songs.size} songs")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = songs.size

        val idsWithPersistedLyrics = songs
            .mapNotNull { it.id.toLongOrNull() }
            .chunked(900)
            .flatMap { chunk -> lyricsDao.getSongIdsWithLyrics(chunk) }
            .toHashSet()

        // Only scan songs that don't already have persisted lyrics.
        val songsToScan = songs.filter { song ->
            val songId = song.id.toLongOrNull()
            song.lyrics.isNullOrBlank() && (songId == null || songId !in idsWithPersistedLyrics)
        }
        val skippedCount = total - songsToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(LOG_OWNER, "Skipping $skippedCount songs that already have lyrics. Scanning ${songsToScan.size} songs.")
        
        onProgress(processedCount.get(), total)
        
        if (songsToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8) // Limit concurrency

        coroutineScope {
            songsToScan.map { song ->
                async {
                    semaphore.withPermit {
                        try {
                            // Find lyrics file
                            val songFile = File(song.path)
                            val directory = songFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                // Strategy 1: Exact match name
                                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                    val exactMatch = File(directory, "${songFile.nameWithoutExtension}.$extension")
                                    if (exactMatch.exists() && exactMatch.canRead()) {
                                        foundFile = exactMatch
                                        break
                                    }
                                }
                                
                                // Strategy 2: Artist - Title
                                if (foundFile == null) {
                                    val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                        val altMatch = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                                        if (altMatch.exists() && altMatch.canRead()) {
                                            foundFile = altMatch
                                            break
                                        }
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val validated = readValidatedLocalLyrics(foundFile)
                                    if (validated != null) {
                                        try {
                                            lyricsDao.insert(
                                                 com.nobodybuilds.listens.data.database.LyricsEntity(
                                                     songId = song.id.toLong(),
                                                     content = validated.sanitizedContent,
                                                     isSynced = validated.parsedLyrics.synced?.isNotEmpty() == true,
                                                     source = "local_file"
                                                 )
                                            )
                                            updatedCount.incrementAndGet()
                                            LogUtils.d(LOG_OWNER, "Auto-assigned lyrics from ${foundFile.name}")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Skipping DB update for ID in scanner: ${song.id}", e)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning lyrics for ${song.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(LOG_OWNER, "Bulk scan complete. Updated ${updatedCount.get()} songs.")
        return@withContext updatedCount.get()
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("lyrics_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(LOG_OWNER, e, "Error creating temp file from URI")
            null
        }
    }
}
