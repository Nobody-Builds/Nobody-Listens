package com.twilighty.lmusic.presentation.nb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.twilighty.lmusic.presentation.components.SmartImage
import com.twilighty.lmusic.ui.theme.NbBody
import com.twilighty.lmusic.ui.theme.Daylight
import com.twilighty.lmusic.ui.theme.NbDisplay
import com.twilighty.lmusic.ui.theme.UnboundedFamily

// =====================================================================================
// LMusic on the Nobody Builds "Daylight" design system (NobodyBuilds-Design). Colours always
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
    val haptics = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
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
    val haptics = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
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
    val haptics = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
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

private val ArtPalette = com.twilighty.lmusic.data.covers.NbCoverColors.ART_PALETTE.map { Color(it) }

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

// ---------------------------------------------------------------- Layout pieces

@Composable
fun NbTopBar(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    titleStyle: TextStyle = NbType.h1,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        navigationIcon?.invoke()
        Column(Modifier.weight(1f)) {
            if (kicker != null) Text(kicker, style = NbType.caption, color = Nb.textMuted)
            Text(title, style = titleStyle, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun NbSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = NbType.h3, color = Nb.text, modifier = Modifier.weight(1f, fill = false))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = NbType.label,
                color = Nb.textSecondary,
                modifier = Modifier
                    .clip(NbShapes.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun NbOverline(text: String, modifier: Modifier = Modifier, color: Color = Nb.textMuted) {
    Text(text.uppercase(), style = NbType.overline, color = color, modifier = modifier)
}

@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = Nb.border,
    background: Color = Nb.panel,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(NbShapes.lg)
            .background(background)
            .border(1.dp, borderColor, NbShapes.lg)
            .then(if (onClick != null) Modifier.clickable(onClick = { com.twilighty.lmusic.presentation.haptics.LMusicHaptics.get().tap(); onClick() }) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun NbTile(
    background: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(NbShapes.xl)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = { com.twilighty.lmusic.presentation.haptics.LMusicHaptics.get().tap(); onClick() }) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun NbStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = Nb.textMuted,
    onClick: (() -> Unit)? = null,
) {
    NbCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = NbType.meta, color = Nb.textMuted, maxLines = 1)
        Text(value, style = NbType.stat, color = Nb.text, maxLines = 1)
        if (supporting != null) Text(supporting, style = NbType.caption.copy(fontWeight = FontWeight.SemiBold), color = supportingColor, maxLines = 1)
    }
}

@Composable
fun NbMeter(progress: Float, modifier: Modifier = Modifier, color: Color = Nb.ink, track: Color = Nb.emphasis, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(NbShapes.pill).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(NbShapes.pill).background(color))
    }
}

/** Daylight's date block, used for album years and listening-history days. */
@Composable
fun NbYearBlock(top: String, number: String, modifier: Modifier = Modifier, accent: Color = Nb.ink, onAccent: Color = Nb.onInk) {
    Column(
        modifier = modifier
            .width(52.dp)
            .clip(NbShapes.md)
            .border(1.dp, Nb.border, NbShapes.md)
            .background(Nb.panel),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            top.uppercase(),
            style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.08.em),
            color = onAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(accent).padding(vertical = 3.dp)
        )
        Text(number, style = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 28.sp), color = Nb.text)
    }
}

// ---------------------------------------------------------------- Rows

@Composable
fun NbSongRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    artModel: Any? = null,
    artKey: String = title,
    isCurrent: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(NbShapes.lg)
            .then(if (onClick != null) Modifier.clickable(onClick = { com.twilighty.lmusic.presentation.haptics.LMusicHaptics.get().tap(); onClick() }) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) leading() else NbArt(model = artModel, title = artKey, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title, color = if (isCurrent) Nb.accentText else Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailingText != null) Text(trailingText, style = NbType.caption, color = Nb.textMuted)
        trailing?.invoke(this)
    }
}

@Composable
fun NbSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = { com.twilighty.lmusic.presentation.haptics.LMusicHaptics.get().tap(); onClick() }) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(NbShapes.md).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title, color = Nb.text)
            if (subtitle != null) Text(subtitle, style = NbType.meta, color = Nb.textMuted)
        }
        trailing?.invoke()
    }
}

@Composable
fun NbSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptics = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { callback -> { value: Boolean -> haptics.toggle(value); callback(value) } },
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Nb.ink,
            checkedThumbColor = Nb.onInk,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Nb.emphasis,
            uncheckedThumbColor = Nb.borderStrong,
            uncheckedBorderColor = Nb.borderStrong,
        ),
        thumbContent = null
    )
}

/** Pill segmented control (Light / Dark / System). */
@Composable
fun NbSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics.current
    Row(
        modifier = modifier.clip(NbShapes.pill).background(Nb.muted).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(NbShapes.pill)
                    .background(if (selected) Nb.panel else Color.Transparent)
                    .clickable(role = Role.Tab) { if (index != selectedIndex) haptics.selection(); onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option, style = NbType.label, color = if (selected) Nb.text else Nb.textSecondary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Feedback

@Composable
fun NbCallout(
    boldText: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .background(Nb.warningSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Nb.warningText, modifier = Modifier.size(18.dp))
        Text(
            buildString { append(boldText); append(' '); append(text) },
            style = NbType.meta,
            color = Nb.warningText,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = NbType.label.copy(fontWeight = FontWeight.Bold),
                color = Nb.warningText,
                modifier = Modifier.clip(NbShapes.pill).clickable(role = Role.Button, onClick = onAction).padding(6.dp)
            )
        }
    }
}

@Composable
fun NbEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .dashedBorder(Nb.borderStrong)
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, style = NbType.h3, color = Nb.text, textAlign = TextAlign.Center)
        Text(body, style = NbType.body, color = Nb.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp), content = actions)
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = this.drawBehindDashed(color)

private fun Modifier.drawBehindDashed(color: Color): Modifier = this.drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        ),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
    )
}

/** Thin divider in Daylight border colour. */
@Composable
fun NbDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Nb.border))
}

@Composable
fun NbSpacer(height: Dp) = Spacer(Modifier.height(height))
