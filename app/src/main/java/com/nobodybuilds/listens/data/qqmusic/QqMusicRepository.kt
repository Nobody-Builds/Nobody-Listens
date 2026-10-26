package com.nobodybuilds.listens.data.qqmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.QqMusicDao
import com.nobodybuilds.listens.data.database.QqMusicPlaylistEntity
import com.nobodybuilds.listens.data.network.qqmusic.QqMusicApiService
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.stream.BulkSyncResult
import com.nobodybuilds.listens.data.stream.CloudMusicUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QqMusicRepository @Inject constructor(
    private val api: QqMusicApiService,
    private val dao: QqMusicDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) {

    data class BulkSyncResult(
        val playlistCount: Int,
        val syncedSongCount: Int,
        val failedPlaylistCount: Int
    )

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "qqmusic_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "QqMusicRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("qqmusic_prefs_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()
    private val inFlightSongUrlRequests = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Result<String>>>()
    private val lastSongUrlAttemptAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val songUrlRequestCooldownMs = 1500L
    private val qqSongUrlRequestMutex = Mutex()
    @Volatile
    private var lastGlobalSongUrlRequestAtMs = 0L
    private val globalSongUrlRequestIntervalMs = 1100L

    private val unifiedLibrary = QqMusicUnifiedLibrary(dao, musicDao)
    private val playlistSync = QqMusicPlaylistSync(api, dao, playlistPreferencesRepository, unifiedLibrary)

    init {
        initFromSavedCookies()
        _isLoggedInFlow.value = api.hasLogin()
    }

    val isLoggedIn: Boolean
        get() = api.hasLogin()

    val userNickname: String?
        get() = prefs.getString("qqmusic_nickname", null)

    val userAvatarUrl: String?
        get() = prefs.getString("qqmusic_avatar_url", null)

    fun getPlaylists(): Flow<List<QqMusicPlaylistEntity>> = dao.getAllPlaylists()

    fun initFromSavedCookies() {
        val cookieJson = prefs.getString("qqmusic_cookies", null) ?: return
        runCatching {
            val map = jsonToMap(cookieJson)
            if (map.isNotEmpty()) {
                api.setPersistedCookies(map)
            }
        }.onFailure {
            Timber.w(it, "Failed to restore QQ Music cookies")
        }
    }

    suspend fun loginWithCookies(cookieJson: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookies = jsonToMap(cookieJson)
                if (cookies.isEmpty()) {
                    throw IllegalStateException("No cookies captured")
                }
                prefs.edit().putString("qqmusic_cookies", cookieJson).apply()
                api.setPersistedCookies(cookies)
                if (!api.hasLogin()) {
                    throw IllegalStateException("QQ Music login cookie not detected")
                }

                // Fetch user profile to get real nickname
                var nickname = "QQ Music User"
                try {
                    // Try to get nickname from created playlists API
                    val createdPlaylistsRaw = api.getUserCreatedPlaylists(start = 0, size = 1)
                    Timber.d("QQ Music getUserCreatedPlaylists response: ${createdPlaylistsRaw.take(500)}")

                    if (createdPlaylistsRaw.isNotBlank()) {
                        val json = JSONObject(createdPlaylistsRaw)
                        val code = json.optInt("code", -1)
                        if (code == 0) {
                            val data = json.optJSONObject("data")
                            // The nickname is in "hostname" field
                            val hostname = data?.optString("hostname")
                            if (!hostname.isNullOrBlank()) {
                                nickname = hostname
                                Timber.d("QQ Music login: got nickname=$nickname")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch QQ Music user info, using default nickname")
                }

                val avatarUrl = api.getUserAvatarUrl()

                prefs.edit()
                    .putString("qqmusic_nickname", nickname)
                    .putString("qqmusic_avatar_url", avatarUrl)
                    .apply()

                _isLoggedInFlow.value = true
                nickname
            }
        }
    }

    suspend fun getSongUrl(songMid: String): Result<String> {
        val now = System.currentTimeMillis()
        val lastAttempt = lastSongUrlAttemptAtMs[songMid]
        if (lastAttempt != null && now - lastAttempt < songUrlRequestCooldownMs) {
            Timber.d("Skip QQ song URL retry due to cooldown: songMid=$songMid")
            return Result.failure(IllegalStateException("QQ song URL request throttled"))
        }
        lastSongUrlAttemptAtMs[songMid] = now

        inFlightSongUrlRequests[songMid]?.let {
            return it.await()
        }

        val requestDeferred = CompletableDeferred<Result<String>>()
        val existing = inFlightSongUrlRequests.putIfAbsent(songMid, requestDeferred)
        if (existing != null) {
            return existing.await()
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                // Phase 1: Request without filename to get default M4A URL and discover media_mid.
                val defaultPurl = requestPurl(songMid)

                if (defaultPurl.isBlank()) {
                    throw IllegalStateException("No playable URL for songMid=$songMid (empty purl)")
                }

                val defaultUrl = if (defaultPurl.startsWith("http")) defaultPurl
                    else "https://ws.stream.qqmusic.qq.com/$defaultPurl"

                // Extract media_mid from purl (format: "C400{mediaMid}.m4a?vkey=...")
                val mediaMid = defaultPurl.substringBefore("?").drop(4).substringBefore(".")
                if (mediaMid.isNotBlank()) {
                    // Phase 2: Try MP3 320kbps with discovered media_mid.
                    val mp3Filename = "M500${mediaMid}.mp3"
                    val mp3Purl = requestPurl(songMid, filename = mp3Filename)
                    if (mp3Purl.isNotBlank()) {
                        Timber.d("Resolved QQ Music MP3 URL for songMid=$songMid")
                        return@runCatching if (mp3Purl.startsWith("http")) mp3Purl
                            else "https://ws.stream.qqmusic.qq.com/$mp3Purl"
                    }
                    Timber.d("MP3 unavailable for songMid=$songMid, falling back to M4A")
                }

                // Fallback: use the default M4A URL.
                Timber.d("Resolved QQ Music M4A URL for songMid=$songMid")
                defaultUrl
            }
        }

        requestDeferred.complete(result)
        inFlightSongUrlRequests.remove(songMid, requestDeferred)
        return result
    }

    /**
     * Make a single vkey request and return the purl string (empty if unavailable).
     * Respects the global rate-limit mutex.
     */
    private suspend fun requestPurl(songMid: String, filename: String? = null): String {
        qqSongUrlRequestMutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = globalSongUrlRequestIntervalMs - (now - lastGlobalSongUrlRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastGlobalSongUrlRequestAtMs = System.currentTimeMillis()
        }

        val raw = api.getSongDownloadUrl(songMid, filename = filename)
        val root = JSONObject(raw)
        return root
            .optJSONObject("req_0")
            ?.optJSONObject("data")
            ?.optJSONArray("midurlinfo")
            ?.optJSONObject(0)
            ?.optString("purl", "")
            .orEmpty()
    }

    suspend fun logout() {
        api.logout()
        prefs.edit().clear().apply()
        
        // Delete all QQ Music playlists from the database
        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            dao.deletePlaylist(playlist.id)
            deleteAppPlaylistForQqMusicPlaylist(playlist.id)
        }
        
        musicDao.clearAllQqMusicSongs()
        _isLoggedInFlow.value = false
    }

    suspend fun deletePlaylistById(playlistId: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteSongsByPlaylist(playlistId)
            dao.deletePlaylist(playlistId)
            deleteAppPlaylistForQqMusicPlaylist(playlistId)
            syncUnifiedLibrarySongsFromQqMusic()
        }
    }

    // ─── Content Sync ──────────────────────────────────────────────────

    enum class PlaylistSyncType {
        CREATED,    // 我创建的歌单
        COLLECTED,  // 我收藏的歌单
        ALL         // 全部
    }

    suspend fun syncUserPlaylists(syncType: PlaylistSyncType = PlaylistSyncType.ALL): Result<List<QqMusicPlaylistEntity>> {
        Timber.d("syncUserPlaylists called, isLoggedIn=$isLoggedIn")
        if (!isLoggedIn) {
            Timber.w("syncUserPlaylists: Not logged in, aborting")
            return Result.failure(Exception("Not logged in"))
        }
        return playlistSync.syncUserPlaylists(syncType)
    }

    suspend fun syncPlaylistSongs(playlistId: Long): Result<Int> = playlistSync.syncPlaylistSongs(playlistId)

    suspend fun syncAllPlaylistsAndSongs(syncType: PlaylistSyncType = PlaylistSyncType.ALL): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val playlistResult = syncUserPlaylists(syncType).getOrElse { return@runCatching BulkSyncResult(0, 0, 0) }
                if (playlistResult.isEmpty()) {
                    return@runCatching BulkSyncResult(0, 0, 0)
                }

                var syncedSongCount = 0
                var failedPlaylistCount = 0

                playlistResult.forEach { playlist ->
                    val songSyncResult = syncPlaylistSongs(playlist.id)
                    songSyncResult.fold(
                        onSuccess = { count -> syncedSongCount += count },
                        onFailure = {
                            failedPlaylistCount += 1
                            Timber.w(it, "Failed syncing QQ playlist ${playlist.id}")
                        }
                    )
                }

                syncUnifiedLibrarySongsFromQqMusic()

                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            }
        }
    }

    private fun jsonToMap(json: String): Map<String, String> =
        CloudMusicUtils.jsonToMap(json)

    // ─── Unified Library Sync ──────────────────────────────────────────

    private suspend fun syncUnifiedLibrarySongsFromQqMusic() = unifiedLibrary.syncUnifiedLibrarySongsFromQqMusic()

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun deleteAppPlaylistForQqMusicPlaylist(qqPlaylistId: Long) =
        playlistSync.deleteAppPlaylistForQqMusicPlaylist(qqPlaylistId)
}
