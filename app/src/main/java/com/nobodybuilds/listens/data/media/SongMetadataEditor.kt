package com.nobodybuilds.listens.data.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kyant.taglib.TagLib
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import com.nobodybuilds.listens.data.database.TelegramDao // Added
import com.nobodybuilds.listens.data.database.serializeArtistRefs
import com.nobodybuilds.listens.data.model.ArtistRef
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.worker.collectArtistNames
import com.nobodybuilds.listens.utils.AlbumArtUtils
import com.nobodybuilds.listens.utils.LocalArtworkUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first // Added
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale

private const val TAG = "SongMetadataEditor"

class SongMetadataEditor(
    private val context: Context,
    private val musicDao: MusicDao,
    private val telegramDao: TelegramDao, // Added
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val tagWriters = SongMetadataTagWriters(context)
    private val mediaStoreAccess = SongMetadataMediaStore(context)

    /**
     * Maximum allowed length for metadata fields to prevent buffer overflows
     */
    private object MetadataLimits {
        const val MAX_TITLE_LENGTH = 500
        const val MAX_ARTIST_LENGTH = 500
        const val MAX_ALBUM_LENGTH = 500
        const val MAX_GENRE_LENGTH = 100
        const val MAX_LYRICS_LENGTH = 50_000
    }

    /**
     * Validates metadata input and returns error message if invalid
     */
    private fun validateMetadataInput(
        title: String,
        artist: String,
        album: String,
        genre: String,
        lyrics: String
    ): String? {
        if (title.isBlank()) return "Title cannot be empty"
        if (title.length > MetadataLimits.MAX_TITLE_LENGTH) return "Title too long"
        if (artist.length > MetadataLimits.MAX_ARTIST_LENGTH) return "Artist name too long"
        if (album.length > MetadataLimits.MAX_ALBUM_LENGTH) return "Album name too long"
        if (genre.length > MetadataLimits.MAX_GENRE_LENGTH) return "Genre too long"
        if (lyrics.length > MetadataLimits.MAX_LYRICS_LENGTH) return "Lyrics too long"
        return null
    }

    private fun parseReplayGainUpdate(rawValue: String?, fieldName: String): Result<ReplayGainUpdate> {
        if (rawValue == null) return Result.success(ReplayGainUpdate.Keep)

        val trimmedValue = rawValue.trim()
        if (trimmedValue.isEmpty()) return Result.success(ReplayGainUpdate.Clear)

        val normalizedValue = trimmedValue
            .replace(',', '.')
            .replace(Regex("(?i)\\s*d\\s*b\\s*$"), "")
            .trim()

        val gainDb = normalizedValue.toFloatOrNull()
            ?: return Result.failure(IllegalArgumentException("$fieldName must be a valid dB value"))

        return Result.success(
            ReplayGainUpdate.Set(
                formattedValue = String.format(Locale.US, "%.2f dB", gainDb)
            )
        )
    }

    private suspend fun updateSongArtistMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        trackNumber: Int,
        discNumber: Int?
    ) {
        val existingArtists = musicDao.getAllArtistsListRaw()
        val existingByNormalizedName =
            existingArtists.associateBy { it.name.trim().lowercase(Locale.ROOT) }.toMutableMap()
        var nextArtistId = (musicDao.getMaxArtistId() ?: 0L) + 1L

        val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
        val wordDelimiters = userPreferencesRepository.artistWordDelimitersFlow.first()
        val extractFromTitle = userPreferencesRepository.extractArtistsFromTitleFlow.first()

        val artistNames =
            collectArtistNames(
                rawArtistName = artist,
                title = title,
                artistDelimiters = artistDelimiters,
                wordDelimiters = wordDelimiters,
                extractFromTitle = extractFromTitle
            ).ifEmpty {
                listOf(artist.trim().ifEmpty { "Unknown Artist" })
            }

        val artistsToEnsure = mutableListOf<ArtistEntity>()
        val artistRefs = artistNames.mapIndexed { index, rawName ->
            val normalizedName = rawName.trim()
            val normalizedKey = normalizedName.lowercase(Locale.ROOT)
            val existingArtist = existingByNormalizedName[normalizedKey]
            val artistId =
                if (existingArtist != null) {
                    existingArtist.id
                } else {
                    val newId = nextArtistId++
                    val newArtist = ArtistEntity(
                        id = newId,
                        name = normalizedName,
                        trackCount = 0,
                        imageUrl = null,
                        customImageUri = null
                    )
                    existingByNormalizedName[normalizedKey] = newArtist
                    artistsToEnsure += newArtist
                    newId
                }

            ArtistRef(
                id = artistId,
                name = normalizedName,
                isPrimary = index == 0
            )
        }.filter { it.name.isNotEmpty() }

        val primaryArtistId = artistRefs.firstOrNull()?.id ?: -1L
        val artistsJson = serializeArtistRefs(artistRefs)
        val crossRefs = artistRefs.map { ref ->
            SongArtistCrossRef(
                songId = songId,
                artistId = ref.id,
                isPrimary = ref.isPrimary
            )
        }

        musicDao.updateSongMetadataAndArtistLinks(
            songId = songId,
            title = title,
            artist = artist,
            artistId = primaryArtistId,
            artistsJson = artistsJson,
            album = album,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber,
            artistsToEnsure = artistsToEnsure,
            crossRefs = crossRefs
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun editSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null,
        coverArtUpdate: CoverArtUpdate? = null,
        extendedTags: ExtendedTagUpdate? = null,
    ): SongMetadataEditResult = withContext(Dispatchers.IO) {
        val validationError = validateMetadataInput(newTitle, newArtist, newAlbum, newGenre, newLyrics)
        if (validationError != null) {
            Timber.w("Metadata validation failed: $validationError")
            return@withContext SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.INVALID_INPUT,
                errorMessage = validationError
            )
        }

        try {
            val trimmedLyrics = newLyrics.trim()
            val trimmedGenre = newGenre.trim()
            val normalizedGenre = trimmedGenre.takeIf { it.isNotBlank() }
            val replayGainTrackUpdate = parseReplayGainUpdate(
                rawValue = newReplayGainTrackGainDb,
                fieldName = "Track ReplayGain"
            ).getOrElse { error ->
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.INVALID_INPUT,
                    errorMessage = error.message ?: "Invalid Track ReplayGain value"
                )
            }
            val replayGainAlbumUpdate = parseReplayGainUpdate(
                rawValue = newReplayGainAlbumGainDb,
                fieldName = "Album ReplayGain"
            ).getOrElse { error ->
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.INVALID_INPUT,
                    errorMessage = error.message ?: "Invalid Album ReplayGain value"
                )
            }

            val isTelegramSong = songId < 0
            val filePath = if (isTelegramSong) {
                musicDao.getSongById(songId).first()?.filePath
            } else {
                mediaStoreAccess.getFilePathFromMediaStore(songId)
            }

            if (filePath.isNullOrBlank() && !isTelegramSong) {
                Timber.tag(TAG).e("Could not get file path for songId: $songId")
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.FILE_NOT_FOUND,
                    errorMessage = "Could not find file in media library"
                )
            }

            // Write permission is now handled upstream via MediaStore.createWriteRequest()
            // before this method is called. No File.canWrite() check needed.

            val finalFilePath = filePath ?: ""
            val extension = finalFilePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val detectedContainer = if (finalFilePath.isNotBlank() && File(finalFilePath).exists()) {
                detectContainerFormat(finalFilePath)
            } else {
                DetectedContainer.UNKNOWN
            }
            val effectiveExtension = when {
                detectedContainer == DetectedContainer.OGG_OPUS -> {
                    if (extension != detectedContainer.canonicalExtension) {
                        Timber.tag(TAG).d(
                            "METADATA_EDIT: Detected Ogg Opus stream in .$extension file. " +
                                "Routing write through the Opus-safe metadata path."
                        )
                    }
                    detectedContainer.canonicalExtension
                }
                detectedContainer != DetectedContainer.UNKNOWN &&
                    detectedContainer.canonicalExtension != extension -> {
                    Timber.tag(TAG).w(
                        "METADATA_EDIT: Extension mismatch — filename has .$extension but magic bytes " +
                            "indicate ${detectedContainer.name}. Routing write as .${detectedContainer.canonicalExtension} " +
                            "via temp-file swap to avoid container corruption."
                    )
                    detectedContainer.canonicalExtension
                }
                else -> extension
            }
            val needsExtensionSwap =
                effectiveExtension != extension && detectedContainer != DetectedContainer.OGG_OPUS
            val flacAnalysis = isProblematicFlacFile(finalFilePath)
            val isHighResFlac = flacAnalysis is FlacAnalysisResult.Problematic
            val useVorbisJavaPrimary = effectiveExtension == "opus"
            val useJAudioTaggerPrimary = effectiveExtension in setOf("wav", "ogg") || isHighResFlac
            val fileExists = finalFilePath.isNotBlank() && File(finalFilePath).exists()

            val runPipeline: (String) -> Boolean = { path ->
                if (useVorbisJavaPrimary) {
                    Timber.tag(TAG).d("METADATA_EDIT: Using VorbisJava Opus writer for $effectiveExtension: $path")
                    tagWriters.updateFileMetadataWithVorbisJava(
                        filePath = path,
                        newTitle = newTitle,
                        newArtist = newArtist,
                        newAlbum = newAlbum,
                        newGenre = trimmedGenre,
                        newLyrics = trimmedLyrics,
                        newTrackNumber = newTrackNumber,
                        newDiscNumber = newDiscNumber,
                        replayGainTrackUpdate = replayGainTrackUpdate,
                        replayGainAlbumUpdate = replayGainAlbumUpdate,
                        coverArtUpdate = coverArtUpdate
                    )
                } else if (useJAudioTaggerPrimary) {
                    Timber.tag(TAG).d("METADATA_EDIT: Using JAudioTagger as primary for $effectiveExtension: $path")
                    tagWriters.updateFileMetadataWithJAudioTagger(
                        filePath = path,
                        newTitle = newTitle,
                        newArtist = newArtist,
                        newAlbum = newAlbum,
                        newGenre = trimmedGenre,
                        newLyrics = trimmedLyrics,
                        newTrackNumber = newTrackNumber,
                        newDiscNumber = newDiscNumber,
                        replayGainTrackUpdate = replayGainTrackUpdate,
                        replayGainAlbumUpdate = replayGainAlbumUpdate,
                        coverArtUpdate = coverArtUpdate,
                        extendedTags = extendedTags
                    )
                } else {
                    Timber.tag(TAG).d("METADATA_EDIT: Using TagLib for $effectiveExtension: $path")
                    val tagLibSuccess = tagWriters.updateFileMetadataWithTagLib(
                        filePath = path,
                        newTitle = newTitle,
                        newArtist = newArtist,
                        newAlbum = newAlbum,
                        newGenre = trimmedGenre,
                        newLyrics = trimmedLyrics,
                        newTrackNumber = newTrackNumber,
                        newDiscNumber = newDiscNumber,
                        replayGainTrackUpdate = replayGainTrackUpdate,
                        replayGainAlbumUpdate = replayGainAlbumUpdate,
                        coverArtUpdate = coverArtUpdate,
                        extendedTags = extendedTags
                    )
                    if (!tagLibSuccess) {
                        Timber.tag(TAG)
                            .w("METADATA_EDIT: TagLib failed for $effectiveExtension, falling back to JAudioTagger")
                        tagWriters.updateFileMetadataWithJAudioTagger(
                            filePath = path,
                            newTitle = newTitle,
                            newArtist = newArtist,
                            newAlbum = newAlbum,
                            newGenre = trimmedGenre,
                            newLyrics = trimmedLyrics,
                            newTrackNumber = newTrackNumber,
                            newDiscNumber = newDiscNumber,
                            replayGainTrackUpdate = replayGainTrackUpdate,
                            replayGainAlbumUpdate = replayGainAlbumUpdate,
                            coverArtUpdate = coverArtUpdate,
                            extendedTags = extendedTags
                        )
                    } else true
                }
            }

            val fileUpdateSuccess = if (!fileExists) {
                if (isTelegramSong) {
                    Timber.tag(TAG)
                        .w("METADATA_EDIT: Telegram file not found (streaming?). Skipping file tags, updating DB only.")
                    true
                } else {
                    Timber.tag(TAG).e("METADATA_EDIT: File does not exist: $finalFilePath")
                    false
                }
            } else if (needsExtensionSwap) {
                tagWriters.writeMetadataViaExtensionSwap(finalFilePath, effectiveExtension, runPipeline)
            } else {
                runPipeline(finalFilePath)
            }

            if (!fileUpdateSuccess) {
                Timber.tag(TAG).e("Failed to update file metadata for songId: $songId")
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.TAGLIB_ERROR,
                    errorMessage = "Failed to write metadata to file"
                )
            }

            if (isTelegramSong) {
                val songEntity = musicDao.getSongById(songId).first()
                if (songEntity?.telegramChatId != null && songEntity.telegramFileId != null) {
                    val telegramId = "${songEntity.telegramChatId}_${songEntity.telegramFileId}"
                    val telegramSong = telegramDao.getSongsByIds(listOf(telegramId)).first().firstOrNull()
                    if (telegramSong != null) {
                        val updatedTelegramSong = telegramSong.copy(
                            title = newTitle,
                            artist = newArtist,
                        )
                        telegramDao.insertSongs(listOf(updatedTelegramSong))
                        Timber.d("Updated TelegramDao for song: $telegramId")
                    }
                }
            } else {
                val mediaStoreSuccess = mediaStoreAccess.updateMediaStoreMetadata(
                    songId = songId,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = trimmedGenre,
                    trackNumber = newTrackNumber,
                    discNumber = newDiscNumber
                )
                if (!mediaStoreSuccess) {
                    Timber.w("MediaStore update failed, but file was updated for songId: $songId")
                }
            }

            var storedCoverArtUri: String? = null
            updateSongArtistMetadata(
                songId = songId,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = normalizedGenre,
                trackNumber = newTrackNumber,
                discNumber = newDiscNumber
            )

            extendedTags?.let { extra ->
                runCatching {
                    val current = musicDao.getSongById(songId).first()
                    musicDao.updateSongAlbumArtistAndYear(
                        songId = songId,
                        albumArtist = if (extra.albumArtist != null) extra.albumArtist.trim().ifBlank { null } else current?.albumArtist,
                        year = if (extra.year != null) (extra.yearNumber ?: 0) else (current?.year ?: 0)
                    )
                }.onFailure { Timber.tag(TAG).w(it, "Could not update album artist/year in library") }
            }

            coverArtUpdate?.let {
                AlbumArtUtils.clearCacheForSong(context, songId)
                storedCoverArtUri = if (it.isDeletion) null else LocalArtworkUri.buildSongUriWithTimestamp(songId)
                musicDao.updateSongAlbumArt(songId, storedCoverArtUri)
            }

            if (finalFilePath.isNotBlank()) {
                mediaStoreAccess.forceMediaRescan(finalFilePath)
            }

            Timber.tag(TAG).e("METADATA_EDIT: Successfully updated metadata for songId: $songId")
            SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.NO_WRITE_PERMISSION,
                errorMessage = "Permission denied: ${e.localizedMessage}"
            )
        } catch (e: IOException) {
            Timber.e(e, "IO exception editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.IO_ERROR,
                errorMessage = "Error accessing file: ${e.localizedMessage}"
            )
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OOM editing metadata for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.FILE_CORRUPTED,
                errorMessage = "File too large or corrupted"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata for songId: $songId")
            val errorType = when {
                e.message?.contains("corrupt", ignoreCase = true) == true -> MetadataEditError.FILE_CORRUPTED
                e.message?.contains("unsupported", ignoreCase = true) == true -> MetadataEditError.UNSUPPORTED_FORMAT
                else -> MetadataEditError.UNKNOWN
            }
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = errorType,
                errorMessage = e.localizedMessage ?: "Unknown error occurred"
            )
        }
    }

    /**
     * Artwork-only write (Cover studio): replaces the embedded pictures and nothing else. Text
     * tags (TITLE, ARTIST, ALBUM, TRACKNUMBER, …), ReplayGain and MediaStore text columns are
     * never touched. Refreshes the library's art reference like [editSongMetadata] does for art.
     */
    suspend fun editSongArtwork(
        songId: Long,
        coverArtUpdate: CoverArtUpdate,
    ): SongMetadataEditResult = withContext(Dispatchers.IO) {
        try {
            val filePath = if (songId < 0) {
                musicDao.getSongById(songId).first()?.filePath
            } else {
                mediaStoreAccess.getFilePathFromMediaStore(songId)
            }
            if (filePath.isNullOrBlank() || !File(filePath).exists()) {
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.FILE_NOT_FOUND,
                    errorMessage = "Could not find file in media library"
                )
            }
            val extension = filePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val detectedContainer = detectContainerFormat(filePath)
            val effectiveExtension = when {
                detectedContainer == DetectedContainer.OGG_OPUS -> detectedContainer.canonicalExtension
                detectedContainer != DetectedContainer.UNKNOWN &&
                    detectedContainer.canonicalExtension != extension -> detectedContainer.canonicalExtension
                else -> extension
            }
            val needsExtensionSwap =
                effectiveExtension != extension && detectedContainer != DetectedContainer.OGG_OPUS
            val isHighResFlac = isProblematicFlacFile(filePath) is FlacAnalysisResult.Problematic
            val useVorbisJava = effectiveExtension == "opus"
            val useJAudioTagger = effectiveExtension in setOf("wav", "ogg") || isHighResFlac

            val runPipeline: (String) -> Boolean = { path ->
                when {
                    useVorbisJava -> tagWriters.updateFileMetadataWithVorbisJava(
                        filePath = path,
                        newTitle = "",
                        newArtist = "",
                        newAlbum = "",
                        newGenre = "",
                        newLyrics = "",
                        newTrackNumber = 0,
                        newDiscNumber = null,
                        coverArtUpdate = coverArtUpdate,
                        artworkOnly = true,
                    )
                    useJAudioTagger -> tagWriters.writeArtworkWithJAudioTagger(path, coverArtUpdate)
                    else -> tagWriters.writeArtworkWithTagLib(path, coverArtUpdate) ||
                        tagWriters.writeArtworkWithJAudioTagger(path, coverArtUpdate)
                }
            }
            val ok = if (needsExtensionSwap) {
                tagWriters.writeMetadataViaExtensionSwap(filePath, effectiveExtension, runPipeline)
            } else {
                runPipeline(filePath)
            }
            if (!ok) {
                return@withContext SongMetadataEditResult(
                    success = false,
                    updatedAlbumArtUri = null,
                    error = MetadataEditError.TAGLIB_ERROR,
                    errorMessage = "Failed to write artwork to file"
                )
            }

            AlbumArtUtils.clearCacheForSong(context, songId)
            val storedCoverArtUri =
                if (coverArtUpdate.isDeletion) null else LocalArtworkUri.buildSongUriWithTimestamp(songId)
            musicDao.updateSongAlbumArt(songId, storedCoverArtUri)
            mediaStoreAccess.forceMediaRescan(filePath)
            SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception writing artwork for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.NO_WRITE_PERMISSION,
                errorMessage = "Permission denied: ${e.localizedMessage}"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to write artwork for songId: $songId")
            SongMetadataEditResult(
                success = false,
                updatedAlbumArtUri = null,
                error = MetadataEditError.UNKNOWN,
                errorMessage = e.localizedMessage ?: "Unknown error occurred"
            )
        }
    }
}
