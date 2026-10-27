package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.presentation.components.AutoScrollingTextOnDemand
import com.nobodybuilds.listens.presentation.components.SmartImageCompactListTargetSize
import com.nobodybuilds.listens.presentation.components.SmartImage
import com.nobodybuilds.listens.presentation.components.subcomps.EnhancedSongListItem
import com.nobodybuilds.listens.presentation.viewmodel.GenreDetailListItem
import com.nobodybuilds.listens.presentation.viewmodel.AlbumData
import com.nobodybuilds.listens.presentation.viewmodel.StablePlayerState
import com.nobodybuilds.listens.utils.formatSongCount
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

// --- Section Extensions ---

// --- Item Composables for Flattened List ---

@Composable
fun GenreArtistHeader(
    artistName: String,
    artistImageUrl: String?
) {
    val headerShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 24.dp, smoothnessAsPercentTR = 60,
            cornerRadiusTL = 24.dp, smoothnessAsPercentTL = 60,
            cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0,
            cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0
        )
    }

    val context = LocalContext.current
    val imageRequest = remember(artistImageUrl) {
        if (!artistImageUrl.isNullOrEmpty()) {
            ImageRequest.Builder(context)
                .data(artistImageUrl)
                .crossfade(true)
                .build()
        } else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = headerShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = artistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.cd_generic_artist),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun GenreAlbumHeader(
    album: AlbumData,
    useArtistStyle: Boolean,
    onSongClick: (Song) -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    val shape = remember(useArtistStyle) {
        if (useArtistStyle) {
            RectangleShape
        } else {
             AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp, smoothnessAsPercentTR = 60,
                cornerRadiusTL = 24.dp, smoothnessAsPercentTL = 60,
                cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0,
                cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0
            )
        }
    }
    
    Box(
         modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.artUri,
                contentDescription = null,
                targetSize = SmartImageCompactListTargetSize,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val shouldScroll = album.name.length > 20
                if (shouldScroll) {
                    AutoScrollingTextOnDemand(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        gradientEdgeColor = MaterialTheme.colorScheme.surface,
                        expansionFractionProvider = { 1f },
                    )
                } else {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = formatSongCount(album.songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    if(album.songs.isNotEmpty()) onSongClick(album.songs.first())
                },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album))
            }
        }
    }
}

@Composable
fun GenreSongItemWrapper(
    item: com.nobodybuilds.listens.presentation.viewmodel.GenreDetailListItem.SongItem,
    stablePlayerState: StablePlayerState,
    onSongClick: (Song) -> Unit,
    onMoreOptionsClick: (Song) -> Unit
) {
    val song = item.song
    val isFirstInAlbum = item.isFirstInAlbum
    val isLastInAlbum = item.isLastInAlbum
    val isLastAlbumInSection = item.isLastAlbumInSection
    val useArtistStyle = item.useArtistStyle

    // Optimization: Cache shapes to avoid reallocation during scroll
    val songItemShape = remember(isFirstInAlbum, isLastInAlbum) {
        when {
            isFirstInAlbum && isLastInAlbum -> RoundedCornerShape(16.dp)
            isFirstInAlbum -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            isLastInAlbum -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            else -> RoundedCornerShape(4.dp)
        }
    }
    
    val containerShape = remember(isLastInAlbum, isLastAlbumInSection) {
        if (isLastInAlbum && isLastAlbumInSection) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTR = 0.dp, smoothnessAsPercentTR = 0,
                cornerRadiusTL = 0.dp, smoothnessAsPercentTL = 0,
                cornerRadiusBR = 24.dp, smoothnessAsPercentBR = 60,
                cornerRadiusBL = 24.dp, smoothnessAsPercentBL = 60
            ) 
        } else {
           RectangleShape
        }
    }
   
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f), containerShape)
            .padding(horizontal = 8.dp) 
            .padding(bottom = if (isLastInAlbum && !isLastAlbumInSection && useArtistStyle) 8.dp else 0.dp)
    ) {
        Column {
            if (!isFirstInAlbum) Spacer(Modifier.height(2.dp))
            
            // Optimization: De-reference stable state values to avoid observing the whole object
            val isCurrent = stablePlayerState.currentSong?.id == song.id
            val isPlaying = stablePlayerState.isPlaying

            EnhancedSongListItem(
                 song = song,
                 isPlaying = isPlaying,
                 isCurrentSong = isCurrent,
                 showAlbumArt = false,
                 customShape = songItemShape,
                 onClick = { onSongClick(song) },
                 onMoreOptionsClick = onMoreOptionsClick
             )
             
             if (isLastInAlbum) Spacer(Modifier.height(8.dp))
        }
    }
}
