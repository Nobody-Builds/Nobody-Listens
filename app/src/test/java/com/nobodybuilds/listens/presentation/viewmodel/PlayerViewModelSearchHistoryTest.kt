package com.nobodybuilds.listens.presentation.viewmodel

import app.cash.turbine.test
import com.nobodybuilds.listens.data.model.SearchFilterType
import com.nobodybuilds.listens.data.model.SearchHistoryItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
@DisplayName("Search History")
class PlayerViewModelSearchHistoryTest : PlayerViewModelTestBase() {

    @Test
    fun `test_loadSearchHistory_updatesUiState`() = runTest {
        val historyItems = listOf(SearchHistoryItem(query = "q1", timestamp = 1L))
         // Mock the SearchStateHolder's loadSearchHistory to update the flow
        every { mockSearchStateHolder.loadSearchHistory(any()) } answers {
            _searchHistoryFlow.value = historyItems.toImmutableList()
        }

        playerViewModel.playerUiState.test {
            // Skip initial state from init
            val initialState = awaitItem()
            if (initialState.searchHistory == historyItems) {
                cancelAndConsumeRemainingEvents()
                return@test
            }

            playerViewModel.loadSearchHistory()
            
            val currentItem = awaitItem()
            assertEquals(historyItems, currentItem.searchHistory)
            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun `test_clearSearchHistory_callsRepository_andUpdatesUiState`() = runTest {
        // Initialize with some history
        val initialHistory = listOf(SearchHistoryItem(query = "q1", timestamp = 1L))
        _searchHistoryFlow.value = initialHistory.toImmutableList()
        
        // Mock clear behavior
        every { mockSearchStateHolder.clearSearchHistory() } answers {
            _searchHistoryFlow.value = persistentListOf()
        }

        playerViewModel.playerUiState.test {
           // Await initial state with history
           var state = awaitItem()
           while (state.searchHistory != initialHistory) {
               state = awaitItem()
           }

            playerViewModel.clearSearchHistory()

            var emitted = awaitItem()
            while (emitted.searchHistory.isNotEmpty()) {
                emitted = awaitItem()
            }
            assertTrue(emitted.searchHistory.isEmpty())
            verify { mockSearchStateHolder.clearSearchHistory() }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test_deleteSearchHistoryItem_callsRepository_andRefreshesHistory`() = runTest {
        val queryToDelete = "delete me"
        val keepQuery = "keep me"
        val initialHistory = listOf(SearchHistoryItem(query = queryToDelete, timestamp = 1L), SearchHistoryItem(query = keepQuery, timestamp = 2L))
        val finalHistory = listOf(SearchHistoryItem(query = keepQuery, timestamp = 2L))

        // Initial state
        _searchHistoryFlow.value = initialHistory.toImmutableList()
        
        // Mock delete behavior
        every { mockSearchStateHolder.deleteSearchHistoryItem(queryToDelete) } answers {
            _searchHistoryFlow.value = finalHistory.toImmutableList()
        }

        playerViewModel.playerUiState.test {
            // Await initial state
           var state = awaitItem()
           while (state.searchHistory != initialHistory) {
               state = awaitItem()
           }

            playerViewModel.deleteSearchHistoryItem(queryToDelete)

            var emitted = awaitItem()
            while (emitted.searchHistory != finalHistory) {
                emitted = awaitItem()
            }
            assertEquals(finalHistory, emitted.searchHistory)
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `test_updateSearchFilter_updatesUiState`() = runTest {
         val newFilter = SearchFilterType.ARTISTS
         
         // Mock update behavior
         coEvery { mockSearchStateHolder.updateSearchFilter(newFilter) } answers {
             _selectedSearchFilterFlow.value = newFilter
         }

        playerViewModel.playerUiState.test {
            skipItems(1) // Skip initial

            playerViewModel.updateSearchFilter(newFilter)
            
            val emittedItem = awaitItem()
            assertEquals(newFilter, emittedItem.selectedSearchFilter)
            cancelAndConsumeRemainingEvents()
        }
    }
}
