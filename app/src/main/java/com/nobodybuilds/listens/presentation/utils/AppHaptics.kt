package com.nobodybuilds.listens.presentation.utils

import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.view.ViewCompat
import android.view.HapticFeedbackConstants
import com.nobodybuilds.listens.presentation.haptics.HapticEvent
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHapticsEngine

@Immutable
data class AppHapticsConfig(
    val enabled: Boolean = true,
    // Reserved for future per-category/intensity controls.
    val intensityScale: Float = 1f
)

val LocalAppHapticsConfig = staticCompositionLocalOf { AppHapticsConfig() }

val NoOpHapticFeedback: HapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

fun View.performAppHapticFeedback(
    appHapticsConfig: AppHapticsConfig,
    feedbackConstant: Int,
    flags: Int = 0
): Boolean {
    if (!appHapticsConfig.enabled) return false
    if (performViaEngine(feedbackConstant)) return true
    return performHapticFeedback(feedbackConstant, flags)
}

fun performAppCompatHapticFeedback(
    view: View,
    appHapticsConfig: AppHapticsConfig,
    feedbackConstant: Int,
    flags: Int = 0
): Boolean {
    if (!appHapticsConfig.enabled) return false
    if (performViaEngine(feedbackConstant)) return true
    return ViewCompat.performHapticFeedback(view, feedbackConstant, flags)
}

/** Older call sites pass View constants; route them through the Nobody Listens engine so they follow the level. */
private fun performViaEngine(feedbackConstant: Int): Boolean {
    val engine = NobodyListensHapticsEngine.current() ?: return false
    val event = when (feedbackConstant) {
        HapticFeedbackConstants.LONG_PRESS -> HapticEvent.LongPress
        HapticFeedbackConstants.CONFIRM -> HapticEvent.Success
        HapticFeedbackConstants.REJECT -> HapticEvent.Error
        HapticFeedbackConstants.TOGGLE_ON -> HapticEvent.ToggleOn
        HapticFeedbackConstants.TOGGLE_OFF -> HapticEvent.ToggleOff
        HapticFeedbackConstants.GESTURE_START -> HapticEvent.DragLift
        HapticFeedbackConstants.GESTURE_END -> HapticEvent.DragDrop
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE -> HapticEvent.SwipeThreshold
        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK -> HapticEvent.DragSlot
        HapticFeedbackConstants.SEGMENT_TICK, HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.TEXT_HANDLE_MOVE -> HapticEvent.Selection
        else -> HapticEvent.Tap
    }
    engine.perform(event)
    return true
}
