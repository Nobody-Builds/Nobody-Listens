package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.ButtonDefaults as M3ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon as M3Icon
import com.nobodybuilds.listens.presentation.components.outputRouteIcon
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.shared.WearVolumeState

@Composable
internal fun SecondaryControlsRow(
    volumeEnabled: Boolean,
    deviceEnabled: Boolean,
    deviceRouteType: String,
    moreEnabled: Boolean,
    onVolumeClick: () -> Unit,
    onOutputClick: () -> Unit,
    onMoreClick: () -> Unit,
    deviceActiveColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        SecondaryActionSlot(lower = false) {
            SecondaryActionButton(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                enabled = volumeEnabled,
                active = false,
                activeColor = deviceActiveColor,
                raisedInactiveStyle = true,
                onClick = onVolumeClick,
                contentDescription = "Volume",
            )
        }
        SecondaryActionSlot(lower = true) {
            SecondaryActionButton(
                icon = outputRouteIcon(deviceRouteType),
                enabled = deviceEnabled,
                active = deviceRouteType == com.nobodybuilds.listens.shared.WearVolumeState.ROUTE_TYPE_WATCH,
                activeColor = deviceActiveColor,
                raisedInactiveStyle = true,
                onClick = onOutputClick,
                contentDescription = "Output device",
            )
        }
        SecondaryActionSlot(lower = false) {
            SecondaryActionButton(
                icon = Icons.Rounded.MoreVert,
                enabled = moreEnabled,
                active = false,
                activeColor = deviceActiveColor,
                raisedInactiveStyle = true,
                heavilyMutedWhenDisabled = true,
                onClick = onMoreClick,
                contentDescription = "More options",
            )
        }
    }
}

@Composable
private fun SecondaryActionButton(
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean,
    activeColor: Color,
    raisedInactiveStyle: Boolean = false,
    heavilyMutedWhenDisabled: Boolean = false,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val palette = LocalWearPalette.current
    val activeContainerColor = activeColor.copy(alpha = 0.84f)
    val inactiveContainerColor = if (raisedInactiveStyle) {
        palette.surfaceContainerHighest
    } else {
        palette.chipContainer
    }
    val inactiveContentColor = if (raisedInactiveStyle) {
        palette.textPrimary.copy(alpha = 0.94f)
    } else {
        palette.chipContent
    }
    val disabledContainerColor = if (heavilyMutedWhenDisabled) {
        palette.surfaceContainerLowest.copy(alpha = 0.92f)
    } else {
        palette.controlDisabledContainer
    }
    val disabledContentColor = if (heavilyMutedWhenDisabled) {
        palette.textSecondary.copy(alpha = 0.48f)
    } else {
        palette.controlDisabledContent
    }
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> disabledContainerColor
            active -> activeContainerColor
            else -> inactiveContainerColor
        },
        animationSpec = spring(),
        label = "secondaryContainer",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> disabledContentColor
            active -> if (activeContainerColor.luminance() > 0.52f) Color.Black else Color.White
            else -> inactiveContentColor
        },
        animationSpec = spring(),
        label = "secondaryTint",
    )

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SecondaryActionSlot(
    lower: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(width = 48.dp, height = 48.dp),
        contentAlignment = if (lower) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        content()
    }
}

@Composable
internal fun BottomQueueShortcut(
    revealProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWearPalette.current
    val clampedProgress = revealProgress.coerceIn(0f, 1f)
    if (clampedProgress <= 0.01f) return

    val containerColor by animateColorAsState(
        targetValue = if (enabled) palette.controlContainer else palette.controlDisabledContainer,
        animationSpec = spring(),
        label = "queueShortcutContainer",
    )
    val iconColor by animateColorAsState(
        targetValue = if (enabled) palette.controlContent else palette.controlDisabledContent,
        animationSpec = spring(),
        label = "queueShortcutIcon",
    )

    val edgeHeight = lerp(16.dp, 66.dp, clampedProgress)
    val iconSize = lerp(14.dp, 24.dp, clampedProgress)
    val containerAlpha = (clampedProgress * 1.1f).coerceIn(0f, 1f)

    EdgeButton(
        onClick = onClick,
        enabled = enabled && clampedProgress > 0.65f,
        buttonSize = EdgeButtonSize.Small,
        colors = M3ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = iconColor,
            disabledContainerColor = palette.controlDisabledContainer,
            disabledContentColor = palette.controlDisabledContent,
        ),
        modifier = modifier
            //.height(edgeHeight)
            .graphicsLayer {
                alpha = containerAlpha
                scaleY = 0.55f + (0.45f * clampedProgress)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        ,
    ) {
        M3Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "Queue",
            modifier = Modifier.size(iconSize),
        )
    }
}
