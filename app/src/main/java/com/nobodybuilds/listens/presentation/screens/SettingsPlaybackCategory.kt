package com.nobodybuilds.listens.presentation.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Playback: background play, ReplayGain, YouTube Music, Cast, headphones and queue. */
@Composable
internal fun SettingsPlaybackCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    SettingsSubsection(title = stringResource(R.string.setcat_background_playback)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_keep_playing_label),
            description = stringResource(R.string.setcat_keep_playing_desc),
            options = mapOf("true" to stringResource(R.string.label_on), "false" to stringResource(R.string.label_off)),
            selectedKey = if (uiState.keepPlayingInBackground) "true" else "false",
            onSelectionChanged = { settingsViewModel.setKeepPlayingInBackground(it.toBoolean()) },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SettingsItem(
            title = stringResource(R.string.setcat_battery_optimization_title),
            subtitle = stringResource(R.string.setcat_battery_optimization_subtitle),
            onClick = {
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    Toast.makeText(context, context.getString(R.string.toast_battery_already_disabled), Toast.LENGTH_SHORT).show()
                    return@SettingsItem
                }
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_battery_settings_unavailable), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_all_inclusive_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_replaygain_section)) {
        SwitchSettingItem(
            title = stringResource(R.string.setcat_replaygain_enable_title),
            subtitle = stringResource(R.string.setcat_replaygain_enable_subtitle),
            checked = uiState.replayGainEnabled,
            onCheckedChange = { settingsViewModel.setReplayGainEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        AnimatedVisibility(
            visible = uiState.replayGainEnabled,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(animationSpec = spring(stiffness = 400f)),
            exit = shrinkVertically(animationSpec = spring(stiffness = 500f)) + fadeOut(animationSpec = spring(stiffness = 500f))
        ) {
            ThemeSelectorItem(
                label = stringResource(R.string.setcat_gain_mode_label),
                description = stringResource(R.string.setcat_gain_mode_desc),
                options = mapOf("track" to stringResource(R.string.setcat_gain_mode_track), "album" to stringResource(R.string.setcat_gain_mode_album)),
                selectedKey = if (uiState.replayGainUseAlbumGain) "album" else "track",
                onSelectionChanged = { settingsViewModel.setReplayGainUseAlbumGain(it == "album") },
                leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
            )
        }
    }

    YouTubeMusicQualitySettings()
    JioSaavnQualitySettings()

    SettingsSubsection(title = stringResource(R.string.setcat_cast)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_cast_autoplay_label),
            description = stringResource(R.string.setcat_cast_autoplay_desc),
            options = mapOf("false" to stringResource(R.string.label_enabled), "true" to stringResource(R.string.label_disabled)),
            selectedKey = if (uiState.disableCastAutoplay) "true" else "false",
            onSelectionChanged = { settingsViewModel.setDisableCastAutoplay(it.toBoolean()) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_cast_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_headphones)) {
        SwitchSettingItem(
            title = stringResource(R.string.setcat_headphones_resume_title),
            subtitle = stringResource(R.string.setcat_headphones_resume_subtitle),
            checked = uiState.resumeOnHeadsetReconnect,
            onCheckedChange = { settingsViewModel.setResumeOnHeadsetReconnect(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_headphones_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_queue_transitions)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_crossfade_label),
            description = stringResource(R.string.setcat_crossfade_desc),
            options = mapOf("true" to stringResource(R.string.label_enabled), "false" to stringResource(R.string.label_disabled)),
            selectedKey = if (uiState.isCrossfadeEnabled) "true" else "false",
            onSelectionChanged = { settingsViewModel.setCrossfadeEnabled(it.toBoolean()) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_align_justify_space_even_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        if (uiState.isCrossfadeEnabled) {
            SliderSettingsItem(
                label = stringResource(R.string.setcat_crossfade_duration),
                value = uiState.crossfadeDuration.toFloat(),
                valueRange = 1000f..12000f,
                steps= 10,
                onValueChange = { settingsViewModel.setCrossfadeDuration(it.toInt()) },
                valueText = { value -> "${(value / 1000).toInt()}s" }
            )
        }
        SwitchSettingItem(
            title = stringResource(R.string.setcat_hifi_mode_title),
            subtitle = if (uiState.hiFiModeDeviceSupported)
                stringResource(R.string.setcat_hifi_mode_subtitle_supported)
            else
                stringResource(R.string.setcat_hifi_mode_subtitle_unsupported),
            checked = uiState.hiFiModeEnabled,
            onCheckedChange = { settingsViewModel.setHiFiModeEnabled(it) },
            enabled = uiState.hiFiModeDeviceSupported,
            leadingIcon = { Icon(painterResource(R.drawable.rounded_high_quality_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_persistent_shuffle_title),
            subtitle = stringResource(R.string.setcat_persistent_shuffle_subtitle),
            checked = uiState.persistentShuffleEnabled,
            onCheckedChange = { settingsViewModel.setPersistentShuffleEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        com.nobodybuilds.listens.presentation.playback.SmartShuffleSetting()
        SwitchSettingItem(
            title = stringResource(R.string.setcat_show_queue_history_title),
            subtitle = stringResource(R.string.setcat_show_queue_history_subtitle),
            checked = uiState.showQueueHistory,
            onCheckedChange = { settingsViewModel.setShowQueueHistory(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_queue_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
}

@Composable
private fun YouTubeMusicQualitySettings(
    viewModel: com.nobodybuilds.listens.presentation.ytmusic.YouTubeMusicSettingsViewModel = hiltViewModel(),
) {
    val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestionsEnabled.collectAsStateWithLifecycle()
    val options = com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAudioQuality.entries.associate { it.name to it.label }
    fun quality(name: String) = com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAudioQuality.valueOf(name)
    SettingsSubsection(title = "YouTube Music") {
        ThemeSelectorItem(
            label = "Streaming quality",
            description = "High plays the best audio YouTube offers. Normal and Data saver use less data.",
            options = options,
            selectedKey = streamQuality.name,
            onSelectionChanged = { viewModel.setStreamQuality(quality(it)) },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        ThemeSelectorItem(
            label = "Download quality",
            description = "Downloads are saved as tagged Opus files in Music/Nobody Listens/YouTube Music.",
            options = options,
            selectedKey = downloadQuality.name,
            onSelectionChanged = { viewModel.setDownloadQuality(quality(it)) },
            leadingIcon = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = "Search suggestions",
            subtitle = "Suggest searches from YouTube as you type. Each keystroke is sent to YouTube.",
            checked = searchSuggestions,
            onCheckedChange = viewModel::setSearchSuggestionsEnabled,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
}

@Composable
private fun JioSaavnQualitySettings(
    viewModel: com.nobodybuilds.listens.presentation.jiosaavn.JioSaavnSettingsViewModel = hiltViewModel(),
) {
    val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val options = com.nobodybuilds.listens.data.jiosaavn.JioSaavnAudioQuality.entries.associate { it.name to "${it.label} · ${it.kbps} kbps" }
    fun quality(name: String) = com.nobodybuilds.listens.data.jiosaavn.JioSaavnAudioQuality.valueOf(name)
    SettingsSubsection(title = "JioSaavn") {
        ThemeSelectorItem(
            label = "Streaming quality",
            description = "JioSaavn decides what each account gets: 320 kbps is a Pro feature. The player shows the bitrate you got.",
            options = options,
            selectedKey = streamQuality.name,
            onSelectionChanged = { viewModel.setStreamQuality(quality(it)) },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        ThemeSelectorItem(
            label = "Download quality",
            description = "Downloads are saved as tagged .m4a files in Music/Nobody Listens/JioSaavn.",
            options = options,
            selectedKey = downloadQuality.name,
            onSelectionChanged = { viewModel.setDownloadQuality(quality(it)) },
            leadingIcon = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }
}
