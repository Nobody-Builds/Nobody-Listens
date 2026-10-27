package com.nobodybuilds.listens.presentation.noise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.data.noise.BinauralMode
import com.nobodybuilds.listens.data.noise.NoiseGroup
import com.nobodybuilds.listens.data.noise.NoiseKind
import com.nobodybuilds.listens.data.noise.NoisePlayState
import com.nobodybuilds.listens.data.noise.NoiseSettings
import com.nobodybuilds.listens.data.noise.dsp.NoiseMixer
import com.nobodybuilds.listens.presentation.lofi.LofiSlider
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbDialog
import com.nobodybuilds.listens.presentation.nb.NbDivider
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbSegmented
import com.nobodybuilds.listens.presentation.nb.NbSettingRow
import com.nobodybuilds.listens.presentation.nb.NbSwitch
import com.nobodybuilds.listens.presentation.nb.NbTextField
import com.nobodybuilds.listens.presentation.nb.NbType

/** "Playing · Deep work", "Under music", "Off". */
fun NoisePlayState.label(settings: NoiseSettings): String = when {
    underMusic && sounding -> "Playing softly under your music"
    underMusic -> "On · sounds while music plays"
    sounding -> "Playing · ${settings.presetName ?: settings.summary}"
    else -> "Off"
}

