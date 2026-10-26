package com.nobodybuilds.listens.presentation.nb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.ui.theme.NbBody

// =====================================================================================
// Daylight form parts: fields (label above, 12dp radius, 1dp border), check / radio marks,
// compare rows (autofill review), sheet handle, dialog shell and toast.
// =====================================================================================

private val FieldShape = RoundedCornerShape(12.dp)

@Composable
fun NbFieldLabel(label: String, optional: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = NbType.label, color = Nb.text)
        if (optional) Text("Optional", style = NbType.caption, color = Nb.textMuted)
    }
}

/** Container used by both [NbTextField] overloads. */
@Composable
private fun NbFieldFrame(
    label: String?,
    optional: Boolean,
    helper: String?,
    isError: Boolean,
    highlighted: Boolean,
    focused: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    inner: @Composable () -> Unit,
) {
    val borderColor = when {
        isError -> Nb.danger
        focused -> Nb.ink
        highlighted -> Nb.accentText
        else -> Nb.borderStrong
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) NbFieldLabel(label, optional)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(FieldShape)
                .background(if (enabled) Nb.canvas else Nb.subtle)
                .border(if (focused || isError) 1.5.dp else 1.dp, borderColor, FieldShape)
                .padding(start = if (leading != null) 10.dp else 14.dp, end = if (trailing != null) 6.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leading?.invoke()
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) { inner() }
            trailing?.invoke()
        }
        if (helper != null) {
            Text(helper, style = NbType.caption, color = if (isError) Nb.danger else Nb.textMuted)
        }
    }
}

private val FieldTextStyle: TextStyle
    @Composable get() = NbType.body.copy(color = Nb.text, fontSize = 16.sp)

/** Daylight text field: label above, 1dp border, 12dp radius. */
@Composable
fun NbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    optional: Boolean = false,
    helper: String? = null,
    isError: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = textStyle ?: FieldTextStyle
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(fieldModifier),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(Nb.ink),
        decorationBox = { inner ->
            NbFieldFrame(label, optional, helper, isError, highlighted, focused, enabled, Modifier.fillMaxWidth(), leading, trailing) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = style.copy(color = Nb.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        }
    )
}

/** [TextFieldValue] version of [NbTextField] (keeps cursor / selection). */
@Composable
fun NbTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    optional: Boolean = false,
    helper: String? = null,
    isError: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = FieldTextStyle
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(fieldModifier),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        cursorBrush = SolidColor(Nb.ink),
        decorationBox = { inner ->
            NbFieldFrame(label, optional, helper, isError, highlighted, focused, enabled, Modifier.fillMaxWidth(), leading, trailing) {
                if (value.text.isEmpty() && placeholder != null) {
                    Text(placeholder, style = style.copy(color = Nb.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        }
    )
}

// ---------------------------------------------------------------- Marks

/** Square check mark (24dp, 7dp radius): ink when on, outlined when off. */
@Composable
fun NbCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) Nb.ink else Nb.canvas)
            .then(if (!checked) Modifier.border(1.5.dp, Nb.borderStrong, shape) else Modifier)
            .then(
                if (onCheckedChange != null) Modifier.clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Nb.onInk, modifier = Modifier.size(16.dp))
    }
}

/** Round radio mark (22dp): thick ink ring when selected. */
@Composable
fun NbRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(if (selected) 7.dp else 1.5.dp, if (selected) Nb.ink else Nb.borderStrong, CircleShape)
    )
}

/** List row with a radio on the end, used for option lists in sheets. */
@Composable
fun NbRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NbType.title.copy(fontWeight = FontWeight.Medium), color = Nb.text)
            if (subtitle != null) Text(subtitle, style = NbType.meta, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else NbRadio(selected)
    }
}

// ---------------------------------------------------------------- Compare row (autofill)

/**
 * One field in the autofill review: current value struck through (or "Empty"), the new
 * value below, and a check mark. The card outline turns ink when picked.
 */
@Composable
fun NbCompareRow(
    label: String,
    current: String,
    proposed: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .border(if (checked) 1.5.dp else 1.dp, if (checked) Nb.ink else Nb.border, NbShapes.lg)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            NbOverline(label)
            if (current.isBlank()) {
                Text("Empty", style = NbType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Nb.textMuted)
            } else if (current != proposed) {
                Text(current, style = NbType.caption.copy(textDecoration = TextDecoration.LineThrough), color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(proposed, style = NbType.title, color = Nb.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        NbCheckbox(checked = checked, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------- Sheet / dialog / toast

@Composable
fun NbSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(40.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Nb.borderStrong)
    )
}

val NbSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Daylight dialog shell: canvas card, 24dp radius, h3 title, content, actions aligned end.
 * Buttons go in [actions] (usually a Secondary "Cancel" and a Primary confirm).
 */
@Composable
fun NbDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    properties: DialogProperties = DialogProperties(),
    scrollable: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier = modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(NbShapes.xxl)
                .background(Nb.canvas)
                .border(1.dp, Nb.border, NbShapes.xxl)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = NbType.h3, color = Nb.text)
            if (body != null) Text(body, style = NbType.body, color = Nb.textSecondary)
            Column(
                modifier = if (scrollable) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Dark toast with an underlined action (Undo). Always dark in both themes. */
@Composable
fun NbToast(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(NbShapes.lg)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon?.invoke()
        Text(
            text,
            style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Bold, fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier
                    .clip(NbShapes.sm)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

/** Plain action row for menus: 24dp icon, label, optional trailing. Danger tints red. */
@Composable
fun NbActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val fg = when {
        !enabled -> Nb.textMuted
        danger -> Nb.danger
        else -> Nb.text
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(NbShapes.md)
            .clickable(enabled = enabled, role = Role.Button, onClick = {
                if (danger) com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().selection() else com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().tap()
                onClick()
            })
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (danger || !enabled) fg else Nb.textSecondary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = NbType.title.copy(fontWeight = if (danger) FontWeight.SemiBold else FontWeight.Medium), color = fg)
            if (subtitle != null) Text(subtitle, style = NbType.caption, color = Nb.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}
