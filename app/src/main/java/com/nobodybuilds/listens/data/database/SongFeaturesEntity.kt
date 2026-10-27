package com.nobodybuilds.listens.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-device audio features for one song (smart features, experimental).
 *
 * [scores] holds the 521 YAMNet class scores averaged over the analysed windows, quantised to one
 * byte per class with a square-root curve (see `SmartVectors.quantize`) — 521 bytes per song.
 */
@Entity(tableName = "song_features")
data class SongFeaturesEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "model_version") val modelVersion: Int,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long,
    @ColumnInfo(name = "scores", typeAffinity = ColumnInfo.BLOB) val scores: ByteArray?,
    @ColumnInfo(name = "energy") val energy: Float,
    @ColumnInfo(name = "energy_var") val energyVar: Float,
    @ColumnInfo(name = "tempo") val tempo: Float,
    @ColumnInfo(name = "brightness") val brightness: Float,
    @ColumnInfo(name = "mood_tags") val moodTags: String,
    @ColumnInfo(name = "genre_tags") val genreTags: String,
    @ColumnInfo(name = "status") val status: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SongFeaturesEntity) return false
        return songId == other.songId && modelVersion == other.modelVersion &&
            analyzedAt == other.analyzedAt && status == other.status &&
            (scores?.contentEquals(other.scores) ?: (other.scores == null))
    }

    override fun hashCode(): Int {
        var result = songId.hashCode()
        result = 31 * result + modelVersion
        result = 31 * result + analyzedAt.hashCode()
        result = 31 * result + status
        return result
    }

    companion object {
        const val STATUS_OK = 0
        const val STATUS_FAILED = 1
    }
}

/** Minimal song row the smart indexer needs. */
data class SmartSongRow(
    val id: Long,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    val duration: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean
)
