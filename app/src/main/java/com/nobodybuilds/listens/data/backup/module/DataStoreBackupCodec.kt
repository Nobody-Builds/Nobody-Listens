package com.nobodybuilds.listens.data.backup.module

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * One preference of one feature DataStore inside a backup module. Same value fields as
 * PreferenceBackupEntry plus [store] (the DataStore name, e.g. "lmusic_haptics"), so the module is a
 * plain JSON array and passes the generic preference-entry validation.
 */
data class StoreBackupEntry(
    val store: String,
    val key: String,
    val type: String,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val booleanValue: Boolean? = null,
    val floatValue: Float? = null,
    val doubleValue: Double? = null,
    val stringSetValue: Set<String>? = null
)

/** Pure conversion between DataStore [Preferences] and [StoreBackupEntry] lists. */
object DataStoreBackupCodec {

    fun export(store: String, preferences: Preferences, includeKey: (String) -> Boolean): List<StoreBackupEntry> =
        preferences.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!includeKey(name)) return@mapNotNull null
            when (value) {
                is String -> StoreBackupEntry(store, name, "string", stringValue = value)
                is Int -> StoreBackupEntry(store, name, "int", intValue = value)
                is Long -> StoreBackupEntry(store, name, "long", longValue = value)
                is Boolean -> StoreBackupEntry(store, name, "boolean", booleanValue = value)
                is Float -> StoreBackupEntry(store, name, "float", floatValue = value)
                is Double -> StoreBackupEntry(store, name, "double", doubleValue = value)
                is Set<*> -> StoreBackupEntry(
                    store, name, "string_set",
                    stringSetValue = value.filterIsInstance<String>().toSet()
                )
                else -> null
            }
        }.sortedBy { it.key }

    /**
     * Replaces the keys this module owns ([includeKey]) with [entries] for [store]. Keys the module
     * does not own (caches, running sessions) are left untouched; entries for other stores are ignored.
     */
    fun restoreInto(
        store: String,
        preferences: MutablePreferences,
        entries: List<StoreBackupEntry>,
        includeKey: (String) -> Boolean
    ) {
        preferences.asMap().keys
            .filter { includeKey(it.name) }
            .forEach { key ->
                @Suppress("UNCHECKED_CAST")
                preferences.remove(key as Preferences.Key<Any>)
            }
        entries.asSequence()
            .filter { it.store == store && !(it.key as String?).isNullOrBlank() && includeKey(it.key) }
            .forEach { entry -> write(preferences, entry) }
    }

    private fun write(preferences: MutablePreferences, entry: StoreBackupEntry) {
        when (entry.type) {
            "string" -> entry.stringValue?.let { preferences[stringPreferencesKey(entry.key)] = it }
            // Gson may hand numbers back as doubles; accept any numeric field.
            "int" -> (entry.intValue ?: entry.longValue?.toInt() ?: entry.doubleValue?.toInt())
                ?.let { preferences[intPreferencesKey(entry.key)] = it }
            "long" -> (entry.longValue ?: entry.intValue?.toLong() ?: entry.doubleValue?.toLong())
                ?.let { preferences[longPreferencesKey(entry.key)] = it }
            "boolean" -> entry.booleanValue?.let { preferences[booleanPreferencesKey(entry.key)] = it }
            "float" -> (entry.floatValue ?: entry.doubleValue?.toFloat())
                ?.let { preferences[floatPreferencesKey(entry.key)] = it }
            "double" -> (entry.doubleValue ?: entry.floatValue?.toDouble())
                ?.let { preferences[doublePreferencesKey(entry.key)] = it }
            "string_set" -> entry.stringSetValue?.let { preferences[stringSetPreferencesKey(entry.key)] = it }
        }
    }
}
