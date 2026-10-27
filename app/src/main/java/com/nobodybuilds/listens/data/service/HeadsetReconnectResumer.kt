package com.nobodybuilds.listens.data.service

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.Player
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import timber.log.Timber

/**
 * Resumes playback when headphones come back within a short window after they were unplugged
 * (and the player paused for "audio becoming noisy"), when the user has turned that on.
 */
internal class HeadsetReconnectResumer(
    private val context: Context,
    private val engine: DualPlayerEngine
) {
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var headsetReconnectCallback: AudioDeviceCallback? = null
    var shouldResumeAfterHeadsetReconnect = false
    var lastNoisyPauseRealtimeMs = 0L
    var resumeOnHeadsetReconnectEnabled = false

    fun registerHeadsetReconnectMonitor() {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!addedDevices.any(::isReconnectableHeadsetOutput)) return
                maybeResumeAfterHeadsetReconnect()
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        headsetReconnectCallback = callback
    }

    fun unregisterHeadsetReconnectMonitor() {
        headsetReconnectCallback?.let { callback ->
            runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        }
        headsetReconnectCallback = null
        clearHeadsetReconnectResume()
    }

    private fun maybeResumeAfterHeadsetReconnect() {
        if (!resumeOnHeadsetReconnectEnabled || !shouldResumeAfterHeadsetReconnect) return

        val elapsedSinceNoisyPause = SystemClock.elapsedRealtime() - lastNoisyPauseRealtimeMs
        if (elapsedSinceNoisyPause > HEADSET_RECONNECT_RESUME_WINDOW_MS) {
            clearHeadsetReconnectResume()
            return
        }

        if (!hasReconnectableHeadsetOutput()) {
            return
        }

        val player = engine.masterPlayer
        if (
            player.currentMediaItem == null ||
            player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            clearHeadsetReconnectResume()
            return
        }

        Timber.tag(TAG).d("Resuming playback after headset reconnect")
        clearHeadsetReconnectResume()
        player.play()
    }

    private fun hasReconnectableHeadsetOutput(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any(::isReconnectableHeadsetOutput)
    }

    private fun isReconnectableHeadsetOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
    }

    fun clearHeadsetReconnectResume() {
        shouldResumeAfterHeadsetReconnect = false
        lastNoisyPauseRealtimeMs = 0L
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
        const val HEADSET_RECONNECT_RESUME_WINDOW_MS = 15_000L
    }
}
