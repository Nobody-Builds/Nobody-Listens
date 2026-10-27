package com.nobodybuilds.listens.presentation.haptics

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Fires [NobodyListensHaptics.pageChange] when a pager's current page changes.
 *
 * @param userDragOnly only fire while the user is dragging the pager. Use it when a tab chip / button
 *   already gives feedback for programmatic page changes, so nothing double fires.
 * @param onSettledPage optional hook for page-specific feedback (e.g. `celebration()` on a summary page).
 */
@Composable
fun HapticPagerEffect(
    pagerState: PagerState,
    userDragOnly: Boolean = false,
    onSettledPage: ((page: Int, haptics: NobodyListensHaptics) -> Unit)? = null,
) {
    val haptics = LocalNobodyListensHaptics.current
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val isDragged by rememberUpdatedState(dragged)
    val settledHook by rememberUpdatedState(onSettledPage)
    LaunchedEffect(pagerState, userDragOnly) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { if (!userDragOnly || isDragged) haptics.pageChange() }
    }
    if (onSettledPage != null) {
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .drop(1)
                .collect { page -> settledHook?.invoke(page, haptics) }
        }
    }
}
