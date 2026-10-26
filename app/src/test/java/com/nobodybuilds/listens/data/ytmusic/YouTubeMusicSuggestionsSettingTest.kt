package com.nobodybuilds.listens.data.ytmusic

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

class YouTubeMusicSuggestionsSettingTest {

    private val prefsByName = mutableMapOf<String, FakePrefs>()
    private var requests = 0

    // YouTubeMusicClients.http() drops interceptors, but keeps the event listener and DNS: count calls there, and
    // fail every lookup so nothing reaches the network.
    private val client = OkHttpClient.Builder()
        .eventListener(object : EventListener() {
            override fun callStart(call: Call) { requests++ }
        })
        .dns { throw UnknownHostException("offline test") }
        .build()

    private val context = mockk<Context>(relaxed = true).also { ctx ->
        every { ctx.getSharedPreferences(any(), any()) } answers { prefsByName.getOrPut(firstArg()) { FakePrefs() } }
    }

    @Test
    fun `suggestions are off by default and never reach YouTube`() = runTest {
        val repository = YouTubeMusicRepository(context, client)

        assertFalse(repository.searchSuggestionsEnabled.value)
        assertEquals(emptyList<String>(), repository.searchSuggestions("daft punk"))
        assertEquals(0, requests)
    }

    @Test
    fun `turning suggestions on is remembered and asks YouTube`() = runTest {
        YouTubeMusicRepository(context, client).setSearchSuggestionsEnabled(true)
        val repository = YouTubeMusicRepository(context, client)

        assertTrue(repository.searchSuggestionsEnabled.value)
        runCatching { repository.searchSuggestions("daft punk") }
        assertTrue(requests > 0)
    }

    /** In-memory SharedPreferences: enough for the repository's settings and session store. */
    private class FakePrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>? ?: defValues)
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { values[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) = apply { values[key] = value }
            override fun putInt(key: String, value: Int) = apply { values[key] = value }
            override fun putLong(key: String, value: Long) = apply { values[key] = value }
            override fun putFloat(key: String, value: Float) = apply { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
            override fun remove(key: String) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun commit() = true
            override fun apply() = Unit
        }
    }
}
