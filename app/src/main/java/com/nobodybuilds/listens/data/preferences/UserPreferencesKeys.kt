package com.nobodybuilds.listens.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/** DataStore keys used by [UserPreferencesRepository]. The stored names must never change. */
internal object UserPreferencesKeys {
    val APP_REBRAND_DIALOG_SHOWN = booleanPreferencesKey("app_rebrand_dialog_shown")
    val BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED =
        booleanPreferencesKey("beta_05_clean_install_disclaimer_dismissed")
    val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
    val BLOCKED_DIRECTORIES = stringSetPreferencesKey("blocked_directories")
    // Include-list model: only songs inside these folders are scanned.
    val INCLUDED_DIRECTORIES = stringSetPreferencesKey("included_directories")
    val INCLUDED_DIRECTORIES_MIGRATED = booleanPreferencesKey("included_directories_migrated")
    val INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
    // val GLOBAL_THEME_PREFERENCE = stringPreferencesKey("global_theme_preference_v2") //
    // Removed
    val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
    val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
    val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
    val FAVORITE_SONG_IDS = stringSetPreferencesKey("favorite_song_ids")
    val USER_PLAYLISTS = stringPreferencesKey("user_playlists_json_v1")
    val PLAYLIST_SONG_ORDER_MODES = stringPreferencesKey("playlist_song_order_modes")

    // Sort Option Keys
    val SONGS_SORT_OPTION = stringPreferencesKey("songs_sort_option")
    val SONGS_SORT_OPTION_MIGRATED = booleanPreferencesKey("songs_sort_option_migrated_v2")
    val ALBUMS_SORT_OPTION = stringPreferencesKey("albums_sort_option")
    val ARTISTS_SORT_OPTION = stringPreferencesKey("artists_sort_option")
    val PLAYLISTS_SORT_OPTION = stringPreferencesKey("playlists_sort_option")
    val FOLDERS_SORT_OPTION = stringPreferencesKey("folders_sort_option")
    val LIKED_SONGS_SORT_OPTION = stringPreferencesKey("liked_songs_sort_option")

    // UI State Keys
    val LAST_LIBRARY_TAB_INDEX =
            intPreferencesKey("last_library_tab_index") // Corrected: Add intPreferencesKey here
    val LAST_STORAGE_FILTER = stringPreferencesKey("last_storage_filter")
    val MOCK_GENRES_ENABLED = booleanPreferencesKey("mock_genres_enabled")
    val LAST_DAILY_MIX_UPDATE = longPreferencesKey("last_daily_mix_update")
    val DAILY_MIX_SONG_IDS = stringPreferencesKey("daily_mix_song_ids")
    val YOUR_MIX_SONG_IDS = stringPreferencesKey("your_mix_song_ids")
    val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
    val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
    val NAV_BAR_COMPACT_MODE = booleanPreferencesKey("nav_bar_compact_mode")
    val CAROUSEL_STYLE = stringPreferencesKey("carousel_style")
    val LIBRARY_NAVIGATION_MODE = stringPreferencesKey("library_navigation_mode")
    val LAUNCH_TAB = stringPreferencesKey("launch_tab")

    // Transition Settings
    val GLOBAL_TRANSITION_SETTINGS = stringPreferencesKey("global_transition_settings_json")
    val LIBRARY_TABS_ORDER = stringPreferencesKey("library_tabs_order")
    val IS_FOLDER_FILTER_ACTIVE = booleanPreferencesKey("is_folder_filter_active")
    val IS_FOLDERS_PLAYLIST_VIEW = booleanPreferencesKey("is_folders_playlist_view")
    val SHOW_TELEGRAM_CLOUD_PLAYLISTS = booleanPreferencesKey("show_telegram_cloud_playlists")
    val HIDE_LOCAL_MEDIA = booleanPreferencesKey("hide_local_media")
    val TELEGRAM_TOPIC_DISPLAY_MODE = stringPreferencesKey("telegram_topic_display_mode")
    val FOLDERS_SOURCE = stringPreferencesKey("folders_source")
    val FOLDER_BACK_GESTURE_NAVIGATION = booleanPreferencesKey("folder_back_gesture_navigation")
    val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
    val KEEP_PLAYING_IN_BACKGROUND = booleanPreferencesKey("keep_playing_in_background")
    val IS_CROSSFADE_ENABLED = booleanPreferencesKey("is_crossfade_enabled")
    val HI_FI_MODE_ENABLED = booleanPreferencesKey("hi_fi_mode_enabled")
    val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
    val CUSTOM_GENRES = androidx.datastore.preferences.core.stringSetPreferencesKey("custom_genres")
    val CUSTOM_GENRE_ICONS = stringPreferencesKey("custom_genre_icons") // JSON Map<String, Int>
    val REPEAT_MODE = intPreferencesKey("repeat_mode")
    val IS_SHUFFLE_ON = booleanPreferencesKey("is_shuffle_on")
    val PERSISTENT_SHUFFLE_ENABLED = booleanPreferencesKey("persistent_shuffle_enabled")
    val DISABLE_CAST_AUTOPLAY = booleanPreferencesKey("disable_cast_autoplay")
    val RESUME_ON_HEADSET_RECONNECT = booleanPreferencesKey("resume_on_headset_reconnect")
    val SHOW_QUEUE_HISTORY = booleanPreferencesKey("show_queue_history")
    val PLAYBACK_QUEUE_SNAPSHOT = stringPreferencesKey("playback_queue_snapshot_v1")
    val FULL_PLAYER_SHOW_FILE_INFO = booleanPreferencesKey("full_player_show_file_info")
    val FULL_PLAYER_DELAY_ALL = booleanPreferencesKey("full_player_delay_all")
    val FULL_PLAYER_DELAY_ALBUM = booleanPreferencesKey("full_player_delay_album")
    val FULL_PLAYER_DELAY_METADATA = booleanPreferencesKey("full_player_delay_metadata")
    val FULL_PLAYER_DELAY_PROGRESS = booleanPreferencesKey("full_player_delay_progress")
    val FULL_PLAYER_DELAY_CONTROLS = booleanPreferencesKey("full_player_delay_controls")
    val FULL_PLAYER_PLACEHOLDERS = booleanPreferencesKey("full_player_placeholders")
    val FULL_PLAYER_PLACEHOLDER_TRANSPARENT = booleanPreferencesKey("full_player_placeholder_transparent")
    val FULL_PLAYER_PLACEHOLDERS_ON_CLOSE = booleanPreferencesKey("full_player_placeholders_on_close")
    val FULL_PLAYER_SWITCH_ON_DRAG_RELEASE = booleanPreferencesKey("full_player_switch_on_drag_release")
    val FULL_PLAYER_DELAY_THRESHOLD = intPreferencesKey("full_player_delay_threshold_percent")
    val FULL_PLAYER_CLOSE_THRESHOLD = intPreferencesKey("full_player_close_threshold_percent")
    // Deprecated experiment key kept only for one-time cleanup after removing the legacy player sheet.
    val USE_PLAYER_SHEET_V2 = booleanPreferencesKey("use_player_sheet_v2")

