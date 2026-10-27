package com.nobodybuilds.listens.data.navidrome

import android.content.Context
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.NavidromeDao
import com.nobodybuilds.listens.data.database.toEntity
import com.nobodybuilds.listens.data.navidrome.model.NavidromeSong
import com.nobodybuilds.listens.data.network.navidrome.NavidromeApiService
import com.nobodybuilds.listens.data.network.navidrome.NavidromeResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/** Syncs every song in the Navidrome library, album by album, into the library playlist. */
internal class NavidromeLibrarySongs(
    private val api: NavidromeApiService,
    private val dao: NavidromeDao,
    private val context: Context
) {
    private companion object {
        private const val TAG = "NavidromeRepo"
        private const val LIBRARY_PLAYLIST_ID = "__library__"
    }

    /** The caller checks that the user is logged in. */
    suspend fun syncLibrarySongs(onProgress: ((Float, String) -> Unit)?): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing library songs from server")
                val allSongs = mutableListOf<NavidromeSong>()
                val pageSize = 500
                
                onProgress?.invoke(0.1f, context.getString(R.string.dash_status_fetching_albums))
                val fetchedAlbums = fetchAllAlbums(pageSize)

                // Fetch songs for each album in parallel
                val totalAlbums = fetchedAlbums.size
                val concurrencyLimit = 5
                val semaphore = Semaphore(concurrencyLimit)
                val processedCount = AtomicInteger(0)

                val albumSongLists = coroutineScope {
                    fetchedAlbums.map { albumJson ->
                        async {
                            semaphore.withPermit {
                                val albumId = albumJson.optString("id", "")
                                val albumTitle = albumJson.optString("title", "Unknown Album")
                                if (albumId.isBlank()) return@withPermit emptyList()

                                val songsResult = api.getAlbum(albumId)
                                val currentProcessed = processedCount.incrementAndGet()
                                
                                val progress = 0.1f + (currentProcessed.toFloat() / totalAlbums.coerceAtLeast(1) * 0.8f)
                                onProgress?.invoke(
                                    progress, 
                                    context.getString(R.string.dash_status_fetching_songs_from_format, albumTitle)
                                )

                                songsResult.fold(
                                    onSuccess = { songJsons ->
                                        NavidromeResponseParser.parseSongs(songJsons)
                                    },
                                    onFailure = {
                                        Timber.w(it, "$TAG: Failed to fetch songs for album $albumId")
                                        emptyList()
                                    }
                                )
                            }
                        }
                    }.awaitAll()
                }

                allSongs.addAll(albumSongLists.flatten())

                if (allSongs.isEmpty()) {
                    Timber.d("$TAG: No library songs found on server")
                    onProgress?.invoke(1f, context.getString(R.string.dash_status_no_songs_found))
                    return@withContext Result.success(0)
                }

                onProgress?.invoke(
                    0.95f, 
                    context.getString(R.string.dash_status_saving_songs_format, allSongs.size)
                )
                // Deduplicate by song ID
                val uniqueSongs = allSongs.distinctBy { it.id }

                val entities = uniqueSongs.map { song ->
                    song.toEntity(LIBRARY_PLAYLIST_ID)
                }

                // Replace all library songs
                dao.clearLibrarySongs()
                dao.insertSongs(entities)

                Timber.d("$TAG: Synced ${entities.size} library songs from ${fetchedAlbums.size} albums")
                onProgress?.invoke(1f, context.getString(R.string.dash_status_library_sync_complete))
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync library songs")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch all albums from server with pagination.
     */
    private suspend fun fetchAllAlbums(pageSize: Int): List<JSONObject> {
        val allAlbums = mutableListOf<JSONObject>()
        var offset = 0

        while (true) {
            val albumsResult = api.getAlbumList(
                type = "alphabeticalByName",
                size = pageSize,
                offset = offset
            )

            val albumJsons = albumsResult.getOrNull()
            if (albumJsons.isNullOrEmpty()) break

            allAlbums.addAll(albumJsons)
            offset += albumJsons.size
            if (albumJsons.size < pageSize) break
        }

        return allAlbums
    }
}
