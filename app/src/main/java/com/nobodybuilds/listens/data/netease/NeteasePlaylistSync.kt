package com.nobodybuilds.listens.data.netease

import com.nobodybuilds.listens.data.database.NeteaseDao
import com.nobodybuilds.listens.data.database.NeteasePlaylistEntity
import com.nobodybuilds.listens.data.database.NeteaseSongEntity
import com.nobodybuilds.listens.data.network.netease.NeteaseApiService
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** Syncs Netease playlists and their songs, and mirrors each one as an app playlist. */
internal class NeteasePlaylistSync(
    private val api: NeteaseApiService,
    private val dao: NeteaseDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val unifiedLibrary: NeteaseUnifiedLibrary
) {
    private companion object {
        private const val NETEASE_PLAYLIST_PREFIX = "netease_playlist:"
        private const val NETEASE_PLAYLIST_PAGE_SIZE = 50
        private const val NETEASE_SONG_DETAIL_BATCH_SIZE = 500
        private const val NETEASE_MAX_PLAYLIST_PAGES = 200
    }

    /** Syncs the playlists of [userId] (-1 when unknown). The caller checks that the user is logged in. */
    suspend fun syncUserPlaylists(userId: Long): Result<List<NeteasePlaylistEntity>> {
        return withContext(Dispatchers.IO) {
            try {
                val uid = if (userId != -1L) userId else api.getCurrentUserId()
                Timber.d("syncUserPlaylists: fetching playlists for uid=$uid")
                val entitiesById = LinkedHashMap<Long, NeteasePlaylistEntity>()
                var offset = 0
                var page = 0
                var hasMore = true

                while (hasMore) {
                    val raw = api.getUserPlaylists(
                        userId = uid,
                        offset = offset,
                        limit = NETEASE_PLAYLIST_PAGE_SIZE
                    )
                    Timber.d("syncUserPlaylists: page=$page offset=$offset response length=${raw.length}")
                    val root = JSONObject(raw)

                    if (root.optInt("code", -1) != 200) {
                        Timber.e("syncUserPlaylists: API error code=${root.optInt("code")}")
                        return@withContext Result.failure(Exception("API error: code ${root.optInt("code")}"))
                    }

                    val playlistArray = root.optJSONArray("playlist") ?: break
                    val fetchedCount = playlistArray.length()
                    if (fetchedCount == 0) break

                    for (i in 0 until fetchedCount) {
                        val pl = playlistArray.optJSONObject(i) ?: continue
                        val id = pl.optLong("id")
                        if (id <= 0L) continue
                        entitiesById[id] = NeteasePlaylistEntity(
                            id = id,
                            name = pl.optString("name", ""),
                            coverUrl = pl.optString("coverImgUrl", ""),
                            songCount = pl.optInt("trackCount", 0),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }

                    offset += fetchedCount
                    val totalCount = root.optInt("count", -1)
                    val moreFlag = root.optBoolean("more", false)
                    hasMore = when {
                        moreFlag -> true
                        totalCount > 0 -> offset < totalCount
                        else -> fetchedCount >= NETEASE_PLAYLIST_PAGE_SIZE
                    }

                    page += 1
                    if (page >= NETEASE_MAX_PLAYLIST_PAGES) {
                        Timber.w("syncUserPlaylists: reached max page guard ($NETEASE_MAX_PLAYLIST_PAGES), stopping pagination")
                        hasMore = false
                    }
                }

                val entities = entitiesById.values.toList()

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = localPlaylists.filter { it.id !in remoteIds }

                stalePlaylists.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.id)
                    dao.deletePlaylist(stale.id)
                    // Delete corresponding app playlists for removed Netease playlists
                    deleteAppPlaylistForNeteasePlaylist(stale.id)
                }

                entities.forEach { dao.insertPlaylist(it) }
                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromNetease()
                }
                Result.success(entities)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync user playlists")
                Result.failure(e)
            }
        }
    }

    suspend fun syncPlaylistSongs(
        playlistId: Long,
        syncUnifiedLibrary: Boolean
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = api.getPlaylistDetail(playlistId)
                val root = JSONObject(raw)

                if (root.optInt("code", -1) != 200) {
                    return@withContext Result.failure(Exception("API error"))
                }

                val playlist = root.optJSONObject("playlist")
                    ?: return@withContext Result.failure(Exception("No playlist data"))
                val embeddedTracks = playlist.optJSONArray("tracks")
                val trackIds = playlist.optJSONArray("trackIds")
                val playlistName = playlist.optString("name", "")

                val entitiesBySongId = LinkedHashMap<Long, NeteaseSongEntity>()
                val orderedTrackIds = mutableListOf<Long>()

                for (i in 0 until (embeddedTracks?.length() ?: 0)) {
                    val track = embeddedTracks?.optJSONObject(i) ?: continue
                    val entity = parseTrackToEntity(track, playlistId)
                    entitiesBySongId[entity.neteaseId] = entity
                }

                for (i in 0 until (trackIds?.length() ?: 0)) {
                    val id = trackIds?.optJSONObject(i)?.optLong("id") ?: 0L
                    if (id > 0L) {
                        orderedTrackIds.add(id)
                    }
                }

                val existingTrackIds = entitiesBySongId.keys.toSet()
                val missingTrackIds = if (orderedTrackIds.isNotEmpty()) {
                    orderedTrackIds.filterNot(existingTrackIds::contains)
                } else {
                    emptyList()
                }

                if (missingTrackIds.isNotEmpty()) {
                    Timber.d(
                        "syncPlaylistSongs: playlistId=$playlistId needs ${missingTrackIds.size} additional tracks beyond embedded detail"
                    )
                    missingTrackIds.chunked(NETEASE_SONG_DETAIL_BATCH_SIZE).forEach { chunk ->
                        val detailRaw = api.getSongDetails(chunk)
                        val detailRoot = JSONObject(detailRaw)
                        if (detailRoot.optInt("code", -1) != 200) {
                            Timber.w(
                                "syncPlaylistSongs: getSongDetails failed for chunk size=${chunk.size}, code=${detailRoot.optInt("code", -1)}"
                            )
                            return@forEach
                        }
                        val detailSongs = detailRoot.optJSONArray("songs") ?: return@forEach
                        for (i in 0 until detailSongs.length()) {
                            val track = detailSongs.optJSONObject(i) ?: continue
                            val entity = parseTrackToEntity(track, playlistId)
                            entitiesBySongId[entity.neteaseId] = entity
                        }
                    }
                }

                val entities = if (orderedTrackIds.isNotEmpty()) {
                    val ordered = orderedTrackIds.mapNotNull { entitiesBySongId[it] }
                    if (ordered.size < entitiesBySongId.size) {
                        val orderedSet = orderedTrackIds.toSet()
                        ordered + entitiesBySongId.values.filterNot { it.neteaseId in orderedSet }
                    } else {
                        ordered
                    }
                } else {
                    entitiesBySongId.values.toList()
                }

                val expectedTrackCount = playlist.optInt("trackCount", orderedTrackIds.size)
                if (expectedTrackCount > 0 && entities.size < expectedTrackCount) {
                    Timber.w(
                        "syncPlaylistSongs: playlistId=$playlistId expected=$expectedTrackCount synced=${entities.size} (API may still be limiting some tracks)"
                    )
                }

                dao.deleteSongsByPlaylist(playlistId)
                dao.insertSongs(entities)
                
                // Create or update the corresponding app playlist
                updateAppPlaylistForNeteasePlaylist(playlistId, playlistName, entities)
                
                if (syncUnifiedLibrary) {
                    syncUnifiedLibrarySongsFromNetease()
                }

                Timber.d("Synced ${entities.size} songs for playlist $playlistId")
                Result.success(entities.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    private suspend fun syncUnifiedLibrarySongsFromNetease() = unifiedLibrary.syncUnifiedLibrarySongsFromNetease()

    private fun toUnifiedSongId(neteaseId: Long): Long = unifiedLibrary.toUnifiedSongId(neteaseId)

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForNetease(neteasePlaylistId: Long): String {
        return "$NETEASE_PLAYLIST_PREFIX$neteasePlaylistId"
    }

    private suspend fun updateAppPlaylistForNeteasePlaylist(
        neteasePlaylistId: Long,
        playlistName: String,
        neteaseEntities: List<NeteaseSongEntity>
    ) {
        try {
            // Convert Netease song entities to unified song IDs (Long format, stored as String)
            // These must match the IDs generated in syncUnifiedLibrarySongsFromNetease
            val unifiedSongIds = neteaseEntities.map { entity ->
                toUnifiedSongId(entity.neteaseId).toString()
            }

            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            
            // Get all current app playlists
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                // Update the existing playlist
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = playlistName,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "NETEASE" // Mark as NetEase source
                    )
                )
                Timber.d("Updated app playlist for Netease playlist $neteasePlaylistId: $playlistName")
            } else {
                // Create a new playlist with custom ID to prevent duplicates
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,  // Use NetEase prefix ID for matching on next sync
                    source = "NETEASE"         // Mark as NetEase source
                )
                Timber.d("Created new app playlist for Netease playlist $neteasePlaylistId: $playlistName with ID: $appPlaylistId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for Netease playlist $neteasePlaylistId")
        }
    }

    suspend fun deleteAppPlaylistForNeteasePlaylist(neteasePlaylistId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForNetease(neteasePlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for Netease playlist $neteasePlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Netease playlist $neteasePlaylistId")
        }
    }
}
