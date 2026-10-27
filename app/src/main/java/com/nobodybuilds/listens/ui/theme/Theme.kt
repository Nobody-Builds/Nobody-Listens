package com.nobodybuilds.listens.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemePair

val LocalNobodyListensDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NobodyListensStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val colorArgb = color.toArgb()
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = colorArgb
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = useDarkIcons
    }
}

/**
 * Daylight light scheme: Paper canvas, white panels, Blueprint does the work. Hard Hat
 * yellow is only a fill (tertiaryFixed) — never text on Paper — so `tertiary`, which the
 * app uses for accent text, is the text-safe accent ink.
 */
val DaylightLightColorScheme: ColorScheme = Daylight.Light.let { t ->
    lightColorScheme(
        primary = t.accent,
        onPrimary = t.onAccent,
        primaryContainer = t.accentSoft,
        onPrimaryContainer = t.ink,
        inversePrimary = Daylight.Dark.accent,
        secondary = t.muted,
        onSecondary = t.bg,
        secondaryContainer = t.info,
        onSecondaryContainer = t.onInfo,
        tertiary = t.accentInk,
        onTertiary = t.onAccent,
        tertiaryContainer = t.emphasisSoft,
        onTertiaryContainer = t.ink,
        background = t.bg,
        onBackground = t.ink,
        surface = t.bg,
        onSurface = t.ink,
        surfaceVariant = t.surface,
        onSurfaceVariant = t.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = t.ink,
        inverseOnSurface = t.bg,
        error = t.danger,
        onError = t.onDanger,
        errorContainer = Color(0xFFFDECEC),
        onErrorContainer = t.danger,
        outline = t.outline,
        outlineVariant = t.line,
        scrim = Daylight.NightShift,
        surfaceBright = t.panel,
        surfaceDim = t.hover,
        surfaceContainerLowest = t.panel,
        surfaceContainerLow = Color(0xFFF0F3F9),
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.hover,
        surfaceContainerHighest = Color(0xFFDCE2EC),
    ).withDaylightFixedRoles(false)
}

/** Daylight dark scheme: Night Shift navy (no black), surfaces stepped toward blue-grey. */
val DaylightDarkColorScheme: ColorScheme = Daylight.Dark.let { t ->
    darkColorScheme(
        primary = t.accent,
        onPrimary = t.onAccent,
        primaryContainer = t.accentSoft,
        onPrimaryContainer = t.accentInk,
        inversePrimary = Daylight.Light.accent,
        secondary = t.muted,
        onSecondary = t.bg,
        secondaryContainer = t.infoSoft,
        onSecondaryContainer = t.infoInk,
        tertiary = t.accentInk,
        onTertiary = t.onAccent,
        tertiaryContainer = t.emphasisSoft,
        onTertiaryContainer = t.ink,
        background = t.bg,
        onBackground = t.ink,
        surface = t.bg,
        onSurface = t.ink,
        surfaceVariant = t.surface,
        onSurfaceVariant = t.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = t.ink,
        inverseOnSurface = t.bg,
        error = t.danger,
        onError = t.onDanger,
        errorContainer = Color(0xFF3B2425),
        onErrorContainer = t.danger,
        outline = t.outline,
        outlineVariant = t.line,
        scrim = Daylight.NightShift,
        surfaceBright = t.hover,
        surfaceDim = t.sidebar,
        surfaceContainerLowest = t.sidebar,
        surfaceContainerLow = t.surface,
        surfaceContainer = t.panel,
        surfaceContainerHigh = t.hover,
        surfaceContainerHighest = Color(0xFF33425F),
    ).withDaylightFixedRoles(true)
}

/**
 * Opt-in pitch black for OLED screens: the canvas goes to #000000 and raised layers step up
 * through Night Shift, so cards and sheets still read as Daylight navy. Accents and text are
 * the dark scheme's own.
 */
val DaylightPitchBlackColorScheme: ColorScheme = DaylightDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceVariant = Color(0xFF111826),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B1019),
    surfaceContainer = Color(0xFF111826),
    surfaceContainerHigh = Daylight.NightShift,
    surfaceContainerHighest = Daylight.Dark.surface,
    surfaceBright = Daylight.Dark.panel,
)

/** Pitch black over any dark scheme (Material You): only the canvas layers turn black. */
fun ColorScheme.withPitchBlackCanvas(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)

// Older code (album-art fallbacks, lava lamp) still asks for these by name.
val LightColorScheme: ColorScheme get() = DaylightLightColorScheme
val DarkColorScheme: ColorScheme get() = DaylightDarkColorScheme

@Composable
fun NobodyListensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    useBrandColors: Boolean = true,
    pitchBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val daylightDark = if (pitchBlack) DaylightPitchBlackColorScheme else DaylightDarkColorScheme
    val finalColorScheme = when {
        // The album-art scheme (now-playing, detail screens) wins when one is passed.
        colorSchemePairOverride != null ->
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        // Nobody Listens default: Daylight app-wide.
        useBrandColors -> if (darkTheme) daylightDark else DaylightLightColorScheme
        // Material You: the wallpaper colours (Android 12+, always true at minSdk 33).
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> runCatching {
            if (darkTheme) {
                dynamicDarkColorScheme(context).let { if (pitchBlack) it.withPitchBlackCanvas() else it }
            } else {
                dynamicLightColorScheme(context)
            }
        }.getOrElse { if (darkTheme) daylightDark else DaylightLightColorScheme }
        darkTheme -> daylightDark
        else -> DaylightLightColorScheme
    }

    // Flat status bar in the canvas colour (Daylight has no shadows or tonal tints).
    NobodyListensStatusBarStyle(color = finalColorScheme.background)

    CompositionLocalProvider(
        LocalNobodyListensDarkTheme provides darkTheme,
        LocalUseBrandColors provides (useBrandColors && colorSchemePairOverride == null)
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
