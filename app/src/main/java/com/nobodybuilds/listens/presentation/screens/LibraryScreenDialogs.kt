@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import com.nobodybuilds.listens.presentation.navigation.navigateSafelyReplacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.components.ReorderTabsSheet
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.components.MultiSelectionBottomSheet
import com.nobodybuilds.listens.presentation.components.AlbumMultiSelectionOptionSheet
import com.nobodybuilds.listens.presentation.components.PlaylistMultiSelectionBottomSheet
import com.nobodybuilds.listens.presentation.components.PlaylistCreationTypeDialog
import com.nobodybuilds.listens.presentation.components.CreateAiPlaylistDialog
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.SongInfoBottomSheetViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import com.nobodybuilds.listens.presentation.screens.CreatePlaylistDialog
import com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet
import android.widget.Toast
import com.nobodybuilds.listens.data.service.wear.PhoneWatchTransferState
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import com.nobodybuilds.listens.data.model.Playlist

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun LibraryScreenDialogs(
    screenState: LibraryScreenState,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    songInfoBottomSheetViewModel: SongInfoBottomSheetViewModel,
    hasActiveAiProviderApiKey: Boolean,
    isGeneratingAiPlaylist: Boolean,
    aiError: String?,
    activeWatchTransfer: PhoneWatchTransferState?,
    selectedSongForInfo: Song?,
    favoriteIds: Set<String>,
    bottomBarHeightDp: Dp,
    selectedSongs: List<Song>,
    selectedPlaylists: List<Playlist>,
    selectedPlaylistIds: Set<String>,
    tabTitles: List<String>,
    currentTabIndex: Int,
    pagerState: PagerState,
    scope: CoroutineScope,
    isCompactNavigation: Boolean
) {
    val context = LocalContext.current
    val playlistMultiSelectionState = playerViewModel.playlistSelectionStateHolder
    var showSongInfoBottomSheet by screenState.showSongInfoBottomSheet
    var showPlaylistBottomSheet by screenState.showPlaylistBottomSheet
    var playlistSheetSongs by screenState.playlistSheetSongs
    var showWatchTransferDialog by screenState.showWatchTransferDialog
    var showCreatePlaylistDialog by screenState.showCreatePlaylistDialog
    var showPlaylistCreationTypeDialog by screenState.showPlaylistCreationTypeDialog
    var showCreateAiPlaylistDialog by screenState.showCreateAiPlaylistDialog
    var aiGenerationRequestedFromDialog by screenState.aiGenerationRequestedFromDialog
    var showReorderTabsSheet by screenState.showReorderTabsSheet
    var showTabSwitcherSheet by screenState.showTabSwitcherSheet
    var showMultiSelectionSheet by screenState.showMultiSelectionSheet
    var selectedAlbums by screenState.selectedAlbums
    var showAlbumMultiSelectionSheet by screenState.showAlbumMultiSelectionSheet
    var showPlaylistMultiSelectionSheet by screenState.showPlaylistMultiSelectionSheet
    var showMergePlaylistDialog by screenState.showMergePlaylistDialog
    var pendingMergePlaylistIds by screenState.pendingMergePlaylistIds
    PlaylistCreationTypeDialog(
        visible = showPlaylistCreationTypeDialog,
        onDismiss = { showPlaylistCreationTypeDialog = false },
        onManualSelected = {
            showPlaylistCreationTypeDialog = false
            showCreatePlaylistDialog = true
        },
        onAiSelected = {
            if (hasActiveAiProviderApiKey) {
                showPlaylistCreationTypeDialog = false
                playerViewModel.clearAiPlaylistError()
                showCreateAiPlaylistDialog = true
            } else {
                Toast.makeText(context, context.getString(R.string.toast_set_ai_provider_api_key_first), Toast.LENGTH_SHORT).show()
            }
        },
        isAiEnabled = hasActiveAiProviderApiKey,
        onSetupAiClick = {
            navController.navigateSafely(Screen.SettingsCategory.createRoute("ai"))
        }
    )

    CreatePlaylistDialog(
        visible = showCreatePlaylistDialog,
        onDismiss = { showCreatePlaylistDialog = false },
        onGenerateClick = {
            showCreatePlaylistDialog = false
            if (hasActiveAiProviderApiKey) {
                playerViewModel.clearAiPlaylistError()
                showCreateAiPlaylistDialog = true
            } else {
                Toast.makeText(context, context.getString(R.string.toast_set_gemini_api_key_first), Toast.LENGTH_SHORT).show()
            }
        },
        onCreate = { name, imageUri, color, icon, songIds, cropScale, cropPanX, cropPanY, shapeType, d1, d2, d3, d4, smartRuleKey ->
            playlistViewModel.createPlaylist(
                name = name,
                coverImageUri = imageUri,
                coverColor = color,
                coverIcon = icon,
                songIds = songIds,
                cropScale = cropScale,
                cropPanX = cropPanX,
                cropPanY = cropPanY,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverShapeType = shapeType,
                coverShapeDetail1 = d1,
                coverShapeDetail2 = d2,
                coverShapeDetail3 = d3,
                coverShapeDetail4 = d4,
                smartRuleKey = smartRuleKey
            )
        }
    )

    CreateAiPlaylistDialog(
        visible = showCreateAiPlaylistDialog && hasActiveAiProviderApiKey,
        isGenerating = isGeneratingAiPlaylist,
        error = aiError,
        onDismiss = {
            showCreateAiPlaylistDialog = false
            aiGenerationRequestedFromDialog = false
            playerViewModel.clearAiPlaylistError()
        },
        onGenerate = { playlistName, prompt, minLength, maxLength ->
            aiGenerationRequestedFromDialog = true
            playerViewModel.generateAiPlaylist(
                prompt = prompt,
                minLength = minLength,
                maxLength = maxLength,
                saveAsPlaylist = true,
                playlistName = playlistName
            )
        }
    )

    if (showWatchTransferDialog && activeWatchTransfer != null) {
        val currentWatchTransfer = activeWatchTransfer!!
        WatchTransferProgressDialog(
            transfer = currentWatchTransfer,
            onDismiss = { showWatchTransferDialog = false },
            onCancelTransfer = {
                songInfoBottomSheetViewModel.cancelWatchTransfer(currentWatchTransfer.requestId)
                showWatchTransferDialog = false
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) { derivedStateOf { currentSong?.let {
            favoriteIds.contains(
                it.id)
        } } }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_added_to_queue))
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(context.getString(R.string.toast_playing_next))
                },
                onAddToPlayList = {
                    playlistSheetSongs = listOf(currentSong)
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
                removeFromListTrigger = {},
                songInfoViewModel = songInfoBottomSheetViewModel
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = { showPlaylistBottomSheet = false },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
        )
    }

    // Multi-Selection Bottom Sheet
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? android.app.Activity

        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteIds,
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                }
            }
        )
    }

    // Album Multi-Selection Option Sheet
    if (showAlbumMultiSelectionSheet && selectedAlbums.isNotEmpty()) {
        AlbumMultiSelectionOptionSheet(
            selectedAlbums = selectedAlbums,
            maxSelection = MAX_ALBUM_MULTI_SELECTION,
            onDismiss = { showAlbumMultiSelectionSheet = false },
            onPlay = {
                playerViewModel.playSelectedAlbums(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAlbumsAsNext(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedAlbumsToQueue(selectedAlbums)
                selectedAlbums = emptyList()
                showAlbumMultiSelectionSheet = false
            }
        )
    }

    // Playlist Multi-Selection Bottom Sheet
    if (showPlaylistMultiSelectionSheet && selectedPlaylists.isNotEmpty()) {
        val activity = context as? android.app.Activity

        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylists,
            onDismiss = {
                showPlaylistMultiSelectionSheet = false
            },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistIds.toList())
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            },
            onMergeAll = {
                pendingMergePlaylistIds = selectedPlaylistIds.toList()
                showMergePlaylistDialog = true
                showPlaylistMultiSelectionSheet = false
            },
            onShareAll = {
                activity?.let {
                    playlistViewModel.shareSelectedPlaylistsAsZip(selectedPlaylistIds.toList(), it)
                }
                showPlaylistMultiSelectionSheet = false
                playlistMultiSelectionState.clearSelection()
            }
        )
    }

    if (showTabSwitcherSheet) {
        LibraryTabSwitcherSheet(
            tabs = tabTitles,
            currentIndex = currentTabIndex,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        targetPageForTabIndex(
                            currentPage = pagerState.currentPage,
                            targetTabIndex = index,
                            tabCount = tabTitles.size,
                            compactMode = isCompactNavigation
                        )
                    )
                }
                showTabSwitcherSheet = false
            },
            onEditClick = {
                showTabSwitcherSheet = false
                showReorderTabsSheet = true
            },
            onDismiss = { showTabSwitcherSheet = false }
        )
    }

    if (showReorderTabsSheet) {
        ReorderTabsSheet(
            tabs = tabTitles,
            onReorder = { newOrder ->
                playerViewModel.saveLibraryTabsOrder(newOrder)
            },
            onReset = {
                playerViewModel.resetLibraryTabsOrder()
            },
            onDismiss = { showReorderTabsSheet = false }
        )
    }

    // Merge Playlists Dialog
    if (showMergePlaylistDialog && pendingMergePlaylistIds.isNotEmpty()) {
        var mergePlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showMergePlaylistDialog = false
                pendingMergePlaylistIds = emptyList()
                mergePlaylistName = ""
            },
            title = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.presentation_batch_d_merge_playlists_prompt))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mergePlaylistName,
                        onValueChange = { mergePlaylistName = it },
                        placeholder = { Text(stringResource(R.string.presentation_batch_d_merge_playlists_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.presentation_batch_d_merge_playlists_body,
                            pendingMergePlaylistIds.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mergePlaylistName.isNotEmpty()) {
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
                            playlistViewModel.mergePlaylistsIntoOne(
                                pendingMergePlaylistIds,
                                mergePlaylistName
                            )
                            playlistMultiSelectionState.clearSelection()
                            showMergePlaylistDialog = false
                            pendingMergePlaylistIds = emptyList()
                            mergePlaylistName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMergePlaylistDialog = false
                    pendingMergePlaylistIds = emptyList()
                    mergePlaylistName = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
