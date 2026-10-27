package com.nobodybuilds.listens.presentation.components.player

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.SongFeaturesDao
import com.nobodybuilds.listens.data.database.SongFeaturesEntity
import com.nobodybuilds.listens.data.smart.SmartPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Preference
// ---------------------------------------------------------------------------------------------

private val Context.livingArtworkDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_living_artwork")

@Singleton
class LivingArtworkPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val enabledKey = booleanPreferencesKey("enabled")

    val enabled: Flow<Boolean> = context.livingArtworkDataStore.data
        .map { it[enabledKey] ?: true }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        context.livingArtworkDataStore.edit { it[enabledKey] = enabled }
    }
}

// ---------------------------------------------------------------------------------------------
// ViewModel: setting + tempo of the current song (from the on-device smart layer)
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class LivingArtworkViewModel @Inject constructor(
    private val preferences: LivingArtworkPreferences,
    private val smartPreferences: SmartPreferences,
    private val songFeaturesDao: SongFeaturesDao,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferences.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _tempoBpm = MutableStateFlow(0f)
    /** Tempo of the song last passed to [loadTempo], or 0 when unknown / smart features off. */
    val tempoBpm: StateFlow<Float> = _tempoBpm

    private var tempoSongId: String? = null

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnabled(enabled) }
    }

    fun loadTempo(songId: String) {
        if (songId == tempoSongId) return
        tempoSongId = songId
        viewModelScope.launch {
            val bpm = runCatching {
                if (!smartPreferences.enabled.first()) return@runCatching 0f
                val features = songFeaturesDao.get(songId)
                if (features == null || features.status != SongFeaturesEntity.STATUS_OK) 0f
                else features.tempo
            }.getOrDefault(0f)
            if (tempoSongId == songId) _tempoBpm.value = bpm
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------------------------

private const val DEFAULT_BREATH_SECONDS = 4.5f
private const val BREATH_SCALE = 0.015f
private const val DRIFT_X_DP = 3f
private const val DRIFT_Y_DP = 2.2f
private const val MAX_ROTATION_DEG = 0.6f
/** Phase wraps at 12 breaths (LCM of the 1, 2, 3 and 4-breath sub-cycles) to keep floats precise. */
private const val PHASE_WRAP = 12f

/** Breath period: four beats, clamped to 2–6 s; 4.5 s when the tempo is unknown. */
internal fun breathPeriodSeconds(bpm: Float): Float =
    if (bpm <= 0f || bpm.isNaN()) DEFAULT_BREATH_SECONDS
    else (60f / bpm * 4f).coerceIn(2f, 6f)

/** True when the system "Remove animations" setting is on (animator duration scale 0). */
internal fun isSystemAnimationOff(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/**
 * Animation state for the living artwork. Values are only read from [apply] (layer phase), so
 * running the motion never recomposes the player.
 */
@Stable
class LivingArtworkState internal constructor() {
    internal var phase by mutableFloatStateOf(0f)
    internal val amplitude = Animatable(0f)

    fun apply(scope: androidx.compose.ui.graphics.GraphicsLayerScope, baseScale: Float) {
        val amp = amplitude.value
        if (amp <= 0f) {
            scope.scaleX = baseScale
            scope.scaleY = baseScale
            return
        }
        val p = phase
        val twoPi = 2f * PI.toFloat()
        val breath = 0.5f - 0.5f * cos(twoPi * p) // 0 → 1 → 0 over one period
        val s = baseScale * (1f + BREATH_SCALE * amp * breath)
        with(scope) {
            scaleX = s
            scaleY = s
            // Slow Lissajous drift (2- and 3-breath cycles) and a 4-breath sway.
            translationX = DRIFT_X_DP.dp.toPx() * amp * sin(twoPi * p / 2f)
            translationY = DRIFT_Y_DP.dp.toPx() * amp * sin(twoPi * p / 3f + PI.toFloat() / 4f)
            rotationZ = MAX_ROTATION_DEG * amp * sin(twoPi * p / 4f)
        }
    }
}

/**
 * Drives the breathing motion. Runs a frame loop only while [active] (or while easing back to
 * rest); otherwise no frames are requested.
 */
@Composable
fun rememberLivingArtworkState(
    active: Boolean,
    tempoBpm: Float,
): LivingArtworkState {
    val context = LocalContext.current
    val reduceMotion = remember { isSystemAnimationOff(context) }
    val state = remember { LivingArtworkState() }
    val periodSeconds by rememberUpdatedState(breathPeriodSeconds(tempoBpm))
    val runMotion = active && !reduceMotion

    LaunchedEffect(runMotion, reduceMotion) {
        if (reduceMotion) {
            state.amplitude.snapTo(0f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                state.amplitude.animateTo(
                    targetValue = if (runMotion) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (runMotion) 1400 else 900,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            var lastFrame = -1L
            while (isActive && (runMotion || state.amplitude.value > 0f || state.amplitude.isRunning)) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (lastFrame > 0L) {
                        val dt = (now - lastFrame) / 1_000_000_000f
                        var next = state.phase + dt / periodSeconds
                        if (next >= PHASE_WRAP) next -= PHASE_WRAP
                        state.phase = next
                    }
                    lastFrame = now
                }
            }
        }
    }
    return state
}

// ---------------------------------------------------------------------------------------------
// Double-tap heart
// ---------------------------------------------------------------------------------------------

@Stable
class HeartPopState internal constructor() {
    internal val scale = Animatable(0f)
    internal val alpha = Animatable(0f)
    internal var trigger by mutableIntStateOf(0)
    internal var liked by mutableStateOf(true)

    /** Shows the heart. [liked] picks a filled (liked) or outlined (removed) heart. */
    fun pop(liked: Boolean) {
        this.liked = liked
        trigger++
    }
}

@Composable
fun rememberHeartPopState(): HeartPopState = remember { HeartPopState() }

/** The heart that pops over the cover: scale 0 → 1.2 → 1 and fades out over ~700 ms. */
@Composable
fun HeartPopOverlay(state: HeartPopState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.trigger) {
        if (state.trigger == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                state.scale.snapTo(0f)
                state.scale.animateTo(1.2f, tween(180, easing = FastOutSlowInEasing))
                state.scale.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
            }
            launch {
                state.alpha.snapTo(1f)
                state.alpha.animateTo(1f, tween(380))
                state.alpha.animateTo(0f, tween(320, easing = LinearEasing))
            }
        }
    }
    if (state.trigger == 0) return
    Box(modifier = modifier) {
        Icon(
            painter = painterResource(
                if (state.liked) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24
            ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    val s = state.scale.value
                    scaleX = s
                    scaleY = s
                    alpha = state.alpha.value
                }
        )
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupLivingArtworkDataStore: DataStore<Preferences> get() = livingArtworkDataStore
