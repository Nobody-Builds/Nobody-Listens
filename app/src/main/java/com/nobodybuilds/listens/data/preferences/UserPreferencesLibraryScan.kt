package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.Companion.DEFAULT_ARTIST_DELIMITERS
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.Companion.DEFAULT_ARTIST_WORD_DELIMITERS
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository.Companion.INCLUDE_MODE_BLOCKED_ROOTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Library scanning settings (multi-artist parsing, sync state, folder rules, duration filters) behind [UserPreferencesRepository]. */
internal class UserPreferencesLibraryScan(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    // ===== Multi-Artist Settings =====

    val artistDelimitersFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val stored = preferences[UserPreferencesKeys.ARTIST_DELIMITERS]
                if (stored != null) {
                    try {
                        json.decodeFromString<List<String>>(stored)
                    } catch (e: Exception) {
                        DEFAULT_ARTIST_DELIMITERS
                    }
                } else {
                    DEFAULT_ARTIST_DELIMITERS
                }
            }

    suspend fun setArtistDelimiters(delimiters: List<String>) {
        // Ensure at least one delimiter is always maintained
        if (delimiters.isEmpty()) {
            return
        }

        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ARTIST_DELIMITERS] = json.encodeToString(delimiters)
            // Mark rescan as required when delimiters change
            preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistDelimitersToDefault() {
        setArtistDelimiters(DEFAULT_ARTIST_DELIMITERS)
    }

    val artistWordDelimitersFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val stored = preferences[UserPreferencesKeys.ARTIST_WORD_DELIMITERS]
            if (stored != null) {
                try {
                    json.decodeFromString<List<String>>(stored)
                } catch (e: Exception) {
                    DEFAULT_ARTIST_WORD_DELIMITERS
                }
            } else {
                DEFAULT_ARTIST_WORD_DELIMITERS
            }
        }

    suspend fun setArtistWordDelimiters(delimiters: List<String>) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ARTIST_WORD_DELIMITERS] = json.encodeToString(delimiters)
            preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetArtistWordDelimitersToDefault() {
        setArtistWordDelimiters(DEFAULT_ARTIST_WORD_DELIMITERS)
    }

    val extractArtistsFromTitleFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] ?: true // Enabled by default
        }

    suspend fun setExtractArtistsFromTitle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.EXTRACT_ARTISTS_FROM_TITLE] = enabled
            preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val groupByAlbumArtistFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.GROUP_BY_ALBUM_ARTIST] ?: false
            }

    suspend fun setGroupByAlbumArtist(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.GROUP_BY_ALBUM_ARTIST] = enabled
            // Mark rescan as required when this setting changes
            preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val artistSettingsRescanRequiredFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] ?: false
            }

    suspend fun clearArtistSettingsRescanRequired() {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ARTIST_SETTINGS_RESCAN_REQUIRED] = false
        }
    }

    // ===== Library Sync Settings =====

    val lastSyncTimestampFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
            }

    val directoryRulesVersionFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            }

    val lastAppliedDirectoryRulesVersionFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] ?: 0
            }

    suspend fun getLastSyncTimestamp(): Long {
        return lastSyncTimestampFlow.first()
    }

    suspend fun getDirectoryRulesVersion(): Int {
        return directoryRulesVersionFlow.first()
    }

    suspend fun getLastAppliedDirectoryRulesVersion(): Int {
        return lastAppliedDirectoryRulesVersionFlow.first()
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun markDirectoryRulesVersionApplied(version: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LAST_APPLIED_DIRECTORY_RULES_VERSION] = version
        }
    }

    // ===== End Library Sync Settings =====

    // ===== Smart Duration Filtering =====

    /** Minimum song duration in milliseconds. Default 10000ms (10 seconds). */
    val minSongDurationFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            (preferences[UserPreferencesKeys.MIN_SONG_DURATION] ?: 10000).coerceIn(0, 120000)
        }

    suspend fun setMinSongDuration(durationMs: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.MIN_SONG_DURATION] = durationMs.coerceIn(0, 120000)
        }
    }

    suspend fun getMinSongDuration(): Int {
        return minSongDurationFlow.first()
    }

    // ===== End Smart Duration Filtering =====

    // ===== Album Tracks Filtering =====

    val minTracksPerAlbumFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.MIN_TRACKS_PER_ALBUM] ?: 1
        }

    suspend fun setMinTracksPerAlbum(minTracks: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.MIN_TRACKS_PER_ALBUM] = minTracks
        }
    }

    // ===== End Album Tracks Filtering =====

    /**
     * Effective "allowed" rules used by scanning and library queries.
     * When an include list exists, the included folders are added as allow rules so they
     * override the catch-all storage block below (see [blockedDirectoriesFlow]).
     */
    val allowedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                val raw = preferences[UserPreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet()
                val included = preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES] ?: emptySet()
                if (included.isEmpty()) {
                    raw
                } else {
                    // Allow rules outside every included folder would re-open folders the include list leaves out.
                    raw.filter { rule ->
                        included.any { com.nobodybuilds.listens.data.library.MusicFolderPaths.isSameOrInside(rule, it) }
                    }.toSet() + included
                }
            }.distinctUntilChanged()

    /**
     * Effective "blocked" rules used by scanning and library queries.
     * With an include list, every storage root is blocked and only the included folders
     * (deeper, so they win in [com.nobodybuilds.listens.utils.DirectoryRuleResolver]) are allowed.
     * Raw blocked folders inside included folders keep working as "excluded subfolders".
     */
    val blockedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                val raw = preferences[UserPreferencesKeys.BLOCKED_DIRECTORIES] ?: emptySet()
                val included = preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES] ?: emptySet()
                if (included.isEmpty()) raw else raw + INCLUDE_MODE_BLOCKED_ROOTS
            }.distinctUntilChanged()

    /** Raw allow rules as edited by the folder explorer (nested exceptions inside excluded subfolders). */
    val rawAllowedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet()
            }.distinctUntilChanged()

    /** Raw blocked rules as edited by the folder explorer ("Excluded subfolders"). */
    val rawBlockedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.BLOCKED_DIRECTORIES] ?: emptySet()
            }.distinctUntilChanged()

    /** Folders the user chose as music folders. Empty = legacy "scan everything" behaviour. */
    val includedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES] ?: emptySet()
            }.distinctUntilChanged()

    val includedDirectoriesMigratedFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES_MIGRATED] ?: false
            }.distinctUntilChanged()

    suspend fun setIncludedDirectoriesMigrated(done: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES_MIGRATED] = done
        }
    }

    suspend fun updateIncludedDirectories(includedPaths: Set<String>) {
        dataStore.edit { preferences ->
            val current = preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES] ?: emptySet()
            if (current == includedPaths) return@edit
            preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES] = includedPaths
            preferences[UserPreferencesKeys.INCLUDED_DIRECTORIES_MIGRATED] = true
            // Directory rules changed: force next sync to fetch full library again.
            preferences[UserPreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            val currentVersion = preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] =
                if (currentVersion == Int.MAX_VALUE) 0 else currentVersion + 1
        }
    }

    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            // Directory rules changed: force next sync to fetch full library again.
            preferences[UserPreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            val currentVersion = preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] =
                if (currentVersion == Int.MAX_VALUE) 0 else currentVersion + 1
        }
    }

    suspend fun updateDirectorySelections(allowedPaths: Set<String>, blockedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            preferences[UserPreferencesKeys.BLOCKED_DIRECTORIES] = blockedPaths
            // Directory rules changed: force next sync to fetch full library again.
            preferences[UserPreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            val currentVersion = preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] ?: 0
            preferences[UserPreferencesKeys.DIRECTORY_RULES_VERSION] =
                if (currentVersion == Int.MAX_VALUE) 0 else currentVersion + 1
        }
    }
}
