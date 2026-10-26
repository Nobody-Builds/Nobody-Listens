package com.nobodybuilds.listens

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.ArtistImageRepository
import com.nobodybuilds.listens.data.telegram.TelegramRepository
import com.nobodybuilds.listens.presentation.viewmodel.LibraryStateHolder
import com.nobodybuilds.listens.presentation.viewmodel.ThemeStateHolder
import com.nobodybuilds.listens.utils.AlbumArtCacheManager
import com.nobodybuilds.listens.utils.AlbumArtUtils
import com.nobodybuilds.listens.utils.CrashHandler
import com.nobodybuilds.listens.utils.AppLocaleManager
import com.nobodybuilds.listens.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NobodyListensApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.nobodybuilds.listens.data.image.TelegramCoilFetcher.Factory>

    @Inject
    lateinit var navidromeCoilFetcherFactory: dagger.Lazy<com.nobodybuilds.listens.data.image.NavidromeCoilFetcher.Factory>

    @Inject
    lateinit var jellyfinCoilFetcherFactory: dagger.Lazy<com.nobodybuilds.listens.data.image.JellyfinCoilFetcher.Factory>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.nobodybuilds.listens.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var telegramRepository: dagger.Lazy<TelegramRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var updatePreferences: dagger.Lazy<com.nobodybuilds.listens.data.update.UpdatePreferences>

    @Inject
    lateinit var updateScheduler: dagger.Lazy<com.nobodybuilds.listens.data.update.UpdateScheduler>

    @Inject
    lateinit var musicFoldersRepository: dagger.Lazy<com.nobodybuilds.listens.data.library.MusicFoldersRepository>

    @Inject
    lateinit var smartShuffleController: dagger.Lazy<com.nobodybuilds.listens.data.playback.SmartShuffleController>

    @Inject
    lateinit var smartPreferences: dagger.Lazy<com.nobodybuilds.listens.data.smart.SmartPreferences>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "lmusic_playback"
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
        // Crash capture as early as possible (before super.onCreate / Hilt), so early crashes are
        // recorded too. Writes filesDir/crash/last_crash.txt, then the default handler runs.
        // Benchmark runs intentionally kill/restart the process: don't record those as crashes.
        if (BuildConfig.BUILD_TYPE != "benchmark") runCatching { CrashHandler.install(this) }
    }

    override fun onCreate() {
        super.onCreate()

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        val isBenchmarkBuild = BuildConfig.BUILD_TYPE == "benchmark"
        // Safe mode after two early crashes in a row (counted by CrashHandler; see data/crash/SafeMode.kt).
        val safeMode = !isBenchmarkBuild &&
            runCatching { com.nobodybuilds.listens.data.crash.SafeMode.onAppStart(this) }.getOrDefault(false)
        if (!isBenchmarkBuild) {
            // A process that lives this long (e.g. started only for playback) resets the early-crash count.
            runCatching {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { com.nobodybuilds.listens.data.crash.SafeMode.markStable(this) }, 60_000L
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
            Timber.plant(ReleaseTree())
        }
        // Last ~200 INFO+ log lines, attached to crash reports.
        runCatching { Timber.plant(com.nobodybuilds.listens.data.crash.RecentLogBuffer) }
        if (safeMode) runCatching { applySafeMode() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            // Channels from before the Nobody Listens rename would otherwise linger in system settings.
            listOf("pixelplay_music_channel", "pixelplay_cast_server", "pixelplay_watch_transfers").forEach { legacyId ->
                runCatching { notificationManager.deleteNotificationChannel(legacyId) }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        startupScope.launch {
            AlbumArtUtils.migrateLegacyCacheLocation(this@NobodyListensApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }

        // Resolve these on the main thread: their dependency graphs create objects that
        // register lifecycle observers, which Android only allows on the main thread.
        if (!safeMode) {
            val foldersRepository = musicFoldersRepository.get()
            startupScope.launch {
                // One-time move from "excluded folders" to the "music folders" include list.
                runCatching { foldersRepository.migrateIfNeeded() }
                    .onFailure { Timber.w(it, "Music folders migration failed") }
            }
            startOptionalFeatures()
        } else {
            // "Turn features back on" in the safe mode banner restarts these without a relaunch.
            com.nobodybuilds.listens.data.crash.SafeMode.addExitListener { runCatching { startOptionalFeatures() } }
        }
    }

    /** Features that stay off in safe mode. Main thread only (resolves Lazy deps). */
    private fun startOptionalFeatures() {
        // Smart shuffle setting -> shuffle order used by every shuffle path (QueueUtils).
        smartShuffleController.get().start(startupScope)

        if (BuildConfig.BUILD_TYPE != "benchmark") {
            val prefs = updatePreferences.get()
            val scheduler = updateScheduler.get()
            startupScope.launch {
                // Weekly update check (KEEP keeps the existing schedule); cancelled when switched off.
                val enabled = runCatching { prefs.checksEnabled.first() }.getOrDefault(true)
                runCatching { scheduler.apply(enabled) }
            }
        }
    }

    /**
     * Runs when safe mode is active this launch: smart shuffle stays plain, update checks are not
     * scheduled this session, the music folders migration waits, and Smart indexing is kept off for
     * this session (SmartIndexScheduler / SmartIndexWorker check SafeMode). The saved Smart setting
     * is left unchanged, so indexing resumes after "Turn features back on".
     */
    private fun applySafeMode() {
        com.nobodybuilds.listens.utils.SmartShuffleRuntime.enabled = false
        if (com.nobodybuilds.listens.data.crash.SafeMode.enteredThisLaunch) {
            Timber.w("Safe mode entered after repeated early crashes")
        }
        val smartPreferences = smartPreferences.get() // resolved on the main thread (trivial constructor)
        startupScope.launch {
            runCatching { com.nobodybuilds.listens.data.smart.SmartIndexScheduler.cancel(this@NobodyListensApplication) }
        }
        com.nobodybuilds.listens.data.crash.SafeMode.addExitListener {
            startupScope.launch {
                runCatching {
                    if (smartPreferences.isEnabled()) {
                        com.nobodybuilds.listens.data.smart.SmartIndexScheduler.schedule(this@NobodyListensApplication)
                    }
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(localArtworkCoilFetcherFactory.get())
                add(telegramCoilFetcherFactory.get())
                add(navidromeCoilFetcherFactory.get())
                add(jellyfinCoilFetcherFactory.get())
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            telegramRepository.get().clearMemoryCache()
            MediaMetadataRetrieverPool.clear()
        }

        libraryStateHolder.get().trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
