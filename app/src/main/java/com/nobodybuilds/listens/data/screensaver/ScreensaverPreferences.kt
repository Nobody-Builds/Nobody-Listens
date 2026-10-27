package com.nobodybuilds.listens.data.screensaver

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.screensaverDataStore by preferencesDataStore(name = "lmusic_screensaver")

enum class ScreensaverClockStyle(val key: String, val label: String) {
    LARGE("large", "Large digital"),
    MINIMAL("minimal", "Minimal");

    companion object {
        fun fromKey(key: String?): ScreensaverClockStyle = entries.firstOrNull { it.key == key } ?: LARGE
    }
}

/** Where the weather location comes from. */
data class WeatherLocationSetting(
    val useDeviceLocation: Boolean,
    val cityName: String?,
    val cityLat: Double?,
    val cityLon: Double?,
)

data class ScreensaverSettings(
    val offerWhenCharging: Boolean = true,
    val showWeather: Boolean = false,
    val clockStyle: ScreensaverClockStyle = ScreensaverClockStyle.LARGE,
    val shiftIntervalSec: Int = 60,
    val showArtwork: Boolean = true,
    val location: WeatherLocationSetting = WeatherLocationSetting(false, null, null, null),
)

/** Screensaver settings + the cached weather (DataStore "lmusic_screensaver"). Constructor does no work. */
@Singleton
class ScreensaverPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val offer = booleanPreferencesKey("offer_when_charging")
        val showWeather = booleanPreferencesKey("show_weather")
        val clockStyle = stringPreferencesKey("clock_style")
        val shiftInterval = intPreferencesKey("shift_interval_sec")
        val showArtwork = booleanPreferencesKey("show_artwork")
        val useDeviceLocation = booleanPreferencesKey("use_device_location")
        val cityName = stringPreferencesKey("city_name")
        val cityLat = doublePreferencesKey("city_lat")
        val cityLon = doublePreferencesKey("city_lon")
        // Cached weather
        val wTemp = doublePreferencesKey("w_temp")
        val wCode = intPreferencesKey("w_code")
        val wIsDay = booleanPreferencesKey("w_is_day")
        val wCity = stringPreferencesKey("w_city")
        val wAt = longPreferencesKey("w_at")
        val wLat = doublePreferencesKey("w_lat")
        val wLon = doublePreferencesKey("w_lon")
    }

    val settings: Flow<ScreensaverSettings> = context.screensaverDataStore.data.map { p ->
        ScreensaverSettings(
            offerWhenCharging = p[Keys.offer] ?: true,
            showWeather = p[Keys.showWeather] ?: false,
            clockStyle = ScreensaverClockStyle.fromKey(p[Keys.clockStyle]),
            shiftIntervalSec = BurnIn.sanitizeShiftIntervalSec(p[Keys.shiftInterval] ?: 60),
            showArtwork = p[Keys.showArtwork] ?: true,
            location = WeatherLocationSetting(
                useDeviceLocation = p[Keys.useDeviceLocation] ?: false,
                cityName = p[Keys.cityName],
                cityLat = p[Keys.cityLat],
                cityLon = p[Keys.cityLon],
            ),
        )
    }.distinctUntilChanged()

    suspend fun current(): ScreensaverSettings = settings.first()

    suspend fun setOfferWhenCharging(v: Boolean) = context.screensaverDataStore.edit { it[Keys.offer] = v }
    suspend fun setShowWeather(v: Boolean) = context.screensaverDataStore.edit { it[Keys.showWeather] = v }
    suspend fun setClockStyle(v: ScreensaverClockStyle) = context.screensaverDataStore.edit { it[Keys.clockStyle] = v.key }
    suspend fun setShiftIntervalSec(v: Int) =
        context.screensaverDataStore.edit { it[Keys.shiftInterval] = BurnIn.sanitizeShiftIntervalSec(v) }
    suspend fun setShowArtwork(v: Boolean) = context.screensaverDataStore.edit { it[Keys.showArtwork] = v }
    suspend fun setUseDeviceLocation(v: Boolean) = context.screensaverDataStore.edit { it[Keys.useDeviceLocation] = v }

    suspend fun setCity(place: GeoPlace?) = context.screensaverDataStore.edit {
        if (place == null) {
            it.remove(Keys.cityName); it.remove(Keys.cityLat); it.remove(Keys.cityLon)
        } else {
            it[Keys.cityName] = place.name
            it[Keys.cityLat] = place.latitude
            it[Keys.cityLon] = place.longitude
            it[Keys.useDeviceLocation] = false
        }
    }

    suspend fun cachedWeather(): WeatherSnapshot? {
        val p = context.screensaverDataStore.data.first()
        val temp = p[Keys.wTemp] ?: return null
        val code = p[Keys.wCode] ?: return null
        return WeatherSnapshot(
            temperatureC = temp,
            weatherCode = code,
            isDay = p[Keys.wIsDay] ?: true,
            city = p[Keys.wCity],
            fetchedAtMs = p[Keys.wAt] ?: 0L,
            latitude = p[Keys.wLat] ?: 0.0,
            longitude = p[Keys.wLon] ?: 0.0,
        )
    }

    suspend fun saveWeather(s: WeatherSnapshot) = context.screensaverDataStore.edit {
        it[Keys.wTemp] = s.temperatureC
        it[Keys.wCode] = s.weatherCode
        it[Keys.wIsDay] = s.isDay
        if (s.city != null) it[Keys.wCity] = s.city else it.remove(Keys.wCity)
        it[Keys.wAt] = s.fetchedAtMs
        it[Keys.wLat] = s.latitude
        it[Keys.wLon] = s.longitude
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupScreensaverDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    get() = screensaverDataStore
