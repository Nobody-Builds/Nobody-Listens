package com.nobodybuilds.listens.data.service.http

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.AlbumArtUtils
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

internal data class AudioStreamSource(
    val sourceLabel: String,
    val fileSize: Long,
    val lastModifiedEpochMs: Long?,
    val inputStreamFactory: () -> InputStream
)

internal data class ArtStreamSource(
    val sourceLabel: String,
    val contentType: ContentType,
    val contentLength: Long?,
    val lastModifiedEpochMs: Long?,
    val inputStreamFactory: () -> InputStream
)

/** Finds a song, its audio bytes and its artwork for the Cast HTTP server. */
internal class CastMediaSourceResolver(
    private val context: Context,
    private val musicRepository: MusicRepository
) {
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private val transparentPng1x1: ByteArray by lazy {
        byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )
    }

    fun resolveAudioStreamSource(song: Song, uri: Uri): AudioStreamSource? {
        val fallbackFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile && it.canRead() }
        val sizeFromProvider = queryContentLengthFromProvider(uri)
        val songLastModified = resolveSongLastModifiedEpochMs(song, fallbackFile)

        var hasAssetDescriptor = false
        var assetDescriptorSize = -1L
        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                hasAssetDescriptor = true
                assetDescriptorSize = afd.length
                    .takeIf { it > 0L }
                    ?: afd.declaredLength.takeIf { it > 0L }
                    ?: -1L
            }
        }.onFailure { throwable ->
            Timber.tag(CAST_HTTP_LOG_TAG).d(throwable, "openAssetFileDescriptor failed. songId=%s", song.id)
        }
        if (hasAssetDescriptor) {
            val resolvedSize = when {
                assetDescriptorSize > 0L -> assetDescriptorSize
                sizeFromProvider > 0L -> sizeFromProvider
                fallbackFile != null -> fallbackFile.length()
                else -> -1L
            }
            return AudioStreamSource(
                sourceLabel = "asset_fd",
                fileSize = resolvedSize,
                lastModifiedEpochMs = songLastModified
            ) {
                contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
                    ?: throw IllegalStateException("AssetFileDescriptor unavailable for uri=$uri songId=${song.id}")
            }
        }

        var hasFileDescriptor = false
        var fdSize = -1L
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                hasFileDescriptor = true
                fdSize = pfd.statSize.takeIf { it > 0L } ?: -1L
            }
        }.onFailure { throwable ->
            Timber.tag(CAST_HTTP_LOG_TAG).d(throwable, "openFileDescriptor failed. songId=%s", song.id)
        }
        if (hasFileDescriptor) {
            val resolvedSize = when {
                fdSize > 0L -> fdSize
                sizeFromProvider > 0L -> sizeFromProvider
                fallbackFile != null -> fallbackFile.length()
                else -> -1L
            }
            return AudioStreamSource(
                sourceLabel = "parcel_fd",
                fileSize = resolvedSize,
                lastModifiedEpochMs = songLastModified
            ) {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("ParcelFileDescriptor unavailable for uri=$uri songId=${song.id}")
                ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }

        val hasInputStream = runCatching {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        }.onFailure { throwable ->
            Timber.tag(CAST_HTTP_LOG_TAG).d(throwable, "openInputStream probe failed. songId=%s", song.id)
        }.getOrDefault(false)
        if (hasInputStream) {
            return AudioStreamSource(
                sourceLabel = "content_stream",
                fileSize = when {
                    sizeFromProvider > 0L -> sizeFromProvider
                    fallbackFile != null -> fallbackFile.length()
                    else -> -1L
                },
                lastModifiedEpochMs = songLastModified
            ) {
                contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("InputStream unavailable for uri=$uri songId=${song.id}")
            }
        }

        if (fallbackFile != null) {
            return AudioStreamSource(
                sourceLabel = "file_path",
                fileSize = fallbackFile.length(),
                lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, fallbackFile)
            ) {
                FileInputStream(fallbackFile)
            }
        }
        return null
    }

    suspend fun resolveSongForServing(songId: String): Song? {
        val repositorySong = musicRepository.getSong(songId).firstOrNull()
        if (repositorySong != null) {
            return repositorySong
        }

        val id = songId.toLongOrNull() ?: return null
        Timber.tag(CAST_HTTP_LOG_TAG).w(
            "Song not found in repository. Falling back to MediaStore query for songId=%s",
            songId
        )
        Timber.tag("PX_CAST_HTTP").w("song_resolver repo_miss songId=$songId")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        return runCatching {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                val songIdLong = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val path = cursor.getString(dataCol).orEmpty()
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songIdLong)
                val albumArtUri = AlbumArtUtils.getAlbumArtUri(
                    appContext = context,
                    path = path,
                    songId = songIdLong,
                    forceRefresh = false
                )

                Song(
                    id = songIdLong.toString(),
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    artistId = cursor.getLong(artistIdCol),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = albumId,
                    albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null,
                    path = path,
                    contentUriString = contentUri.toString(),
                    albumArtUriString = albumArtUri,
                    duration = cursor.getLong(durationCol),
                    trackNumber = cursor.getInt(trackCol),
                    year = cursor.getInt(yearCol),
                    dateAdded = cursor.getLong(dateAddedCol),
                    dateModified = cursor.getLong(dateModifiedCol),
                    mimeType = if (mimeTypeCol >= 0) cursor.getString(mimeTypeCol) else null,
                    bitrate = null,
                    sampleRate = null
                )
            }
        }.onSuccess { resolved ->
            if (resolved != null) {
                Timber.tag("PX_CAST_HTTP")
                    .i("song_resolver media_store_hit songId=$songId mime=${resolved.mimeType} path=${resolved.path}")
            } else {
                Timber.tag("PX_CAST_HTTP").e("song_resolver media_store_miss songId=$songId")
            }
        }.onFailure { throwable ->
            Timber.tag(CAST_HTTP_LOG_TAG).e(throwable, "MediaStore fallback failed for songId=%s", songId)
            Timber.tag("PX_CAST_HTTP")
                .e(throwable, "song_resolver media_store_error songId=$songId")
        }.getOrNull()
    }

    fun resolveArtStreamSource(song: Song): ArtStreamSource {
        val albumArtUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.toUri()

        if (albumArtUri != null) {
            val uriMimeType = runCatching { contentResolver.getType(albumArtUri) }.getOrNull()
            val contentType = resolveImageContentType(
                mimeType = uriMimeType,
                uriPath = albumArtUri.path
            )
            val contentLength = runCatching {
                contentResolver.openAssetFileDescriptor(albumArtUri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0L } ?: afd.declaredLength.takeIf { it > 0L }
                }
            }.getOrNull()
            val canOpenStream = runCatching {
                contentResolver.openInputStream(albumArtUri)?.use { true } ?: false
            }.onFailure { throwable ->
                if (throwable is java.io.FileNotFoundException && throwable.message?.contains("No content provider") == true) {
                    Timber.tag(CAST_HTTP_LOG_TAG).d("Album art URI probe expected fail (No content provider). songId=%s", song.id)
                } else {
                    Timber.tag(CAST_HTTP_LOG_TAG).d(throwable, "Album art URI probe failed. songId=%s", song.id)
                }
            }.getOrDefault(false)

            if (canOpenStream) {
                return ArtStreamSource(
                    sourceLabel = "album_art_uri",
                    contentType = contentType,
                    contentLength = contentLength,
                    lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, null)
                ) {
                    contentResolver.openInputStream(albumArtUri)
                        ?: throw IllegalStateException("Unable to open albumArt uri=$albumArtUri songId=${song.id}")
                }
            }
        }

        val embeddedArt = extractEmbeddedAlbumArt(song)
        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
            return ArtStreamSource(
                sourceLabel = "embedded_picture",
                contentType = resolveEmbeddedImageContentType(embeddedArt),
                contentLength = embeddedArt.size.toLong(),
                lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, null)
            ) {
                ByteArrayInputStream(embeddedArt)
            }
        }

        return placeholderArtSource()
    }

    private fun extractEmbeddedAlbumArt(song: Song): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = song.contentUriString.toUri()
            val sourceConfigured = runCatching {
                retriever.setDataSource(context, uri)
                true
            }.getOrElse {
                val path = song.path.takeIf { value -> value.isNotBlank() } ?: return null
                runCatching {
                    retriever.setDataSource(path)
                    true
                }.getOrDefault(false)
            }
            if (!sourceConfigured) {
                null
            } else {
                retriever.embeddedPicture
            }
        } catch (e: Exception) {
            Timber.tag(CAST_HTTP_LOG_TAG).d(e, "Embedded album art extraction failed. songId=%s", song.id)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveEmbeddedImageContentType(bytes: ByteArray): ContentType {
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> ContentType.Image.JPEG

            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> ContentType.Image.PNG

            bytes.size >= 6 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> ContentType.Image.GIF

            else -> ContentType.Image.JPEG
        }
    }

    fun placeholderArtSource(): ArtStreamSource {
        return ArtStreamSource(
            sourceLabel = "placeholder_png",
            contentType = ContentType.Image.PNG,
            contentLength = transparentPng1x1.size.toLong(),
            lastModifiedEpochMs = null
        ) {
            ByteArrayInputStream(transparentPng1x1)
        }
    }

    private fun queryContentLengthFromProvider(uri: Uri): Long {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumnIndex == -1 || !cursor.moveToFirst()) {
                    return@use -1L
                }
                cursor.getLong(sizeColumnIndex)
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun resolveSongLastModifiedEpochMs(song: Song, fallbackFile: File?): Long? {
        val fileTimestamp = fallbackFile?.lastModified()?.takeIf { it > 0L }
        val songTimestamp = normalizeEpochMillis(song.dateModified)
            ?: normalizeEpochMillis(song.dateAdded)
        return fileTimestamp ?: songTimestamp
    }

    private fun normalizeEpochMillis(value: Long): Long? {
        if (value <= 0L) return null
        return if (value < 10_000_000_000L) value * 1000L else value
    }

    private fun resolveImageContentType(mimeType: String?, uriPath: String?): ContentType {
        val normalizedMime = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
        if (!normalizedMime.isNullOrBlank()) {
            return runCatching { ContentType.parse(normalizedMime) }
                .getOrElse { ContentType.Image.JPEG }
        }

        val extension = uriPath
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        return when (extension) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "webp" -> runCatching { ContentType.parse("image/webp") }.getOrElse { ContentType.Image.JPEG }
            "gif" -> ContentType.Image.GIF
            "bmp" -> runCatching { ContentType.parse("image/bmp") }.getOrElse { ContentType.Image.JPEG }
            "heic", "heif" -> runCatching { ContentType.parse("image/heif") }.getOrElse { ContentType.Image.JPEG }
            else -> ContentType.Image.JPEG
        }
    }
}
