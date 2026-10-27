package com.nobodybuilds.listens.data.ytmusic

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnRepository
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio cache for streamed YouTube and JioSaavn songs, so a replay is instant and needs
 * no data. Songs not played for [MAX_IDLE_MS] are removed when the app starts, and the
 * cache never grows past [MAX_BYTES]: when full, the least recently played go first.
 * Only those streams use it; local files and other sources are read as before.
 */
@OptIn(UnstableApi::class)
@Singleton
class YouTubeMusicCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeMusicRepository,
    private val jioSaavnRepository: JioSaavnRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.filesDir, "ytmusic_audio_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { created -> scope.launch { removeUnplayed(created) } }
    }

    /** Wraps [upstream] so YouTube and JioSaavn streams are read through the cache. */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory {
        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            // The proxy URL changes port every launch, so key by song and quality instead.
            .setCacheKeyFactory { spec -> cacheKey(spec.uri) ?: spec.key ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DataSource.Factory {
            RoutingDataSource(upstream.createDataSource(), cached.createDataSource()) { uri -> cacheKey(uri) != null }
        }
    }

    /** Different qualities are different files, so each gets its own entry. */
    private fun cacheKey(uri: Uri): String? =
        YouTubeMusicUris.videoIdFromPlaybackUri(uri)?.let { "ytmusic:$it:${repository.streamQuality.value}" }
            ?: JioSaavnUris.songIdFromPlaybackUri(uri)?.let(::jioSaavnCacheKey)

    /**
     * Keyed by the bitrate JioSaavn granted, not the one asked for: signing in or out can
     * hand out a different file for the same setting, and two files must never share an
     * entry. Not cached until the stream is resolved and the bitrate known.
     */
    private fun jioSaavnCacheKey(songId: String): String? =
        jioSaavnRepository.knownStreamBitrate(songId)?.let { "jiosaavn:$songId:$it" }

    private fun removeUnplayed(cache: SimpleCache) {
        val cutoff = System.currentTimeMillis() - MAX_IDLE_MS
        runCatching {
            cache.keys.toList().forEach { key ->
                val lastPlayed = cache.getCachedSpans(key).maxOfOrNull { it.lastTouchTimestamp } ?: return@forEach
                if (lastPlayed < cutoff) cache.removeResource(key)
            }
        }.onFailure { Timber.w(it, "Could not clean the YouTube Music cache") }
    }

    /** Sends each open() to the cached or the plain source, depending on the URI. */
    private class RoutingDataSource(
        private val plain: DataSource,
        private val cached: DataSource,
        private val useCache: (Uri) -> Boolean,
    ) : DataSource {
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            plain.addTransferListener(transferListener)
            cached.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (useCache(dataSpec.uri)) cached else plain
            current = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(current).read(buffer, offset, length)

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

        override fun close() {
            current?.close()
            current = null
        }
    }

    private companion object {
        const val MAX_BYTES = 1024L * 1024 * 1024
        const val MAX_IDLE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
