package com.nobodybuilds.listens.data.service

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.equalizer.EqualizerManager
import com.nobodybuilds.listens.data.preferences.EqualizerPreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Applies the equalizer and playback preferences to [MusicService] and follows their changes. */
internal class MusicServicePreferenceSync(
    private val serviceScope: CoroutineScope,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val equalizerManager: EqualizerManager,
    private val engine: DualPlayerEngine,
    private val state: MusicServiceState,
    private val headset: HeadsetReconnectResumer,
    private val playbackVolume: PlaybackVolumeController,
    private val mediaButtons: MediaSessionButtons,
    private val sessionProvider: () -> MediaSession?
) {
    private val mediaSession: MediaSession? get() = sessionProvider()
    private var keepPlayingInBackground by state::keepPlayingInBackground
    private var persistentShuffleEnabled by state::persistentShuffleEnabled
    private var isManualShuffleEnabled by state::isManualShuffleEnabled
    private var resumeOnHeadsetReconnectEnabled by headset::resumeOnHeadsetReconnectEnabled
    private var replayGainEnabled by playbackVolume::replayGainEnabled
    private var replayGainUseAlbumGain by playbackVolume::replayGainUseAlbumGain

    private fun clearHeadsetReconnectResume() = headset.clearHeadsetReconnectResume()

    private fun applyReplayGain(mediaItem: MediaItem?) = playbackVolume.applyReplayGain(mediaItem)

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    /** Starts the collectors; they run until [serviceScope] is cancelled. */
    fun start() {
        // Restore equalizer state from preferences and only attach audio effects when
        // the user actually has at least one effect enabled for the current session.
        serviceScope.launch {
            val eqEnabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
            val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
            val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
            val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
            val bassBoostStrength = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
            val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
            val virtualizerStrength = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
            val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
            val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()

            equalizerManager.restoreState(
                eqEnabled, presetName, customBands,
                bassBoostEnabled, bassBoostStrength,
                virtualizerEnabled, virtualizerStrength,
                loudnessEnabled, loudnessStrength
            )

            val sessionId = engine.getAudioSessionId()
            if (sessionId != 0) {
                equalizerManager.attachToAudioSessionIfNeeded(sessionId)
            }

            // Re-attach equalizer whenever the active audio session changes (e.g. crossfade)
            engine.activeAudioSessionId.collect { newSessionId ->
                if (newSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(newSessionId)
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.keepPlayingInBackgroundFlow.collect { enabled ->
                keepPlayingInBackground = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.hiFiModeEnabledFlow.collect { enabled ->
                engine.setHiFiMode(enabled)
            }
        }

        serviceScope.launch {
            userPreferencesRepository.resumeOnHeadsetReconnectFlow.collect { enabled ->
                resumeOnHeadsetReconnectEnabled = enabled
                if (!enabled) {
                    clearHeadsetReconnectResume()
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.persistentShuffleEnabledFlow.collect { enabled ->
                persistentShuffleEnabled = enabled
            }
        }

        // ReplayGain preference collectors
        serviceScope.launch {
            userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
                replayGainEnabled = enabled
                // Re-apply to current track when toggled
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
                replayGainUseAlbumGain = useAlbum
                // Re-apply to current track when mode changes
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }

        // Initialize shuffle state from preferences
        serviceScope.launch {
            val persistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (persistent) {
                isManualShuffleEnabled = userPreferencesRepository.isShuffleOnFlow.first()
                mediaSession?.let { refreshMediaSessionUi(it) }
            }
        }
    }
}
