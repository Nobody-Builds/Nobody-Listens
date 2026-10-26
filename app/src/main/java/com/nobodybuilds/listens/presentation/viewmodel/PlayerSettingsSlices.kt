package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.preferences.AlbumArtQuality
import com.nobodybuilds.listens.data.preferences.CarouselStyle
import com.nobodybuilds.listens.data.preferences.FullPlayerLoadingTweaks
import com.nobodybuilds.listens.data.preferences.NavBarStyle
import com.nobodybuilds.listens.data.preferences.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

// ---------------------------------------------------------------------------
// FullPlayerSlice — consolidates 11 independent flows into ONE subscription.
// Previously FullPlayerContent had ~13 separate collectAsStateWithLifecycle()
// calls. Each emission from any of them caused a recompose of the entire 2k-line
// composable. Now a single collect + distinctUntilChanged batches all settings.
// ---------------------------------------------------------------------------
data class FullPlayerSlice(
    val currentSongArtists: List<Artist> = emptyList(),
    val lyricsSyncOffset: Int = 0,
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val audioMetadata: PlaybackAudioMetadata = PlaybackAudioMetadata(),
    val showPlayerFileInfo: Boolean = true,
    val immersiveLyricsEnabled: Boolean = false,
    val immersiveLyricsTimeout: Long = 4000L,
    val isImmersiveTemporarilyDisabled: Boolean = false,
    val isRemotePlaybackActive: Boolean = false,
    val selectedRouteName: String? = null,
    val isBluetoothEnabled: Boolean = false,
    val bluetoothName: String? = null
)

// ---------------------------------------------------------------------------
// PlayerConfigSlice — consolidates 7 infrequently-changing preference flows
// into ONE subscription. Previously the player sheet had 7 separate
// collectAsStateWithLifecycle() calls for config values, each causing a full
// sheet recomposition when any preference changed.
// ---------------------------------------------------------------------------
data class PlayerConfigSlice(
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val tapBackgroundClosesPlayer: Boolean = false,
    val useSmoothCorners: Boolean = true,
    val playerThemePreference: String = ThemePreference.ALBUM_ART
)

/**
 * Builds PlayerViewModel's [FullPlayerSlice] and [PlayerConfigSlice]: many settings flows
 * combined into one subscription each, so the player sheet recomposes once per change.
 */
