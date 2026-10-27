package com.nobodybuilds.listens.presentation.screensaver

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nobodybuilds.listens.data.screensaver.ScreensaverPreferences
import com.nobodybuilds.listens.data.screensaver.ScreensaverWeatherRepository
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import com.nobodybuilds.listens.presentation.haptics.LocalNobodyListensHaptics
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen, landscape, OLED-friendly standby screen: clock, date, weather and now playing.
 * Keeps the screen on only while charging. Closing it returns to the app.
 */
@AndroidEntryPoint
class ScreensaverActivity : ComponentActivity() {

    @Inject lateinit var preferences: ScreensaverPreferences
    @Inject lateinit var weatherRepository: ScreensaverWeatherRepository

    private lateinit var session: ScreensaverSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        session = ScreensaverSession(applicationContext, preferences, weatherRepository, lifecycleScope)
        ScreensaverState.active = true

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.charging.collect { charging ->
                    if (charging) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            CompositionLocalProvider(LocalNobodyListensHaptics provides NobodyListensHaptics.get()) {
                ScreensaverRoot(session = session, autoExit = true, onExit = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.start()
    }

    override fun onStop() {
        session.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        ScreensaverState.active = false
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, ScreensaverActivity::class.java)
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}

/** Whether the screensaver activity is currently open (so the app does not offer it again). */
object ScreensaverState {
    @Volatile var active: Boolean = false
}
