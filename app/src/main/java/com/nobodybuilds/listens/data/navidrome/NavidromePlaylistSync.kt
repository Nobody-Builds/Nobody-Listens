package com.nobodybuilds.listens.data.navidrome

import com.nobodybuilds.listens.data.database.NavidromeDao
import com.nobodybuilds.listens.data.database.NavidromePlaylistEntity
import com.nobodybuilds.listens.data.database.NavidromeSongEntity
import com.nobodybuilds.listens.data.database.toEntity
import com.nobodybuilds.listens.data.navidrome.model.NavidromeSong
import com.nobodybuilds.listens.data.network.navidrome.NavidromeApiService
import com.nobodybuilds.listens.data.network.navidrome.NavidromeResponseParser
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** Syncs Navidrome playlists and their songs, and mirrors each one as an app playlist. */
internal class NavidromePlaylistSync(
    private val api: NavidromeApiService,
    private val dao: NavidromeDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val unifiedLibrary: NavidromeUnifiedLibrary
) {
    private companion object {
        private const val TAG = "NavidromeRepo"
        private const val NAVIDROME_PLAYLIST_PREFIX = "navidrome_playlist:"
    }

    /** Sync user playlists from server. The caller checks that the user is logged in. */
    suspend fun syncPlaylists(): Result<List<NavidromePlaylistEntity>> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing playlists")
                val result = api.getPlaylists()

                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlists")
                    )
                }

                val jsonObjects = result.getOrThrow()
                val playlists = NavidromeResponseParser.parsePlaylists(jsonObjects)

                // CRITICAL BUG FIX: If we have local playlists but the server returns an empty list,
                // do NOT proceed with syncing or deleting. This is likely a transient error or empty response.
                // We only delete stale playlists if we actually got some data back to compare with.
                if (playlists.isEmpty() && jsonObjects.isNotEmpty()) {
                    Timber.w("$TAG: Parser returned empty playlists but JSON response had items. Parsing error suspected. Aborting.")
                    return@withContext Result.failure(Exception("Playlist parsing error"))
                }

                if (playlists.isEmpty()) {
                    val localCount = dao.getPlaylistCount()
                    if (localCount > 0) {
                        Timber.w("$TAG: Server returned empty playlists but we have $localCount locally. Aborting sync to prevent data loss.")
                        return@withContext Result.success(emptyList()) 
                    }
                }

                val entities = playlists.map { playlist ->
                    NavidromePlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        comment = playlist.comment,
                        owner = playlist.owner,
                        coverArtId = playlist.coverArt,
                        songCount = playlist.songCount,
                        duration = playlist.duration,
                        public = playlist.public,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                // Remove stale playlists
                // CRITICAL: Only remove if we successfully fetched at least one playlist OR the fetch was a success but the user has none.
                // Avoid clearing all if it's a transient network error that wasn't caught.
                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                
                // FIXED: If entities is empty, we already handled the protection (localCount > 0) above.
                // However, we must ensure we ONLY delete playlists if the API response was TRULY empty (jsonObjects is empty).
                val stalePlaylists = if (entities.isNotEmpty() || jsonObjects.isEmpty()) {
                    localPlaylists.filter { it.id !in remoteIds }
                } else {
                    emptyList()
                }

                if (stalePlaylists.isNotEmpty()) {
                    Timber.d("$TAG: Removing ${stalePlaylists.size} stale playlists")
                    stalePlaylists.forEach { stale ->
                        dao.deleteSongsByPlaylist(stale.id)
                        dao.deletePlaylist(stale.id)
                        deleteAppPlaylistForNavidromePlaylist(stale.id)
                    }
                }

                // Insert updated playlists
                entities.forEach { dao.insertPlaylist(it) }

                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromNavidrome()
                }

                Timber.d("$TAG: Synced ${entities.size} playlists")
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Syncing songs for playlist $playlistId")

                val result = api.getPlaylist(playlistId)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Failed to get playlist")
                    )
                }

                val res: Pair<JSONObject, List<JSONObject>> = result.getOrThrow()
                val songJsons = res.second
                val songs = NavidromeResponseParser.parseSongs(songJsons)

                // CRITICAL BUG FIX: If the server returns empty songs (e.g. failure to parse or server error)
                // but counts are positive, we do NOT empty our local cache.
                if (songs.isEmpty() && songJsons.isNotEmpty()) {
                    Timber.w("$TAG: FAILED to parse songs for playlist $playlistId even though JSON has data. Aborting.")
                    return@withContext Result.failure(Exception("Parsing error"))
                }

                val entities = songs.map { song: NavidromeSong ->
                    song.toEntity(playlistId)
                }

                if (entities.isNotEmpty()) {
                    Timber.d("$TAG: Playlist $playlistId - Deleting old songs, inserting ${entities.size} new songs")
                    dao.deleteSongsByPlaylist(playlistId)
                    dao.insertSongs(entities)
                    
                    // Update app playlist only if we have data
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForNavidromePlaylist(playlistId, playlistName, entities)
                } else if (songJsons.isEmpty()) {
                    // This is a TRULY empty playlist on the server.
                    // We should ONLY clear it if we actually got a successful empty list response,
                    // not a parse error.
                    Timber.d("$TAG: Playlist $playlistId is empty on server, clearing local cache")
                    dao.deleteSongsByPlaylist(playlistId)
                    val playlistName = dao.getPlaylistById(playlistId)?.name ?: "Playlist"
                    updateAppPlaylistForNavidromePlaylist(playlistId, playlistName, emptyList())
                } else {
                    Timber.w("$TAG: songJsons was not empty (${songJsons.size}) but entities was empty. Parsing issue?")
                }

                // NOTE: Unified library sync is now handled by the caller (e.g., syncAllPlaylistsAndSongs)
                // to avoid multiple redundant syncs. If you need immediate sync for single playlist,
                // call syncUnifiedLibrarySongsFromNavidrome() after this method.

                Timber.d("$TAG: Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync playlist songs")
                Result.failure(e)
            }
        }
    }

    private suspend fun syncUnifiedLibrarySongsFromNavidrome() = unifiedLibrary.syncUnifiedLibrarySongsFromNavidrome()

    private fun toUnifiedSongId(navidromeId: String): Long = unifiedLibrary.toUnifiedSongId(navidromeId)

    // ─── App Playlist Management ───────────────────────────────────────────

    private fun getAppPlaylistIdForNavidrome(navidromePlaylistId: String): String {
        return "$NAVIDROME_PLAYLIST_PREFIX$navidromePlaylistId"
    }

    private suspend fun updateAppPlaylistForNavidromePlaylist(
        navidromePlaylistId: String,
        playlistName: String,
        navidromeEntities: List<NavidromeSongEntity>
    ) {
        try {
            val unifiedSongIds = navidromeEntities.map { entity ->
                toUnifiedSongId(entity.navidromeId).toString()
            }

            val appPlaylistId = getAppPlaylistIdForNavidrome(navidromePlaylistId)
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "NAVIDROME"
                    )
                )
                Timber.d("$TAG: Updated app playlist for Navidrome playlist $navidromePlaylistId")
            } else {
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "NAVIDROME"
                )
                Timber.d("$TAG: Created app playlist for Navidrome playlist $navidromePlaylistId")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to update app playlist for Navidrome playlist $navidromePlaylistId")
        }
    }

    suspend fun deleteAppPlaylistForNavidromePlaylist(navidromePlaylistId: String) {
        try {
            val appPlaylistId = getAppPlaylistIdForNavidrome(navidromePlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("$TAG: Deleted app playlist for Navidrome playlist $navidromePlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to delete app playlist for Navidrome playlist $navidromePlaylistId")
        }
    }
}
