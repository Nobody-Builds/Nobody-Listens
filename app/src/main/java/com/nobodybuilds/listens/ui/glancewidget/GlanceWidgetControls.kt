package com.nobodybuilds.listens.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.nobodybuilds.listens.R
import androidx.glance.unit.ColorProvider
import com.nobodybuilds.listens.utils.createScalableBackgroundBitmap
import timber.log.Timber

@Composable
fun AlbumArtImageGlance(
    bitmapData: ByteArray?,
    albumArtUri: String? = null,
    size: Dp? = null,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
    cornerRadius: Dp = 16.dp
) {
    val TAG_AAIG = "AlbumArtImageGlance"
    Timber.tag(TAG_AAIG)
        .d("Init. bitmapData is null: ${bitmapData == null}. Requested Dp size: $size")
    if (bitmapData != null) Timber.tag(TAG_AAIG).d("bitmapData size: ${bitmapData.size} bytes")

    val sizingModifier = if (size != null) modifier.size(size) else modifier
    val widgetDpSize = LocalSize.current // Get the actual size of the composable

    val requestedPx = with(context.resources.displayMetrics) {
        val dp = size ?: minOf(widgetDpSize.width, widgetDpSize.height)
        if (dp == Dp.Unspecified || dp <= 0.dp) 0 else (dp.value * density).toInt()
    }
    // Capped at WidgetBitmaps.COVER_PX: full-widget-size bitmaps overflowed the RemoteViews
    // transaction limit and made the widget stop updating.
    val imageProvider = WidgetBitmaps.load(context, bitmapData, albumArtUri, requestedPx)
        ?.let { ImageProvider(it) }

    Box(
        modifier = sizingModifier
    ) {
        if (imageProvider != null) {
            Image(
                provider = imageProvider,
                contentDescription = context.getString(R.string.widget_album_art),
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerRadius),
                contentScale = ContentScale.Crop
            )
        } else {
            val placeholderBaseSize = size ?: minOf(widgetDpSize.width, widgetDpSize.height)
            val resolvedPlaceholderBaseSize = when {
                placeholderBaseSize == Dp.Unspecified || placeholderBaseSize <= 0.dp -> 72.dp
                else -> placeholderBaseSize
            }

            val placeholderSize = resolvedPlaceholderBaseSize
                .times(0.65f)
                .coerceIn(36.dp, 96.dp)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius)
                    .background(GlanceTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_music_placeholder),
                    contentDescription = context.getString(R.string.widget_album_art_placeholder),
                    modifier = GlanceModifier.size(placeholderSize),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

@Composable
fun PlayPauseButtonGlance(
    modifier: GlanceModifier = GlanceModifier,
    isPlaying: Boolean,
    iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 0.dp
) {
    val context = LocalContext.current
    val params = actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE)
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .clickable(playerControlAction(params)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(if (isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24),
            contentDescription = context.getString(if (isPlaying) R.string.cd_pause else R.string.cd_play),
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

@Composable
fun NextButtonDGlance(
    modifier: GlanceModifier = GlanceModifier,
    width: Dp? = null,
    height: Dp? = null,
    backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
    iconProvider: ImageProvider = ImageProvider(R.drawable.rounded_skip_next_24),
    iconSize: Dp = 24.dp,
    iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    topLeftCorner: Dp = 8.dp,
    topRightCorner: Dp = 8.dp,
    bottomLeftCorner: Dp = 8.dp,
    bottomRightCorner: Dp = 8.dp,
) {
    val context = LocalContext.current
    val bgColor = backgroundColor.getColor(context)
    val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)

    val sizeModifier = modifier.then(
        when {
            width != null && height != null -> GlanceModifier.size(width, height)
            width != null -> GlanceModifier.width(width)
            height != null -> GlanceModifier.height(height)
            else -> GlanceModifier
        }
    )

    val backgroundBitmap = createScalableBackgroundBitmap(
        context = context,
        color = bgColor,
        topLeft = topLeftCorner,
        topRight = topRightCorner,
        bottomLeft = bottomLeftCorner,
        bottomRight = bottomRightCorner,
        width = width,
        height = height
    )

    Box(
        modifier = sizeModifier
            .background(ImageProvider(backgroundBitmap))
            .clickable(playerControlAction(params)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = iconProvider,
            contentDescription = context.getString(R.string.next_track),
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

@Composable
fun NextButtonGlance(
    modifier: GlanceModifier = GlanceModifier,
    iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 8.dp
) {
    val context = LocalContext.current
    val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .clickable(playerControlAction(params)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.rounded_skip_next_24),
            contentDescription = context.getString(R.string.next_track),
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

@Composable
fun PreviousButtonGlance(
    modifier: GlanceModifier = GlanceModifier,
    iconColor: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    backgroundColor: ColorProvider = GlanceTheme.colors.surfaceVariant,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 8.dp
) {
    val context = LocalContext.current
    val params = actionParametersOf(PlayerActions.key to PlayerActions.PREVIOUS)
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .clickable(playerControlAction(params)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.rounded_skip_previous_24),
            contentDescription = context.getString(R.string.previous_track),
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

@Composable
fun EndOfQueuePlaceholder(
    modifier: GlanceModifier = GlanceModifier,
    size: Dp,
    cornerRadius: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(cornerRadius)
    ) {

    }
}
