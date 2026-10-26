package com.nobodybuilds.listens.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SongMetadataEditor"

/** Writes tags and artwork into the audio file itself (TagLib, JAudioTagger, VorbisJava for Opus). */
internal class SongMetadataTagWriters(
    private val context: Context,
) {

    /** TagLib: replaces only the pictures; the property map (text tags) is left as it is in the file. */
    fun writeArtworkWithTagLib(filePath: String, update: CoverArtUpdate): Boolean {
        val pictures = when {
            update.isDeletion -> emptyArray<Picture>()
            update.bytes != null -> arrayOf(
                Picture(
                    data = update.bytes,
                    description = "Front Cover",
                    pictureType = "Front Cover",
                    mimeType = update.mimeType
                )
            )
            else -> return false
        }
        return try {
            val audioFile = File(filePath)
            val saved = ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                TagLib.savePictures(fd.dup().detachFd(), pictures)
            }
            if (saved) {
                runCatching { java.io.RandomAccessFile(audioFile, "rw").use { it.fd.sync() } }
            } else {
                Timber.tag(TAG).w("TAGLIB: savePictures failed for $filePath")
            }
            saved
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "TAGLIB: artwork-only write failed for $filePath")
            false
        }
    }

    /** JAudioTagger: deleteArtworkField + setField(artwork) + commit; other fields untouched. */
    fun writeArtworkWithJAudioTagger(filePath: String, update: CoverArtUpdate): Boolean {
        return try {
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
            val audioFile = AudioFileIO.read(File(filePath))
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            if (update.isDeletion) {
                tag.deleteArtworkField()
            } else {
                val bytes = update.bytes ?: return false
                val artwork = AndroidArtwork()
                artwork.binaryData = bytes
                artwork.mimeType = update.mimeType
                artwork.pictureType = 3 // Front cover
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                artwork.width = options.outWidth
                artwork.height = options.outHeight
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audioFile.tag = tag
            audioFile.commit()
            true
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "JAUDIOTAGGER: artwork-only write failed for $filePath")
            false
        }
    }

    /**
     * Copies [originalPath] to a temp file with [correctExtension], runs [writer] on the temp
     * path (so JAudioTagger/TagLib pick the correct parser by extension), and streams the edited
     * bytes back into the original location. The original filename/extension are preserved so
     * MediaStore URIs stay valid; only the file's byte content is replaced.
     */
    fun writeMetadataViaExtensionSwap(
        originalPath: String,
        correctExtension: String,
        writer: (String) -> Boolean
    ): Boolean {
        val originalFile = File(originalPath)
        val tempFile = File(
            context.cacheDir,
            "metadata_edit_${System.nanoTime()}.$correctExtension"
        )
        return try {
            originalFile.inputStream().use { input ->
                FileOutputStream(tempFile).use { out -> input.copyTo(out) }
            }
            Timber.tag(TAG).d(
                "METADATA_EDIT: Copied ${originalFile.length()} bytes to temp ${tempFile.name} for extension-corrected write"
            )
            val writeOk = writer(tempFile.absolutePath)
            if (!writeOk) {
                Timber.tag(TAG).e("METADATA_EDIT: Writer failed on temp file ${tempFile.absolutePath}")
                return false
            }
            // Stream edited bytes back to the original path (truncate + overwrite).
            tempFile.inputStream().use { input ->
                FileOutputStream(originalFile, false).use { out -> input.copyTo(out) }
            }
            Timber.tag(TAG).d(
                "METADATA_EDIT: Restored ${tempFile.length()} edited bytes back to $originalPath"
            )
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "METADATA_EDIT: Extension-swap pipeline failed for $originalPath")
            false
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                Timber.tag(TAG).w("METADATA_EDIT: Could not delete temp file ${tempFile.absolutePath}")
            }
        }
    }

    fun updateFileMetadataWithTagLib(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        replayGainTrackUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        replayGainAlbumUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        coverArtUpdate: CoverArtUpdate? = null,
        extendedTags: ExtendedTagUpdate? = null
    ): Boolean {
        // Check for problematic FLAC files first
        when (val flacResult = isProblematicFlacFile(filePath)) {
            is FlacAnalysisResult.Problematic -> {
                Timber.tag(TAG)
                    .w("TAGLIB: Skipping file modification for high-resolution FLAC (${flacResult.sampleRate}Hz, ${flacResult.bitsPerSample}bit)")
                Timber.tag(TAG)
                    .w("TAGLIB: High-res FLAC files may not work correctly with TagLib. Will update MediaStore only.")
                // Return true to indicate we should proceed with MediaStore-only update
                // The calling code will still update MediaStore and local DB
                return true
            }
            else -> { /* Continue with normal processing */ }
        }
        
        return try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Timber.tag(TAG).e("TAGLIB: Audio file does not exist: $filePath")
                return false
            }
            Timber.tag(TAG).e("TAGLIB: Opening file: $filePath")

            // Open file with read/write permissions
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                // Get existing metadata or create empty map
                Timber.tag(TAG).e("TAGLIB: Getting existing metadata...")
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                Timber.tag(TAG)
                    .e("TAGLIB: Existing metadata: ${existingMetadata?.propertyMap?.keys}")
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                // Update metadata fields
                propertyMap["TITLE"] = arrayOf(newTitle)
                propertyMap["ARTIST"] = arrayOf(newArtist)
                propertyMap["ALBUM"] = arrayOf(newAlbum)
                propertyMap.upsertOrRemove("GENRE", newGenre)
                propertyMap.upsertOrRemove("LYRICS", newLyrics)
                propertyMap["TRACKNUMBER"] = arrayOf(newTrackNumber.toString())
                if (newDiscNumber != null && newDiscNumber > 0) {
                    propertyMap["DISCNUMBER"] = arrayOf(newDiscNumber.toString())
                } else {
                    propertyMap.remove("DISCNUMBER")
                }
                if (extendedTags?.albumArtist != null) {
                    propertyMap.upsertOrRemove("ALBUMARTIST", extendedTags.albumArtist.trim())
                } else {
                    propertyMap["ALBUMARTIST"] = arrayOf(newArtist)
                }
                extendedTags?.year?.let { propertyMap.upsertOrRemove("DATE", it.trim()) }
                extendedTags?.composer?.let { propertyMap.upsertOrRemove("COMPOSER", it.trim()) }
                extendedTags?.musicBrainzRecordingId?.takeIf { it.isNotBlank() }?.let { propertyMap["MUSICBRAINZ_TRACKID"] = arrayOf(it) }
                extendedTags?.musicBrainzReleaseId?.takeIf { it.isNotBlank() }?.let { propertyMap["MUSICBRAINZ_ALBUMID"] = arrayOf(it) }
                extendedTags?.musicBrainzArtistId?.takeIf { it.isNotBlank() }?.let { propertyMap["MUSICBRAINZ_ARTISTID"] = arrayOf(it) }
                extendedTags?.musicBrainzReleaseGroupId?.takeIf { it.isNotBlank() }?.let { propertyMap["MUSICBRAINZ_RELEASEGROUPID"] = arrayOf(it) }
                propertyMap.applyReplayGainUpdate(REPLAYGAIN_TRACK_GAIN_KEY, replayGainTrackUpdate)
                propertyMap.applyReplayGainUpdate(REPLAYGAIN_ALBUM_GAIN_KEY, replayGainAlbumUpdate)
                Timber.tag(TAG).e("TAGLIB: Updated property map, saving...")

                // Save metadata
                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                Timber.tag(TAG).e("TAGLIB: savePropertyMap result: $metadataSaved")
                if (!metadataSaved) {
                    Timber.tag(TAG).e("TAGLIB: Failed to save metadata for file: $filePath")
                    return false
                }

                // Update cover art if provided
                coverArtUpdate?.let { update ->
                    val pictures = if (update.isDeletion) {
                        emptyArray<Picture>()
                    } else if (update.bytes != null) {
                        arrayOf(
                            Picture(
                                data = update.bytes,
                                description = "Front Cover",
                                pictureType = "Front Cover",
                                mimeType = update.mimeType
                            )
                        )
                    } else {
                        null
                    }

                    pictures?.let {
                        val pictureFd = fd.dup()
                        val coverSaved = TagLib.savePictures(pictureFd.detachFd(), it)
                        if (!coverSaved) {
                            Timber.tag(TAG)
                                .w("TAGLIB: Failed to save cover art, but metadata was saved")
                        } else {
                            Timber.tag(TAG)
                                .d("TAGLIB: Successfully ${if (update.isDeletion) "removed" else "embedded"} cover art")
                        }
                    }
                }
            }

            // Force file system sync to ensure data is written to disk
            try {
                java.io.RandomAccessFile(audioFile, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not sync file, changes should still be persisted")
            }

            Timber.tag(TAG).e("TAGLIB: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Timber.tag(TAG).e("TAGLIB ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun updateFileMetadataWithJAudioTagger(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        replayGainTrackUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        replayGainAlbumUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        coverArtUpdate: CoverArtUpdate? = null,
        extendedTags: ExtendedTagUpdate? = null
    ): Boolean {
        val targetFile = File(filePath)
        
        return try {
            // Suppress JAudioTagger's verbose logging
            java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
            
            val audioFile = AudioFileIO.read(targetFile)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            
            // Update text fields
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            val albumArtistValue = extendedTags?.albumArtist?.trim()
            when {
                albumArtistValue == null -> tag.setField(FieldKey.ALBUM_ARTIST, newArtist)
                albumArtistValue.isBlank() -> tag.deleteField(FieldKey.ALBUM_ARTIST)
                else -> tag.setField(FieldKey.ALBUM_ARTIST, albumArtistValue)
            }
            extendedTags?.year?.trim()?.let { year ->
                if (year.isBlank()) tag.deleteField(FieldKey.YEAR) else tag.setField(FieldKey.YEAR, year)
            }
            extendedTags?.composer?.trim()?.let { composer ->
                if (composer.isBlank()) tag.deleteField(FieldKey.COMPOSER) else tag.setField(FieldKey.COMPOSER, composer)
            }
            runCatching {
                extendedTags?.musicBrainzRecordingId?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.MUSICBRAINZ_TRACK_ID, it) }
                extendedTags?.musicBrainzReleaseId?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.MUSICBRAINZ_RELEASEID, it) }
                extendedTags?.musicBrainzArtistId?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.MUSICBRAINZ_ARTISTID, it) }
                extendedTags?.musicBrainzReleaseGroupId?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID, it) }
            }.onFailure { Timber.tag(TAG).w(it, "JAUDIOTAGGER: Could not write MusicBrainz IDs") }
            
            if (newGenre.isNotBlank()) {
                tag.setField(FieldKey.GENRE, newGenre)
            } else {
                tag.deleteField(FieldKey.GENRE)
            }
            
            if (newLyrics.isNotBlank()) {
                tag.setField(FieldKey.LYRICS, newLyrics)
            } else {
                tag.deleteField(FieldKey.LYRICS)
            }
            
            tag.setField(FieldKey.TRACK, newTrackNumber.toString())
            if (newDiscNumber != null && newDiscNumber > 0) {
                tag.setField(FieldKey.DISC_NO, newDiscNumber.toString())
            } else {
                tag.deleteField(FieldKey.DISC_NO)
            }
            tag.applyReplayGainUpdate(REPLAYGAIN_TRACK_GAIN_KEY, replayGainTrackUpdate)
            tag.applyReplayGainUpdate(REPLAYGAIN_ALBUM_GAIN_KEY, replayGainAlbumUpdate)

            // Update cover art if provided
            coverArtUpdate?.let { update ->
                if (update.isDeletion) {
                    tag.deleteArtworkField()
                    Timber.tag(TAG).d("JAUDIOTAGGER: Removed cover art")
                } else if (update.bytes != null) {
                    try {
                        tag.deleteArtworkField()
                        val artwork = AndroidArtwork()
                        artwork.binaryData = update.bytes
                        artwork.mimeType = update.mimeType
                        artwork.pictureType = 3 // Standard value for "Front Cover"
                        
                        // Extract dimensions using Android native BitmapFactory to avoid ImageIO crash
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(update.bytes, 0, update.bytes.size, options)
                        artwork.width = options.outWidth
                        artwork.height = options.outHeight
                        
                        tag.setField(artwork)
                        Timber.tag(TAG)
                            .d("JAUDIOTAGGER: Embedded new cover art (${update.mimeType}, ${options.outWidth}x${options.outHeight})")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "JAUDIOTAGGER: Failed to create artwork from bytes")
                    }
                } else {
                    Timber.tag(TAG).w("JAUDIOTAGGER: Ignoring invalid CoverArtUpdate with no bytes and no deletion flag")
                }
            }

            audioFile.commit()
            Timber.tag(TAG).d("JAUDIOTAGGER: SUCCESS - Updated file metadata: $filePath")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e("JAUDIOTAGGER ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun updateFileMetadataWithVorbisJava(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        replayGainTrackUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        replayGainAlbumUpdate: ReplayGainUpdate = ReplayGainUpdate.Keep,
        coverArtUpdate: CoverArtUpdate? = null,
        /** True: only the picture comment changes; every text comment stays as it is. */
        artworkOnly: Boolean = false
    ): Boolean {
        val audioFile = File(filePath)
        val originalExtension = audioFile.extension.ifBlank { "opus" }
        var tempFile: File? = null
        var opusFile: OpusFile? = null
        
        return try {
            if (!audioFile.exists()) {
                Timber.tag(TAG).e("VORBISJAVA: Audio file does not exist: $filePath")
                return false
            }

            Timber.tag(TAG).e("VORBISJAVA: Reading Opus file: $filePath")
            
            // Read existing file
            val sourceOpusFile = OpusFile(audioFile)
            opusFile = sourceOpusFile
            val tags = sourceOpusFile.tags ?: OpusTags()

            Timber.tag(TAG).e("VORBISJAVA: Existing tags: ${tags.allComments}")
            
            if (!artworkOnly) {
                tags.replaceSingleComment("TITLE", newTitle)
                tags.replaceSingleComment("ARTIST", newArtist)
                tags.replaceSingleComment("ALBUMARTIST", newArtist)
                tags.replaceSingleComment("ALBUM", newAlbum)
                tags.replaceSingleComment("GENRE", newGenre)
                tags.replaceSingleComment("LYRICS", newLyrics)
                tags.replaceSingleComment("TRACKNUMBER", newTrackNumber.takeIf { it > 0 }?.toString())
                tags.replaceSingleComment("DISCNUMBER", newDiscNumber?.takeIf { it > 0 }?.toString())
                tags.applyReplayGainUpdate(REPLAYGAIN_TRACK_GAIN_KEY, replayGainTrackUpdate)
                tags.applyReplayGainUpdate(REPLAYGAIN_ALBUM_GAIN_KEY, replayGainAlbumUpdate)
            }
            coverArtUpdate?.let { update ->
                tags.applyCoverArtUpdate(update)
            }

            Timber.tag(TAG).e("VORBISJAVA: Updated tags: ${tags.allComments}")
            
            tempFile = File(
                context.cacheDir,
                "metadata_edit_opus_${System.nanoTime()}.$originalExtension"
            )
            
            Timber.tag(TAG).e("VORBISJAVA: Writing to temp file: ${tempFile.path}")
            FileOutputStream(tempFile).use { fos ->
                OpusFile(fos, sourceOpusFile.info, tags).use { newOpusFile ->
                    var packet = sourceOpusFile.nextAudioPacket
                    while (packet != null) {
                        newOpusFile.writeAudioData(packet)
                        packet = sourceOpusFile.nextAudioPacket
                    }
                }
            }
            sourceOpusFile.close()
            opusFile = null
            
            // Verify temp file was created and has content
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Timber.tag(TAG).e("VORBISJAVA: Temp file creation failed or is empty")
                return false
            }
            Timber.tag(TAG)
                .e("VORBISJAVA: Temp file size: ${tempFile.length()} bytes, original: ${audioFile.length()} bytes")
            
            tempFile.inputStream().use { input ->
                FileOutputStream(audioFile, false).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            Timber.tag(TAG).e("VORBISJAVA: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Timber.tag(TAG).e("VORBISJAVA ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            try {
                opusFile?.close()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "VORBISJAVA: Could not close source Opus file")
            }
            if (tempFile?.exists() == true && tempFile?.delete() == false) {
                Timber.tag(TAG).w("VORBISJAVA: Could not delete temp file ${tempFile?.absolutePath}")
            }
        }
    }
}
