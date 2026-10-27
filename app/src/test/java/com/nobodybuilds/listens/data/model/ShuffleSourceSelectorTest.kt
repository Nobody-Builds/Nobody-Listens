package com.nobodybuilds.listens.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShuffleSourceSelectorTest {

    @Test
    fun `shuffle all keeps the whole filtered library, not a 500 song sample`() {
        val ids = (1L..10_000L).shuffled()
        val selected = ShuffleSourceSelector.selectIds(ids)
        assertEquals(10_000, selected.size)
        assertEquals(ids, selected)
    }

    @Test
    fun `duplicate ids are dropped once, order kept`() {
        assertEquals(listOf(3L, 1L, 2L), ShuffleSourceSelector.selectIds(listOf(3L, 1L, 3L, 2L, 1L)))
        assertTrue(ShuffleSourceSelector.selectIds(emptyList()).isEmpty())
    }

    @Test
    fun `album and artist shuffle respect the source filter`() {
        val local = Song.emptySong().copy(id = "1", contentUriString = "content://media/1")
        val cloud = Song.emptySong().copy(id = "-2", contentUriString = "telegram://1/2", telegramFileId = 2)
        val songs = listOf(local, cloud)
        assertEquals(listOf(local), ShuffleSourceSelector.selectSongs(StorageFilter.OFFLINE, songs))
        assertEquals(listOf(cloud), ShuffleSourceSelector.selectSongs(StorageFilter.ONLINE, songs))
        assertEquals(songs, ShuffleSourceSelector.selectSongs(StorageFilter.ALL, songs))
    }
}
