package com.twilighty.lmusic.presentation.lofi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.lofi.ChordColour
import com.twilighty.lmusic.data.lofi.LofiMood
import com.twilighty.lmusic.data.lofi.LofiPreset
import com.twilighty.lmusic.data.lofi.LofiSettings
import com.twilighty.lmusic.data.lofi.LofiSource
import com.twilighty.lmusic.data.lofi.LofiState
import com.twilighty.lmusic.presentation.haptics.LocalLMusicHaptics
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbChip
import com.twilighty.lmusic.presentation.nb.NbDialog
import com.twilighty.lmusic.presentation.nb.NbDivider
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbIconButtonStyle
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbSectionHeader
import com.twilighty.lmusic.presentation.nb.NbSegmented
import com.twilighty.lmusic.presentation.nb.NbSettingRow
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbStatus
import com.twilighty.lmusic.presentation.nb.NbStatusChip
import com.twilighty.lmusic.presentation.nb.NbSwitch
import com.twilighty.lmusic.presentation.nb.NbTextField
import com.twilighty.lmusic.presentation.nb.NbTopBar
import com.twilighty.lmusic.presentation.nb.NbType
import kotlin.math.roundToInt

private enum class LofiTab(val label: String) { Generate("Generate"), Radio("Radio"), Ambience("Ambience") }

/** Lofi: on-device generator, lofi radio and ambient layers. Reached from the Home header. */
@Composable
fun LofiScreen(
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: LofiViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LofiTab.Generate) }
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Nb.canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = "Lofi",
                    kicker = when {
                        state.source == LofiSource.Radio && state.isPlaying -> "Playing ${state.station?.name ?: "radio"}"
                        state.source == LofiSource.Generator && state.isPlaying -> "Playing · made on your phone"
                        else -> "Beats to relax to"
                    },
                    navigationIcon = {
                        NbIconButton(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    },
                    actions = {
                        if (state.source != null) {
                            NbIconButton(imageVector = Icons.Rounded.Stop, contentDescription = "Stop lofi", onClick = viewModel::stop, style = NbIconButtonStyle.Tonal)
                        }
                    }
                )
            }
            item(key = "tabs") {
                NbSegmented(
                    options = LofiTab.entries.map { it.label },
                    selectedIndex = tab.ordinal,
                    onSelect = { tab = LofiTab.entries[it] },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                )
            }
            state.error?.let { err ->
                item(key = "error") {
                    NbStatusChip(text = err, status = NbStatus.Danger, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            when (tab) {
                LofiTab.Generate -> generateItems(settings, state, viewModel)
                LofiTab.Radio -> radioItems(state, browse, favourites, viewModel)
                LofiTab.Ambience -> ambienceItems(viewModel)
            }
        }
    }
}

// ==================================================================== Generate

