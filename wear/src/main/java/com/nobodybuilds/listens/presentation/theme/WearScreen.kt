package com.nobodybuilds.listens.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Screen shape and size for the layouts that aren't a Horologist ScalingLazyColumn (which already adapts).
 * Sizes in the player are designed at 192 dp, the smallest common round watch, and grow from there.
 */
@Immutable
data class WearScreen(val isRound: Boolean, val widthDp: Int, val heightDp: Int) {

    /** Side inset that keeps a full-width line readable: round screens lose their corners, square ones don't. */
    val contentInset: Dp
        get() = if (isRound) (min(widthDp, heightDp) * ROUND_INSET_FRACTION).dp else SQUARE_INSET

    /** Multiplier for the main controls: 1 at 192 dp, up to 1.1 on large watches (more pushes rows past the curve). */
    val controlScale: Float
        get() = (min(widthDp, heightDp) / DESIGN_SIZE_DP).coerceIn(1f, MAX_SCALE)

    fun scaled(size: Dp): Dp = size * controlScale

    companion object {
        /** (1 - 1/sqrt 2) / 2: the margin of the square inscribed in the circle. */
        const val ROUND_INSET_FRACTION = 0.1464f
        val SQUARE_INSET = 12.dp
        const val DESIGN_SIZE_DP = 192f
        const val MAX_SCALE = 1.1f
    }
}

@Composable
fun rememberWearScreen(): WearScreen {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        WearScreen(
            isRound = configuration.isScreenRound,
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
        )
    }
}