internal class PlayerSettingsSlices(
    private val scope: CoroutineScope,
    private val currentSongArtists: StateFlow<List<Artist>>,
    private val currentSongLyricsSyncOffset: StateFlow<Int>,
    private val albumArtQuality: StateFlow<AlbumArtQuality>,
    private val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata>,
    private val showPlayerFileInfo: StateFlow<Boolean>,
    private val immersiveLyricsEnabled: StateFlow<Boolean>,
    private val immersiveLyricsTimeout: StateFlow<Long>,
    private val isImmersiveTemporarilyDisabled: StateFlow<Boolean>,
    private val isRemotePlaybackActive: StateFlow<Boolean>,
    private val selectedRouteName: StateFlow<String?>,
    private val isBluetoothEnabled: StateFlow<Boolean>,
    private val bluetoothName: StateFlow<String?>,
    private val navBarCornerRadius: StateFlow<Int>,
    private val navBarStyle: StateFlow<String>,
    private val carouselStyle: StateFlow<String>,
    private val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks>,
    private val tapBackgroundClosesPlayer: StateFlow<Boolean>,
    private val useSmoothCorners: StateFlow<Boolean>,
    private val playerThemePreference: StateFlow<String>
) {
    // Intermediate combine #1: 5 settings flows
    private val fullPlayerSlicePart1 = combine(
        currentSongArtists,
        currentSongLyricsSyncOffset,
        albumArtQuality,
        playbackAudioMetadata,
        showPlayerFileInfo
    ) { artists: List<Artist>, syncOffset: Int, artQuality: AlbumArtQuality,
        audioMeta: PlaybackAudioMetadata, showFileInfo: Boolean ->
        FullPlayerSlicePart1(artists, syncOffset, artQuality, audioMeta, showFileInfo)
    }

    private data class BluetoothSlice(val enabled: Boolean, val name: String?)

    private val bluetoothSlice = combine(isBluetoothEnabled, bluetoothName) { bt, btName ->
        BluetoothSlice(bt, btName)
    }

    // Intermediate combine #2: remaining flows (≤5 for Kotlin type inference)
    private val fullPlayerSlicePart2 = combine(
        immersiveLyricsEnabled,
        immersiveLyricsTimeout,
        isImmersiveTemporarilyDisabled,
        isRemotePlaybackActive,
        combine(selectedRouteName, bluetoothSlice) { route, bt -> route to bt }
    ) { immersive: Boolean, immersiveTimeout: Long, immersiveDisabled: Boolean,
        remotePb: Boolean, routeAndBt: Pair<String?, BluetoothSlice> ->
        val (routeName, bt) = routeAndBt
        FullPlayerSlicePart2(immersive, immersiveTimeout, immersiveDisabled, remotePb, routeName, bt.enabled, bt.name)
    }

    private data class FullPlayerSlicePart1(
        val currentSongArtists: List<Artist>,
        val lyricsSyncOffset: Int,
        val albumArtQuality: AlbumArtQuality,
        val audioMetadata: PlaybackAudioMetadata,
        val showPlayerFileInfo: Boolean
    )

    private data class FullPlayerSlicePart2(
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val isImmersiveTemporarilyDisabled: Boolean,
        val isRemotePlaybackActive: Boolean,
        val selectedRouteName: String?,
        val isBluetoothEnabled: Boolean,
        val bluetoothName: String?
    )

    val fullPlayerSlice: StateFlow<FullPlayerSlice> = combine(
        fullPlayerSlicePart1,
        fullPlayerSlicePart2
    ) { p1, p2 ->
        FullPlayerSlice(
            currentSongArtists = p1.currentSongArtists,
            lyricsSyncOffset = p1.lyricsSyncOffset,
            albumArtQuality = p1.albumArtQuality,
            audioMetadata = p1.audioMetadata,
            showPlayerFileInfo = p1.showPlayerFileInfo,
            immersiveLyricsEnabled = p2.immersiveLyricsEnabled,
            immersiveLyricsTimeout = p2.immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = p2.isImmersiveTemporarilyDisabled,
            isRemotePlaybackActive = p2.isRemotePlaybackActive,
            selectedRouteName = p2.selectedRouteName,
            isBluetoothEnabled = p2.isBluetoothEnabled,
            bluetoothName = p2.bluetoothName
        )
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), FullPlayerSlice())

    private val playerConfigSlicePart1 = combine(
        navBarCornerRadius,
        navBarStyle,
        carouselStyle,
        fullPlayerLoadingTweaks,
        tapBackgroundClosesPlayer
    ) { radius, style, carousel, tweaks, tapClose ->
        PlayerConfigSlicePart1(radius, style, carousel, tweaks, tapClose)
    }

    private data class PlayerConfigSlicePart1(
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val carouselStyle: String,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
        val tapBackgroundClosesPlayer: Boolean
    )

    val playerConfigSlice: StateFlow<PlayerConfigSlice> = combine(
        playerConfigSlicePart1,
        useSmoothCorners,
        playerThemePreference
    ) { p1, smoothCorners, themePref ->
        PlayerConfigSlice(
            navBarCornerRadius = p1.navBarCornerRadius,
            navBarStyle = p1.navBarStyle,
            carouselStyle = p1.carouselStyle,
            fullPlayerLoadingTweaks = p1.fullPlayerLoadingTweaks,
            tapBackgroundClosesPlayer = p1.tapBackgroundClosesPlayer,
            useSmoothCorners = smoothCorners,
            playerThemePreference = themePref
        )
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), PlayerConfigSlice())
}
