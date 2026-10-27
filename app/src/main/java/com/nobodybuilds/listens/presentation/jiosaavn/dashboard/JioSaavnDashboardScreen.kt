package com.nobodybuilds.listens.presentation.jiosaavn.dashboard

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.presentation.jiosaavn.auth.JioSaavnLoginActivity
import com.nobodybuilds.listens.presentation.streaming.StreamingDashboardScreen
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel

@Composable
fun JioSaavnDashboardScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: JioSaavnDashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    StreamingDashboardScreen(
        viewModel = viewModel,
        playerViewModel = playerViewModel,
        toSong = JioSaavnUris::toSong,
        searchLabel = "Search or paste a JioSaavn link",
        signInBody = "Open your liked songs and playlists. Feed, search, play, and download work without an account; " +
            "a Pro account also gets 320 kbps.",
        onSignIn = { context.startActivity(Intent(context, JioSaavnLoginActivity::class.java)) },
        onBack = onBack,
    )
}
