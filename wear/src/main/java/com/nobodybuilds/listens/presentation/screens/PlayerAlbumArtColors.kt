package com.nobodybuilds.listens.presentation.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.lerp
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

internal data class AlbumOverlayTextColors(
    val clock: Color,
    val title: Color,
    val artist: Color,
    val clockShadow: Shadow,
    val bottomShadow: Shadow,
)

internal data class AlbumContrastOverlay(
    val topColor: Color,
    val topAlpha: Float,
    val topFadeEnd: Float,
    val bottomColor: Color,
    val bottomAlpha: Float,
    val bottomFadeStart: Float,
)

internal fun deriveAlbumOverlayTextColors(
    albumArt: Bitmap?,
    fallbackPrimary: Color,
    fallbackSecondary: Color,
): AlbumOverlayTextColors {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) {
        val defaultShadow = Shadow(
            color = Color.Black.copy(alpha = 0.56f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
        return AlbumOverlayTextColors(
            clock = fallbackPrimary,
            title = fallbackPrimary,
            artist = fallbackSecondary.copy(alpha = 0.92f),
            clockShadow = defaultShadow,
            bottomShadow = defaultShadow,
        )
    }

    val topBg = sampleRegionAverageColor(albumArt, startYFraction = 0f, endYFraction = 0.24f)
    val bottomBg = sampleRegionAverageColor(albumArt, startYFraction = 0.66f, endYFraction = 1f)

    val preferredClock = deriveClockTintFromAlbumArt(albumArt, fallbackPrimary)
    val clockColor = deriveReadableTintedColor(
        preferred = preferredClock,
        background = topBg,
        minContrast = 4.8,
        tintStrength = 0.34f,
    )
    val titleColor = deriveReadableTintedColor(
        preferred = fallbackPrimary.copy(alpha = 0.99f),
        background = bottomBg,
        minContrast = 5.2,
        tintStrength = 0.18f,
    )
    val artistColor = deriveReadableTintedColor(
        preferred = fallbackSecondary.copy(alpha = 0.96f),
        background = bottomBg,
        minContrast = 4.4,
        tintStrength = 0.22f,
    ).copy(alpha = 0.97f)

    val clockShadow = if (clockColor.luminance() < 0.5f) {
        Shadow(
            color = Color.White.copy(alpha = 0.36f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f,
        )
    } else {
        Shadow(
            color = Color.Black.copy(alpha = 0.58f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
    }
    val bottomShadow = if (titleColor.luminance() < 0.5f) {
        Shadow(
            color = Color.White.copy(alpha = 0.30f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f,
        )
    } else {
        Shadow(
            color = Color.Black.copy(alpha = 0.55f),
            offset = Offset(0f, 1.6f),
            blurRadius = 5f,
        )
    }

    return AlbumOverlayTextColors(
        clock = clockColor,
        title = titleColor,
        artist = artistColor,
        clockShadow = clockShadow,
        bottomShadow = bottomShadow,
    )
}

internal fun deriveAlbumContrastOverlay(
    albumArt: Bitmap?,
    textColors: AlbumOverlayTextColors,
): AlbumContrastOverlay {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) {
        return AlbumContrastOverlay(
            topColor = Color.Black,
            topAlpha = 0.30f,
            topFadeEnd = 0.30f,
            bottomColor = Color.Black,
            bottomAlpha = 0.36f,
            bottomFadeStart = 0.64f,
        )
    }

    val topBg = sampleRegionAverageColor(albumArt, startYFraction = 0f, endYFraction = 0.24f)
    val bottomBg = sampleRegionAverageColor(albumArt, startYFraction = 0.62f, endYFraction = 1f)

    val topBase = if (textColors.clock.luminance() > 0.5f) Color.Black else Color.White
    val topAlpha = solveScrimAlphaForContrast(
        textColor = textColors.clock,
        backgroundColor = topBg,
        scrimBaseColor = topBase,
        minContrast = 6.0,
        extraHeadroom = 0.10f,
    )

    val bottomBase = if (textColors.title.luminance() > 0.5f) Color.Black else Color.White
    val bottomTitleAlpha = solveScrimAlphaForContrast(
        textColor = textColors.title,
        backgroundColor = bottomBg,
        scrimBaseColor = bottomBase,
        minContrast = 6.4,
        extraHeadroom = 0.10f,
    )
    val bottomArtistAlpha = solveScrimAlphaForContrast(
        textColor = textColors.artist,
        backgroundColor = bottomBg,
        scrimBaseColor = bottomBase,
        minContrast = 5.1,
        extraHeadroom = 0.08f,
    )

    return AlbumContrastOverlay(
        topColor = topBase,
        topAlpha = topAlpha,
        topFadeEnd = 0.33f,
        bottomColor = bottomBase,
        bottomAlpha = max(bottomTitleAlpha, bottomArtistAlpha),
        bottomFadeStart = 0.56f,
    )
}

private fun solveScrimAlphaForContrast(
    textColor: Color,
    backgroundColor: Color,
    scrimBaseColor: Color,
    minContrast: Double,
    extraHeadroom: Float,
): Float {
    val opaqueBg = toOpaqueArgb(backgroundColor)
    val textArgb = textColor.toArgb()
    val currentContrast = ColorUtils.calculateContrast(textArgb, opaqueBg)
    if (currentContrast >= minContrast) {
        return 0f
    }

    var alpha = 0f
    while (alpha <= 0.84f) {
        val scrimArgb = ColorUtils.setAlphaComponent(
            scrimBaseColor.toArgb(),
            (alpha * 255f).toInt().coerceIn(0, 255),
        )
        val compositedBg = ColorUtils.compositeColors(scrimArgb, opaqueBg)
        val contrast = ColorUtils.calculateContrast(textArgb, compositedBg)
        if (contrast >= minContrast) {
            return (alpha + extraHeadroom).coerceIn(0f, 0.84f)
        }
        alpha += 0.03f
    }

    return 0.84f
}

private fun toOpaqueArgb(color: Color): Int {
    val argb = color.toArgb()
    return if (android.graphics.Color.alpha(argb) >= 255) {
        argb
    } else {
        ColorUtils.compositeColors(argb, Color.Black.toArgb())
    }
}

private fun deriveReadableTintedColor(
    preferred: Color,
    background: Color,
    minContrast: Double,
    tintStrength: Float,
): Color {
    val lightBase = Color(0xFFF7F7F7)
    val darkBase = Color(0xFF111111)

    val lightContrast = ColorUtils.calculateContrast(lightBase.toArgb(), background.toArgb())
    val darkContrast = ColorUtils.calculateContrast(darkBase.toArgb(), background.toArgb())
    val highContrastBase = if (lightContrast >= darkContrast) lightBase else darkBase

    val preferredContrast = ColorUtils.calculateContrast(preferred.toArgb(), background.toArgb())
    if (preferredContrast >= minContrast) return preferred

    val clampedTintStrength = tintStrength.coerceIn(0f, 0.5f)
    val tintSteps = floatArrayOf(
        clampedTintStrength,
        clampedTintStrength * 0.72f,
        clampedTintStrength * 0.46f,
        clampedTintStrength * 0.24f,
        0f,
    )

    tintSteps.forEach { blend ->
        val candidate = androidx.compose.ui.graphics.lerp(highContrastBase, preferred, blend)
        val contrast = ColorUtils.calculateContrast(candidate.toArgb(), background.toArgb())
        if (contrast >= minContrast) {
            return candidate
        }
    }

    return highContrastBase
}

private fun sampleRegionAverageColor(
    albumArt: Bitmap,
    startYFraction: Float,
    endYFraction: Float,
): Color {
    val width = albumArt.width
    val height = albumArt.height
    val yStart = (height * startYFraction).toInt().coerceIn(0, height - 1)
    val yEnd = (height * endYFraction).toInt().coerceIn(yStart + 1, height)
    val step = (min(width, (yEnd - yStart).coerceAtLeast(1)) / 24).coerceAtLeast(1)

    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = yStart
    while (y < yEnd) {
        var x = 0
        while (x < width) {
            val pixel = albumArt.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha >= 28) {
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r + g + b > 30) {
                    redSum += r
                    greenSum += g
                    blueSum += b
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return Color.Black
    return Color(
        android.graphics.Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    )
}

private fun deriveClockTintFromAlbumArt(albumArt: Bitmap?, fallback: Color): Color {
    if (albumArt == null || albumArt.width <= 0 || albumArt.height <= 0) return fallback

    val width = albumArt.width
    val height = albumArt.height
    val step = (min(width, height) / 28).coerceAtLeast(1)

    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = albumArt.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha >= 28) {
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r + g + b > 30) {
                    redSum += r
                    greenSum += g
                    blueSum += b
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return fallback

    val avgColor = android.graphics.Color.rgb(
        (redSum / count).toInt(),
        (greenSum / count).toInt(),
        (blueSum / count).toInt(),
    )
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(avgColor, hsl)
    hsl[1] = max(0.42f, hsl[1]).coerceAtMost(0.92f)
    hsl[2] = max(0.68f, hsl[2]).coerceAtMost(0.90f)

    var tinted = Color(ColorUtils.HSLToColor(hsl))
    val lum = tinted.luminance()
    tinted = when {
        lum < 0.60f -> androidx.compose.ui.graphics.lerp(tinted, Color.White, 0.32f)
        lum > 0.92f -> androidx.compose.ui.graphics.lerp(tinted, Color.White, 0.08f)
        else -> tinted
    }
    return tinted.copy(alpha = 0.98f)
}
