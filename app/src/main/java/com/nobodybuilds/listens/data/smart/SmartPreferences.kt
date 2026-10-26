package com.nobodybuilds.listens.data.smart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smartDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_smart")

/** Settings for the experimental on-device smart features. */
@Singleton
class SmartPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST_INDEXED_AT = longPreferencesKey("last_indexed_at")
        val MIXES_JSON = stringPreferencesKey("mixes_json")
        val MIXES_COMPUTED_AT = longPreferencesKey("mixes_computed_at")
    }

    val enabled: Flow<Boolean> = context.smartDataStore.data
        .map { it[Keys.ENABLED] ?: false }
        .distinctUntilChanged()

    val lastIndexedAt: Flow<Long> = context.smartDataStore.data
        .map { it[Keys.LAST_INDEXED_AT] ?: 0L }
        .distinctUntilChanged()

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.smartDataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun setLastIndexedAt(value: Long) {
        context.smartDataStore.edit { it[Keys.LAST_INDEXED_AT] = value }
    }

    suspend fun cachedMixes(): Pair<String, Long>? {
        val prefs = context.smartDataStore.data.first()
        val json = prefs[Keys.MIXES_JSON] ?: return null
        return json to (prefs[Keys.MIXES_COMPUTED_AT] ?: 0L)
    }

    suspend fun setCachedMixes(json: String, computedAt: Long) {
        context.smartDataStore.edit {
            it[Keys.MIXES_JSON] = json
            it[Keys.MIXES_COMPUTED_AT] = computedAt
        }
    }

    suspend fun clearCaches() {
        context.smartDataStore.edit {
            it.remove(Keys.MIXES_JSON)
            it.remove(Keys.MIXES_COMPUTED_AT)
            it.remove(Keys.LAST_INDEXED_AT)
        }
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupSmartDataStore: DataStore<Preferences> get() = smartDataStore
