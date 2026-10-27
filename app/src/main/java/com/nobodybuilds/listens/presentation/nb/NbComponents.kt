package com.nobodybuilds.listens.presentation.nb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.ui.theme.NbBody
import com.nobodybuilds.listens.ui.theme.Daylight
import com.nobodybuilds.listens.ui.theme.NbDisplay
import com.nobodybuilds.listens.ui.theme.UnboundedFamily

// =====================================================================================
// Nobody Listens on the Nobody Builds "Daylight" design system (NobodyBuilds-Design). Colours always
// come from MaterialTheme, so Material You and the album-art schemes work too. The Daylight
// scheme maps: surface = Paper canvas, surfaceContainerLowest = white panel (light),
// surfaceContainer = surface step, outlineVariant = line, outline = outline,
// primary = Blueprint, tertiary = accent ink, tertiaryFixed = Hard Hat (one bold moment).
// Album-tinted areas pass their own accent. No shadows: layers are surface steps and lines.
// =====================================================================================

object NbType {
    fun display(size: TextUnit = 36.sp) = TextStyle(
        fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = size,
        lineHeight = size * 1.04f, letterSpacing = (-0.025).em
    )
    val h1 = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.025).em)
    val h2 = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.025).em)
    val h3 = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
    val body = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyLarge = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp)
    val meta = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    val label = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val overline = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)
    val stat = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.025).em)
    /** Big light numbers (timers, totals): Gilroy Light. */
    fun numeral(size: TextUnit = 56.sp) = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.Light, fontSize = size, lineHeight = size * 1.05f, letterSpacing = (-0.02).em)
    /** Unbounded: one short accent phrase per layout. */
    val accent = TextStyle(fontFamily = UnboundedFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
}

// Daylight radii: controls 10, cards and windows 16, sheets 20, tags and pills fully round.
object NbShapes {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(16.dp)
    val xxl = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** Colours read from the active scheme, named by role. */
object Nb {
    val canvas: Color @Composable get() = MaterialTheme.colorScheme.surface
    /** Cards: white on Paper in light, a raised navy step in dark. */
    val panel: Color @Composable get() = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val subtle: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val muted: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val emphasis: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val borderStrong: Color @Composable get() = MaterialTheme.colorScheme.outline
    val text: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val textSecondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val textMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val ink: Color @Composable get() = MaterialTheme.colorScheme.primary
    val onInk: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val accent: Color @Composable get() = MaterialTheme.colorScheme.tertiaryFixed
    val onAccent: Color @Composable get() = MaterialTheme.colorScheme.onTertiaryFixed
    val accentText: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val accentSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val dangerSoft: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    // Status and tint roles Material 3 lacks, straight from the Daylight tokens.
    val successSoft: Color @Composable get() = tokens.successSoft
    val successText: Color @Composable get() = tokens.successInk
    val warningSoft: Color @Composable get() = tokens.warnSoft
    val warningText: Color @Composable get() = tokens.warnInk
    val infoSoft: Color @Composable get() = tokens.infoSoft
    val infoText: Color @Composable get() = tokens.infoInk
    val tintMint: Color @Composable get() = tokens.successSoft
    val tintLavender: Color @Composable get() = tokens.accentSoft
    val tintPeach: Color @Composable get() = tokens.emphasisSoft
    val highlightMint: Color @Composable get() = if (isDark) tokens.accentSoft else Daylight.Sky
    val highlightPeach: Color @Composable get() = if (isDark) tokens.emphasisSoft else Daylight.Butter
    val isDark: Boolean @Composable get() = MaterialTheme.colorScheme.surface.red < 0.5f
    private val tokens: Daylight.Tokens @Composable get() = Daylight.tokens(isDark)
}

// ---------------------------------------------------------------- Buttons

enum class NbButtonStyle { Primary, Accent, Secondary, Quiet, Danger, DangerQuiet, Inverse }
enum class NbButtonSize(val height: Dp, val hPad: Dp, val fontSize: TextUnit) {
    Small(36.dp, 14.dp, 14.sp), Medium(44.dp, 18.dp, 15.sp), Large(52.dp, 24.dp, 17.sp)
}

@Composable
fun NbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NbButtonStyle = NbButtonStyle.Primary,
    size: NbButtonSize = NbButtonSize.Medium,
    enabled: Boolean = true,
    icon: Painter? = null,
    accent: Color? = null,
    onAccent: Color? = null,
) {
    val accentFill = accent ?: Nb.accent
    val accentOn = onAccent ?: Nb.onAccent
    val haptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
    val (bg, fg, borderColor) = when {
        !enabled -> Triple(Nb.emphasis, Nb.textMuted, Color.Transparent)
        style == NbButtonStyle.Primary -> Triple(Nb.ink, Nb.onInk, Color.Transparent)
        style == NbButtonStyle.Accent -> Triple(accentFill, accentOn, Color.Transparent)
        style == NbButtonStyle.Secondary -> Triple(Color.Transparent, Nb.ink, Nb.borderStrong)
        style == NbButtonStyle.Quiet -> Triple(Color.Transparent, Nb.textSecondary, Color.Transparent)
        style == NbButtonStyle.Danger -> Triple(Color.Transparent, Nb.danger, Nb.danger)
        style == NbButtonStyle.DangerQuiet -> Triple(Color.Transparent, Nb.danger, Color.Transparent)
        else -> Triple(Daylight.Paper, Daylight.NightShift, Color.Transparent)
    }
    Row(
        modifier = modifier
            .heightIn(min = size.height)
            .clip(NbShapes.pill)
            .background(bg)
            .then(if (borderColor != Color.Transparent) Modifier.border(if (style == NbButtonStyle.Danger) 1.5.dp else 1.dp, borderColor, NbShapes.pill) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = { haptics.tap(); onClick() })
            .padding(horizontal = if (style == NbButtonStyle.Quiet || style == NbButtonStyle.DangerQuiet) 8.dp else size.hPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (size == NbButtonSize.Small) 16.dp else 18.dp))
        }
        Text(
            text = text,
            color = fg,
            style = TextStyle(fontFamily = NbBody, fontWeight = if (style == NbButtonStyle.Secondary || style == NbButtonStyle.Quiet) FontWeight.SemiBold else FontWeight.Bold, fontSize = size.fontSize),
            maxLines = 1
        )
    }
}

