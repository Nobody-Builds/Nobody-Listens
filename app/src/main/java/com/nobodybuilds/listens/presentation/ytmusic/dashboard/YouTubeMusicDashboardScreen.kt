package com.nobodybuilds.listens.presentation.ytmusic.dashboard

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.presentation.streaming.StreamingDashboardScreen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.presentation.ytmusic.auth.YouTubeMusicLoginActivity
import com.nobodybuilds.listens.presentation.ytmusic.video.YouTubeMusicVideoActivity

@Composable
fun YouTubeMusicDashboardScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeMusicDashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    StreamingDashboardScreen(
        viewModel = viewModel,
        playerViewModel = playerViewModel,
        toSong = YouTubeMusicUris::toSong,
        searchLabel = "Search or paste a YouTube link",
        signInBody = "Open your library and playlists. Feed, search, play, and download work without an account.",
        onSignIn = { context.startActivity(Intent(context, YouTubeMusicLoginActivity::class.java)) },
        onBack = onBack,
        onWatch = { track -> context.startActivity(YouTubeMusicVideoActivity.intent(context, track.videoId, track.title)) },
    )
}
