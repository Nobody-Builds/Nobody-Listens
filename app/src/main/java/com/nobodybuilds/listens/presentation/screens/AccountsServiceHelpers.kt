@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.netease.auth.NeteaseLoginActivity
import com.nobodybuilds.listens.presentation.jellyfin.auth.JellyfinLoginActivity
import com.nobodybuilds.listens.presentation.navidrome.auth.NavidromeLoginActivity
import com.nobodybuilds.listens.presentation.qqmusic.auth.QqMusicLoginActivity
import com.nobodybuilds.listens.presentation.ytmusic.auth.YouTubeMusicLoginActivity
import com.nobodybuilds.listens.presentation.jiosaavn.auth.JioSaavnLoginActivity
import com.nobodybuilds.listens.presentation.telegram.auth.TelegramLoginActivity
import com.nobodybuilds.listens.presentation.viewmodel.ExternalServiceAccount

internal data class ServicePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val statusContainer: Color,
    val statusTint: Color,
    val primaryActionContainer: Color,
    val primaryActionTint: Color
)

@Composable
internal fun servicePalette(service: ExternalServiceAccount): ServicePalette {
    // Daylight: one palette for every service (the logo carries the identity), and the
    // connected status is a word on the success soft fill. Navidrome's logo keeps its colours.
    val scheme = MaterialTheme.colorScheme
    return ServicePalette(
        iconContainer = scheme.primaryContainer,
        iconTint = if (service == ExternalServiceAccount.NAVIDROME) Color.Unspecified else scheme.onPrimaryContainer,
        statusContainer = com.nobodybuilds.listens.presentation.nb.Nb.successSoft,
        statusTint = com.nobodybuilds.listens.presentation.nb.Nb.successText,
        primaryActionContainer = scheme.primaryContainer,
        primaryActionTint = scheme.onPrimaryContainer
    )
}

private fun accountIcon(service: ExternalServiceAccount): ImageVector {
    return when (service) {
        ExternalServiceAccount.TELEGRAM -> Icons.AutoMirrored.Rounded.Send
        ExternalServiceAccount.GOOGLE_DRIVE -> Icons.Rounded.CloudQueue
        ExternalServiceAccount.NETEASE -> Icons.Rounded.MusicNote
        ExternalServiceAccount.QQ_MUSIC -> Icons.Rounded.MusicNote
        ExternalServiceAccount.NAVIDROME -> Icons.Rounded.CloudQueue
        ExternalServiceAccount.JELLYFIN -> Icons.Rounded.CloudQueue
        ExternalServiceAccount.YOUTUBE_MUSIC -> Icons.Rounded.MusicNote
        ExternalServiceAccount.JIOSAAVN -> Icons.Rounded.MusicNote
    }
}

@Composable
internal fun ServiceIcon(service: ExternalServiceAccount, tint: Color, modifier: Modifier = Modifier) {
    if (service == ExternalServiceAccount.NAVIDROME) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            // Subsonic icon (Bottom) - No outer container
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_subsonic),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(32.dp)
            )
            
            // Navidrome icon (Top) - Closer horizontal offset, no outer container
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_navidrome),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 16.dp) // Closer overlap offset (was 24dp)
            )
        }
    } else if (service == ExternalServiceAccount.JELLYFIN) {
        Icon(
            painter = painterResource(R.drawable.ic_jellyfin),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = accountIcon(service),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
internal fun serviceDisplayName(service: ExternalServiceAccount): String {
    return when (service) {
        ExternalServiceAccount.TELEGRAM -> stringResource(R.string.presentation_batch_b_service_telegram)
        ExternalServiceAccount.GOOGLE_DRIVE -> stringResource(R.string.auth_gdrive_title)
        ExternalServiceAccount.NETEASE -> stringResource(R.string.presentation_batch_b_service_netease)
        ExternalServiceAccount.QQ_MUSIC -> stringResource(R.string.screen_qq_music_dashboard_title)
        ExternalServiceAccount.NAVIDROME -> stringResource(R.string.cd_subsonic_logo)
        ExternalServiceAccount.JELLYFIN -> stringResource(R.string.auth_jellyfin_title)
        ExternalServiceAccount.YOUTUBE_MUSIC -> "YouTube Music"
        ExternalServiceAccount.JIOSAAVN -> "JioSaavn"
    }
}

internal fun openService(
    context: Context,
    service: ExternalServiceAccount,
    onOpenNeteaseDashboard: () -> Unit,
    onOpenQqMusicDashboard: () -> Unit,
    onOpenNavidromeDashboard: () -> Unit,
    onOpenJellyfinDashboard: () -> Unit,
    onOpenYouTubeMusicDashboard: () -> Unit,
    onOpenJioSaavnDashboard: () -> Unit,
    preferNeteaseDashboard: Boolean
) {
    when (service) {
        ExternalServiceAccount.TELEGRAM -> {
            safeStartActivity(
                context = context,
                intent = Intent(context, TelegramLoginActivity::class.java)
            )
        }
        ExternalServiceAccount.GOOGLE_DRIVE -> {
            Toast.makeText(context, context.getString(R.string.accounts_google_drive_soon), Toast.LENGTH_SHORT).show()
        }
        ExternalServiceAccount.NETEASE -> {
            if (preferNeteaseDashboard) {
                onOpenNeteaseDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, NeteaseLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.QQ_MUSIC -> {
            if (preferNeteaseDashboard) {
                onOpenQqMusicDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, QqMusicLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.NAVIDROME -> {
            if (preferNeteaseDashboard) {
                onOpenNavidromeDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, NavidromeLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.JELLYFIN -> {
            if (preferNeteaseDashboard) {
                onOpenJellyfinDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, JellyfinLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.YOUTUBE_MUSIC -> {
            if (preferNeteaseDashboard) {
                onOpenYouTubeMusicDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, YouTubeMusicLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.JIOSAAVN -> {
            if (preferNeteaseDashboard) {
                onOpenJioSaavnDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, JioSaavnLoginActivity::class.java)
                )
            }
        }
    }
}

private fun safeStartActivity(
    context: Context,
    intent: Intent
) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.accounts_unable_open_screen), Toast.LENGTH_SHORT).show()
        }
}
