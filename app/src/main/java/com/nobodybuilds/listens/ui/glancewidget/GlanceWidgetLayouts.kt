package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
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
import com.nobodybuilds.listens.data.model.QueueItem

@Composable
fun SmallWidgetLayout(
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
    val buttonCornerRadius = 60.dp
    val playButtonCornerRadius = 60.dp

    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(12.dp) // Using 12dp for this smaller layout
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Start
            ) {
                AlbumArtImageGlance(
                    modifier = GlanceModifier.defaultWeight(),
                    bitmapData = albumArtBitmapData,
                    albumArtUri = albumArtUri,
                    context = context,
                    cornerRadius = 12.dp
                )
                //Spacer(GlanceModifier.width(10.dp))
            }
            Spacer(GlanceModifier.height(8.dp))
            PlayPauseButtonGlance(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .height(50.dp),
                isPlaying = isPlaying,
                cornerRadius = playButtonCornerRadius,
                iconSize = 26.dp,
                backgroundColor = primaryContainerColor,
                iconColor = onPrimaryContainerColor
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .height(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PreviousButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconSize = 26.dp,
                    cornerRadius = buttonCornerRadius,
                    backgroundColor = secondaryColor,
                    iconColor = onSecondaryColor
                )
                Spacer(GlanceModifier.width(8.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconSize = 26.dp,
                    cornerRadius = buttonCornerRadius,
                    backgroundColor = secondaryColor,
                    iconColor = onSecondaryColor
                )
            }
        }
    }
}

@Composable
fun MediumWidgetLayout(
    modifier: GlanceModifier,
    title: String,
    artist: String,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
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
    val buttonCornerRadius = 60.dp
    val playButtonCornerRadius = 60.dp

    // *** FIX: Apply padding to the outer Box for consistency ***
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        // *** FIX: Removed padding from the inner Column ***
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top part: Album Art + Title/Artist
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtImageGlance(
                    bitmapData = albumArtBitmapData,
                    albumArtUri = albumArtUri,
                    size = 80.dp,
                    context = context,
                    cornerRadius = 12.dp
                )
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 2
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = artist,
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 2
                    )
                }
            }

            // Spacer to push buttons down
            Spacer(GlanceModifier.height(12.dp))

            // Bottom part: Control Buttons
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviousButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    isPlaying = isPlaying,
                    iconColor = onPrimaryContainerColor,
                    backgroundColor = primaryContainerColor,
                    cornerRadius = playButtonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
            }
        }
    }
}

@Composable
fun LargeWidgetLayout(
    modifier: GlanceModifier,
    title: String,
    artist: String,
    albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    backgroundColor: ColorProvider,
    bgCornerRadius: Dp,
    isPlaying: Boolean,
    isFavorite: Boolean,
    textColor: ColorProvider,
    context: Context
) {
    // *** FIX: Apply padding to the outer Box for consistency ***
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        // *** FIX: Removed padding from the inner Column ***
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                AlbumArtImageGlance(
                    bitmapData = albumArtBitmapData,
                    albumArtUri = albumArtUri,
                    size = 64.dp,
                    context = context,
                    cornerRadius = 12.dp
                )
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor), maxLines = 1)
                    Text(text = artist, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                }
                Spacer(GlanceModifier.width(4.dp))
                Image(
                    provider = ImageProvider(if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24),
                    contentDescription = context.getString(R.string.cd_favorite),
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(playerControlAction(actionParametersOf(PlayerActions.key to PlayerActions.FAVORITE)))
                        .padding(2.dp),
                    colorFilter = ColorFilter.tint(textColor)
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            Spacer(GlanceModifier.height(4.dp))
            // Progress bar commented out as in original code
            Spacer(GlanceModifier.height(10.dp))

            // Control Buttons Row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val secondaryColor = GlanceTheme.colors.secondaryContainer
                val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
                val primaryContainerColor = GlanceTheme.colors.primaryContainer
                val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
                val buttonCornerRadius = 60.dp
                val playButtonCornerRadius = 60.dp

                PreviousButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier.size(56.dp),
                    isPlaying = isPlaying,
                    iconColor = onPrimaryContainerColor,
                    backgroundColor = primaryContainerColor,
                    cornerRadius = playButtonCornerRadius
                )
                Spacer(GlanceModifier.width(8.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    cornerRadius = buttonCornerRadius
                )
            }
        }
    }
}

