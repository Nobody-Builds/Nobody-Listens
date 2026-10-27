package com.nobodybuilds.listens.presentation.nb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.ui.theme.NbBody
import com.nobodybuilds.listens.ui.theme.NbDisplay

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
            .then(if (onClick != null) Modifier.clickable(onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().tap(); onClick() }) else Modifier)
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
            .then(if (onClick != null) Modifier.clickable(onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().tap(); onClick() }) else Modifier)
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
            .then(if (onClick != null) Modifier.clickable(onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().tap(); onClick() }) else Modifier)
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
            .then(if (onClick != null) Modifier.clickable(onClick = { com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().tap(); onClick() }) else Modifier)
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
    val haptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
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
    val haptics = com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics.current
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
