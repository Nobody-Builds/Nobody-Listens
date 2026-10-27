package com.nobodybuilds.listens.data.telegram

import com.nobodybuilds.listens.data.model.Song
import org.drinkless.tdlib.TdApi

/**
 * Maps a Telegram audio message, or a document that is an audio file, to a [Song].
 * Artwork that is already downloaded is remembered in [resolvedPathCache] by file id.
 */
internal fun mapTelegramMessageToSong(message: TdApi.Message, resolvedPathCache: MutableMap<Int, String>): Song? {
    val content = message.content

    return when (content) {
        is TdApi.MessageAudio -> {
            val audio = content.audio

            var albumArtPath: String? = null
            var thumbnail = audio.albumCoverThumbnail

            if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
            }

            if (thumbnail != null) {
                albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                    resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                }
            }

            Song(
                id = "${message.chatId}_${message.id}",
                title = audio.title.takeIf { it.isNotEmpty() }
                    ?: audio.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                artist = audio.performer.takeIf { it.isNotEmpty() } ?: "Unknown Artist",
                artistId = -1,
                album = "Telegram Stream",
                albumId = -1,
                path = "",
                contentUriString = "telegram://${message.chatId}/${message.id}",
                albumArtUriString = albumArtPath,
                duration = audio.duration * 1000L,
                telegramFileId = audio.audio.id,
                telegramChatId = message.chatId,
                mimeType = audio.mimeType,
                bitrate = 0,
                sampleRate = 0,
                year = 0,
                trackNumber = 0,
                dateAdded = message.date.toLong(),
                isFavorite = false
            )
        }
        is TdApi.MessageDocument -> {
            val document = content.document

            val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
            val isAudioExtension = document.fileName.lowercase().run {
                endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") ||
                        endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
            }

            if (isAudioMime || isAudioExtension) {
                var albumArtPath: String? = null
                val thumbnail = document.thumbnail
                if (thumbnail != null) {
                    albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                    if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                        resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                    }
                }

                Song(
                    id = "${message.chatId}_${message.id}",
                    title = document.fileName.substringBeforeLast('.').ifEmpty { "Unknown Track" },
                    artist = "Telegram Audio",
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
                    albumArtUriString = albumArtPath,
                    duration = 0L,
                    telegramFileId = document.document.id,
                    telegramChatId = message.chatId,
                    mimeType = document.mimeType,
                    bitrate = 0,
                    sampleRate = 0,
                    year = 0,
                    trackNumber = 0,
                    dateAdded = message.date.toLong(),
                    isFavorite = false
                )
            } else null
        }
        else -> null
    }
}
