package com.nobodybuilds.listens.data.screensaver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// =====================================================================================
// Screensaver (standby) mode — pure logic, no Android types, unit tested in app/src/test.
// =====================================================================================

/** When to show the "Turn on screensaver?" prompt. */
object ScreensaverTrigger {

    /** How long the conditions must hold before we ask (avoids prompting on a quick tilt). */
    const val STABLE_FOR_MS = 1_500L

    data class Conditions(
        val offerEnabled: Boolean,
        val charging: Boolean,
        val playing: Boolean,
        val landscape: Boolean,
        val foreground: Boolean,
        val screenOn: Boolean,
        /** "Not now" was tapped and charging/playing have not reset since. */
        val snoozed: Boolean,
        /** The screensaver is already showing. */
        val screensaverActive: Boolean = false,
    )

    fun shouldOffer(c: Conditions): Boolean =
        c.offerEnabled && c.charging && c.playing && c.landscape && c.foreground && c.screenOn &&
            !c.snoozed && !c.screensaverActive

    /** Whether the orientation sensor is worth running (only while it could lead to a prompt). */
    fun shouldWatchOrientation(c: Conditions): Boolean =
        c.offerEnabled && c.charging && c.playing && c.foreground && c.screenOn && !c.snoozed && !c.screensaverActive

    /** A "Not now" snooze lasts until the charger is unplugged or music stops. */
    fun snoozeStillValid(snoozed: Boolean, charging: Boolean, playing: Boolean): Boolean =
        snoozed && charging && playing
}

/**
 * Landscape detection from OrientationEventListener degrees (0 = portrait, 90/270 = landscape),
 * with hysteresis so a phone resting near 45° does not flicker.
 */
object OrientationDetector {
    const val UNKNOWN = -1

    fun isLandscape(degrees: Int, wasLandscape: Boolean): Boolean {
        if (degrees == UNKNOWN || degrees < 0) return wasLandscape
        val d = degrees % 360
        // Distance from the nearest landscape angle (90 or 270).
        val fromLandscape = minOf(abs(d - 90), abs(d - 270))
        return if (wasLandscape) fromLandscape <= 55 else fromLandscape <= 30
    }
}

/** Offset in dp. */
data class DriftOffset(val x: Float, val y: Float)

/** Burn-in protection: pixel shift, slow position swaps, idle dimming. */
object BurnIn {
    const val MAX_SHIFT_DP = 8f
    const val STEP_DP = 3f
    const val DEFAULT_SHIFT_INTERVAL_MS = 60_000L
    const val SWAP_INTERVAL_MS = 10 * 60_000L
    const val IDLE_DIM_AFTER_MS = 30 * 60_000L
    const val NORMAL_LEVEL = 1f
    const val IDLE_LEVEL = 0.55f

    /** Allowed pixel shift intervals (seconds) for the settings picker. */
    val SHIFT_INTERVAL_CHOICES_SEC = listOf(30, 60, 120)

    /**
     * One random-walk step: move up to [step] dp in each axis and stay within ±[max] dp.
     * Never returns the same point twice in a row (always moves at least a little).
     */
    fun nextDrift(
        current: DriftOffset,
        random: Random,
        max: Float = MAX_SHIFT_DP,
        step: Float = STEP_DP,
    ): DriftOffset {
        fun axis(v: Float): Float {
            var delta = (random.nextFloat() * 2f - 1f) * step
            if (abs(delta) < step / 4f) delta = if (delta >= 0f) step / 4f else -step / 4f
            // Bounce off the box edges instead of sticking to them (step back the other way).
            if (v + delta > max || v + delta < -max) delta = -delta
            return (v + delta).coerceIn(-max, max)
        }
        return DriftOffset(axis(current.x), axis(current.y))
    }

    /** Clock on the left (false) or right (true): swaps every [interval] ms. */
    fun isSwapped(elapsedMs: Long, interval: Long = SWAP_INTERVAL_MS): Boolean =
        elapsedMs >= 0 && interval > 0 && (elapsedMs / interval) % 2L == 1L

    /** Overall content brightness: dims after [IDLE_DIM_AFTER_MS] with no interaction. */
    fun brightnessLevel(idleMs: Long): Float =
        if (idleMs >= IDLE_DIM_AFTER_MS) IDLE_LEVEL else NORMAL_LEVEL

    /** Very slow breathing (0.88 … 1.0) over [periodMs]; 1.0 when motion is reduced. */
    fun breathing(timeMs: Long, periodMs: Long = 20_000L, reduceMotion: Boolean = false): Float {
        if (reduceMotion || periodMs <= 0) return 1f
        val phase = (timeMs % periodMs).toDouble() / periodMs
        val wave = (kotlin.math.cos(phase * 2 * Math.PI) + 1.0) / 2.0 // 1 → 0 → 1
        return (0.88 + 0.12 * wave).toFloat()
    }

    fun sanitizeShiftIntervalSec(sec: Int): Int =
        if (sec in SHIFT_INTERVAL_CHOICES_SEC) sec else (DEFAULT_SHIFT_INTERVAL_MS / 1000).toInt()
}

/** When the screensaver closes by itself. */
object ScreensaverExit {
    const val PAUSED_EXIT_AFTER_MS = 10 * 60_000L