    // Multi-Artist Settings
    val ARTIST_DELIMITERS = stringPreferencesKey("artist_delimiters")
    val ARTIST_WORD_DELIMITERS = stringPreferencesKey("artist_word_delimiters")
    val EXTRACT_ARTISTS_FROM_TITLE = booleanPreferencesKey("extract_artists_from_title")
    val GROUP_BY_ALBUM_ARTIST = booleanPreferencesKey("group_by_album_artist")
    val ARTIST_SETTINGS_RESCAN_REQUIRED =
            booleanPreferencesKey("artist_settings_rescan_required")

    // Equalizer Settings
    val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
    val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
    val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
    val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
    val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
    val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
    val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
    val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
    val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")

    // Dismissed Warning States
    val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
    val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
    val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
    val BACKUP_INFO_DISMISSED = booleanPreferencesKey("backup_info_dismissed")

    // View Mode
    // val IS_GRAPH_VIEW = booleanPreferencesKey("is_graph_view") // Deprecated
    val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")

    // Custom Presets
    val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json") // List<EqualizerPreset>
    val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json") // List<String> (names)

    // Library Sync
    val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    val DIRECTORY_RULES_VERSION = intPreferencesKey("directory_rules_version")
    val LAST_APPLIED_DIRECTORY_RULES_VERSION =
        intPreferencesKey("last_applied_directory_rules_version")

    // Lyrics Sync Offset per song (Map<songId, offsetMs> as JSON)
    val LYRICS_SYNC_OFFSETS = stringPreferencesKey("lyrics_sync_offsets_json")

    // Lyrics Source Preference
    val LYRICS_SOURCE_PREFERENCE = stringPreferencesKey("lyrics_source_preference")
    val AUTO_SCAN_LRC_FILES = booleanPreferencesKey("auto_scan_lrc_files")

    // Developer Options
    val ALBUM_ART_QUALITY = stringPreferencesKey("album_art_quality")
    val ALBUM_ART_CACHE_LIMIT_MB = intPreferencesKey("album_art_cache_limit_mb")
    val TAP_BACKGROUND_CLOSES_PLAYER = booleanPreferencesKey("tap_background_closes_player")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val IMMERSIVE_LYRICS_ENABLED = booleanPreferencesKey("immersive_lyrics_enabled")
    val IMMERSIVE_LYRICS_TIMEOUT = longPreferencesKey("immersive_lyrics_timeout")
    val USE_ANIMATED_LYRICS = booleanPreferencesKey("use_animated_lyrics")
    val ANIMATED_LYRICS_BLUR_ENABLED = booleanPreferencesKey("animated_lyrics_blur_enabled")
    val ANIMATED_LYRICS_BLUR_STRENGTH = androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")

    // Genre View Preference
    val IS_GENRE_GRID_VIEW = booleanPreferencesKey("is_genre_grid_view")

    // Album View Preference
    val IS_ALBUMS_LIST_VIEW = booleanPreferencesKey("is_albums_list_view")

    // Collage Pattern
    val COLLAGE_PATTERN = stringPreferencesKey("collage_pattern")
    val COLLAGE_AUTO_ROTATE = booleanPreferencesKey("collage_auto_rotate")

    // Quick Settings / Last Playlist
    val LAST_PLAYLIST_ID = stringPreferencesKey("last_playlist_id")
    val LAST_PLAYLIST_NAME = stringPreferencesKey("last_playlist_name")

    // Smart Duration Filtering
    val MIN_SONG_DURATION = intPreferencesKey("min_song_duration_ms")

    // Album Tracks Filtering
    val MIN_TRACKS_PER_ALBUM = intPreferencesKey("min_tracks_per_album")

    // ReplayGain
    val REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
    val REPLAYGAIN_USE_ALBUM_GAIN = booleanPreferencesKey("replaygain_use_album_gain")
}
