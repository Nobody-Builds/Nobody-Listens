package com.nobodybuilds.listens.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemePair
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle

fun generateColorSchemeFromSeed(
    seedColor: Color,
    paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
): ColorSchemePair {
    return runCatching {
        val seedArgb = seedColor.toArgb()
        val sourceHct = Hct.fromInt(seedArgb)
        val shouldForceNeutral = shouldUseNeutralArtworkScheme(seedArgb, sourceHct)

        val lightScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = paletteStyle,
            isDark = false
        ).toComposeColorScheme()
        val darkScheme = createDynamicScheme(
            sourceHct = sourceHct,
            paletteStyle = paletteStyle,
            isDark = true
        ).toComposeColorScheme()
        if (shouldForceNeutral) {
            ColorSchemePair(
                light = lightScheme.toGrayscaleColorScheme(),
                dark = darkScheme.toGrayscaleColorScheme()
            )
        } else {
            ColorSchemePair(lightScheme, darkScheme)
        }
    }.getOrElse {
        ColorSchemePair(LightColorScheme, DarkColorScheme)
    }
}

fun generateMonochromeColorSchemeFromSeed(seedColor: Color): ColorSchemePair {
    return runCatching {
        val sourceHct = Hct.fromInt(seedColor.toArgb())
        ColorSchemePair(
            light = SchemeMonochrome(sourceHct, false, 0.0).toComposeColorScheme(),
            dark = SchemeMonochrome(sourceHct, true, 0.0).toComposeColorScheme()
        )
    }.getOrElse {
        ColorSchemePair(LightColorScheme, DarkColorScheme)
    }
}

private fun createDynamicScheme(
    sourceHct: Hct,
    paletteStyle: AlbumArtPaletteStyle,
    isDark: Boolean
): DynamicScheme {
    return when (paletteStyle) {
        AlbumArtPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.VIBRANT -> SchemeVibrant(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.EXPRESSIVE -> SchemeExpressive(sourceHct, isDark, 0.0)
        AlbumArtPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(sourceHct, isDark, 0.0)
    }
}

private fun ColorScheme.toGrayscaleColorScheme(): ColorScheme {
    fun convert(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[0] = 0f
        hsl[1] = 0f
        return Color(ColorUtils.HSLToColor(hsl))
    }

    return copy(
        primary = convert(primary),
        onPrimary = convert(onPrimary),
        primaryContainer = convert(primaryContainer),
        onPrimaryContainer = convert(onPrimaryContainer),
        inversePrimary = convert(inversePrimary),
        secondary = convert(secondary),
        onSecondary = convert(onSecondary),
        secondaryContainer = convert(secondaryContainer),
        onSecondaryContainer = convert(onSecondaryContainer),
        tertiary = convert(tertiary),
        onTertiary = convert(onTertiary),
        tertiaryContainer = convert(tertiaryContainer),
        onTertiaryContainer = convert(onTertiaryContainer),
        background = convert(background),
        onBackground = convert(onBackground),
        surface = convert(surface),
        onSurface = convert(onSurface),
        surfaceVariant = convert(surfaceVariant),
        onSurfaceVariant = convert(onSurfaceVariant),
        surfaceTint = convert(surfaceTint),
        inverseSurface = convert(inverseSurface),
        inverseOnSurface = convert(inverseOnSurface),
        error = convert(error),
        onError = convert(onError),
        errorContainer = convert(errorContainer),
        onErrorContainer = convert(onErrorContainer),
        outline = convert(outline),
        outlineVariant = convert(outlineVariant),
        scrim = convert(scrim),
        surfaceBright = convert(surfaceBright),
        surfaceDim = convert(surfaceDim),
        surfaceContainer = convert(surfaceContainer),
        surfaceContainerHigh = convert(surfaceContainerHigh),
        surfaceContainerHighest = convert(surfaceContainerHighest),
        surfaceContainerLow = convert(surfaceContainerLow),
        surfaceContainerLowest = convert(surfaceContainerLowest),
        primaryFixed = convert(primaryFixed),
        primaryFixedDim = convert(primaryFixedDim),
        onPrimaryFixed = convert(onPrimaryFixed),
        onPrimaryFixedVariant = convert(onPrimaryFixedVariant),
        secondaryFixed = convert(secondaryFixed),
        secondaryFixedDim = convert(secondaryFixedDim),
        onSecondaryFixed = convert(onSecondaryFixed),
        onSecondaryFixedVariant = convert(onSecondaryFixedVariant),
        tertiaryFixed = convert(tertiaryFixed),
        tertiaryFixedDim = convert(tertiaryFixedDim),
        onTertiaryFixed = convert(onTertiaryFixed),
        onTertiaryFixedVariant = convert(onTertiaryFixedVariant)
    )
}

private fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(getPrimary()),
        onPrimary = Color(getOnPrimary()),
        primaryContainer = Color(getPrimaryContainer()),
        onPrimaryContainer = Color(getOnPrimaryContainer()),
        inversePrimary = Color(getInversePrimary()),
        secondary = Color(getSecondary()),
        onSecondary = Color(getOnSecondary()),
        secondaryContainer = Color(getSecondaryContainer()),
        onSecondaryContainer = Color(getOnSecondaryContainer()),
        tertiary = Color(getTertiary()),
        onTertiary = Color(getOnTertiary()),
        tertiaryContainer = Color(getTertiaryContainer()),
        onTertiaryContainer = Color(getOnTertiaryContainer()),
        background = Color(getBackground()),
        onBackground = Color(getOnBackground()),
        surface = Color(getSurface()),
        onSurface = Color(getOnSurface()),
        surfaceVariant = Color(getSurfaceVariant()),
        onSurfaceVariant = Color(getOnSurfaceVariant()),
        surfaceTint = Color(getSurfaceTint()),
        inverseSurface = Color(getInverseSurface()),
        inverseOnSurface = Color(getInverseOnSurface()),
        error = Color(getError()),
        onError = Color(getOnError()),
        errorContainer = Color(getErrorContainer()),
        onErrorContainer = Color(getOnErrorContainer()),
        outline = Color(getOutline()),
        outlineVariant = Color(getOutlineVariant()),
        scrim = Color(getScrim()),
        surfaceBright = Color(getSurfaceBright()),
        surfaceDim = Color(getSurfaceDim()),
        surfaceContainer = Color(getSurfaceContainer()),
        surfaceContainerHigh = Color(getSurfaceContainerHigh()),
        surfaceContainerHighest = Color(getSurfaceContainerHighest()),
        surfaceContainerLow = Color(getSurfaceContainerLow()),
        surfaceContainerLowest = Color(getSurfaceContainerLowest()),
        primaryFixed = Color(getPrimaryFixed()),
        primaryFixedDim = Color(getPrimaryFixedDim()),
        onPrimaryFixed = Color(getOnPrimaryFixed()),
        onPrimaryFixedVariant = Color(getOnPrimaryFixedVariant()),
        secondaryFixed = Color(getSecondaryFixed()),
        secondaryFixedDim = Color(getSecondaryFixedDim()),
        onSecondaryFixed = Color(getOnSecondaryFixed()),
        onSecondaryFixedVariant = Color(getOnSecondaryFixedVariant()),
        tertiaryFixed = Color(getTertiaryFixed()),
        tertiaryFixedDim = Color(getTertiaryFixedDim()),
        onTertiaryFixed = Color(getOnTertiaryFixed()),
        onTertiaryFixedVariant = Color(getOnTertiaryFixedVariant())
    )
}
