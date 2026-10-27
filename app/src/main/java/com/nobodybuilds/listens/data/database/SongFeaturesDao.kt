package com.nobodybuilds.listens.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val LOCAL_SONG_FILTER = """
    s.source_type = 0 AND (
        s.content_uri_string LIKE 'content://%' OR
        s.content_uri_string LIKE 'file://%' OR
        s.content_uri_string LIKE '/%'
    )
"""

@Dao
interface SongFeaturesDao {

    @Upsert
    suspend fun upsert(features: SongFeaturesEntity)

    @Upsert
    suspend fun upsertAll(features: List<SongFeaturesEntity>)

    @Query("SELECT * FROM song_features WHERE song_id = :songId LIMIT 1")
    suspend fun get(songId: String): SongFeaturesEntity?

    @Query("SELECT * FROM song_features WHERE status = 0 AND model_version = :modelVersion")
    suspend fun getAllAnalyzed(modelVersion: Int): List<SongFeaturesEntity>

    /** Newest analysis time among analysed songs (part of the vector cache key). */
    @Query("SELECT MAX(analyzed_at) FROM song_features WHERE status = 0 AND model_version = :modelVersion")
    suspend fun getMaxAnalyzedAt(modelVersion: Int): Long?

    @Query("SELECT COUNT(*) FROM song_features WHERE status = 0 AND model_version = :modelVersion")
    fun observeAnalyzedCount(modelVersion: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM song_features WHERE status = 1 AND model_version = :modelVersion")
    fun observeFailedCount(modelVersion: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs s WHERE $LOCAL_SONG_FILTER")
    fun observeLocalSongCount(): Flow<Int>

    /**
     * Local songs that have no features for [modelVersion] yet, plus failed ones analysed before
     * [retryFailedBefore] (a failed decode is retried after a while, never in a tight loop).
     */
    @Query(
        """
        SELECT s.id, s.artist_name, s.content_uri_string, s.file_path, s.duration, s.is_favorite
        FROM songs s
        LEFT JOIN song_features f ON f.song_id = CAST(s.id AS TEXT)
        WHERE $LOCAL_SONG_FILTER
          AND (
            f.song_id IS NULL OR f.model_version < :modelVersion OR
            (f.status = 1 AND f.analyzed_at < :retryFailedBefore)
          )
        ORDER BY s.date_added DESC
        LIMIT :limit
        """
    )
    suspend fun getPendingSongs(modelVersion: Int, retryFailedBefore: Long, limit: Int): List<SmartSongRow>

    @Query(
        """
        SELECT COUNT(*) FROM songs s
        LEFT JOIN song_features f ON f.song_id = CAST(s.id AS TEXT)
        WHERE $LOCAL_SONG_FILTER
          AND (
            f.song_id IS NULL OR f.model_version < :modelVersion OR
            (f.status = 1 AND f.analyzed_at < :retryFailedBefore)
          )
        """
    )
    suspend fun countPendingSongs(modelVersion: Int, retryFailedBefore: Long): Int

    /** Analysed songs joined with their library row (only songs still in the library). */
    @Query(
        """
        SELECT s.id, s.artist_name, s.content_uri_string, s.file_path, s.duration, s.is_favorite
        FROM songs s
        INNER JOIN song_features f ON f.song_id = CAST(s.id AS TEXT)
        WHERE f.status = 0 AND f.model_version = :modelVersion
        """
    )
    suspend fun getAnalyzedSongRows(modelVersion: Int): List<SmartSongRow>

    @Query("DELETE FROM song_features WHERE song_id NOT IN (SELECT CAST(id AS TEXT) FROM songs)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM song_features")
    suspend fun clearAll()
}
