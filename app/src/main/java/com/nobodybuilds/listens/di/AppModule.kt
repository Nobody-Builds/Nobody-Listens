package com.nobodybuilds.listens.di

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
import com.nobodybuilds.listens.BuildConfig
import com.nobodybuilds.listens.NobodyListensApplication
import com.nobodybuilds.listens.data.database.AlbumArtThemeDao
import com.nobodybuilds.listens.data.database.EngagementDao
import com.nobodybuilds.listens.data.database.FavoritesDao
import com.nobodybuilds.listens.data.database.GDriveDao
import com.nobodybuilds.listens.data.database.LyricsDao
import com.nobodybuilds.listens.data.database.AiCacheDao
import com.nobodybuilds.listens.data.database.AiUsageDao
import com.nobodybuilds.listens.data.database.LocalPlaylistDao
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.NobodyListensDatabase
import com.nobodybuilds.listens.data.database.SearchHistoryDao
import com.nobodybuilds.listens.data.database.TransitionDao
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.preferences.dataStore
import com.nobodybuilds.listens.data.media.SongMetadataEditor
import com.nobodybuilds.listens.data.network.deezer.DeezerApiService
import com.nobodybuilds.listens.data.network.netease.NeteaseApiService
import com.nobodybuilds.listens.data.network.lyrics.LrcLibApiService
import com.nobodybuilds.listens.data.repository.ArtistImageRepository
import com.nobodybuilds.listens.data.repository.LyricsRepository
import com.nobodybuilds.listens.data.repository.LyricsRepositoryImpl
import com.nobodybuilds.listens.data.repository.MediaStoreSongRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.repository.MusicRepositoryImpl
import com.nobodybuilds.listens.data.repository.SongRepository
import com.nobodybuilds.listens.data.repository.TransitionRepository
import com.nobodybuilds.listens.data.repository.TransitionRepositoryImpl
import com.nobodybuilds.listens.data.repository.FolderTreeBuilder
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
    fun provideApplication(@ApplicationContext app: Context): NobodyListensApplication {
        return app as NobodyListensApplication
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
            android.content.ComponentName(context, com.nobodybuilds.listens.data.service.MusicService::class.java)
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
    fun provideNobodyListensDatabase(@ApplicationContext context: Context): NobodyListensDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            NobodyListensDatabase::class.java,
            // Internal file name kept from the first builds so existing libraries survive updates.
            "pixelplay_database"
        ).addMigrations(
            NobodyListensDatabase.MIGRATION_3_4,
            NobodyListensDatabase.MIGRATION_4_5,
            NobodyListensDatabase.MIGRATION_5_6,
            NobodyListensDatabase.MIGRATION_6_7,
            NobodyListensDatabase.MIGRATION_7_8,
            NobodyListensDatabase.MIGRATION_8_9,
            NobodyListensDatabase.MIGRATION_9_10,
            NobodyListensDatabase.MIGRATION_10_11,
            NobodyListensDatabase.MIGRATION_11_12,
            NobodyListensDatabase.MIGRATION_12_13,
            NobodyListensDatabase.MIGRATION_13_14,
            NobodyListensDatabase.MIGRATION_14_15,
            NobodyListensDatabase.MIGRATION_15_16,
            NobodyListensDatabase.MIGRATION_16_17,
            NobodyListensDatabase.MIGRATION_17_18,
            NobodyListensDatabase.MIGRATION_18_19,
            NobodyListensDatabase.MIGRATION_19_20,
            NobodyListensDatabase.MIGRATION_20_21,
            NobodyListensDatabase.MIGRATION_21_22,
            NobodyListensDatabase.MIGRATION_22_23,
            NobodyListensDatabase.MIGRATION_23_24,
            NobodyListensDatabase.MIGRATION_24_25,
            NobodyListensDatabase.MIGRATION_25_26,
            NobodyListensDatabase.MIGRATION_26_27,
            NobodyListensDatabase.MIGRATION_27_28,
            NobodyListensDatabase.MIGRATION_28_29,
            NobodyListensDatabase.MIGRATION_29_30,
            NobodyListensDatabase.MIGRATION_30_31,
            NobodyListensDatabase.MIGRATION_31_32,
            NobodyListensDatabase.MIGRATION_32_33,
            NobodyListensDatabase.MIGRATION_33_34,
            NobodyListensDatabase.MIGRATION_34_35,
            NobodyListensDatabase.MIGRATION_35_36,
            NobodyListensDatabase.MIGRATION_36_37,
            NobodyListensDatabase.MIGRATION_37_38,
            NobodyListensDatabase.MIGRATION_38_39,
            NobodyListensDatabase.MIGRATION_39_40,
            NobodyListensDatabase.MIGRATION_40_41
        )
            .addCallback(NobodyListensDatabase.createRuntimeArtifactsCallback())
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
    fun provideAlbumArtThemeDao(database: NobodyListensDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: NobodyListensDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: NobodyListensDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: NobodyListensDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: NobodyListensDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: NobodyListensDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: NobodyListensDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Singleton
    @Provides
    fun provideGDriveDao(database: NobodyListensDatabase): GDriveDao {
        return database.gdriveDao()
    }

    @Singleton
    @Provides
    fun provideLocalPlaylistDao(database: NobodyListensDatabase): LocalPlaylistDao {
        return database.localPlaylistDao()
    }

    @Singleton
    @Provides
    fun provideQqMusicDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.QqMusicDao {
        return database.qqmusicDao()
    }

    @Singleton
    @Provides
    fun provideNavidromeDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.NavidromeDao {
        return database.navidromeDao()
    }
    
    @Singleton
    @Provides
    fun provideAiCacheDao(database: NobodyListensDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun provideAiUsageDao(database: NobodyListensDatabase): AiUsageDao {
        return database.aiUsageDao()
    }

    @Provides
    fun provideSongFeaturesDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.SongFeaturesDao {
        return database.songFeaturesDao()
    }

    @Singleton
    @Provides
    fun provideJellyfinDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.JellyfinDao {
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
        youTubeMusicRepository: com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository,
        jioSaavnRepository: com.nobodybuilds.listens.data.jiosaavn.JioSaavnRepository,
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient,
            youTubeLyrics = { videoId -> youTubeMusicRepository.lyrics(videoId)?.text },
            jioSaavnLyrics = { songId -> jioSaavnRepository.lyrics(songId)?.text },
        )
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.nobodybuilds.listens.data.observer.MediaStoreObserver,
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
    fun provideTelegramDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.TelegramDao {
        return database.telegramDao()
    }

    @Singleton
    @Provides
    fun provideNeteaseDao(database: NobodyListensDatabase): com.nobodybuilds.listens.data.database.NeteaseDao {
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
        telegramDao: com.nobodybuilds.listens.data.database.TelegramDao,
        telegramCacheManager: Lazy<com.nobodybuilds.listens.data.telegram.TelegramCacheManager>,
        telegramRepository: Lazy<com.nobodybuilds.listens.data.telegram.TelegramRepository>,
        songRepository: SongRepository,
        favoritesDao: FavoritesDao,
        artistImageRepository: ArtistImageRepository,
        folderTreeBuilder: FolderTreeBuilder,
        youTubeMusicAccountSync: com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAccountSync,
        jioSaavnAccountSync: com.nobodybuilds.listens.data.jiosaavn.JioSaavnAccountSync,
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
            // Each sync ignores songs that are not its own, so both hear every like.
            onFavoriteChanged = { songId, isFavorite ->
                youTubeMusicAccountSync.onFavoriteChanged(songId, isFavorite)
                jioSaavnAccountSync.onFavoriteChanged(songId, isFavorite)
            },
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
        telegramDao: com.nobodybuilds.listens.data.database.TelegramDao,
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
