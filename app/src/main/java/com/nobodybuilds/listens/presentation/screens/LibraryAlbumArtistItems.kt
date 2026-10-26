@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.screens

import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbType
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbAvatar
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbArtSquare
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.ui.theme.LocalNobodyListensDarkTheme
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.ShimmerBox
import com.nobodybuilds.listens.data.model.Album
import com.nobodybuilds.listens.data.model.Artist
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemePair
import com.nobodybuilds.listens.utils.formatSongCount
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.flow.StateFlow

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    // Daylight flat album card: square art (r16), title 15/600, muted meta. No tinted background.
    val artShape = NbShapes.lg
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "albumGridSelectionScale"
    )

    if (isLoading) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(artShape)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NbShapes.lg)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(selectionScale)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Nb.ink, artShape) else Modifier
                    )
            ) {
                NbArtSquare(
                    model = album.albumArtUriString,
                    title = album.title,
                    shape = artShape,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, album.title),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(color = Nb.ink, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionIndex?.toString() ?: "✓",
                            color = Nb.onInk,
                            style = NbType.label.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                album.title,
                style = NbType.title.copy(fontSize = 15.sp),
                color = Nb.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(album.artist, formatSongCount(album.songCount)).filter { it.isNotBlank() }.joinToString(" · "),
                style = NbType.caption,
                color = Nb.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ArtistListItem(artist: Artist, onClick: () -> Unit, isLoading: Boolean = false) {
    // Daylight artist row: round avatar (image or initials), name, muted song count.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(NbShapes.lg)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            ShimmerBox(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        } else {
            NbAvatar(model = artist.effectiveImageUrl, name = artist.name, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(artist.name, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSongCount(artist.songCount), style = NbType.meta, color = Nb.textMuted, maxLines = 1)
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Nb.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AlbumListItem(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsStateWithLifecycle()
    val systemIsDark = LocalNobodyListensDarkTheme.current
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 16.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.99f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "albumListSelectionBorder"
    )

    if (isLoading) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(NbShapes.lg)
                .then(if (isSelected) Modifier.background(Nb.muted) else Modifier)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NbArt(
                model = album.albumArtUriString,
                title = album.title,
                size = 56.dp,
                shape = NbShapes.md,
                contentDescription = stringResource(R.string.cd_album_art_for_title, album.title)
            )
            Column(Modifier.weight(1f)) {
                Text(album.title, style = NbType.title, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(album.artist, formatSongCount(album.songCount)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = NbType.meta,
                    color = Nb.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(color = Nb.ink, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionIndex?.toString() ?: "✓",
                        style = NbType.caption.copy(fontWeight = FontWeight.Bold),
                        color = Nb.onInk
                    )
                }
            }
        }
    }
}
