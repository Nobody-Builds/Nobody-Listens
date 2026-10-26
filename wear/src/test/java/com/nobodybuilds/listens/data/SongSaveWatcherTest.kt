package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.shared.WearTransferProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** "Save playlist to watch" fetches one song at a time; these are the ways a song's transfer can go. */
class SongSaveWatcherTest {

    private fun transfer(requestId: String, songId: String, status: String) =
        TransferState(requestId, songId, songTitle = "", bytesTransferred = 0, totalBytes = 0, status = status)

    private val running = WearTransferProgress.STATUS_TRANSFERRING

    @Test
    fun `waits while the request has not started, then reports the saved song`() {
        val watcher = SongSaveWatcher("s1", "r1")
        assertNull(watcher.update(emptySet(), emptyMap()))
        assertNull(watcher.update(emptySet(), mapOf("r1" to transfer("r1", "s1", running))))
        assertEquals(true, watcher.update(setOf("s1"), emptyMap()))
    }

    @Test
    fun `a failed transfer ends the wait without the song`() {
        val watcher = SongSaveWatcher("s1", "r1")
        assertEquals(false, watcher.update(emptySet(), mapOf("r1" to transfer("r1", "s1", WearTransferProgress.STATUS_FAILED))))
    }

    @Test
    fun `a cancelled transfer disappears instead of being marked, which also ends the wait`() {
        val watcher = SongSaveWatcher("s1", "r1")
        assertNull(watcher.update(emptySet(), mapOf("r1" to transfer("r1", "s1", running))))
        assertEquals(false, watcher.update(emptySet(), emptyMap()))
    }

    @Test
    fun `follows a transfer of the same song that was already running`() {
        val watcher = SongSaveWatcher("s1", "mine")
        assertNull(watcher.update(emptySet(), mapOf("other" to transfer("other", "s1", running))))
        assertEquals(true, watcher.update(setOf("s1"), emptyMap()))
    }

    @Test
    fun `an old failed transfer of the song does not end a new request`() {
        val watcher = SongSaveWatcher("s1", "new")
        assertNull(watcher.update(emptySet(), mapOf("old" to transfer("old", "s1", WearTransferProgress.STATUS_FAILED))))
        assertNull(watcher.update(emptySet(), mapOf("new" to transfer("new", "s1", running))))
        assertEquals(true, watcher.update(setOf("s1"), emptyMap()))
    }

    @Test
    fun `other songs' transfers are ignored`() {
        val watcher = SongSaveWatcher("s1", "r1")
        assertNull(watcher.update(setOf("s2"), mapOf("r2" to transfer("r2", "s2", WearTransferProgress.STATUS_FAILED))))
    }
}
