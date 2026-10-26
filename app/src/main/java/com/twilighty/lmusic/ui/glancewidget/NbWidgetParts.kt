package com.twilighty.lmusic.ui.glancewidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.twilighty.lmusic.MainActivity
import com.twilighty.lmusic.R

/** Shared bits for the Daylight-style widgets: flat surfaces, pill buttons, ink text, Hard Hat accent. */
internal object NbWidget {
    val Radius = 24.dp
    val InnerRadius = 12.dp

    /** Opens the app. On the lock screen this asks to unlock first (a normal activity PendingIntent). */
    fun openApp(): Action = actionStartActivity<MainActivity>()

    /** Translucent dark surface for the lock screen (sits on the wallpaper, both themes). */
    val LockSurface = ColorProvider(Color(0xB3172033))
    val LockButton = ColorProvider(Color(0x33FFFFFF))
    val LockText = ColorProvider(Color(0xFFF5F7FB))
    val LockTextMuted = ColorProvider(Color(0xFFC3C8D2))
    val OnArtScrim = ColorProvider(Color(0x99172033))
    val White = ColorProvider(Color.White)
}

@Composable
internal fun WidgetCircleButton(
    icon: Int,
    contentDescription: String,
    action: Action,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: ColorProvider = GlanceTheme.colors.surfaceVariant,
    tint: ColorProvider = GlanceTheme.colors.onSurface,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background)
            .cornerRadius(size / 2)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** Hard Hat play/pause button wired to the music service. */
@Composable
internal fun WidgetPlayPause(
    context: android.content.Context,
    isPlaying: Boolean,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    background: ColorProvider = GlanceTheme.colors.primary,
    tint: ColorProvider = GlanceTheme.colors.onPrimary,
) = WidgetCircleButton(
    icon = if (isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24,
    contentDescription = if (isPlaying) "Pause" else "Play",
    action = musicServiceAction(context, PlayerActions.PLAY_PAUSE),
    size = size,
    iconSize = iconSize,
    background = background,
    tint = tint,
)

@Composable
internal fun WidgetTitle(text: String, color: ColorProvider = GlanceTheme.colors.onSurface, size: TextUnit = 14.sp, maxLines: Int = 1) {
    Text(text = text, style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold), maxLines = maxLines)
}

@Composable
internal fun WidgetMeta(text: String, color: ColorProvider = GlanceTheme.colors.onSurfaceVariant, size: TextUnit = 12.sp, maxLines: Int = 1) {
    Text(text = text, style = TextStyle(color = color, fontSize = size), maxLines = maxLines)
}

/** Small uppercase-free overline, Daylight style (sentence case, muted, medium). */
@Composable
internal fun WidgetOverline(text: String, color: ColorProvider = GlanceTheme.colors.onSurfaceVariant) {
    Text(text = text, style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}
