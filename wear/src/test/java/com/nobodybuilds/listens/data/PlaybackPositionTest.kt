package com.nobodybuilds.listens.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackPositionTest {
    @Test
    fun `resuming UI updates catches up without running a background ticker`() {
        val anchor = PlaybackPosition(10_000L, 180_000L, true, 5_000L)

        assertEquals(11_000L, anchor.positionAt(6_000L))
        // No intermediate samples while the screen is off.
        assertEquals(70_000L, anchor.positionAt(65_000L))
    }

    @Test
    fun `pause and seek replace the anchor without continuing old progress`() {
        val playing = PlaybackPosition(10_000L, 180_000L, true, 5_000L)
        val paused = PlaybackPosition(playing.positionAt(8_000L), 180_000L, false, 8_000L)
        assertEquals(13_000L, paused.positionAt(50_000L))

        val seeked = PlaybackPosition(2_000L, 180_000L, true, 50_000L)
        assertEquals(3_000L, seeked.positionAt(51_000L))
    }

    @Test
    fun `progress is bounded for track end unknown duration and invalid positions`() {
        assertEquals(10_000L, PlaybackPosition(9_000L, 10_000L, true, 0L).positionAt(60_000L))
        assertEquals(0L, PlaybackPosition(5_000L, -1L, true, 0L).positionAt(60_000L))
        assertEquals(0L, PlaybackPosition(-5_000L, 10_000L, false, 0L).positionAt(1_000L))
        assertEquals(10_000L, PlaybackPosition(50_000L, 10_000L, false, 0L).positionAt(1_000L))
        assertEquals(2_000L, PlaybackPosition(2_000L, 10_000L, true, 5_000L).positionAt(4_000L))
    }
}
