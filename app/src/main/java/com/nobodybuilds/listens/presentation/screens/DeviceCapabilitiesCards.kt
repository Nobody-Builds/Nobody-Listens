package com.nobodybuilds.listens.presentation.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.AudioCapabilities
import com.nobodybuilds.listens.presentation.viewmodel.ExoPlayerInfo
import com.nobodybuilds.listens.presentation.viewmodel.FormatSupportInfo
import com.nobodybuilds.listens.presentation.viewmodel.LocalMusicStorageSummary
import com.nobodybuilds.listens.presentation.viewmodel.MemorySummary
import com.nobodybuilds.listens.presentation.viewmodel.PlaybackCompatibilitySummary
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
internal fun PlaybackReadinessCard(
    deviceInfo: Map<String, String>,
    audioCapabilities: AudioCapabilities?,
    storageSummary: LocalMusicStorageSummary?,
    playbackCompatibility: PlaybackCompatibilitySummary?,
    modifier: Modifier = Modifier
) {
    val needsReview = playbackCompatibility?.let {
        it.unsupportedLibrarySongCount > 0 || it.resampledLocalSongCount > 0
    } ?: false
    val containerColor = if (needsReview) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (needsReview) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(32.dp, 60),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIcon(
                    icon = if (needsReview) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    containerColor = contentColor.copy(alpha = 0.12f),
                    contentColor = contentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (needsReview) {
                            stringResource(R.string.device_capabilities_review_title)
                        } else {
                            stringResource(R.string.device_capabilities_ready_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }

            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroMetricTile(
                    label = stringResource(R.string.device_capabilities_metric_formats),
                    value = audioCapabilities?.supportedCodecs
                        ?.flatMap { it.supportedTypes }
                        ?.distinct()
                        ?.size
                        ?.toString()
                        ?: stringResource(R.string.device_capabilities_unknown_short),
                    modifier = Modifier.weight(1f),
                    containerColor = contentColor.copy(alpha = 0.10f),
                    contentColor = contentColor
                )
                HeroMetricTile(
                    label = stringResource(R.string.device_capabilities_metric_hw_decoders),
                    value = audioCapabilities?.supportedCodecs
                        ?.count { it.isHardwareAccelerated }
                        ?.toString()
                        ?: stringResource(R.string.device_capabilities_unknown_short),
                    modifier = Modifier.weight(1f),
                    containerColor = contentColor.copy(alpha = 0.10f),
                    contentColor = contentColor
                )
                HeroMetricTile(
                    label = stringResource(R.string.device_capabilities_metric_local_music),
                    value = storageSummary?.localSongCount?.toString()
                        ?: stringResource(R.string.device_capabilities_unknown_short),
                    modifier = Modifier.weight(1f),
                    containerColor = contentColor.copy(alpha = 0.10f),
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
internal fun LocalMusicStorageCard(
    storageSummary: LocalMusicStorageSummary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val musicSize = remember(storageSummary.localMusicBytes) {
        Formatter.formatShortFileSize(context, storageSummary.localMusicBytes)
    }
    val availableSize = remember(storageSummary.deviceAvailableBytes) {
        Formatter.formatShortFileSize(context, storageSummary.deviceAvailableBytes)
    }
    val totalSize = remember(storageSummary.deviceTotalBytes) {
        Formatter.formatShortFileSize(context, storageSummary.deviceTotalBytes)
    }
    val musicPercent = storagePercentLabel(storageSummary.localMusicStorageFraction)
    val usedPercent = storagePercentLabel(storageSummary.deviceUsedFraction)

    CapabilityCard(
        title = stringResource(R.string.device_capabilities_storage_title),
        icon = Icons.Rounded.Storage,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTile(
                label = stringResource(R.string.device_capabilities_storage_music_size),
                value = musicSize,
                supporting = stringResource(
                    R.string.device_capabilities_storage_music_count,
                    storageSummary.localSongCount
                ),
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                label = stringResource(R.string.device_capabilities_storage_available),
                value = availableSize,
                supporting = stringResource(R.string.device_capabilities_storage_total, totalSize),
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProgressReadout(
                label = stringResource(R.string.device_capabilities_storage_music_footprint),
                value = musicPercent,
                progress = storageSummary.localMusicStorageFraction
            )
            ProgressReadout(
                label = stringResource(R.string.device_capabilities_storage_device_used),
                value = usedPercent,
                progress = storageSummary.deviceUsedFraction,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (storageSummary.cloudSongCount > 0 || storageSummary.unavailableLocalFileCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (storageSummary.cloudSongCount > 0) {
                    TonalChip(
                        text = stringResource(
                            R.string.device_capabilities_storage_cloud_count,
                            storageSummary.cloudSongCount
                        )
                    )
                }
                if (storageSummary.unavailableLocalFileCount > 0) {
                    TonalChip(
                        text = stringResource(
                            R.string.device_capabilities_storage_unavailable_count,
                            storageSummary.unavailableLocalFileCount
                        ),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaybackPathCard(
    audioCapabilities: AudioCapabilities,
    memorySummary: MemorySummary?,
    exoPlayerInfo: ExoPlayerInfo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val availableRam = memorySummary?.availableRamBytes?.let {
        Formatter.formatShortFileSize(context, it)
    } ?: stringResource(R.string.device_capabilities_unknown_short)
    val totalRam = memorySummary?.totalRamBytes?.let {
        Formatter.formatShortFileSize(context, it)
    } ?: stringResource(R.string.device_capabilities_unknown_short)

    CapabilityCard(
        title = stringResource(R.string.device_capabilities_playback_path_title),
        icon = Icons.Rounded.Speaker,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTile(
                label = stringResource(R.string.presentation_batch_g_device_label_sample_rate),
                value = stringResource(R.string.presentation_batch_g_device_value_hz, audioCapabilities.outputSampleRate),
                supporting = stringResource(
                    R.string.device_capabilities_buffer_frames,
                    audioCapabilities.outputFramesPerBuffer
                ),
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                label = stringResource(R.string.device_capabilities_hifi_pcm_float),
                value = yesNo(audioCapabilities.isPcmFloatSupported),
                supporting = stringResource(R.string.device_capabilities_hifi_pcm_float_supporting),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTile(
                label = stringResource(R.string.presentation_batch_g_device_label_low_latency),
                value = yesNo(audioCapabilities.isLowLatencySupported),
                supporting = stringResource(R.string.presentation_batch_g_device_label_pro_audio) +
                    ": " + yesNo(audioCapabilities.isProAudioSupported),
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                label = stringResource(R.string.device_capabilities_memory_title),
                value = availableRam,
                supporting = stringResource(R.string.device_capabilities_memory_available_of, totalRam),
                modifier = Modifier.weight(1f)
            )
        }

        SectionLabel(text = stringResource(R.string.device_capabilities_offload_title))
        ChipRow(
            emptyText = stringResource(R.string.device_capabilities_offload_empty),
            chips = audioCapabilities.offloadSupportedFormats
        )

        SectionLabel(text = stringResource(R.string.device_capabilities_outputs_title))
        if (audioCapabilities.outputRoutes.isEmpty()) {
            Text(
                text = stringResource(R.string.device_capabilities_outputs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                audioCapabilities.outputRoutes.take(5).forEach { route ->
                    OutputRouteRow(
                        name = route.name,
                        category = route.category
                    )
                }
            }
        }

        exoPlayerInfo?.let { exo ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoTile(
                    label = stringResource(R.string.presentation_batch_g_device_section_exoplayer),
                    value = exo.version,
                    supporting = stringResource(R.string.device_capabilities_renderers_count, exo.renderers),
                    icon = Icons.Rounded.Memory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun FormatCompatibilityCard(
    formats: List<FormatSupportInfo>,
    playbackCompatibility: PlaybackCompatibilitySummary?,
    modifier: Modifier = Modifier
) {
    CapabilityCard(
        title = stringResource(R.string.device_capabilities_formats_title),
        icon = Icons.Rounded.GraphicEq,
        verticalSpacing = 0.dp,
        modifier = modifier
    ) {
        val rows = formats.chunked(2)
        Spacer(
            modifier = Modifier.height(12.dp)
        )
        rows.forEachIndexed { index, rowFormats ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFormats.forEach { format ->
                    FormatSupportTile(
                        format = format,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowFormats.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (index != rows.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        playbackCompatibility?.let { compatibility ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalChip(
                    text = stringResource(
                        R.string.device_capabilities_formats_supported_count,
                        compatibility.supportedLibrarySongCount
                    ),
                    leadingIcon = Icons.Rounded.CheckCircle
                )
                if (compatibility.unknownFormatSongCount > 0) {
                    TonalChip(
                        text = stringResource(
                            R.string.device_capabilities_formats_unknown_count,
                            compatibility.unknownFormatSongCount
                        ),
                        leadingIcon = Icons.Rounded.Info
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaybackFindingsCard(
    compatibility: PlaybackCompatibilitySummary,
    modifier: Modifier = Modifier
) {
    val hasFindings = compatibility.unsupportedLibrarySongCount > 0 ||
        compatibility.unknownFormatSongCount > 0 ||
        compatibility.resampledLocalSongCount > 0

    CapabilityCard(
        title = stringResource(R.string.device_capabilities_findings_title),
        icon = if (hasFindings) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        modifier = modifier
    ) {
        if (!hasFindings) {
            FindingRow(
                icon = Icons.Rounded.CheckCircle,
                title = stringResource(R.string.device_capabilities_finding_clear_title),
                body = stringResource(R.string.device_capabilities_finding_clear_body),
                tone = FindingTone.Success
            )
            return@CapabilityCard
        }

        if (compatibility.unsupportedLibrarySongCount > 0) {
            FindingRow(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(
                    R.string.device_capabilities_finding_unsupported_title,
                    compatibility.unsupportedLibrarySongCount
                ),
                body = stringResource(
                    R.string.device_capabilities_finding_unsupported_body,
                    compatibility.unsupportedFormats.take(4).joinToString(", ")
                ),
                tone = FindingTone.Error
            )
        }

        if (compatibility.resampledLocalSongCount > 0) {
            Spacer(Modifier.height(8.dp))
            FindingRow(
                icon = Icons.Rounded.Warning,
                title = stringResource(
                    R.string.device_capabilities_finding_resample_title,
                    compatibility.resampledLocalSongCount
                ),
                body = stringResource(
                    R.string.device_capabilities_finding_resample_body,
                    compatibility.maxLocalSampleRate ?: 0
                ),
                tone = FindingTone.Warning
            )
        }

        if (compatibility.unknownFormatSongCount > 0) {
            Spacer(Modifier.height(8.dp))
            FindingRow(
                icon = Icons.Rounded.Info,
                title = stringResource(
                    R.string.device_capabilities_finding_unknown_title,
                    compatibility.unknownFormatSongCount
                ),
                body = stringResource(R.string.device_capabilities_finding_unknown_body),
                tone = FindingTone.Info
            )
        }
    }
}

@Composable
internal fun DeviceInfoPanel(
    deviceInfo: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val orderedEntries = remember(deviceInfo) { orderedDeviceInfoEntries(deviceInfo) }
    val localized = localizedDeviceInfoEntries(orderedEntries)

    CapabilityCard(
        title = stringResource(R.string.presentation_batch_g_device_info_title),
        icon = Icons.Rounded.Info,
        verticalSpacing = 0.dp,
        enableTopSpacer = true,
        modifier = modifier
    ) {
        val rows = localized.chunked(2)
        rows.forEachIndexed { index, entries ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { (label, value) ->
                    InfoTile(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (entries.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (index != rows.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
