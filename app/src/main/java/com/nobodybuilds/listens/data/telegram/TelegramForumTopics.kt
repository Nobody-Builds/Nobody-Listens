package com.nobodybuilds.listens.data.telegram

import com.nobodybuilds.listens.data.database.TelegramTopicEntity
import com.nobodybuilds.listens.data.model.Song
import org.drinkless.tdlib.TdApi
import timber.log.Timber

/** Forum topic support for [TelegramRepository]: forum detection, topic lists and per-topic audio. */
internal class TelegramForumTopics(
    private val clientManager: TelegramClientManager,
    private val mapMessageToSong: (TdApi.Message) -> Song?
) {
    suspend fun isForum(chatId: Long): Boolean {
        return try {
            val chat = clientManager.sendRequest<TdApi.Chat>(TdApi.GetChat(chatId))
            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup) return false
            val supergroup = clientManager.sendRequest<TdApi.Supergroup>(
                TdApi.GetSupergroup(type.supergroupId)
            )
            supergroup.isForum
        } catch (e: Exception) {
            Timber.w(e, "isForum check failed for chatId=$chatId")
            false
        }
    }

    suspend fun getForumTopics(chatId: Long): List<TelegramTopicEntity> {
        val topics = mutableListOf<TelegramTopicEntity>()
        try {
            var offsetDate = 0
            var offsetMessageId = 0L          // int53 → Long ✓
            var offsetForumTopicId = 0        // int32 → Int ✓

            while (true) {
                val request = TdApi.GetForumTopics().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.offsetDate = offsetDate              // Int ✓
                    this.offsetMessageId = offsetMessageId   // Long ✓
                    this.offsetForumTopicId = offsetForumTopicId  // Int ✓
                    this.limit = 100
                }
                val result = clientManager.sendRequest<TdApi.ForumTopics>(request)

                if (result.topics.isEmpty()) break

                for (topic in result.topics) {
                    val info = topic.info
                    val emojiId = info.icon.customEmojiId

                    // Resolve the thread ID from ForumTopicInfo via reflection.
                    // We only accept Long/Int fields with a non-zero value whose name
                    // looks like a thread/message identifier. We skip fields named
                    // exactly "id" because in many TDLib Java builds that field is a
                    // String composite key, not the numeric thread ID.
                    val threadId: Long = run {
                        // Log all fields once so we can confirm the correct name in Logcat
                        val allFields = info.javaClass.declaredFields
                        Timber.d("ForumTopicInfo fields: ${allFields.map { "${it.name}:${it.type.simpleName}" }}")

                        var resolved = 0L
                        // Prefer the most specific name first, skip bare "id" (likely String)
                        val preferredNames = listOf(
                            "messageThreadId", "message_thread_id",
                            "threadId", "thread_id",
                            "topicId", "topic_id",
                            "forumTopicId", "forum_topic_id"
                        )
                        for (name in preferredNames) {
                            try {
                                val f = info.javaClass.getDeclaredField(name)
                                f.isAccessible = true
                                val v = f.get(info)
                                val candidate = when (v) {
                                    is Long -> v
                                    is Int  -> v.toLong()
                                    else    -> 0L
                                }
                                if (candidate != 0L) {
                                    Timber.d("ForumTopicInfo: resolved threadId via field '$name' = $candidate")
                                    resolved = candidate
                                    break
                                }
                            } catch (_: NoSuchFieldException) { }
                        }

                        // Last resort: scan ALL Long/Int fields for the first non-zero value
                        // that isn't a known non-thread field
                        if (resolved == 0L) {
                            val skipNames = setOf("chatId", "chat_id", "creatorUserId",
                                "creator_user_id", "customEmojiId", "custom_emoji_id",
                                "editDate", "edit_date", "date")
                            for (f in allFields) {
                                if (f.name in skipNames) continue
                                if (f.type != Long::class.java && f.type != Int::class.java) continue
                                try {
                                    f.isAccessible = true
                                    val candidate = when (val v = f.get(info)) {
                                        is Long -> v
                                        is Int  -> v.toLong()
                                        else    -> 0L
                                    }
                                    if (candidate != 0L) {
                                        Timber.w("ForumTopicInfo: fallback threadId via field '${f.name}' = $candidate")
                                        resolved = candidate
                                        break
                                    }
                                } catch (_: Exception) { }
                            }
                        }

                        if (resolved == 0L) {
                            Timber.e("ForumTopicInfo: could not resolve threadId for topic '${info.name}'")
                        }
                        resolved
                    }

                    // Only add topics where we successfully resolved a thread ID
                    if (threadId != 0L) {
                        topics.add(
                            TelegramTopicEntity(
                                id = "${chatId}_${threadId}",
                                chatId = chatId,
                                threadId = threadId,
                                name = info.name,
                                iconEmoji = if (emojiId != 0L) emojiId.toString() else null
                            )
                        )
                    }
                }

                if (result.nextOffsetDate == 0) break

                offsetDate         = result.nextOffsetDate           // Int ✓
                offsetMessageId    = result.nextOffsetMessageId      // Long ✓
                offsetForumTopicId = result.nextOffsetForumTopicId   // Int ✓
            }

            Timber.d("Fetched ${topics.size} forum topics for chat $chatId")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching forum topics for chat $chatId")
        }
        return topics
    }

    suspend fun getAudioMessagesByTopic(chatId: Long, threadId: Long): List<Song> {
        Timber.d("Fetching audio for topic threadId=$threadId in chat=$chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()

                    // Set the topic/thread filter via reflection to handle different TDLib builds.
                    // In newer builds the field is 'topicId' (MessageTopic object).
                    // In older builds it was 'messageThreadId' (Long).
                    val scFields = this.javaClass.declaredFields
                    Timber.d("SearchChatMessages fields: ${scFields.map { "${it.name}:${it.type.simpleName}" }}")

                    var topicSet = false

                    // Try 'topicId' field (newer TDLib — expects a MessageTopic object)
                    try {
                        val f = this.javaClass.getDeclaredField("topicId")
                        f.isAccessible = true
                        // MessageTopicForum wraps the thread ID as Int
                        f.set(this, TdApi.MessageTopicForum(threadId.toInt()))
                        Timber.d("SearchChatMessages: set topicId = MessageTopicForum($threadId)")
                        topicSet = true
                    } catch (_: NoSuchFieldException) { }

                    // Fallback: try 'messageThreadId' field (older TDLib — Long)
                    if (!topicSet) {
                        try {
                            val f = this.javaClass.getDeclaredField("messageThreadId")
                            f.isAccessible = true
                            f.set(this, threadId)
                            Timber.d("SearchChatMessages: set messageThreadId = $threadId")
                            topicSet = true
                        } catch (_: NoSuchFieldException) { }
                    }

                    if (!topicSet) {
                        Timber.e("SearchChatMessages: could not set topic filter — results will be unfiltered")
                    }
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            Timber.d("Topic $threadId: fetched ${allSongs.size} songs")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching audio for topic $threadId in chat $chatId")
        }
        return allSongs
    }
}
