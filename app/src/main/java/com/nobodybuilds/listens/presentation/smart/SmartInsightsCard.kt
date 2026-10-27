package com.nobodybuilds.listens.presentation.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.smart.SmartInsights
import com.nobodybuilds.listens.data.stats.StatsTimeRange
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbMeter
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbType
import kotlin.math.roundToInt

/**
 * Stats screen card with on-device smart insights for [range]. Shows nothing when smart
 * features are off. [refreshKey] reloads (e.g. the stats summary object).
 */
@Composable
fun SmartInsightsCard(
    range: StatsTimeRange,
    modifier: Modifier = Modifier,
    refreshKey: Any? = null,
    viewModel: SmartInsightsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    LaunchedEffect(enabled, range, refreshKey) {
        if (enabled) viewModel.load(range, force = refreshKey != null)
    }
    if (!enabled) return
    SmartInsightsContent(insights = insights, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartInsightsContent(insights: SmartInsights?, modifier: Modifier = Modifier) {
    NbCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbOverline("Smart insights", modifier = Modifier.weight(1f))
            NbStatusChip(text = "Experimental", status = NbStatus.Neutral)
        }

        if (insights == null || insights.playCount == 0 || insights.moodMix.isEmpty()) {
            Text(
                if (insights == null) "Loading…" else "Play a few analysed songs to see your mood mix here.",
                style = NbType.body,
                color = Nb.textSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )
            return@NbCard
        }

        // Taste drift
        Text(insights.tasteDriftLabel, style = NbType.h3, color = Nb.text, modifier = Modifier.padding(top = 10.dp))
        Text(
            buildString {
                append("Compared with the previous period")
                append(" · ")
                append("${(insights.discoveryRate * 100).roundToInt()}% of plays were new to you")
            },
            style = NbType.meta,
            color = Nb.textMuted
        )

        // Mood mix
        NbDivider(Modifier.padding(vertical = 14.dp))
        Text("Mood mix", style = NbType.title, color = Nb.text)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            insights.moodMix.entries.take(5).forEachIndexed { index, (mood, share) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(mood, style = NbType.meta, color = Nb.text, modifier = Modifier.width(92.dp), maxLines = 1)
                    NbMeter(
                        progress = share.coerceIn(0f, 1f),
                        modifier = Modifier.weight(1f),
                        color = if (index == 0) Nb.ink else Nb.ink.copy(alpha = 0.55f)
                    )
                    Text(
                        "${(share * 100).roundToInt()}%",
                        style = NbType.caption,
                        color = Nb.textMuted,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Energy by hour
        NbDivider(Modifier.padding(vertical = 14.dp))
        Text("Energy by hour", style = NbType.title, color = Nb.text)
        EnergyByHourBars(insights.energyByHour, Modifier.padding(top = 10.dp))

        // Top genres
        if (insights.topGenres.isNotEmpty()) {
            NbDivider(Modifier.padding(vertical = 14.dp))
            Text("Top genres", style = NbType.title, color = Nb.text)
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                insights.topGenres.forEach { (genre, share) ->
                    Text(
                        "$genre ${(share * 100).roundToInt()}%",
                        style = NbType.caption,
                        color = Nb.text,
                        modifier = Modifier
                            .clip(NbShapes.pill)
                            .background(Nb.muted)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (insights.coverage < 0.6f) {
            Text(
                "Based on ${(insights.coverage * 100).roundToInt()}% of your plays. Indexing continues while charging.",
                style = NbType.caption,
                color = Nb.textMuted,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun EnergyByHourBars(values: List<Float?>, modifier: Modifier = Modifier) {
    val peak = values.filterNotNull().maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
    val peakHour = values.indices.maxByOrNull { values[it] ?: -1f }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { hour, v ->
                val fraction = if (v == null) 0.04f else (v / peak).coerceIn(0.08f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(NbShapes.sm)
                        .background(
                            when {
                                v == null -> Nb.emphasis
                                hour == peakHour -> Nb.ink
                                else -> Nb.ink.copy(alpha = 0.45f)
                            }
                        )
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("12a", "6a", "12p", "6p").forEach { label ->
                Text(label, style = NbType.caption, color = Nb.textMuted, modifier = Modifier.weight(1f))
            }
        }
    }
}
