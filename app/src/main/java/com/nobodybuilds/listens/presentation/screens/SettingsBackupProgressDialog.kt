package com.nobodybuilds.listens.presentation.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.backup.model.BackupOperationType
import com.nobodybuilds.listens.data.backup.model.BackupTransferProgressUpdate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val BackupTransferDialogMinimumVisibilityMs = 1500L

@Composable
internal fun BackupTransferProgressDialogHost(progress: BackupTransferProgressUpdate?) {
    var visibleProgress by remember { mutableStateOf<BackupTransferProgressUpdate?>(null) }
    var visibleSinceMs by remember { mutableStateOf(0L) }
    var isHoldingForMinimumTime by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (progress != null) {
            if (visibleProgress == null || isHoldingForMinimumTime) {
                visibleSinceMs = SystemClock.elapsedRealtime()
            }
            isHoldingForMinimumTime = false
            visibleProgress = progress
            return@LaunchedEffect
        }

        val currentVisibleProgress = visibleProgress ?: return@LaunchedEffect
        isHoldingForMinimumTime = true
        val elapsed = SystemClock.elapsedRealtime() - visibleSinceMs
        val remaining = BackupTransferDialogMinimumVisibilityMs - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
        if (visibleProgress == currentVisibleProgress) {
            visibleProgress = null
            visibleSinceMs = 0L
        }
        isHoldingForMinimumTime = false
    }

    visibleProgress?.let { currentProgress ->
        BackupTransferProgressDialog(progress = currentProgress)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupTransferProgressDialog(progress: BackupTransferProgressUpdate) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "BackupTransferProgress"
    )
    val progressPercent = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)
    val statusText = when (progress.operation) {
        BackupOperationType.EXPORT -> stringResource(R.string.backup_exporting)
        else -> stringResource(R.string.backup_importing)
    }
    val stepText = stringResource(
        R.string.backup_step_format,
        progress.step.coerceAtLeast(1),
        progress.totalSteps
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (progress.operation == BackupOperationType.EXPORT) {
                        stringResource(R.string.backup_dialog_creating)
                    } else {
                        stringResource(R.string.backup_dialog_restoring)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.84f),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_f_percent, progressPercent),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.4f
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.presentation_batch_f_status_bullet_step, statusText, stepText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                AnimatedContent(
                    targetState = progress.detail,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "BackupStepDetail"
                ) { animatedDetail ->
                    Text(
                        text = animatedDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                progress.section?.let { section ->
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
