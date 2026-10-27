package com.nobodybuilds.listens.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Nobody Builds "Daylight" design system (Nobody Listens default). Values copied exactly
// from NobodyBuilds-Design/tokens/tokens.json; DaylightSchemeTest checks them.
// ---------------------------------------------------------------------------
object Daylight {
    // Brand
    val NightShift = Color(0xFF172033)
    val Blueprint = Color(0xFF2457A6)
    val HardHat = Color(0xFFFDC521)
    val Sky = Color(0xFFCFE0F7)
    val Butter = Color(0xFFFFE8A3)
    val Brick = Color(0xFFEA724A)
    val Paper = Color(0xFFF5F7FB)

    /** One theme's tokens, named as in tokens.json. */
    class Tokens(
        val bg: Color,
        val surface: Color,
        val panel: Color,
        val hover: Color,
        val line: Color,
        val outline: Color,
        val ink: Color,
        val muted: Color,
        val focus: Color,
        val accent: Color,
        val accentHover: Color,
        val accentActive: Color,
        val onAccent: Color,
        val accentSoft: Color,
        val accentInk: Color,
        val emphasis: Color,
        val emphasisHover: Color,
        val emphasisActive: Color,
        val onEmphasis: Color,
        val emphasisSoft: Color,
        val success: Color,
        val onSuccess: Color,
        val successSoft: Color,
        val successInk: Color,
        val warn: Color,
        val onWarn: Color,
        val warnSoft: Color,
        val warnInk: Color,
        val info: Color,
        val onInfo: Color,
        val infoSoft: Color,
        val infoInk: Color,
        val danger: Color,
        val onDanger: Color,
        val dangerLine: Color,
        val sidebar: Color,
    )

    val Light = Tokens(
        bg = Color(0xFFF5F7FB),
        surface = Color(0xFFECF0F7),
        panel = Color(0xFFFFFFFF),
        hover = Color(0xFFE4E9F2),
        line = Color(0xFFD3DAE6),
        outline = Color(0xFF7D8699),
        ink = Color(0xFF172033),
        muted = Color(0xFF4F5A70),
        focus = Color(0xFF2457A6),
        accent = Color(0xFF2457A6),
        accentHover = Color(0xFF1F4C92),
        accentActive = Color(0xFF1A417E),
        onAccent = Color(0xFFF5F7FB),
        accentSoft = Color(0xFFE3ECF8),
        accentInk = Color(0xFF2457A6),
        emphasis = Color(0xFFFDC521),
        emphasisHover = Color(0xFFFDCD45),
        emphasisActive = Color(0xFFFED66A),
        onEmphasis = Color(0xFF172033),
        emphasisSoft = Color(0xFFFFF6D6),
        success = Color(0xFFA6D98F),
        onSuccess = Color(0xFF172033),
        successSoft = Color(0xFFF1F9EC),
        successInk = Color(0xFF3B6B2B),
        warn = Color(0xFFEA724A),
        onWarn = Color(0xFF172033),
        warnSoft = Color(0xFFFCEBE4),
        warnInk = Color(0xFFA3412A),
        info = Color(0xFFCFE0F7),
        onInfo = Color(0xFF172033),
        infoSoft = Color(0xFFEEF4FC),
        infoInk = Color(0xFF2457A6),
        danger = Color(0xFFB91C1C),
        onDanger = Color(0xFFF5F7FB),
        dangerLine = Color(0xFFFCA5A5),
        sidebar = Color(0xFFCFE0F7),
    )

    val Dark = Tokens(
        bg = Color(0xFF172033),
        surface = Color(0xFF1D2840),
        panel = Color(0xFF22304B),
        hover = Color(0xFF2A3957),
        line = Color(0xFF3E4D6B),
        outline = Color(0xFF8592AD),
        ink = Color(0xFFF5F7FB),
        muted = Color(0xFFC3C8D2),
        focus = Color(0xFFFDC521),
        accent = Color(0xFF7FA8E8),
        accentHover = Color(0xFF92B5EC),
        accentActive = Color(0xFFA5C2EF),
        onAccent = Color(0xFF172033),
        accentSoft = Color(0xFF263A5C),
        accentInk = Color(0xFFA9C8F0),
        emphasis = Color(0xFFFDC521),
        emphasisHover = Color(0xFFFDCD45),
        emphasisActive = Color(0xFFFED66A),
        onEmphasis = Color(0xFF172033),
        emphasisSoft = Color(0xFF3A3424),
        success = Color(0xFFA6D98F),
        onSuccess = Color(0xFF172033),
        successSoft = Color(0xFF26382F),
        successInk = Color(0xFFA6D98F),
        warn = Color(0xFFEA724A),
        onWarn = Color(0xFF172033),
        warnSoft = Color(0xFF3B2C2B),
        warnInk = Color(0xFFF2A88E),
        info = Color(0xFFCFE0F7),
        onInfo = Color(0xFF172033),
        infoSoft = Color(0xFF24344F),
        infoInk = Color(0xFFCFE0F7),
        danger = Color(0xFFFCA5A5),
        onDanger = Color(0xFF172033),
        dangerLine = Color(0xFF7C6261),
        sidebar = Color(0xFF121A2B),
    )

    fun tokens(isDark: Boolean): Tokens = if (isDark) Dark else Light
}
