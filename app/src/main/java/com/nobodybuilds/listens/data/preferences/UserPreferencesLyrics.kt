package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lyrics settings (sync offsets, source, immersive and animated lyrics) behind [UserPreferencesRepository]. */
internal class UserPreferencesLyrics(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    // ===== Lyrics Sync Offset Settings =====

    /**
     * Lyrics sync offset per song in milliseconds.
     * Stored as a JSON map: { "songId": offsetMs, ... }
     * Positive values = lyrics appear later (use when lyrics are ahead of audio)
     * Negative values = lyrics appear earlier (use when lyrics are behind audio)
     */
    private val lyricsSyncOffsetsFlow: Flow<Map<String, Int>> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LYRICS_SYNC_OFFSETS]?.let { jsonString ->
                    try {
                        json.decodeFromString<Map<String, Int>>(jsonString)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } ?: emptyMap()
            }

    fun getLyricsSyncOffsetFlow(songId: String): Flow<Int> {
        return lyricsSyncOffsetsFlow.map { offsets -> offsets[songId] ?: 0 }
    }

    suspend fun getLyricsSyncOffset(songId: String): Int {
        return getLyricsSyncOffsetFlow(songId).first()
    }

    suspend fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        dataStore.edit { preferences ->
            val currentOffsets = preferences[UserPreferencesKeys.LYRICS_SYNC_OFFSETS]?.let { jsonString ->
                try {
                    json.decodeFromString<Map<String, Int>>(jsonString).toMutableMap()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } ?: mutableMapOf()

            if (offsetMs == 0) {
                currentOffsets.remove(songId) // Don't store default value
            } else {
                currentOffsets[songId] = offsetMs
            }

            preferences[UserPreferencesKeys.LYRICS_SYNC_OFFSETS] = json.encodeToString(currentOffsets)
        }
    }

    // ===== End Lyrics Sync Offset Settings =====

    // ===== Lyrics Source Preference Settings =====

    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> =
            dataStore.data.map { preferences ->
                LyricsSourcePreference.fromName(preferences[UserPreferencesKeys.LYRICS_SOURCE_PREFERENCE])
            }

    suspend fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LYRICS_SOURCE_PREFERENCE] = preference.name
        }
    }

    val autoScanLrcFilesFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.AUTO_SCAN_LRC_FILES] ?: false
            }

    suspend fun setAutoScanLrcFiles(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.AUTO_SCAN_LRC_FILES] = enabled
        }
    }

    val immersiveLyricsEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.IMMERSIVE_LYRICS_ENABLED] ?: false
            }

    val immersiveLyricsTimeoutFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] ?: 4000L
            }

    suspend fun setImmersiveLyricsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IMMERSIVE_LYRICS_ENABLED] = enabled
        }
    }

    suspend fun setImmersiveLyricsTimeout(timeout: Long) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IMMERSIVE_LYRICS_TIMEOUT] = timeout
        }
    }

    // ===== End Lyrics Source Preference Settings =====

    // ===== End Multi-Artist Settings =====

    val useAnimatedLyricsFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.USE_ANIMATED_LYRICS] ?: false
        }

    suspend fun setUseAnimatedLyrics(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.USE_ANIMATED_LYRICS] = enabled
        }
    }

    val animatedLyricsBlurEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] ?: true
        }

    suspend fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ANIMATED_LYRICS_BLUR_ENABLED] = enabled
        }
    }

    // Range should ideally be 0.0f to 5.0f (or similar)
    val animatedLyricsBlurStrengthFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] ?: 2.5f
        }

    suspend fun setAnimatedLyricsBlurStrength(strength: Float) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ANIMATED_LYRICS_BLUR_STRENGTH] = strength
        }
    }
}
