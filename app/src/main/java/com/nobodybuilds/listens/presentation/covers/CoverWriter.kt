package com.nobodybuilds.listens.presentation.covers

import com.nobodybuilds.listens.data.media.AudioMetadataReader
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import com.nobodybuilds.listens.data.media.ExtendedTagReader
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.health.LibraryHealthAnalyzer
import com.nobodybuilds.listens.presentation.viewmodel.LibraryStateHolder
import com.nobodybuilds.listens.presentation.viewmodel.MetadataEditStateHolder
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class CoverSkipReason(val label: String) {
    NOT_LOCAL("Not a file on this device"),
    FILE_MISSING("File not found"),
    UNREADABLE("Couldn't read the file's tags"),
    HAS_ARTWORK("Already has artwork"),
    SAVE_FAILED("Couldn't save"),
    PERMISSION_DENIED("Permission denied"),
}

sealed interface CoverWriteOutcome {
    data class Saved(val song: Song) : CoverWriteOutcome
    data class Skipped(val reason: CoverSkipReason) : CoverWriteOutcome
}

/**
 * Embeds a made cover into a song file through the artwork-only save path
 * ([MetadataEditStateHolder.saveArtworkOnly]). Only the pictures change; no other tag is
 * written. If the file's tags can't be read, the file is skipped.
 */
class CoverWriter @Inject constructor(
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
) {

    /** What's in the file right now (never the library database). Mirrors LibraryHealthViewModel.readCurrent. */
    private data class FileTags(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val lyrics: String,
        val trackNumber: Int,
        val discNumber: Int?,
        val hasEmbeddedArtwork: Boolean,
    )

    private sealed interface Read {
        data object Missing : Read
        data object Unreadable : Read
        data class Tags(val tags: FileTags) : Read
    }

    private suspend fun readFile(song: Song): Read = withContext(Dispatchers.IO) {
        val file = File(song.path)
        if (!file.exists()) return@withContext Read.Missing
        val meta = runCatching { AudioMetadataReader.read(file) }.getOrNull() ?: return@withContext Read.Unreadable
        val extended = runCatching { ExtendedTagReader.read(file) }.getOrNull() ?: return@withContext Read.Unreadable
        Read.Tags(
            FileTags(
                title = meta.title.orEmpty().trim(),
                artist = meta.artist.orEmpty().trim(),
                album = meta.album.orEmpty().trim(),
                albumArtist = extended.albumArtist.ifBlank { meta.albumArtist.orEmpty() }.trim(),
                genre = meta.genre.orEmpty().trim(),
                lyrics = meta.lyrics.orEmpty(),
                trackNumber = meta.trackNumber ?: 0,
                discNumber = meta.discNumber,
                hasEmbeddedArtwork = meta.artwork != null,
            )
        )
    }

    /**
     * @param keepExisting when true, a file that already has embedded artwork is left alone.
     */
    suspend fun write(song: Song, jpeg: ByteArray, keepExisting: Boolean): CoverWriteOutcome {
        if (!LibraryHealthAnalyzer.isLocal(song)) return CoverWriteOutcome.Skipped(CoverSkipReason.NOT_LOCAL)
        val tags = when (val read = readFile(song)) {
            Read.Missing -> return CoverWriteOutcome.Skipped(CoverSkipReason.FILE_MISSING)
            Read.Unreadable -> return CoverWriteOutcome.Skipped(CoverSkipReason.UNREADABLE)
            is Read.Tags -> read.tags
        }
        if (keepExisting && tags.hasEmbeddedArtwork) return CoverWriteOutcome.Skipped(CoverSkipReason.HAS_ARTWORK)

        val result = try {
            // Artwork-only path: text tags (title, artist, album, track…) are never rewritten.
            metadataEditStateHolder.saveArtworkOnly(
                song = song,
                coverArtUpdate = CoverArtUpdate(bytes = jpeg, mimeType = "image/jpeg"),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cover studio: save threw for ${song.title}")
            null
        }

        val updated = result?.updatedSong
        return if (result != null && result.success && updated != null) {
            libraryStateHolder.updateSong(updated)
            CoverWriteOutcome.Saved(updated)
        } else {
            Timber.w("Cover studio: save failed for ${song.title}: ${result?.error} ${result?.errorMessage}")
            CoverWriteOutcome.Skipped(CoverSkipReason.SAVE_FAILED)
        }
    }
}
