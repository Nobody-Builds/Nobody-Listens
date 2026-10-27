package com.nobodybuilds.listens.data.media

import java.util.concurrent.ConcurrentHashMap

/**
 * Nobody Listens' extra tag fields, written into the audio file next to Nobody Listens' own
 * (title, artist, album, genre, track, disc, lyrics, ReplayGain, cover art).
 *
 * Text fields: null leaves the file's value alone, blank removes it.
 * MusicBrainz IDs are only ever added (never cleared) and come from autofill.
 */
data class ExtendedTagUpdate(
    val albumArtist: String? = null,
    val year: String? = null,
    val composer: String? = null,
    val musicBrainzRecordingId: String? = null,
    val musicBrainzReleaseId: String? = null,
    val musicBrainzArtistId: String? = null,
    val musicBrainzReleaseGroupId: String? = null,
) {
    /** The year as a number for the library database, or null when it isn't one. */
    val yearNumber: Int? get() = year?.trim()?.take(4)?.toIntOrNull()
}

/**
 * Hands the edit sheet's extra fields to the save pipeline without changing the
 * signatures of the many screens that relay song edits (and survives the MediaStore
 * write-permission round trip in between). Keyed by song id; read once on save.
 */
object PendingExtendedTags {
    private val pending = ConcurrentHashMap<String, ExtendedTagUpdate>()

    fun put(songId: String, update: ExtendedTagUpdate) {
        pending[songId] = update
    }

    fun take(songId: String): ExtendedTagUpdate? = pending.remove(songId)

    fun clear(songId: String) {
        pending.remove(songId)
    }
}

/** The extra fields as they currently are in the file, for pre-filling the edit sheet. */
data class ExtendedTagSnapshot(
    val albumArtist: String = "",
    val year: String = "",
    val composer: String = "",
)

object ExtendedTagReader {
    fun read(file: java.io.File): ExtendedTagSnapshot? = runCatching {
        if (!file.exists()) return null
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val map = com.kyant.taglib.TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                ?.propertyMap
                .orEmpty()
            fun first(vararg keys: String): String =
                keys.firstNotNullOfOrNull { key -> map[key]?.firstOrNull()?.takeIf { it.isNotBlank() } }.orEmpty()
            ExtendedTagSnapshot(
                albumArtist = first("ALBUMARTIST", "ALBUM ARTIST"),
                year = first("DATE", "YEAR", "ORIGINALDATE"),
                composer = first("COMPOSER"),
            )
        }
    }.getOrNull()
}
