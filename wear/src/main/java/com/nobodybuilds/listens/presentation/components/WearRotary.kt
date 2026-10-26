package com.nobodybuilds.listens.presentation.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import timber.log.Timber

/**
 * Galaxy watches turn a bezel (rotating or touch) in clicks; other watches have a smooth crown.
 * Lists move one item per bezel click, like Samsung's own apps, and scroll freely with a crown.
 */
val isBezelWatch: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

/**
 * Bezel and crown input handled by the app instead of the lists' built-in default. Only the screen
 * or page on show passes [active], so pages in a pager and overlays never fight over the bezel; the
 * owner takes it again when the app comes back (after the keyboard or another app).
 */
@Composable
fun Modifier.rotaryInput(behavior: RotaryScrollableBehavior, active: Boolean): Modifier {
    if (!active) return this
    val focusRequester = remember { FocusRequester() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        withFrameNanos { } // the focus target attaches during the first frame
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Rotary input could not take focus")
        }
    }
    return rotaryScrollable(behavior = behavior, focusRequester = focusRequester)
}
