package com.twilighty.lmusic.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.twilighty.lmusic.BuildConfig
import com.twilighty.lmusic.LMusicApplication
import com.twilighty.lmusic.data.database.AlbumArtThemeDao
import com.twilighty.lmusic.data.database.EngagementDao
import com.twilighty.lmusic.data.database.FavoritesDao
import com.twilighty.lmusic.data.database.GDriveDao
import com.twilighty.lmusic.data.database.LyricsDao
import com.twilighty.lmusic.data.database.AiCacheDao
import com.twilighty.lmusic.data.database.AiUsageDao
import com.twilighty.lmusic.data.database.LocalPlaylistDao
import com.twilighty.lmusic.data.database.MusicDao
import com.twilighty.lmusic.data.database.LMusicDatabase
import com.twilighty.lmusic.data.database.SearchHistoryDao
import com.twilighty.lmusic.data.database.TransitionDao
import com.twilighty.lmusic.data.preferences.UserPreferencesRepository
import com.twilighty.lmusic.data.preferences.PlaylistPreferencesRepository
import com.twilighty.lmusic.data.preferences.dataStore
import com.twilighty.lmusic.data.media.SongMetadataEditor
import com.twilighty.lmusic.data.network.deezer.DeezerApiService
import com.twilighty.lmusic.data.network.netease.NeteaseApiService
import com.twilighty.lmusic.data.network.lyrics.LrcLibApiService
import com.twilighty.lmusic.data.repository.ArtistImageRepository
import com.twilighty.lmusic.data.repository.LyricsRepository
import com.twilighty.lmusic.data.repository.LyricsRepositoryImpl
import com.twilighty.lmusic.data.repository.MediaStoreSongRepository
import com.twilighty.lmusic.data.repository.MusicRepository
import com.twilighty.lmusic.data.repository.MusicRepositoryImpl
import com.twilighty.lmusic.data.repository.SongRepository
import com.twilighty.lmusic.data.repository.TransitionRepository
import com.twilighty.lmusic.data.repository.TransitionRepositoryImpl
import com.twilighty.lmusic.data.repository.FolderTreeBuilder
import dagger.Module
import dagger.Provides
import dagger.Lazy
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): LMusicApplication {
        return app as LMusicApplication
    }

    @Singleton
    @Provides
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideSessionToken(@ApplicationContext context: Context): androidx.media3.session.SessionToken {
        return androidx.media3.session.SessionToken(
            context,
            android.content.ComponentName(context, com.twilighty.lmusic.data.service.MusicService::class.java)
        )
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    @AppScope
    fun provideAppCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Singleton
    @Provides
    fun provideLMusicDatabase(@ApplicationContext context: Context): LMusicDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            LMusicDatabase::class.java,
            // Internal file name kept from the first builds so existing libraries survive updates.
            "pixelplay_database"
        ).addMigrations(
            LMusicDatabase.MIGRATION_3_4,
            LMusicDatabase.MIGRATION_4_5,
            LMusicDatabase.MIGRATION_5_6,
            LMusicDatabase.MIGRATION_6_7,
            LMusicDatabase.MIGRATION_7_8,
            LMusicDatabase.MIGRATION_8_9,
            LMusicDatabase.MIGRATION_9_10,
            LMusicDatabase.MIGRATION_10_11,
            LMusicDatabase.MIGRATION_11_12,
            LMusicDatabase.MIGRATION_12_13,
            LMusicDatabase.MIGRATION_13_14,
            LMusicDatabase.MIGRATION_14_15,
            LMusicDatabase.MIGRATION_15_16,
            LMusicDatabase.MIGRATION_16_17,
            LMusicDatabase.MIGRATION_17_18,
            LMusicDatabase.MIGRATION_18_19,
            LMusicDatabase.MIGRATION_19_20,
            LMusicDatabase.MIGRATION_20_21,
            LMusicDatabase.MIGRATION_21_22,
            LMusicDatabase.MIGRATION_22_23,
            LMusicDatabase.MIGRATION_23_24,
            LMusicDatabase.MIGRATION_24_25,
            LMusicDatabase.MIGRATION_25_26,
            LMusicDatabase.MIGRATION_26_27,
            LMusicDatabase.MIGRATION_27_28,
            LMusicDatabase.MIGRATION_28_29,
            LMusicDatabase.MIGRATION_29_30,
            LMusicDatabase.MIGRATION_30_31,
            LMusicDatabase.MIGRATION_31_32,
            LMusicDatabase.MIGRATION_32_33,
            LMusicDatabase.MIGRATION_33_34,
            LMusicDatabase.MIGRATION_34_35,
            LMusicDatabase.MIGRATION_35_36,
            LMusicDatabase.MIGRATION_36_37,
            LMusicDatabase.MIGRATION_37_38,
            LMusicDatabase.MIGRATION_38_39,
            LMusicDatabase.MIGRATION_39_40,
            LMusicDatabase.MIGRATION_40_41
        )
            .addCallback(LMusicDatabase.createRuntimeArtifactsCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

        // P2-4: Only allow destructive migration in debug builds.
        // In release, a migration bug will crash the app (revealing the problem)
        // rather than silently wiping user data (playlists, favorites, statistics).
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }

        return builder.build()
    }

    @Singleton
    @Provides
    fun provideAlbumArtThemeDao(database: LMusicDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: LMusicDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: LMusicDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: LMusicDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: LMusicDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: LMusicDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: LMusicDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Singleton
    @Provides
    fun provideGDriveDao(database: LMusicDatabase): GDriveDao {
        return database.gdriveDao()
    }

    @Singleton
    @Provides
    fun provideLocalPlaylistDao(database: LMusicDatabase): LocalPlaylistDao {
        return database.localPlaylistDao()
    }

    @Singleton
    @Provides
    fun provideQqMusicDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.QqMusicDao {
        return database.qqmusicDao()
    }

    @Singleton
    @Provides
    fun provideNavidromeDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.NavidromeDao {
        return database.navidromeDao()
    }
    
    @Singleton
    @Provides
    fun provideAiCacheDao(database: LMusicDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun provideAiUsageDao(database: LMusicDatabase): AiUsageDao {
        return database.aiUsageDao()
    }

    @Provides
    fun provideSongFeaturesDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.SongFeaturesDao {
        return database.songFeaturesDao()
    }

    @Singleton
    @Provides
    fun provideJellyfinDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.JellyfinDao {
        return database.jellyfinDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        // Add interceptor for QQ Music images
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                // Add Referer header for QQ Music images
                val newRequest = if (url.contains("y.qq.com")) {
                    request.newBuilder()
                        .header("Referer", "https://y.qq.com/")
                        .build()
                } else {
                    request
                }

                chain.proceed(newRequest)
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // Use 20% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, always cache
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        lrcLibApiService: LrcLibApiService,
        lyricsDao: LyricsDao,
        okHttpClient: OkHttpClient,
        youTubeMusicRepository: com.twilighty.lmusic.data.ytmusic.YouTubeMusicRepository,
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient,
            youTubeLyrics = { videoId -> youTubeMusicRepository.lyrics(videoId)?.text },
        )
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.twilighty.lmusic.data.observer.MediaStoreObserver,
        favoritesDao: FavoritesDao,
        userPreferencesRepository: UserPreferencesRepository,
        musicDao: MusicDao
    ): SongRepository {
        return MediaStoreSongRepository(
            context = context,
            mediaStoreObserver = mediaStoreObserver,
            favoritesDao = favoritesDao,
            userPreferencesRepository = userPreferencesRepository,
            musicDao = musicDao
        )
    }

    @Singleton
    @Provides
    fun provideTelegramDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.TelegramDao {
        return database.telegramDao()
    }

    @Singleton
    @Provides
    fun provideNeteaseDao(database: LMusicDatabase): com.twilighty.lmusic.data.database.NeteaseDao {
        return database.neteaseDao()
    }

    @Provides
    @Singleton
    fun provideFolderTreeBuilder(): FolderTreeBuilder {
        return FolderTreeBuilder()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        playlistPreferencesRepository: PlaylistPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        musicDao: MusicDao,
        lyricsRepository: LyricsRepository,
        telegramDao: com.twilighty.lmusic.data.database.TelegramDao,
        telegramCacheManager: Lazy<com.twilighty.lmusic.data.telegram.TelegramCacheManager>,
        telegramRepository: Lazy<com.twilighty.lmusic.data.telegram.TelegramRepository>,
        songRepository: SongRepository,
        favoritesDao: FavoritesDao,
        artistImageRepository: ArtistImageRepository,
        folderTreeBuilder: FolderTreeBuilder,
        youTubeMusicAccountSync: com.twilighty.lmusic.data.ytmusic.YouTubeMusicAccountSync,
    ): MusicRepository {
        return MusicRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            playlistPreferencesRepository = playlistPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            musicDao = musicDao,
            lyricsRepository = lyricsRepository,
            telegramDao = telegramDao,
            telegramCacheManagerProvider = telegramCacheManager,
            telegramRepositoryProvider = telegramRepository,
            songRepository = songRepository,
            favoritesDao = favoritesDao,
            artistImageRepository = artistImageRepository,
            folderTreeBuilder = folderTreeBuilder,
            onFavoriteChanged = youTubeMusicAccountSync::onFavoriteChanged,
        )

    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideSongMetadataEditor(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        telegramDao: com.twilighty.lmusic.data.database.TelegramDao,
        userPreferencesRepository: UserPreferencesRepository
    ): SongMetadataEditor {
        return SongMetadataEditor(context, musicDao, telegramDao, userPreferencesRepository)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con logging e interceptor de User-Agent.
     * Retry logic with backoff is handled in coroutine-based callers.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        )
        
        // Connection pool with optimized connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Add User-Agent header (required by some APIs)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "NobodyListens/1.0 (Android; Music Player)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia de OkHttpClient con timeouts para búsquedas de lyrics.
     * Includes DNS resolver, modern TLS, connection pool, and connection retry.
     */
    @Provides
    @Singleton
    @FastOkHttpClient
    fun provideFastOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        
        // Connection pool to reuse connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        // Use Cloudflare and Google DNS to avoid potential DNS issues
        val dns = okhttp3.Dns { hostname ->
            try {
                // First try system DNS
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Fallback to manual resolution if system DNS fails
                java.net.InetAddress.getAllByName(hostname).toList()
            }
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .connectionPool(connectionPool)
            // Use HTTP/1.1 to avoid HTTP/2 stream issues with some servers
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // Use modern TLS connection spec
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS
            ))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Enable built-in retry on connection failure
            .retryOnConnectionFailure(true)
            // Add headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", "NobodyListens/1.0 (Android; Music Player)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(@FastOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Deezer.
     */
    @Provides
    @Singleton
    @DeezerRetrofit
    fun provideDeezerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Deezer.
     */
    @Provides
    @Singleton
    fun provideDeezerApiService(@DeezerRetrofit retrofit: Retrofit): DeezerApiService {
        return retrofit.create(DeezerApiService::class.java)
    }

    /**
     * Provee el repositorio de imágenes de artistas.
     */
    @Provides
    @Singleton
    fun provideArtistImageRepository(
        deezerApiService: DeezerApiService,
        musicDao: MusicDao
    ): ArtistImageRepository {
        return ArtistImageRepository(deezerApiService, musicDao)
    }
}
