package com.nobodybuilds.listens.presentation.garden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nobodybuilds.listens.data.garden.GardenGrowth
import com.nobodybuilds.listens.data.garden.GardenSnapshot
import com.nobodybuilds.listens.data.garden.GardenStage
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbMeter
import com.nobodybuilds.listens.presentation.nb.NbSectionHeader
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatTile
import com.nobodybuilds.listens.presentation.nb.NbTopBar
import com.nobodybuilds.listens.presentation.nb.NbType
import java.time.LocalDate
import kotlinx.coroutines.launch

private data class MilestoneInfo(val days: Int, val name: String, val color: Color, val reachedLine: String, val waitingLine: String)

private val MILESTONE_INFO = listOf(
    MilestoneInfo(7, "Golden flower", Color(0xFFF2B53A), "A golden flower bloomed beside your plant", "Blooms at 7 listening days"),
    MilestoneInfo(30, "Rose flower", Color(0xFFE8638B), "A rose flower joined your garden", "Blooms at 30 listening days"),
    MilestoneInfo(100, "Ember crown", Color(0xFFE8590C), "An ember crown flower tops your tree", "Blooms at 100 listening days"),
)

@Composable
fun GardenScreen(
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: GardenViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val flowerColors by viewModel.flowerColors.collectAsStateWithLifecycle()
    val growKey by viewModel.growKey.collectAsStateWithLifecycle()
    val haptics = LocalNobodyListensHaptics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    LaunchedEffect(viewModel) {
        viewModel.celebrate.collect { haptics.celebration() }
    }

    val s = snapshot
    val spec = remember(s, flowerColors) {
        s?.let {
            GardenSceneSpec(
                stage = it.growth.stage,
                treeMaturity = it.growth.treeMaturity,
                seed = it.seed,
                flowerColors = flowerColors,
                season = it.season,
                milestones = it.growth.milestonesReached,
            )
        }
    }
    val layout = remember(spec) { spec?.let { buildGardenLayout(it) } }

    fun share(title: String, subtitle: String, name: String) {
        val l = layout ?: return
        scope.launch { shareGarden(context, l, dark, title, subtitle, name) }
    }

    Box(Modifier.fillMaxSize().background(Nb.canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = "Garden",
                    kicker = when {
                        s == null -> " "
                        s.gardenDay > 0 -> "Day ${s.gardenDay} of your garden"
                        else -> "Listen for 5 minutes to plant it"
                    },
                    navigationIcon = {
                        NbIconButton(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    },
                    actions = {
                        if (s != null && layout != null) {
                            NbIconButton(
                                imageVector = Icons.Rounded.IosShare,
                                contentDescription = "Share garden",
                                onClick = {
                                    share(
                                        title = if (s.gardenDay > 0) "Day ${s.gardenDay} of my garden" else "My listening garden",
                                        subtitle = "${s.growth.stage.title} · ${s.growth.listeningDays} listening days",
                                        name = "garden_day_${s.gardenDay}"
                                    )
                                }
                            )
                        }
                    }
                )
            }

            item(key = "scene") {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(NbShapes.xxl)
                        .background(Nb.tintMint),
                    contentAlignment = Alignment.Center
                ) {
                    if (layout != null) {
                        GardenSceneView(
                            layout = layout,
                            dark = dark,
                            growKey = growKey,
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(12.dp)
                                .semantics { contentDescription = "Your garden: ${s?.growth?.stage?.title ?: ""}" }
                        )
                    } else {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }

            if (s != null) {
                item(key = "stage") { StageSection(s) }
                item(key = "stats") {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbStatTile(label = "Listening days", value = s.growth.listeningDays.toString(), modifier = Modifier.weight(1f))
                        NbStatTile(
                            label = "Focus rounds",
                            value = s.growth.focusRounds.toString(),
                            supporting = "Each adds a little growth",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "calendar") { CalendarSection(s, dark) }
                item(key = "milestones_header") {
                    NbSectionHeader(title = "Milestones", modifier = Modifier.padding(horizontal = 16.dp))
                }
                MILESTONE_INFO.forEach { info ->
                    item(key = "milestone_${info.days}") {
                        val reached = info.days in s.growth.milestonesReached
                        MilestoneCard(
                            info = info,
                            reached = reached,
                            onShare = {
                                share(
                                    title = "${info.days} listening days",
                                    subtitle = "${info.name} · day ${s.gardenDay} of my garden",
                                    name = "garden_milestone_${info.days}"
                                )
                            }
                        )
                    }
                }
                item(key = "note") {
                    Text(
                        "Your garden never wilts. Missed days are fine — it simply waits for you.",
                        style = NbType.caption,
                        color = Nb.textMuted,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * The garden scene: ground and pot are static, the plant sways gently on its own graphics layer
 * (no recomposition, no redraw). The sway only runs while the screen is resumed and this item is
 * composed, so it stops off-screen.
 */
@Composable
fun GardenSceneView(layout: GardenLayout, dark: Boolean, growKey: Int, modifier: Modifier = Modifier) {
    val palette = if (dark) GardenPalette.Dark else GardenPalette.Light
    val sway = remember { Animatable(0f) }
    val grow = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val amplitude = when {
        layout.stage == GardenStage.Seed -> 0f
        layout.stage == GardenStage.LittleTree -> 0.8f
        else -> 1.6f
    }
    LaunchedEffect(lifecycle, amplitude) {
        if (amplitude == 0f) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                sway.animateTo(1f, tween(2800, easing = FastOutSlowInEasing))
                sway.animateTo(-1f, tween(2800, easing = FastOutSlowInEasing))
            }
        }
    }
    LaunchedEffect(growKey) {
        if (growKey > 0) {
            grow.snapTo(0.84f)
            grow.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawGardenGround(layout, palette) }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val side = minOf(size.width, size.height)
                    val pivotY = if (size.height > 0f) (size.height - side + layout.baseY * side) / size.height else 1f
                    transformOrigin = TransformOrigin(0.5f, pivotY)
                    rotationZ = sway.value * amplitude
                    scaleX = grow.value
                    scaleY = grow.value
                }
        ) { drawGardenPlant(layout, palette) }
    }
}

@Composable
private fun StageSection(s: GardenSnapshot) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.growth.stage.title, style = NbType.h2, color = Nb.text)
        Text("Listening days: ${s.growth.listeningDays}", style = NbType.body, color = Nb.textMuted)
        Spacer(Modifier.height(4.dp))
        NbMeter(progress = s.growth.stageProgress, color = leafColor())
        Text(GardenGrowth.nextStageHint(s.growth), style = NbType.meta, color = Nb.textSecondary)
    }
}

