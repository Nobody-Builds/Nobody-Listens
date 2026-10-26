package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.presentation.model.RecentlyPlayedSongUiModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

@Composable
internal fun RecentlyPlayedTimestampDivider(
    label: String,
    isHourBucket: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val railColor = if (isHourBucket) colors.primary else colors.secondary
    val chipContainer = if (isHourBucket) {
        colors.primaryContainer.copy(alpha = 0.78f)
    } else {
        colors.secondaryContainer.copy(alpha = 0.78f)
    }
    val chipContent = if (isHourBucket) colors.onPrimaryContainer else colors.onSecondaryContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0f),
                            railColor.copy(alpha = 0.50f)
                        )
                    )
                )
        )
        Surface(
            color = chipContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 22.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTR = 22.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 22.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = 22.dp,
                smoothnessAsPercentTL = 60
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(railColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chipContent
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0.50f),
                            railColor.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

@Composable
internal fun RecentlyPlayedEmptyState(
    range: StatsTimeRange,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentTL = 60
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.presentation_batch_b_recent_empty_title,
                    range.displayName.lowercase()
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.presentation_batch_b_recent_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal data class RecentlyPlayedTimestampGroup(
    val key: String,
    val label: String,
    val isHourBucket: Boolean,
    val songs: List<RecentlyPlayedSongUiModel>
)

internal fun groupRecentlyPlayedSongs(
    context: android.content.Context,
    songs: List<RecentlyPlayedSongUiModel>,
    range: StatsTimeRange
): List<RecentlyPlayedTimestampGroup> {
    if (songs.isEmpty()) return emptyList()
    val zoneId = ZoneId.systemDefault()
    val sorted = songs.sortedByDescending { it.lastPlayedTimestamp }

    val groups = mutableListOf<RecentlyPlayedTimestampGroup>()
    var currentBucketKey: String? = null
    var currentLabel: String? = null
    var currentIsHourBucket = false
    var bucket = mutableListOf<RecentlyPlayedSongUiModel>()

    sorted.forEach { item ->
        val timeBucket = resolveTimestampBucket(
            context = context,
            timestamp = item.lastPlayedTimestamp,
            range = range,
            zoneId = zoneId
        )
        if (currentBucketKey == null || currentBucketKey == timeBucket.key) {
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket += item
        } else {
            groups += RecentlyPlayedTimestampGroup(
                key = currentBucketKey ?: "",
                label = currentLabel ?: "",
                isHourBucket = currentIsHourBucket,
                songs = bucket
            )
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket = mutableListOf(item)
        }
    }

    if (bucket.isNotEmpty() && currentLabel != null && currentBucketKey != null) {
        groups += RecentlyPlayedTimestampGroup(
            key = currentBucketKey ?: "",
            label = currentLabel ?: "",
            isHourBucket = currentIsHourBucket,
            songs = bucket
        )
    }

    return groups
}

private data class TimestampBucket(
    val key: String,
    val label: String,
    val isHourBucket: Boolean
)

private fun resolveTimestampBucket(
    context: android.content.Context,
    timestamp: Long,
    range: StatsTimeRange,
    zoneId: ZoneId
): TimestampBucket {
    val safeTimestamp = timestamp.coerceAtLeast(0L)
    val safeNow = System.currentTimeMillis().coerceAtLeast(0L)
    val zonedDateTime = Instant.ofEpochMilli(safeTimestamp).atZone(zoneId)
    val date = zonedDateTime.toLocalDate()
    val nowDate = Instant.ofEpochMilli(safeNow).atZone(zoneId).toLocalDate()

    if (range == StatsTimeRange.DAY) {
        val hourStart = zonedDateTime
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return TimestampBucket(
            key = hourStart.toInstant().toEpochMilli().toString(),
            label = hourStart.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault())),
            isHourBucket = true
        )
    }

    return when {
        date == nowDate -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.presentation_batch_b_date_today),
                isHourBucket = false
            )
        }
        date == nowDate.minusDays(1) -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.presentation_batch_b_date_yesterday),
                isHourBucket = false
            )
        }
        range == StatsTimeRange.YEAR || range == StatsTimeRange.ALL -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                isHourBucket = false
            )
        }
        else -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                isHourBucket = false
            )
        }
    }
}
