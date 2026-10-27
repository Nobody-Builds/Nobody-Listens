package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.worker.SyncProgress
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel

/** Settings > Library: folders, filtering, sync and lyrics management. */
@Composable
internal fun SettingsLibraryCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    isSyncing: Boolean,
    syncProgress: SyncProgress,
    syncIndicatorLabel: String?,
    onOpenExplorer: () -> Unit,
    onFullSyncRequest: () -> Unit,
    onRebuildRequest: () -> Unit,
    onClearLyricsRequest: () -> Unit
) {
    var minSongDurationDraft by remember(uiState.minSongDuration) {
        mutableStateOf(uiState.minSongDuration.toFloat())
    }
    var minTracksPerAlbumDraft by remember(uiState.minTracksPerAlbum) {
        mutableStateOf(uiState.minTracksPerAlbum.toFloat())
    }
    var albumArtCacheLimitDraft by remember(uiState.albumArtCacheLimitMb) {
        mutableStateOf(uiState.albumArtCacheLimitMb.toFloat())
    }

    SettingsSubsection(title = stringResource(R.string.setcat_library_structure)) {
        com.nobodybuilds.listens.presentation.folders.MusicFoldersSettingsItem()
        SettingsItem(
            title = "Excluded subfolders",
            subtitle = "Skip folders inside your music folders",
            leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = onOpenExplorer
        )
        SettingsItem(
            title = stringResource(R.string.setcat_artists_title),
            subtitle = stringResource(R.string.setcat_artists_subtitle),
            leadingIcon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.ArtistSettings.route) }
        )
        SettingsItem(
            title = stringResource(R.string.health_title),
            subtitle = stringResource(R.string.health_settings_subtitle),
            leadingIcon = { Icon(Icons.Rounded.HealthAndSafety, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.LibraryHealth.route) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_filtering)) {
        SliderSettingsItem(
            label = stringResource(R.string.setcat_min_song_duration),
            value = minSongDurationDraft,
            valueRange = 0f..120000f,
            steps = 23, // 0, 5, 10, 15, ... 120 seconds (24 positions, 23 steps)
            onValueChange = { minSongDurationDraft = it },
            onValueChangeFinished = {
                val selectedDuration = minSongDurationDraft.toInt()
                if (selectedDuration != uiState.minSongDuration) {
                    settingsViewModel.setMinSongDuration(selectedDuration)
                }
            },
            valueText = { value -> "${(value / 1000).toInt()}s" }
        )
        SliderSettingsItem(
            label = stringResource(R.string.setcat_min_tracks_per_album),
            value = minTracksPerAlbumDraft,
            valueRange = 1f..5f,
            steps = 3, // 1, 2, 3, 4, 5
            onValueChange = { minTracksPerAlbumDraft = it },
            onValueChangeFinished = {
                val selectedTracks = minTracksPerAlbumDraft.toInt()
                if (selectedTracks != uiState.minTracksPerAlbum) {
                    settingsViewModel.setMinTracksPerAlbum(selectedTracks)
                }
            },
            valueText = { value -> "${value.toInt()}" }
        )
        SliderSettingsItem(
            label = stringResource(R.string.setcat_album_art_cache_limit),
            value = albumArtCacheLimitDraft,
            valueRange = 50f..1500f,
            steps = 28, // 50, 100, 150, ... 1500 (30 stops)
            onValueChange = { albumArtCacheLimitDraft = it },
            onValueChangeFinished = {
                val selectedLimit = albumArtCacheLimitDraft.toInt()
                if (selectedLimit != uiState.albumArtCacheLimitMb) {
                    settingsViewModel.setAlbumArtCacheLimitMb(selectedLimit)
                }
            },
            valueText = { value -> "${value.toInt()} MB" }
        )
    }

    SettingsSubsection(title = stringResource(R.string.setcat_sync_scanning)) {
        RefreshLibraryItem(
            isSyncing = isSyncing,
            syncProgress = syncProgress,
            activeOperationLabel = if (isSyncing) syncIndicatorLabel else null,
            onFullSync = {
                if (isSyncing) return@RefreshLibraryItem
                onFullSyncRequest()
            },
            onRebuild = {
                if (isSyncing) return@RefreshLibraryItem
                onRebuildRequest()
            }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_auto_scan_lrc_title),
            subtitle = stringResource(R.string.setcat_auto_scan_lrc_subtitle),
            checked = uiState.autoScanLrcFiles,
            onCheckedChange = { settingsViewModel.setAutoScanLrcFiles(it) },
            leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(
        title = stringResource(R.string.setcat_lyrics_management),
        addBottomSpace = false
    ) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_lyrics_source_priority_label),
            description = stringResource(R.string.setcat_lyrics_source_priority_desc),
            options = mapOf(
                LyricsSourcePreference.EMBEDDED_FIRST.name to stringResource(R.string.setcat_lyrics_embedded_first),
                LyricsSourcePreference.API_FIRST.name to stringResource(R.string.setcat_lyrics_online_first),
                LyricsSourcePreference.LOCAL_FIRST.name to stringResource(R.string.setcat_lyrics_local_first)
            ),
            selectedKey = uiState.lyricsSourcePreference.name,
            onSelectionChanged = { key ->
                settingsViewModel.setLyricsSourcePreference(
                    LyricsSourcePreference.fromName(key)
                )
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SettingsItem(
            title = stringResource(R.string.setcat_reset_imported_lyrics_title),
            subtitle = stringResource(R.string.setcat_reset_imported_lyrics_subtitle),
            leadingIcon = { Icon(Icons.Rounded.ClearAll, null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = onClearLyricsRequest
        )
    }
}
