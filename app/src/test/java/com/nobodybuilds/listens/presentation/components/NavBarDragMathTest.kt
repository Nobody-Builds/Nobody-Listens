package com.nobodybuilds.listens.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NavBarDragMathTest {

    private val width = 400f // 4 tabs -> 100px slots
    private val count = 4
    private val fling = 1000f

    @Nested
    inner class IndexAt {
        @Test
        fun `ltr maps x to slots left to right`() {
            assertEquals(0, navBarIndexAt(10f, width, count, isRtl = false))
            assertEquals(1, navBarIndexAt(150f, width, count, isRtl = false))
            assertEquals(2, navBarIndexAt(200f, width, count, isRtl = false))
            assertEquals(3, navBarIndexAt(399f, width, count, isRtl = false))
        }

        @Test
        fun `rtl mirrors the slots`() {
            assertEquals(3, navBarIndexAt(10f, width, count, isRtl = true))
            assertEquals(2, navBarIndexAt(150f, width, count, isRtl = true))
            assertEquals(0, navBarIndexAt(399f, width, count, isRtl = true))
        }

        @Test
        fun `finger outside the bar clamps to the outer tabs`() {
            assertEquals(0, navBarIndexAt(-50f, width, count, isRtl = false))
            assertEquals(3, navBarIndexAt(900f, width, count, isRtl = false))
            assertEquals(3, navBarIndexAt(-50f, width, count, isRtl = true))
            assertEquals(0, navBarIndexAt(900f, width, count, isRtl = true))
        }

        @Test
        fun `nothing to hit returns minus one`() {
            assertEquals(-1, navBarIndexAt(10f, width, 0, isRtl = false))
            assertEquals(-1, navBarIndexAt(10f, 0f, count, isRtl = false))
            assertEquals(-1, navBarIndexAt(Float.NaN, width, count, isRtl = false))
            assertEquals(-1, navBarIndexAt(10f, Float.POSITIVE_INFINITY, count, isRtl = false))
        }
    }

    @Nested
    inner class Geometry {
        @Test
        fun `slot centres respect direction`() {
            assertEquals(50f, navBarSlotCenterX(0, width, count, isRtl = false))
            assertEquals(350f, navBarSlotCenterX(3, width, count, isRtl = false))
            assertEquals(350f, navBarSlotCenterX(0, width, count, isRtl = true))
            assertEquals(50f, navBarSlotCenterX(3, width, count, isRtl = true))
        }

        @Test
        fun `centre and index round trip`() {
            for (rtl in listOf(false, true)) {
                for (i in 0 until count) {
                    assertEquals(i, navBarIndexAt(navBarSlotCenterX(i, width, count, rtl), width, count, rtl))
                }
            }
        }

        @Test
        fun `pill track stays between outer centres`() {
            assertEquals(50f, navBarPillTrackX(0f, width, count))
            assertEquals(222f, navBarPillTrackX(222f, width, count))
            assertEquals(350f, navBarPillTrackX(1000f, width, count))
        }
    }

    @Nested
    inner class Release {
        @Test
        fun `crossing into another tab wins over velocity`() {
            assertEquals(3, resolveNavBarDragTarget(0, 3, velocityX = -5000f, fling, count, isRtl = false))
        }

        @Test
        fun `slow drag that stays on the start tab snaps back`() {
            assertEquals(1, resolveNavBarDragTarget(1, 1, velocityX = 300f, fling, count, isRtl = false))
        }

        @Test
        fun `fling steps one tab in visual direction ltr`() {
            assertEquals(2, resolveNavBarDragTarget(1, 1, velocityX = 1500f, fling, count, isRtl = false))
            assertEquals(0, resolveNavBarDragTarget(1, 1, velocityX = -1500f, fling, count, isRtl = false))
        }

        @Test
        fun `fling steps are mirrored in rtl`() {
            assertEquals(0, resolveNavBarDragTarget(1, 1, velocityX = 1500f, fling, count, isRtl = true))
            assertEquals(2, resolveNavBarDragTarget(1, 1, velocityX = -1500f, fling, count, isRtl = true))
        }

        @Test
        fun `fling at the edges clamps`() {
            assertEquals(3, resolveNavBarDragTarget(3, 3, velocityX = 2000f, fling, count, isRtl = false))
            assertEquals(0, resolveNavBarDragTarget(0, 0, velocityX = -2000f, fling, count, isRtl = false))
        }

        @Test
        fun `threshold is inclusive and bad velocity is ignored`() {
            assertEquals(2, resolveNavBarDragTarget(1, 1, velocityX = fling, fling, count, isRtl = false))
            assertEquals(1, resolveNavBarDragTarget(1, 1, velocityX = Float.NaN, fling, count, isRtl = false))
        }

        @Test
        fun `no tabs gives minus one`() {
            assertEquals(-1, resolveNavBarDragTarget(0, 0, 0f, fling, 0, isRtl = false))
        }
    }
}
