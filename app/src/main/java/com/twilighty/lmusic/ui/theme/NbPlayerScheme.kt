package com.twilighty.lmusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** True while the app uses the Daylight palette (Settings › Appearance › App colours). */
val LocalUseBrandColors = staticCompositionLocalOf { true }

/** The accent the player uses when a song has no artwork to take a colour from: Blueprint. */
val NbDefaultAccent = Daylight.Blueprint

/**
 * The colours of one "album accent" (driven by the current song's artwork):
 *  - [fill]: solid fills — play button, seek bar, progress, chips' active state
 *  - [onFill]: text and icons on [fill]
 *  - [text]: the accent used as text on the canvas (readable in light and dark)
 *  - [soft]: the accent mixed into the canvas — the player background and soft chips
 */
data class NbAccent(
    val fill: Color,
    val onFill: Color,
    val text: Color,
    val soft: Color,
    val softStrong: Color,
)

fun nbAccentFrom(seed: Color, isDark: Boolean): NbAccent {
    val t = Daylight.tokens(isDark)
    val canvas = t.bg
    // Light: deep fills that carry Paper icons. Dark: light fills (tone ~80) that carry
    // Night Shift icons, so the play button and seek bar stand out from the navy canvas.
    val fill = if (isDark) {
        when {
            seed.luminance() < 0.12f -> lerp(seed, Color.White, 0.40f)
            seed.luminance() > 0.65f -> lerp(seed, Color.Black, 0.15f)
            else -> seed
        }
    } else {
        if (seed.luminance() > 0.32f) lerp(seed, Daylight.NightShift, 0.45f) else seed
    }
    // Whichever of Night Shift and Paper reads better on the fill (mid tones sit near 0.3 luminance).
    val l = fill.luminance()
    val onFill = if ((l + 0.05f) / (Daylight.NightShift.luminance() + 0.05f) >= (Daylight.Paper.luminance() + 0.05f) / (l + 0.05f)) {
        Daylight.NightShift
    } else {
        Daylight.Paper
    }
    val text = if (isDark) lerp(fill, Color.White, if (fill.luminance() > 0.3f) 0.1f else 0.42f) else fill
    val soft = lerp(canvas, fill, if (isDark) 0.12f else 0.09f)
    val softStrong = lerp(canvas, fill, if (isDark) 0.22f else 0.18f)
    return NbAccent(fill = fill, onFill = onFill, text = text, soft = soft, softStrong = softStrong)
}

/**
 * Turns the Material scheme generated from the album art into a Daylight scheme: Daylight
 * neutrals and ink text everywhere, with the artwork colour used only as the accent.
 * [albumScheme] equal to [base] means "no artwork" → the Blueprint default accent.
 */
fun nbPlayerScheme(albumScheme: ColorScheme, base: ColorScheme, isDark: Boolean): ColorScheme {
    val seed = if (albumScheme == base) NbDefaultAccent else albumScheme.primary
    val accent = nbAccentFrom(seed, isDark)
    val ink = base.onSurface
    return base.copy(
        primary = accent.fill,
        onPrimary = accent.onFill,
        primaryContainer = accent.soft,
        onPrimaryContainer = ink,
        inversePrimary = accent.text,
        secondary = base.onSurfaceVariant,
        onSecondary = base.surface,
        secondaryContainer = accent.softStrong,
        onSecondaryContainer = ink,
        tertiary = accent.text,
        onTertiary = accent.onFill,
        tertiaryContainer = accent.softStrong,
        onTertiaryContainer = ink,
        surfaceTint = Color.Transparent,
        // Fixed roles drive the full player's transport buttons and toggles.
        primaryFixed = accent.fill,
        primaryFixedDim = accent.fill,
        onPrimaryFixed = accent.onFill,
        onPrimaryFixedVariant = accent.onFill,
        secondaryFixed = accent.softStrong,
        secondaryFixedDim = Color.Transparent,
        onSecondaryFixed = ink,
        onSecondaryFixedVariant = base.onSurfaceVariant,
        tertiaryFixed = accent.fill,
        tertiaryFixedDim = accent.fill,
        onTertiaryFixed = accent.onFill,
        onTertiaryFixedVariant = accent.onFill,
    )
}

/**
 * Fixed roles for the plain Daylight schemes (Material's defaults would be purple).
 * tertiaryFixed is Hard Hat: the one bold moment per screen (NbButtonStyle.Accent).
 */
fun ColorScheme.withDaylightFixedRoles(isDark: Boolean): ColorScheme {
    val t = Daylight.tokens(isDark)
    return copy(
        primaryFixed = t.accentSoft,
        primaryFixedDim = t.line,
        onPrimaryFixed = t.ink,
        onPrimaryFixedVariant = t.muted,
        secondaryFixed = t.surface,
        secondaryFixedDim = t.hover,
        onSecondaryFixed = t.ink,
        onSecondaryFixedVariant = t.muted,
        tertiaryFixed = t.emphasis,
        tertiaryFixedDim = t.emphasisActive,
        onTertiaryFixed = t.onEmphasis,
        onTertiaryFixedVariant = t.onEmphasis,
    )
}
