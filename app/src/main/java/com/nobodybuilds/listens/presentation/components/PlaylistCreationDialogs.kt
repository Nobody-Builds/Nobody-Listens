@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

@Composable
fun PlaylistCreationTypeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onManualSelected: () -> Unit,
    onAiSelected: () -> Unit,
    isAiEnabled: Boolean = true,
    onSetupAiClick: (() -> Unit)? = null
) {
    if (!visible) return

    val dialogShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = 40.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBR = 40.dp,
        smoothnessAsPercentBR = 60
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_e_create_playlist_title),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_e_create_playlist_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                CreationModeCard(
                    title = stringResource(R.string.presentation_batch_e_creation_mode_manual),
                    subtitle = stringResource(R.string.presentation_batch_e_creation_mode_manual_subtitle),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = onManualSelected,
                    enabled = true,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                CreationModeCard(
                    title = stringResource(R.string.presentation_batch_e_creation_mode_ai),
                    subtitle = if (isAiEnabled) {
                        stringResource(R.string.presentation_batch_e_creation_mode_ai_subtitle_enabled)
                    } else {
                        stringResource(R.string.presentation_batch_e_creation_mode_ai_subtitle_disabled)
                    },
                    icon = {
                        Icon(
                            imageVector = if (isAiEnabled) Icons.Rounded.AutoAwesome else Icons.Rounded.Key,
                            contentDescription = null,
                            tint = if (isAiEnabled) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    onClick = onAiSelected,
                    enabled = isAiEnabled,
                    containerColor = if (isAiEnabled) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    contentColor = if (isAiEnabled) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (!isAiEnabled) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onSetupAiClick?.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.presentation_batch_e_setup_api_key))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationModeCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 22.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 22.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 22.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 22.dp,
            smoothnessAsPercentBR = 60
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.16f),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 12.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusTR = 18.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBL = 18.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBR = 12.dp,
                    smoothnessAsPercentBR = 60
                )
            ) {
                Row(modifier = Modifier.padding(10.dp)) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun CreateAiPlaylistDialog(
    visible: Boolean,
    isGenerating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGenerate: (playlistName: String?, prompt: String, minLength: Int, maxLength: Int) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = {
                if (!isGenerating) {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 7 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 7 }) + fadeOut(animationSpec = tween(200)),
                label = "create_ai_playlist_dialog"
            ) {
                CreateAiPlaylistContent(
                    isGenerating = isGenerating,
                    error = error,
                    onDismiss = onDismiss,
                    onGenerate = onGenerate
                )
            }
        }
    }
}
