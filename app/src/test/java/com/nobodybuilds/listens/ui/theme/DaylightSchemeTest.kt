package com.nobodybuilds.listens.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Daylight tokens must match NobodyBuilds-Design/tokens/tokens.json (copied to
 * test resources as daylight/tokens.json), and every role pair the app draws text or
 * meaningful graphics with must keep WCAG contrast: 4.5:1 for text, 3:1 for graphics.
 */
class DaylightSchemeTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    /** Reads one theme block ("light" or "dark") from the fixture without a JSON library. */
    private fun fixture(theme: String): Map<String, String> {
        val json = javaClass.classLoader!!.getResource("daylight/tokens.json")!!.readText()
        val block = Regex("\"$theme\"\\s*:\\s*\\{([^}]*)\\}").find(json)!!.groupValues[1]
        return Regex("\"([a-z-]+)\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"").findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun tokensByName(t: Daylight.Tokens): Map<String, Color> = mapOf(
        "bg" to t.bg, "surface" to t.surface, "panel" to t.panel, "hover" to t.hover, "line" to t.line,
        "outline" to t.outline, "ink" to t.ink, "muted" to t.muted, "focus" to t.focus,
        "accent" to t.accent, "accent-hover" to t.accentHover, "accent-active" to t.accentActive,
        "on-accent" to t.onAccent, "accent-soft" to t.accentSoft, "accent-ink" to t.accentInk,
        "emphasis" to t.emphasis, "emphasis-hover" to t.emphasisHover, "emphasis-active" to t.emphasisActive,
        "on-emphasis" to t.onEmphasis, "emphasis-soft" to t.emphasisSoft,
        "success" to t.success, "on-success" to t.onSuccess, "success-soft" to t.successSoft, "success-ink" to t.successInk,
        "warn" to t.warn, "on-warn" to t.onWarn, "warn-soft" to t.warnSoft, "warn-ink" to t.warnInk,
        "info" to t.info, "on-info" to t.onInfo, "info-soft" to t.infoSoft, "info-ink" to t.infoInk,
        "danger" to t.danger, "on-danger" to t.onDanger, "danger-line" to t.dangerLine, "sidebar" to t.sidebar,
    )

    @ParameterizedTest
    @ValueSource(strings = ["light", "dark"])
    fun `tokens match the design kit`(theme: String) {
        val expected = fixture(theme)
        val actual = tokensByName(Daylight.tokens(theme == "dark"))
        assertTrue(expected.size >= 50, "fixture parse found only ${expected.size} tokens")
        actual.forEach { (name, color) ->
            assertEquals(expected[name], hex(color), "token $name ($theme)")
        }
    }

    private fun textPairs(s: ColorScheme) = listOf(
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onTertiaryFixed/tertiaryFixed" to (s.onTertiaryFixed to s.tertiaryFixed),
        "onSurface/surface" to (s.onSurface to s.surface),
        "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant/surfaceContainerLowest" to (s.onSurfaceVariant to s.surfaceContainerLowest),
        "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "primary text/surface" to (s.primary to s.surface),
        "tertiary text/surface" to (s.tertiary to s.surface),
        "secondary text/surface" to (s.secondary to s.surface),
        "onError/error" to (s.onError to s.error),
        "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
        "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
    )

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `text roles keep 4,5 to 1`(dark: Boolean) {
        val scheme = if (dark) DaylightDarkColorScheme else DaylightLightColorScheme
        textPairs(scheme).forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= 4.5, "$name is %.2f:1 (dark=$dark)".format(ratio))
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `outline and primary graphics keep 3 to 1 on the canvas`(dark: Boolean) {
        val s = if (dark) DaylightDarkColorScheme else DaylightLightColorScheme
        assertTrue(contrast(s.outline, s.surface) >= 3.0, "outline (dark=$dark)")
        assertTrue(contrast(s.primary, s.surfaceContainerHigh) >= 3.0, "primary on surfaceContainerHigh (dark=$dark)")
    }

    @Test
    fun `pitch black keeps Daylight dark colours on a black canvas`() {
        val s = DaylightPitchBlackColorScheme
        val d = DaylightDarkColorScheme
        assertEquals(Color.Black, s.background)
        assertEquals(Color.Black, s.surface)
        assertEquals(Color.Transparent, s.surfaceTint)
        listOf(
            "primary" to (s.primary to d.primary), "tertiary" to (s.tertiary to d.tertiary),
            "tertiaryFixed" to (s.tertiaryFixed to d.tertiaryFixed), "onSurface" to (s.onSurface to d.onSurface),
            "onSurfaceVariant" to (s.onSurfaceVariant to d.onSurfaceVariant), "outline" to (s.outline to d.outline),
        ).forEach { (name, pair) -> assertEquals(pair.second, pair.first, name) }
        textPairs(s).forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue(ratio >= 4.5, "$name is %.2f:1 (pitch black)".format(ratio))
        }
        assertTrue(contrast(s.outline, s.surface) >= 3.0, "outline (pitch black)")
        assertTrue(contrast(s.primary, s.surfaceContainerHigh) >= 3.0, "primary on surfaceContainerHigh (pitch black)")
    }

    @Test
    fun `hard hat is a fill, never the text accent`() {
        assertEquals(Daylight.HardHat, DaylightLightColorScheme.tertiaryFixed)
        assertNotEquals(Daylight.HardHat, DaylightLightColorScheme.tertiary)
        assertTrue(contrast(Daylight.HardHat, Daylight.Paper) < 3.0, "if this fails, the Hard Hat rules can be relaxed")
    }

    @Test
    fun `no tonal tint so elevation never recolours surfaces`() {
        assertEquals(Color.Transparent, DaylightLightColorScheme.surfaceTint)
        assertEquals(Color.Transparent, DaylightDarkColorScheme.surfaceTint)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `album accents keep their labels readable`(dark: Boolean) {
        val seeds = listOf(
            NbDefaultAccent, Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFDC521),
            Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFF808080),
        )
        seeds.forEach { seed ->
            val accent = nbAccentFrom(seed, dark)
            val ratio = contrast(accent.onFill, accent.fill)
            assertTrue(ratio >= 3.0, "seed ${hex(seed)} onFill/fill %.2f:1 (dark=$dark)".format(ratio))
        }
    }
}
