package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobodybuilds.listens.data.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Favorites, playlist order, mixes and last-playlist state behind [UserPreferencesRepository]. */
internal class UserPreferencesCollections(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    val dailyMixSongIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[UserPreferencesKeys.DAILY_MIX_SONG_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveDailyMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.DAILY_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val yourMixSongIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[UserPreferencesKeys.YOUR_MIX_SONG_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveYourMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.YOUR_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val lastDailyMixUpdateFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LAST_DAILY_MIX_UPDATE] ?: 0L
            }

    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LAST_DAILY_MIX_UPDATE] = timestamp
        }
    }

    val favoriteSongIdsFlow: Flow<Set<String>> =
            dataStore.data // Nuevo flujo para favoritos
                    .map { preferences ->
                preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
            dataStore.data.map { preferences ->
                val serializedModes = preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES]
                if (serializedModes.isNullOrBlank()) {
                    emptyMap()
                } else {
                    runCatching { json.decodeFromString<Map<String, String>>(serializedModes) }
                            .getOrDefault(emptyMap())
                }
            }

    // Legacy DataStore playlist payload kept only for one-time migration and old backup compatibility.
    val legacyUserPlaylistsFlow: Flow<List<Playlist>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[UserPreferencesKeys.USER_PLAYLISTS]
                if (jsonString != null) {
                    runCatching { json.decodeFromString<List<Playlist>>(jsonString) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

    suspend fun getLegacyUserPlaylistsOnce(): List<Playlist> = legacyUserPlaylistsFlow.first()

    suspend fun clearLegacyUserPlaylists() {
        dataStore.edit { preferences ->
            preferences.remove(UserPreferencesKeys.USER_PLAYLISTS)
        }
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            val updated = existingModes.toMutableMap()
            updated[playlistId] = modeValue

            preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) {
        dataStore.edit { preferences ->
            if (modes.isEmpty()) {
                preferences.remove(UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES)
            } else {
                preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(modes)
            }
        }
    }

    suspend fun clearPlaylistSongOrderMode(playlistId: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            if (!existingModes.containsKey(playlistId)) return@edit

            val updated = existingModes.toMutableMap()
            updated.remove(playlistId)

            preferences[UserPreferencesKeys.PLAYLIST_SONG_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun toggleFavoriteSong(
            songId: String,
            removing: Boolean = false
    ) { // Nueva función para favoritos
        dataStore.edit { preferences ->
            val currentFavorites = preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            val contains = currentFavorites.contains(songId)

            if (contains) preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites - songId
            else {
                if (removing)
                        preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites - songId
                else preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites + songId
            }
        }
    }

    suspend fun setFavoriteSong(songId: String, isFavorite: Boolean) {
        dataStore.edit { preferences ->
            val currentFavorites = preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] = if (isFavorite) {
                currentFavorites + songId
            } else {
                currentFavorites - songId
            }
        }
    }

    suspend fun clearFavoriteSongIds() {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FAVORITE_SONG_IDS] = emptySet()
        }
    }

    // --- Quick Settings: Last Playlist ---

    val lastPlaylistIdFlow: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.LAST_PLAYLIST_ID]?.takeIf { it.isNotBlank() }
        }

    val lastPlaylistNameFlow: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.LAST_PLAYLIST_NAME] }

    suspend fun setLastPlaylist(playlistId: String, playlistName: String) {
        dataStore.edit {
            it[UserPreferencesKeys.LAST_PLAYLIST_ID] = playlistId
            it[UserPreferencesKeys.LAST_PLAYLIST_NAME] = playlistName
        }
    }

    suspend fun clearLastPlaylist() {
        dataStore.edit {
            it.remove(UserPreferencesKeys.LAST_PLAYLIST_ID)
            it.remove(UserPreferencesKeys.LAST_PLAYLIST_NAME)
        }
    }
}
