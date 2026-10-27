package com.nobodybuilds.listens.presentation.haptics

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Fires [NobodyListensHaptics.sheetOpen] / [NobodyListensHaptics.sheetClose] / [NobodyListensHaptics.sheetSnap] when a
 * Material3 bottom sheet *settles*. Call it right next to a `ModalBottomSheet(sheetState = …)`:
 *
 * ```
 * val sheetState = rememberModalBottomSheetState()
 * HapticBottomSheetEffect(sheetState)
 * ModalBottomSheet(sheetState = sheetState, …) { … }
 * ```
 *
 * If the sheet leaves composition while it was visible (dismissed via state flag), a close is fired once.
 * The engine rate limits sheet events, so a settle + dispose never double fires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticBottomSheetEffect(sheetState: SheetState) {
    val haptics = LocalNobodyListensHaptics.current
    val tracker = remember { SheetTracker() }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }
            .distinctUntilChanged()
            .collect { value ->
                val previous = tracker.last
                tracker.last = value
                if (previous == null) {
                    // First observed value: a sheet composed already open still counts as an open.
                    if (value != SheetValue.Hidden) haptics.sheetOpen()
                    return@collect
                }
                when {
                    previous == SheetValue.Hidden && value != SheetValue.Hidden -> haptics.sheetOpen()
                    previous != SheetValue.Hidden && value == SheetValue.Hidden -> haptics.sheetClose()
                    previous != value -> haptics.sheetSnap()
                }
            }
    }
    DisposableEffect(sheetState) {
        onDispose {
            if (tracker.last != null && tracker.last != SheetValue.Hidden) haptics.sheetClose()
            tracker.last = SheetValue.Hidden
        }
    }
}

/**
 * For sheets / overlays whose state isn't reachable: fires [NobodyListensHaptics.sheetOpen] when it enters
 * composition and [NobodyListensHaptics.sheetClose] when it leaves. Place it inside the sheet's content.
 */
@Composable
fun HapticSheetLifecycleEffect() {
    val haptics = LocalNobodyListensHaptics.current
    DisposableEffect(Unit) {
        haptics.sheetOpen()
        onDispose { haptics.sheetClose() }
    }
}

private class SheetTracker {
    @OptIn(ExperimentalMaterial3Api::class)
    var last: SheetValue? = null
}

/**
 * For custom sheets driven by a visibility flag (e.g. the queue sheet): fires [NobodyListensHaptics.sheetOpen]
 * when [visible] turns true and [NobodyListensHaptics.sheetClose] when it turns false. The first value is ignored.
 */
@Composable
fun HapticVisibilityEffect(visible: Boolean) {
    val haptics = LocalNobodyListensHaptics.current
    val previous = remember { booleanArrayOf(visible) }
    LaunchedEffect(visible) {
        if (previous[0] != visible) {
            if (visible) haptics.sheetOpen() else haptics.sheetClose()
            previous[0] = visible
        }
    }
}
