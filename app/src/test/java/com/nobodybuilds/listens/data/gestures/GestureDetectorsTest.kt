package com.nobodybuilds.listens.data.gestures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GestureDetectorsTest {

    private val g = STANDARD_GRAVITY

    @Nested
    inner class Shake {
        private fun detector() = ShakeDetector(thresholdMs2 = 2.5f * g)

        /** Feeds a peak (high sample then low sample) and returns whether the high sample fired. */
        private fun ShakeDetector.peak(t: Long, magnitude: Float = 3f * g): Boolean {
            val fired = onSample(magnitude, t)
            onSample(0.2f * g, t + 40)
            return fired
        }

        @Test
        fun `two peaks within the window fire`() {
            val d = detector()
            assertFalse(d.peak(0))
            assertTrue(d.peak(300))
        }

        @Test
        fun `a single peak does not fire`() {
            val d = detector()
            assertFalse(d.peak(0))
            assertFalse(d.onSample(0f, 2_000))
        }

        @Test
        fun `peaks further apart than 600 ms do not fire`() {
            val d = detector()
            assertFalse(d.peak(0))
            assertFalse(d.peak(700))
            // ...but the second peak starts a new window.
            assertTrue(d.peak(1_100))
        }

        @Test
        fun `a sustained jolt counts as one peak`() {
            val d = detector()
            assertFalse(d.onSample(3f * g, 0))
            assertFalse(d.onSample(3.2f * g, 50))
            assertFalse(d.onSample(2.9f * g, 100))
            assertFalse(d.onSample(2f * g, 150)) // below threshold but above re-arm level
            assertFalse(d.onSample(3f * g, 200)) // still not re-armed
        }

        @Test
        fun `below threshold never fires`() {
            val d = detector()
            for (t in 0L..1_000L step 20) {
                assertFalse(d.onSample(if ((t / 20) % 2 == 0L) 2.4f * g else 0f, t))
            }
        }

        @Test
        fun `cooldown blocks a second shake for 2 seconds`() {
            val d = detector()
            d.peak(0)
            assertTrue(d.peak(200))
            assertFalse(d.peak(600))
            assertFalse(d.peak(800))
            assertFalse(d.peak(2_100)) // still inside the cooldown (1.9 s after the shake)
            assertFalse(d.peak(2_300)) // first peak after the cooldown
            assertTrue(d.peak(2_500))
        }

        @Test
        fun `sensitivity changes the threshold`() {
            val d = ShakeDetector(thresholdMs2 = ShakeSensitivity.HIGH.thresholdMs2)
            assertFalse(d.peak(0, 2.1f * g))
            assertTrue(d.peak(200, 2.1f * g))

            val low = ShakeDetector(thresholdMs2 = ShakeSensitivity.LOW.thresholdMs2)
            assertFalse(low.peak(0, 2.6f * g))
            assertFalse(low.peak(200, 2.6f * g))
        }

        @Test
        fun `reset clears pending peaks`() {
            val d = detector()
            d.peak(0)
            d.reset()
            assertFalse(d.peak(200))
        }

        @Test
        fun `NaN is ignored`() {
            assertFalse(detector().onSample(Float.NaN, 0))
        }
    }

    @Nested
    inner class FaceDown {
        private val up = 9.8f
        private val down = -9.7f

        /** Feeds [z] every 50 ms from [from] until [to] inclusive; returns the events seen. */
        private fun FaceDownDetector.feed(z: Float, from: Long, to: Long, near: Boolean? = null) =
            (from..to step 50).mapNotNull { onSample(0f, 0f, z, it, near) }

        private fun FaceDownDetector.feedXyz(x: Float, y: Float, z: Float, from: Long, to: Long, step: Long = 50) =
            (from..to step step).mapNotNull { onSample(x, y, z, it) }

        @Test
        fun `face-down for 800 ms pauses once`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 500)
            val events = d.feed(down, 550, 3_000)
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), events)
            assertTrue(d.isFaceDown)
        }

        @Test
        fun `fires only after the hold time`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            assertTrue(d.feed(down, 150, 900).isEmpty()) // 750 ms
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), d.feed(down, 950, 1_000))
        }

        @Test
        fun `a brief flip does not fire`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            d.feed(down, 150, 700)
            d.feed(up, 750, 800)
            assertTrue(d.feed(down, 850, 1_600).isEmpty())
        }

        @Test
        fun `already face-down when listening starts does not fire`() {
            val d = FaceDownDetector()
            assertTrue(d.feed(down, 0, 5_000).isEmpty())
        }

        @Test
        fun `z just above the threshold does not fire`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            assertTrue(d.feed(-8.0f, 150, 3_000).isEmpty())
        }

        @Test
        fun `proximity far blocks, unknown proximity is ignored`() {
            val far = FaceDownDetector()
            far.feed(up, 0, 100)
            assertTrue(far.feed(down, 150, 2_000, near = false).isEmpty())

            val near = FaceDownDetector()
            near.feed(up, 0, 100)
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), near.feed(down, 150, 2_000, near = true))
        }

        @Test
        fun `turning face-up after face-down fires FACE_UP`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            d.feed(down, 150, 1_000)
            val events = d.feed(up, 1_050, 2_000)
            assertEquals(listOf(FaceDownDetector.Event.FACE_UP), events)
            assertFalse(d.isFaceDown)
        }

        @Test
        fun `face-up without a prior face-down never fires`() {
            val d = FaceDownDetector()
            assertTrue(d.feed(up, 0, 3_000).isEmpty())
        }

        @Test
        fun `can pause again after being turned back up`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            d.feed(down, 150, 1_000)
            d.feed(up, 1_050, 1_600)
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), d.feed(down, 1_650, 2_600))
        }

        @Test
        fun `reset keeping face-down still allows FACE_UP`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            d.feed(down, 150, 1_000)
            d.reset(keepFaceDown = true)
            assertEquals(listOf(FaceDownDetector.Event.FACE_UP), d.feed(up, 5_000, 6_000))
        }

        @Test
        fun `full reset forgets face-down`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            d.feed(down, 150, 1_000)
            d.reset()
            assertTrue(d.feed(up, 5_000, 6_000).isEmpty())
        }

        @Test
        fun `upright phone in a pocket never fires`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            // Standing on its end (gravity along y), and upside down in a pocket.
            assertTrue(d.feedXyz(0f, 9.8f, 0.3f, 150, 5_000).isEmpty())
            assertTrue(d.feedXyz(0.5f, -9.6f, -1.5f, 5_050, 10_000).isEmpty())
        }

        @Test
        fun `tilted 30 degrees screen-down does not fire`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            assertTrue(d.feedXyz(4.9f, 0f, -8.5f, 150, 5_000).isEmpty())
            // Even with z just under the threshold, a large sideways tilt blocks it.
            assertTrue(d.feedXyz(0f, 3.5f, -9.0f, 5_050, 10_000).isEmpty())
        }

        @Test
        fun `slightly tilted on a table still fires`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), d.feedXyz(0.8f, -0.6f, -9.7f, 150, 2_000))
        }

        @Test
        fun `moving while screen-down does not fire`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            // Swinging in a bag: screen-down but the vector keeps moving.
            val events = (150L..5_000L step 50).mapNotNull { t ->
                val swing = if ((t / 50) % 4 < 2) 1.2f else -1.2f
                d.onSample(swing, -swing, -9.6f, t)
            }
            assertTrue(events.isEmpty())
        }

        @Test
        fun `accelerating screen-down does not fire`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100)
            assertTrue(d.feed(-12.5f, 150, 3_000).isEmpty())
        }

        @Test
        fun `works with 5 Hz batched samples`() {
            val d = FaceDownDetector()
            d.feedXyz(0f, 0f, up, 0, 400, step = 200)
            assertTrue(d.feedXyz(0f, 0f, down, 600, 1_200, step = 200).isEmpty()) // 600 ms
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), d.feedXyz(0f, 0f, down, 1_400, 1_400, step = 200))
        }

        @Test
        fun `proximity near is fine (that is a table)`() {
            val d = FaceDownDetector()
            d.feed(up, 0, 100, near = true)
            assertEquals(listOf(FaceDownDetector.Event.FACE_DOWN), d.feed(down, 150, 2_000, near = true))
        }

        @Test
        fun `NaN is ignored`() {
            assertNull(FaceDownDetector().onSample(0f, 0f, Float.NaN, 0))
            assertNull(FaceDownDetector().onSample(Float.NaN, 0f, -9.8f, 0))
        }
    }

    @Nested
    inner class PocketShake {
        private fun detector() = PocketShakeDetector(thresholdMs2 = ShakeSensitivity.MEDIUM.screenOffThresholdMs2)

        /** One peak: a strong sample then a calm one. */
        private fun PocketShakeDetector.peak(t: Long, magnitude: Float = 4f * g, near: Boolean? = null): Boolean {
            val fired = onSample(magnitude, t, near)
            onSample(0.2f * g, t + 40, near)
            return fired
        }

        /** One shake = two peaks 200 ms apart; returns whether the second peak fired. */
        private fun PocketShakeDetector.shake(t: Long, magnitude: Float = 4f * g, near: Boolean? = null): Boolean {
            peak(t, magnitude, near)
            return peak(t + 200, magnitude, near)
        }

        @Test
        fun `screen-off threshold is firmer than screen-on`() {
            ShakeSensitivity.entries.forEach {
                assertTrue(it.screenOffThresholdMs2 > it.thresholdMs2)
            }
        }

        @Test
        fun `a single shake does not fire`() {
            val d = detector()
            assertFalse(d.shake(0))
            assertFalse(d.onSample(0f, 3_000))
        }

        @Test
        fun `two shakes within 1_2 s fire`() {
            val d = detector()
            assertFalse(d.shake(0))
            assertTrue(d.shake(500))
        }

        @Test
        fun `two shakes too far apart do not fire`() {
            val d = detector()
            assertFalse(d.shake(0))
            assertFalse(d.shake(1_500)) // 1.5 s after the first shake ended
            // ...but that one counts as the first of a new double shake.
            assertTrue(d.shake(2_100))
        }

        @Test
        fun `a screen-on strength shake is not enough`() {
            val d = detector()
            val medium = 2.7f * g // above the screen-on medium threshold, below screen-off
            assertFalse(d.shake(0, medium))
            assertFalse(d.shake(500, medium))
            assertFalse(d.shake(1_000, medium))
        }

        @Test
        fun `ignored while proximity is near`() {
            val d = detector()
            assertFalse(d.shake(0, near = true))
            assertFalse(d.shake(500, near = true))
            assertFalse(d.shake(1_000, near = true))
        }

        @Test
        fun `near in the middle forgets the first shake`() {
            val d = detector()
            assertFalse(d.shake(0, near = false))
            d.onSample(0f, 450, proximityNear = true)
            assertFalse(d.shake(500, near = false))
            assertTrue(d.shake(900, near = false))
        }

        @Test
        fun `unknown proximity still allows a double shake`() {
            val d = detector()
            assertFalse(d.shake(0, near = null))
            assertTrue(d.shake(500, near = null))
        }

        @Test
        fun `cooldown blocks an immediate second shuffle`() {
            val d = detector()
            d.shake(0)
            assertTrue(d.shake(500))
            assertFalse(d.shake(1_000))
            assertFalse(d.shake(1_500))
            assertFalse(d.shake(3_000)) // first shake after the cooldown
            assertTrue(d.shake(3_500))
        }

        /**
         * Raw 50 Hz accelerometer samples (phone resting screen-up) with a one-sample jolt on x at
         * each of [peaksAtMs], like a batch the sensor hub delivers in one go.
         */
        private fun batch(fromMs: Long, toMs: Long, peaksAtMs: Set<Long>, peak: Float = 45f): List<Pair<Long, FloatArray>> =
            (fromMs..toMs step 20).map { t ->
                t to floatArrayOf(if (t in peaksAtMs) peak else 0f, 0f, g)
            }

        /** Runs samples through the same pipeline as the screen-off listener; returns firing times. */
        private fun PocketShakeDetector.process(
            filter: GravityFilter,
            samples: List<Pair<Long, FloatArray>>,
            near: Boolean? = null,
        ): List<Long> = samples.mapNotNull { (t, v) ->
            val magnitude = filter.update(v[0], v[1], v[2])
            if (onSample(magnitude, t, near)) t else null
        }

        @Test
        fun `a double shake inside one 1_2 s batch fires once at the sample time`() {
            val d = detector()
            val f = GravityFilter()
            d.process(f, batch(0, 980, emptySet())) // settle the gravity estimate
            val fired = d.process(f, batch(1_000, 2_180, setOf(1_100L, 1_300L, 1_600L, 1_800L)))
            assertEquals(listOf(1_800L), fired)
        }

        @Test
        fun `a double shake split across two batches still fires`() {
            val d = detector()
            val f = GravityFilter()
            d.process(f, batch(0, 980, emptySet()))
            assertTrue(d.process(f, batch(1_000, 1_380, setOf(1_100L, 1_300L))).isEmpty())
            // The next batch arrives ~1.2 s later in wall time; only sample timestamps matter.
            assertEquals(listOf(1_600L), d.process(f, batch(1_400, 2_580, setOf(1_400L, 1_600L))))
        }

        @Test
        fun `shakes further apart than the window do not fire even when delivered together`() {
            val d = detector()
            val f = GravityFilter()
            d.process(f, batch(0, 980, emptySet()))
            // One batch, but the two shakes are 1.6 s apart in sample time.
            val fired = d.process(f, batch(1_000, 4_000, setOf(1_100L, 1_300L, 2_900L, 3_100L)))
            assertTrue(fired.isEmpty())
        }

        @Test
        fun `a batched double shake while near does not fire`() {
            val d = detector()
            val f = GravityFilter()
            d.process(f, batch(0, 980, emptySet()), near = true)
            val fired = d.process(f, batch(1_000, 2_180, setOf(1_100L, 1_300L, 1_600L, 1_800L)), near = true)
            assertTrue(fired.isEmpty())
        }

        @Test
        fun `walking-strength bumps in a batch do not fire`() {
            val d = detector()
            val f = GravityFilter()
            d.process(f, batch(0, 980, emptySet()))
            // ~2 steps per second at about 2.5 g of linear acceleration.
            val steps = (1_000L..6_000L step 500).toSet()
            assertTrue(d.process(f, batch(1_000, 6_000, steps, peak = 31f)).isEmpty())
        }

        @Test
        fun `NaN is ignored`() {
            assertFalse(detector().onSample(Float.NaN, 0))
        }
    }

    @Nested
    inner class Gravity {
        @Test
        fun `steady gravity yields no linear acceleration`() {
            val f = GravityFilter()
            var last = 0f
            repeat(50) { last = f.update(0f, 0f, g) }
            assertTrue(last < 0.01f)
            assertTrue(f.gravity[2] > g - 0.01f)
        }

        @Test
        fun `a sudden jolt shows up as linear acceleration`() {
            val f = GravityFilter()
            repeat(50) { f.update(0f, 0f, g) }
            assertTrue(f.update(30f, 0f, g) > 20f)
        }
    }
}
