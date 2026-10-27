package com.nobodybuilds.listens.data.garden

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gardenDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_garden")

/** Small bits of garden memory: celebrated milestones, last stage seen, last day the widget grew. */
@Singleton
class GardenPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val SHOWN_MILESTONES = stringSetPreferencesKey("shown_milestones")
        val LAST_SEEN_STAGE = intPreferencesKey("last_seen_stage")
        val WIDGET_DAY = longPreferencesKey("widget_listening_day")
    }

    private val store get() = context.gardenDataStore

    val shownMilestones: Flow<Set<Int>> get() = store.data
        .map { prefs -> prefs[Keys.SHOWN_MILESTONES].orEmpty().mapNotNull { it.toIntOrNull() }.toSet() }
        .distinctUntilChanged()

    suspend fun markMilestonesShown(milestones: Collection<Int>) {
        if (milestones.isEmpty()) return
        store.edit { prefs ->
            prefs[Keys.SHOWN_MILESTONES] = prefs[Keys.SHOWN_MILESTONES].orEmpty() + milestones.map { it.toString() }
        }
    }

    /** -1 when the garden screen has never been opened. */
    suspend fun lastSeenStage(): Int = store.data.first()[Keys.LAST_SEEN_STAGE] ?: -1

    suspend fun setLastSeenStage(ordinal: Int) {
        store.edit { it[Keys.LAST_SEEN_STAGE] = ordinal }
    }

    suspend fun widgetListeningDay(): Long = store.data.first()[Keys.WIDGET_DAY] ?: Long.MIN_VALUE

    suspend fun setWidgetListeningDay(epochDay: Long) {
        store.edit { it[Keys.WIDGET_DAY] = epochDay }
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupGardenDataStore: DataStore<Preferences> get() = gardenDataStore
