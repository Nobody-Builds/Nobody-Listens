package com.nobodybuilds.listens.presentation.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.theme.LocalWearPalette
import com.nobodybuilds.listens.presentation.theme.radialBackgroundBrush
import com.nobodybuilds.listens.presentation.theme.rememberWearScreen
import com.nobodybuilds.listens.shared.WearPlayerState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The song's artwork behind the controls, like Spotify on Galaxy Watch: blurred (API 31+; older watches get it
 * unblurred) and darkened so Paper text and controls stay readable on any cover. The scrim keeps even a white cover
 * at about 6:1 against Paper text, and the edges fall off to black for the bezel.
 */
@Composable
internal fun PlayerArtworkBackdrop(albumArt: Bitmap) {
    val palette = LocalWearPalette.current
    val image = remember(albumArt) { albumArt.asImageBitmap() }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(18.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surfaceContainerLowest.copy(alpha = 0.70f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.70f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun AlbumArtPage(
    state: WearPlayerState,
    albumArt: Bitmap?,
    onTap: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val textColors = remember(albumArt, palette.textPrimary, palette.textSecondary) {
        deriveAlbumOverlayTextColors(
            albumArt = albumArt,
            fallbackPrimary = palette.textPrimary,
            fallbackSecondary = palette.textSecondary,
        )
    }
    val contrastOverlay = remember(albumArt, textColors) {
        deriveAlbumContrastOverlay(
            albumArt = albumArt,
            textColors = textColors,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val fallbackBrush = remember(palette) { palette.radialBackgroundBrush() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            0.94f to Color.Black.copy(alpha = 0.52f),
                            1f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.32f),
                            0.14f to Color.Black.copy(alpha = 0.08f),
                            0.24f to Color.Transparent,
                            0.68f to Color.Transparent,
                            0.86f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to contrastOverlay.topColor.copy(alpha = contrastOverlay.topAlpha),
                            contrastOverlay.topFadeEnd to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            contrastOverlay.bottomFadeStart to Color.Transparent,
                            1f to contrastOverlay.bottomColor.copy(alpha = contrastOverlay.bottomAlpha),
                        ),
                    ),
                ),
        )

        LargeAlbumClockText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .zIndex(5f),
            color = textColors.clock,
            shadow = textColors.clockShadow,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = rememberWearScreen().contentInset + 8.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.songTitle.ifEmpty { "Nothing playing" },
                style = MaterialTheme.typography.title2.copy(
                    shadow = textColors.bottomShadow,
                ),
                fontWeight = FontWeight.Bold,
                color = textColors.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.artistName.ifEmpty { "Connect phone playback" },
                style = MaterialTheme.typography.body1.copy(
                    shadow = textColors.bottomShadow,
                ),
                color = textColors.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun LargeAlbumClockText(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shadow: Shadow? = null,
) {
    val displayTime by produceState(initialValue = "--:--") {
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        while (true) {
            val now = LocalTime.now()
            value = now.format(formatter)
            // Sleep until the next minute boundary so we never recompose mid-minute.
            val secondsToNextMinute = (60 - now.second).coerceAtLeast(1)
            delay(secondsToNextMinute * 1000L)
        }
    }
    val gSansFlex = remember {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(650),
                    FontVariation.width(146f),
                    FontVariation.Setting("ROND", 56f),
                    FontVariation.Setting("XTRA", 520f),
                    FontVariation.Setting("YOPQ", 90f),
                    FontVariation.Setting("YTLC", 505f),
                ),
            ),
        )
    }

    Text(
        text = displayTime,
        color = color,
        fontFamily = gSansFlex,
        fontWeight = FontWeight(760),
        fontSize = 26.sp,
        lineHeight = 26.sp,
        style = if (shadow != null) TextStyle(shadow = shadow) else TextStyle.Default,
        modifier = modifier,
    )
}