@Composable
private fun leafColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) GardenPalette.Dark.leaf else GardenPalette.Light.leaf

@Composable
private fun CalendarSection(s: GardenSnapshot, dark: Boolean) {
    val weeks = remember(s.today) { GardenGrowth.calendarWeeks(s.today, 8) }
    val filled = if (dark) GardenPalette.Dark.leaf else GardenPalette.Light.leaf
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NbSectionHeader(title = "Last 8 weeks")
        NbCard(modifier = Modifier.fillMaxWidth()) {
            val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 7) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabels[row], style = NbType.caption, color = Nb.textMuted, modifier = Modifier.widthIn(min = 20.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                            weeks.forEach { week ->
                                CalendarDot(date = week[row], today = s.today, listened = week[row]?.let { it in s.listeningDays } == true, filled = filled)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Filled dots are days you listened for 5 minutes or more.",
                style = NbType.caption,
                color = Nb.textMuted
            )
        }
    }
}

@Composable
private fun CalendarDot(date: LocalDate?, today: LocalDate, listened: Boolean, filled: Color) {
    val base = Modifier.size(16.dp)
    when {
        date == null -> Spacer(base)
        listened -> Box(
            base.clip(CircleShape).background(filled)
                .then(if (date == today) Modifier.border(2.dp, Nb.ink, CircleShape) else Modifier)
        )
        else -> Box(
            base.clip(CircleShape).background(Nb.emphasis)
                .then(if (date == today) Modifier.border(1.5.dp, Nb.borderStrong, CircleShape) else Modifier)
        )
    }
}

@Composable
private fun MilestoneCard(info: MilestoneInfo, reached: Boolean, onShare: () -> Unit) {
    NbCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (reached) info.color.copy(alpha = 0.18f) else Nb.muted),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (reached) info.color else Nb.emphasis))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${info.days} listening days · ${info.name}",
                    style = NbType.title,
                    color = if (reached) Nb.text else Nb.textMuted
                )
                Text(if (reached) info.reachedLine else info.waitingLine, style = NbType.meta, color = Nb.textMuted)
            }
            if (reached) {
                NbButton(
                    text = "Share",
                    onClick = onShare,
                    style = NbButtonStyle.Secondary,
                    size = NbButtonSize.Small,
                    icon = rememberVectorPainter(Icons.Rounded.IosShare),
                    modifier = Modifier.heightIn(min = 36.dp)
                )
            }
        }
    }
}
