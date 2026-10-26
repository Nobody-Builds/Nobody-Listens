package com.nobodybuilds.listens.data.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.nobodybuilds.listens.data.database.EngagementDao
import com.nobodybuilds.listens.utils.SmartShuffle
import com.nobodybuilds.listens.utils.SmartShuffleRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.smartShuffleDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_smart_shuffle")

/** Holds the "Smart shuffle" setting and feeds [SmartShuffleRuntime] with recent play history. */
@Singleton
class SmartShuffleController @Inject constructor(
    @ApplicationContext context: Context,
    private val engagementDao: EngagementDao,
) {
    private val dataStore = context.smartShuffleDataStore
    private val enabledKey = booleanPreferencesKey("smart_shuffle_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: true }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    @Volatile
    private var started = false

    /** Mirrors the setting into [SmartShuffleRuntime] (reacts to changes, no polling). */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        SmartShuffleRuntime.refreshRecent = { refreshRecent() }
        scope.launch {
            enabled.collect { on ->
                SmartShuffleRuntime.enabled = on
                if (on) runCatching { refreshRecent() }
            }
        }
    }

    private suspend fun refreshRecent() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - SmartShuffle.RECENT_WINDOW_MS
        SmartShuffleRuntime.recentlyPlayedIds = engagementDao.getRecentlyPlayedSongs(RECENT_LIMIT)
            .asSequence()
            .filter { it.lastPlayedTimestamp >= cutoff }
            .map { it.songId }
            .toSet()
    }

    private companion object {
        const val RECENT_LIMIT = 300
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupSmartShuffleDataStore: DataStore<Preferences> get() = smartShuffleDataStore
