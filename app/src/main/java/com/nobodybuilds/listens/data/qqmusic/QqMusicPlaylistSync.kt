package com.nobodybuilds.listens.data.qqmusic

import com.nobodybuilds.listens.data.database.QqMusicDao
import com.nobodybuilds.listens.data.database.QqMusicPlaylistEntity
import com.nobodybuilds.listens.data.database.QqMusicSongEntity
import com.nobodybuilds.listens.data.network.qqmusic.QqMusicApiService
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import com.nobodybuilds.listens.data.qqmusic.QqMusicRepository.PlaylistSyncType

/** Syncs QQ Music playlists and their songs, and mirrors each one as an app playlist. */
internal class QqMusicPlaylistSync(
    private val api: QqMusicApiService,
    private val dao: QqMusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val unifiedLibrary: QqMusicUnifiedLibrary
) {
    private companion object {
        private const val QQ_MUSIC_PLAYLIST_PREFIX = "qqmusic_playlist:"
        private const val QQ_USER_PLAYLIST_PAGE_SIZE = 100
        private const val QQ_PLAYLIST_SONG_PAGE_SIZE = 1000
        private const val QQ_MAX_PLAYLIST_PAGES = 200
    }

    /** The caller checks that the user is logged in. */
    suspend fun syncUserPlaylists(syncType: PlaylistSyncType): Result<List<QqMusicPlaylistEntity>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val entitiesById = LinkedHashMap<Long, QqMusicPlaylistEntity>()
                var start = 0
                var page = 0

                // First, fetch user created playlists
                if (syncType == PlaylistSyncType.CREATED || syncType == PlaylistSyncType.ALL) {
                    Timber.d("syncUserPlaylists: fetching user created playlists")
                    var createdStart = 0
                    var createdPage = 0
                    while (true) {
                        val raw = api.getUserCreatedPlaylists(start = createdStart, size = QQ_USER_PLAYLIST_PAGE_SIZE)
                        Timber.d("syncUserPlaylists: created page=$createdPage start=$createdStart response length=${raw.length}")
                        val root = JSONObject(raw)
                        val code = root.optInt("code", -1)
                        if (code != 0) {
                            Timber.w("getUserCreatedPlaylists API error: code=$code")
                            break
                        }

                        val data = root.optJSONObject("data") ?: break
                        val disslist = data.optJSONArray("disslist") ?: break
                        val fetchedCount = disslist.length()
                        if (fetchedCount == 0) break

                        var newInPage = 0
                        for (i in 0 until fetchedCount) {
                            val pl = disslist.optJSONObject(i) ?: continue
                            val songCount = pl.optInt("song_cnt", 0)
                            if (songCount == 0) continue
                            val id = pl.optLong("tid", 0L)
                            if (id <= 0L) continue
                            val name = pl.optString("diss_name", "")
                            if (name.isBlank()) continue
                            val previous = entitiesById.put(
                                id,
                                QqMusicPlaylistEntity(
                                    id = id,
                                    name = name,
                                    coverUrl = pl.optString("diss_cover", ""),
                                    songCount = songCount,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            )
                            if (previous == null) newInPage += 1
                        }

                        createdStart += fetchedCount
                        val total = data.optInt("total_cnt", -1)
                        val reachedTotal = total > 0 && createdStart >= total
                        val hasLikelyMore = fetchedCount >= QQ_USER_PLAYLIST_PAGE_SIZE

                        if (reachedTotal || !hasLikelyMore) break
                        if (newInPage == 0 && createdPage > 0) {
                            Timber.w("syncUserPlaylists: created playlists pagination returned no new items at page=$createdPage, stopping")
                            break
                        }

                        createdPage += 1
                        if (createdPage >= QQ_MAX_PLAYLIST_PAGES) {
                            Timber.w("syncUserPlaylists: reached max page guard for created playlists, stopping")
                            break
                        }
                    }
                }

                // Then, fetch collected playlists
                if (syncType == PlaylistSyncType.COLLECTED || syncType == PlaylistSyncType.ALL) {
                    Timber.d("syncUserPlaylists: fetching collected playlists")
                    while (true) {
                        val raw = api.getUserPlaylists(start = start, count = QQ_USER_PLAYLIST_PAGE_SIZE)
                        Timber.d("syncUserPlaylists: page=$page start=$start response length=${raw.length}")
                        val root = JSONObject(raw)
                        val code = root.optInt("code", -1)
                        if (code != 0) {
                            throw Exception("QQ Music API Error: code=$code. Check your login.")
                        }

                        val data = root.optJSONObject("data") ?: break
                        val cdlist = data.optJSONArray("cdlist") ?: break
                        val fetchedCount = cdlist.length()
                        if (fetchedCount == 0) break

                        var newInPage = 0
                        for (i in 0 until fetchedCount) {
                            val pl = cdlist.optJSONObject(i) ?: continue
                            val songCount = pl.optInt("songnum", 0)
                            if (songCount == 0) continue
                            val id = pl.optLong("dissid", 0L)
                            if (id <= 0L) continue
                            val name = decodeBase64IfNeeded(pl.optString("dissname", ""))
                            if (name.isBlank()) continue
                            val previous = entitiesById.put(
                                id,
                                QqMusicPlaylistEntity(
                                    id = id,
                                    name = name,
                                    coverUrl = pl.optString("logo", ""),
                                    songCount = songCount,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            )
                            if (previous == null) newInPage += 1
                        }

                        start += fetchedCount

                        val totalCandidates = listOf(
                            data.optInt("total", -1),
                            data.optInt("dissnum", -1),
                            data.optInt("totaldissnum", -1),
                            data.optInt("cdnum", -1),
                            data.optInt("dirnum", -1)
                        )
                        val total = totalCandidates.firstOrNull { it > 0 } ?: -1
                        val reachedTotal = total > 0 && start >= total
                        val hasLikelyMore = fetchedCount >= QQ_USER_PLAYLIST_PAGE_SIZE

                        if (reachedTotal || !hasLikelyMore) break
                        if (newInPage == 0 && page > 0) {
                            Timber.w("syncUserPlaylists: pagination returned no new playlists at page=$page, stopping")
                            break
                        }

                        page += 1
                        if (page >= QQ_MAX_PLAYLIST_PAGES) {
                            Timber.w("syncUserPlaylists: reached max page guard ($QQ_MAX_PLAYLIST_PAGES), stopping pagination")
                            break
                        }
                    }
                }

                val entities = entitiesById.values.toList()

                val localPlaylists = dao.getAllPlaylistsList()
                val remoteIds = entities.map { it.id }.toSet()
                val stalePlaylists = localPlaylists.filter { it.id !in remoteIds }

                stalePlaylists.forEach { stale ->
                    dao.deleteSongsByPlaylist(stale.id)
                    dao.deletePlaylist(stale.id)
                    deleteAppPlaylistForQqMusicPlaylist(stale.id)
                }

                entities.forEach { dao.insertPlaylist(it) }
                
                if (stalePlaylists.isNotEmpty()) {
                    syncUnifiedLibrarySongsFromQqMusic()
                }
                
                entities
            }
        }
    }

    suspend fun syncPlaylistSongs(playlistId: Long): Result<Int> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val entitiesById = LinkedHashMap<String, QqMusicSongEntity>()
                var songBegin = 0
                var page = 0
                var expectedSongCount = -1

                while (true) {
                    val raw = api.getPlaylistDetail(
                        playlistId = playlistId,
                        songBegin = songBegin,
                        songNum = QQ_PLAYLIST_SONG_PAGE_SIZE
                    )
                    val root = JSONObject(raw)

                    val code = root.optInt("code", -1)
                    if (code != 0) {
                        throw Exception("API error code=$code")
                    }

                    val cdlist = root.optJSONArray("cdlist") ?: throw Exception("No cdlist")
                    val firstCd = cdlist.optJSONObject(0) ?: throw Exception("Empty cdlist")
                    val songlist = firstCd.optJSONArray("songlist") ?: break
                    val fetchedCount = songlist.length()
                    if (fetchedCount == 0) break

                    if (expectedSongCount < 0) {
                        expectedSongCount = firstCd.optInt("songnum", firstCd.optInt("total_song_num", -1))
                    }

                    var newInPage = 0
                    for (i in 0 until fetchedCount) {
                        val track = songlist.optJSONObject(i) ?: continue
                        val entity = parseTrackToEntity(track, playlistId)
                        if (entity.songMid.isBlank()) continue
                        val previous = entitiesById.put(entity.id, entity)
                        if (previous == null) newInPage += 1
                    }

                    songBegin += fetchedCount
                    val reachedExpected = expectedSongCount > 0 && songBegin >= expectedSongCount
                    val hasLikelyMore = fetchedCount >= QQ_PLAYLIST_SONG_PAGE_SIZE

                    if (reachedExpected || !hasLikelyMore) break
                    if (newInPage == 0) {
                        Timber.w("syncPlaylistSongs: pagination returned no new songs for playlistId=$playlistId at page=$page, stopping")
                        break
                    }

                    page += 1
                    if (page >= QQ_MAX_PLAYLIST_PAGES) {
                        Timber.w("syncPlaylistSongs: reached max page guard ($QQ_MAX_PLAYLIST_PAGES) for playlistId=$playlistId")
                        break
                    }
                }

                val entities = entitiesById.values.toList()
                if (expectedSongCount > 0 && entities.size < expectedSongCount) {
                    Timber.w(
                        "syncPlaylistSongs: playlistId=$playlistId expected=$expectedSongCount synced=${entities.size} (API may still be limiting)"
                    )
                }

                dao.deleteSongsByPlaylist(playlistId)
                dao.insertSongs(entities)

                // Update app playlist
                val playlistName = entities.firstOrNull()?.let { "QQ Music Playlist" } ?: "Playlist $playlistId"
                // Ideally we should get the actual name from the list, but for now we search
                val name = dao.getAllPlaylistsList().find { it.id == playlistId }?.name ?: playlistName
                updateAppPlaylistForQqMusicPlaylist(playlistId, name, entities)

                syncUnifiedLibrarySongsFromQqMusic()

                Timber.d("Synced ${entities.size} songs for QQ Music playlist $playlistId")
                entities.size
            }
        }
    }

    private suspend fun syncUnifiedLibrarySongsFromQqMusic() = unifiedLibrary.syncUnifiedLibrarySongsFromQqMusic()

    private fun toUnifiedSongId(mid: String): Long = unifiedLibrary.toUnifiedSongId(mid)

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForQqMusic(qqPlaylistId: Long): String {
        return "$QQ_MUSIC_PLAYLIST_PREFIX$qqPlaylistId"
    }

    private suspend fun updateAppPlaylistForQqMusicPlaylist(
        qqPlaylistId: Long,
        playlistName: String,
        qqEntities: List<QqMusicSongEntity>
    ) {
        try {
            val unifiedSongIds = qqEntities.map { entity ->
                toUnifiedSongId(entity.songMid).toString()
            }

            val appPlaylistId = getAppPlaylistIdForQqMusic(qqPlaylistId)
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
                        source = "QQMUSIC"
                    )
                )
                Timber.d("Updated app playlist for QQ Music playlist $qqPlaylistId: $playlistName")
            } else {
                playlistPreferencesRepository.createPlaylist(
                    name = playlistName,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "QQMUSIC"
                )
                Timber.d("Created new app playlist for QQ Music playlist $qqPlaylistId: $playlistName")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for QQ Music playlist $qqPlaylistId")
        }
    }

    suspend fun deleteAppPlaylistForQqMusicPlaylist(qqPlaylistId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForQqMusic(qqPlaylistId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for QQ Music playlist $qqPlaylistId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for QQ Music playlist $qqPlaylistId")
        }
    }
}
