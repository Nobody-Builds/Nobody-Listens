@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.ExternalAccountUiModel
import com.nobodybuilds.listens.presentation.viewmodel.ExternalServiceAccount
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
internal fun ConnectedAccountCard(
    account: ExternalAccountUiModel,
    onManage: () -> Unit,
    onLogout: () -> Unit,
    painter: androidx.compose.ui.graphics.painter.Painter? = null
) {
    val statusSoon = stringResource(R.string.presentation_batch_b_accounts_status_soon)
    val statusConnected = stringResource(R.string.presentation_batch_b_accounts_status_connected)
    val openService = stringResource(R.string.presentation_batch_b_accounts_open_service)
    val comingSoonShort = stringResource(R.string.presentation_batch_b_accounts_coming_soon_short)
    val loggingOut = stringResource(R.string.presentation_batch_b_accounts_logging_out)
    val logOut = stringResource(R.string.cd_logout)
    val palette = servicePalette(account.service)
    val isComingSoon = account.service == ExternalServiceAccount.GOOGLE_DRIVE
    val cardShape = AbsoluteSmoothCornerShape(28.dp, 60)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    if (account.service == ExternalServiceAccount.NAVIDROME) {
        ServiceIcon(
            service = account.service,
            tint = palette.iconTint,
            modifier = Modifier
                .width(48.dp)
                .height(40.dp)
        )
    } else {
        Surface(
            shape = AbsoluteSmoothCornerShape(16.dp, 60),
            color = palette.iconContainer
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = palette.iconTint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            } else {
                ServiceIcon(
                    service = account.service,
                    tint = palette.iconTint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
    }

    Spacer(Modifier.size(12.dp))

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = account.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = account.accountLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (isComingSoon) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            palette.statusContainer
        }
    ) {
        Text(
            text = if (isComingSoon) statusSoon else statusConnected,
            style = MaterialTheme.typography.labelMedium,
            color = if (isComingSoon) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                palette.statusTint
            },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

            Surface(
                shape = AbsoluteSmoothCornerShape(14.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = palette.iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = account.syncedContentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))

            FilledTonalButton(
                onClick = onManage,
                enabled = !account.isLoggingOut && !isComingSoon,
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.primaryActionContainer,
                    contentColor = palette.primaryActionTint,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    imageVector = if (isComingSoon) Icons.Rounded.Link else Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (isComingSoon) comingSoonShort else openService,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onLogout,
                enabled = !account.isLoggingOut,
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                border = BorderStroke(1.dp, palette.primaryActionTint.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (account.isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (account.isLoggingOut) loggingOut else logOut,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun EmptyAccountsCard(
    disconnectedServices: List<ExternalServiceAccount>,
    onConnect: (ExternalServiceAccount) -> Unit
) {
    val noLinkedTitle = stringResource(R.string.presentation_batch_b_accounts_no_linked_title)
    val noLinkedBody = stringResource(R.string.presentation_batch_b_accounts_no_linked_body)
    val connectTemplate = stringResource(R.string.presentation_batch_b_accounts_connect_service)
    val serviceSoonTemplate = stringResource(R.string.presentation_batch_b_accounts_service_paren_coming_soon)
    Card(
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = noLinkedTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = noLinkedBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            disconnectedServices.forEach { service ->
                val isComingSoon = service == ExternalServiceAccount.GOOGLE_DRIVE
                val painter = when (service) {
                    ExternalServiceAccount.NETEASE -> painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_)
                    ExternalServiceAccount.QQ_MUSIC -> painterResource(R.drawable.qq_music)
                    ExternalServiceAccount.TELEGRAM -> painterResource(R.drawable.telegram)
                    else -> null
                }
                FilledTonalButton(
                    onClick = { if (!isComingSoon) onConnect(service) },
                    enabled = !isComingSoon,
                    shape = AbsoluteSmoothCornerShape(18.dp, 60),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isComingSoon) {
                            serviceSoonTemplate.format(serviceDisplayName(service))
                        } else {
                            connectTemplate.format(serviceDisplayName(service))
                        }
                    )
                }
            }
        }
    }
}
