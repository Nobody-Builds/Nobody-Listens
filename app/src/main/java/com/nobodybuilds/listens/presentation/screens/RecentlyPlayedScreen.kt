package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.presentation.components.MiniPlayerHeight
import com.nobodybuilds.listens.presentation.components.PlaylistBottomSheet
import com.nobodybuilds.listens.presentation.components.RecentlyPlayedRangeSelector
import com.nobodybuilds.listens.presentation.components.SongInfoBottomSheet
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.model.collectRecentlyPlayedSongIds
import com.nobodybuilds.listens.presentation.model.mapRecentlyPlayedSongs
import com.nobodybuilds.listens.presentation.viewmodel.PlaylistViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    Trace.beginSection("RecentlyPlayedScreen.Composition")

    val context = LocalContext.current
    val queueRecentlyPlayed = stringResource(R.string.presentation_batch_b_queue_recently_played)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    var selectedRange by rememberSaveable { mutableStateOf(StatsTimeRange.WEEK) }
    val lazyListState = rememberLazyListState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val bottomBarHeightDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val recentSongIds = remember(playbackHistory, selectedRange) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    val recentSongsInitialValue: List<Song>? = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) emptyList<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentSongsInitialValue)

    val recentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs, selectedRange) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    val groupedSongs = remember(recentlyPlayedSongs, selectedRange, context) {
        groupRecentlyPlayedSongs(
            context = context,
            songs = recentlyPlayedSongs,
            range = selectedRange
        )
    }
    val queueSongs = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val bgColors = listOf(
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.surface
    )

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = bgColors,
            endY = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (recentlyPlayedSourceSongs == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "recently_played_header") {
                    ExpressiveRecentlyPlayedHeader(
                        title = stringResource(R.string.presentation_batch_b_recently_played_title),
                        songs = recentlyPlayedSongs,
                        selectedRange = selectedRange,
                        scrollState = lazyListState
                    )
                }

                item(key = "recently_played_range_selector") {
                    RecentlyPlayedRangeSelector(
                        selected = selectedRange,
                        onRangeSelected = { selectedRange = it },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }

                if (recentlyPlayedSongs.isNotEmpty()) {
                    item(key = "recently_played_actions") {
                        RecentlyPlayedActions(
                            onPlay = {
                                val firstSong = queueSongs.firstOrNull() ?: return@RecentlyPlayedActions
                                playerViewModel.playSongs(queueSongs, firstSong, queueRecentlyPlayed)
                            },
                            onShuffle = {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = queueSongs,
                                    queueName = queueRecentlyPlayed,
                                    startAtZero = true,
                                )
                            },
                            shuffleLabel = shuffleLabel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (recentlyPlayedSongs.isEmpty()) {
                    item(key = "recently_played_empty") {
                        RecentlyPlayedEmptyState(
                            range = selectedRange,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    groupedSongs.forEachIndexed { groupIndex, group ->
                        item(key = "recently_played_time_${groupIndex}_${group.key}") {
                            RecentlyPlayedTimestampDivider(
                                label = group.label,
                                isHourBucket = group.isHourBucket,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(group.songs, key = { songUi -> songUi.song.id }, contentType = { "recently_played_song" }) { item ->
                            EnhancedSongListItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                song = item.song,
                                isCurrentSong = currentSongId == item.song.id,
                                isPlaying = currentSongId == item.song.id && isPlaying,
                                onClick = {
                                    playerViewModel.playSongs(
                                        songsToPlay = queueSongs,
                                        startSong = item.song,
                                        queueName = queueRecentlyPlayed
                                    )
                                },
                                onMoreOptionsClick = { song ->
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            SongInfoBottomSheet(
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(song)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = false
                },
                onPlaySong = {
                    if (queueSongs.isNotEmpty()) {
                        playerViewModel.playSongs(queueSongs, song, queueRecentlyPlayed)
                    }
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    song.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        song,
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
                    playerViewModel.generateAiMetadata(song, fields)
                },
                removeFromListTrigger = {}
            )

            if (showPlaylistBottomSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(song),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }

        FilledIconButton(
            onClick = { navController.popBackStack() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.auth_cd_back)
            )
        }
    }

    Trace.endSection()
}
