package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.presentation.model.RecentlyPlayedSongUiModel
import kotlinx.coroutines.flow.map
import androidx.compose.ui.res.stringResource

@Composable
internal fun ExpressiveRecentlyPlayedHeader(
    title: String,
    songs: List<RecentlyPlayedSongUiModel>,
    selectedRange: StatsTimeRange,
    scrollState: LazyListState
) {
    val highlightedArt = remember(songs) { songs.take(4).map { it.song.albumArtUriString } }
    val parallaxOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.36f else 0f
        }
    }
    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 520f)).coerceIn(0f, 1f)
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val titleStyle = rememberRecentlyPlayedTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            secondary.copy(alpha = 0.24f),
                            primary.copy(alpha = 0.10f),
                            surface.copy(alpha = 0.95f),
                            surface
                        ),
                        endY = 780f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
//            Surface(
//                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
//                shape = RoundedCornerShape(50),
//                tonalElevation = 0.dp
//            ) {
//                Text(
//                    text = "LIVE HISTORY · ${selectedRange.displayName.uppercase()}",
//                    style = MaterialTheme.typography.labelMedium,
//                    fontWeight = FontWeight.SemiBold,
//                    color = MaterialTheme.colorScheme.onSecondaryContainer,
//                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
//                )
//            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberRecentlyPlayedTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(560),
                        FontVariation.width(122f),
                        FontVariation.grade(40),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(560),
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.4).sp
        )
    }
}

@Composable
internal fun RecentlyPlayedActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    shuffleLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 52.dp,
                topEnd = 14.dp,
                bottomStart = 52.dp,
                bottomEnd = 14.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.presentation_batch_b_play_latest))
        }

        FilledTonalButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 52.dp,
                bottomStart = 14.dp,
                bottomEnd = 52.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(shuffleLabel)
        }
    }
}
