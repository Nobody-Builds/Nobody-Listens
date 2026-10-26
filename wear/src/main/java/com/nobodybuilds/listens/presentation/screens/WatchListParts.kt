package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.nobodybuilds.listens.presentation.components.AlwaysOnScalingPositionIndicator
import com.nobodybuilds.listens.presentation.components.WearTopTimeText
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.WearTitleFont
import com.nobodybuilds.listens.presentation.theme.screenBackgroundColor
import com.nobodybuilds.listens.presentation.theme.surfaceContainerColor

/** The standard watch list screen: scaling list, scroll indicator and the time on top. */
@Composable
internal fun WatchListScaffold(content: ScalingLazyListScope.() -> Unit) {
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()
    Box(modifier = Modifier.fillMaxSize().background(palette.screenBackgroundColor())) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), columnState = columnState, content = content)
        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )
        WearTopTimeText(
            modifier = Modifier.align(Alignment.TopCenter).zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
internal fun WatchListTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.title2,
        fontFamily = WearTitleFont,
        color = LocalWearPalette.current.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    )
}

@Composable
internal fun WatchListMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = LocalWearPalette.current.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
internal fun WatchListChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = LocalWearPalette.current.textSecondary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    Chip(
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textPrimary) },
        secondaryLabel = subtitle.takeIf { it.isNotEmpty() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.textSecondary.copy(alpha = 0.78f)) }
        },
        icon = { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = palette.surfaceContainerColor(),
            contentColor = palette.chipContent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