enum class NbIconButtonStyle { Plain, Tonal, Outline, Filled, Accent }

@Composable
fun NbIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NbIconButtonStyle = NbIconButtonStyle.Plain,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val haptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
    val bg = when (style) {
        NbIconButtonStyle.Tonal -> Nb.muted
        NbIconButtonStyle.Filled -> Nb.ink
        NbIconButtonStyle.Accent -> Nb.accent
        else -> Color.Transparent
    }
    val fg = tint ?: when (style) {
        NbIconButtonStyle.Filled -> Nb.onInk
        NbIconButtonStyle.Accent -> Nb.onAccent
        else -> Nb.text
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == NbIconButtonStyle.Outline) Modifier.border(1.dp, Nb.borderStrong, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = { haptics.tap(); onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter, contentDescription = contentDescription, tint = if (enabled) fg else Nb.textMuted, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun NbIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NbIconButtonStyle = NbIconButtonStyle.Plain,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) = NbIconButton(
    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(imageVector),
    contentDescription = contentDescription, onClick = onClick, modifier = modifier,
    style = style, size = size, iconSize = iconSize, tint = tint, enabled = enabled
)

// ---------------------------------------------------------------- Chips

@Composable
fun NbChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Nb.text
    val haptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(NbShapes.pill)
            .background(bg)
            .then(if (!selected) Modifier.border(1.dp, Nb.borderStrong, NbShapes.pill) else Modifier)
            .clickable(role = Role.Tab, onClick = { haptics.selection(); onClick() })
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, color = fg, style = NbType.label, maxLines = 1)
    }
}

enum class NbStatus { Success, Warning, Danger, Neutral, Accent }

@Composable
fun NbStatusChip(text: String, status: NbStatus, modifier: Modifier = Modifier, icon: Painter? = null) {
    val (bg, fg) = when (status) {
        NbStatus.Success -> Nb.successSoft to Nb.successText
        NbStatus.Warning -> Nb.warningSoft to Nb.warningText
        NbStatus.Danger -> Nb.dangerSoft to Nb.danger
        NbStatus.Neutral -> Nb.muted to Nb.textSecondary
        NbStatus.Accent -> MaterialTheme.colorScheme.primaryContainer to Nb.accentText
    }
    Row(
        modifier = modifier
            .clip(NbShapes.pill)
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, color = fg, style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Bold, fontSize = 13.sp), maxLines = 1)
    }
}

// ---------------------------------------------------------------- Album art

private val ArtPalette = com.nobodybuilds.listens.data.covers.NbCoverColors.ART_PALETTE.map { Color(it) }

/** Stable placeholder colour for a title/album, from the Daylight cover palette (Paper initials). */
fun nbArtColor(key: String): Color = ArtPalette[(key.hashCode() and 0x7fffffff) % ArtPalette.size]

fun nbInitials(text: String): String {
    val words = text.split(' ', '-', '_', '/', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

/**
 * Album art the Daylight way: real artwork when there is some, otherwise the cover
 * placeholder — a solid colour with a dot grid and the album's initials.
 */
@Composable
fun NbArt(
    model: Any?,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape((size.value * 0.21f).coerceIn(8f, 24f).dp),
    contentDescription: String? = null,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val color = remember(title) { nbArtColor(title) }
    Box(modifier = modifier.size(size).clip(shape).background(color)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            val r = 1.4.dp.toPx()
            var y = step / 2
            while (y < this.size.height) {
                var x = step / 2
                while (x < this.size.width) {
                    drawCircle(Daylight.Paper.copy(alpha = 0.17f), radius = r, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Text(
            text = nbInitials(title),
            color = Daylight.Paper,
            style = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.28f).sp, letterSpacing = (-0.03).em, lineHeight = (size.value * 0.28f).sp),
            modifier = Modifier.align(Alignment.BottomStart).padding(size * 0.1f),
            maxLines = 1
        )
        if (model != null && (model !is String || model.isNotBlank())) {
            SmartImage(
                model = model,
                contentDescription = contentDescription,
                shape = shape,
                targetSize = Size(sizePx, sizePx),
                placeHolderBackgroundColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
