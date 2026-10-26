package com.nobodybuilds.listens.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.shared.R as SharedR
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbSettingRow
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbTile
import com.nobodybuilds.listens.presentation.nb.NbTopBar
import com.nobodybuilds.listens.presentation.nb.NbType

private const val DeveloperName = "Shaan Shoukath"
private const val DeveloperHandle = "Shaan-Shoukath"
private const val CoDeveloperName = "Abhiram NJ"
private const val CoDeveloperHandle = "twilighty-abhi"
private const val RepoUrl = "https://github.com/Nobody-Builds/Nobody-Listens"
private const val ReleasesUrl = "$RepoUrl/releases"
private const val IssuesUrl = "$RepoUrl/issues"
private const val UpstreamUrl = "https://github.com/twilighty-abhi/lmusic-player"
private const val PixelPlayerUrl = "https://github.com/theovilardo/PixelPlayer"

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
                NbOverline("Developers")
                Spacer(Modifier.height(10.dp))
                DeveloperRow(
                    name = DeveloperName,
                    role = "Developer · @$DeveloperHandle",
                    avatar = R.drawable.developer_avatar,
                    onClick = { openUrl(context, "https://github.com/$DeveloperHandle") }
                )
                Spacer(Modifier.height(10.dp))
                DeveloperRow(
                    name = CoDeveloperName,
                    role = "Developer, LMusic author · @$CoDeveloperHandle",
                    avatar = R.drawable.developer_avatar_abhiram,
                    onClick = { openUrl(context, "https://github.com/$CoDeveloperHandle") }
                )
                Spacer(Modifier.height(14.dp))
                NbButton(
                    text = "Nobody Listens on GitHub",
                    onClick = { openUrl(context, RepoUrl) },
                    style = NbButtonStyle.Secondary,
                    icon = painterResource(R.drawable.github),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                NbButton(
                    text = "LMusic on GitHub",
                    onClick = { openUrl(context, UpstreamUrl) },
                    style = NbButtonStyle.Secondary,
                    icon = painterResource(R.drawable.github),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            NbCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AboutLinkRow(title = "Updates", subtitle = "Every release, with the APK to install") { openUrl(context, ReleasesUrl) }
                NbDivider()
                AboutLinkRow(title = "Report a problem", subtitle = "Open an issue on GitHub") { openUrl(context, IssuesUrl) }
            }

            NbCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NbOverline("A fork of LMusic", modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nobody Listens is a fork of LMusic by Abhiram NJ (Twilight Abhi), which is built on PixelPlayer. " +
                        "Thanks to both for the player this grew from.",
                    style = NbType.body,
                    color = Nb.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                AboutLinkRow(title = "LMusic", subtitle = "by Abhiram NJ · github.com/twilighty-abhi") { openUrl(context, UpstreamUrl) }
                NbDivider()
                AboutLinkRow(title = "PixelPlayer", subtitle = "by theovilardo and contributors") { openUrl(context, PixelPlayerUrl) }
            }

            NbTile(background = Nb.subtle) {
                Text("Made for listening, not for engagement.", style = NbType.title, color = Nb.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nobody Listens plays the music on your phone. No accounts, no ads, no tracking — your listening stats stay on this device.",
                    style = NbType.body,
                    color = Nb.textSecondary
                )
            }

            Text(
                "© $DeveloperName. Based on LMusic © Abhiram NJ and PixelPlayer. Released under the MIT License.",
                style = NbType.caption,
                color = Nb.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun DeveloperRow(name: String, role: String, @DrawableRes avatar: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(NbShapes.md).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(NbShapes.pill)
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = NbType.h3, color = Nb.text)
            Text(role, style = NbType.meta, color = Nb.textMuted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutHero(versionName: String, onVersionLongPress: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    NbTile(background = Nb.accentSoft, modifier = Modifier.fillMaxWidth()) {
        // The launcher tile: the mark keeps its Paper backing in dark mode too, since its outline vanishes on dark.
        Box(
            Modifier.size(80.dp).clip(NbShapes.xl).background(colorResource(SharedR.color.nl_icon_background)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(SharedR.drawable.nl_mark),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Nobody Listens", style = NbType.display(40.sp()), color = Nb.text)
        Spacer(Modifier.height(4.dp))
        Text("Your music, on your phone.", style = NbType.bodyLarge, color = Nb.textSecondary)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(NbShapes.pill)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().longPress()
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
