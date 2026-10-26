package com.nobodybuilds.listens.data.noise

/** Group a sound is listed under on the Noise picker. */
enum class NoiseGroup(val label: String) {
    Colour("Noise colours"),
    Everyday("Everyday sounds"),
}

/**
 * Every sound the noise generator can make. All are synthesized live on the phone (nothing is
 * downloaded). [trim] evens out loudness so every sound sits at a similar level at the same volume.
 */
enum class NoiseKind(val label: String, val group: NoiseGroup, val trim: Float) {
    White("White", NoiseGroup.Colour, 0.52f),
    Pink("Pink", NoiseGroup.Colour, 0.93f),
    Brown("Brown", NoiseGroup.Colour, 1.1f),
    Grey("Grey", NoiseGroup.Colour, 0.57f),
    Blue("Blue", NoiseGroup.Colour, 1.3f),
    Violet("Violet", NoiseGroup.Colour, 0.33f),
    Fan("Fan", NoiseGroup.Everyday, 0.93f),
    Rain("Rain", NoiseGroup.Everyday, 1.18f),
    Ocean("Ocean waves", NoiseGroup.Everyday, 1.08f),
    Stream("Stream", NoiseGroup.Everyday, 1.75f),
    Wind("Wind", NoiseGroup.Everyday, 1.28f),
    Fireplace("Fireplace", NoiseGroup.Everyday, 0.98f),
    Cafe("Café", NoiseGroup.Everyday, 0.98f),
    Train("Train cabin", NoiseGroup.Everyday, 0.58f),
    Airplane("Airplane cabin", NoiseGroup.Everyday, 0.56f);
}

/** Optional binaural beat presets. [beatHz] is the left/right difference. */
enum class BinauralMode(val label: String, val beatHz: Float, val blurb: String) {
    Off("Off", 0f, ""),
    Focus("Focus", 14f, "About 14 Hz (beta)"),
    Calm("Calm", 10f, "About 10 Hz (alpha)");
}
