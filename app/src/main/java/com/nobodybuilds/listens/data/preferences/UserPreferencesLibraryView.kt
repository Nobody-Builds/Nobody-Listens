package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.SortOption
import com.nobodybuilds.listens.data.model.StorageFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Library screen settings (sort options, tabs, views, folders, custom genres) behind [UserPreferencesRepository]. */
internal class UserPreferencesLibraryView(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {

    // Custom Genres Names
    val customGenresFlow: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.CUSTOM_GENRES] ?: emptySet()
        }

    // Custom Genres Icons (JSON Map: Name -> ResId)
    val customGenreIconsFlow: Flow<Map<String, Int>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[UserPreferencesKeys.CUSTOM_GENRE_ICONS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<Map<String, Int>>(jsonString)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        }

    suspend fun addCustomGenre(genre: String, iconResId: Int? = null) {
        dataStore.edit { preferences ->
            val currentGenres = preferences[UserPreferencesKeys.CUSTOM_GENRES] ?: emptySet()
            preferences[UserPreferencesKeys.CUSTOM_GENRES] = currentGenres + genre

            if (iconResId != null) {
                val currentIconsJson = preferences[UserPreferencesKeys.CUSTOM_GENRE_ICONS]
                val currentIcons = if (currentIconsJson != null) {
                    try {
                        json.decodeFromString<Map<String, Int>>(currentIconsJson)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                val newIcons = currentIcons.toMutableMap()
                newIcons[genre] = iconResId
                preferences[UserPreferencesKeys.CUSTOM_GENRE_ICONS] = json.encodeToString(newIcons)
            }
        }
    }

    val isGenreGridViewFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.IS_GENRE_GRID_VIEW] ?: true // Default to Grid (true)
        }

    suspend fun setGenreGridView(isGrid: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IS_GENRE_GRID_VIEW] = isGrid
        }
    }

    val isAlbumsListViewFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.IS_ALBUMS_LIST_VIEW] ?: false // Default to Grid (false)
        }

    suspend fun setAlbumsListView(isList: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IS_ALBUMS_LIST_VIEW] = isList
        }
    }

    // Flows for Sort Options
    val songsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.SONGS_SORT_OPTION],
                                SortOption.SONGS,
                                SortOption.SongTitleAZ
                        )
                        .storageKey
            }

    val albumsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.ALBUMS_SORT_OPTION],
                                SortOption.ALBUMS,
                                SortOption.AlbumTitleAZ
                        )
                        .storageKey
            }

    val artistsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.ARTISTS_SORT_OPTION],
                                SortOption.ARTISTS,
                                SortOption.ArtistNameAZ
                        )
                        .storageKey
            }

    val playlistsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.PLAYLISTS_SORT_OPTION],
                                SortOption.PLAYLISTS,
                                SortOption.PlaylistNameAZ
                        )
                        .storageKey
            }

    val foldersSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.FOLDERS_SORT_OPTION],
                                SortOption.FOLDERS,
                                SortOption.FolderNameAZ
                        )
                        .storageKey
            }

    val likedSongsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[UserPreferencesKeys.LIKED_SONGS_SORT_OPTION],
                                SortOption.LIKED,
                                SortOption.LikedSongDateLiked
                        )
                        .storageKey
            }

    // Functions to update Sort Options
    suspend fun setSongsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.SONGS_SORT_OPTION] = optionKey
            preferences[UserPreferencesKeys.SONGS_SORT_OPTION_MIGRATED] = true
        }
    }

    suspend fun setAlbumsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ALBUMS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setArtistsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.ARTISTS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setPlaylistsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.PLAYLISTS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setFoldersSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FOLDERS_SORT_OPTION] = optionKey
        }
    }

    suspend fun setLikedSongsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LIKED_SONGS_SORT_OPTION] = optionKey
        }
    }

    suspend fun ensureLibrarySortDefaults() {
        dataStore.edit { preferences ->
            val songsMigrated = preferences[UserPreferencesKeys.SONGS_SORT_OPTION_MIGRATED] ?: false
            val rawSongSort = preferences[UserPreferencesKeys.SONGS_SORT_OPTION]
            val resolvedSongSort =
                    SortOption.fromStorageKey(rawSongSort, SortOption.SONGS, SortOption.SongTitleAZ)
            val shouldForceSongDefault =
                    !songsMigrated &&
                            (rawSongSort.isNullOrBlank() ||
                                    rawSongSort == SortOption.SongTitleZA.storageKey ||
                                    rawSongSort == SortOption.SongTitleZA.displayName)

            preferences[UserPreferencesKeys.SONGS_SORT_OPTION] =
                    if (shouldForceSongDefault) {
                        SortOption.SongTitleAZ.storageKey
                    } else {
                        resolvedSongSort.storageKey
                    }
            if (!songsMigrated) {
                preferences[UserPreferencesKeys.SONGS_SORT_OPTION_MIGRATED] = true
            }

            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.SONGS_SORT_OPTION,
                    SortOption.SONGS,
                    SortOption.SongTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.ALBUMS_SORT_OPTION,
                    SortOption.ALBUMS,
                    SortOption.AlbumTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.ARTISTS_SORT_OPTION,
                    SortOption.ARTISTS,
                    SortOption.ArtistNameAZ
            )
            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.PLAYLISTS_SORT_OPTION,
                    SortOption.PLAYLISTS,
                    SortOption.PlaylistNameAZ
            )
            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.FOLDERS_SORT_OPTION,
                    SortOption.FOLDERS,
                    SortOption.FolderNameAZ
            )
            migrateSortPreference(
                    preferences,
                    UserPreferencesKeys.LIKED_SONGS_SORT_OPTION,
                    SortOption.LIKED,
                    SortOption.LikedSongDateLiked
            )
        }
    }

    private fun migrateSortPreference(
            preferences: MutablePreferences,
            key: Preferences.Key<String>,
            allowed: Collection<SortOption>,
            fallback: SortOption
    ) {
        val resolved = SortOption.fromStorageKey(preferences[key], allowed, fallback)
        if (preferences[key] != resolved.storageKey) {
            preferences[key] = resolved.storageKey
        }
    }

    // --- Library UI State ---
    val lastLibraryTabIndexFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.LAST_LIBRARY_TAB_INDEX] ?: 0 // Default to 0 (Songs tab)
            }

    suspend fun saveLastLibraryTabIndex(tabIndex: Int) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LAST_LIBRARY_TAB_INDEX] = tabIndex
        }
    }

    val lastStorageFilterFlow: Flow<StorageFilter> =
        dataStore.data.map { preferences ->
            when (preferences[UserPreferencesKeys.LAST_STORAGE_FILTER]) {
                "ONLINE"  -> StorageFilter.ONLINE
                "OFFLINE" -> StorageFilter.OFFLINE
                "DOWNLOADED" -> StorageFilter.DOWNLOADED
                else      -> StorageFilter.ALL
            }
        }

    suspend fun saveLastStorageFilter(filter: StorageFilter) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.LAST_STORAGE_FILTER] = filter.name
        }
    }

    val mockGenresEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.MOCK_GENRES_ENABLED] ?: false // Default to false
            }

    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.MOCK_GENRES_ENABLED] = enabled }
    }

    val libraryTabsOrderFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.LIBRARY_TABS_ORDER]
        }

    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.LIBRARY_TABS_ORDER] = order }
    }

    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { preferences -> preferences.remove(UserPreferencesKeys.LIBRARY_TABS_ORDER) }
    }

    suspend fun migrateTabOrder() {
        dataStore.edit { preferences ->
            val orderJson = preferences[UserPreferencesKeys.LIBRARY_TABS_ORDER]
            if (orderJson != null) {
                try {
                    val order = json.decodeFromString<MutableList<String>>(orderJson)
                    if (!order.contains("FOLDERS")) {
                        val likedIndex = order.indexOf("LIKED")
                        if (likedIndex != -1) {
                            order.add(likedIndex + 1, "FOLDERS")
                        } else {
                            order.add("FOLDERS") // Fallback
                        }
                        preferences[UserPreferencesKeys.LIBRARY_TABS_ORDER] = json.encodeToString(order)
                    }
                } catch (e: Exception) {
                    // Si la deserialización falla, no hacemos nada para evitar sobrescribir los
                    // datos del usuario.
                }
            }
            // Si orderJson es nulo, significa que el usuario nunca ha reordenado,
            // por lo que se utilizará el orden predeterminado que ya incluye FOLDERS.
        }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.IS_FOLDER_FILTER_ACTIVE] ?: false
            }

    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IS_FOLDER_FILTER_ACTIVE] = isActive
        }
    }

    val isFoldersPlaylistViewFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] ?: false
        }

    val showTelegramCloudPlaylistsFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] ?: true
        }

    val hideLocalMediaFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.HIDE_LOCAL_MEDIA] ?: false
        }.distinctUntilChanged()

    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> = dataStore.data
        .map { preferences ->
            TelegramTopicDisplayMode.fromStorageKey(preferences[UserPreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE])
        }

    val foldersSourceFlow: Flow<FolderSource> = dataStore.data
        .map { preferences ->
            FolderSource.fromStorageKey(preferences[UserPreferencesKeys.FOLDERS_SOURCE])
        }

    val folderBackGestureNavigationFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[UserPreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] ?: true
        }

    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.IS_FOLDERS_PLAYLIST_VIEW] = isPlaylistView
        }
    }

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.SHOW_TELEGRAM_CLOUD_PLAYLISTS] = show
        }
    }

    suspend fun setHideLocalMedia(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.HIDE_LOCAL_MEDIA] = hide
        }
    }

    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.TELEGRAM_TOPIC_DISPLAY_MODE] = mode.storageKey
        }
    }

    suspend fun setFoldersSource(source: FolderSource) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FOLDERS_SOURCE] = source.storageKey
        }
    }

    suspend fun setFolderBackGestureNavigation(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.FOLDER_BACK_GESTURE_NAVIGATION] = enabled
        }
    }
}
