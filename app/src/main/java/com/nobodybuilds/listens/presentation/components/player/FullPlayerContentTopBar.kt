package com.nobodybuilds.listens.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.LocalMaterialTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerTopBar(
    isLandscape: Boolean,
    expansionFractionProvider: () -> Float,
    isCastConnecting: Boolean,
    isRemotePlaybackActive: Boolean,
    currentSong: Song?,
    selectedRouteName: String?,
    isBluetoothEnabled: Boolean,
    bluetoothName: String?,
    playerAccentColor: Color,
    playerOnAccentColor: Color,
    onCollapse: () -> Unit,
    onShowCastClicked: () -> Unit,
    onQueueClick: () -> Unit,
    onSongOptionsClick: () -> Unit
) {
    // MD3: TopAppBar 在竖屏时滑入，横屏时向上滑出淡出
    AnimatedVisibility(
        visible = !isLandscape,
        enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
               slideOutVertically(
                   targetOffsetY = { -it / 2 },
                   animationSpec = tween(220, easing = FastOutSlowInEasing)
               )
    ) {
        TopAppBar(
            modifier = Modifier.graphicsLayer {
                val fraction = expansionFractionProvider()
                // TopBar should always fade in smoothly, ignoring delayAll to avoid empty UI
                val startThreshold = 0f
                val endThreshold = 1f
                alpha = ((fraction - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
            ),
            title = {
                if (!isCastConnecting) {
                    AnimatedVisibility(visible = (!isRemotePlaybackActive)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modifier = Modifier.padding(start = 18.dp),
                                text = stringResource(R.string.setcat_now_playing),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (currentSong != null && (currentSong.telegramChatId != null || currentSong.contentUriString.startsWith("telegram:"))) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Rounded.Cloud,
                                    contentDescription = stringResource(R.string.presentation_batch_g_player_cd_cloud_stream),
                                    tint = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 8.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier
                        // Ancho total = 14dp de padding + 42dp del botón
                        .width(56.dp)
                        .height(42.dp),
                    // 2. Alinea el contenido (el botón) al final (derecha) y centrado verticalmente
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // 3. Tu botón circular original, sin cambios
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(playerOnAccentColor.copy(alpha = 0.7f))
                            .clickable(onClick = onCollapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_collapse),
                            tint = playerAccentColor
                        )
                    }
                }
            },
            actions = {
                Row(
                    modifier = Modifier
                        .padding(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val showCastLabel = isCastConnecting || (isRemotePlaybackActive && selectedRouteName != null)
                    val isBluetoothActive =
                        isBluetoothEnabled && !bluetoothName.isNullOrEmpty() && !isRemotePlaybackActive && !isCastConnecting
                    val castIconPainter = when {
                        isCastConnecting || isRemotePlaybackActive -> painterResource(R.drawable.rounded_cast_24)
                        isBluetoothActive -> painterResource(R.drawable.rounded_bluetooth_24)
                        else -> painterResource(R.drawable.rounded_mobile_speaker_24)
                    }
                    val castCornersExpanded = 50.dp
                    val castCornersCompact = 6.dp
                    val castTopStart = castCornersExpanded
                    val castTopEnd by animateDpAsState(
                        targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                    val castBottomStart = castCornersExpanded
                    val castBottomEnd by animateDpAsState(
                        targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                    val castContainerColor = playerOnAccentColor.copy(alpha = 0.7f)
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .align(Alignment.CenterVertically)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                            .widthIn(
                                min = 50.dp,
                                max = if (showCastLabel) 190.dp else 58.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    topStart = castTopStart.coerceAtLeast(0.dp),
                                    topEnd = castTopEnd.coerceAtLeast(0.dp),
                                    bottomStart = castBottomStart.coerceAtLeast(0.dp),
                                    bottomEnd = castBottomEnd.coerceAtLeast(0.dp)
                                )
                            )
                            .background(castContainerColor)
                            .clickable { onShowCastClicked() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(start = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                painter = castIconPainter,
                                contentDescription = when {
                                    isCastConnecting || isRemotePlaybackActive -> stringResource(R.string.presentation_batch_g_player_cd_cast)
                                    isBluetoothActive -> stringResource(R.string.presentation_batch_g_player_cd_bluetooth)
                                    else -> stringResource(R.string.presentation_batch_g_player_cd_local_playback)
                                },
                                tint = playerAccentColor
                            )
                            AnimatedVisibility(visible = showCastLabel) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(8.dp))
                                    AnimatedContent(
                                        targetState = when {
                                            isCastConnecting -> stringResource(R.string.presentation_batch_g_player_connecting)
                                            isRemotePlaybackActive && selectedRouteName != null -> selectedRouteName ?: ""
                                            else -> ""
                                        },
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                                        },
                                        label = "castButtonLabel"
                                    ) { label ->
                                        Row(
                                            modifier = Modifier.padding(end = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = playerAccentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            AnimatedVisibility(visible = isCastConnecting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier
                                                        .size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = playerAccentColor
                                                )
                                            }
                                            if (isRemotePlaybackActive && !isCastConnecting) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(LocalMaterialTheme.current.onTertiaryContainer)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Queue Button
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 50.dp,
                                    bottomStart = 6.dp,
                                    bottomEnd = 50.dp
                                )
                            )
                            .background(playerOnAccentColor.copy(alpha = 0.7f))
                            .clickable {
                                onQueueClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_queue_music_24),
                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_queue),
                            tint = playerAccentColor
                        )
                    }

                    // Song options: the full song menu for the current song.
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(playerOnAccentColor.copy(alpha = 0.7f))
                            .clickable { onSongOptionsClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_more_vert_24),
                            contentDescription = "Song options",
                            tint = playerAccentColor
                        )
                    }
                }
            }
        )
    }
}
