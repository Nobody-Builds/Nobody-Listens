package com.nobodybuilds.listens.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.model.PlaybackQueueSnapshot
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.data.model.TransitionSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreference {
    const val DEFAULT = "default"
    const val DYNAMIC = "dynamic"
    const val ALBUM_ART = "album_art"
    const val GLOBAL = "global"
}

/** App-wide color source. Nobody Listens defaults to the Daylight design system. */
object AppColorStyle {
    const val DAYLIGHT = "yuyu" // stored value kept from before the Daylight revamp
    const val MATERIAL_YOU = "material_you"
}

object AppThemeMode {
    const val FOLLOW_SYSTEM = "follow_system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

const val MIN_NAV_BAR_CORNER_RADIUS = 0
const val MAX_NAV_BAR_CORNER_RADIUS = 60

internal fun sanitizeNavBarCornerRadius(radius: Int): Int =
        radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)

/**
 * Album art quality settings for developer options.
 * Controls maximum resolution for album artwork in player view.
 * Thumbnails in lists always use low resolution for performance.
 *
 * @property maxSize Maximum size in pixels (0 = original size)
 * @property label Human-readable label for UI
 */
enum class AlbumArtQuality(val maxSize: Int, val label: String) {
    LOW(256, "Low (256px) - Better performance"),
    MEDIUM(512, "Medium (512px) - Balanced"),
    HIGH(800, "High (800px) - Best quality"),
    ORIGINAL(0, "Original - Maximum quality")
}

