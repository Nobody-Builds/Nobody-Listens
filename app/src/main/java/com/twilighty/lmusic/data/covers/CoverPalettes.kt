package com.twilighty.lmusic.data.covers

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Daylight colours as ARGB ints (pure Kotlin so the generator and its tests need no Android). */
object NbCoverColors {
    const val NIGHT_SHIFT: Int = 0xFF172033.toInt()
    const val PAPER: Int = 0xFFF5F7FB.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLUEPRINT: Int = 0xFF2457A6.toInt()
    const val HARD_HAT: Int = 0xFFFDC521.toInt()
    const val BRICK: Int = 0xFFEA724A.toInt()
    const val SKY: Int = 0xFFCFE0F7.toInt()
    const val BUTTER: Int = 0xFFFFE8A3.toInt()
    const val GREEN: Int = 0xFFA6D98F.toInt()
    const val ACCENT_SOFT: Int = 0xFFE3ECF8.toInt()
    const val EMPHASIS_SOFT: Int = 0xFFFFF6D6.toInt()

    /**
     * Deep Daylight colours that carry Paper initials at 4.5:1 or more. Same values and order
     * as the NbArt placeholder palette (NbComponents.kt reads this list).
     */
    val ART_PALETTE: List<Int> = listOf(
        0xFF2457A6, 0xFF172033, 0xFFA3412A, 0xFF3B6B2B,
        0xFF4F5A70, 0xFF1A417E, 0xFF6B4E0E, 0xFF7A3324,
    ).map { it.toInt() }

    /** The colour NbArt uses as the placeholder for [key], so a made cover matches what was shown. */
    fun artColorFor(key: String): Int = ART_PALETTE[(key.hashCode() and 0x7fffffff) % ART_PALETTE.size]
}

/** Three colours per cover: a background and two pattern colours. */
data class ResolvedPalette(val background: Int, val primary: Int, val secondary: Int) {
    val colors: List<Int> get() = listOf(background, primary, secondary)
}

/** A palette chip in the studio. [AUTO] picks one per album from the seed. */
enum class CoverPaletteChoice(val label: String) {
    AUTO("Auto"),
    BRICK("Brick"),
    NIGHT_SHIFT("Night Shift"),
    SKY("Sky"),
    GREEN("Green"),
    BUTTER("Butter"),
    PAPER("Paper"),
    PASTEL("Pastel"),
    COVER("Cover");

    companion object {
        val fixed: List<CoverPaletteChoice> = entries.filter { it != AUTO }
    }
}

object CoverPalettes {
    private val C = NbCoverColors

    fun resolve(choice: CoverPaletteChoice, key: String, rng: SeededRandom? = null): ResolvedPalette = when (choice) {
        CoverPaletteChoice.AUTO -> {
            val pick = if (rng != null) CoverPaletteChoice.fixed[rng.nextInt(CoverPaletteChoice.fixed.size)]
            else CoverPaletteChoice.fixed[(key.hashCode() and 0x7fffffff) % CoverPaletteChoice.fixed.size]
            resolve(pick, key, null)
        }
        CoverPaletteChoice.BRICK -> ResolvedPalette(C.WHITE, C.BRICK, C.NIGHT_SHIFT)
        CoverPaletteChoice.NIGHT_SHIFT -> ResolvedPalette(C.NIGHT_SHIFT, C.HARD_HAT, C.SKY)
        CoverPaletteChoice.SKY -> ResolvedPalette(C.SKY, C.BLUEPRINT, C.WHITE)
        CoverPaletteChoice.GREEN -> ResolvedPalette(C.GREEN, C.NIGHT_SHIFT, C.PAPER)
        CoverPaletteChoice.BUTTER -> ResolvedPalette(C.BUTTER, C.BRICK, C.NIGHT_SHIFT)
        CoverPaletteChoice.PAPER -> ResolvedPalette(C.PAPER, C.BLUEPRINT, C.SKY)
        CoverPaletteChoice.PASTEL -> ResolvedPalette(C.ACCENT_SOFT, C.SKY, C.NIGHT_SHIFT)
        CoverPaletteChoice.COVER -> {
            val bg = C.artColorFor(key)
            ResolvedPalette(bg, C.BUTTER, C.PAPER)
        }
    }
}

/** WCAG contrast maths on ARGB ints. */
object CoverContrast {
    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Night Shift or Paper, whichever reads better on [background]. */
    fun textColorOn(background: Int): Int =
        if (ratio(NbCoverColors.NIGHT_SHIFT, background) >= ratio(NbCoverColors.PAPER, background)) {
            NbCoverColors.NIGHT_SHIFT
        } else {
            NbCoverColors.PAPER
        }
}
