package com.nobodybuilds.listens.data.lofi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lofiDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_lofi")

/** Lofi settings, named presets, favourite stations and ambience volumes. */
@Singleton
class LofiPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store get() = context.lofiDataStore

    val presets: Flow<List<LofiPreset>> = store.data.map { prefs ->
        decode(prefs[PRESETS], presetsSerializer) ?: emptyList()
    }

    val favourites: Flow<List<RadioStation>> = store.data.map { prefs ->
        decode(prefs[FAVOURITES], stationsSerializer) ?: emptyList()
    }

    suspend fun loadSettings(): LofiSettings? =
        decode(store.data.first()[SETTINGS], LofiSettings.serializer())?.normalized()

    suspend fun saveSettings(settings: LofiSettings) {
        store.edit { it[SETTINGS] = json.encodeToString(LofiSettings.serializer(), settings) }
    }

    suspend fun savePreset(name: String, settings: LofiSettings) {
        store.edit { prefs ->
            val list = decode(prefs[PRESETS], presetsSerializer).orEmpty()
                .filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[PRESETS] = json.encodeToString(presetsSerializer, list + LofiPreset(name, settings))
        }
    }

    suspend fun deletePreset(name: String) {
        store.edit { prefs ->
            val list = decode(prefs[PRESETS], presetsSerializer).orEmpty().filterNot { it.name == name }
            prefs[PRESETS] = json.encodeToString(presetsSerializer, list)
        }
    }

    suspend fun toggleFavourite(station: RadioStation) {
        store.edit { prefs ->
            val list = decode(prefs[FAVOURITES], stationsSerializer).orEmpty()
            val next = if (list.any { it.id == station.id }) list.filterNot { it.id == station.id } else list + station
            prefs[FAVOURITES] = json.encodeToString(stationsSerializer, next)
        }
    }

    suspend fun ambienceVolumes(): Map<AmbienceKind, Float> {
        val prefs = store.data.first()
        return AmbienceKind.entries.associateWith { prefs[ambienceKey(it)] ?: 0.5f }
    }

    suspend fun saveAmbienceVolume(kind: AmbienceKind, volume: Float) {
        store.edit { it[ambienceKey(kind)] = volume }
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val PRESETS = stringPreferencesKey("presets")
        val FAVOURITES = stringPreferencesKey("favourite_stations")
        fun ambienceKey(kind: AmbienceKind) = floatPreferencesKey("ambience_${kind.name.lowercase()}")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val presetsSerializer = ListSerializer(LofiPreset.serializer())
        val stationsSerializer = ListSerializer(RadioStation.serializer())
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupLofiDataStore: DataStore<Preferences> get() = lofiDataStore
