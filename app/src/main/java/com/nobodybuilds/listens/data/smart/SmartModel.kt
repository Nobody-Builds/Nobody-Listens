package com.nobodybuilds.listens.data.smart

/**
 * Constants for the on-device YAMNet model (AudioSet, 521 classes).
 */
object SmartModel {
    /** Bump when feature extraction changes; songs analysed with an older version get re-indexed. */
    const val MODEL_VERSION = 1

    const val ASSET_PATH = "ml/yamnet.tflite"
    const val NUM_CLASSES = 521
    const val SAMPLE_RATE = 16_000
    const val FRAME_SAMPLES = 15_600 // 0.975 s

    const val MUSIC = 132

    // Vocal-ish classes, used for the "Focus" mix (low vocals).
    val VOCAL_CLASSES = intArrayOf(0, 24, 25, 27, 31, 249, 250)

    /** Music-related range of the AudioSet ontology (instruments, genres, moods). */
    const val MUSIC_RANGE_START = 133
    const val MUSIC_RANGE_END = 276 // inclusive

    val GENRES: Map<Int, String> = linkedMapOf(
        211 to "Pop", 212 to "Hip hop", 214 to "Rock", 215 to "Metal", 216 to "Punk",
        221 to "R&B", 222 to "Soul", 223 to "Reggae", 224 to "Country", 227 to "Funk",
        228 to "Folk", 230 to "Jazz", 231 to "Disco", 232 to "Classical", 233 to "Opera",
        234 to "Electronic", 235 to "House", 236 to "Techno", 237 to "Dubstep",
        238 to "Drum and bass", 240 to "EDM", 241 to "Ambient", 242 to "Trance",
        243 to "Latin", 246 to "Blues", 248 to "New age", 249 to "Vocal", 250 to "A cappella",
        252 to "Afrobeat", 254 to "Gospel", 255 to "Asian", 256 to "Carnatic",
        257 to "Bollywood", 259 to "Traditional", 260 to "Indie", 265 to "Soundtrack",
        266 to "Lullaby", 269 to "Dance"
    )

    const val HAPPY = 271
    const val SAD = 272
    const val TENDER = 273
    const val EXCITING = 274
    const val ANGRY = 275
    const val SCARY = 276

    const val MOOD_CALM = "Calm"
    const val MOOD_UPBEAT = "Upbeat"
    const val MOOD_MELANCHOLY = "Melancholy"
    const val MOOD_ENERGETIC = "Energetic"
    const val MOOD_ROMANTIC = "Romantic"
    const val MOOD_DARK = "Dark"
    const val MOOD_PARTY = "Party"

    val MOODS = listOf(MOOD_CALM, MOOD_UPBEAT, MOOD_MELANCHOLY, MOOD_ENERGETIC, MOOD_ROMANTIC, MOOD_DARK, MOOD_PARTY)
}
