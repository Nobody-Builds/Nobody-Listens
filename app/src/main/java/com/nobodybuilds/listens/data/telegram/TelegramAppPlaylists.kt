package com.nobodybuilds.listens.data.telegram

import com.nobodybuilds.listens.data.database.TelegramSongEntity
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import timber.log.Timber

/** Mirrors synced Telegram channels and forum topics as app playlists. */
internal class TelegramAppPlaylists(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {
    private companion object {
        private const val TELEGRAM_PLAYLIST_PREFIX = "telegram_channel:"
        private const val TELEGRAM_TOPIC_PLAYLIST_PREFIX = "telegram_topic:"
    }

    private fun getAppPlaylistIdForChannel(chatId: Long) = "$TELEGRAM_PLAYLIST_PREFIX$chatId"
    private fun getAppPlaylistIdForTopic(chatId: Long, threadId: Long) =
        "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_$threadId"

    private fun toUnifiedTelegramSongId(telegramSongId: String): Long {
        val songId = -(telegramSongId.hashCode().toLong().absoluteValue)
        return if (songId == 0L) -1L else songId
    }

    suspend fun updateAppPlaylistForTelegramChannel(
        chatId: Long,
        channelTitle: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(getAppPlaylistIdForChannel(chatId), channelTitle, unifiedSongIds, "TELEGRAM")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for Telegram channel $chatId")
        }
    }

    suspend fun updateAppPlaylistForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(
                getAppPlaylistIdForTopic(chatId, threadId),
                topicName,
                unifiedSongIds,
                "TELEGRAM_TOPIC"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for topic $threadId in chat $chatId")
        }
    }

    private suspend fun upsertPlaylist(
        playlistId: String,
        name: String,
        songIds: List<String>,
        source: String
    ) {
        val existing = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow
                .map { it.find { p -> p.id == playlistId } }
                .first()
        }

        if (existing != null) {
            playlistPreferencesRepository.updatePlaylist(
                existing.copy(
                    name = name,
                    songIds = songIds,
                    lastModified = System.currentTimeMillis(),
                    source = source
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = songIds,
                customId = playlistId,
                source = source
            )
        }
    }

    suspend fun deleteAppPlaylistForTelegramChannel(chatId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForChannel(chatId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Telegram channel $chatId")
        }
    }

    suspend fun deleteAppPlaylistForTopic(chatId: Long, threadId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForTopic(chatId, threadId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for topic $threadId in chat $chatId")
        }
    }

    suspend fun deleteAllTopicPlaylistsForChannel(chatId: Long) {
        try {
            val all = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.first()
            }
            val prefix = "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_"
            all.filter { it.id.startsWith(prefix) }.forEach {
                playlistPreferencesRepository.deletePlaylist(it.id)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete topic playlists for channel $chatId")
        }
    }
}