@Singleton
class UserPreferencesRepository
@Inject
constructor(
        private val dataStore: DataStore<Preferences>,
        private val json: Json // Inyectar Json para serialización
) {

    // Each settings area lives in its own helper; the members below forward to them so callers stay unchanged.
    private val playback = UserPreferencesPlayback(dataStore, json)
    private val libraryScan = UserPreferencesLibraryScan(dataStore, json)
    private val lyrics = UserPreferencesLyrics(dataStore, json)
    private val libraryView = UserPreferencesLibraryView(dataStore, json)
    private val appearance = UserPreferencesAppearance(dataStore)
    private val collections = UserPreferencesCollections(dataStore, json)
    private val backup = UserPreferencesBackup(dataStore)

    val appRebrandDialogShownFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.APP_REBRAND_DIALOG_SHOWN] ?: false
            }

    suspend fun setAppRebrandDialogShown(wasShown: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.APP_REBRAND_DIALOG_SHOWN] = wasShown
        }
    }

    val beta05CleanInstallDisclaimerDismissedFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[UserPreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] ?: false
        }

    suspend fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[UserPreferencesKeys.BETA_05_CLEAN_INSTALL_DISCLAIMER_DISMISSED] = dismissed
        }
    }

    val backupInfoDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[UserPreferencesKeys.BACKUP_INFO_DISMISSED] ?: false
    }

    suspend fun setBackupInfoDismissed(dismissed: Boolean) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.BACKUP_INFO_DISMISSED] = dismissed }
    }

    enum class EqualizerViewMode {
        SLIDERS, GRAPH, HYBRID
    }

    val initialSetupDoneFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[UserPreferencesKeys.INITIAL_SETUP_DONE] ?: false
            }

    suspend fun setInitialSetupDone(isDone: Boolean) {
        dataStore.edit { preferences -> preferences[UserPreferencesKeys.INITIAL_SETUP_DONE] = isDone }
    }

    companion object {
        /** Default character delimiters for splitting multi-artist tags */
        val DEFAULT_ARTIST_DELIMITERS = listOf("/", ";", ",", "+", "&")
        /** Default word-based delimiters (matched case-insensitively with whitespace boundaries) */
        val DEFAULT_ARTIST_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")
        const val DEFAULT_ALBUM_ART_CACHE_LIMIT_MB = 200
        /** Catch-all roots blocked while an include list is active. */
        val INCLUDE_MODE_BLOCKED_ROOTS: Set<String> = setOf("/storage", "/sdcard", "/mnt")
    }

    // ===== Delegated to UserPreferencesPlayback =====
    val isCrossfadeEnabledFlow: Flow<Boolean> get() = playback.isCrossfadeEnabledFlow
    suspend fun setCrossfadeEnabled(enabled: Boolean) = playback.setCrossfadeEnabled(enabled)
    val hiFiModeEnabledFlow: Flow<Boolean> get() = playback.hiFiModeEnabledFlow
    suspend fun setHiFiModeEnabled(enabled: Boolean) = playback.setHiFiModeEnabled(enabled)
    val crossfadeDurationFlow: Flow<Int> get() = playback.crossfadeDurationFlow
    suspend fun setCrossfadeDuration(duration: Int) = playback.setCrossfadeDuration(duration)
    val repeatModeFlow: Flow<Int> get() = playback.repeatModeFlow
    suspend fun setRepeatMode(@Player.RepeatMode mode: Int) = playback.setRepeatMode(mode)
    val isShuffleOnFlow: Flow<Boolean> get() = playback.isShuffleOnFlow
    suspend fun setShuffleOn(on: Boolean) = playback.setShuffleOn(on)
    val persistentShuffleEnabledFlow: Flow<Boolean> get() = playback.persistentShuffleEnabledFlow
    suspend fun setPersistentShuffleEnabled(enabled: Boolean) = playback.setPersistentShuffleEnabled(enabled)
    val playbackQueueSnapshotFlow: Flow<PlaybackQueueSnapshot?> get() = playback.playbackQueueSnapshotFlow
    suspend fun getPlaybackQueueSnapshotOnce(): PlaybackQueueSnapshot? = playback.getPlaybackQueueSnapshotOnce()
    suspend fun setPlaybackQueueSnapshot(snapshot: PlaybackQueueSnapshot?) = playback.setPlaybackQueueSnapshot(snapshot)
    val globalTransitionSettingsFlow: Flow<TransitionSettings> get() = playback.globalTransitionSettingsFlow
    suspend fun saveGlobalTransitionSettings(settings: TransitionSettings) = playback.saveGlobalTransitionSettings(settings)
    val replayGainEnabledFlow: Flow<Boolean> get() = playback.replayGainEnabledFlow
    val replayGainUseAlbumGainFlow: Flow<Boolean> get() = playback.replayGainUseAlbumGainFlow
    suspend fun setReplayGainEnabled(enabled: Boolean) = playback.setReplayGainEnabled(enabled)
    suspend fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) = playback.setReplayGainUseAlbumGain(useAlbumGain)
    val keepPlayingInBackgroundFlow: Flow<Boolean> get() = playback.keepPlayingInBackgroundFlow
    val disableCastAutoplayFlow: Flow<Boolean> get() = playback.disableCastAutoplayFlow
    val resumeOnHeadsetReconnectFlow: Flow<Boolean> get() = playback.resumeOnHeadsetReconnectFlow
    val showQueueHistoryFlow: Flow<Boolean> get() = playback.showQueueHistoryFlow
    suspend fun setShowQueueHistory(show: Boolean) = playback.setShowQueueHistory(show)
    suspend fun setKeepPlayingInBackground(enabled: Boolean) = playback.setKeepPlayingInBackground(enabled)
    suspend fun setDisableCastAutoplay(disabled: Boolean) = playback.setDisableCastAutoplay(disabled)
    suspend fun setResumeOnHeadsetReconnect(enabled: Boolean) = playback.setResumeOnHeadsetReconnect(enabled)

    // ===== Delegated to UserPreferencesLibraryScan =====
    val artistDelimitersFlow: Flow<List<String>> get() = libraryScan.artistDelimitersFlow
    suspend fun setArtistDelimiters(delimiters: List<String>) = libraryScan.setArtistDelimiters(delimiters)
    suspend fun resetArtistDelimitersToDefault() = libraryScan.resetArtistDelimitersToDefault()
    val artistWordDelimitersFlow: Flow<List<String>> get() = libraryScan.artistWordDelimitersFlow
    suspend fun setArtistWordDelimiters(delimiters: List<String>) = libraryScan.setArtistWordDelimiters(delimiters)
    suspend fun resetArtistWordDelimitersToDefault() = libraryScan.resetArtistWordDelimitersToDefault()
    val extractArtistsFromTitleFlow: Flow<Boolean> get() = libraryScan.extractArtistsFromTitleFlow
    suspend fun setExtractArtistsFromTitle(enabled: Boolean) = libraryScan.setExtractArtistsFromTitle(enabled)
    val groupByAlbumArtistFlow: Flow<Boolean> get() = libraryScan.groupByAlbumArtistFlow
    suspend fun setGroupByAlbumArtist(enabled: Boolean) = libraryScan.setGroupByAlbumArtist(enabled)
    val artistSettingsRescanRequiredFlow: Flow<Boolean> get() = libraryScan.artistSettingsRescanRequiredFlow
    suspend fun clearArtistSettingsRescanRequired() = libraryScan.clearArtistSettingsRescanRequired()
    val lastSyncTimestampFlow: Flow<Long> get() = libraryScan.lastSyncTimestampFlow
    val directoryRulesVersionFlow: Flow<Int> get() = libraryScan.directoryRulesVersionFlow
    val lastAppliedDirectoryRulesVersionFlow: Flow<Int> get() = libraryScan.lastAppliedDirectoryRulesVersionFlow
    suspend fun getLastSyncTimestamp(): Long = libraryScan.getLastSyncTimestamp()
    suspend fun getDirectoryRulesVersion(): Int = libraryScan.getDirectoryRulesVersion()
    suspend fun getLastAppliedDirectoryRulesVersion(): Int = libraryScan.getLastAppliedDirectoryRulesVersion()
    suspend fun setLastSyncTimestamp(timestamp: Long) = libraryScan.setLastSyncTimestamp(timestamp)
    suspend fun markDirectoryRulesVersionApplied(version: Int) = libraryScan.markDirectoryRulesVersionApplied(version)
    val minSongDurationFlow: Flow<Int> get() = libraryScan.minSongDurationFlow
    suspend fun setMinSongDuration(durationMs: Int) = libraryScan.setMinSongDuration(durationMs)
    suspend fun getMinSongDuration(): Int = libraryScan.getMinSongDuration()
    val minTracksPerAlbumFlow: Flow<Int> get() = libraryScan.minTracksPerAlbumFlow
    suspend fun setMinTracksPerAlbum(minTracks: Int) = libraryScan.setMinTracksPerAlbum(minTracks)
    val allowedDirectoriesFlow: Flow<Set<String>> get() = libraryScan.allowedDirectoriesFlow
    val blockedDirectoriesFlow: Flow<Set<String>> get() = libraryScan.blockedDirectoriesFlow
    val rawAllowedDirectoriesFlow: Flow<Set<String>> get() = libraryScan.rawAllowedDirectoriesFlow
    val rawBlockedDirectoriesFlow: Flow<Set<String>> get() = libraryScan.rawBlockedDirectoriesFlow
    val includedDirectoriesFlow: Flow<Set<String>> get() = libraryScan.includedDirectoriesFlow
    val includedDirectoriesMigratedFlow: Flow<Boolean> get() = libraryScan.includedDirectoriesMigratedFlow
    suspend fun setIncludedDirectoriesMigrated(done: Boolean) = libraryScan.setIncludedDirectoriesMigrated(done)
    suspend fun updateIncludedDirectories(includedPaths: Set<String>) = libraryScan.updateIncludedDirectories(includedPaths)
    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) = libraryScan.updateAllowedDirectories(allowedPaths)
    suspend fun updateDirectorySelections(allowedPaths: Set<String>, blockedPaths: Set<String>) = libraryScan.updateDirectorySelections(allowedPaths, blockedPaths)

    // ===== Delegated to UserPreferencesLyrics =====
    fun getLyricsSyncOffsetFlow(songId: String): Flow<Int> = lyrics.getLyricsSyncOffsetFlow(songId)
    suspend fun getLyricsSyncOffset(songId: String): Int = lyrics.getLyricsSyncOffset(songId)
    suspend fun setLyricsSyncOffset(songId: String, offsetMs: Int) = lyrics.setLyricsSyncOffset(songId, offsetMs)
    val lyricsSourcePreferenceFlow: Flow<LyricsSourcePreference> get() = lyrics.lyricsSourcePreferenceFlow
    suspend fun setLyricsSourcePreference(preference: LyricsSourcePreference) = lyrics.setLyricsSourcePreference(preference)
    val autoScanLrcFilesFlow: Flow<Boolean> get() = lyrics.autoScanLrcFilesFlow
    suspend fun setAutoScanLrcFiles(enabled: Boolean) = lyrics.setAutoScanLrcFiles(enabled)
    val immersiveLyricsEnabledFlow: Flow<Boolean> get() = lyrics.immersiveLyricsEnabledFlow
    val immersiveLyricsTimeoutFlow: Flow<Long> get() = lyrics.immersiveLyricsTimeoutFlow
    suspend fun setImmersiveLyricsEnabled(enabled: Boolean) = lyrics.setImmersiveLyricsEnabled(enabled)
    suspend fun setImmersiveLyricsTimeout(timeout: Long) = lyrics.setImmersiveLyricsTimeout(timeout)
    val useAnimatedLyricsFlow: Flow<Boolean> get() = lyrics.useAnimatedLyricsFlow
    suspend fun setUseAnimatedLyrics(enabled: Boolean) = lyrics.setUseAnimatedLyrics(enabled)
    val animatedLyricsBlurEnabledFlow: Flow<Boolean> get() = lyrics.animatedLyricsBlurEnabledFlow
    suspend fun setAnimatedLyricsBlurEnabled(enabled: Boolean) = lyrics.setAnimatedLyricsBlurEnabled(enabled)
    val animatedLyricsBlurStrengthFlow: Flow<Float> get() = lyrics.animatedLyricsBlurStrengthFlow
    suspend fun setAnimatedLyricsBlurStrength(strength: Float) = lyrics.setAnimatedLyricsBlurStrength(strength)

    // ===== Delegated to UserPreferencesLibraryView =====
    val customGenresFlow: Flow<Set<String>> get() = libraryView.customGenresFlow
    val customGenreIconsFlow: Flow<Map<String, Int>> get() = libraryView.customGenreIconsFlow
    suspend fun addCustomGenre(genre: String, iconResId: Int? = null) = libraryView.addCustomGenre(genre, iconResId)
    val isGenreGridViewFlow: Flow<Boolean> get() = libraryView.isGenreGridViewFlow
    suspend fun setGenreGridView(isGrid: Boolean) = libraryView.setGenreGridView(isGrid)
    val isAlbumsListViewFlow: Flow<Boolean> get() = libraryView.isAlbumsListViewFlow
    suspend fun setAlbumsListView(isList: Boolean) = libraryView.setAlbumsListView(isList)
    val songsSortOptionFlow: Flow<String> get() = libraryView.songsSortOptionFlow
    val albumsSortOptionFlow: Flow<String> get() = libraryView.albumsSortOptionFlow
    val artistsSortOptionFlow: Flow<String> get() = libraryView.artistsSortOptionFlow
    val playlistsSortOptionFlow: Flow<String> get() = libraryView.playlistsSortOptionFlow
    val foldersSortOptionFlow: Flow<String> get() = libraryView.foldersSortOptionFlow
    val likedSongsSortOptionFlow: Flow<String> get() = libraryView.likedSongsSortOptionFlow
    suspend fun setSongsSortOption(optionKey: String) = libraryView.setSongsSortOption(optionKey)
    suspend fun setAlbumsSortOption(optionKey: String) = libraryView.setAlbumsSortOption(optionKey)
    suspend fun setArtistsSortOption(optionKey: String) = libraryView.setArtistsSortOption(optionKey)
    suspend fun setPlaylistsSortOption(optionKey: String) = libraryView.setPlaylistsSortOption(optionKey)
    suspend fun setFoldersSortOption(optionKey: String) = libraryView.setFoldersSortOption(optionKey)
    suspend fun setLikedSongsSortOption(optionKey: String) = libraryView.setLikedSongsSortOption(optionKey)
    suspend fun ensureLibrarySortDefaults() = libraryView.ensureLibrarySortDefaults()
    val lastLibraryTabIndexFlow: Flow<Int> get() = libraryView.lastLibraryTabIndexFlow
    suspend fun saveLastLibraryTabIndex(tabIndex: Int) = libraryView.saveLastLibraryTabIndex(tabIndex)
    val lastStorageFilterFlow: Flow<StorageFilter> get() = libraryView.lastStorageFilterFlow
    suspend fun saveLastStorageFilter(filter: StorageFilter) = libraryView.saveLastStorageFilter(filter)
    val mockGenresEnabledFlow: Flow<Boolean> get() = libraryView.mockGenresEnabledFlow
    suspend fun setMockGenresEnabled(enabled: Boolean) = libraryView.setMockGenresEnabled(enabled)
    val libraryTabsOrderFlow: Flow<String?> get() = libraryView.libraryTabsOrderFlow
    suspend fun saveLibraryTabsOrder(order: String) = libraryView.saveLibraryTabsOrder(order)
    suspend fun resetLibraryTabsOrder() = libraryView.resetLibraryTabsOrder()
    suspend fun migrateTabOrder() = libraryView.migrateTabOrder()
    val isFolderFilterActiveFlow: Flow<Boolean> get() = libraryView.isFolderFilterActiveFlow
    suspend fun setFolderFilterActive(isActive: Boolean) = libraryView.setFolderFilterActive(isActive)
    val isFoldersPlaylistViewFlow: Flow<Boolean> get() = libraryView.isFoldersPlaylistViewFlow
    val showTelegramCloudPlaylistsFlow: Flow<Boolean> get() = libraryView.showTelegramCloudPlaylistsFlow
    val hideLocalMediaFlow: Flow<Boolean> get() = libraryView.hideLocalMediaFlow
    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> get() = libraryView.telegramTopicDisplayModeFlow
    val foldersSourceFlow: Flow<FolderSource> get() = libraryView.foldersSourceFlow
    val folderBackGestureNavigationFlow: Flow<Boolean> get() = libraryView.folderBackGestureNavigationFlow
    suspend fun setFoldersPlaylistView(isPlaylistView: Boolean) = libraryView.setFoldersPlaylistView(isPlaylistView)
    suspend fun setShowTelegramCloudPlaylists(show: Boolean) = libraryView.setShowTelegramCloudPlaylists(show)
    suspend fun setHideLocalMedia(hide: Boolean) = libraryView.setHideLocalMedia(hide)
    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) = libraryView.setTelegramTopicDisplayMode(mode)
    suspend fun setFoldersSource(source: FolderSource) = libraryView.setFoldersSource(source)
    suspend fun setFolderBackGestureNavigation(enabled: Boolean) = libraryView.setFolderBackGestureNavigation(enabled)

    // ===== Delegated to UserPreferencesAppearance =====
    val showPlayerFileInfoFlow: Flow<Boolean> get() = appearance.showPlayerFileInfoFlow
    suspend fun setShowPlayerFileInfo(show: Boolean) = appearance.setShowPlayerFileInfo(show)
    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> get() = appearance.fullPlayerLoadingTweaksFlow
    val navBarCornerRadiusFlow: Flow<Int> get() = appearance.navBarCornerRadiusFlow
    suspend fun setNavBarCornerRadius(radius: Int) = appearance.setNavBarCornerRadius(radius)
    val navBarStyleFlow: Flow<String> get() = appearance.navBarStyleFlow
    suspend fun setNavBarStyle(style: String) = appearance.setNavBarStyle(style)
    val navBarCompactModeFlow: Flow<Boolean> get() = appearance.navBarCompactModeFlow
    suspend fun setNavBarCompactMode(enabled: Boolean) = appearance.setNavBarCompactMode(enabled)
    val libraryNavigationModeFlow: Flow<String> get() = appearance.libraryNavigationModeFlow
    suspend fun setLibraryNavigationMode(mode: String) = appearance.setLibraryNavigationMode(mode)
    val carouselStyleFlow: Flow<String> get() = appearance.carouselStyleFlow
    suspend fun setCarouselStyle(style: String) = appearance.setCarouselStyle(style)
    val launchTabFlow: Flow<String> get() = appearance.launchTabFlow
    suspend fun setLaunchTab(tab: String) = appearance.setLaunchTab(tab)
    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) = appearance.setDelayAllFullPlayerContent(enabled)
    suspend fun setDelayAlbumCarousel(enabled: Boolean) = appearance.setDelayAlbumCarousel(enabled)
    suspend fun setDelaySongMetadata(enabled: Boolean) = appearance.setDelaySongMetadata(enabled)
    suspend fun setDelayProgressBar(enabled: Boolean) = appearance.setDelayProgressBar(enabled)
    suspend fun setDelayControls(enabled: Boolean) = appearance.setDelayControls(enabled)
    suspend fun setFullPlayerPlaceholders(enabled: Boolean) = appearance.setFullPlayerPlaceholders(enabled)
    suspend fun setTransparentPlaceholders(enabled: Boolean) = appearance.setTransparentPlaceholders(enabled)
    suspend fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) = appearance.setFullPlayerPlaceholdersOnClose(enabled)
    suspend fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) = appearance.setFullPlayerSwitchOnDragRelease(enabled)
    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) = appearance.setFullPlayerAppearThreshold(thresholdPercent)
    suspend fun setFullPlayerCloseThreshold(thresholdPercent: Int) = appearance.setFullPlayerCloseThreshold(thresholdPercent)
    suspend fun clearDeprecatedPlayerSheetPreference() = appearance.clearDeprecatedPlayerSheetPreference()
    val useSmoothCornersFlow: Flow<Boolean> get() = appearance.useSmoothCornersFlow
    suspend fun setUseSmoothCorners(enabled: Boolean) = appearance.setUseSmoothCorners(enabled)
    val albumArtQualityFlow: Flow<AlbumArtQuality> get() = appearance.albumArtQualityFlow
    suspend fun setAlbumArtQuality(quality: AlbumArtQuality) = appearance.setAlbumArtQuality(quality)
    val albumArtCacheLimitMbFlow: Flow<Int> get() = appearance.albumArtCacheLimitMbFlow
    suspend fun setAlbumArtCacheLimitMb(limitMb: Int) = appearance.setAlbumArtCacheLimitMb(limitMb)
    val tapBackgroundClosesPlayerFlow: Flow<Boolean> get() = appearance.tapBackgroundClosesPlayerFlow
    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) = appearance.setTapBackgroundClosesPlayer(enabled)
    val hapticsEnabledFlow: Flow<Boolean> get() = appearance.hapticsEnabledFlow
    suspend fun setHapticsEnabled(enabled: Boolean) = appearance.setHapticsEnabled(enabled)
    val collagePatternFlow: Flow<CollagePattern> get() = appearance.collagePatternFlow
    suspend fun setCollagePattern(pattern: CollagePattern) = appearance.setCollagePattern(pattern)
    val collageAutoRotateFlow: Flow<Boolean> get() = appearance.collageAutoRotateFlow
    suspend fun setCollageAutoRotate(enabled: Boolean) = appearance.setCollageAutoRotate(enabled)

    // ===== Delegated to UserPreferencesCollections =====
    val dailyMixSongIdsFlow: Flow<List<String>> get() = collections.dailyMixSongIdsFlow
    suspend fun saveDailyMixSongIds(songIds: List<String>) = collections.saveDailyMixSongIds(songIds)
    val yourMixSongIdsFlow: Flow<List<String>> get() = collections.yourMixSongIdsFlow
    suspend fun saveYourMixSongIds(songIds: List<String>) = collections.saveYourMixSongIds(songIds)
    val lastDailyMixUpdateFlow: Flow<Long> get() = collections.lastDailyMixUpdateFlow
    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) = collections.saveLastDailyMixUpdateTimestamp(timestamp)
    val favoriteSongIdsFlow: Flow<Set<String>> get() = collections.favoriteSongIdsFlow
    val playlistSongOrderModesFlow: Flow<Map<String, String>> get() = collections.playlistSongOrderModesFlow
    val legacyUserPlaylistsFlow: Flow<List<Playlist>> get() = collections.legacyUserPlaylistsFlow
    suspend fun getLegacyUserPlaylistsOnce(): List<Playlist> = collections.getLegacyUserPlaylistsOnce()
    suspend fun clearLegacyUserPlaylists() = collections.clearLegacyUserPlaylists()
    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) = collections.setPlaylistSongOrderMode(playlistId, modeValue)
    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) = collections.setPlaylistSongOrderModes(modes)
    suspend fun clearPlaylistSongOrderMode(playlistId: String) = collections.clearPlaylistSongOrderMode(playlistId)
    suspend fun toggleFavoriteSong(songId: String, removing: Boolean = false) = collections.toggleFavoriteSong(songId, removing)
    suspend fun setFavoriteSong(songId: String, isFavorite: Boolean) = collections.setFavoriteSong(songId, isFavorite)
    suspend fun clearFavoriteSongIds() = collections.clearFavoriteSongIds()
    val lastPlaylistIdFlow: Flow<String?> get() = collections.lastPlaylistIdFlow
    val lastPlaylistNameFlow: Flow<String?> get() = collections.lastPlaylistNameFlow
    suspend fun setLastPlaylist(playlistId: String, playlistName: String) = collections.setLastPlaylist(playlistId, playlistName)
    suspend fun clearLastPlaylist() = collections.clearLastPlaylist()

    // ===== Delegated to UserPreferencesBackup =====
    suspend fun clearPreferencesByKeys(keyNames: Set<String>) = backup.clearPreferencesByKeys(keyNames)
    suspend fun clearPreferencesExceptKeys(excludedKeyNames: Set<String>) = backup.clearPreferencesExceptKeys(excludedKeyNames)
    suspend fun exportPreferencesForBackup(): List<PreferenceBackupEntry> = backup.exportPreferencesForBackup()
    suspend fun importPreferencesFromBackup(entries: List<PreferenceBackupEntry>, clearExisting: Boolean = true) = backup.importPreferencesFromBackup(entries, clearExisting)
}
