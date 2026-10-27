package com.nobodybuilds.listens.presentation.viewmodel

import app.cash.turbine.test
import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PlayerViewModel startup and navigation tests. Genre covers, shuffle and search history live in
 * PlayerViewModelGenreTest, PlayerViewModelShuffleTest and PlayerViewModelSearchHistoryTest.
 */
@ExperimentalCoroutinesApi
class PlayerViewModelTest : PlayerViewModelTestBase() {

    @Test
    fun `repeat preference changes after startup are not pushed back into controller`() = runTest {
        advanceUntilIdle()
        controllerRepeatModeWrites.clear()

        _repeatModeFlow.value = Player.REPEAT_MODE_ONE
        advanceUntilIdle()

        assertTrue(controllerRepeatModeWrites.isEmpty())
        assertEquals(Player.REPEAT_MODE_OFF, controllerRepeatMode)
    }

    @Test
    fun `album navigation from player accepts synthetic negative album ids`() = runTest {
        playerViewModel.albumNavigationRequests.test {
            playerViewModel.triggerAlbumNavigationFromPlayer(-42L)
            advanceUntilIdle()

            assertEquals(-42L, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `album navigation from player still ignores sentinel album id`() = runTest {
        playerViewModel.albumNavigationRequests.test {
            playerViewModel.triggerAlbumNavigationFromPlayer(-1L)
            advanceUntilIdle()

            expectNoEvents()
        }
    }
}
