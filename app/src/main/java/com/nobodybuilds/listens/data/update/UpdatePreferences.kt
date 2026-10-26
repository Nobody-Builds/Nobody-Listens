package com.nobodybuilds.listens.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nobodybuilds.listens.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_updates")

/** Update-checker settings and the last check result. */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.updateDataStore

    private object Keys {
        val ENABLED = booleanPreferencesKey("weekly_check_enabled")
        val LATEST_VERSION = stringPreferencesKey("latest_version")
        val LATEST_URL = stringPreferencesKey("latest_url")
        val LATEST_NOTES = stringPreferencesKey("latest_notes_first_line")
        val CHECKED_AT = longPreferencesKey("checked_at")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val DISMISSED_VERSION = stringPreferencesKey("banner_dismissed_version")
    }

    val checksEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.ENABLED] ?: true }.distinctUntilChanged()

    val state: Flow<UpdateState> = dataStore.data.map { p ->
        UpdateState(
            latestVersion = p[Keys.LATEST_VERSION],
            url = p[Keys.LATEST_URL],
            notesFirstLine = p[Keys.LATEST_NOTES],
            checkedAt = p[Keys.CHECKED_AT] ?: 0L,
            skippedVersion = p[Keys.SKIPPED_VERSION],
            dismissedVersion = p[Keys.DISMISSED_VERSION],
            installedVersion = BuildConfig.VERSION_NAME,
        )
    }.distinctUntilChanged()

    suspend fun setChecksEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun saveResult(release: ReleaseInfo, checkedAt: Long) {
        dataStore.edit {
            it[Keys.LATEST_VERSION] = release.version
            it[Keys.LATEST_URL] = release.url
            it[Keys.LATEST_NOTES] = release.notesFirstLine
            it[Keys.CHECKED_AT] = checkedAt
        }
    }

    suspend fun skipVersion(version: String) {
        dataStore.edit { it[Keys.SKIPPED_VERSION] = version }
    }

    suspend fun dismissBanner(version: String) {
        dataStore.edit { it[Keys.DISMISSED_VERSION] = version }
    }
}

/** Same DataStore for the backup module (data/backup/module/FeatureDataStores.kt). */
internal val Context.backupUpdateDataStore: DataStore<Preferences> get() = updateDataStore
