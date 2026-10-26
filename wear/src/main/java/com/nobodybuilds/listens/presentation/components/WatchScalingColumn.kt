package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnState

/**
 * The app's scrolling list: Horologist's responsive layout ([columnState]) with our own bezel
 * handling ([rotaryInput]) in place of the built-in one. Pass [rotaryActive] = false while another
 * page or an overlay owns the bezel.
 */
@Composable
fun WatchScalingColumn(
    columnState: ScalingLazyColumnState,
    modifier: Modifier = Modifier,
    rotaryActive: Boolean = true,
    content: ScalingLazyListScope.() -> Unit,
) {
    val rotary = if (isBezelWatch) {
        RotaryScrollableDefaults.snapBehavior(scrollableState = columnState.state, hapticFeedbackEnabled = true)
    } else {
        RotaryScrollableDefaults.behavior(scrollableState = columnState.state, hapticFeedbackEnabled = true)
    }
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize().rotaryInput(rotary, rotaryActive),
        state = columnState.state,
        contentPadding = columnState.contentPadding,
        reverseLayout = columnState.reverseLayout,
        verticalArrangement = columnState.verticalArrangement,
        horizontalAlignment = columnState.horizontalAlignment,
        flingBehavior = ScrollableDefaults.flingBehavior(),
        userScrollEnabled = columnState.userScrollEnabled,
        scalingParams = columnState.scalingParams,
        anchorType = columnState.anchorType,
        autoCentering = columnState.autoCentering,
        rotaryScrollableBehavior = null,
        content = content,
    )
}
