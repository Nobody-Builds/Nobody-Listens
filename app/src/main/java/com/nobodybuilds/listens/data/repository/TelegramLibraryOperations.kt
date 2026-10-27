package com.nobodybuilds.listens.data.repository

import android.content.Context
import android.util.Log
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.TelegramChannelEntity
import com.nobodybuilds.listens.data.database.TelegramDao
import com.nobodybuilds.listens.data.database.TelegramTopicEntity
import com.nobodybuilds.listens.data.database.toTelegramEntity
import com.nobodybuilds.listens.data.database.toTelegramEntityWithThread
import com.nobodybuilds.listens.data.model.Song
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Telegram channel, topic and song storage for [MusicRepositoryImpl], plus the unified-library sync trigger. */
internal class TelegramLibraryOperations(
    private val context: Context,
    private val musicDao: MusicDao,
    private val telegramDao: TelegramDao,
    private val telegramCacheManagerProvider: Lazy<com.nobodybuilds.listens.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.nobodybuilds.listens.data.telegram.TelegramRepository>,
    private val repositoryScope: CoroutineScope,
) {

    @Volatile private var telegramDownloadSyncObserverStarted = false
    private val telegramCacheManager: com.nobodybuilds.listens.data.telegram.TelegramCacheManager
        get() = telegramCacheManagerProvider.get()
    private val telegramRepository: com.nobodybuilds.listens.data.telegram.TelegramRepository
        get() = telegramRepositoryProvider.get()

    private fun ensureTelegramDownloadSyncObserverStarted() {
        if (telegramDownloadSyncObserverStarted) return
        telegramDownloadSyncObserverStarted = true

        repositoryScope.launch {
            telegramRepository.songFileUpdated.collect {
                androidx.work.WorkManager.getInstance(context).enqueue(
                    com.nobodybuilds.listens.data.worker.SyncWorker.incrementalSyncWork()
                )
            }
        }
    }

    suspend fun saveTelegramSongs(songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }
        if (entities.isNotEmpty()) {
            ensureTelegramDownloadSyncObserverStarted()
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
            // Trigger sync to update main DB
            androidx.work.WorkManager.getInstance(context).enqueue(
                com.nobodybuilds.listens.data.worker.SyncWorker.incrementalSyncWork()
            )
        }
    }

    suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }.filter { it.chatId == chatId }
        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByChatId(chatId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }
        // Sync into the unified `songs` table is triggered later by saveTelegramChannel().
        // We deliberately do NOT enqueue here: the SyncWorker gates Telegram processing on
        // an existing channel row (telegramDao.getAllChannels()), and on first add this
        // function runs BEFORE the channel entity is persisted. Triggering here would race
        // and skip the sync, leaving songs invisible until the next app launch.
    }

    suspend fun clearTelegramData() {
        // Delete all Telegram playlists from app playlists
        val allChannels = telegramDao.getAllChannels().first()
        allChannels.forEach { channel ->
            telegramRepository.deleteAppPlaylistForTelegramChannel(channel.chatId)
        }

        musicDao.clearAllTelegramSongs()
        telegramDao.clearAll()
        // Clear all Telegram caches (TDLib files, embedded art, memory)
        telegramRepository.clearMemoryCache()
        telegramCacheManager.clearAllCache()
    }

    suspend fun saveTelegramChannel(channel: TelegramChannelEntity) {
        telegramDao.insertChannel(channel)

        // Create or update the corresponding app playlist.
        // Forum channels use per-topic playlists (managed by replaceTelegramSongsForTopic),
        // so we skip the flat channel-level playlist when topics exist.
        try {
            val topics = withContext(Dispatchers.IO) {
                telegramDao.getTopicsByChannelOnce(channel.chatId)
            }
            if (topics.isEmpty()) {
                // Flat (non-forum) channel: create/update a single channel playlist
                val channelSongs = withContext(Dispatchers.IO) {
                    telegramDao.getSongsByChatId(channel.chatId)
                }
                telegramRepository.updateAppPlaylistForTelegramChannel(
                    channel.chatId,
                    channel.title,
                    channelSongs
                )
            }
            // Forum channels: topic playlists are managed by replaceTelegramSongsForTopic
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to update app playlist for Telegram channel ${channel.chatId}", e)
        }

        // Trigger the unified-library sync now that the channel row exists. SyncWorker's
        // Telegram phase is gated on telegramDao.getAllChannels() being non-empty, so this
        // is the earliest moment the sync can succeed. KEEP (not REPLACE) ensures we never
        // cancel a heavier full/rebuild that might be running under the same unique name.
        requestTelegramUnifiedSync()
    }

    fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> {
        return telegramDao.getAllChannels()
    }

    suspend fun deleteTelegramChannel(chatId: Long) {
        musicDao.clearTelegramSongsForChat(chatId)
        telegramDao.deleteSongsByChatId(chatId)
        telegramDao.deleteTopicsByChannel(chatId)     // NEW: remove topics
        telegramDao.deleteChannel(chatId)
        telegramRepository.deleteAppPlaylistForTelegramChannel(chatId)
        telegramRepository.deleteAllTopicPlaylistsForChannel(chatId)  // NEW: remove topic playlists
    }

    suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>) {
        telegramDao.insertTopics(topics)
    }

    suspend fun replaceTopicsForChannel(
        chatId: Long,
        freshTopics: List<TelegramTopicEntity>
    ) {
        val existingTopics = telegramDao.getTopicsByChannelOnce(chatId)
        val freshThreadIds = freshTopics.map { it.threadId }.toSet()

        // Delete topics that are no longer returned by Telegram
        val removedTopics = existingTopics.filter { it.threadId !in freshThreadIds }
        for (removed in removedTopics) {
            // Delete their songs from the telegram_songs table
            telegramDao.deleteSongsByTopicId(chatId, removed.threadId)
            // Delete their songs from the main music DB
            musicDao.clearTelegramSongsForTopic(chatId, removed.threadId)
            // Delete their playlist from the app playlist store
            telegramRepository.deleteAppPlaylistForTopic(chatId, removed.threadId)
            // Delete the topic row itself
            telegramDao.deleteTopic(removed.id)
        }

        // Insert/update the fresh topic list
        if (freshTopics.isNotEmpty()) {
            telegramDao.insertTopics(freshTopics)
        }
    }

    suspend fun getTopicsForChannel(chatId: Long): List<TelegramTopicEntity> {
        return telegramDao.getTopicsByChannelOnce(chatId)
    }
    fun getAllTelegramTopics(): Flow<List<TelegramTopicEntity>> {
        return telegramDao.getAllTopics()
    }

    suspend fun replaceTelegramSongsForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        songs: List<Song>
    ) {
        // Stamp each song entity with the threadId before inserting
        val entities = songs.mapNotNull { it.toTelegramEntityWithThread(threadId) }
            .filter { it.chatId == chatId }

        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByTopicId(chatId, threadId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }

        // Create/update the per-topic app playlist
        telegramRepository.updateAppPlaylistForTopic(chatId, threadId, topicName, entities)

        // Best-effort sync trigger: if no worker is in flight yet, KEEP enqueues a fresh
        // incremental run that will catch the rows being committed during the topic
        // loop. This is the safety net for forum flows that fail mid-loop and never
        // reach the final saveTelegramChannel() call (the dashboard's syncForumChannel
        // path in particular — its only sync trigger is the end-of-flow channel save).
        // Subsequent topic insertions are no-ops here because the existing worker is
        // running; the calling VM's finally block ensures a follow-up sync runs
        // afterwards to pick up rows added past the worker's Telegram phase.
        requestTelegramUnifiedSync()
    }

    fun requestTelegramUnifiedSync() {
        // KEEP — never disturb a full/rebuild sync that shares this unique work name.
        // If no worker is queued or running, this enqueues a fresh incremental run.
        // If one is already in flight, we let it complete; its Telegram phase reads
        // telegram_songs at run time and will pick up rows committed before then.
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.nobodybuilds.listens.data.worker.SyncWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            com.nobodybuilds.listens.data.worker.SyncWorker.incrementalSyncWork()
        )
    }
}
