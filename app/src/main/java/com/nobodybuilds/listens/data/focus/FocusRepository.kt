package com.nobodybuilds.listens.data.focus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_focus")

/** One completed focus round. [date] is the local day it finished on, ISO `yyyy-MM-dd`. */
@Serializable
data class FocusHistory(
    val date: String,
    val minutes: Int,
    /** Wall-clock millis when the round finished. */
    val completedAt: Long,
) {
    val localDate: LocalDate get() = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.MIN)
}

/**
 * Focus sessions storage: settings, the running session (for restore after process death) and
 * the history of completed focus rounds. DataStore `lmusic_focus`.
 *
 * ## Public API for other features (e.g. the Listening garden)
 *
 * Inject `FocusRepository` (Hilt `@Singleton`). Everything is read-only for you:
 *
 * - `history: Flow<List<FocusHistory>>` — every completed focus round, oldest first
 *   (`FocusHistory(date = "2026-10-18", minutes = 25, completedAt = <epoch millis>)`).
 *   One entry is appended each time a focus round runs to its end (skipped/stopped rounds
 *   are not recorded). Kept for ~400 days, at most 4000 entries.
 * - `minutesByDay: Flow<Map<LocalDate, Int>>` — focus minutes per local day.
 * - `completedRounds: Flow<Int>` — total completed rounds ever kept (handy for "growth").
 * - `todayMinutes: Flow<Int>` — minutes focused today.
 *
 * Example: `focusRepository.history.map { it.size }` grows by one per finished round.
 * Constructors do no work; flows read DataStore lazily on collection.
 */
@Singleton
class FocusRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val store get() = context.focusDataStore

    val history: Flow<List<FocusHistory>> = store.data
        .map { decode(it[HISTORY], historySerializer).orEmpty() }
        .distinctUntilChanged()

    val minutesByDay: Flow<Map<LocalDate, Int>> = history.map { list ->
        list.groupBy { it.localDate }.mapValues { (_, v) -> v.sumOf { it.minutes } }
    }

    val completedRounds: Flow<Int> = history.map { it.size }

    val todayMinutes: Flow<Int> = minutesByDay.map { it[LocalDate.now()] ?: 0 }

    val config: Flow<FocusConfig> = store.data
        .map { decode(it[CONFIG], FocusConfig.serializer())?.normalized() ?: FocusConfig() }
        .distinctUntilChanged()

    val musicSource: Flow<FocusMusicSource> = store.data
        .map { prefs -> prefs[MUSIC]?.let { v -> FocusMusicSource.entries.firstOrNull { it.name == v } } ?: FocusMusicSource.Lofi }
        .distinctUntilChanged()

    suspend fun setConfig(config: FocusConfig) {
        store.edit { it[CONFIG] = json.encodeToString(FocusConfig.serializer(), config.normalized()) }
    }

    suspend fun setMusicSource(source: FocusMusicSource) {
        store.edit { it[MUSIC] = source.name }
    }

    suspend fun recordCompleted(minutes: Int, completedAt: Long = System.currentTimeMillis()) {
        val day = Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val cutoff = day.minusDays(KEEP_DAYS).toString()
        store.edit { prefs ->
            val list = decode(prefs[HISTORY], historySerializer).orEmpty()
            val updated = (list + FocusHistory(day.toString(), minutes, completedAt))
                .filter { it.date >= cutoff }
                .takeLast(MAX_ENTRIES)
            prefs[HISTORY] = json.encodeToString(historySerializer, updated)
        }
    }

    // ---------------------------------------------------------------- running session

    internal suspend fun loadActive(): ActiveFocusSession? =
        decode(store.data.first()[ACTIVE], ActiveFocusSession.serializer())

    internal suspend fun saveActive(session: ActiveFocusSession?) {
        store.edit { prefs ->
            if (session == null) prefs.remove(ACTIVE)
            else prefs[ACTIVE] = json.encodeToString(ActiveFocusSession.serializer(), session)
        }
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private companion object {
        val CONFIG = stringPreferencesKey("config")
        val MUSIC = stringPreferencesKey("music_source")
        val ACTIVE = stringPreferencesKey("active_session")
        val HISTORY = stringPreferencesKey("history")
        const val KEEP_DAYS = 400L
        const val MAX_ENTRIES = 4000
        val historySerializer = ListSerializer(FocusHistory.serializer())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/** What is persisted while a session runs. */
@Serializable
internal data class ActiveFocusSession(
    val timer: FocusTimerState,
    val music: FocusMusicSource,
    val savedAt: Long = 0L,
)

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupFocusDataStore: DataStore<Preferences> get() = focusDataStore
