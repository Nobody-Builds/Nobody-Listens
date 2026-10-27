package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nobodybuilds.listens.R
import androidx.glance.unit.ColorProvider

@Composable
fun VeryThinWidgetLayout(
    modifier: GlanceModifier,
    title: String,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    artist: String,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean,
    textColor: ColorProvider,
    context: Context
) {
    val secondaryColor = GlanceTheme.colors.secondaryContainer
    val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
    val size = LocalSize.current
    val albumArtSize = size.height - 32.dp

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp) // Padding applied to the outer box
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(bgCornerRadius),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {

            AlbumArtImageGlance(
                modifier = GlanceModifier
                    .size(albumArtSize),
                bitmapData = albumArtBitmapData,
                albumArtUri = albumArtUri,
                context = context,
                cornerRadius = 12.dp
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != context.getString(R.string.widget_tap_to_open)) {
                    Text(text = artist, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            PlayPauseButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .size(48.dp, 48.dp)
                    .fillMaxHeight(),
                backgroundColor = primaryContainerColor,
                iconColor = onPrimaryContainerColor,
                isPlaying = isPlaying,
                iconSize = 26.dp,
                cornerRadius = 60.dp
            )
            Spacer(GlanceModifier.width(10.dp))
            NextButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .size(48.dp, 48.dp)
                    .fillMaxHeight(),
                iconColor = onSecondaryColor,
                iconSize = 26.dp,
                backgroundColor = secondaryColor,
                cornerRadius = 60.dp
            )
        }
    }
}


@Composable
fun ThinWidgetLayout(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    title: String,
    artist: String,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean,
    textColor: ColorProvider,
    context: Context
) {
    val secondaryColor = GlanceTheme.colors.secondaryContainer
    val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
    val size = LocalSize.current
    val albumArtSize = size.height - 32.dp

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp) // Padding applied to the outer box
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(bgCornerRadius)
            ,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {

            AlbumArtImageGlance(
                modifier = GlanceModifier
                    .size(albumArtSize),
                bitmapData = albumArtBitmapData,
                albumArtUri = albumArtUri,
                context = context,
                cornerRadius = 12.dp
            )
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                if (artist.isNotEmpty() && artist != context.getString(R.string.widget_tap_to_open)) {
                    Text(text = artist, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            PlayPauseButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .size(48.dp, 48.dp)
                    .fillMaxHeight(),
                backgroundColor = primaryContainerColor,
                iconColor = onPrimaryContainerColor,
                isPlaying = isPlaying,
                iconSize = 26.dp,
                cornerRadius = 60.dp
            )
            Spacer(GlanceModifier.width(10.dp))
            NextButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .size(48.dp, 48.dp)
                    .fillMaxHeight(),
                iconColor = onSecondaryColor,
                iconSize = 26.dp,
                backgroundColor = secondaryColor,
                cornerRadius = 60.dp
            )
        }
    }
}

@Composable
fun GabeTwoHeightWidgetLayout(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean,
    context: Context
) {
    val secondaryColor = GlanceTheme.colors.secondaryContainer
    val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {

            AlbumArtImageGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(48.dp)
                    //.padding(4.dp)
                ,
                bitmapData = albumArtBitmapData,
                albumArtUri = albumArtUri,
                //size = 48.dp,
                context = context,
                cornerRadius = 12.dp
            )
            Spacer(GlanceModifier.height(14.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(bgCornerRadius)
            ) {
                PlayPauseButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth(),
                    backgroundColor = primaryContainerColor,
                    iconColor = onPrimaryContainerColor,
                    isPlaying = isPlaying,
                    iconSize = 26.dp,
                    cornerRadius = 60.dp
                )
                Spacer(GlanceModifier.height(10.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth(),
                    iconColor = onSecondaryColor,
                    iconSize = 26.dp,
                    backgroundColor = secondaryColor,
                    cornerRadius = 60.dp
                )
            }
        }
    }
}

@Composable
fun GabeWidgetLayout(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean,
    context: Context
) {
    val secondaryColor = GlanceTheme.colors.secondaryContainer
    val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            AlbumArtImageGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .height(48.dp),
                bitmapData = albumArtBitmapData,
                albumArtUri = albumArtUri,
                context = context,
                cornerRadius = 12.dp
            )
            Spacer(GlanceModifier.height(14.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(bgCornerRadius)
            ) {
                PreviousButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth(),
                    iconColor = onSecondaryColor,
                    iconSize = 26.dp,
                    backgroundColor = secondaryColor,
                    cornerRadius = 60.dp
                )
                Spacer(GlanceModifier.height(10.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth(),
                    backgroundColor = primaryContainerColor,
                    iconColor = onPrimaryContainerColor,
                    isPlaying = isPlaying,
                    iconSize = 26.dp,
                    cornerRadius = 60.dp
                )
                Spacer(GlanceModifier.height(10.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth(),
                    iconColor = onSecondaryColor,
                    iconSize = 26.dp,
                    backgroundColor = secondaryColor,
                    cornerRadius = 60.dp
                )
            }
        }
    }
}

@Composable
fun OneByOneWidgetLayout(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    isPlaying: Boolean
) {
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        PlayPauseButtonGlance(
            modifier = GlanceModifier.fillMaxSize(),
            backgroundColor = primaryContainerColor,
            iconColor = onPrimaryContainerColor,
            isPlaying = isPlaying,
            iconSize = 36.dp,
            cornerRadius = 60.dp
        )
    }
}

@Composable
fun SmallHorizontalWidgetLayout(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean,
    context: Context
) {
    val primaryContainerColor = GlanceTheme.colors.primaryContainer
    val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(bgCornerRadius),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            AlbumArtImageGlance(
                modifier = GlanceModifier.padding(vertical = 6.dp),
                bitmapData = albumArtBitmapData,
                albumArtUri = albumArtUri,
                size = 58.dp,
                context = context,
                cornerRadius = 12.dp
            )
            Spacer(GlanceModifier.width(14.dp))
            PlayPauseButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                backgroundColor = primaryContainerColor,
                iconColor = onPrimaryContainerColor,
                isPlaying = isPlaying,
                iconSize = 26.dp,
                cornerRadius = 60.dp
            )
        }
    }
}
