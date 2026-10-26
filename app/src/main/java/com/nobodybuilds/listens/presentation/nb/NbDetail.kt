package com.nobodybuilds.listens.presentation.nb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================================
// Detail screens (album, artist, playlist): flat app bar, art + title header, and the
// Play / Shuffle pill row from the Album / Artist / Playlist canvases.
// =====================================================================================

/**
 * Flat app bar: back on the left, optional [title] (shown once the header has scrolled
 * away) and trailing [actions]. Draws on the canvas colour and pads for the status bar.
 */
@Composable
fun NbDetailAppBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    backContentDescription: String = "Back",
    background: Color = Nb.canvas,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .heightIn(min = 60.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NbIconButton(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = backContentDescription,
            onClick = onBack
        )
        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (title != null) {
                Text(title, style = NbType.h3, color = Nb.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/**
 * Art on the left, overline / title / subtitle stacked on the right, bottom-aligned —
 * the album and playlist header.
 */
@Composable
fun NbCollectionHeader(
    overline: String,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = Nb.text,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    caption: String? = null,
    art: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        art()
        Column(Modifier.weight(1f).padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NbOverline(overline)
            Text(
                title,
                style = NbType.display(if (title.length > 22) 26.sp else 32.sp),
                color = titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = NbType.title.copy(fontWeight = FontWeight.Bold),
                    color = Nb.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onSubtitleClick != null) {
                        Modifier.clip(NbShapes.sm).clickable(role = Role.Button, onClick = onSubtitleClick)
                    } else Modifier
                )
            }
            if (caption != null) Text(caption, style = NbType.meta, color = Nb.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Centered header for artists: round avatar, display name, muted meta line. */
@Composable
fun NbAvatarHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    avatar: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        avatar()
        Text(
            title,
            style = NbType.display(if (title.length > 20) 28.sp else 36.sp),
            color = Nb.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (caption != null) Text(caption, style = NbType.meta, color = Nb.textMuted, textAlign = TextAlign.Center)
    }
}

/** Play (ink, primary) + Shuffle (outline, secondary) pills that share the row. */
@Composable
fun NbPlayShuffleRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    playLabel: String = "Play",
    shuffleLabel: String = "Shuffle",
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NbButton(
            text = playLabel,
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            style = NbButtonStyle.Primary,
            size = NbButtonSize.Large,
            enabled = enabled,
            icon = rememberVectorPainter(Icons.Rounded.PlayArrow)
        )
        NbButton(
            text = shuffleLabel,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
            style = NbButtonStyle.Secondary,
            size = NbButtonSize.Large,
            enabled = enabled,
            icon = rememberVectorPainter(Icons.Rounded.Shuffle)
        )
        trailing?.invoke(this)
    }
}
