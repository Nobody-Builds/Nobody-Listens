package com.nobodybuilds.listens.data.screensaver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Weather for the screensaver from Open-Meteo (free, no key). Only called while the screensaver is
 * showing and "Show weather" is on. Cached for 30 min; every failure returns null (weather hidden).
 */
private const val RETRY_MS = 5 * 60_000L

@Singleton
class ScreensaverWeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ScreensaverPreferences,
) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Result of one refresh: what to show and when to try again. */
    data class Refresh(val snapshot: WeatherSnapshot?, val nextRefreshMs: Long)

    /** Last device-location city lookup, so the Geocoder runs only when the location moves. */
    @Volatile private var cityCache: Triple<Double, Double, String?>? = null

    /**
     * Current weather, or a null snapshot when off / no location / offline. Never throws except
     * [CancellationException] (rethrown so a cancelled refresh does not blank the weather).
     * Next refresh: 30 min after success, ~5 min after a failure.
     */
    suspend fun refresh(settings: ScreensaverSettings): Refresh {
        if (!settings.showWeather) return Refresh(null, OpenMeteo.CACHE_MS)
        return try {
            val target = resolveLocation(settings.location) ?: return Refresh(null, RETRY_MS)
            val now = System.currentTimeMillis()
            val cached = preferences.cachedWeather()
            if (OpenMeteo.isFresh(cached, target.lat, target.lon, now) && cached != null) {
                val city = target.city ?: cached.city ?: if (target.needsGeocode) cityFor(target.lat, target.lon) else null
                val remaining = (OpenMeteo.CACHE_MS - (now - cached.fetchedAtMs)).coerceAtLeast(RETRY_MS)
                return Refresh(cached.copy(city = city), remaining)
            }
            val city = target.city ?: if (target.needsGeocode) cityFor(target.lat, target.lon) else null
            val body = httpGet(OpenMeteo.forecastUrl(target.lat, target.lon))
                ?: return Refresh(cached?.takeIf { now - it.fetchedAtMs < 3 * OpenMeteo.CACHE_MS }, RETRY_MS)
            val snapshot = OpenMeteo.parseForecast(body, city, target.lat, target.lon, now)
                ?: return Refresh(null, RETRY_MS)
            preferences.saveWeather(snapshot)
            Refresh(snapshot, OpenMeteo.CACHE_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Refresh(null, RETRY_MS)
        }
    }

    suspend fun searchCity(name: String): List<GeoPlace> {
        if (name.isBlank()) return emptyList()
        val body = httpGet(OpenMeteo.geocodingUrl(name)) ?: return emptyList()
        return OpenMeteo.parseGeocoding(body)
    }

    private data class Target(val lat: Double, val lon: Double, val city: String?, val needsGeocode: Boolean)

    private fun resolveLocation(setting: WeatherLocationSetting): Target? {
        if (setting.useDeviceLocation && hasLocationPermission()) {
            val loc = lastKnownLocation()
            if (loc != null) {
                // City from the location cache only; the Geocoder runs later, only when needed.
                val c = cityCache?.takeIf { abs(it.first - loc.latitude) < 0.05 && abs(it.second - loc.longitude) < 0.05 }
                return Target(loc.latitude, loc.longitude, c?.third, needsGeocode = c == null)
            }
        }
        val lat = setting.cityLat
        val lon = setting.cityLon
        return if (lat != null && lon != null) Target(lat, lon, setting.cityName, needsGeocode = false) else null
    }

    private suspend fun cityFor(lat: Double, lon: Double): String? {
        val name = cityName(lat, lon)
        cityCache = Triple(lat, lon, name)
        return name
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Location? = runCatching {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    private suspend fun cityName(lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) null else
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                Geocoder(context, Locale.getDefault()).getFromLocation(
                    lat, lon, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            val a = addresses.firstOrNull()
                            if (cont.isActive) cont.resume(a?.locality ?: a?.subAdminArea ?: a?.adminArea)
                        }
                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
