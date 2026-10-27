package com.nobodybuilds.listens.data.taste

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.tasteDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_taste")

@Serializable
data class TasteMatchHistoryEntry(val name: String, val percent: Int, val timestamp: Long)

/** Display name shown with your taste code, and a short history of past matches. */
@Singleton
class TastePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val NAME = stringPreferencesKey("display_name")
        val HISTORY = stringPreferencesKey("history")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val historySerializer = ListSerializer(TasteMatchHistoryEntry.serializer())

    /** Null until the user has confirmed a name once. */
    val displayName: Flow<String?> = context.tasteDataStore.data
        .map { it[Keys.NAME] }
        .distinctUntilChanged()

    val history: Flow<List<TasteMatchHistoryEntry>> = context.tasteDataStore.data
        .map { prefs ->
            prefs[Keys.HISTORY]?.let { raw ->
                runCatching { json.decodeFromString(historySerializer, raw) }.getOrNull()
            }.orEmpty()
        }
        .distinctUntilChanged()

    suspend fun setDisplayName(name: String) {
        context.tasteDataStore.edit { it[Keys.NAME] = name.trim().take(40) }
    }

    suspend fun addHistory(entry: TasteMatchHistoryEntry) {
        context.tasteDataStore.edit { prefs ->
            val current = prefs[Keys.HISTORY]?.let { raw ->
                runCatching { json.decodeFromString(historySerializer, raw) }.getOrNull()
            }.orEmpty()
            val updated = (listOf(entry) + current.filterNot {
                it.name.equals(entry.name, ignoreCase = true) && entry.timestamp - it.timestamp < 60 * 60 * 1000L
            }).take(MAX_HISTORY)
            prefs[Keys.HISTORY] = json.encodeToString(historySerializer, updated)
        }
    }

    suspend fun clearHistory() {
        context.tasteDataStore.edit { it.remove(Keys.HISTORY) }
    }

    companion object {
        const val MAX_HISTORY = 20
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupTasteDataStore: DataStore<Preferences> get() = tasteDataStore
