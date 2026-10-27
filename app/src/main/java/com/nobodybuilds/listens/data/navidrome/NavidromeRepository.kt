package com.nobodybuilds.listens.data.navidrome

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.NavidromeDao
import com.nobodybuilds.listens.data.database.NavidromePlaylistEntity
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.navidrome.model.NavidromeCredentials
import com.nobodybuilds.listens.data.network.navidrome.NavidromeApiService
import com.nobodybuilds.listens.data.network.navidrome.NavidromeResponseParser
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.stream.BulkSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Repository for Navidrome/Subsonic music service.
 *
 * Manages authentication, playlist synchronization, and song caching.
 */
@Singleton
class NavidromeRepository @Inject constructor(
    private val api: NavidromeApiService,
    private val dao: NavidromeDao,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val TAG = "NavidromeRepo"
        private const val PREFS_NAME = "navidrome_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_FULL_SYNC = "last_full_sync"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "$TAG: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    private val unifiedLibrary = NavidromeUnifiedLibrary(dao, musicDao)
    private val playlistSync = NavidromePlaylistSync(api, dao, playlistPreferencesRepository, unifiedLibrary)
    private val librarySongs = NavidromeLibrarySongs(api, dao, context)

    init {
        initFromSavedCredentials()
    }

    // ─── Authentication ──────────────────────────────────────────────────

    /**
     * Initialize API from saved credentials.
     */
    private fun initFromSavedCredentials() {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)

        if (!serverUrl.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
            val credentials = NavidromeCredentials(serverUrl, username, password)
            val validationError = credentials.connectionValidationError()
            if (validationError != null) {
                Timber.w("$TAG: Ignoring insecure or invalid saved Navidrome server URL: $validationError")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                return
            }
            api.setCredentials(credentials)
            _isLoggedInFlow.value = true
            Timber.d("$TAG: Restored credentials for $username@${credentials.normalizedServerUrl}")
        }
    }

    /**
     * Check if user is logged in.
     */
    val isLoggedIn: Boolean
        get() = _isLoggedInFlow.value

    /**
     * Get the current server URL.
     */
    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    /**
     * Get the current username.
     */
    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    var lastFullSyncTime: Long
        get() = prefs.getLong(KEY_LAST_FULL_SYNC, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_FULL_SYNC, value) }

    /**
     * Login to Navidrome server with credentials.
     *
     * @param serverUrl The server URL (e.g., "https://music.example.com")
     * @param username The username
     * @param password The password
     * @return Result with username on success, error on failure
     */
    suspend fun login(serverUrl: String, username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Attempting login to $serverUrl as $username")

                val credentials = NavidromeCredentials(serverUrl, username, password)
                val validationError = credentials.connectionValidationError()
                if (validationError != null) {
                    api.clearCredentials()
                    return@withContext Result.failure(IllegalArgumentException(validationError))
                }
                api.setCredentials(credentials)

                // Test connection
                val pingResult = api.ping()
                if (pingResult.isFailure) {
                    api.clearCredentials()
                    return@withContext Result.failure(
                        pingResult.exceptionOrNull() ?: Exception("Connection failed")
                    )
                }

                // Save credentials
                prefs.edit {
                    putString(KEY_SERVER_URL, credentials.normalizedServerUrl)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_PASSWORD, password)
                }

                _isLoggedInFlow.value = true
                Timber.d("$TAG: Login successful for $username@$serverUrl")
                Result.success(username)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Login failed")
                api.clearCredentials()
                _isLoggedInFlow.value = false
                Result.failure(e)
            }
        }
    }

    /**
     * Logout and clear all cached data.
     */
    suspend fun logout() {
        Timber.d("$TAG: Logging out")
        api.clearCredentials()
        prefs.edit { clear() }

        // Delete all Navidrome playlists from database
        val playlistsToDelete = dao.getAllPlaylistsList()
        playlistsToDelete.forEach { playlist ->
            dao.deleteSongsByPlaylist(playlist.id)
            deleteAppPlaylistForNavidromePlaylist(playlist.id)
        }

        musicDao.clearAllNavidromeSongs()
        dao.clearAllPlaylists()
        _isLoggedInFlow.value = false
    }

    // ─── Playlists ────────────────────────────────────────────────────────

    /**
     * Sync user playlists from server.
     */
    suspend fun syncPlaylists(): Result<List<NavidromePlaylistEntity>> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return playlistSync.syncPlaylists()
    }

    /**
     * Sync songs in a specific playlist.
     */
    suspend fun syncPlaylistSongs(playlistId: String): Result<Int> = playlistSync.syncPlaylistSongs(playlistId)

    /**
     * Sync all songs from the server library by fetching all albums.
     */
    suspend fun syncLibrarySongs(
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<Int> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return librarySongs.syncLibrarySongs(onProgress)
    }

    /**
     * Sync all playlists and their songs, plus library songs.
     */
    suspend fun syncAllPlaylistsAndSongs(
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<BulkSyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedSongCount = 0
            var failedPlaylistCount = 0

            onProgress?.invoke(0.05f, context.getString(R.string.dash_status_syncing_library))
            // Sync library songs (all albums)
            val libResult = syncLibrarySongs { progress, message ->
                // Map library sync progress (0-1) to 0.05-0.4 range
                onProgress?.invoke(0.05f + (progress * 0.35f), message)
            }
            libResult.fold(
                onSuccess = { count -> syncedSongCount += count },
                onFailure = { Timber.w(it, "$TAG: Failed syncing library songs") }
            )

            onProgress?.invoke(0.4f, context.getString(R.string.dash_status_fetching_playlists))
            // Sync playlists
            val playlistResult = syncPlaylists().getOrElse {
                // Playlists failed but library songs may have synced
                try {
                    syncUnifiedLibrarySongsFromNavidrome()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to sync unified library after playlist fetch failure")
                }
                return@withContext Result.success(
                    BulkSyncResult(
                        playlistCount = 0,
                        syncedSongCount = syncedSongCount,
                        failedPlaylistCount = 0
                    )
                )
            }

            val totalPlaylists = playlistResult.size
            playlistResult.forEachIndexed { index, playlist ->
                val progressBase = 0.4f
                val progressStep = 0.5f / totalPlaylists.coerceAtLeast(1)
                val currentProgress = progressBase + (index * progressStep)
                
                onProgress?.invoke(
                    currentProgress, 
                    context.getString(R.string.dash_status_syncing_playlist_format, playlist.name)
                )
                
                val songSyncResult = syncPlaylistSongs(playlist.id)
                songSyncResult.fold(
                    onSuccess = { count -> syncedSongCount += count },
                    onFailure = {
                        failedPlaylistCount += 1
                        Timber.w(it, "$TAG: Failed syncing playlist ${playlist.id}")
                    }
                )
            }

            onProgress?.invoke(0.95f, context.getString(R.string.dash_status_updating_local))
            // Sync to unified library once after everything is synced
            try {
                syncUnifiedLibrarySongsFromNavidrome()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to sync unified library")
            }

            onProgress?.invoke(1f, context.getString(R.string.dash_status_sync_complete))

            if (failedPlaylistCount == 0) {
                lastFullSyncTime = System.currentTimeMillis()
            }

            Result.success(
                BulkSyncResult(
                    playlistCount = playlistResult.size,
                    syncedSongCount = syncedSongCount,
                    failedPlaylistCount = failedPlaylistCount
                )
            )
        }
    }

    /**
     * Get all playlists as Flow.
     */
    fun getPlaylists(): Flow<List<NavidromePlaylistEntity>> = dao.getAllPlaylists()

    /**
     * Get songs in a playlist as Flow of Song.
     */
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return dao.getSongsByPlaylist(playlistId).map { entities ->
            entities.map { it.toSong() }
        }
    }

    /**
     * Get all Navidrome songs as Flow.
     */
    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllNavidromeSongs().map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Search ────────────────────────────────────────────────────────────

    /**
     * Search for songs on the server.
     */
    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>> {
        if (!isLoggedIn) {
            return Result.failure(Exception("Not logged in"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = api.searchSongs(query, count = limit)
                if (result.isFailure) {
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Search failed")
                    )
                }

                val jsonObjects = result.getOrThrow()
                val navidromeSongs = NavidromeResponseParser.parseSongs(jsonObjects)
                val songs = navidromeSongs.map { it.toSong() }

                Result.success(songs)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Search failed")
                Result.failure(e)
            }
        }
    }

    /**
     * Search local cached songs.
     */
    fun searchLocalSongs(query: String): Flow<List<Song>> {
        return dao.searchSongs(query).map { entities ->
            entities.map { it.toSong() }
        }
    }

    // ─── Media URLs ────────────────────────────────────────────────────────

    /**
     * Get the streaming URL for a song.
     *
     * @param songId The Navidrome song ID
     * @param maxBitRate Maximum bitrate (0 = no limit)
     * @return The streaming URL
     */
    fun getStreamUrl(songId: String, maxBitRate: Int = 0): String {
        return api.getStreamUrl(songId, maxBitRate)
    }

    /**
     * Get the cover art URL for a song/album/artist.
     *
     * @param coverArtId The cover art ID
     * @param size Desired size in pixels
     * @return The cover art URL
     */
    fun getCoverArtUrl(coverArtId: String?, size: Int = 500): String? {
        if (coverArtId.isNullOrBlank()) return null
        return api.getCoverArtUrl(coverArtId, size)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────────

    /**
     * Get lyrics for a song.
     */
    suspend fun getLyrics(songId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Try OpenSubsonic extension first
                var result = api.getLyricsBySongId(songId)
                if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                    return@withContext result
                }

                // Fallback to standard lyrics API
                val songEntity = dao.getSongByNavidromeId(songId)
                if (songEntity != null) {
                    result = api.getLyrics(songEntity.artist, songEntity.title)
                    if (result.isSuccess && !result.getOrNull().isNullOrBlank()) {
                        return@withContext result
                    }
                }

                Result.failure(Exception("No lyrics found"))
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to get lyrics for song $songId")
                Result.failure(e)
            }
        }
    }

    // ─── Unified Library Sync ──────────────────────────────────────────────

    /**
     * Sync Navidrome songs to the unified music library.
     */
    suspend fun syncUnifiedLibrarySongsFromNavidrome() = unifiedLibrary.syncUnifiedLibrarySongsFromNavidrome()

    // ─── App Playlist Management ───────────────────────────────────────────

    private suspend fun deleteAppPlaylistForNavidromePlaylist(navidromePlaylistId: String) =
        playlistSync.deleteAppPlaylistForNavidromePlaylist(navidromePlaylistId)

    // ─── Playback Reporting ──────────────────────────────────────────────

    suspend fun reportPlayback(
        navidromeId: String,
        positionMs: Long,
        state: String,
        playbackRate: Float = 1.0f,
        ignoreScrobble: Boolean = false
    ): Result<Unit> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        val result = api.reportPlayback(
            mediaId = navidromeId,
            positionMs = positionMs,
            state = state,
            playbackRate = playbackRate,
            ignoreScrobble = ignoreScrobble
        )
        // Fallback to standard scrobble if reportPlayback is not supported.
        // PS: The latest release of Navidrome currently doesn't support the
        // standard OpenSubsonic API (reportPlayback) at the time of writing
        // See: (https://github.com/navidrome/navidrome/pull/5442), so this is required.
        if (result.isFailure && result.exceptionOrNull()?.message?.contains("404") == true) {
            if (state == "playing" || state == "starting") {
                return api.scrobble(id = navidromeId, submission = false)
            }
        }
        return result
    }

    suspend fun scrobble(navidromeId: String, submission: Boolean = true): Result<Unit> {
        if (!isLoggedIn) return Result.failure(Exception("Not logged in"))
        return api.scrobble(id = navidromeId, submission = submission)
    }

    // ─── Delete ────────────────────────────────────────────────────────────

    suspend fun deletePlaylist(playlistId: String) {
        dao.deleteSongsByPlaylist(playlistId)
        dao.deletePlaylist(playlistId)
        deleteAppPlaylistForNavidromePlaylist(playlistId)
        syncUnifiedLibrarySongsFromNavidrome()
    }
}
