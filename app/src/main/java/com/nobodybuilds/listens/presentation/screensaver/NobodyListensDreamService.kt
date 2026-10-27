package com.nobodybuilds.listens.presentation.screensaver

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nobodybuilds.listens.data.screensaver.ScreensaverPreferences
import com.nobodybuilds.listens.data.screensaver.ScreensaverWeatherRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The same screensaver as the system Screen saver (Settings › Display › Screen saver › Nobody Listens).
 * DreamService is not a ComponentActivity, so it provides its own Lifecycle / SavedState /
 * ViewModelStore owners and sets them on the decor view before Compose attaches.
 */
@AndroidEntryPoint
class NobodyListensDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    @Inject lateinit var preferences: ScreensaverPreferences
    @Inject lateinit var weatherRepository: ScreensaverWeatherRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var session: ScreensaverSession? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = false

        val s = ScreensaverSession(applicationContext, preferences, weatherRepository, lifecycleScope)
        session = s

        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NobodyListensDreamService)
            setViewTreeSavedStateRegistryOwner(this@NobodyListensDreamService)
            setViewTreeViewModelStoreOwner(this@NobodyListensDreamService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ScreensaverRoot(session = s, autoExit = false, onExit = { finish() }, handleBack = false)
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        session?.start()
    }

    override fun onDreamingStopped() {
        session?.stop()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        session?.stop()
        session = null
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        store.clear()
        super.onDestroy()
    }
}
