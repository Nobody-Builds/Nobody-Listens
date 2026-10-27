package com.nobodybuilds.listens.presentation.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Routes Compose's `LocalHapticFeedback` (used by Material components and older call sites) through the
 * Nobody Listens engine, so those haptics follow the Off / Subtle / Full / Strong level and feel as strong as the rest.
 */
class EngineHapticFeedback(private val haptics: NobodyListensHaptics) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val event = when (hapticFeedbackType) {
            HapticFeedbackType.LongPress -> HapticEvent.LongPress
            HapticFeedbackType.Confirm -> HapticEvent.Success
            HapticFeedbackType.Reject -> HapticEvent.Error
            HapticFeedbackType.ToggleOn -> HapticEvent.ToggleOn
            HapticFeedbackType.ToggleOff -> HapticEvent.ToggleOff
            HapticFeedbackType.GestureEnd -> HapticEvent.DragDrop
            HapticFeedbackType.GestureThresholdActivate -> HapticEvent.SwipeThreshold
            HapticFeedbackType.SegmentTick, HapticFeedbackType.TextHandleMove -> HapticEvent.Selection
            HapticFeedbackType.SegmentFrequentTick -> HapticEvent.DragSlot
            else -> HapticEvent.Tap // ContextClick, KeyboardTap, VirtualKey, future types
        }
        haptics.perform(event)
    }
}
