package com.nobodybuilds.listens.data.noise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

private val Context.noiseDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_noise")

/** How noise behaves when it's the sound of a focus session. */
data class NoiseFocusOptions(
    /** Fade out and stop when the session ends (otherwise it keeps playing). */
    val stopAtSessionEnd: Boolean = true,
    /** Keep playing through breaks (otherwise it pauses until the next focus round). */
    val keepThroughBreaks: Boolean = true,
)

/** Noise settings, your own presets and the focus options. */
@Singleton
class NoisePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store get() = context.noiseDataStore

    /** Your presets (the three built-in ones are not stored). */
    val userPresets: Flow<List<NoisePreset>> = store.data.map { decode(it[PRESETS], presetsSerializer).orEmpty() }

    val focusOptions: Flow<NoiseFocusOptions> = store.data.map {
        NoiseFocusOptions(
            stopAtSessionEnd = it[STOP_AT_END] ?: true,
            keepThroughBreaks = it[KEEP_THROUGH_BREAKS] ?: true,
        )
    }

    suspend fun loadSettings(): NoiseSettings? =
        decode(store.data.first()[SETTINGS], NoiseSettings.serializer())?.normalized()

    suspend fun saveSettings(settings: NoiseSettings) {
        store.edit { it[SETTINGS] = json.encodeToString(NoiseSettings.serializer(), settings) }
    }

    suspend fun savePreset(name: String, settings: NoiseSettings) {
        store.edit { prefs ->
            val list = decode(prefs[PRESETS], presetsSerializer).orEmpty()
                .filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[PRESETS] = json.encodeToString(presetsSerializer, list + NoisePreset(name, settings.copy(presetName = name)))
        }
    }

    suspend fun deletePreset(name: String) {
        store.edit { prefs ->
            val list = decode(prefs[PRESETS], presetsSerializer).orEmpty().filterNot { it.name == name }
            prefs[PRESETS] = json.encodeToString(presetsSerializer, list)
        }
    }

    suspend fun setFocusOptions(options: NoiseFocusOptions) {
        store.edit {
            it[STOP_AT_END] = options.stopAtSessionEnd
            it[KEEP_THROUGH_BREAKS] = options.keepThroughBreaks
        }
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val PRESETS = stringPreferencesKey("presets")
        val STOP_AT_END = booleanPreferencesKey("focus_stop_at_end")
        val KEEP_THROUGH_BREAKS = booleanPreferencesKey("focus_keep_through_breaks")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val presetsSerializer = ListSerializer(NoisePreset.serializer())
    }
}

/** For backup/restore (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupNoiseDataStore: DataStore<Preferences> get() = noiseDataStore
