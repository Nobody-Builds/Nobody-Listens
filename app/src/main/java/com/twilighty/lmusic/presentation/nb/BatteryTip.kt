package com.twilighty.lmusic.presentation.nb

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.tipsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_tips")
private val BATTERY_TIP_DISMISSED = booleanPreferencesKey("battery_tip_dismissed")

/** Visibility of the "Samsung may pause Nobody Listens" tip for Focus and Party. */
@Stable
class BatteryTipState internal constructor(
    private val context: Context,
    private val onDismiss: () -> Unit,
) {
    internal var optimized by mutableStateOf(isBatteryOptimized(context))
    internal var dismissed by mutableStateOf(true)

    /** Shown while the app is battery-optimized and the tip was never dismissed. */
    val visible: Boolean get() = optimized && !dismissed

    fun dismiss() = onDismiss()

    fun openSettings() {
        openBatterySettings(context)
    }

    internal fun recheck() {
        optimized = isBatteryOptimized(context)
    }
}

@Composable
fun rememberBatteryTipState(): BatteryTipState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember(context) {
        BatteryTipState(context) {
            scope.launch { runCatching { context.tipsDataStore.edit { it[BATTERY_TIP_DISMISSED] = true } } }
        }
    }
    val dismissed by remember(context) {
        context.tipsDataStore.data.map { it[BATTERY_TIP_DISMISSED] ?: false }
    }.collectAsState(initial = true)
    state.dismissed = dismissed
    // The user may come back from the battery settings page; re-check then (no polling).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.recheck() }
    return state
}

@Composable
fun BatteryOptimizationTip(state: BatteryTipState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NbShapes.lg)
            .background(Nb.warningSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Samsung may pause Nobody Listens in the background. Allow it to keep timers and parties running.",
            style = NbType.meta,
            color = Nb.warningText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TipAction("Allow in settings") { state.openSettings() }
            TipAction("Dismiss") { state.dismiss() }
        }
    }
}

@Composable
private fun TipAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = NbType.label.copy(fontWeight = FontWeight.Bold),
        color = Nb.warningText,
        modifier = Modifier
            .clip(NbShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

private fun isBatteryOptimized(context: Context): Boolean = runCatching {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    !pm.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)

private fun openBatterySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
