package com.twilighty.lmusic.presentation.screensaver

import android.content.res.Configuration
import android.os.PowerManager
import android.view.OrientationEventListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewModelScope
import com.twilighty.lmusic.data.screensaver.OrientationDetector
import com.twilighty.lmusic.data.screensaver.ScreensaverPreferences
import com.twilighty.lmusic.data.screensaver.ScreensaverSettings
import com.twilighty.lmusic.data.screensaver.ScreensaverTrigger
import com.twilighty.lmusic.presentation.viewmodel.PlayerViewModel
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreensaverOfferViewModel @Inject constructor(
    private val preferences: ScreensaverPreferences,
) : ViewModel() {
    val settings: StateFlow<ScreensaverSettings?> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dontAskAgain() {
        viewModelScope.launch { preferences.setOfferWhenCharging(false) }
    }
}

/**
 * "Turn on screensaver?" card: appears when the phone is charging, music is playing and the
 * phone is in landscape while LMusic is open. Everything here (battery receiver, orientation
 * sensor) runs only while the app is in the foreground; the sensor only while charging + playing.
 */
@Composable
fun ScreensaverOfferHost(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: ScreensaverOfferViewModel = hiltViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val offerEnabled = settings?.offerWhenCharging == true

    val playing by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    // Charging: sticky battery state + power connected/disconnected, only while started.
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, offerEnabled) {
        if (!offerEnabled) return@DisposableEffect onDispose { }
        val watcher = ChargingWatcher(context.applicationContext) { charging = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> watcher.start()
                Lifecycle.Event.ON_STOP -> watcher.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            watcher.stop()
        }
    }

    // Snooze ("Not now") until charging or playback resets.
    var snoozed by rememberSaveable { mutableStateOf(false) }
    if (!ScreensaverTrigger.snoozeStillValid(snoozed, charging, playing) && snoozed) snoozed = false

    val configLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenOn = remember(foreground) {
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
    }
    val base = ScreensaverTrigger.Conditions(
        offerEnabled = offerEnabled,
        charging = charging,
        playing = playing,
        landscape = configLandscape,
        foreground = foreground,
        screenOn = screenOn,
        snoozed = snoozed,
        screensaverActive = ScreensaverState.active,
    )

    // Rotation lock on: the UI stays portrait, so read the physical orientation from the
    // accelerometer — only while charging + playing + foreground.
    var sensorLandscape by remember { mutableStateOf(false) }
    val watchSensor = ScreensaverTrigger.shouldWatchOrientation(base) && !configLandscape
    DisposableEffect(watchSensor) {
        if (!watchSensor) { sensorLandscape = false; return@DisposableEffect onDispose { } }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val next = OrientationDetector.isLandscape(orientation, sensorLandscape)
                if (next != sensorLandscape) sensorLandscape = next
            }
        }
        runCatching { if (listener.canDetectOrientation()) listener.enable() }
        onDispose { runCatching { listener.disable() } }
    }

    val shouldOffer = ScreensaverTrigger.shouldOffer(base.copy(landscape = configLandscape || sensorLandscape))
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shouldOffer) {
        if (shouldOffer) {
            delay(ScreensaverTrigger.STABLE_FOR_MS)
            visible = true
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            .widthIn(max = 480.dp)
    ) {
        ScreensaverOfferCard(
            onStart = {
                visible = false
                snoozed = true
                ScreensaverActivity.start(context)
            },
            onNotNow = {
                visible = false
                snoozed = true
            },
            onDontAsk = {
                visible = false
                vm.dontAskAgain()
            }
        )
    }
}

@Composable
private fun ScreensaverOfferCard(onStart: () -> Unit, onNotNow: () -> Unit, onDontAsk: () -> Unit) {
    NbCard(modifier = Modifier.fillMaxWidth(), borderColor = Nb.borderStrong) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Turn on screensaver?", style = NbType.h3, color = Nb.text)
            Text(
                "A dim clock with what's playing, made for a phone on its charger.",
                style = NbType.meta,
                color = Nb.textMuted
            )
        }
        Spacer(Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NbButton(text = "Don't ask again", onClick = onDontAsk, style = NbButtonStyle.Quiet, size = NbButtonSize.Small)
            Spacer(Modifier.weight(1f))
            NbButton(text = "Not now", onClick = onNotNow, style = NbButtonStyle.Secondary, size = NbButtonSize.Small)
            NbButton(text = "Start", onClick = onStart, style = NbButtonStyle.Primary, size = NbButtonSize.Small)
        }
    }
}
