package com.nobodybuilds.listens.data

import android.graphics.Bitmap
import android.os.SystemClock
import com.nobodybuilds.listens.shared.WearPlaybackResult
import com.nobodybuilds.listens.shared.WearPlayerState
import com.nobodybuilds.listens.shared.WearVolumeState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class WearOutputTarget {
    PHONE,
    WATCH,
}

/**
 * Singleton repository that holds the current player state received from the phone.
 * Acts as the single source of truth for the Wear UI layer.
 */
@Singleton
class WearStateRepository @Inject constructor() {

    private val _playerState = MutableStateFlow(WearPlayerState())
    val playerState: StateFlow<WearPlayerState> = _playerState.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    private val _phoneDeviceName = MutableStateFlow("Phone")
    val phoneDeviceName: StateFlow<String> = _phoneDeviceName.asStateFlow()

    private val _outputTarget = MutableStateFlow(WearOutputTarget.PHONE)
    val outputTarget: StateFlow<WearOutputTarget> = _outputTarget.asStateFlow()

    private val _volumeState = MutableStateFlow(WearVolumeState())
    val volumeState: StateFlow<WearVolumeState> = _volumeState.asStateFlow()

    private val _playbackResults = MutableSharedFlow<WearPlaybackResult>(extraBufferCapacity = 8)
    val playbackResults: SharedFlow<WearPlaybackResult> = _playbackResults.asSharedFlow()

    @Volatile
    private var playerStateReceivedAtMs = 0L

    /**
     * Where the phone's song is now. The phone publishes its position only when something changes, so while it
     * plays, the time since that update is added (the same way the player screen moves its progress).
     */
    fun livePhonePositionMs(): Long {
        val state = _playerState.value
        if (state.totalDurationMs > 0L) {
            return PlaybackPosition(
                positionMs = state.currentPositionMs,
                durationMs = state.totalDurationMs,
                isPlaying = state.isPlaying,
                anchoredAtMs = playerStateReceivedAtMs,
            ).positionAt(SystemClock.elapsedRealtime())
        }
        if (!state.isPlaying) return state.currentPositionMs
        val position = state.currentPositionMs + (SystemClock.elapsedRealtime() - playerStateReceivedAtMs)
        return position
    }

    fun updatePlayerState(state: WearPlayerState) {
        playerStateReceivedAtMs = SystemClock.elapsedRealtime()
        _playerState.value = state
        if (state.volumeMax > 0) {
            updateVolumeState(
                level = state.volumeLevel,
                max = state.volumeMax,
                routeType = _volumeState.value.routeType,
                routeName = _volumeState.value.routeName,
            )
        }
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        _albumArt.value = bitmap
    }

    fun setPhoneConnected(connected: Boolean) {
        _isPhoneConnected.value = connected
    }

    fun setPhoneDeviceName(name: String) {
        if (name.isNotBlank()) {
            _phoneDeviceName.value = name
        }
    }

    fun setOutputTarget(target: WearOutputTarget) {
        _outputTarget.value = target
    }

    fun publishPlaybackResult(result: WearPlaybackResult) {
        _playbackResults.tryEmit(result)
    }

    fun updateVolumeState(
        level: Int,
        max: Int,
        routeType: String = _volumeState.value.routeType,
        routeName: String = _volumeState.value.routeName,
    ) {
        val safeMax = max.coerceAtLeast(0)
        val safeLevel = level.coerceIn(0, safeMax.takeIf { it > 0 } ?: 0)
        _volumeState.value = WearVolumeState(
            level = safeLevel,
            max = safeMax,
            routeType = routeType,
            routeName = routeName,
        )
    }

    fun nudgePhoneVolumeLevel(delta: Int) {
        val current = _volumeState.value
        if (current.max <= 0) return
        val next = (current.level + delta).coerceIn(0, current.max)
        _volumeState.value = current.copy(level = next)
    }
}