    enum class Reason { CHARGER_UNPLUGGED, MUSIC_STOPPED }

    /**
     * @param wasCharging the device was charging at some point while the screensaver was open
     *   (opened from Settings while unplugged, plugging out does nothing until it was plugged in).
     * @param pausedSinceMs when music stopped, or null while playing.
     */
    fun reason(charging: Boolean, wasCharging: Boolean, pausedSinceMs: Long?, nowMs: Long): Reason? = when {
        wasCharging && !charging -> Reason.CHARGER_UNPLUGGED
        pausedSinceMs != null && nowMs - pausedSinceMs >= PAUSED_EXIT_AFTER_MS -> Reason.MUSIC_STOPPED
        else -> null
    }
}

// ---------------------------------------------------------------- Weather

enum class WeatherIcon { CLEAR, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER, UNKNOWN }

data class WeatherCondition(val label: String, val icon: WeatherIcon)

/** WMO weather interpretation codes (Open-Meteo `weather_code`) → short text + icon. */
object WmoWeather {
    fun describe(code: Int, isDay: Boolean = true): WeatherCondition = when (code) {
        0 -> WeatherCondition("Clear", if (isDay) WeatherIcon.CLEAR else WeatherIcon.CLEAR_NIGHT)
        1 -> WeatherCondition("Mostly clear", if (isDay) WeatherIcon.CLEAR else WeatherIcon.CLEAR_NIGHT)
        2 -> WeatherCondition("Partly cloudy", WeatherIcon.PARTLY_CLOUDY)
        3 -> WeatherCondition("Overcast", WeatherIcon.CLOUDY)
        45, 48 -> WeatherCondition("Fog", WeatherIcon.FOG)
        51, 53, 55 -> WeatherCondition("Drizzle", WeatherIcon.DRIZZLE)
        56, 57 -> WeatherCondition("Freezing drizzle", WeatherIcon.DRIZZLE)
        61 -> WeatherCondition("Light rain", WeatherIcon.RAIN)
        63 -> WeatherCondition("Rain", WeatherIcon.RAIN)
        65 -> WeatherCondition("Heavy rain", WeatherIcon.RAIN)
        66, 67 -> WeatherCondition("Freezing rain", WeatherIcon.RAIN)
        71 -> WeatherCondition("Light snow", WeatherIcon.SNOW)
        73 -> WeatherCondition("Snow", WeatherIcon.SNOW)
        75 -> WeatherCondition("Heavy snow", WeatherIcon.SNOW)
        77 -> WeatherCondition("Snow grains", WeatherIcon.SNOW)
        80, 81 -> WeatherCondition("Showers", WeatherIcon.RAIN)
        82 -> WeatherCondition("Heavy showers", WeatherIcon.RAIN)
        85, 86 -> WeatherCondition("Snow showers", WeatherIcon.SNOW)
        95 -> WeatherCondition("Thunderstorm", WeatherIcon.THUNDER)
        96, 99 -> WeatherCondition("Thunderstorm, hail", WeatherIcon.THUNDER)
        else -> WeatherCondition("—", WeatherIcon.UNKNOWN)
    }
}

data class WeatherSnapshot(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val city: String?,
    val fetchedAtMs: Long,
    val latitude: Double,
    val longitude: Double,
) {
    val condition: WeatherCondition get() = WmoWeather.describe(weatherCode, isDay)
    val temperatureText: String get() = "${temperatureC.roundToInt()}°"
}

data class GeoPlace(
    val name: String,
    val admin: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String get() = listOfNotNull(name, admin?.takeIf { it != name }, country).joinToString(", ")
}

/** Open-Meteo URLs + JSON parsing. */
object OpenMeteo {
    const val CACHE_MS = 30 * 60_000L

    private val json = Json { ignoreUnknownKeys = true }

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"

    fun geocodingUrl(name: String, count: Int = 5): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=" +
            java.net.URLEncoder.encode(name.trim(), "UTF-8") + "&count=$count&language=en&format=json"

    /** Coordinates rounded to 2 decimals (~1 km): enough for weather, kinder to privacy. */
    fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    fun isFresh(snapshot: WeatherSnapshot?, lat: Double, lon: Double, nowMs: Long): Boolean =
        snapshot != null && nowMs - snapshot.fetchedAtMs in 0 until CACHE_MS &&
            abs(snapshot.latitude - lat) < 0.05 && abs(snapshot.longitude - lon) < 0.05

    fun parseForecast(body: String, city: String?, lat: Double, lon: Double, nowMs: Long): WeatherSnapshot? = runCatching {
        val current = json.parseToJsonElement(body).jsonObject["current"]?.jsonObject ?: return null
        val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val isDay = (current["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1
        WeatherSnapshot(temp, code, isDay, city, nowMs, lat, lon)
    }.getOrNull()

    fun parseGeocoding(body: String): List<GeoPlace> = runCatching {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val lat = o["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            GeoPlace(
                name = name,
                admin = o["admin1"]?.jsonPrimitive?.contentOrNull,
                country = o["country"]?.jsonPrimitive?.contentOrNull,
                latitude = lat,
                longitude = lon,
            )
        }
    }.getOrDefault(emptyList())
}

/** Progress (0…1) of the current song, safe for unknown durations. */
fun screensaverProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
