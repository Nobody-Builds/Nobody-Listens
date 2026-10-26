package com.nobodybuilds.listens.data.service.wear

import com.nobodybuilds.listens.data.jiosaavn.JioSaavnLibrary
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicLibrary
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.shared.WearHandoffSong
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Switching the watch's output to the phone carries its queue over. */
class WatchHandoffQueueTest {

    private val repository = mockk<MusicRepository>()
    private val youTube = mockk<YouTubeMusicLibrary>(relaxed = true)
    private val jioSaavn = mockk<JioSaavnLibrary>(relaxed = true)
    private val handoff = WatchHandoffQueue(repository, youTube, jioSaavn)

    private fun phoneSong(id: String) = mockk<Song> { every { this@mockk.id } returns id }

    @Test
    fun `phone songs and both streaming services carry over in order`() = runTest {
        every { repository.getSongsByIds(listOf("12")) } returns flowOf(listOf(phoneSong("12")))
        val queue = listOf(
            WearHandoffSong("12", "Saved from phone"),
            WearHandoffSong("yt:dQw4w9WgXcQ", "On YouTube", artist = "A"),
            WearHandoffSong("jiosaavn:abc", "On JioSaavn", artist = "B"),
        )

        val (songs, start) = handoff.resolve(queue, currentIndex = 1)!!

        assertEquals(listOf("12", YouTubeMusicUris.songId("dQw4w9WgXcQ").toString(), JioSaavnUris.songId("abc").toString()), songs.map { it.id })
        assertEquals(1, start)
        verify { youTube.remember(match { it.single().videoId == "dQw4w9WgXcQ" }) }
        verify { jioSaavn.remember(match { it.single().id == "abc" }) }
    }

    @Test
    fun `songs the phone no longer has are skipped and the start follows the current song`() = runTest {
        every { repository.getSongsByIds(listOf("1", "2", "3")) } returns flowOf(listOf(phoneSong("1"), phoneSong("3")))
        val queue = listOf(WearHandoffSong("1", "a"), WearHandoffSong("2", "b"), WearHandoffSong("3", "c"))

        val (songs, start) = handoff.resolve(queue, currentIndex = 2)!!

        assertEquals(listOf("1", "3"), songs.map { it.id })
        assertEquals(1, start)
    }

    @Test
    fun `nothing carries over when the current song is not on the phone`() = runTest {
        every { repository.getSongsByIds(listOf("1", "2")) } returns flowOf(listOf(phoneSong("1")))
        val queue = listOf(WearHandoffSong("1", "a"), WearHandoffSong("2", "b"))

        assertNull(handoff.resolve(queue, currentIndex = 1))
    }
}
