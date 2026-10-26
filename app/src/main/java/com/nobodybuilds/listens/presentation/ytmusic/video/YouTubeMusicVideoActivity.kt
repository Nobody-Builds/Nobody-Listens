package com.nobodybuilds.listens.presentation.ytmusic.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Rational
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicFormats
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.ui.theme.NobodyListensTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import javax.inject.Inject

/**
 * Full-screen video for a YouTube track: landscape, system bars hidden, screen kept on.
 * The player's fullscreen button switches back to portrait. Back or Home while playing
 * shrinks it into a picture-in-picture mini player.
 *
 * When the song is already playing in the music player, the video picks up at the same
 * spot, and closing the video moves the song to where the video stopped.
 */
@AndroidEntryPoint
class YouTubeMusicVideoActivity : ComponentActivity() {

    @Inject lateinit var repository: YouTubeMusicRepository

    private var player: ExoPlayer? = null
    private val loading = mutableStateOf(true)
    private val error = mutableStateOf<String?>(null)
    private val controlsVisible = mutableStateOf(true)
    private val qualities = mutableStateOf<List<Int>>(emptyList())
    /** Picked video height, or null for Auto (adaptive). */
    private val selectedQuality = mutableStateOf<Int?>(null)
    private val inPictureInPicture = mutableStateOf(false)
    private var videoAspect = Rational(16, 9)
    private var songId = ""
    private var musicController: MediaController? = null

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val exoPlayer = player ?: return
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf(YouTubeMusicFormats::isValidVideoId)
        if (videoId == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        songId = YouTubeMusicUris.songId(videoId).toString()
        if (savedInstanceState == null) setFullScreen(true)
        hideSystemBars()

        // Taking audio focus pauses the music player while the video plays.
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
        player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                qualities.value = videoHeights(tracks)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePictureInPicture()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = Rational(videoSize.width, videoSize.height)
                    updatePictureInPicture()
                }
            }
        })
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_TOGGLE_PLAYBACK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onBackPressedDispatcher.addCallback(this) { minimizeOrClose() }

        setContent {
            NobodyListensTheme {
                VideoScreen(
                    player = exoPlayer,
                    title = title,
                    loading = loading.value,
                    error = error.value,
                    controlsVisible = controlsVisible.value,
                    isFullScreen = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                    onControlsVisibilityChange = { controlsVisible.value = it },
                    onFullScreenChange = ::setFullScreen,
                    qualities = qualities.value,
                    selectedQuality = selectedQuality.value,
                    onQualitySelected = ::selectQuality,
                    inPictureInPicture = inPictureInPicture.value,
                    onBack = ::minimizeOrClose,
                )
            }
        }

        lifecycleScope.launch {
            val controller = async { connectMusicController() }
            runCatching { repository.resolveVideo(videoId) }
                .onSuccess { video ->
                    // Same song already playing as audio: continue from there and pause it.
                    val startAt = controller.await()
                        ?.takeIf { it.currentMediaItem?.mediaId == songId }
                        ?.let { music ->
                            music.pause()
                            music.currentPosition
                        } ?: 0L
                    // googlevideo checks the User-Agent of the client that asked for the stream.
                    val dataSource = DefaultHttpDataSource.Factory().setUserAgent(video.userAgent)
                    exoPlayer.setMediaSource(
                        HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(video.url)),
                        startAt,
                    )
                    exoPlayer.prepare()
                    exoPlayer.play()
                    loading.value = false
                }
                .onFailure { failure ->
                    loading.value = false
                    error.value = failure.message ?: "This video is not available"
                }
        }
    }

    // singleTask: watching another video while the mini player is open reuses this screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_VIDEO_ID) == getIntent().getStringExtra(EXTRA_VIDEO_ID)) return
        setIntent(intent)
        recreate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and the notification shade bring the bars back; hide them again.
        if (hasFocus) hideSystemBars()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture.value = isInPictureInPictureMode
        // The mini player was closed: stop instead of lingering in the background.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        if (player != null) unregisterReceiver(pipActionReceiver)
        val position = player?.currentPosition
        // Hand the spot back so the song continues where the video stopped.
        musicController?.let { music ->
            if (isFinishing && position != null && music.currentMediaItem?.mediaId == songId) music.seekTo(position)
            music.release()
        }
        musicController = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** Back shrinks a playing video into the mini player, and closes a paused one. */
    private fun minimizeOrClose() {
        if (player?.isPlaying == true && !isInPictureInPictureMode) {
            enterPictureInPictureMode(pictureInPictureParams())
        } else {
            finish()
        }
    }

    private fun updatePictureInPicture() {
        runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    private fun pictureInPictureParams(): PictureInPictureParams {
        val playing = player?.isPlaying == true
        val label = if (playing) "Pause" else "Play"
        val toggle = RemoteAction(
            android.graphics.drawable.Icon.createWithResource(
                this,
                if (playing) com.nobodybuilds.listens.R.drawable.rounded_pause_24 else com.nobodybuilds.listens.R.drawable.rounded_play_arrow_24,
            ),
            label,
            label,
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_TOGGLE_PLAYBACK).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // PiP accepts aspect ratios between 1:2.39 and 2.39:1.
        val aspect = videoAspect.toFloat().coerceIn(1f / 2.39f, 2.39f)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 1000).toInt(), 1000))
            .setActions(listOf(toggle))
            .setAutoEnterEnabled(playing)
            .setSeamlessResizeEnabled(true)
            .build()
    }

    /** The app's music player, to carry a song over to video and back. Null if it does not answer quickly. */
    private suspend fun connectMusicController(): MediaController? = withTimeoutOrNull(1_500L) {
        suspendCancellableCoroutine { continuation ->
            val context = this@YouTubeMusicVideoActivity
            val token = SessionToken(context, ComponentName(context, MusicService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                if (continuation.isActive) {
                    musicController = controller
                    continuation.resume(controller)
                } else {
                    controller?.release()
                }
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
    }

    private fun videoHeights(tracks: Tracks): List<Int> =
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it).height } }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

    /** Pins one HLS variant by height, or returns to adaptive quality for null. */
    private fun selectQuality(height: Int?) {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (height != null) {
            val group = exoPlayer.currentTracks.groups.firstOrNull { group ->
                group.type == C.TRACK_TYPE_VIDEO &&
                    (0 until group.length).any { group.getTrackFormat(it).height == height }
            } ?: return
            val index = (0 until group.length).first { group.getTrackFormat(it).height == height }
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        exoPlayer.trackSelectionParameters = builder.build()
        selectedQuality.value = height
    }

    private fun setFullScreen(fullScreen: Boolean) {
        requestedOrientation = if (fullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun hideSystemBars() {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val ACTION_TOGGLE_PLAYBACK = "com.nobodybuilds.listens.ytmusic.VIDEO_TOGGLE_PLAYBACK"

        fun intent(context: Context, videoId: String, title: String): Intent =
            Intent(context, YouTubeMusicVideoActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_TITLE, title)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoScreen(
    player: ExoPlayer,
    title: String,
    loading: Boolean,
    error: String?,
    controlsVisible: Boolean,
    isFullScreen: Boolean,
    onControlsVisibilityChange: (Boolean) -> Unit,
    onFullScreenChange: (Boolean) -> Unit,
    qualities: List<Int>,
    selectedQuality: Int?,
    onQualitySelected: (Int?) -> Unit,
    inPictureInPicture: Boolean,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setFullscreenButtonState(isFullScreen)
                    setFullscreenButtonClickListener { fullScreen -> onFullScreenChange(fullScreen) }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            onControlsVisibilityChange(visibility == View.VISIBLE)
                        }
                    )
                }
            },
            // The mini player shows only the picture; its play/pause lives on the PiP window.
            update = { view ->
                view.useController = !inPictureInPicture
                if (inPictureInPicture) view.hideController()
            },
            modifier = Modifier.fillMaxSize(),
        )
        // The title bar shows and hides with the player controls, like the system players.
        AnimatedVisibility(
            visible = !inPictureInPicture && (controlsVisible || loading || error != null),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (qualities.isNotEmpty()) {
                    QualityMenu(qualities, selectedQuality, onQualitySelected)
                }
            }
        }
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.tertiary,
            )
            error != null -> Text(
                error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = Color.White,
                fontFamily = GoogleSansRounded,
            )
        }
    }
}

@Composable
private fun QualityMenu(
    qualities: List<Int>,
    selected: Int?,
    onSelected: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Settings, contentDescription = "Video quality", tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<Int?>(null) + qualities).forEach { height ->
                DropdownMenuItem(
                    text = { Text(height?.let { "${it}p" } ?: "Auto", fontFamily = GoogleSansRounded) },
                    trailingIcon = {
                        if (height == selected) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onSelected(height)
                    },
                )
            }
        }
    }
}
