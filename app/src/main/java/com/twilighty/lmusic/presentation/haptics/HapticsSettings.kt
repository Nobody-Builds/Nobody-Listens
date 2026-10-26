package com.twilighty.lmusic.presentation.haptics

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.twilighty.lmusic.R
import com.twilighty.lmusic.presentation.screens.SwitchSettingItem
import com.twilighty.lmusic.presentation.nb.NbSegmented

/**
 * Settings → Behavior → Haptics. "Off / Subtle / Full / Strong" plus the seek-tick switch.
 * Off also turns off the legacy master switch, so every older haptic call site goes quiet too.
 */
@Composable
fun HapticsSettingsSection() {
    val engine = (LocalLMusicHaptics.current as? LMusicHapticsEngine) ?: LMusicHapticsEngine.instance ?: return
    val level by engine.level.collectAsState()
    val seekTicks by engine.seekTicksEnabled.collectAsState()
    val levels = HapticsLevel.entries
    val richLevel = level == HapticsLevel.FULL || level == HapticsLevel.STRONG
    val labels = listOf("Off", "Subtle", "Full", "Strong")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Icon(
                            painterResource(R.drawable.rounded_touch_app_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Haptics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (level) {
                                HapticsLevel.OFF -> "No vibration feedback"
                                HapticsLevel.SUBTLE -> "Lighter, only for key moments"
                                HapticsLevel.FULL -> "Crisp feedback across the app"
                                HapticsLevel.STRONG -> "Punchy, rich feedback everywhere"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                NbSegmented(
                    options = labels,
                    selectedIndex = levels.indexOf(level),
                    onSelect = { index ->
                        val newLevel = levels[index]
                        if (newLevel != level) {
                            engine.setLevel(newLevel)
                            // Preview at the NEW level right away (the saved level updates a moment later).
                            engine.preview(newLevel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        SwitchSettingItem(
            title = "Seek ticks",
            subtitle = "A soft tick every 10 seconds while you scrub",
            checked = seekTicks && richLevel,
            enabled = richLevel,
            onCheckedChange = { engine.setSeekTicks(it) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.rounded_touch_app_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        )
    }
}
