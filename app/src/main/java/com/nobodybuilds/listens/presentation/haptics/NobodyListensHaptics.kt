package com.nobodybuilds.listens.presentation.haptics

import android.content.Context
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * How much haptic feedback the app gives.
 *
 * - [OFF]: nothing.
 * - [SUBTLE]: lighter, only key moments (no taps, ticks or sheet feedback).
 * - [FULL]: crisp feedback everywhere (about 1.35x the old intensity).
 * - [STRONG]: the default for new installs. Punchy, richer multi-primitive patterns (about 1.7x).
 */
enum class HapticsLevel { OFF, SUBTLE, FULL, STRONG }

/** Semantic haptic events. Call sites say *what happened*, the engine decides how it feels. */
enum class HapticEvent {
    PlayPause,
    Skip,
    SeekTick,
    SeekEdge,
    ScrubStart,
    ScrubEnd,
    VolumeStep,
    ToggleOn,
    ToggleOff,
    LongPress,
    Like,
    Unlike,
    DragLift,
    DragSlot,
    DragDrop,
    SwipeThreshold,
    RefreshTrigger,
    Success,
    Error,
    Selection,
    SheetSnap,
    /** Very light tick for plain buttons. Full and Strong only. */
    Tap,
    /** A bottom sheet / dialog-like surface finished opening. Full and Strong only. */
    SheetOpen,
    /** A bottom sheet finished closing (dismissed or swiped away). Full and Strong only. */
    SheetClose,
    /** A pager settled on a new page (recap, onboarding, tabs). Full and Strong only. */
    PageChange,
    /** A short joyful pattern for milestones, achievements, the garden. */
    Celebration,
    /** A very soft double tick for timers (focus timer end / phase change). */
    Chime,
    /** A lub-dub "heartbeat" for double-tap like on artwork. */
    Heartbeat,
}

/**
 * The app-wide haptics API. Use [LocalNobodyListensHaptics] in Compose, or [NobodyListensHaptics.get] elsewhere.
 *
 * Pick the event that describes what happened:
 * - [playPause], [skip]: transport controls.
 * - [tap]: plain button / menu item taps (NbButton, NbIconButton already call it — don't double fire).
 * - [selection]: chips, segmented controls, tabs, list choices (NbChip, NbSegmented already call it).
 * - [toggle]/[toggleOn]/[toggleOff]: switches (NbSwitch already calls it).
 * - [success]: a confirm dialog's positive action, playlist created, saved. [successLight] for small
 *   positive actions such as "add to queue" / "play next".
 * - [error]: something failed / was rejected.
 * - [longPress]: long-press menus.
 * - [like] / [heartbeat]: favourite toggles; heartbeat for double-tap-to-like on artwork.
 * - [dragLift]/[dragSlot]/[dragDrop] (aliases [reorderPick]/[reorderDrop]): reorderable lists.
 * - [sheetOpen]/[sheetClose]/[sheetSnap]: bottom sheets. Prefer `HapticBottomSheetEffect(sheetState)`.
 * - [pageChange]: a pager settled on a new page.
 * - [celebration]: milestones, streaks, garden growth, recap reveals.
 * - [chime]: very soft, for timers (focus timer, sleep timer end).
 * - [seekTick], [seekEdge], [scrubStart], [scrubEnd], [volumeStep]: sliders.
 *
 * All calls are cheap, thread-safe and rate limited; they never throw.
 */
interface NobodyListensHaptics {
    fun perform(event: HapticEvent)

