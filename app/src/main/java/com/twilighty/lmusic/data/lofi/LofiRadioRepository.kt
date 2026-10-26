package com.twilighty.lmusic.data.lofi

import android.content.Context
import com.twilighty.lmusic.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lofi radio: a short curated list plus the free radio-browser.info directory (no key).
 * Results are cached in memory (6 h) and on disk (offline fallback).
 */
@Singleton
class LofiRadioRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    baseClient: OkHttpClient,
) {
    private val client: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }
    private val memory = ConcurrentHashMap<String, Pair<Long, List<RadioStation>>>()

    /** Streams checked by hand; all plain MP3 over HTTPS. */
    val curated: List<RadioStation> = listOf(
        RadioStation("somafm-groovesalad", "Groove Salad", "https://ice2.somafm.com/groovesalad-128-mp3",
            "https://somafm.com/img/groovesalad120.png", 128, "US", "ambient,chill,downtempo"),
        RadioStation("somafm-fluid", "Fluid", "https://ice2.somafm.com/fluid-128-mp3",
            "https://somafm.com/img/fluid120.jpg", 128, "US", "instrumental hip hop,chillhop"),
        RadioStation("somafm-lush", "Lush", "https://ice2.somafm.com/lush-128-mp3",
            "https://somafm.com/img/lush120.jpg", 128, "US", "chill,electronic,vocals"),
        RadioStation("lautfm-lofi", "laut.fm lofi", "https://stream.laut.fm/lofi",
            null, 128, "DE", "lofi"),
        RadioStation("reyfm-lofi", "REYFM #lofi", "https://listen.reyfm.de/lofi_320kbps.mp3",
            null, 320, "DE", "lofi,chillhop"),
        RadioStation("isekoi-chill", "ISEKOI Chill Zone", "https://public.isekoi-radio.com/listen/chill/radio.mp3",
            null, 320, "FR", "lofi,chill"),
        RadioStation("cliamp-lofi", "CLIAMP Lofi Hip Hop", "https://radio.cliamp.stream/lofi/stream",
            null, 128, "NO", "lofi,hip hop"),
    )

    val suggestedTags = listOf("lofi", "chillhop", "lo-fi", "chill", "jazzhop", "study")

    /** Stations for a tag, most popular first. Falls back to the disk cache when offline. */
    suspend fun byTag(tag: String, forceRefresh: Boolean = false): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        val key = tag.trim().lowercase()
        if (key.isEmpty()) return@withContext Result.success(emptyList())
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            memory[key]?.let { (at, list) -> if (now - at < TTL_MS) return@withContext Result.success(list) }
        }
        var lastError: Throwable? = null
        for (host in HOSTS) {
            try {
                val url = "$host/json/stations/bytag".toHttpUrl().newBuilder()
                    .addPathSegment(key)
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("order", "clickcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("limit", "50")
                    .build()
                val body = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body.string()
                }
                val stations = json.decodeFromString(ListSerializer(ApiStation.serializer()), body)
                    .mapNotNull { it.toStation() }
                    .distinctBy { it.url }
                    .distinctBy { it.id }
                memory[key] = now to stations
                runCatching { cacheFile(key).writeText(json.encodeToString(ListSerializer(RadioStation.serializer()), stations)) }
                return@withContext Result.success(stations)
            } catch (t: Throwable) {
                lastError = t
                Timber.tag(TAG).d(t, "radio-browser %s failed", host)
            }
        }
        val cached = runCatching {
            json.decodeFromString(ListSerializer(RadioStation.serializer()), cacheFile(key).readText())
        }.getOrNull()
        if (cached != null) Result.success(cached) else Result.failure(lastError ?: IllegalStateException("No result"))
    }

    /** Tells radio-browser a station was played (helps its popularity ranking). Fire and forget. */
    suspend fun reportClick(station: RadioStation) = withContext(Dispatchers.IO) {
        if (!UUID_REGEX.matches(station.id)) return@withContext
        runCatching {
            client.newCall(Request.Builder().url("${HOSTS.first()}/json/url/${station.id}").build()).execute().close()
        }
    }

    private fun cacheFile(tag: String) = File(context.cacheDir, "lofi_radio_${tag.replace(Regex("[^a-z0-9-]"), "_")}.json")

    @Serializable
    private data class ApiStation(
        @SerialName("stationuuid") val id: String = "",
        val name: String = "",
        @SerialName("url_resolved") val urlResolved: String = "",
        val url: String = "",
        val favicon: String = "",
        val bitrate: Int = 0,
        @SerialName("countrycode") val countryCode: String = "",
        val codec: String = "",
        val hls: Int = 0,
        val tags: String = "",
    ) {
        fun toStation(): RadioStation? {
            val stream = urlResolved.ifBlank { url }.trim()
            if (id.isBlank() || name.isBlank() || stream.isBlank()) return null
            // HLS needs an extra decoder module we don't ship; skip those.
            if (hls == 1 || stream.contains(".m3u8", ignoreCase = true) || codec.equals("UNKNOWN", true) && stream.endsWith("m3u8")) return null
            return RadioStation(
                id = id,
                name = name.trim(),
                url = stream,
                favicon = favicon.takeIf { it.startsWith("http") },
                bitrate = bitrate,
                country = countryCode.ifBlank { null },
                tags = tags.ifBlank { null },
            )
        }
    }

    companion object {
        private const val TAG = "LofiRadio"
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        val USER_AGENT = "NobodyListens/${BuildConfig.VERSION_NAME}"
        private val HOSTS = listOf(
            "https://de1.api.radio-browser.info",
            "https://all.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
        )
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    }
}
