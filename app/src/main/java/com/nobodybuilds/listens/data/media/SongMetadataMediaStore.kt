package com.nobodybuilds.listens.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SongMetadataEditor"

/** MediaStore lookups and updates for edited songs, plus the media rescan after a write. */
internal class SongMetadataMediaStore(
    private val context: Context,
) {

    @RequiresApi(Build.VERSION_CODES.R)
    fun updateMediaStoreMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int?
    ): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.GENRE, genre)
                val encodedTrack = ((discNumber ?: 0) * 1000) + trackNumber
                put(MediaStore.Audio.Media.TRACK, encodedTrack)
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
            }

            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            val success = rowsUpdated > 0

            Timber.d("MediaStore update: $rowsUpdated row(s) affected")
            success

        } catch (e: Exception) {
            Timber.e(e, "Failed to update MediaStore for songId: $songId")
            false
        }
    }

    fun forceMediaRescan(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                Timber.tag(TAG).e("RESCAN: Starting MediaScanner for: $filePath")
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    Timber.tag(TAG).e("RESCAN: Completed for: $path, new URI: $uri")
                }
            } else {
                Timber.tag(TAG).e("RESCAN: File does not exist: $filePath")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("RESCAN ERROR: ${e.message}")
        }
    }

    fun getFilePathFromMediaStore(songId: Long): String? {
        Timber.tag(TAG).e("getFilePathFromMediaStore: Looking up songId: $songId")
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(songId.toString()),
                null
            )?.use { cursor ->
                Timber.tag(TAG).e("getFilePathFromMediaStore: Cursor count: ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    Timber.tag(TAG).e("getFilePathFromMediaStore: Found path: $path")
                    path
                } else {
                    Timber.tag(TAG)
                        .e("getFilePathFromMediaStore: No file found for songId: $songId")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("getFilePathFromMediaStore: Error querying MediaStore: ${e.message}")
            null
        }
    }
    private fun saveCoverArtPreview(songId: Long, coverArtUpdate: CoverArtUpdate): String? {
        return try {
            val extension = imageExtensionFromMimeType(coverArtUpdate.mimeType) ?: "jpg"
            val directory = File(context.cacheDir, "").apply {
                if (!exists()) mkdirs()
            }

            // Clean up old cover art files for this song
            directory.listFiles { file ->
                file.name.startsWith("song_art_${songId}")
            }?.forEach { it.delete() }

            // Save new cover art
            val file = File(directory, "song_art_${songId}_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(coverArtUpdate.bytes)
            }

            file.toUri().toString()
        } catch (e: Exception) {
            Timber.e(e, "Error saving cover art preview for songId: $songId")
            null
        }
    }

    private fun imageExtensionFromMimeType(mimeType: String): String? {
        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }
}
