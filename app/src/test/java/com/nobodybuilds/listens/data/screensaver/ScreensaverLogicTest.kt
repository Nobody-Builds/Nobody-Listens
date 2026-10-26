package com.nobodybuilds.listens.data.screensaver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.random.Random

class ScreensaverTriggerTest {
    private val all = ScreensaverTrigger.Conditions(
        offerEnabled = true, charging = true, playing = true, landscape = true,
        foreground = true, screenOn = true, snoozed = false,
    )

    @Test
    fun `offers when every condition holds`() = assertTrue(ScreensaverTrigger.shouldOffer(all))

    @Test
    fun `any missing condition blocks the offer`() {
        listOf(
            all.copy(offerEnabled = false), all.copy(charging = false), all.copy(playing = false),
            all.copy(landscape = false), all.copy(foreground = false), all.copy(screenOn = false),
            all.copy(snoozed = true), all.copy(screensaverActive = true),
        ).forEach { assertFalse(ScreensaverTrigger.shouldOffer(it), it.toString()) }
    }

    @Test
    fun `orientation sensor runs only while charging and playing in the foreground`() {
        assertTrue(ScreensaverTrigger.shouldWatchOrientation(all.copy(landscape = false)))
        assertFalse(ScreensaverTrigger.shouldWatchOrientation(all.copy(charging = false)))
        assertFalse(ScreensaverTrigger.shouldWatchOrientation(all.copy(playing = false)))
        assertFalse(ScreensaverTrigger.shouldWatchOrientation(all.copy(foreground = false)))
        assertFalse(ScreensaverTrigger.shouldWatchOrientation(all.copy(offerEnabled = false)))
    }

    @Test
    fun `snooze resets when charger is unplugged or music stops`() {
        assertTrue(ScreensaverTrigger.snoozeStillValid(true, charging = true, playing = true))
        assertFalse(ScreensaverTrigger.snoozeStillValid(true, charging = false, playing = true))
        assertFalse(ScreensaverTrigger.snoozeStillValid(true, charging = true, playing = false))
        assertFalse(ScreensaverTrigger.snoozeStillValid(false, charging = true, playing = true))
    }
}

class OrientationDetectorTest {
    @ParameterizedTest
    @ValueSource(ints = [90, 270, 70, 110, 250, 290])
    fun `near landscape angles count as landscape`(deg: Int) =
        assertTrue(OrientationDetector.isLandscape(deg, wasLandscape = false))

    @ParameterizedTest
    @ValueSource(ints = [0, 10, 350, 180, 45])
    fun `portrait angles are not landscape`(deg: Int) =
        assertFalse(OrientationDetector.isLandscape(deg, wasLandscape = false))

    @Test
    fun `hysteresis keeps landscape near 45 degrees`() {
        assertTrue(OrientationDetector.isLandscape(45, wasLandscape = true))
        assertFalse(OrientationDetector.isLandscape(20, wasLandscape = true))
    }

    @Test
    fun `unknown orientation keeps the previous value`() {
        assertTrue(OrientationDetector.isLandscape(OrientationDetector.UNKNOWN, wasLandscape = true))
        assertFalse(OrientationDetector.isLandscape(OrientationDetector.UNKNOWN, wasLandscape = false))
    }
}

class BurnInTest {
    @Test
    fun `random walk stays inside the 8 dp box and always moves`() {
        val random = Random(42)
        var p = DriftOffset(0f, 0f)
        repeat(5_000) {
            val next = BurnIn.nextDrift(p, random)
            assertTrue(abs(next.x) <= BurnIn.MAX_SHIFT_DP && abs(next.y) <= BurnIn.MAX_SHIFT_DP, "$next")
            assertTrue(abs(next.x - p.x) <= BurnIn.STEP_DP + 1e-4f && abs(next.y - p.y) <= BurnIn.STEP_DP + 1e-4f)
            assertNotEquals(p, next)
            p = next
        }
    }

    @Test
    fun `walk from an edge reflects back inside`() {
        val next = BurnIn.nextDrift(DriftOffset(8f, -8f), Random(1))
        assertTrue(next.x <= 8f && next.y >= -8f)
    }

    @Test
    fun `positions swap every 10 minutes`() {
        val m = 60_000L
        assertFalse(BurnIn.isSwapped(0))
        assertFalse(BurnIn.isSwapped(9 * m))
        assertTrue(BurnIn.isSwapped(10 * m))
        assertTrue(BurnIn.isSwapped(19 * m))
        assertFalse(BurnIn.isSwapped(20 * m))
    }

    @Test
    fun `dims after 30 minutes without interaction`() {
        assertEquals(BurnIn.NORMAL_LEVEL, BurnIn.brightnessLevel(29 * 60_000L))
        assertEquals(BurnIn.IDLE_LEVEL, BurnIn.brightnessLevel(30 * 60_000L))
    }

