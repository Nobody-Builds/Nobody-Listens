package com.nobodybuilds.listens.data.noise

import com.nobodybuilds.listens.data.lofi.AudioOut
import com.nobodybuilds.listens.data.noise.dsp.NoiseMixer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time noise: owns the [NoiseMixer] and its audio thread (the shared lofi [AudioOut]).
 * Nothing is created until [start]; after [stop] has faded out, the thread ends and the
 * AudioTrack is released, so it costs nothing while idle.
 *
 * Use [NoiseController] from app code; the foreground service (LofiService) and the
 * under-music mode drive this class.
 */
@Singleton
class NoiseEngine @Inject constructor() {
    private var mixer: NoiseMixer? = null
    private var out: AudioOut? = null
    @Volatile private var wantRunning = false
    /** Level multiplier for "under music" mode. */
    @Volatile private var levelScale = 1f

    val isRunning: Boolean get() = out?.isRunning == true

    /** Output peak level 0..1 for visuals; 0 before the first start. */
    val meter: Float get() = mixer?.levelMeter ?: 0f

    @Synchronized
    fun start(settings: NoiseSettings, fadeInMs: Float = settings.fadeInSeconds * 1000f, scale: Float = 1f): Boolean {
        wantRunning = true
        levelScale = scale
        val m = mixer ?: NoiseMixer(AudioOut.SAMPLE_RATE).also { mixer = it }
        apply(m, settings)
        m.setLevel(1f)
        m.fadeIn(fadeInMs.coerceAtLeast(150f))
        val current = out
        if (current != null && current.isRunning) return true
        return startOut(m)
    }

    private fun startOut(m: NoiseMixer): Boolean {
        val o = AudioOut(m, "noise", onFinished = {
            // A start() that raced the end of a fade-out: bring the thread back.
            if (wantRunning) synchronized(this) { if (wantRunning && out?.isRunning != true) startOut(m) }
        })
        out = o
        return o.start()
    }

    /** Applies new settings while playing (smoothly). */
    @Synchronized
    fun update(settings: NoiseSettings, scale: Float = levelScale) {
        levelScale = scale
        mixer?.let { apply(it, settings) }
    }

    private fun apply(m: NoiseMixer, s: NoiseSettings) {
        m.setLayers(s.layers.map { it.kind to it.volume })
        m.setMaster(s.masterVolume * levelScale)
        m.setTone(s.tone)
        m.setSwell(s.swell)
        m.setBinaural(s.carrierHz.toFloat(), s.binaural.beatHz)
    }

    /** Fades out and releases the audio thread when silent. A second call while fading is ignored. */
    @Synchronized
    fun stop(fadeOutMs: Float = 900f) {
        if (!wantRunning) return
        wantRunning = false
        val m = mixer ?: return
        if (out?.isRunning == true) m.fadeOut(fadeOutMs) else out = null
    }

    /** Extra level for long, externally stepped fades (sleep timer) and breaks. */
    fun setLevel(level: Float) { mixer?.setLevel(level) }

    fun setDucked(ducked: Boolean) { mixer?.setDucked(ducked) }
}
