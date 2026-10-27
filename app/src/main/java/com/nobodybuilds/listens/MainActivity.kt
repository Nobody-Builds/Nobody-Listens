package com.nobodybuilds.listens

import com.nobodybuilds.listens.presentation.navigation.navigateSafely
// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.CallSuper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.nobodybuilds.listens.data.preferences.AppThemeMode
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.data.worker.SyncManager
import com.nobodybuilds.listens.presentation.navigation.Screen
import com.nobodybuilds.listens.presentation.screens.SetupScreen
import com.nobodybuilds.listens.presentation.viewmodel.MainViewModel
import com.nobodybuilds.listens.presentation.viewmodel.PlayerViewModel
import com.nobodybuilds.listens.ui.theme.NobodyListensTheme
import com.nobodybuilds.listens.utils.AppLocaleManager
import com.nobodybuilds.listens.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.annotation.concurrent.Immutable

@Immutable
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var nobodyListensHapticsEngine: com.nobodybuilds.listens.presentation.haptics.NobodyListensHapticsEngine
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)
    // Benchmark / emulator smoke test only: route to open after launch (extra "smoke_route").
    private val _pendingSmokeRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    // Test-only intent extras (is_benchmark, smoke_route) are honoured only in benchmark / smoke-test builds.
    private val testHooksAllowed: Boolean
        get() = BuildConfig.BUILD_TYPE == "benchmark" || BuildConfig.SMOKE_TEST

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // MD3 Optimization: Release Splash Screen immediately to render UI skeleton.
        // Data loading is handled via optimistic UI and smooth transitions.
        splashScreen.setKeepOnScreenCondition { false }

        // LEER SEÑAL DE BENCHMARK
        val isBenchmarkMode = testHooksAllowed && intent.getBooleanExtra("is_benchmark", false)
        val shouldBenchmarkRebuildDatabase =
            isBenchmarkMode && intent.getBooleanExtra("benchmark_rebuild_database", false)
        Log.i(
            "NobodyListensBenchmark",
            "onCreate benchmark=$isBenchmarkMode rebuildDatabase=$shouldBenchmarkRebuildDatabase"
        )
        if (shouldBenchmarkRebuildDatabase) {
            lifecycleScope.launch {
                userPreferencesRepository.setInitialSetupDone(true)
                Log.i("NobodyListensBenchmark", "Enqueueing benchmark database rebuild")
                syncManager.rebuildDatabase()
                delay(1_500L)
                playerViewModel.prepareBenchmarkPlayerFromLibrary()
            }
        }

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val appColorStyle by themePreferencesRepository.appColorStyleFlow.collectAsStateWithLifecycle(initialValue = com.nobodybuilds.listens.data.preferences.AppColorStyle.DAYLIGHT)
            val appPitchBlack by themePreferencesRepository.appPitchBlackFlow.collectAsStateWithLifecycle(initialValue = false)
            // The content fades in over the window background, so match it to stop a navy flash.
            LaunchedEffect(useDarkTheme, appPitchBlack) {
                if (useDarkTheme && appPitchBlack) {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                } else {
                    window.setBackgroundDrawableResource(R.color.nb_window_background)
                }
            }
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            // Permissions Logic
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            // Determine if we need to show Setup based on completion OR missing permissions
            val permissionsValid = permissionState.allPermissionsGranted
            val showSetupScreen = remember(isSetupComplete, permissionsValid, isBenchmarkMode) {
                when {
                    isBenchmarkMode -> false
                    isSetupComplete == null -> null
                    else -> !isSetupComplete!! || !permissionsValid
                }
            }

            // Sync Trigger: When we are NOT showing setup (meaning permissions are good and setup is done)
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                     LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                     mainViewModel.startSync()
                }
            }

            NobodyListensTheme(
                darkTheme = useDarkTheme,
                useBrandColors = appColorStyle != com.nobodybuilds.listens.data.preferences.AppColorStyle.MATERIAL_YOU,
                pitchBlack = appPitchBlack
            ) {
                var contentVisible by remember { mutableStateOf(false) }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(600, easing = LinearOutSlowInEasing),
                    label = "AppContentAlpha"
                )

                LaunchedEffect(Unit) {
                    // Delay slightly to ensure first frame layout is done behind Splash
                    delay(100)
                    contentVisible = true
                }

                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, 
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSetupScreen == null) {
                        SetupGateLoadingScreen()
                    } else {
                        AnimatedContent(
                            targetState = showSetupScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Transition to Setup
                                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                } else {
                                    // Transition from Setup to Main App
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                }
                            },
                            label = "SetupTransition"
                        ) { shouldShowSetup ->
                            if (shouldShowSetup) {
                                SetupScreen(onSetupComplete = {
                                    // Repository-backed setup completion updates the gate automatically.
                                })
                            } else {
                                MainAppContent(playerViewModel, mainViewModel)
                            }
                        }
                    }

                    // Safe mode banner (after repeated early crashes) — see data/crash/SafeMode.kt.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        com.nobodybuilds.listens.presentation.crash.SafeModeBanner(
                            Modifier
                                .align(androidx.compose.ui.Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    // "Something went wrong last time" — shown once per saved crash (filesDir/crash/last_crash.txt).
                    com.nobodybuilds.listens.presentation.crash.CrashReportOnLaunch(enabled = !isBenchmarkMode)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (testHooksAllowed && intent.getBooleanExtra("is_benchmark", false)) {
            intent.getStringExtra("smoke_route")?.let { route ->
                // "screensaver" is its own Activity, not a nav route.
                if (route == "screensaver") {
                    com.nobodybuilds.listens.presentation.screensaver.ScreensaverActivity.start(this)
                } else {
                    _pendingSmokeRoute.value = route
                }
            }
        }

        when {
            // Handle shuffle all shortcut / tile
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                android.util.Log.d("TileDebug", "handleIntent: ACTION_SHUFFLE_ALL received")
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null // Clear action to prevent re-triggering
            }
            
            // Handle playlist shortcut
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.nobodybuilds.listens.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.nobodybuilds.listens.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.nobodybuilds.listens.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("MainActivity", "Unable to persist URI permission for $uri", securityException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        android.util.Log.w("MainActivity", "Persistable URI permission not granted for $uri", illegalArgumentException)
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            LogUtils.w(this, "No activity available to open URL: $url")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsStateWithLifecycle()
        val hasCompletedInitialSync by mainViewModel.hasCompletedInitialSync.collectAsStateWithLifecycle()
        val syncProgress by mainViewModel.syncProgress.collectAsStateWithLifecycle()
        
        // isMediaControllerReady used below for playlist navigation gate
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        // Smoke test navigation (benchmark launches only).
        val pendingSmokeRoute by _pendingSmokeRoute.collectAsStateWithLifecycle()
        LaunchedEffect(pendingSmokeRoute) {
            val route = pendingSmokeRoute ?: return@LaunchedEffect
            repeat(50) {
                val ok = runCatching { navController.navigateSafely(route) }.getOrDefault(false)
                if (ok) { _pendingSmokeRoute.value = null; return@LaunchedEffect }
                delay(100)
            }
            _pendingSmokeRoute.value = null
        }

        // Observe pending playlist navigation
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            // Only process if we have a new playlist ID that hasn't been processed yet
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                // Wait for navigation graph to be ready (retry with delay)
                var success = false
                var attempts = 0
                while (!success && attempts < 50) { // 5 seconds max
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                // Reset so the same playlist can be opened again
                processedPlaylistId = null
            }
        }

        // Estado para controlar si el indicador de carga puede mostrarse después de un delay
        var canShowLoadingIndicator by remember { mutableStateOf(false) }
        // Track when the loading indicator was first shown for minimum display time
        var loadingShownTimestamp by remember { mutableStateOf(0L) }
        val minimumDisplayDuration = 1500L // Show loading for at least 1.5 seconds

        val shouldPotentiallyShowLoading = isSyncing && isLibraryEmpty && !hasCompletedInitialSync

        LaunchedEffect(shouldPotentiallyShowLoading) {
            if (shouldPotentiallyShowLoading) {
                // Espera un breve período antes de permitir que se muestre el indicador de carga
                // Ajusta este valor según sea necesario (por ejemplo, 300-500 ms)
                delay(300L)
                // Vuelve a verificar la condición después del delay,
                // ya que el estado podría haber cambiado.
                if (mainViewModel.isSyncing.value && mainViewModel.isLibraryEmpty.value) {
                    canShowLoadingIndicator = true
                    loadingShownTimestamp = System.currentTimeMillis()
                }
            } else {
                // Ensure minimum display time before hiding
                if (canShowLoadingIndicator && loadingShownTimestamp > 0) {
                    val elapsed = System.currentTimeMillis() - loadingShownTimestamp
                    val remaining = minimumDisplayDuration - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                }
                canShowLoadingIndicator = false
                loadingShownTimestamp = 0L
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(
                activity = this@MainActivity,
                playerViewModel = playerViewModel,
                navController = navController,
                userPreferencesRepository = userPreferencesRepository,
                nobodyListensHapticsEngine = nobodyListensHapticsEngine,
                openExternalUrl = ::openExternalUrl
            )

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (canShowLoadingIndicator) {
                LoadingOverlay(syncProgress)
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()

        if (intent.getBooleanExtra("is_benchmark", false)) {
            // Benchmark mode no longer loads dummy data - uses real library data instead
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    // Resumed this long without crashing -> the launch counts as stable (resets the safe mode counter).
    private val markStableRunnable = Runnable {
        runCatching { com.nobodybuilds.listens.data.crash.SafeMode.markStable(applicationContext) }
    }
    private val stabilityHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    override fun onResume() {
        super.onResume()
        stabilityHandler.removeCallbacks(markStableRunnable)
        stabilityHandler.postDelayed(markStableRunnable, 10_000L)
    }

    override fun onPause() {
        stabilityHandler.removeCallbacks(markStableRunnable)
        super.onPause()
    }

}
