package com.nobodybuilds.listens.data.lofi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.nobodybuilds.listens.data.lofi.dsp.ChimeSynth
import com.nobodybuilds.listens.data.lofi.dsp.LofiSynth
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time procedural lofi. Owns the [LofiSynth] and its audio thread. Nothing is created
 * until [start]; after [stop] the thread and AudioTrack are released (zero CPU when idle).
 *
 * Audio focus and the foreground service live in [LofiService]; use [LofiController] from
 * app code rather than this class directly.
 */
@Singleton
class LofiEngine @Inject constructor() {
    private var synth: LofiSynth? = null
    private var out: AudioOut? = null
    @Volatile private var lastSettings: LofiSettings = LofiSettings()
    @Volatile private var wantRunning = false

    val isRunning: Boolean get() = out?.isRunning == true

    /** Visual meters (0 master, 1 kick, 2 snare, 3 hat, 4 bass, 5 keys), 0..1. Read-only. */
    fun meters(): FloatArray? = synth?.meters

    @Synchronized
    fun start(settings: LofiSettings, fadeInMs: Float = 1500f): Boolean {
        lastSettings = settings
        wantRunning = true
        val s = synth ?: LofiSynth(AudioOut.SAMPLE_RATE, settings).also { synth = it }
        s.submit(settings)
        s.fadeIn(fadeInMs)
        val current = out
        if (current != null && current.isRunning) return true
        return startOut(s)
    }

    private fun startOut(s: LofiSynth): Boolean {
        val o = AudioOut(s, "lofi", onFinished = {
            // A start() that raced the end of a fade-out: bring the thread back.
            if (wantRunning) synchronized(this) { if (wantRunning && out?.isRunning != true) startOut(s) }
        })
        out = o
        return o.start()
    }

    fun update(settings: LofiSettings) {
        lastSettings = settings
        synth?.submit(settings)
    }

    /** Fades out and releases the audio thread when silent. */
    @Synchronized
    fun stop(fadeOutMs: Float = 900f) {
        wantRunning = false
        val s = synth ?: return
        if (out?.isRunning == true) s.fadeOut(fadeOutMs) else out = null
    }

    fun setDucked(ducked: Boolean) { synth?.setDucked(ducked) }

    // ---------------------------------------------------------------- chime
    private val chimeBuffer: FloatArray by lazy { ChimeSynth.render(AudioOut.SAMPLE_RATE) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** A soft bell. Plays on its own short static track; works while other audio is playing. */
    fun playChime(volume: Float = 0.8f) {
        Thread({
            runCatching {
                val mono = chimeBuffer
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(AudioOut.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(mono.size * 4)
                    .build()
                track.write(mono, 0, mono.size, AudioTrack.WRITE_BLOCKING)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                val durationMs = mono.size * 1000L / AudioOut.SAMPLE_RATE + 200L
                mainHandler.postDelayed({ runCatching { track.stop() }; runCatching { track.release() } }, durationMs)
            }.onFailure { Timber.tag("LofiEngine").w(it, "Chime failed") }
        }, "nobody-listens-chime").start()
    }
}
