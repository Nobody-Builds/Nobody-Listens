package com.nobodybuilds.listens.data.lofi

import kotlinx.serialization.Serializable

/** The four moods. Each is a preset of key, tempo, chord colour, swing and textures. */
@Serializable
enum class LofiMood(val label: String, val blurb: String) {
    Dreamy("Dreamy", "Slow, lush and hazy"),
    Rainy("Rainy", "Minor keys and rain on the window"),
    Jazzy("Jazzy", "Swung ii–V–I and café chatter"),
    Sunny("Sunny", "Brighter, bouncier, warm");
}

@Serializable
enum class ChordColour(val label: String) {
    Simple("Simple"),
    Jazzy("Jazzy"),
    Lush("Lush");
}

/**
 * Everything the lofi generator listens to. All levels are 0..1. Changing any field while
 * playing is smooth: levels ramp, tempo glides and harmony changes land on the next bar.
 *
 * [seed] makes the tune deterministic: the same settings and seed always play the same music.
 */
@Serializable
data class LofiSettings(
    val mood: LofiMood = LofiMood.Dreamy,
    val tempoBpm: Int = 72,
    /** Tonic pitch class, 0 = C … 11 = B. */
    val key: Int = 5,
    /** 0..0.6 — how late the off-beat sixteenths land. */
    val swing: Float = 0.25f,
    val chordColour: ChordColour = ChordColour.Lush,
    val drumsOn: Boolean = true,
    val drumsVolume: Float = 0.7f,
    val melodyOn: Boolean = true,
    val melodyVolume: Float = 0.55f,
    val crackle: Float = 0.35f,
    val rain: Float = 0f,
    val cafe: Float = 0f,
    val wobble: Float = 0.45f,
    val warmth: Float = 0.6f,
    val masterVolume: Float = 0.8f,
    val seed: Long = 1L,
) {
    fun normalized(): LofiSettings = copy(
        tempoBpm = tempoBpm.coerceIn(MIN_BPM, MAX_BPM),
        key = ((key % 12) + 12) % 12,
        swing = swing.coerceIn(0f, MAX_SWING),
        drumsVolume = drumsVolume.coerceIn(0f, 1f),
        melodyVolume = melodyVolume.coerceIn(0f, 1f),
        crackle = crackle.coerceIn(0f, 1f),
        rain = rain.coerceIn(0f, 1f),
        cafe = cafe.coerceIn(0f, 1f),
        wobble = wobble.coerceIn(0f, 1f),
        warmth = warmth.coerceIn(0f, 1f),
        masterVolume = masterVolume.coerceIn(0f, 1f),
    )

    /** Applies the mood preset, keeping the seed and the user's on/off and volume choices. */
    fun withMood(newMood: LofiMood): LofiSettings {
        val p = forMood(newMood, seed)
        return copy(
            mood = newMood, tempoBpm = p.tempoBpm, key = p.key, swing = p.swing,
            chordColour = p.chordColour, crackle = p.crackle, rain = p.rain, cafe = p.cafe,
            wobble = p.wobble, warmth = p.warmth,
        )
    }

    val keyName: String get() = KEY_NAMES[((key % 12) + 12) % 12]

    companion object {
        const val MIN_BPM = 60
        const val MAX_BPM = 95
        const val MAX_SWING = 0.6f
        val KEY_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

        fun forMood(mood: LofiMood, seed: Long = 1L): LofiSettings = when (mood) {
            LofiMood.Dreamy -> LofiSettings(
                mood = mood, tempoBpm = 70, key = 5, swing = 0.2f, chordColour = ChordColour.Lush,
                crackle = 0.3f, rain = 0f, cafe = 0f, wobble = 0.6f, warmth = 0.65f, seed = seed
            )
            LofiMood.Rainy -> LofiSettings(
                mood = mood, tempoBpm = 66, key = 9, swing = 0.25f, chordColour = ChordColour.Jazzy,
                crackle = 0.4f, rain = 0.55f, cafe = 0f, wobble = 0.45f, warmth = 0.75f, seed = seed
            )
            LofiMood.Jazzy -> LofiSettings(
                mood = mood, tempoBpm = 84, key = 10, swing = 0.45f, chordColour = ChordColour.Jazzy,
                crackle = 0.35f, rain = 0f, cafe = 0.3f, wobble = 0.3f, warmth = 0.5f, seed = seed
            )
            LofiMood.Sunny -> LofiSettings(
                mood = mood, tempoBpm = 90, key = 0, swing = 0.3f, chordColour = ChordColour.Simple,
                crackle = 0.25f, rain = 0f, cafe = 0f, wobble = 0.25f, warmth = 0.35f, seed = seed
            )
        }
    }
}

@Serializable
data class LofiPreset(val name: String, val settings: LofiSettings)

/** A public internet radio station. */
@Serializable
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String? = null,
    val bitrate: Int = 0,
    val country: String? = null,
    val tags: String? = null,
)

enum class AmbienceKind(val label: String) {
    Rain("Rain"),
    Cafe("Café"),
    Fireplace("Fireplace"),
    Waves("Waves");
}

/** One ambient layer mixed over normal music. */
data class AmbienceLayerState(val enabled: Boolean = false, val volume: Float = 0.5f)
