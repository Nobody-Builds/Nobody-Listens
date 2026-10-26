package com.twilighty.lmusic.presentation.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.twilighty.lmusic.R
import com.twilighty.lmusic.data.gestures.GesturePreferences
import com.twilighty.lmusic.data.gestures.GestureSettings
import com.twilighty.lmusic.data.gestures.ShakeSensitivity
import com.twilighty.lmusic.presentation.screens.SwitchSettingItem
import com.twilighty.lmusic.presentation.nb.NbSegmented
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GestureSettingsViewModel @Inject constructor(
    private val preferences: GesturePreferences,
) : ViewModel() {

    val settings: StateFlow<GestureSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestureSettings())

    /** For the player: double-tap the artwork to like (default on). */
    val doubleTapToLike: StateFlow<Boolean> = preferences.doubleTapToLike
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setShakeToShuffle(enabled: Boolean) = viewModelScope.launch { preferences.setShakeToShuffle(enabled) }
    fun setFaceDownToPause(enabled: Boolean) = viewModelScope.launch { preferences.setFaceDownToPause(enabled) }
    fun setResumeWhenFaceUp(enabled: Boolean) = viewModelScope.launch { preferences.setResumeWhenFaceUp(enabled) }
    fun setDoubleTapToLike(enabled: Boolean) = viewModelScope.launch { preferences.setDoubleTapToLike(enabled) }
    fun setShakeSensitivity(value: ShakeSensitivity) = viewModelScope.launch { preferences.setShakeSensitivity(value) }
}

/** Settings › Behavior › Gestures. */
@Composable
fun GestureSettingsSection(viewModel: GestureSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasMotionSensor = remember {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
    val motionSubtitleSuffix = if (hasMotionSensor) "" else ". Needs a motion sensor"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchSettingItem(
            title = "Double-tap artwork to like",
            subtitle = "Double-tap the cover in the player to like the song",
            checked = settings.doubleTapToLike,
            onCheckedChange = { viewModel.setDoubleTapToLike(it) },
            leadingIcon = { GestureIcon(R.drawable.rounded_favorite_24) }
        )
        SwitchSettingItem(
            title = "Shake to shuffle",
            subtitle = "Shake while music plays to shuffle and jump to a random song. Works with the screen off too: then give two firm shakes. Ignored in a pocket or bag$motionSubtitleSuffix",
            checked = settings.shakeToShuffle && hasMotionSensor,
            enabled = hasMotionSensor,
            onCheckedChange = { viewModel.setShakeToShuffle(it) },
            leadingIcon = { GestureIcon(R.drawable.rounded_shuffle_24) }
        )
        if (settings.shakeToShuffle && hasMotionSensor) {
            ShakeSensitivityCard(
                selected = settings.shakeSensitivity,
                onSelect = { viewModel.setShakeSensitivity(it) }
            )
        }
        SwitchSettingItem(
            title = "Turn face-down to pause",
            subtitle = "Lay your phone flat, screen-down, to pause. Works with the screen off while music plays$motionSubtitleSuffix",
            checked = settings.faceDownToPause && hasMotionSensor,
            enabled = hasMotionSensor,
            onCheckedChange = { viewModel.setFaceDownToPause(it) },
            leadingIcon = { GestureIcon(R.drawable.rounded_pause_24) }
        )
        SwitchSettingItem(
            title = "Resume when face-up",
            subtitle = "Turn it back over within 2 minutes to keep listening",
            checked = settings.resumeWhenFaceUp && settings.faceDownToPause && hasMotionSensor,
            enabled = settings.faceDownToPause && hasMotionSensor,
            onCheckedChange = { viewModel.setResumeWhenFaceUp(it) },
            leadingIcon = { GestureIcon(R.drawable.rounded_play_arrow_24) }
        )
        Text(
            text = "Motion gestures only listen while music plays, also with the screen off or locked. You feel a short buzz when one works.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun GestureIcon(res: Int) {
    Icon(painterResource(res), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun ShakeSensitivityCard(selected: ShakeSensitivity, onSelect: (ShakeSensitivity) -> Unit) {
    val options = ShakeSensitivity.entries
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    GestureIcon(R.drawable.rounded_touch_app_24)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Shake sensitivity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (selected) {
                            ShakeSensitivity.LOW -> "Only a firm shake counts"
                            ShakeSensitivity.MEDIUM -> "Two quick shakes"
                            ShakeSensitivity.HIGH -> "A light shake is enough"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            NbSegmented(
                options = options.map { it.label },
                selectedIndex = options.indexOf(selected),
                onSelect = { index -> onSelect(options[index]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
