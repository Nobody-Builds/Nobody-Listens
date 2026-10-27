@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewModule
import com.nobodybuilds.listens.presentation.components.ToggleSegmentButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.SortOption
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistUiState
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.data.model.LibraryTabId
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import kotlinx.coroutines.flow.first
import com.nobodybuilds.listens.presentation.components.LibrarySortBottomSheet

private const val ENABLE_FOLDERS_SOURCE_TOGGLE = true

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryTabSortSheet(
    sanitizedSortOptions: List<SortOption>,
    currentSelectedSortOption: SortOption?,
    currentTabId: LibraryTabId,
    playerUiState: LibraryScreenPlayerProjection,
    playlistUiState: PlaylistUiState,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    onSortOptionChanged: (SortOption) -> Unit
) {
    val currentSelectionKey = currentSelectedSortOption?.storageKey
    val selectedOptionForSheet = sanitizedSortOptions.firstOrNull { option ->
        option.storageKey == currentSelectionKey
    }
        ?: sanitizedSortOptions.firstOrNull { option ->
            option.storageKey == currentTabId.defaultSort.storageKey
        }
        ?: sanitizedSortOptions.first()


    val isAlbumTab = currentTabId == LibraryTabId.ALBUMS
    val isFoldersTab = currentTabId == LibraryTabId.FOLDERS
    val isPlaylistsTab = currentTabId == LibraryTabId.PLAYLISTS

    LibrarySortBottomSheet(
        title = stringResource(R.string.presentation_batch_d_sort_by),
        options = sanitizedSortOptions,
        selectedOption = selectedOptionForSheet,
        onDismiss = { playerViewModel.hideSortingSheet() },
        onOptionSelected = { option ->
            onSortOptionChanged(option)
            playerViewModel.hideSortingSheet()
        },
        onDirectionToggle = { option ->
            onSortOptionChanged(option)
        },
        showViewToggle = isFoldersTab || isPlaylistsTab,
        viewSectionTitle = if (isPlaylistsTab) {
            stringResource(R.string.presentation_batch_d_view_section_cloud)
        } else {
            stringResource(R.string.presentation_batch_d_view_section_view)
        },
        viewToggleLabel = if (isPlaylistsTab) {
            stringResource(R.string.presentation_batch_d_telegram_cloud_channels)
        } else {
            stringResource(R.string.presentation_batch_d_playlist_view)
        },
        viewToggleChecked = if (isPlaylistsTab) {
            playlistUiState.showTelegramCloudPlaylists
        } else {
            playerUiState.isFoldersPlaylistView
        },
        onViewToggleChange = { isChecked ->
            if (isPlaylistsTab) {
                playlistViewModel.setShowTelegramCloudPlaylists(isChecked)
            } else {
                playerViewModel.setFoldersPlaylistView(isChecked)
            }
        },
        viewToggleContent = if (isAlbumTab) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isList = playerUiState.isAlbumsListView
                    val primaryColor = MaterialTheme.colorScheme.tertiaryContainer
                    val onPrimaryColor = MaterialTheme.colorScheme.onTertiaryContainer
                    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

                    // Grid Item
                    ToggleSegmentButton(
                        modifier = Modifier.weight(1f),
                        active = !isList,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeCornerRadius = 32.dp,
                        onClick = { playerViewModel.setAlbumsListView(false) },
                        text = stringResource(R.string.presentation_batch_d_view_grid),
                        imageVector = Icons.Rounded.ViewModule
                    )

                    // List Item
                    ToggleSegmentButton(
                        modifier = Modifier.weight(1f),
                        active = isList,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeCornerRadius = 32.dp,
                        onClick = { playerViewModel.setAlbumsListView(true) },
                        text = stringResource(R.string.presentation_batch_d_view_list),
                        imageVector = Icons.AutoMirrored.Rounded.ViewList
                    )
                }
            }
        } else null,
        sourceToggleContent = if (isFoldersTab && ENABLE_FOLDERS_SOURCE_TOGGLE) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSdAvailable = playerUiState.isSdCardAvailable
                    ToggleSegmentButton(
                        modifier = Modifier.weight(1f),
                        active = playerUiState.folderSource == FolderSource.INTERNAL,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeCornerRadius = 32.dp,
                        onClick = { playerViewModel.setFoldersSource(FolderSource.INTERNAL) },
                        text = stringResource(R.string.presentation_batch_d_storage_internal)
                    )
                    ToggleSegmentButton(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (isSdAvailable) 1f else 0.5f),
                        active = playerUiState.folderSource == FolderSource.SD_CARD,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeCornerRadius = 32.dp,
                        onClick = {
                            if (isSdAvailable) {
                                playerViewModel.setFoldersSource(FolderSource.SD_CARD)
                            }
                        },
                        text = stringResource(R.string.presentation_batch_d_storage_sd_card)
                    )
                }
                if (!playerUiState.isSdCardAvailable) {
                    Text(
                        text = stringResource(R.string.presentation_batch_d_sd_card_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                    )
                }
            }
        } else null,
        extraContent = {
            if (isPlaylistsTab && playlistUiState.showTelegramCloudPlaylists) {
                Text(
                    text = stringResource(R.string.presentation_batch_d_topics_display),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = com.nobodybuilds.listens.ui.theme.GoogleSansRounded,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_ONLY to stringResource(R.string.presentation_batch_d_topic_mode_channels),
                        com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.TOPICS_ONLY to stringResource(R.string.presentation_batch_d_topic_mode_topics),
                        com.nobodybuilds.listens.data.preferences.TelegramTopicDisplayMode.CHANNELS_AND_TOPICS to stringResource(R.string.presentation_batch_d_topic_mode_both)
                    ).forEach { (mode, label) ->
                        ToggleSegmentButton(
                            modifier = Modifier.weight(1f),
                            active = playlistUiState.telegramTopicDisplayMode == mode,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeCornerRadius = 32.dp,
                            onClick = { playlistViewModel.setTelegramTopicDisplayMode(mode) },
                            text = label
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.presentation_batch_d_cloud_sources_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = com.nobodybuilds.listens.ui.theme.GoogleSansRounded,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )
            com.nobodybuilds.listens.presentation.components.LibrarySheetToggleCard(
                label = stringResource(R.string.presentation_batch_d_cloud_only),
                checked = playerUiState.hideLocalMedia,
                boxBackgroundColor = if (playerUiState.hideLocalMedia)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.surfaceContainerLow,
                boxCornerRadius = if (playerUiState.hideLocalMedia) 18.dp else 50.dp,
                onCheckedChange = { playerViewModel.setHideLocalMedia(it) }
            )
        }
    )
}