    @Test
    fun `breathing stays in range and is flat with reduced motion`() {
        for (t in 0L..40_000L step 500L) {
            val b = BurnIn.breathing(t)
            assertTrue(b in 0.879f..1.0001f, "$b")
        }
        assertEquals(1f, BurnIn.breathing(5_000L, reduceMotion = true))
    }

    @Test
    fun `shift interval falls back to one minute`() {
        assertEquals(30, BurnIn.sanitizeShiftIntervalSec(30))
        assertEquals(60, BurnIn.sanitizeShiftIntervalSec(7))
    }
}

class ScreensaverExitTest {
    private val m = 60_000L

    @Test
    fun `exits when the charger is unplugged after charging`() =
        assertEquals(ScreensaverExit.Reason.CHARGER_UNPLUGGED, ScreensaverExit.reason(false, true, null, 0))

    @Test
    fun `opened while unplugged does not exit for charging`() =
        assertNull(ScreensaverExit.reason(false, false, null, 0))

    @Test
    fun `exits after music stopped for 10 minutes`() {
        assertNull(ScreensaverExit.reason(true, true, pausedSinceMs = 0, nowMs = 9 * m))
        assertEquals(ScreensaverExit.Reason.MUSIC_STOPPED, ScreensaverExit.reason(true, true, 0, 10 * m))
    }

    @Test
    fun `progress is clamped and safe`() {
        assertEquals(0f, screensaverProgress(10, 0))
        assertEquals(0.5f, screensaverProgress(50, 100))
        assertEquals(1f, screensaverProgress(150, 100))
    }
}

class WeatherLogicTest {
    @Test
    fun `wmo codes map to short labels and icons`() {
        assertEquals(WeatherIcon.CLEAR, WmoWeather.describe(0).icon)
        assertEquals(WeatherIcon.CLEAR_NIGHT, WmoWeather.describe(0, isDay = false).icon)
        assertEquals("Partly cloudy", WmoWeather.describe(2).label)
        assertEquals(WeatherIcon.FOG, WmoWeather.describe(48).icon)
        assertEquals(WeatherIcon.DRIZZLE, WmoWeather.describe(53).icon)
        assertEquals("Heavy rain", WmoWeather.describe(65).label)
        assertEquals(WeatherIcon.SNOW, WmoWeather.describe(86).icon)
        assertEquals(WeatherIcon.THUNDER, WmoWeather.describe(99).icon)
        assertEquals(WeatherIcon.UNKNOWN, WmoWeather.describe(1234).icon)
    }

    @Test
    fun `parses an Open-Meteo forecast`() {
        val body = """{"latitude":9.94,"longitude":76.26,"current":{"time":"2026-10-18T10:00","temperature_2m":29.6,"weather_code":80,"is_day":1}}"""
        val s = OpenMeteo.parseForecast(body, "Kochi", 9.94, 76.26, 1_000L)
        assertNotNull(s)
        assertEquals("30°", s!!.temperatureText)
        assertEquals("Showers", s.condition.label)
        assertEquals("Kochi", s.city)
    }

    @Test
    fun `bad forecast json returns null`() {
        assertNull(OpenMeteo.parseForecast("not json", null, 0.0, 0.0, 0))
        assertNull(OpenMeteo.parseForecast("""{"current":{}}""", null, 0.0, 0.0, 0))
    }

    @Test
    fun `parses geocoding results`() {
        val body = """{"results":[{"name":"Kochi","latitude":9.93,"longitude":76.26,"country":"India","admin1":"Kerala"},{"name":"Bad"}]}"""
        val places = OpenMeteo.parseGeocoding(body)
        assertEquals(1, places.size)
        assertEquals("Kochi, Kerala, India", places[0].displayName)
        assertTrue(OpenMeteo.parseGeocoding("{}").isEmpty())
    }

    @Test
    fun `cache is fresh for 30 minutes at the same place`() {
        val s = WeatherSnapshot(20.0, 0, true, null, fetchedAtMs = 0, latitude = 10.0, longitude = 76.0)
        assertTrue(OpenMeteo.isFresh(s, 10.0, 76.0, 29 * 60_000L))
        assertFalse(OpenMeteo.isFresh(s, 10.0, 76.0, 30 * 60_000L))
        assertFalse(OpenMeteo.isFresh(s, 12.0, 76.0, 1_000L))
        assertFalse(OpenMeteo.isFresh(null, 10.0, 76.0, 0))
    }

    @Test
    fun `urls round coordinates and encode the city`() {
        assertTrue(OpenMeteo.forecastUrl(9.93123, 76.2673).contains("latitude=9.93&longitude=76.27"))
        assertTrue(OpenMeteo.geocodingUrl("New York").contains("name=New+York"))
    }
}