/**
 * The noise mixer: presets, up to three sounds with their own volume, tone, swell, fade-in,
 * under-music mode and optional binaural tones. Used on Focus and in the Sleep space.
 * [showFocusOptions] adds the focus-session behaviour switches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoiseSection(
    modifier: Modifier = Modifier,
    showFocusOptions: Boolean = false,
    showPlayButton: Boolean = true,
    viewModel: NoiseViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val focusOptions by viewModel.focusOptions.collectAsStateWithLifecycle()
    var more by rememberSaveable { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }

    NbCard(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NbOverline("Noise")
                Text(state.label(settings), style = NbType.meta, color = Nb.textMuted, maxLines = 1)
            }
            if (showPlayButton) {
                NbButton(
                    text = if (state.active) "Pause" else "Play",
                    onClick = viewModel::togglePlay,
                    style = if (state.active) NbButtonStyle.Primary else NbButtonStyle.Accent,
                    size = NbButtonSize.Small,
                    icon = rememberVectorPainter(if (state.active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow),
                    enabled = state.active || settings.layers.isNotEmpty(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Presets", style = NbType.label, color = Nb.text)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                NbChip(text = p.name, selected = settings.presetName == p.name, onClick = { viewModel.applyPreset(p) })
            }
            NbChip(text = "Save as preset", selected = false, onClick = { saveOpen = true })
        }
        val userPreset = presets.firstOrNull { !it.builtIn && it.name == settings.presetName }
        if (userPreset != null) {
            NbButton(
                text = "Delete “${userPreset.name}”",
                onClick = { viewModel.deletePreset(userPreset.name) },
                style = NbButtonStyle.Quiet,
                size = NbButtonSize.Small,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Sounds · up to ${NoiseMixer.MAX_LAYERS}", style = NbType.label, color = Nb.text)
        NoiseGroup.entries.forEach { group ->
            Spacer(Modifier.height(10.dp))
            Text(group.label, style = NbType.caption, color = Nb.textMuted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseKind.entries.filter { it.group == group }.forEach { kind ->
                    NbChip(
                        text = kind.label,
                        selected = settings.layers.any { it.kind == kind },
                        onClick = { viewModel.toggleLayer(kind) }
                    )
                }
            }
        }
        if (settings.layers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            settings.layers.forEach { layer ->
                LofiSlider(
                    label = layer.kind.label,
                    value = layer.volume,
                    onValueChange = { viewModel.setLayerVolume(layer.kind, it) },
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Pick a sound to start", style = NbType.caption, color = Nb.textMuted)
        }
        LofiSlider(
            label = "Tone",
            value = settings.tone,
            onValueChange = { viewModel.setTone(it) },
            valueRange = -1f..1f,
            valueText = when {
                settings.tone < -0.15f -> "Darker"
                settings.tone > 0.15f -> "Brighter"
                else -> "Neutral"
            },
        )
        LofiSlider(label = "Volume", value = settings.masterVolume, onValueChange = { viewModel.setMaster(it) })
        NbSettingRow(
            title = "Natural movement",
            subtitle = "A gentle, slow swell",
            trailing = { NbSwitch(checked = settings.swell, onCheckedChange = { viewModel.setSwell(it) }) },
        )

        NbDivider()
        NbSettingRow(
            title = if (more) "Fewer options" else "More options",
            subtitle = "Fade-in, under music, binaural tones",
            icon = rememberVectorPainter(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore),
            onClick = { more = !more },
        )
        if (more) {
            Text("Fade in", style = NbType.label, color = Nb.text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseSettings.FADE_IN_CHOICES.forEach { s ->
                    NbChip(
                        text = if (s == 0) "Off" else "$s s",
                        selected = settings.fadeInSeconds == s,
                        onClick = { viewModel.setFadeIn(s) }
                    )
                }
            }
            NbSettingRow(
                title = "Play under music",
                subtitle = "Softly, only while your music plays",
                trailing = { NbSwitch(checked = settings.underMusic, onCheckedChange = { viewModel.setUnderMusic(it) }) },
            )
            NbDivider()
            Spacer(Modifier.height(12.dp))
            Text("Binaural tones (headphones)", style = NbType.label, color = Nb.text)
            Text(
                "Some people find these help; research is mixed. Use headphones.",
                style = NbType.caption,
                color = Nb.textMuted,
            )
            Spacer(Modifier.height(10.dp))
            NbSegmented(
                options = BinauralMode.entries.map { it.label },
                selectedIndex = settings.binaural.ordinal,
                onSelect = { viewModel.setBinaural(BinauralMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.binaural != BinauralMode.Off) {
                Spacer(Modifier.height(8.dp))
                Text("${settings.binaural.blurb} · carrier tone", style = NbType.caption, color = Nb.textMuted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoiseSettings.CARRIERS.forEach { hz ->
                        NbChip(text = "$hz Hz", selected = settings.carrierHz == hz, onClick = { viewModel.setCarrier(hz) })
                    }
                }
            }
        }
        if (showFocusOptions) {
            Spacer(Modifier.height(8.dp))
            NbDivider()
            NbSettingRow(
                title = "Stop at end of session",
                subtitle = "Fades out when the last round ends",
                trailing = {
                    NbSwitch(
                        checked = focusOptions.stopAtSessionEnd,
                        onCheckedChange = { viewModel.setFocusOptions(focusOptions.copy(stopAtSessionEnd = it)) }
                    )
                },
            )
            NbSettingRow(
                title = "Keep playing through breaks",
                subtitle = if (focusOptions.keepThroughBreaks) "Noise carries on during breaks" else "Pauses during breaks",
                trailing = {
                    NbSwitch(
                        checked = focusOptions.keepThroughBreaks,
                        onCheckedChange = { viewModel.setFocusOptions(focusOptions.copy(keepThroughBreaks = it)) }
                    )
                },
            )
        }
    }

    if (saveOpen) {
        SavePresetDialog(
            initial = settings.presetName ?: settings.summary,
            onDismiss = { saveOpen = false },
            onSave = { name -> viewModel.savePreset(name); saveOpen = false },
        )
    }
}

@Composable
private fun SavePresetDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    NbDialog(
        onDismissRequest = onDismiss,
        title = "Save preset",
        actions = {
            NbButton(text = "Cancel", onClick = onDismiss, style = NbButtonStyle.Quiet)
            NbButton(text = "Save", onClick = { onSave(name) }, enabled = name.isNotBlank())
        }
    ) {
        NbTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name")
    }
}
