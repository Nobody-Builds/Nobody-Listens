package com.twilighty.lmusic.data.backup.module

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twilighty.lmusic.data.backup.model.BackupSection
import com.twilighty.lmusic.data.focus.backupFocusDataStore
import com.twilighty.lmusic.data.garden.backupGardenDataStore
import com.twilighty.lmusic.data.gestures.backupGesturesDataStore
import com.twilighty.lmusic.data.lofi.backupLofiDataStore
import com.twilighty.lmusic.data.noise.backupNoiseDataStore
import com.twilighty.lmusic.data.playback.backupSmartShuffleDataStore
import com.twilighty.lmusic.data.screensaver.backupScreensaverDataStore
import com.twilighty.lmusic.data.sleep.backupSleepDataStore
import com.twilighty.lmusic.data.smart.backupSmartDataStore
import com.twilighty.lmusic.data.taste.backupTasteDataStore
import com.twilighty.lmusic.data.update.backupUpdateDataStore
import com.twilighty.lmusic.di.BackupGson
import com.twilighty.lmusic.presentation.components.player.backupLivingArtworkDataStore
import com.twilighty.lmusic.presentation.haptics.backupHapticsDataStore
import com.twilighty.lmusic.presentation.nb.backupHomeDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which keys of one feature DataStore a backup section owns. */
class FeatureStoreSpec(
    val name: String,
    val section: BackupSection,
    val includeKey: (String) -> Boolean,
    val access: (Context) -> DataStore<Preferences>,
)

/**
 * The feature DataStores added after the original backup format, and what is backed up from each.
 *
 * Backed up:
 * - FEATURE_SETTINGS: haptics, gestures, Focus timer + music settings, lofi settings / presets /
 *   favourite stations / ambience levels, noise settings / presets / focus options, Sleep space
 *   choices (sound, timer, fade), smart shuffle, Home preferences, update check switch and
 *   skipped version, living artwork, smart layer switch, Taste match display name, screensaver
 *   settings (clock style, weather switch, city, artwork, burn-in shift, offer when charging).
 * - ACTIVITY_HISTORY: finished Focus rounds, Garden milestones already shown, Taste match history.
 *   (Garden growth itself is computed from playback history + Focus rounds.)
 *
 * Explicitly not backed up:
 * - Music folders include list: lives in the main settings store, so it is already in GLOBAL_SETTINGS
 *   (`included_directories`). The "folders migrated" notice (lmusic_music_folders) is a one-time hint.
 * - Party: no persisted settings (sessions are in-memory only).
 * - Running state and caches: Focus `active_session`, Garden `widget_listening_day`, update check
 *   results (`latest_*`, `checked_at`, `banner_dismissed_version`), smart index/mix caches.
 * - Screensaver weather cache (`w_*` keys); the screensaver settings themselves are backed up.
 * - lmusic_nav_gestures: still being built; add it here once it settles.
 * - lmusic_tips (one-time hints such as the battery tip).
 * - Crash reports, safe mode, and cloud account credentials (SharedPreferences) — device-specific
 *   or secret.
 */
object FeatureDataStores {
    private fun all(): (String) -> Boolean = { true }
    private fun only(vararg keys: String): (String) -> Boolean { val set = keys.toSet(); return { it in set } }
    private fun except(vararg keys: String): (String) -> Boolean { val set = keys.toSet(); return { it !in set } }