private fun LazyListScope.generateItems(settings: LofiSettings, state: LofiState, vm: LofiViewModel) {
    val playing = state.source == LofiSource.Generator && state.isPlaying
    item(key = "hero") { GeneratorHero(settings, state, playing, vm) }
    item(key = "moods") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NbSectionHeader("Mood")
            val moods = LofiMood.entries
            for (row in moods.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in row) {
                        MoodCard(mood = m, selected = settings.mood == m, onClick = { vm.setMood(m) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    item(key = "groove") {
        ControlsCard(title = "Groove") {
            LofiSlider(
                label = "Tempo",
                value = settings.tempoBpm.toFloat(),
                onValueChange = { v -> vm.update { it.copy(tempoBpm = v.roundToInt()) } },
                valueRange = LofiSettings.MIN_BPM.toFloat()..LofiSettings.MAX_BPM.toFloat(),
                valueText = "${settings.tempoBpm} BPM",
            )
            LofiSlider(
                label = "Swing",
                value = settings.swing,
                onValueChange = { v -> vm.update { it.copy(swing = v) } },
                valueRange = 0f..LofiSettings.MAX_SWING,
                valueText = "${(settings.swing * 100).roundToInt()}%",
            )
            Text("Key", style = NbType.label, color = Nb.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LofiSettings.KEY_NAMES.forEachIndexed { i, name ->
                    NbChip(text = name, selected = settings.key == i, onClick = { vm.update { it.copy(key = i) } })
                }
            }
            Text("Chord colour", style = NbType.label, color = Nb.text)
            NbSegmented(
                options = ChordColour.entries.map { it.label },
                selectedIndex = settings.chordColour.ordinal,
                onSelect = { i -> vm.update { it.copy(chordColour = ChordColour.entries[i]) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    item(key = "layers") {
        ControlsCard(title = "Layers") {
            LayerToggle("Drums", settings.drumsOn, { on -> vm.update { it.copy(drumsOn = on) } })
            if (settings.drumsOn) LofiSlider("Drums volume", settings.drumsVolume, { v -> vm.update { it.copy(drumsVolume = v) } })
            LayerToggle("Melody", settings.melodyOn, { on -> vm.update { it.copy(melodyOn = on) } })
            if (settings.melodyOn) LofiSlider("Melody volume", settings.melodyVolume, { v -> vm.update { it.copy(melodyVolume = v) } })
        }
    }
    item(key = "texture") {
        ControlsCard(title = "Texture") {
            LofiSlider("Vinyl crackle", settings.crackle, { v -> vm.update { it.copy(crackle = v) } })
            LofiSlider("Rain", settings.rain, { v -> vm.update { it.copy(rain = v) } })
            LofiSlider("Café", settings.cafe, { v -> vm.update { it.copy(cafe = v) } })
            LofiSlider("Tape wobble", settings.wobble, { v -> vm.update { it.copy(wobble = v) } })
            LofiSlider("Warmth", settings.warmth, { v -> vm.update { it.copy(warmth = v) } })
            NbDivider(Modifier.padding(vertical = 4.dp))
            LofiSlider("Volume", settings.masterVolume, { v -> vm.update { it.copy(masterVolume = v) } })
        }
    }
    item(key = "sleep") { SleepTimerCard(state, vm) }
    item(key = "presets") { PresetsCard(vm) }
}

@Composable
private fun GeneratorHero(settings: LofiSettings, state: LofiState, playing: Boolean, vm: LofiViewModel) {
    val haptics = LocalLMusicHaptics.current
    NbCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), background = Nb.subtle, contentPadding = PaddingValues(16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            LofiVisualizer(meters = vm::meters, active = playing, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .size(76.dp)
                    .clip(NbShapes.pill)
                    .background(Nb.ink)
                    .clickable(role = Role.Button) { haptics.playPause(); vm.toggleGenerator() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Nb.onInk,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(settings.mood.label, style = NbType.h3, color = Nb.text)
                Text(
                    "${settings.keyName} · ${settings.tempoBpm} BPM · ${settings.chordColour.label.lowercase()} chords",
                    style = NbType.meta, color = Nb.textMuted
                )
            }
            NbButton(
                text = "New tune",
                onClick = vm::newTune,
                style = NbButtonStyle.Secondary,
                size = NbButtonSize.Small,
                icon = rememberVectorPainter(Icons.Rounded.Refresh)
            )
        }
        if (state.source == LofiSource.Radio && state.isPlaying) {
            Spacer(Modifier.height(8.dp))
            Text("Radio is playing — press play to switch to the generator.", style = NbType.caption, color = Nb.textMuted)
        }
    }
}

@Composable
private fun MoodCard(mood: LofiMood, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalLMusicHaptics.current
    val (bg, icon) = moodLook(mood)
    Column(
        modifier
            .clip(NbShapes.lg)
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Nb.ink else Nb.border, NbShapes.lg)
            .clickable(role = Role.RadioButton) { haptics.selection(); onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Nb.text, modifier = Modifier.size(22.dp))
        Text(mood.label, style = NbType.title, color = Nb.text)
        Text(mood.blurb, style = NbType.caption, color = Nb.textMuted, maxLines = 2)
    }
}

@Composable
private fun moodLook(mood: LofiMood): Pair<Color, ImageVector> = when (mood) {
    LofiMood.Dreamy -> Nb.tintLavender to Icons.Rounded.NightsStay
    LofiMood.Rainy -> Nb.tintMint to Icons.Rounded.WaterDrop
    LofiMood.Jazzy -> Nb.tintPeach to Icons.Rounded.Piano
    LofiMood.Sunny -> Nb.warningSoft to Icons.Rounded.WbSunny
}

@Composable
private fun ControlsCard(title: String, content: @Composable () -> Unit) {
    NbCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        NbOverline(title)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun LayerToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = NbType.title, color = Nb.text, modifier = Modifier.weight(1f))
        NbSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SleepTimerCard(state: LofiState, vm: LofiViewModel) {
    val active = state.source != null
    var remainingMin by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.sleepTimerEndsAt) {
        val end = state.sleepTimerEndsAt
        if (end == null) { remainingMin = null; return@LaunchedEffect }
        while (true) {
            val left = end - System.currentTimeMillis()
            if (left <= 0) { remainingMin = null; break }
            remainingMin = (left + 59_999L) / 60_000L
            kotlinx.coroutines.delay((left % 60_000L).coerceAtLeast(1_000L))
        }
    }
    ControlsCard(title = "Sleep timer") {
        Text(
            when {
                remainingMin != null -> "Stops in $remainingMin min, with a slow fade"
                !active -> "Start lofi to set a timer"
                else -> "Stop lofi after a while, with a slow fade"
            },
            style = NbType.meta, color = Nb.textMuted
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NbChip(text = "Off", selected = state.sleepTimerEndsAt == null, onClick = { vm.setSleepTimer(null) },
                icon = rememberVectorPainter(Icons.Rounded.Close))
            for (m in listOf(15, 30, 45, 60, 90)) {
                NbChip(
                    text = "$m min",
                    selected = false,
                    onClick = { if (active) vm.setSleepTimer(m) },
                    icon = if (m == 30) rememberVectorPainter(Icons.Rounded.Bedtime) else null
                )
            }
        }
    }
}

@Composable
private fun PresetsCard(vm: LofiViewModel) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    var showSave by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<LofiPreset?>(null) }
    ControlsCard(title = "Presets") {
        if (presets.isEmpty()) {
            Text("Save the sound you like and come back to it. Presets keep the tune too.", style = NbType.meta, color = Nb.textMuted)
        } else {
            for (p in presets) {
                NbSettingRow(
                    title = p.name,
                    subtitle = "${p.settings.mood.label} · ${p.settings.keyName} · ${p.settings.tempoBpm} BPM",
                    onClick = { vm.loadPreset(p) },
                    trailing = {
                        NbIconButton(imageVector = Icons.Rounded.Close, contentDescription = "Delete ${p.name}", onClick = { toDelete = p }, size = 36.dp, iconSize = 18.dp)
                    }
                )
            }
        }
        NbButton(
            text = "Save preset",
            onClick = { showSave = true },
            style = NbButtonStyle.Secondary,
            size = NbButtonSize.Small,
            icon = rememberVectorPainter(Icons.Rounded.Save)
        )
    }
    if (showSave) {
        var name by remember { mutableStateOf("") }
        NbDialog(
            onDismissRequest = { showSave = false },
            title = "Save preset",
            body = "Saves every control and the current tune.",
            actions = {
                NbButton("Cancel", onClick = { showSave = false }, style = NbButtonStyle.Secondary)
                NbButton("Save", onClick = { vm.savePreset(name); showSave = false }, enabled = name.isNotBlank())
            }
        ) {
            NbTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name", placeholder = "Late night study")
        }
    }
    toDelete?.let { p ->
        NbDialog(
            onDismissRequest = { toDelete = null },
            title = "Delete “${p.name}”?",
            actions = {
                NbButton("Cancel", onClick = { toDelete = null }, style = NbButtonStyle.Secondary)
                NbButton("Delete", onClick = { vm.deletePreset(p); toDelete = null }, style = NbButtonStyle.Danger)
            }
        )
    }
}
