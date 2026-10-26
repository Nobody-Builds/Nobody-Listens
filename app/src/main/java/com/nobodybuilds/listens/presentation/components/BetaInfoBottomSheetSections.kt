package com.nobodybuilds.listens.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nobodybuilds.listens.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
internal fun GitHubReportCard(
    onOpenIssues: () -> Unit,
    onReportIssue: () -> Unit
) {
    BetaCardSurface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.github),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_github_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_github_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenIssues,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 14.dp,
                        cornerRadiusTL = 14.dp,
                        cornerRadiusBL = 14.dp,
                        cornerRadiusBR = 14.dp,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Text(text = stringResource(R.string.presentation_batch_g_beta_sheet_open_issues))
                }
                Button(
                    onClick = onReportIssue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 14.dp,
                        cornerRadiusTL = 14.dp,
                        cornerRadiusBL = 14.dp,
                        cornerRadiusBR = 14.dp,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    )
                ) {
                    Text(text = stringResource(R.string.presentation_batch_g_beta_sheet_report_bug))
                }
            }
        }
    }
}

@Composable
internal fun BetaFaqSection(
    title: String,
    summary: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    BetaCardSurface(
        modifier = modifier,
        color = containerColor,
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconTint.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun BetaCardSurface(
    modifier: Modifier = Modifier,
    color: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 18.dp,
        cornerRadiusTL = 18.dp,
        cornerRadiusBL = 18.dp,
        cornerRadiusBR = 18.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentBR = 60
    )

    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            onClick = onClick,
            content = content
        )
    }
}

@Composable
internal fun BetaSubsectionHeader(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun BetaBulletList(items: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            BetaBulletText(text = stringResource(item))
        }
    }
}

@Composable
internal fun BetaFieldList(items: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                BetaSectionDivider()
            }
            Text(
                text = stringResource(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BetaBulletText(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun BetaSectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

internal fun launchUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
