package com.twilighty.lmusic.ui.glancewidget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.glance.material3.ColorProviders
import com.twilighty.lmusic.ui.theme.Daylight

/**
 * Daylight colours for the home-screen widgets. Flat canvas (Paper / Night Shift), ink text,
 * muted secondary text, and Hard Hat for the play button — the widget's one bold moment.
 * Roles are mapped onto Material names so every widget layout picks them up through
 * [androidx.glance.GlanceTheme].
 */
object NbWidgetPalette {
    val Accent = Daylight.HardHat
    val OnAccent = Daylight.NightShift

    val LightCanvas = Daylight.Light.bg
    val LightMuted = Daylight.Light.surface
    val LightText = Daylight.Light.ink
    val LightTextMuted = Daylight.Light.muted
    val LightBorder = Daylight.Light.line

    val DarkCanvas = Daylight.Dark.bg
    val DarkMuted = Daylight.Dark.panel
    val DarkText = Daylight.Dark.ink
    val DarkTextMuted = Daylight.Dark.muted
    val DarkBorder = Daylight.Dark.line
}

val NbGlanceColors = ColorProviders(
    light = lightColorScheme(
        primary = NbWidgetPalette.Accent,
        onPrimary = NbWidgetPalette.OnAccent,
        primaryContainer = NbWidgetPalette.Accent,
        onPrimaryContainer = NbWidgetPalette.OnAccent,
        secondary = NbWidgetPalette.LightText,
        onSecondary = NbWidgetPalette.LightCanvas,
        secondaryContainer = NbWidgetPalette.LightMuted,
        onSecondaryContainer = NbWidgetPalette.LightText,
        tertiary = Daylight.Light.accentInk,
        onTertiary = Daylight.Light.onAccent,
        tertiaryContainer = Daylight.Light.accentSoft,
        onTertiaryContainer = Daylight.Light.accentInk,
        background = NbWidgetPalette.LightCanvas,
        onBackground = NbWidgetPalette.LightText,
        surface = NbWidgetPalette.LightCanvas,
        onSurface = NbWidgetPalette.LightText,
        surfaceVariant = NbWidgetPalette.LightMuted,
        onSurfaceVariant = NbWidgetPalette.LightTextMuted,
        outline = NbWidgetPalette.LightBorder,
    ),
    dark = darkColorScheme(
        primary = NbWidgetPalette.Accent,
        onPrimary = NbWidgetPalette.OnAccent,
        primaryContainer = NbWidgetPalette.Accent,
        onPrimaryContainer = NbWidgetPalette.OnAccent,
        secondary = NbWidgetPalette.DarkText,
        onSecondary = NbWidgetPalette.DarkCanvas,
        secondaryContainer = NbWidgetPalette.DarkMuted,
        onSecondaryContainer = NbWidgetPalette.DarkText,
        tertiary = Daylight.Dark.accentInk,
        onTertiary = Daylight.Dark.onAccent,
        tertiaryContainer = Daylight.Dark.accentSoft,
        onTertiaryContainer = Daylight.Dark.accentInk,
        background = NbWidgetPalette.DarkCanvas,
        onBackground = NbWidgetPalette.DarkText,
        surface = NbWidgetPalette.DarkCanvas,
        onSurface = NbWidgetPalette.DarkText,
        surfaceVariant = NbWidgetPalette.DarkMuted,
        onSurfaceVariant = NbWidgetPalette.DarkTextMuted,
        outline = NbWidgetPalette.DarkBorder,
    ),
)