    fun playPause() = perform(HapticEvent.PlayPause)
    fun skip() = perform(HapticEvent.Skip)
    fun seekTick() = perform(HapticEvent.SeekTick)
    fun seekEdge() = perform(HapticEvent.SeekEdge)
    fun scrubStart() = perform(HapticEvent.ScrubStart)
    fun scrubEnd() = perform(HapticEvent.ScrubEnd)
    fun volumeStep() = perform(HapticEvent.VolumeStep)
    fun toggle(on: Boolean) = perform(if (on) HapticEvent.ToggleOn else HapticEvent.ToggleOff)
    fun toggleOn() = perform(HapticEvent.ToggleOn)
    fun toggleOff() = perform(HapticEvent.ToggleOff)
    fun longPress() = perform(HapticEvent.LongPress)
    fun like(liked: Boolean) = perform(if (liked) HapticEvent.Like else HapticEvent.Unlike)
    fun dragLift() = perform(HapticEvent.DragLift)
    fun dragSlot() = perform(HapticEvent.DragSlot)
    fun dragDrop() = perform(HapticEvent.DragDrop)
    fun swipeThreshold() = perform(HapticEvent.SwipeThreshold)
    fun refreshTrigger() = perform(HapticEvent.RefreshTrigger)
    fun success() = perform(HapticEvent.Success)
    /** A lighter positive confirmation (add to queue, play next). Same feel as a toggle-on. */
    fun successLight() = perform(HapticEvent.ToggleOn)
    fun error() = perform(HapticEvent.Error)
    fun selection() = perform(HapticEvent.Selection)
    fun sheetSnap() = perform(HapticEvent.SheetSnap)
    fun tap() = perform(HapticEvent.Tap)
    fun sheetOpen() = perform(HapticEvent.SheetOpen)
    fun sheetClose() = perform(HapticEvent.SheetClose)
    fun pageChange() = perform(HapticEvent.PageChange)
    /** Alias of [dragLift] for reorderable lists. */
    fun reorderPick() = perform(HapticEvent.DragLift)
    /** Alias of [dragDrop] for reorderable lists. */
    fun reorderDrop() = perform(HapticEvent.DragDrop)
    fun celebration() = perform(HapticEvent.Celebration)
    fun chime() = perform(HapticEvent.Chime)
    fun heartbeat() = perform(HapticEvent.Heartbeat)

    companion object {
        /** Non-Compose accessor (services, view models, receivers). No-op until the engine is created. */
        fun get(): NobodyListensHaptics = Delegating

        internal object Delegating : NobodyListensHaptics {
            override fun perform(event: HapticEvent) {
                NobodyListensHapticsEngine.instance?.perform(event)
            }
        }
    }
}

/** Provided at the root of MainActivity. Falls back to the singleton engine when not provided. */
val LocalNobodyListensHaptics = staticCompositionLocalOf<NobodyListensHaptics> { NobodyListensHaptics.get() }

/** Shorthand for `LocalNobodyListensHaptics.current`. */
val nobodyListensHaptics: NobodyListensHaptics
    @Composable
    @ReadOnlyComposable
    get() = LocalNobodyListensHaptics.current

// ---------------------------------------------------------------- Preferences

private val Context.hapticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_haptics")

@Singleton
class HapticsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LEVEL = stringPreferencesKey("level")
        val SEEK_TICKS = booleanPreferencesKey("seek_ticks")
    }

    val level: Flow<HapticsLevel> = context.hapticsDataStore.data
        .map { prefs ->
            // No stored level = new install (or never changed) -> Strong. A stored "FULL" stays Full.
            prefs[Keys.LEVEL]?.let { raw -> HapticsLevel.entries.firstOrNull { it.name == raw } }
                ?: HapticsLevel.STRONG
        }
        .distinctUntilChanged()

    val seekTicks: Flow<Boolean> = context.hapticsDataStore.data
        .map { it[Keys.SEEK_TICKS] ?: true }
        .distinctUntilChanged()

    suspend fun setLevel(level: HapticsLevel) {
        context.hapticsDataStore.edit { it[Keys.LEVEL] = level.name }
    }

    suspend fun setSeekTicks(enabled: Boolean) {
        context.hapticsDataStore.edit { it[Keys.SEEK_TICKS] = enabled }
    }
}

// ---------------------------------------------------------------- Engine

/**
 * Central haptics engine.
 *
 * Order of preference for every event:
 * 1. `VibrationEffect.Composition` primitives, chosen per level. Primitives the device does not support are
 *    dropped (their delay is kept) so a partly supported pattern still plays,
 * 2. a predefined effect (`EFFECT_TICK/CLICK/HEAVY_CLICK/DOUBLE_CLICK`; Strong uses HEAVY_CLICK for big events),
 * 3. `View.performHapticFeedback` with a matching constant on the attached root view.
 *
 * Vibrations use `USAGE_TOUCH`, so the system "Touch feedback" setting and intensity still apply.
 * Light ticks are rate limited so drags never turn into a buzz, and long patterns can't overlap themselves.
 */
