package com.nobodybuilds.listens.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.LyricsDao
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.LyricsSourcePreference
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.network.lyrics.LrcLibApiService
import com.nobodybuilds.listens.data.network.lyrics.LrcLibResponse
import com.nobodybuilds.listens.data.repository.LyricsRemoteMatcher.rankRemoteLyricsMatches
import com.nobodybuilds.listens.data.repository.LyricsRepositoryLrcFormat.lyricsToRawContent
import com.nobodybuilds.listens.utils.LogUtils
import com.nobodybuilds.listens.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException

internal fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val lyricsDao: com.nobodybuilds.listens.data.database.LyricsDao,
    private val okHttpClient: OkHttpClient,
    /** YouTube Music's plain lyrics for a video id; used only for streamed YouTube songs. */
    private val youTubeLyrics: (suspend (videoId: String) -> String?)? = null,
    /** JioSaavn's plain lyrics for a song id; used only for streamed JioSaavn songs. */
    private val jioSaavnLyrics: (suspend (songId: String) -> String?)? = null,
) : LyricsRepository {

    companion object {
        private const val TAG = "LyricsRepository"
        
        // Cache sizes (matching Rhythm)
        private const val MAX_LYRICS_CACHE_SIZE = 150
    }

    // Thread-safe LRU cache to avoid race conditions across concurrent lyrics requests.
    private val lyricsCache = LruCache<String, Lyrics>(MAX_LYRICS_CACHE_SIZE)

    private val jsonDiskCache = LyricsJsonDiskCache(context, lyricsDao)
    private val remoteFetcher = LyricsRemoteFetcher(context, lrcLibApiService, lyricsDao, okHttpClient, jsonDiskCache)
    private val localFileSource = LyricsLocalFileSource(context, lyricsDao)

    /**
     * Main lyrics fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        val isNeteaseTrack = remoteFetcher.isNeteaseSong(song)
        
        Log.d(TAG, "===== FETCH LYRICS START: ${song.displayArtist} - ${song.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        // Check in-memory cache unless force refresh (early return - matching Rhythm)
        if (!forceRefresh && !isNeteaseTrack) {
            lyricsCache.get(cacheKey)?.let { cached ->
                Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS =====")
                return@withContext cached
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else if (!forceRefresh && isNeteaseTrack) {
            Log.d(TAG, "===== BYPASSING IN-MEMORY CACHE FOR NETEASE TRACK =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        if (!forceRefresh) {
            loadStoredLyrics(song, cacheKey, includeMemoryCache = false)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                Log.d(TAG, "===== RETURNING STORED LYRICS WITHOUT REMOTE FETCH =====")
                return@withContext stored.first
            }
        }

        // Define source fetchers (matching Rhythm pattern)
        val fetchFromLocal: suspend () -> Lyrics? = {
            localFileSource.findLocalLyricsFile(song)
        }

        val fetchFromEmbedded: suspend () -> Lyrics? = {
            localFileSource.loadEmbeddedLyricsFromMetadata(song)
        }

        val fetchFromAPI: suspend () -> Lyrics? = {
            remoteFetcher.fetchLyricsFromAPI(song)
        }

        // Streamed YouTube and JioSaavn songs have no file, so embedded and local lyrics cannot
        // exist: try the lyrics API (synced) and then the service's own lyrics. Phone songs never go to either.
        val youTubeVideoId = com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris.videoIdFrom(song.contentUriString)
        val jioSaavnId = com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris.songIdFrom(song.contentUriString)
        val serviceLyrics: (suspend () -> String?)? = when {
            youTubeVideoId != null -> { { youTubeLyrics?.invoke(youTubeVideoId) } }
            jioSaavnId != null -> { { jioSaavnLyrics?.invoke(jioSaavnId) } }
            else -> null
        }
        if (serviceLyrics != null) {
            val lyrics = runCatching { fetchFromAPI() }.getOrNull()?.takeIf { it.isValid() }
                ?: runCatching { serviceLyrics() }.getOrNull()
                    ?.lines()?.map { it.trimEnd() }?.takeIf { lines -> lines.any { it.isNotBlank() } }
                    ?.let { lines -> Lyrics(plain = lines, areFromRemote = true) }
            if (lyrics != null) {
                lyricsCache.put(cacheKey, lyrics)
                jsonDiskCache.saveLocalLyricsJson(song, lyrics)
            }
            return@withContext lyrics
        }

        // Try sources in order based on preference, with fallback (matching Rhythm)
        val sourceFetchers = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf(fetchFromAPI, fetchFromEmbedded, fetchFromLocal)
            LyricsSourcePreference.EMBEDDED_FIRST -> listOf(fetchFromEmbedded, fetchFromAPI, fetchFromLocal)
            LyricsSourcePreference.LOCAL_FIRST -> listOf(fetchFromLocal, fetchFromEmbedded, fetchFromAPI)
        }

        // Try each source in order until we find lyrics (early return on success)
        for ((index, fetcher) in sourceFetchers.withIndex()) {
            try {
                val lyrics = fetcher()
                if (lyrics != null && lyrics.isValid()) {
                    val sourceName = when (index) {
                        0 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "API"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Embedded"
                            LyricsSourcePreference.LOCAL_FIRST -> "Local"
                        }
                        1 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Embedded"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "API"
                            LyricsSourcePreference.LOCAL_FIRST -> "Embedded"
                        }
                        else -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Local"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Local"
                            LyricsSourcePreference.LOCAL_FIRST -> "API"
                        }
                    }
                    Log.d(TAG, "Found lyrics from $sourceName for: ${song.displayArtist} - ${song.title}")
                    
                    // Cache the result
                    lyricsCache.put(cacheKey, lyrics)
                    
                    // Save to JSON disk cache if from API
                    if (sourceName == "API") {
                        jsonDiskCache.saveLocalLyricsJson(song, lyrics)
                    }
                    
                    return@withContext lyrics
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source ${index + 1}: ${e.message}")
                // Continue to next source
            }
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: ${song.displayArtist} - ${song.title}")
        return@withContext null
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.also { stored ->
            lyricsCache.put(cacheKey, stored.first)
        }
    }

    private suspend fun loadStoredLyrics(
        song: Song,
        cacheKey: String,
        includeMemoryCache: Boolean
    ): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { rawLyrics ->
                parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
            }

        song.id.toLongOrNull()
            ?.let { lyricsDao.getLyrics(it)?.content }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { rawLyrics ->
                parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
            }

        jsonDiskCache.readLyricsJsonCache(song)
            ?.takeIf { it.hasLyrics() }
            ?.let { data ->
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                if (!rawLyrics.isNullOrBlank()) {
                    parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
                }
            }

        if (includeMemoryCache) {
            lyricsCache.get(cacheKey)?.let { cached ->
                lyricsToRawContent(cached)?.let { rawLyrics ->
                    return@withContext cached to rawLyrics
                }
            }
        }

        null
    }

    private fun parseStoredLyrics(rawLyrics: String): Lyrics? {
        val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics)
        return parsedLyrics
            .takeIf { it.isValid() }
            ?.copy(areFromRemote = false)
    }

    // ========== Original methods (kept for backward compatibility) ==========

    override suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Fetching lyrics from remote for: ${song.title}")

            val cacheKey = generateCacheKey(song.id)
            loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                LogUtils.d(
                    this@LyricsRepositoryImpl,
                    "Skipping remote lyrics fetch because stored lyrics already exist for: ${song.title}"
                )
                return@withContext Result.success(stored)
            }

            // First, try the search API which is more flexible, then pick the best match
            val searchResult = searchRemote(song)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                if (results.isNotEmpty()) {
                    // Pick the first result (already sorted by synced priority)
                    val best = results.first()
                    val rawLyricsToSave = best.rawLyrics

                    try {
                        lyricsDao.insert(
                             com.nobodybuilds.listens.data.database.LyricsEntity(
                                 songId = song.id.toLong(),
                                 content = rawLyricsToSave,
                                 isSynced = !best.lyrics.synced.isNullOrEmpty(),
                                 source = "remote"
                             )
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Skipping DB update for non-numeric ID: ${song.id}")
                    }

                    lyricsCache.put(cacheKey, best.lyrics)
                    jsonDiskCache.saveLocalLyricsJson(song, best.lyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")

                    return@withContext Result.success(Pair(best.lyrics, rawLyricsToSave))
                }
            }

            // Fallback: Try the exact match API (less likely to succeed, but worth a shot)
            val response = remoteFetcher.withNetworkRetry(operationName = "lrclib_get_lyrics") {
                lrcLibApiService.getLyrics(
                    trackName = song.title,
                    artistName = song.displayArtist,
                    albumName = song.album,
                    duration = (song.duration / 1000).toInt()
                )
            }

            val exactMatch = response
                ?.let { rankRemoteLyricsMatches(song, listOf(it), RemoteLyricsMatchMode.AUTOMATIC).firstOrNull()?.response }

            if (exactMatch != null) {
                val rawLyricsToSave = exactMatch.syncedLyrics ?: exactMatch.plainLyrics
                    ?: return@withContext Result.failure(NoLyricsFoundException())

                val parsedLyrics = LyricsUtils.parseLyrics(rawLyricsToSave).copy(areFromRemote = true)
                if (!parsedLyrics.isValid()) {
                    return@withContext Result.failure(LyricsException("Parsed lyrics are empty"))
                }

                try {
                    lyricsDao.insert(
                        com.nobodybuilds.listens.data.database.LyricsEntity(
                            songId = song.id.toLong(),
                            content = rawLyricsToSave,
                            isSynced = !parsedLyrics.synced.isNullOrEmpty(),
                            source = "remote"
                        )
                    )
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Skipping DB update for non-numeric ID in fallback: ${song.id}")
                }

                lyricsCache.put(cacheKey, parsedLyrics)
                jsonDiskCache.saveLocalLyricsJson(song, parsedLyrics)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")

                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            when {
                e is HttpException && e.code() == 404 -> Result.failure(NoLyricsFoundException())
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_fetch_lyrics_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> =
        remoteFetcher.searchRemote(song)

    override suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> =
        remoteFetcher.searchRemoteByQuery(title, artist)

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")

        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }

        lyricsDao.insert(
             com.nobodybuilds.listens.data.database.LyricsEntity(
                 songId = songId,
                 content = lyricsContent,
                 isSynced = parsedLyrics.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.put(cacheKey, parsedLyrics)
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.remove(cacheKey)
        try {
            lyricsDao.deleteLyrics(songId)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing lyrics from DB for ID: $songId", e)
        }
        
        // Also remove JSON cache
        try {
            val file = File(context.filesDir, "lyrics/${songId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        lyricsCache.evictAll()
        lyricsDao.deleteAll()
        
        // Also clear JSON cache directory
        try {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists()) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = localFileSource.scanAndAssignLocalLrcFiles(songs, onProgress)

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics in-memory cache")
        lyricsCache.evictAll()
    }

    private fun generateCacheKey(songId: String): String = songId
}

data class LyricsSearchResult(val record: LrcLibResponse, val lyrics: Lyrics, val rawLyrics: String)

data class NoLyricsFoundException(val query: String? = null) : Exception()

class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
