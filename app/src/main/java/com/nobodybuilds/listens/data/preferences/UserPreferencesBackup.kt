package com.nobodybuilds.listens.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Backup export/import and bulk clearing of the settings DataStore behind [UserPreferencesRepository]. */
internal class UserPreferencesBackup(
    private val dataStore: DataStore<Preferences>
) {

    private val backupExcludedKeyNames = setOf(
        UserPreferencesKeys.INITIAL_SETUP_DONE.name
    )

    suspend fun clearPreferencesByKeys(keyNames: Set<String>) {
        if (keyNames.isEmpty()) return
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { key -> key.name in keyNames && key.name !in backupExcludedKeyNames }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun clearPreferencesExceptKeys(excludedKeyNames: Set<String>) {
        val protectedKeyNames = excludedKeyNames + backupExcludedKeyNames
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filterNot { key -> key.name in protectedKeyNames }
                .forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    preferences.remove(key as Preferences.Key<Any>)
                }
        }
    }

    suspend fun exportPreferencesForBackup(): List<PreferenceBackupEntry> {
        val snapshot = dataStore.data.first().asMap()
        return snapshot.mapNotNull { (key, value) ->
            val keyName = key.name
            if (keyName in backupExcludedKeyNames) {
                return@mapNotNull null
            }
            when (value) {
                is String -> PreferenceBackupEntry(
                    key = keyName,
                    type = "string",
                    stringValue = value
                )
                is Int -> PreferenceBackupEntry(
                    key = keyName,
                    type = "int",
                    intValue = value
                )
                is Long -> PreferenceBackupEntry(
                    key = keyName,
                    type = "long",
                    longValue = value
                )
                is Boolean -> PreferenceBackupEntry(
                    key = keyName,
                    type = "boolean",
                    booleanValue = value
                )
                is Float -> PreferenceBackupEntry(
                    key = keyName,
                    type = "float",
                    floatValue = value
                )
                is Double -> PreferenceBackupEntry(
                    key = keyName,
                    type = "double",
                    doubleValue = value
                )
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    PreferenceBackupEntry(
                        key = keyName,
                        type = "string_set",
                        stringSetValue = stringSet
                    )
                }
                else -> null
            }
        }
    }

    suspend fun importPreferencesFromBackup(
        entries: List<PreferenceBackupEntry>,
        clearExisting: Boolean = true
    ) {
        dataStore.edit { preferences ->
            if (clearExisting) {
                preferences.asMap().keys
                    .filterNot { key -> key.name in backupExcludedKeyNames }
                    .forEach { key ->
                        @Suppress("UNCHECKED_CAST")
                        preferences.remove(key as Preferences.Key<Any>)
                    }
            }

            entries.forEach { entry ->
                if (entry.key in backupExcludedKeyNames) {
                    return@forEach
                }
                when (entry.type) {
                    "string" -> {
                        val value = entry.stringValue ?: return@forEach
                        preferences[stringPreferencesKey(entry.key)] = value
                    }
                    "int" -> {
                        val value = entry.intValue
                            ?: entry.doubleValue?.toInt()
                            ?: entry.longValue?.toInt()
                            ?: return@forEach
                        preferences[intPreferencesKey(entry.key)] = value
                    }
                    "long" -> {
                        val value = entry.longValue
                            ?: entry.doubleValue?.toLong()
                            ?: entry.intValue?.toLong()
                            ?: return@forEach
                        preferences[longPreferencesKey(entry.key)] = value
                    }
                    "boolean" -> {
                        val value = entry.booleanValue ?: return@forEach
                        preferences[booleanPreferencesKey(entry.key)] = value
                    }
                    "float" -> {
                        val value = entry.floatValue
                            ?: entry.doubleValue?.toFloat()
                            ?: return@forEach
                        preferences[androidx.datastore.preferences.core.floatPreferencesKey(entry.key)] = value
                    }
                    "double" -> {
                        val value = entry.doubleValue
                            ?: entry.floatValue?.toDouble()
                            ?: return@forEach
                        preferences[androidx.datastore.preferences.core.doublePreferencesKey(entry.key)] = value
                    }
                    "string_set" -> {
                        val value = entry.stringSetValue ?: return@forEach
                        preferences[stringSetPreferencesKey(entry.key)] = value
                    }
                }
            }
        }
    }
}
