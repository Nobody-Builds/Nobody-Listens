package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.model.PlaybackQueueSnapshot
import com.nobodybuilds.listens.data.model.TransitionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Playback settings (crossfade, repeat/shuffle, saved queue, transitions, ReplayGain) behind [UserPreferencesRepository]. */
internal class UserPreferencesPlayback(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val isCrossfadeEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.IS_CROSSFADE_ENABLED] ?: false
            }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IS_CROSSFADE_ENABLED] = enabled
        }
    }

    val hiFiModeEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.HI_FI_MODE_ENABLED] ?: false
        }

    suspend fun setHiFiModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.HI_FI_MODE_ENABLED] = enabled
        }
    }

    val crossfadeDurationFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                (preferences[UserPreferencesKeys.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000)
            }

    suspend fun setCrossfadeDuration(duration: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.CROSSFADE_DURATION] = duration.coerceIn(1000, 12000)
        }
    }

    val repeatModeFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.REPEAT_MODE] ?: Player.REPEAT_MODE_OFF
            }

    suspend fun setRepeatMode(@Player.RepeatMode mode: Int) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.REPEAT_MODE] = mode }
    }

    val isShuffleOnFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.IS_SHUFFLE_ON] ?: false
            }

    suspend fun setShuffleOn(on: Boolean) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.IS_SHUFFLE_ON] = on }
    }

    val persistentShuffleEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] ?: false
            }

    suspend fun setPersistentShuffleEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    val playbackQueueSnapshotFlow: Flow<PlaybackQueueSnapshot?> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT]?.let { raw ->
                    runCatching { json.decodeFromString<PlaybackQueueSnapshot>(raw) }.getOrNull()
                }
            }

    suspend fun getPlaybackQueueSnapshotOnce(): PlaybackQueueSnapshot? {
        return playbackQueueSnapshotFlow.first()
    }

    suspend fun setPlaybackQueueSnapshot(snapshot: PlaybackQueueSnapshot?) {
        dataStore.edit { preferences ->
            if (snapshot == null || snapshot.items.isEmpty()) {
                preferences.remove(UserPreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT)
            } else {
                preferences[UserPreferencesKeys.PLAYBACK_QUEUE_SNAPSHOT] = json.encodeToString(snapshot)
            }
        }
    }

    val globalTransitionSettingsFlow: Flow<TransitionSettings> =
            dataStore.data.map { preferences ->
                val duration = (preferences[UserPreferencesKeys.CROSSFADE_DURATION] ?: 2000).coerceIn(1000, 12000)
                val settings =
                        preferences[UserPreferencesKeys.GLOBAL_TRANSITION_SETTINGS]?.let { jsonString ->
                            try {
                                json.decodeFromString<TransitionSettings>(jsonString)
                            } catch (e: Exception) {
                                TransitionSettings() // Return default on error
                            }
                        }
                                ?: TransitionSettings() // Return default if not set

                settings.copy(durationMs = duration)
            }

    suspend fun saveGlobalTransitionSettings(settings: TransitionSettings) {
        dataStore.edit { preferences ->
            val jsonString = json.encodeToString(settings)
            preferences[UserPreferencesKeys.GLOBAL_TRANSITION_SETTINGS] = jsonString
        }
    }

    // ===== ReplayGain =====

    val replayGainEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.REPLAYGAIN_ENABLED] ?: false
        }

    val replayGainUseAlbumGainFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.REPLAYGAIN_USE_ALBUM_GAIN] ?: false
        }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.REPLAYGAIN_ENABLED] = enabled
        }
    }

    suspend fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.REPLAYGAIN_USE_ALBUM_GAIN] = useAlbumGain
        }
    }

    // ===== End ReplayGain =====

    val keepPlayingInBackgroundFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] ?: true
            }

    val disableCastAutoplayFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.DISABLE_CAST_AUTOPLAY] ?: false
            }

    val resumeOnHeadsetReconnectFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.RESUME_ON_HEADSET_RECONNECT] ?: false
            }

    val showQueueHistoryFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.SHOW_QUEUE_HISTORY] ?: false  // Default to false for performance
            }

    suspend fun setShowQueueHistory(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.SHOW_QUEUE_HISTORY] = show
        }
    }

    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] = enabled
        }
    }

    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.DISABLE_CAST_AUTOPLAY] = disabled
        }
    }

    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.RESUME_ON_HEADSET_RECONNECT] = enabled
        }
    }
}
