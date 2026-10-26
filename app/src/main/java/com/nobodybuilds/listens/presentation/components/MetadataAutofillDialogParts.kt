package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.autofill.AutofillCandidate
import com.nobodybuilds.listens.data.autofill.AutofillSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbType

@Composable
internal fun AutofillTopBar(
    title: String,
    navIcon: androidx.compose.ui.graphics.vector.ImageVector,
    navDescription: String,
    onNav: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Nb.canvas)
            .statusBarsPadding()
            .heightIn(min = 60.dp)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NbIconButton(imageVector = navIcon, contentDescription = navDescription, onClick = onNav)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (subtitle != null) NbType.h3 else NbType.h2,
                color = Nb.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = NbType.caption, color = Nb.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun AutofillSectionHeader(title: String, subtitle: String, badge: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = NbType.h3, color = Nb.text, modifier = Modifier.weight(1f))
            badge?.invoke()
        }
        Text(text = subtitle, style = NbType.caption, color = Nb.textMuted)
    }
}

@Composable
internal fun CandidateRow(candidate: AutofillCandidate, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .background(Nb.canvas)
            .border(if (highlighted) 1.5.dp else 1.dp, if (highlighted) Nb.ink else Nb.border, NbShapes.lg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (candidate.source == AutofillSource.FILES) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(NbShapes.md)
                    .background(Nb.muted),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Nb.text)
            }
        } else {
            NbArt(
                model = candidate.thumbnailUrl,
                title = candidate.fields.album.ifBlank { candidate.fields.title },
                size = 52.dp,
                shape = RoundedCornerShape(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = candidate.fields.title.ifBlank { "—" },
                style = NbType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Nb.text
            )
            Text(
                text = candidate.fields.artist.ifBlank { "—" },
                style = NbType.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Nb.textMuted
            )
            val meta = listOfNotNull(
                candidate.fields.album.ifBlank { null },
                candidate.fields.year.ifBlank { null },
                candidate.releaseType,
                candidate.durationMs?.let { formatDuration(it) }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = NbType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Nb.textMuted
                )
            }
        }
        if (candidate.source == AutofillSource.MUSICBRAINZ) {
            ConfidenceChip(candidate.confidence)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Nb.textMuted)
        }
    }
}

@Composable
internal fun ConfidenceChip(confidence: Int, short: Boolean = false) {
    NbStatusChip(
        text = if (short) "$confidence%" else stringResource(R.string.nobody_listens_autofill_match, confidence),
        status = when {
            confidence >= 80 -> NbStatus.Success
            confidence >= 65 -> NbStatus.Warning
            else -> NbStatus.Neutral
        }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