    val specs: List<FeatureStoreSpec> = listOf(
        FeatureStoreSpec("lmusic_haptics", BackupSection.FEATURE_SETTINGS, all()) { it.backupHapticsDataStore },
        FeatureStoreSpec("lmusic_gestures", BackupSection.FEATURE_SETTINGS, all()) { it.backupGesturesDataStore },
        FeatureStoreSpec("lmusic_focus", BackupSection.FEATURE_SETTINGS, only("config", "music_source")) { it.backupFocusDataStore },
        FeatureStoreSpec("lmusic_lofi", BackupSection.FEATURE_SETTINGS, all()) { it.backupLofiDataStore },
        FeatureStoreSpec("lmusic_noise", BackupSection.FEATURE_SETTINGS, all()) { it.backupNoiseDataStore },
        FeatureStoreSpec("lmusic_sleep", BackupSection.FEATURE_SETTINGS, all()) { it.backupSleepDataStore },
        FeatureStoreSpec("lmusic_smart_shuffle", BackupSection.FEATURE_SETTINGS, all()) { it.backupSmartShuffleDataStore },
        FeatureStoreSpec("lmusic_home", BackupSection.FEATURE_SETTINGS, all()) { it.backupHomeDataStore },
        FeatureStoreSpec("lmusic_updates", BackupSection.FEATURE_SETTINGS, only("weekly_check_enabled", "skipped_version")) { it.backupUpdateDataStore },
        FeatureStoreSpec("lmusic_living_artwork", BackupSection.FEATURE_SETTINGS, all()) { it.backupLivingArtworkDataStore },
        FeatureStoreSpec("lmusic_smart", BackupSection.FEATURE_SETTINGS, only("enabled")) { it.backupSmartDataStore },
        FeatureStoreSpec("lmusic_taste", BackupSection.FEATURE_SETTINGS, only("display_name")) { it.backupTasteDataStore },
        // Weather cache keys all start with "w_"; everything else is a user setting.
        FeatureStoreSpec("lmusic_screensaver", BackupSection.FEATURE_SETTINGS, { !it.startsWith("w_") }) { it.backupScreensaverDataStore },

        FeatureStoreSpec("lmusic_focus", BackupSection.ACTIVITY_HISTORY, only("history")) { it.backupFocusDataStore },
        FeatureStoreSpec("lmusic_garden", BackupSection.ACTIVITY_HISTORY, except("widget_listening_day")) { it.backupGardenDataStore },
        FeatureStoreSpec("lmusic_taste", BackupSection.ACTIVITY_HISTORY, only("history")) { it.backupTasteDataStore },
    )

    fun specsFor(section: BackupSection): List<FeatureStoreSpec> = specs.filter { it.section == section }
}

/** Backs up a set of feature DataStores as one JSON array of [StoreBackupEntry]. */
abstract class FeatureStoresModuleHandler(
    private val context: Context,
    private val gson: Gson,
) : BackupModuleHandler {

    private val specs: List<FeatureStoreSpec> by lazy { FeatureDataStores.specsFor(section) }

    private suspend fun collect(): List<StoreBackupEntry> = specs.flatMap { spec ->
        val prefs = runCatching { spec.access(context).data.first() }.getOrNull() ?: return@flatMap emptyList()
        DataStoreBackupCodec.export(spec.name, prefs, spec.includeKey)
    }

    override suspend fun export(): String = withContext(Dispatchers.IO) { gson.toJson(collect()) }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) { collect().size }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, StoreBackupEntry::class.java).type
        val entries: List<StoreBackupEntry> = gson.fromJson<List<StoreBackupEntry>>(payload, type).orEmpty()
        // Only stores present in the file are replaced, so a backup made before a store existed
        // never wipes that feature's current settings.
        val storesInFile = entries.mapTo(HashSet()) { it.store }
        specs.filter { it.name in storesInFile }.forEach { spec ->
            spec.access(context).edit { prefs ->
                DataStoreBackupCodec.restoreInto(spec.name, prefs, entries, spec.includeKey)
            }
        }
    }

    override suspend fun rollback(snapshot: String) {
        // A snapshot lists every owned key that existed; stores with nothing to restore are cleared
        // back to their (empty) owned state too.
        val type = TypeToken.getParameterized(List::class.java, StoreBackupEntry::class.java).type
        val entries: List<StoreBackupEntry> = gson.fromJson<List<StoreBackupEntry>>(snapshot, type).orEmpty()
        withContext(Dispatchers.IO) {
            specs.forEach { spec ->
                spec.access(context).edit { prefs ->
                    DataStoreBackupCodec.restoreInto(spec.name, prefs, entries, spec.includeKey)
                }
            }
        }
    }
}

@Singleton
class FeatureSettingsModuleHandler @Inject constructor(
    @ApplicationContext context: Context,
    @BackupGson gson: Gson,
) : FeatureStoresModuleHandler(context, gson) {
    override val section = BackupSection.FEATURE_SETTINGS
}

@Singleton
class ActivityHistoryModuleHandler @Inject constructor(
    @ApplicationContext context: Context,
    @BackupGson gson: Gson,
) : FeatureStoresModuleHandler(context, gson) {
    override val section = BackupSection.ACTIVITY_HISTORY
}