@Singleton
class NobodyListensHapticsEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: HapticsPreferences,
    private val userPreferencesRepository: UserPreferencesRepository,
) : NobodyListensHaptics {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val vibrator: Vibrator? = runCatching {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    }.getOrNull()?.takeIf { it.hasVibrator() }

    private val touchAttributes: VibrationAttributes =
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private var viewRef: WeakReference<View>? = null

    /** Effective level: the legacy master switch (hapticsEnabled) wins when it is off. */
    val level: StateFlow<HapticsLevel> = combine(
        userPreferencesRepository.hapticsEnabledFlow,
        preferences.level
    ) { enabled, level -> if (!enabled) HapticsLevel.OFF else level }
        .stateIn(scope, SharingStarted.Eagerly, HapticsLevel.STRONG)

    val seekTicksEnabled: StateFlow<Boolean> = preferences.seekTicks
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val lastEventTime = LongArray(HapticEvent.entries.size)
    private var lastLightTickTime = 0L
    private val primitiveSupport = HashMap<Int, Boolean>()

    init {
        instance = this
    }

    fun attachView(view: View) {
        viewRef = WeakReference(view)
    }

    fun setLevel(newLevel: HapticsLevel) {
        scope.launch {
            if (newLevel != HapticsLevel.OFF) preferences.setLevel(newLevel)
            userPreferencesRepository.setHapticsEnabled(newLevel != HapticsLevel.OFF)
        }
    }

    fun setSeekTicks(enabled: Boolean) {
        scope.launch { preferences.setSeekTicks(enabled) }
    }

    /**
     * Plays [event] at an explicit [previewLevel], ignoring the saved level and rate limits.
     * Used by the settings screen so a level change is felt at the NEW level straight away.
     */
    fun preview(previewLevel: HapticsLevel, event: HapticEvent = HapticEvent.ToggleOn) {
        if (previewLevel == HapticsLevel.OFF) {
            runCatching { vibrator?.cancel() }
            return
        }
        play(event, previewLevel)
    }

    override fun perform(event: HapticEvent) {
        val currentLevel = level.value
        if (currentLevel == HapticsLevel.OFF) return
        if (!isAllowed(event, currentLevel)) return
        if (!passesRateLimit(event)) return
        play(event, currentLevel)
    }

    private fun play(event: HapticEvent, level: HapticsLevel) {
        try {
            if (playComposition(event, level)) return
            if (playPredefined(event, level)) return
            playViewFallback(event)
        } catch (_: Throwable) {
            // Haptics must never crash the app.
        }
    }

    private fun isAllowed(event: HapticEvent, level: HapticsLevel): Boolean {
        val rich = level == HapticsLevel.FULL || level == HapticsLevel.STRONG
        return when (event) {
            HapticEvent.SeekTick -> rich && seekTicksEnabled.value
            HapticEvent.Tap, HapticEvent.SheetSnap, HapticEvent.DragSlot, HapticEvent.VolumeStep,
            HapticEvent.SheetOpen, HapticEvent.SheetClose, HapticEvent.PageChange -> rich
            else -> true
        }
    }

    private fun isLightTick(event: HapticEvent) = when (event) {
        HapticEvent.SeekTick, HapticEvent.VolumeStep, HapticEvent.DragSlot,
        HapticEvent.Selection, HapticEvent.SheetSnap, HapticEvent.Tap, HapticEvent.PageChange -> true
        else -> false
    }

    /** Same-event window. Long patterns and sheet settles get a longer one so they never stack. */
    private fun sameEventWindow(event: HapticEvent): Long = when (event) {
        HapticEvent.Celebration, HapticEvent.Heartbeat, HapticEvent.Chime -> 450L
        HapticEvent.SheetOpen, HapticEvent.SheetClose -> 250L
        HapticEvent.Success, HapticEvent.Error -> 150L
        HapticEvent.PageChange -> 80L
        else -> SAME_EVENT_WINDOW_MS
    }

    @Synchronized
    private fun passesRateLimit(event: HapticEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        val index = event.ordinal
        // The same event twice within a short window is almost always a double trigger.
        if (now - lastEventTime[index] < sameEventWindow(event)) return false
        if (isLightTick(event) && now - lastLightTickTime < MIN_TICK_INTERVAL_MS) return false
        lastEventTime[index] = now
        if (isLightTick(event)) lastLightTickTime = now
        return true
    }

    private data class Primitive(val id: Int, val scale: Float, val delayMs: Int = 0)

    /** The base ("old Full") patterns. Subtle/Full/Strong scale these unless Strong has its own. */
    private fun basePrimitives(event: HapticEvent): List<Primitive> = when (event) {
        HapticEvent.PlayPause -> listOf(Primitive(CLICK, 0.7f))
        HapticEvent.Skip -> listOf(Primitive(QUICK_RISE, 0.25f), Primitive(CLICK, 0.5f))
        HapticEvent.SeekTick -> listOf(Primitive(TICK, 0.35f))
        HapticEvent.SeekEdge -> listOf(Primitive(THUD, 0.55f))
        HapticEvent.ScrubStart -> listOf(Primitive(TICK, 0.6f))
        HapticEvent.ScrubEnd -> listOf(Primitive(CLICK, 0.4f))
        HapticEvent.VolumeStep -> listOf(Primitive(LOW_TICK, 0.5f))
        HapticEvent.ToggleOn -> listOf(Primitive(CLICK, 0.6f))
        HapticEvent.ToggleOff -> listOf(Primitive(LOW_TICK, 0.8f))
        HapticEvent.LongPress -> listOf(Primitive(THUD, 0.6f))
        HapticEvent.Like -> listOf(Primitive(QUICK_RISE, 0.4f), Primitive(CLICK, 0.9f, delayMs = 30))
        HapticEvent.Unlike -> listOf(Primitive(QUICK_FALL, 0.5f))
        HapticEvent.DragLift -> listOf(Primitive(QUICK_RISE, 0.3f), Primitive(TICK, 0.7f))
        HapticEvent.DragSlot -> listOf(Primitive(TICK, 0.45f))
        HapticEvent.DragDrop -> listOf(Primitive(CLICK, 0.65f))
        HapticEvent.SwipeThreshold -> listOf(Primitive(CLICK, 0.55f))
        HapticEvent.RefreshTrigger -> listOf(Primitive(SLOW_RISE, 0.3f), Primitive(CLICK, 0.7f))
        HapticEvent.Success -> listOf(Primitive(TICK, 0.5f), Primitive(CLICK, 0.8f, delayMs = 60))
        HapticEvent.Error -> listOf(
            Primitive(CLICK, 0.8f),
            Primitive(CLICK, 0.8f, delayMs = 80),
            Primitive(QUICK_FALL, 0.4f, delayMs = 20)
        )
        HapticEvent.Selection -> listOf(Primitive(TICK, 0.5f))
        HapticEvent.SheetSnap -> listOf(Primitive(LOW_TICK, 0.6f))
        HapticEvent.Tap -> listOf(Primitive(TICK, 0.3f))
        HapticEvent.SheetOpen -> listOf(Primitive(QUICK_RISE, 0.25f), Primitive(CLICK, 0.4f))
        HapticEvent.SheetClose -> listOf(Primitive(QUICK_FALL, 0.4f), Primitive(LOW_TICK, 0.3f, delayMs = 10))
        HapticEvent.PageChange -> listOf(Primitive(TICK, 0.45f))
        HapticEvent.Celebration -> listOf(
            Primitive(TICK, 0.35f),
            Primitive(TICK, 0.5f, delayMs = 60),
            Primitive(CLICK, 0.6f, delayMs = 60),
            Primitive(QUICK_RISE, 0.35f, delayMs = 40),
            Primitive(CLICK, 0.75f, delayMs = 20)
        )
        HapticEvent.Chime -> listOf(Primitive(TICK, 0.2f), Primitive(TICK, 0.15f, delayMs = 140))
        HapticEvent.Heartbeat -> listOf(Primitive(CLICK, 0.6f), Primitive(LOW_TICK, 0.45f, delayMs = 110))
    }

    /** Hand-tuned, richer patterns for Strong. Null = base pattern x [STRONG_SCALE]. */
    private fun strongPrimitives(event: HapticEvent): List<Primitive>? = when (event) {
        HapticEvent.PlayPause -> listOf(Primitive(CLICK, 1f), Primitive(LOW_TICK, 0.6f, delayMs = 30))
        HapticEvent.Skip -> listOf(Primitive(QUICK_RISE, 0.7f), Primitive(CLICK, 1f))
        HapticEvent.Like -> listOf(
            Primitive(CLICK, 0.8f),
            Primitive(CLICK, 1f, delayMs = 60),
            Primitive(THUD, 0.7f, delayMs = 40)
        )
        HapticEvent.Unlike -> listOf(Primitive(QUICK_FALL, 0.8f))
        HapticEvent.ToggleOn -> listOf(Primitive(QUICK_RISE, 0.5f), Primitive(CLICK, 1f))
        HapticEvent.ToggleOff -> listOf(Primitive(QUICK_FALL, 0.85f))
        HapticEvent.Success -> listOf(
            Primitive(TICK, 0.6f),
            Primitive(CLICK, 0.75f, delayMs = 55),
            Primitive(CLICK, 1f, delayMs = 55)
        )
        HapticEvent.Error -> listOf(Primitive(THUD, 1f), Primitive(LOW_TICK, 0.8f, delayMs = 60))
        HapticEvent.LongPress -> listOf(Primitive(SLOW_RISE, 0.6f), Primitive(CLICK, 1f))
        HapticEvent.SeekTick -> listOf(Primitive(TICK, 0.7f))
        HapticEvent.SheetSnap -> listOf(Primitive(THUD, 0.6f))
        HapticEvent.Tap -> listOf(Primitive(TICK, 0.55f))
        HapticEvent.Selection -> listOf(Primitive(TICK, 0.8f))
        HapticEvent.DragLift -> listOf(Primitive(QUICK_RISE, 0.55f), Primitive(CLICK, 0.85f))
        HapticEvent.DragDrop -> listOf(Primitive(CLICK, 1f), Primitive(LOW_TICK, 0.5f, delayMs = 25))
        HapticEvent.SheetOpen -> listOf(Primitive(QUICK_RISE, 0.45f), Primitive(CLICK, 0.75f))
        HapticEvent.SheetClose -> listOf(Primitive(QUICK_FALL, 0.7f), Primitive(LOW_TICK, 0.5f, delayMs = 15))
        HapticEvent.PageChange -> listOf(Primitive(TICK, 0.75f))
        HapticEvent.Celebration -> listOf(
            Primitive(TICK, 0.6f),
            Primitive(TICK, 0.8f, delayMs = 55),
            Primitive(CLICK, 0.9f, delayMs = 55),
            Primitive(QUICK_RISE, 0.6f, delayMs = 40),
            Primitive(CLICK, 1f, delayMs = 20),
            Primitive(THUD, 0.5f, delayMs = 60)
        )
        // Chime stays soft on purpose: it fires while the user is focusing.
        HapticEvent.Chime -> listOf(Primitive(TICK, 0.3f), Primitive(TICK, 0.22f, delayMs = 140))
        HapticEvent.Heartbeat -> listOf(
            Primitive(CLICK, 1f),
            Primitive(THUD, 0.7f, delayMs = 100),
            Primitive(LOW_TICK, 0.5f, delayMs = 220)
        )
        else -> null
    }

    private fun primitivesFor(event: HapticEvent, level: HapticsLevel): List<Primitive> {
        if (level == HapticsLevel.STRONG) strongPrimitives(event)?.let { return it }
        val scale = when (level) {
            HapticsLevel.SUBTLE -> SUBTLE_SCALE
            HapticsLevel.FULL -> FULL_SCALE
            HapticsLevel.STRONG -> STRONG_SCALE
            HapticsLevel.OFF -> 0f
        }
        // Chime must stay very soft at every level.
        val effective = if (event == HapticEvent.Chime) scale.coerceAtMost(1.2f) else scale
        return basePrimitives(event).map { it.copy(scale = it.scale * effective) }
    }

    private fun isPrimitiveSupported(vib: Vibrator, id: Int): Boolean = synchronized(primitiveSupport) {
        primitiveSupport.getOrPut(id) { vib.areAllPrimitivesSupported(id) }
    }

    private fun playComposition(event: HapticEvent, level: HapticsLevel): Boolean {
        val vib = vibrator ?: return false
        val composition = VibrationEffect.startComposition()
        var carriedDelay = 0
        var added = 0
        primitivesFor(event, level).forEach { p ->
            if (isPrimitiveSupported(vib, p.id)) {
                composition.addPrimitive(p.id, p.scale.coerceIn(0.05f, 1f), p.delayMs + carriedDelay)
                carriedDelay = 0
                added++
            } else {
                carriedDelay += p.delayMs
            }
        }
        if (added == 0) return false
        vib.vibrate(composition.compose(), touchAttributes)
        return true
    }

    private fun predefinedFor(event: HapticEvent, level: HapticsLevel): Int {
        val strong = level == HapticsLevel.STRONG
        return when (event) {
            HapticEvent.SeekTick, HapticEvent.VolumeStep, HapticEvent.DragSlot, HapticEvent.Selection,
            HapticEvent.SheetSnap, HapticEvent.Tap, HapticEvent.ScrubStart, HapticEvent.ToggleOff,
            HapticEvent.Unlike, HapticEvent.PageChange, HapticEvent.Chime, HapticEvent.SheetClose ->
                VibrationEffect.EFFECT_TICK
            HapticEvent.LongPress, HapticEvent.SeekEdge -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticEvent.Like, HapticEvent.Success, HapticEvent.Error, HapticEvent.Celebration,
            HapticEvent.Heartbeat -> VibrationEffect.EFFECT_DOUBLE_CLICK
            else -> if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
        }
    }

    private fun playPredefined(event: HapticEvent, level: HapticsLevel): Boolean {
        val vib = vibrator ?: return false
        val effect = predefinedFor(event, level)
        if (vib.areAllEffectsSupported(effect) == Vibrator.VIBRATION_EFFECT_SUPPORT_NO) return false
        vib.vibrate(VibrationEffect.createPredefined(effect), touchAttributes)
        return true
    }

    private fun viewConstantFor(event: HapticEvent): Int = when (event) {
        HapticEvent.SeekTick, HapticEvent.VolumeStep, HapticEvent.Tap, HapticEvent.Chime ->
            HapticFeedbackConstants.CLOCK_TICK
        HapticEvent.Selection, HapticEvent.DragSlot, HapticEvent.SheetSnap, HapticEvent.PageChange ->
            HapticFeedbackConstants.SEGMENT_TICK
        HapticEvent.ToggleOn, HapticEvent.Like, HapticEvent.Heartbeat -> HapticFeedbackConstants.TOGGLE_ON
        HapticEvent.ToggleOff, HapticEvent.Unlike -> HapticFeedbackConstants.TOGGLE_OFF
        HapticEvent.ScrubStart, HapticEvent.DragLift, HapticEvent.SheetOpen -> HapticFeedbackConstants.GESTURE_START
        HapticEvent.ScrubEnd, HapticEvent.DragDrop, HapticEvent.SheetClose -> HapticFeedbackConstants.GESTURE_END
        HapticEvent.Success, HapticEvent.RefreshTrigger, HapticEvent.SwipeThreshold,
        HapticEvent.Celebration -> HapticFeedbackConstants.CONFIRM
        HapticEvent.Error -> HapticFeedbackConstants.REJECT
        HapticEvent.LongPress, HapticEvent.SeekEdge -> HapticFeedbackConstants.LONG_PRESS
        HapticEvent.PlayPause, HapticEvent.Skip -> HapticFeedbackConstants.CONTEXT_CLICK
    }

    private fun playViewFallback(event: HapticEvent) {
        val view = viewRef?.get() ?: return
        val constant = viewConstantFor(event)
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            view.performHapticFeedback(constant)
        } else {
            view.post { view.performHapticFeedback(constant) }
        }
    }

    companion object {
        private const val SUBTLE_SCALE = 0.55f
        private const val FULL_SCALE = 1.35f
        private const val STRONG_SCALE = 1.7f
        private const val MIN_TICK_INTERVAL_MS = 35L
        private const val SAME_EVENT_WINDOW_MS = 30L


        @Volatile
        internal var instance: NobodyListensHapticsEngine? = null
            private set

        /** The engine once Hilt has created it, else null. */
        fun current(): NobodyListensHapticsEngine? = instance
    }
}

// Composition primitive ids (plain vals: KSP chokes on const vals that reference Java fields here).
private val CLICK: Int get() = VibrationEffect.Composition.PRIMITIVE_CLICK
private val TICK: Int get() = VibrationEffect.Composition.PRIMITIVE_TICK
private val LOW_TICK: Int get() = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
private val THUD: Int get() = VibrationEffect.Composition.PRIMITIVE_THUD
private val QUICK_RISE: Int get() = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
private val QUICK_FALL: Int get() = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
private val SLOW_RISE: Int get() = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupHapticsDataStore: DataStore<Preferences> get() = hapticsDataStore
