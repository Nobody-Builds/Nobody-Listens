package com.nobodybuilds.listens.data.noise

import com.nobodybuilds.listens.data.noise.dsp.NoiseMixer
import kotlinx.serialization.Serializable

@Serializable
data class NoiseLayer(val kind: NoiseKind, val volume: Float = 0.6f)

/** Everything the noise generator listens to. Levels are 0..1, [tone] is -1 (darker) .. 1 (brighter). */
@Serializable
data class NoiseSettings(
    val layers: List<NoiseLayer> = listOf(NoiseLayer(NoiseKind.Brown, 0.7f), NoiseLayer(NoiseKind.Rain, 0.5f)),
    val tone: Float = 0f,
    val swell: Boolean = true,
    val fadeInSeconds: Int = 10,
    val masterVolume: Float = 0.7f,
    val binaural: BinauralMode = BinauralMode.Off,
    val carrierHz: Int = 200,
    /** Play softly under music instead of on its own (no audio focus, only while music plays). */
    val underMusic: Boolean = false,
    /** The preset these settings came from, for labels; cleared when changed by hand. */
    val presetName: String? = null,
) {
    fun normalized(): NoiseSettings = copy(
        layers = layers.distinctBy { it.kind }.take(NoiseMixer.MAX_LAYERS)
            .map { it.copy(volume = it.volume.coerceIn(0f, 1f)) },
        tone = tone.coerceIn(-1f, 1f),
        fadeInSeconds = fadeInSeconds.coerceIn(0, 120),
        masterVolume = masterVolume.coerceIn(0f, 1f),
        carrierHz = carrierHz.coerceIn(100, 400),
    )

    /** A short description, e.g. "Brown + rain". */
    val summary: String
        get() = if (layers.isEmpty()) "No sounds"
        else layers.joinToString(" + ") { it.kind.label }.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        val CARRIERS = listOf(150, 200, 250, 300)
        val FADE_IN_CHOICES = listOf(0, 10, 30, 60)
    }
}

@Serializable
data class NoisePreset(val name: String, val settings: NoiseSettings, val builtIn: Boolean = false)

object NoiseDefaults {
    val DeepWork = NoisePreset(
        "Deep work",
        NoiseSettings(
            layers = listOf(NoiseLayer(NoiseKind.Brown, 0.7f), NoiseLayer(NoiseKind.Rain, 0.45f)),
            tone = -0.2f, presetName = "Deep work",
        ),
        builtIn = true,
    )
    val CalmDesk = NoisePreset(
        "Calm desk",
        NoiseSettings(
            layers = listOf(NoiseLayer(NoiseKind.Pink, 0.55f), NoiseLayer(NoiseKind.Cafe, 0.5f)),
            tone = 0f, presetName = "Calm desk",
        ),
        builtIn = true,
    )
    val Sleep = NoisePreset(
        "Sleep",
        NoiseSettings(
            layers = listOf(NoiseLayer(NoiseKind.Brown, 0.6f), NoiseLayer(NoiseKind.Ocean, 0.6f)),
            tone = -0.5f, fadeInSeconds = 30, masterVolume = 0.6f, presetName = "Sleep",
        ),
        builtIn = true,
    )
    val all = listOf(DeepWork, CalmDesk, Sleep)
}
