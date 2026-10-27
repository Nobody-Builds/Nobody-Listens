package com.nobodybuilds.listens.presentation.health

import com.nobodybuilds.listens.data.autofill.AutofillScoring
import com.nobodybuilds.listens.data.autofill.OfflineMetadataGuesser
import com.nobodybuilds.listens.data.model.Song
import java.io.File
import kotlin.math.abs

/** The checks shown on the Library health screen. */
enum class HealthSection {
    MISSING_ARTWORK,
    MISSING_YEAR,
    MISSING_ALBUM_ARTIST,
    MISSING_GENRE,
    UNKNOWN_ARTIST_ALBUM,
    DUPLICATES;

    /** Sections whose songs batch autofill can try to fix. */
    val fixable: Boolean get() = this != DUPLICATES
}

/** One copy inside a group of possible duplicates. */
data class DuplicateCopy(
    val song: Song,
    val sizeBytes: Long,
)

data class DuplicateGroup(
    val key: String,
    val title: String,
    val artist: String,
    val copies: List<DuplicateCopy>,
)

data class LibraryHealthReport(
    val totalSongs: Int = 0,
    val sections: Map<HealthSection, List<Song>> = emptyMap(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
) {
    fun count(section: HealthSection): Int =
        if (section == HealthSection.DUPLICATES) duplicateGroups.size else sections[section].orEmpty().size

    val issueCount: Int get() = HealthSection.entries.sumOf { count(it) }
}

/**
 * Computes library health from the in-memory song list. Pure and cheap except for the
 * file sizes of duplicate copies, so call it off the main thread.
 */
object LibraryHealthAnalyzer {

    private const val DUPLICATE_TOLERANCE_MS = 3_000L

    /** Only files on this device can be checked and re-tagged. */
    fun isLocal(song: Song): Boolean {
        val id = song.id.toLongOrNull() ?: return false
        return id > 0 && song.path.isNotBlank() &&
            song.telegramFileId == null && song.neteaseId == null && song.gdriveFileId == null &&
            song.qqMusicMid == null && song.navidromeId == null && song.jellyfinId == null
    }

    fun analyze(allSongs: List<Song>): LibraryHealthReport {
        val songs = allSongs.filter(::isLocal)
        val sections = HealthSection.entries
            .filter { it.fixable }
            .associateWith { section -> songs.filter { needsFix(it, section) } }
        return LibraryHealthReport(
            totalSongs = songs.size,
            sections = sections,
            duplicateGroups = findDuplicates(songs),
        )
    }

    fun needsFix(song: Song, section: HealthSection): Boolean = when (section) {
        HealthSection.MISSING_ARTWORK -> song.albumArtUriString.isNullOrBlank()
        HealthSection.MISSING_YEAR -> song.year !in 1000..9999
        HealthSection.MISSING_ALBUM_ARTIST -> OfflineMetadataGuesser.isUnknown(song.albumArtist)
        HealthSection.MISSING_GENRE -> OfflineMetadataGuesser.isUnknown(song.genre)
        HealthSection.UNKNOWN_ARTIST_ALBUM ->
            OfflineMetadataGuesser.isUnknown(song.artist) || OfflineMetadataGuesser.isUnknown(song.album)
        HealthSection.DUPLICATES -> false
    }

    private fun findDuplicates(songs: List<Song>): List<DuplicateGroup> {
        val byKey = songs
            .filter { it.title.isNotBlank() && !OfflineMetadataGuesser.isUnknown(it.title) }
            .groupBy { AutofillScoring.normalize(it.title) + "\u0000" + AutofillScoring.normalize(it.artist) }
            .filter { it.value.size > 1 && it.key.substringBefore('\u0000').isNotBlank() }

        val groups = mutableListOf<DuplicateGroup>()
        byKey.forEach { (key, candidates) ->
            // Cluster by duration: each cluster is anchored on its shortest song.
            val sorted = candidates.sortedBy { it.duration }
            var cluster = mutableListOf<Song>()
            fun flush() {
                if (cluster.size > 1) {
                    val first = cluster.first()
                    groups += DuplicateGroup(
                        key = key + "|" + first.id,
                        title = first.title,
                        artist = first.displayArtist,
                        copies = cluster.map { DuplicateCopy(it, runCatching { File(it.path).length() }.getOrDefault(0L)) }
                            .sortedByDescending { (it.song.bitrate ?: 0) },
                    )
                }
                cluster = mutableListOf()
            }
            sorted.forEach { song ->
                val anchor = cluster.firstOrNull()
                if (anchor != null && abs(song.duration - anchor.duration) > DUPLICATE_TOLERANCE_MS) flush()
                cluster += song
            }
            flush()
        }
        return groups.sortedBy { it.title.lowercase() }
    }
}
