package com.nobodybuilds.listens.data.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.musicFoldersDataStore: DataStore<Preferences> by preferencesDataStore(name = "lmusic_music_folders")

/** One-time Home notice shown after the music folders were set up from the existing library. */
@Singleton
class MusicFoldersNoticePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val showKey = booleanPreferencesKey("showFoldersMigratedNotice")

    val showFoldersMigratedNotice: Flow<Boolean> = context.musicFoldersDataStore.data
        .map { it[showKey] ?: false }
        .distinctUntilChanged()

    suspend fun setShowFoldersMigratedNotice(value: Boolean) {
        context.musicFoldersDataStore.edit { it[showKey] = value }
    }
}
