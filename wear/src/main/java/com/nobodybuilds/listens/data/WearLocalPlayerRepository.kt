package com.nobodybuilds.listens.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.local.LocalSongDao
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.shared.WearHandoffSong
import com.nobodybuilds.listens.shared.WearPlaybackCommand
import com.nobodybuilds.listens.shared.WearThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing ExoPlayer for standalone local playback on the watch.
 * Plays audio files that have been transferred from the phone and stored locally.
 *
 * Uses a lightweight local ExoPlayer plus MediaSession so Bluetooth headset media buttons
 * and other system transport controls route to watch playback correctly.
 */
@Singleton
class WearLocalPlayerRepository @Inject constructor(
    private val application: Application,
    private val localSongDao: LocalSongDao,
    private val youTube: WearYouTubeRepository,
    private val jioSaavn: WearJioSaavnRepository,
    private val streamingNetwork: WearStreamingNetwork,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private val artworkLoader = WearLocalPlayerArtworkLoader(application)
    private val streaming = WearStreamingPlayback(streamingNetwork, youTube, jioSaavn)
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val _localPlayerState = MutableStateFlow(WearLocalPlayerState())
    val localPlayerState: StateFlow<WearLocalPlayerState> = _localPlayerState.asStateFlow()

    private val _isLocalPlaybackActive = MutableStateFlow(false)
    val isLocalPlaybackActive: StateFlow<Boolean> = _isLocalPlaybackActive.asStateFlow()

    private val _localPaletteSeedArgb = MutableStateFlow<Int?>(null)
    val localPaletteSeedArgb: StateFlow<Int?> = _localPaletteSeedArgb.asStateFlow()

    private val _localThemePalette = MutableStateFlow<WearThemePalette?>(null)
    val localThemePalette: StateFlow<WearThemePalette?> = _localThemePalette.asStateFlow()

    private val _localAlbumArt = MutableStateFlow<Bitmap?>(null)
    val localAlbumArt: StateFlow<Bitmap?> = _localAlbumArt.asStateFlow()

    private val _localQueueState = MutableStateFlow(WearLocalQueueState())
    val localQueueState: StateFlow<WearLocalQueueState> = _localQueueState.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentQueueSongIds: List<String> = emptyList()
    private var currentQueueSongsById: Map<String, LocalSongEntity> = emptyMap()
    private var currentQueueItemsById: Map<String, WearQueueSong> = emptyMap()
    private var lastPaletteSongId: String = ""
    private var lastArtworkSongId: String = ""
    private var transientSongIds: Set<String> = emptySet()
    private var transientCleanupPaths: Set<String> = emptySet()

    companion object {
        private const val TAG = "WearLocalPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
        private const val MEDIA_SESSION_ID = "wear-local-playback"
    }

    init {
        scope.launch {
            localSongDao.getAllSongs().collect { songs ->
                if (songs.isNotEmpty()) {
                    val latestSongsById = songs.associateBy { it.songId }
                    if (currentQueueSongsById.isNotEmpty()) {
                        currentQueueSongsById = currentQueueSongsById.mapValues { (songId, song) ->
                            latestSongsById[songId] ?: song
                        }
                    }
                }

                if (!localPlayerState.value.isEmpty) {
                    updateState()
                }
            }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(application)
            .setMediaSourceFactory(DefaultMediaSourceFactory(wearPlayerDataSourceFactory(application, youTube, jioSaavn)))
            .setLoadControl(wearStreamingLoadControl())
            .build().also { player ->
            exoPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            player.setHandleAudioBecomingNoisy(true)
            ensureMediaSession(player)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    if (playbackState == Player.STATE_ENDED) {
                        stopPositionUpdates()
                        streaming.updateNetwork(player)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    streaming.updateNetwork(player)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                    streaming.onItemChanged()
                    streaming.updateNetwork(player)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!streaming.recover(player, error)) streaming.updateNetwork(player)
                }
            })
            Timber.tag(TAG).d("ExoPlayer created")
        }
    }

    private fun ensureMediaSession(player: ExoPlayer) {
        if (mediaSession != null) return

        mediaSession = MediaSession.Builder(application, player)
            .setId(MEDIA_SESSION_ID)
            .build()
    }

    /**
     * Start local playback with the given songs, beginning at [startIndex].
     */
    fun playLocalSongs(
        songs: List<LocalSongEntity>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
    ) {
        scope.launch {
            val playableSongs = songs.filter { song ->
                val file = File(song.localPath)
                file.isFile && file.length() > 0L
            }
            if (playableSongs.isEmpty()) {
                Timber.tag(TAG).w("No playable local files available")
                return@launch
            }

            val queueSongs = playableSongs.map { song ->
                WearQueueSong(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    uri = Uri.fromFile(File(song.localPath)),
                )
            }
            startPlayback(
                queueSongs = queueSongs,
                queueSongIdToLocal = playableSongs.associateBy { it.songId },
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay,
            )
        }
    }

    /**
     * Start local playback from watch MediaStore songs.
     */
    fun playUriSongs(
        songs: List<WearQueueSong>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
    ) {
        scope.launch {
            if (songs.isEmpty()) {
                Timber.tag(TAG).w("No watch library songs available")
                return@launch
            }
            startPlayback(
                queueSongs = songs,
                queueSongIdToLocal = emptyMap(),
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay,
            )
        }
    }

    fun playTemporarySong(
        song: LocalSongEntity,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        cleanupPaths: Set<String> = emptySet(),
    ) {
        scope.launch {
            val file = File(song.localPath)
            if (!file.isFile || file.length() <= 0L) {
                Timber.tag(TAG).w("Temporary playback file missing for songId=%s", song.songId)
                return@launch
            }

            startPlayback(
                queueSongs = listOf(
                    WearQueueSong(
                        songId = song.songId,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        uri = Uri.fromFile(file),
                    )
                ),
                queueSongIdToLocal = mapOf(song.songId to song),
                startIndex = 0,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay,
                transientSongIds = setOf(song.songId),
                transientCleanupPaths = cleanupPaths +
                    setOf(song.localPath) +
                    listOfNotNull(song.artworkPath),
            )
        }
    }

    private suspend fun startPlayback(
        queueSongs: List<WearQueueSong>,
        queueSongIdToLocal: Map<String, LocalSongEntity>,
        startIndex: Int,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        transientSongIds: Set<String> = emptySet(),
        transientCleanupPaths: Set<String> = emptySet(),
    ) {
        if (autoPlay && streaming.isStream(queueSongs.getOrNull(startIndex)?.uri)) streamingNetwork.acquireAndWait()
        withContext(Dispatchers.Main) {
            val player = getOrCreatePlayer()
            if (this@WearLocalPlayerRepository.transientCleanupPaths.isNotEmpty()) {
                player.stop()
            }
            clearTransientPlaybackArtifacts()
            currentQueueSongIds = queueSongs.map { it.songId }
            val latestSongsById = queueSongIdToLocal.keys.mapNotNull { songId ->
                localSongDao.getSongById(songId)
            }.associateBy { it.songId }
            currentQueueSongsById = queueSongIdToLocal.mapValues { (songId, song) ->
                latestSongsById[songId] ?: song
            }
            currentQueueItemsById = queueSongs.associateBy { it.songId }
            this@WearLocalPlayerRepository.transientSongIds = transientSongIds
            this@WearLocalPlayerRepository.transientCleanupPaths = transientCleanupPaths
                .filter { it.isNotBlank() }
                .toSet()
            lastPaletteSongId = ""
            lastArtworkSongId = ""
            _localThemePalette.value = null
            _localPaletteSeedArgb.value = null
            _localAlbumArt.value = null

            val mediaItems = queueSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.songId)
                    .setUri(song.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            val startIndexSafe = startIndex.coerceIn(0, mediaItems.lastIndex)
            player.setMediaItems(mediaItems, startIndexSafe, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = autoPlay
            if (autoPlay) {
                player.play()
            } else {
                player.pause()
            }
            _isLocalPlaybackActive.value = true
            updateQueueState(currentIndex = startIndexSafe)
            updateState()
            Timber.tag(TAG).d(
                "Playing locally: ${queueSongs.getOrNull(startIndexSafe)?.title}, queue=${queueSongs.size}, autoPlay=$autoPlay"
            )
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * What the phone needs to carry on from here: the queue and where the current song is. Null when nothing
     * is playing, or the current song exists only on the watch (`device:` songs from its own storage).
     */
    fun continueOnPhoneCommand(): WearPlaybackCommand? {
        val player = exoPlayer?.takeIf { it.isPlaying } ?: return null
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            handoffSong(player.getMediaItemAt(index).mediaId)?.let { index to it }
        }
        val start = queue.indexOfFirst { it.first == player.currentMediaItemIndex }
        if (start == -1) return null
        return WearPlaybackCommand(
            action = WearPlaybackCommand.CONTINUE_ON_PHONE,
            queueIndex = start,
            positionMs = player.currentPosition,
            handoffQueue = queue.map { it.second },
        )
    }

    private fun handoffSong(songId: String): WearHandoffSong? {
        if (songId.startsWith(WearDeviceMusicRepository.DEVICE_SONG_PREFIX)) return null
        val item = currentQueueItemsById[songId]
        val local = currentQueueSongsById[songId]
        return WearHandoffSong(
            songId = songId,
            title = item?.title ?: local?.title.orEmpty(),
            artist = item?.artist ?: local?.artist.orEmpty(),
            album = item?.album ?: local?.album.orEmpty(),
            artworkUrl = item?.artworkUrl,
        )
    }

    fun next() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                updateState()
            }
        }
    }

    fun cycleRepeat() {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                updateState()
            }
        }
    }

    fun playQueueIndex(index: Int) {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                if (index !in 0 until player.mediaItemCount) return@withContext

                player.seekToDefaultPosition(index)
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.play()
                updateState()
            }
        }
    }

    suspend fun removeSongFromActiveQueue(songId: String) {
        withContext(Dispatchers.Main) {
            val queueIndex = currentQueueSongIds.indexOf(songId)
            if (queueIndex == -1) return@withContext

            val player = exoPlayer
            if (player == null || currentQueueSongIds.size <= 1) {
                release()
                return@withContext
            }

            player.removeMediaItem(queueIndex)
            currentQueueSongIds = currentQueueSongIds.toMutableList().apply {
                removeAt(queueIndex)
            }
            currentQueueSongsById = currentQueueSongsById.toMutableMap().apply {
                remove(songId)
            }
            currentQueueItemsById = currentQueueItemsById.toMutableMap().apply {
                remove(songId)
            }
            if (lastPaletteSongId == songId) lastPaletteSongId = ""
            if (lastArtworkSongId == songId) lastArtworkSongId = ""
            updateQueueState()
            updateState()
        }
    }

    /**
     * Stop local playback and release the player.
     */
    fun release() {
        stopPositionUpdates()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        clearTransientPlaybackArtifacts()
        _isLocalPlaybackActive.value = false
        _localPlayerState.value = WearLocalPlayerState()
        _localThemePalette.value = null
        _localPaletteSeedArgb.value = null
        _localAlbumArt.value = null
        _localQueueState.value = WearLocalQueueState()
        currentQueueSongIds = emptyList()
        currentQueueSongsById = emptyMap()
        currentQueueItemsById = emptyMap()
        lastPaletteSongId = ""
        lastArtworkSongId = ""
        Timber.tag(TAG).d("ExoPlayer released")
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        val currentLocalSong = currentItem?.mediaId?.let(currentQueueSongsById::get)
        _localPlayerState.value = WearLocalPlayerState(
            songId = currentItem?.mediaId ?: "",
            songTitle = currentItem?.mediaMetadata?.title?.toString() ?: "",
            artistName = currentItem?.mediaMetadata?.artist?.toString() ?: "",
            albumName = currentItem?.mediaMetadata?.albumTitle?.toString() ?: "",
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            totalDurationMs = player.duration.coerceAtLeast(0L),
            isFavorite = currentLocalSong?.isFavorite == true,
            canToggleFavorite = currentLocalSong != null && currentItem.mediaId !in transientSongIds,
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        updateQueueState(currentIndex = player.currentMediaItemIndex)
        updatePaletteForSong(currentItem?.mediaId.orEmpty())
        updateArtworkForSong(currentItem?.mediaId.orEmpty())
    }

    private fun clearTransientPlaybackArtifacts() {
        transientCleanupPaths.forEach { path ->
            runCatching { File(path).delete() }
                .onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to delete transient playback artifact: %s", path)
                }
        }
        transientCleanupPaths = emptySet()
        transientSongIds = emptySet()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                // Skip the StateFlow churn when the user can't see the UI: the
                // ExoPlayer keeps tracking position internally, we just don't
                // need to repaint composables. We still wake every second to
                // notice the screen turning back on quickly, but we don't run
                // the (allocating) updateState() pipeline.
                if (WearLifecycleState.isInteractiveNow) {
                    updateState()
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateQueueState(currentIndex: Int? = null) {
        val player = exoPlayer
        val rawCurrentIndex = currentIndex ?: exoPlayer?.currentMediaItemIndex ?: -1
        _localQueueState.value = buildWearLocalQueueState(
            player = player,
            rawCurrentIndex = rawCurrentIndex,
            currentQueueSongIds = currentQueueSongIds,
            currentQueueItemsById = currentQueueItemsById,
        )
    }

    private fun updatePaletteForSong(songId: String) {
        if (songId.isBlank()) {
            lastPaletteSongId = ""
            _localThemePalette.value = null
            _localPaletteSeedArgb.value = null
            return
        }
        if (songId == lastPaletteSongId) return
        lastPaletteSongId = songId

        val queueSong = currentQueueSongsById[songId]
        val cachedThemePalette = queueSong?.themePaletteJson
            ?.takeIf { it.isNotBlank() }
            ?.let { encodedPalette ->
                runCatching { json.decodeFromString<WearThemePalette>(encodedPalette) }
                    .onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to decode persisted Wear palette")
                    }
                    .getOrNull()
            }
        val cachedSeed = queueSong?.paletteSeedArgb
        _localThemePalette.value = cachedThemePalette
        if (cachedThemePalette != null || cachedSeed != null) {
            _localPaletteSeedArgb.value = cachedSeed
            return
        }

        _localThemePalette.value = null
        _localPaletteSeedArgb.value = null
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val extractedSeed = artworkLoader.extractSeedFromLocalSong(queueSong)
                if (extractedSeed != null) {
                    runCatching { localSongDao.updatePaletteSeed(queueSong.songId, extractedSeed) }
                        .onFailure { error ->
                            Timber.tag(TAG).w(error, "Failed to persist local palette seed")
                        }
                }

                withContext(Dispatchers.Main) {
                    if (lastPaletteSongId != queueSong.songId) return@withContext
                    if (extractedSeed != null) {
                        currentQueueSongsById = currentQueueSongsById.toMutableMap().apply {
                            put(queueSong.songId, queueSong.copy(paletteSeedArgb = extractedSeed))
                        }
                    }
                    _localPaletteSeedArgb.value = extractedSeed
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId] ?: return
        scope.launch(Dispatchers.IO) {
            val extractedSeed = queueItem.artworkUrl
                ?.let { url -> artworkLoader.loadRemoteArtwork(url)?.let(artworkLoader::extractSeed) }
                ?: artworkLoader.extractSeedFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastPaletteSongId != queueItem.songId) return@withContext
                _localThemePalette.value = null
                _localPaletteSeedArgb.value = extractedSeed
            }
        }
    }

    private fun updateArtworkForSong(songId: String) {
        if (songId.isBlank()) {
            lastArtworkSongId = ""
            _localAlbumArt.value = null
            return
        }
        if (songId == lastArtworkSongId) return
        lastArtworkSongId = songId

        val queueSong = currentQueueSongsById[songId]
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = artworkLoader.loadLocalAlbumArtBitmap(queueSong)
                withContext(Dispatchers.Main) {
                    if (lastArtworkSongId != queueSong.songId) return@withContext
                    _localAlbumArt.value = bitmap
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId]
        if (queueItem == null) {
            _localAlbumArt.value = null
            return
        }
        scope.launch(Dispatchers.IO) {
            val bitmap = queueItem.artworkUrl?.let(artworkLoader::loadRemoteArtwork)
                ?: artworkLoader.loadArtworkBitmapFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastArtworkSongId != queueItem.songId) return@withContext
                _localAlbumArt.value = bitmap
            }
        }
    }
}