@Composable
fun ExtraLargeWidgetLayout(
    modifier: GlanceModifier, title: String, artist: String, albumArtBitmapData: ByteArray?,
    albumArtUri: String?,
    isPlaying: Boolean, backgroundColor: ColorProvider, bgCornerRadius: Dp,
    textColor: ColorProvider,
    context: Context,
    queue: List<QueueItem>
) {
    val playButtonCornerRadius = 60.dp

    // *** FIX: Apply padding to the outer Box for consistency ***
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(bgCornerRadius)
            .padding(16.dp)
    ) {
        // *** FIX: Removed padding from the inner Column ***
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top Row: Album Art & Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                AlbumArtImageGlance(
                    bitmapData = albumArtBitmapData,
                    albumArtUri = albumArtUri,
                    size = 68.dp,
                    context = context,
                    cornerRadius = 12.dp
                )
                Spacer(GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor),
                        maxLines = 2
                    )
                    Text(
                        text = artist,
                        style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1
                    )
                }
            }

            Spacer(GlanceModifier.height(14.dp))

            // Bottom Row: Controls
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .height(56.dp)
                ,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val secondaryColor = GlanceTheme.colors.secondaryContainer
                val onSecondaryColor = GlanceTheme.colors.onSecondaryContainer
                val primaryContainerColor = GlanceTheme.colors.primaryContainer
                val onPrimaryContainerColor = GlanceTheme.colors.onPrimaryContainer
                val buttonCornerRadius = 60.dp

                PreviousButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    iconSize = 28.dp,
                    cornerRadius = buttonCornerRadius
                )
                Spacer(GlanceModifier.width(10.dp))
                PlayPauseButtonGlance(
                    modifier = GlanceModifier.size(56.dp),
                    isPlaying = isPlaying,
                    iconColor = onPrimaryContainerColor,
                    backgroundColor = primaryContainerColor,
                    iconSize = 30.dp,
                    cornerRadius = playButtonCornerRadius
                )
                Spacer(GlanceModifier.width(10.dp))
                NextButtonGlance(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    iconColor = onSecondaryColor,
                    backgroundColor = secondaryColor,
                    iconSize = 28.dp,
                    cornerRadius = buttonCornerRadius
                )
            }

            Spacer(GlanceModifier.defaultWeight()) // Empuja el contenido hacia abajo
            //Spacer(GlanceModifier.height(16.dp))

//                Text(
//                    text = "Next Up",
//                    style = TextStyle(
//                        fontSize = 16.sp,
//                        fontWeight = FontWeight.Bold,
//                        color = textColor
//                    ),
//                    modifier = GlanceModifier.padding(bottom = 8.dp)
//                )
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .background(textColor.getColor(context).copy(alpha = 0.15f))
                    .height(2.dp)
                    .cornerRadius(60.dp)
            ) {

            }

            Spacer(GlanceModifier.height(12.dp))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = queue.take(4)
                val itemSize = 58.dp
                val cornerRadius = 12.dp

                for (i in 0 until 4) {
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i < items.size) {
                            val queueItem = items[i]
                            AlbumArtImageGlance(
                                modifier = GlanceModifier.clickable(
                                    playerControlAction(
                                        actionParametersOf(
                                            PlayerActions.key to PlayerActions.PLAY_FROM_QUEUE,
                                            PlayerActions.songIdKey to queueItem.id
                                        )
                                    )
                                ),
                                bitmapData = null,
                                albumArtUri = queueItem.albumArtUri,
                                size = itemSize,
                                context = context,
                                cornerRadius = cornerRadius
                            )
                        } else {
                            EndOfQueuePlaceholder(
                                size = itemSize,
                                cornerRadius = cornerRadius
                            )
                        }
                    }

                    if (i < 3) {
                        Spacer(GlanceModifier.width(8.dp))
                    }
                }
            }
        }
    }
}
