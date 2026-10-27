package com.nobodybuilds.listens.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.LyricsDao
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.network.lyrics.LrcLibApiService
import com.nobodybuilds.listens.data.network.lyrics.LrcLibResponse
import com.nobodybuilds.listens.data.repository.LyricsRemoteMatcher.cleanTitleSmart
import com.nobodybuilds.listens.data.repository.LyricsRemoteMatcher.rankRemoteLyricsMatches
import com.nobodybuilds.listens.data.repository.LyricsRepositoryLrcFormat.convertAmlTtmlToLrc
import com.nobodybuilds.listens.utils.LogUtils
import com.nobodybuilds.listens.utils.LyricsUtils
import com.nobodybuilds.listens.utils.NetworkRetryUtils
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

private data class RemoteSearchStrategy(
    val name: String,
    val request: suspend () -> Array<LrcLibResponse>?
)

private data class RemoteSearchBatch(
    val strategyName: String,
    val responses: List<LrcLibResponse>
)

/** LRCLIB search and AMLLDB (NetEase word-by-word) lyrics, with rate limiting and retries. */
internal class LyricsRemoteFetcher(
    private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val lyricsDao: LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val jsonDiskCache: LyricsJsonDiskCache,
) {

    companion object {
        private const val TAG = "LyricsRepository"
        private const val LOG_OWNER = "LyricsRepositoryImpl"

        // API rate limiting constants (matching Rhythm)
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
        private const val AMLLDB_NCM_LYRICS_BASE_URL = "https://amlldb.bikonoo.com/lyrics/ncm-lyrics/"
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L
    }

    // Repository scope for background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe rate limiting state.
    private val lastApiCalls = ConcurrentHashMap<String, Long>()
    private val apiCallCounts = ConcurrentHashMap<String, Int>()

    /**
     * Executes multiple remote search strategies in parallel and returns the first non-empty result set.
     * This keeps search responsive while preserving the existing "first useful strategy wins" behavior.
     */
    private suspend fun runSearchStrategiesFast(
        strategies: List<RemoteSearchStrategy>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()

        val channel = Channel<RemoteSearchBatch>(capacity = strategies.size)
        val jobs = strategies.map { strategy ->
            launch {
                val responses = runCatching {
                    withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") {
                        strategy.request()
                    }
                }.getOrElse { error ->
                    Log.d(
                        TAG,
                        "Strategy ${strategy.name} failed after retries: ${error.message}"
                    )
                    null
                }
                    ?.toList()
                    .orEmpty()
                channel.trySend(
                    RemoteSearchBatch(
                        strategyName = strategy.name,
                        responses = responses
                    )
                )
            }
        }

        repeat(strategies.size) {
            val batch = channel.receive()
            if (batch.responses.isNotEmpty()) {
                Log.d(TAG, "Fast search hit from strategy: ${batch.strategyName} (${batch.responses.size} results)")
                jobs.forEach { it.cancel() }
                channel.close()
                return@coroutineScope batch.responses.distinctBy { it.id }
            }
        }

        channel.close()
        emptyList()
    }

    suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = { it is IOException || (it is HttpException && (it.code() == 429 || it.code() >= 500)) },
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = shouldRetry,
            onRetry = { attempt, attempts, throwable ->
                Log.d(
                    TAG,
                    "Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}"
                )
            },
            block = block
        )
    }

    private fun Int.isRetryableHttpStatusCode(): Boolean {
        return this == 429 || this in 500..599
    }

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls per minute
        val callsInLastMinute = apiCallCounts[apiName] ?: 0
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        // Update call count for rate limiting
        val currentCount = apiCallCounts[apiName] ?: 0
        apiCallCounts[apiName] = currentCount + 1

        // Reset counter every minute
        if (currentCount == 0) {
            repositoryScope.launch {
                delay(60000)
                apiCallCounts[apiName] = 0
            }
        }
    }

    /**
     * Fetches lyrics from LRCLIB API with rate limiting (matching Rhythm)
     */
    suspend fun fetchLyricsFromAPI(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val isNetease = isNeteaseSong(song)

        if (isNetease) {
            val amlLyrics = fetchFromAmlldb(song)
            if (amlLyrics != null) {
                Log.d(TAG, "===== LOADED WORD-BY-WORD LYRICS FROM AMLLDB =====")
                return@withContext amlLyrics
            }
            Log.d(TAG, "AMLLDB unavailable for Netease song, falling back to cache/LRCLIB")
        }

        // Check JSON disk cache first (matching Rhythm)
        val cachedJson = jsonDiskCache.loadLocalLyricsJson(song)
        if (cachedJson != null) {
            Log.d(TAG, "===== LOADED LYRICS FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        // Apply rate limiting
        val currentTime = System.currentTimeMillis()
        val delayNeeded = calculateApiDelay("lrclib", currentTime)
        if (delayNeeded > 0) {
            Log.d(TAG, "Rate limiting: waiting ${delayNeeded}ms before API call")
            delay(delayNeeded)
        }
        updateLastApiCall("lrclib", System.currentTimeMillis())

        try {
            val cleanArtist = song.displayArtist.trim().replace(Regex("\\(.*?\\)"), "").trim()
            val cleanTitle = song.title.trim().replace(Regex("\\(.*?\\)"), "").trim()
            val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
            val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()
            val useSimplifiedStrategy =
                simplifiedArtist != cleanArtist || simplifiedTitle != cleanTitle

            val searchStrategies = buildList {
                add(
                    RemoteSearchStrategy("track+artist") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                    }
                )
                add(
                    RemoteSearchStrategy("combined_query") {
                        lrcLibApiService.searchLyrics(query = "$cleanArtist $cleanTitle")
                    }
                )
                if (useSimplifiedStrategy) {
                    add(
                        RemoteSearchStrategy("simplified_track+artist") {
                            lrcLibApiService.searchLyrics(trackName = simplifiedTitle, artistName = simplifiedArtist)
                        }
                    )
                }
                
                // Smart title cleanup strategy (removes leading digits/spaces and truncates at -, (, ))
                val smartTitle = cleanTitleSmart(cleanTitle)
                if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                    Log.d(TAG, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                    add(RemoteSearchStrategy("smart_track_only") {
                        lrcLibApiService.searchLyrics(trackName = smartTitle)
                    })
                }
            }

            var results = runSearchStrategiesFast(searchStrategies)

            // Strategy 4: Aggressive fallback - remove artist and trim title at separators
            if (results.isEmpty()) {
                 val separators = charArrayOf('-', ',', '(', ')', '$', '#', ':', '%')
                 val index = cleanTitle.indexOfAny(separators)
                 if (index != -1) {
                     val superCleanTitle = cleanTitle.substring(0, index).trim()
                     if (superCleanTitle.isNotEmpty()) {
                          Log.d(TAG, "Strategy 4: Searching with super simplified title: '$superCleanTitle' (no artist)")
                          val fallbackResults = runCatching {
                                withNetworkRetry(operationName = "lrclib_super_clean_search") {
                                    lrcLibApiService.searchLyrics(trackName = superCleanTitle)
                                }
                          }.getOrNull()
                          if (!fallbackResults.isNullOrEmpty()) {
                              results = fallbackResults.toList()
                          }
                     }
                 }
            }

            if (results.isEmpty()) {
                Log.d(TAG, "No results from LRCLIB API")
                return@withContext null
            }

            val bestMatch = rankRemoteLyricsMatches(
                song = song,
                responses = results,
                mode = RemoteLyricsMatchMode.AUTOMATIC
            ).firstOrNull()?.response

            if (bestMatch != null) {
                val rawLyrics = bestMatch.syncedLyrics ?: bestMatch.plainLyrics
                if (!rawLyrics.isNullOrBlank()) {
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (parsedLyrics.isValid()) {
                        Log.d(TAG, "LRCLIB lyrics found - Synced: ${!bestMatch.syncedLyrics.isNullOrBlank()}, Plain: ${!bestMatch.plainLyrics.isNullOrBlank()}")
                        
                        // Save to database
                        try {
                            lyricsDao.insert(
                                com.nobodybuilds.listens.data.database.LyricsEntity(
                                    songId = song.id.toLong(),
                                    content = rawLyrics,
                                    isSynced = !bestMatch.syncedLyrics.isNullOrBlank(),
                                    source = "remote"
                                )
                            )
                        } catch (e: NumberFormatException) {
                            Log.w(TAG, "Skipping database save for non-numeric song ID: ${song.id} (likely Telegram song). Lyrics will be cached in JSON.")
                        }
                        
                        return@withContext parsedLyrics
                    }
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB lyrics fetch failed: ${e.message}", e)
            return@withContext null
        }
    }

    fun isNeteaseSong(song: Song): Boolean =
        song.neteaseId != null || song.contentUriString.startsWith("netease://")

    private fun resolveNeteaseSongId(song: Song): Long? {
        song.neteaseId?.let { return it }
        if (!song.contentUriString.startsWith("netease://")) return null
        return Uri.parse(song.contentUriString).host?.toLongOrNull()
    }

    private suspend fun fetchFromAmlldb(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val neteaseSongId = resolveNeteaseSongId(song) ?: return@withContext null
        val request = Request.Builder()
            .url("$AMLLDB_NCM_LYRICS_BASE_URL$neteaseSongId")
            .get()
            .build()

        try {
            val ttml = withNetworkRetry(
                operationName = "amlldb_fetch:$neteaseSongId",
                shouldRetry = { throwable -> throwable is IOException }
            ) {
                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body?.string().orEmpty()
                        response.code.isRetryableHttpStatusCode() ->
                            throw IOException("AMLLDB HTTP ${response.code} for songId=$neteaseSongId")
                        else -> ""
                    }
                }
            }

            if (ttml.isBlank() || ttml.contains("歌词不存在")) return@withContext null
            val lrc = convertAmlTtmlToLrc(ttml) ?: return@withContext null
            val parsed = LyricsUtils.parseLyrics(lrc)
            if (!parsed.isValid()) return@withContext null
            return@withContext parsed.copy(areFromRemote = true)
        } catch (e: Exception) {
            Log.w(TAG, "AMLLDB fetch failed for $neteaseSongId: ${e.message}")
            return@withContext null
        }
    }

    suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(LOG_OWNER, "Searching remote for lyrics for: ${song.title} by ${song.displayArtist}")

            val combinedQuery = "${song.title} ${song.displayArtist}"
            val cleanTitle = song.title.trim()
            val cleanArtist = song.displayArtist.trim()

            // FAST STRATEGY: run all requests in parallel, keep first non-empty batch
            // FAST STRATEGY: run all requests in parallel, keep first non-empty batch
            val strategies = buildList {
                add(RemoteSearchStrategy("query+artist") {
                    lrcLibApiService.searchLyrics(query = combinedQuery, artistName = cleanArtist)
                })
                add(RemoteSearchStrategy("track+artist") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                })
                
                // Smart title cleanup strategy
                val smartTitle = cleanTitleSmart(cleanTitle)
                if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                    LogUtils.d(LOG_OWNER, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                    add(RemoteSearchStrategy("smart_track_only") {
                        lrcLibApiService.searchLyrics(trackName = smartTitle)
                    })
                }

                add(RemoteSearchStrategy("track_only") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle)
                })
                add(RemoteSearchStrategy("query_title_only") {
                    lrcLibApiService.searchLyrics(query = cleanTitle)
                })
            }

            val uniqueResults = runSearchStrategiesFast(strategies)

            if (uniqueResults.isNotEmpty()) {
                val rankedMatches = rankRemoteLyricsMatches(
                    song = song,
                    responses = uniqueResults,
                    mode = RemoteLyricsMatchMode.CANDIDATE
                )
                val results = rankedMatches.mapNotNull { match ->
                    val response = match.response
                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(LOG_OWNER, "Parsed lyrics are empty for: ${song.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedLyrics.isNullOrEmpty()
                    LogUtils.d(LOG_OWNER, "  Found: ${response.name} by ${response.artistName} (synced: $hasSynced)")
                    LyricsSearchResult(response, parsedLyrics, rawLyrics)
                }

                if (results.isNotEmpty()) {
                    val syncedCount = results.count { !it.record.syncedLyrics.isNullOrEmpty() }
                    LogUtils.d(LOG_OWNER, "Found ${results.size} lyrics for: ${song.title} ($syncedCount with synced)")
                    Result.success(Pair(combinedQuery, results))
                } else {
                    LogUtils.d(LOG_OWNER, "No matching lyrics found for: ${song.title}")
                    Result.failure(NoLyricsFoundException(combinedQuery))
                }
            } else {
                LogUtils.d(LOG_OWNER, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException(combinedQuery))
            }
        } catch (e: Exception) {
            LogUtils.e(LOG_OWNER, e, "Error searching remote for lyrics")
            when {
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e))
            }
        }
    }

    suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim()
            val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
            val query = listOfNotNull(
                cleanTitle.takeIf { it.isNotBlank() },
                cleanArtist
            ).joinToString(" ")

            LogUtils.d(LOG_OWNER, "Manual lyrics search: title=$title, artist=$artist")

            val strategies = buildList {
                add(RemoteSearchStrategy("manual_query") { lrcLibApiService.searchLyrics(query = query) })
                if (!cleanArtist.isNullOrBlank()) {
                    add(
                        RemoteSearchStrategy("manual_track+artist") {
                            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                        }
                    )
                }
            }

            // Run both in parallel and take the first non-empty result set.
            val responses = runSearchStrategiesFast(strategies)

            if (responses.isEmpty()) {
                return@withContext Result.failure(NoLyricsFoundException(query))
            }

            val results = responses.mapNotNull { response ->
                val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                val parsed = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                if (!parsed.isValid()) return@mapNotNull null

                LyricsSearchResult(response, parsed, rawLyrics)
            }.sortedByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

            if (results.isEmpty()) {
                Result.failure(NoLyricsFoundException(query))
            } else {
                Result.success(Pair(query, results))
            }
        } catch (e: Exception) {
            LogUtils.e(LOG_OWNER, e, "Manual search failed")
            Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e)
            )
        }
    }
}
