package com.nobodybuilds.listens.data.backup.module

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nobodybuilds.listens.data.backup.model.BackupSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataStoreBackupCodecTest {

    @Test
    fun `export then restore round-trips owned keys through gson`() {
        val source = mutablePreferencesOf(
            stringPreferencesKey("config") to "{\"focus\":25}",
            stringPreferencesKey("active_session") to "running",
            booleanPreferencesKey("seek_ticks") to true,
            floatPreferencesKey("ambience_rain") to 0.4f,
            longPreferencesKey("big") to 9_000_000_000L,
            stringSetPreferencesKey("shown_milestones") to setOf("seed", "sprout"),
        )
        val owns: (String) -> Boolean = { it != "active_session" }
        val exported = DataStoreBackupCodec.export("lmusic_focus", source, owns)
        assertFalse(exported.any { it.key == "active_session" })

        val gson = Gson()
        val type = TypeToken.getParameterized(List::class.java, StoreBackupEntry::class.java).type
        val parsed: List<StoreBackupEntry> = gson.fromJson(gson.toJson(exported), type)

        val target = mutablePreferencesOf(
            stringPreferencesKey("active_session") to "keep me",
            stringPreferencesKey("stale") to "remove me",
        )
        DataStoreBackupCodec.restoreInto("lmusic_focus", target, parsed, owns)
        assertEquals("{\"focus\":25}", target[stringPreferencesKey("config")])
        assertEquals("keep me", target[stringPreferencesKey("active_session")])
        assertNull(target[stringPreferencesKey("stale")])
        assertEquals(true, target[booleanPreferencesKey("seek_ticks")])
        assertEquals(0.4f, target[floatPreferencesKey("ambience_rain")])
        assertEquals(9_000_000_000L, target[longPreferencesKey("big")])
        assertEquals(setOf("seed", "sprout"), target[stringSetPreferencesKey("shown_milestones")])
    }

    @Test
    fun `entries for other stores are ignored`() {
        val target = mutablePreferencesOf()
        DataStoreBackupCodec.restoreInto(
            "lmusic_haptics", target,
            listOf(StoreBackupEntry("lmusic_gestures", "shake_to_shuffle", "boolean", booleanValue = true)),
        ) { true }
        assertTrue(target.asMap().isEmpty())
    }

    @Test
    fun `every feature store spec belongs to one of the new sections`() {
        val sections = FeatureDataStores.specs.map { it.section }.toSet()
        assertEquals(setOf(BackupSection.FEATURE_SETTINGS, BackupSection.ACTIVITY_HISTORY), sections)
        // A store split across sections must not have overlapping owned keys.
        val focus = FeatureDataStores.specs.filter { it.name == "lmusic_focus" }
        assertEquals(2, focus.size)
        assertTrue(focus.count { it.includeKey("history") } == 1)
        assertTrue(focus.none { it.includeKey("active_session") })
    }

    @Test
    fun `screensaver settings are backed up but not the weather cache`() {
        val spec = FeatureDataStores.specs.single { it.name == "lmusic_screensaver" }
        assertEquals(BackupSection.FEATURE_SETTINGS, spec.section)
        listOf("offer_when_charging", "show_weather", "clock_style", "shift_interval_sec", "show_artwork",
            "use_device_location", "city_name", "city_lat", "city_lon").forEach { assertTrue(spec.includeKey(it), it) }
        listOf("w_temp", "w_code", "w_is_day", "w_city", "w_at", "w_lat", "w_lon").forEach { assertFalse(spec.includeKey(it), it) }
    }
}
