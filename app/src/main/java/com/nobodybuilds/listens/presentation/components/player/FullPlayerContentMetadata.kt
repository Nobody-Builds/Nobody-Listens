package com.nobodybuilds.listens.presentation.components.player

import com.nobodybuilds.listens.data.model.Lyrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Artist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.AutoScrollingTextOnDemand
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                title = currentSong.title,
                artist = currentSong.displayArtist,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                gradientEdgeColor = gradientEdgeColor,
                playerViewModel = playerViewModel,
                onClickArtist = onClickArtist,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        }
        
        val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
        val isBuffering = stablePlayerState.isBuffering


        AnimatedVisibility(
            visible = isBuffering,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 400,
                    delayMillis = 80,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = 80
                )
            ),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200
                )
            )
        ) {
            Surface(
                shape = CircleShape,
                color = chipColor,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = chipContentColor
                    )
                }
            }
        }

        // YouTube songs only: save a tagged copy to Music/Nobody Listens/YouTube Music.
        song?.let { currentSong ->
            com.nobodybuilds.listens.presentation.ytmusic.YouTubeMusicDownloadButton(
                song = currentSong,
                containerColor = chipColor,
                contentColor = chipContentColor,
            )
        }

        if (showQueueButton) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(height = 42.dp, width = 50.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 50.dp,
                                topEnd = 6.dp,
                                bottomStart = 50.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .background(chipColor)
                        .clickable { onClickLyrics() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_lyrics_24),
                        contentDescription = stringResource(R.string.presentation_batch_g_player_cd_lyrics),
                        tint = chipContentColor
                    )
                }
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
                        .background(chipColor)
                        .clickable { onClickQueue() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_queue_music_24),
                        contentDescription = stringResource(R.string.presentation_batch_g_player_cd_queue),
                        tint = chipContentColor
                    )
                }
            }
        } else {
            // Portrait Mode: Just the Lyrics button (Queue is in TopBar)
            FilledIconButton(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = chipColor,
                    contentColor = chipContentColor
                ),
                onClick = onClickLyrics,
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_lyrics_24),
                    contentDescription = stringResource(R.string.presentation_batch_g_player_cd_lyrics)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val resolvedArtistId by remember(artists, artistId) {
        derivedStateOf { artists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: artistId }
    }
    // Daylight now-playing title: Gilroy ExtraBold, artist in Google Sans Flex.
    val titleStyle = com.nobodybuilds.listens.presentation.nb.NbType.h1.copy(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        color = textColor
    )

    val artistStyle = com.nobodybuilds.listens.presentation.nb.NbType.bodyLarge.copy(
        letterSpacing = 0.sp,
        color = artistTextColor
    )

    Column(
        horizontalAlignment = Alignment.Start,
            modifier = modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction // Or apply specific fade logic if desired
                translationY = (1f - fraction) * 24f
            }
    ) {
        // We pass 1f to AutoScrollingTextOnDemand because the alpha/translation is now handled by the parent Column graphicsLayer
        // and we want it "fully rendered" but hidden/moved by the layer.
        // Actually, AutoScrollingTextOnDemand uses expansionFraction to start scrolling only when fully expanded?
        // Let's check AutoScrollingTextOnDemand. Assuming it uses it for scrolling trigger.
        // If we want to avoid recomposition, we might need to pass the provider or just 1f if scrolling logic handles itself.
        // For now, let's pass the current value from provider for logic correctness, but ideally this component should be optimized too.
        AutoScrollingTextOnDemand(
            title,
            titleStyle,
            gradientEdgeColor,
            expansionFractionProvider,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))



        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },

                onLongClick = {
                    if (isNavigatingToArtist) return@combinedClickable
                    coroutineScope.launch {
                        isNavigatingToArtist = true
                        try {
                            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
                        } finally {
                            isNavigatingToArtist = false
                        }
                    }
                }
            )
        )
    }
}
