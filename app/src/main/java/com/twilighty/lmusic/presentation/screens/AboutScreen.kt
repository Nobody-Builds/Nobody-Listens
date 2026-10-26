package com.twilighty.lmusic.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.twilighty.lmusic.R
import com.twilighty.lmusic.presentation.components.MiniPlayerHeight
import com.twilighty.lmusic.presentation.navigation.Screen
import com.twilighty.lmusic.presentation.navigation.navigateSafely
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbSettingRow
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbStatus
import com.twilighty.lmusic.presentation.nb.NbStatusChip
import com.twilighty.lmusic.presentation.nb.NbTile
import com.twilighty.lmusic.presentation.nb.NbTopBar
import com.twilighty.lmusic.presentation.nb.NbType

private const val DeveloperName = "Abhiram NJ"
private const val DeveloperHandle = "Twilight Abhi"
private const val DeveloperGitHub = "https://github.com/twilighty-abhi"
private const val RepoUrl = "https://github.com/twilighty-abhi/lmusic-player"
private const val ReleasesUrl = "$RepoUrl/releases"
private const val IssuesUrl = "$RepoUrl/issues"

@OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
) {
    val context = LocalContext.current
    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: Exception) {
        "—"
    }
    val bottomPad = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nb.canvas)
            .statusBarsPadding()
    ) {
        NbTopBar(
            title = "About",
            titleStyle = NbType.h2,
            navigationIcon = {
                NbIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigationIconClick
                )
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AboutHero(
                versionName = versionName,
                onVersionLongPress = { navController.navigateSafely(Screen.EasterEgg.route) }
            )

            NbCard {
                NbOverline("Developer")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(NbShapes.pill).background(Nb.ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AN", style = NbType.h3, color = Nb.onInk)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(DeveloperName, style = NbType.h3, color = Nb.text)
                        Text("$DeveloperHandle · @twilighty-abhi", style = NbType.meta, color = Nb.textMuted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                NbButton(
                    text = "GitHub profile",
                    onClick = { openUrl(context, DeveloperGitHub) },
                    style = NbButtonStyle.Secondary,
                    icon = painterResource(R.drawable.github),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            NbCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AboutLinkRow(title = "Source code", subtitle = "github.com/twilighty-abhi/lmusic-player") { openUrl(context, RepoUrl) }
                NbDivider()
                AboutLinkRow(title = "Updates", subtitle = "Every release, with the APK to install") { openUrl(context, ReleasesUrl) }
                NbDivider()
                AboutLinkRow(title = "Report a problem", subtitle = "Open an issue on GitHub") { openUrl(context, IssuesUrl) }
            }

            NbTile(background = Nb.subtle) {
                Text("Made for listening, not for engagement.", style = NbType.title, color = Nb.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "LMusic plays the music on your phone. No accounts, no ads, no tracking — your listening stats stay on this device.",
                    style = NbType.body,
                    color = Nb.textSecondary
                )
            }

            Text(
                "© $DeveloperName. Released under the MIT License.",
                style = NbType.caption,
                color = Nb.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutHero(versionName: String, onVersionLongPress: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    NbTile(background = Nb.accentSoft, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(64.dp).clip(NbShapes.xl).background(Nb.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.lmusic_base_monochrome),
                contentDescription = null,
                tint = Nb.onAccent,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("LMusic", style = NbType.display(40.sp()), color = Nb.text)
        Spacer(Modifier.height(4.dp))
        Text("Your music, on your phone.", style = NbType.bodyLarge, color = Nb.textSecondary)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(NbShapes.pill)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        com.twilighty.lmusic.presentation.haptics.LMusicHaptics.get().longPress()
                        onVersionLongPress()
                    }
                )
        ) {
            NbStatusChip(text = "Version $versionName", status = NbStatus.Accent)
        }
    }
}

@Composable
private fun AboutLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    NbSettingRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Nb.textMuted
            )
        }
    )
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
