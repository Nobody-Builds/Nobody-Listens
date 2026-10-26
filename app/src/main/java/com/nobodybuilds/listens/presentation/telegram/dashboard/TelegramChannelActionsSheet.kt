@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.telegram.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.TelegramChannelEntity
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelActionsBottomSheet(
    channel: TelegramChannelEntity,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val publicChannelFallback = stringResource(R.string.presentation_batch_f_public_channel_fallback)
    val usernameLabel = remember(channel.username) {
        channel.username
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("@")) it else "@$it" }
    }

    com.nobodybuilds.listens.presentation.haptics.HapticBottomSheetEffect(sheetState)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
//            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = usernameLabel ?: publicChannelFallback,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            ChannelActionCard(
                title = if (isSyncing) {
                    stringResource(R.string.presentation_batch_f_syncing_channel)
                } else {
                    stringResource(R.string.presentation_batch_f_sync_now)
                },
                subtitle = if (isSyncing) {
                    stringResource(R.string.presentation_batch_f_updating_songs_from_telegram)
                } else {
                    stringResource(R.string.presentation_batch_f_fetch_latest_songs)
                },
                icon = Icons.Rounded.Sync,
                onClick = onSync,
                enabled = !isSyncing,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingContent = if (isSyncing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else {
                    null
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
            ChannelActionCard(
                title = stringResource(R.string.presentation_batch_f_remove_channel),
                subtitle = stringResource(R.string.presentation_batch_f_remove_channel_subtitle),
                icon = Icons.Rounded.Delete,
                onClick = onDelete,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 18.dp,
        cornerRadiusTL = 18.dp,
        cornerRadiusBR = 18.dp,
        cornerRadiusBL = 18.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        color = containerColor,
        contentColor = contentColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = contentColor.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
