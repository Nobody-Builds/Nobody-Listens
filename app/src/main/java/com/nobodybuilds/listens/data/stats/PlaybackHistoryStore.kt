package com.nobodybuilds.listens.data.stats

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import timber.log.Timber
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.PlaybackEvent
import com.nobodybuilds.listens.data.stats.PlaybackStatsRepository.SkipEvent

/** File-backed playback and skip history used by [PlaybackStatsRepository]. */
internal class PlaybackHistoryStore(context: Context) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "playback_history.json")
    // Nobody Listens: plays stopped before the 30-second mark, used for the skip-rate insight.
    private val skipFile = File(context.filesDir, "skip_history.json")
    private val atomicSkipFile = AtomicFile(skipFile)
    val skipLock = Any()
    private val skipsType = object : TypeToken<MutableList<SkipEvent>>() {}.type
    private val atomicHistoryFile = AtomicFile(historyFile)
    private val fileLock = Any()
    private val eventsType = object : TypeToken<MutableList<PlaybackEvent>>() {}.type

    fun readEvents(): List<PlaybackEvent> {
        val raw = synchronized(fileLock) { readRawHistoryLocked() }
        return parseEvents(raw)
    }

    private fun readRawHistoryLocked(): String? {
        return runCatching {
            atomicHistoryFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
            .onFailure { throwable ->
                if (throwable !is FileNotFoundException) {
                    Timber.e(throwable, "Failed reading playback history")
                }
            }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseEvents(raw: String?): MutableList<PlaybackEvent> {
        if (raw.isNullOrBlank()) {
            return mutableListOf()
        }

        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)
            parseElement(element)
        }.getOrElse { throwable ->
            Timber.e(throwable, "Failed parsing playback history")
            mutableListOf()
        }
    }

    private fun parseElement(element: JsonElement?): MutableList<PlaybackEvent> {
        if (element == null || element.isJsonNull) return mutableListOf()
        if (element.isJsonArray) {
            val parsed: MutableList<PlaybackEvent> = gson.fromJson(element, eventsType)
            return parsed.mapTo(mutableListOf()) { sanitizeEvent(it) }
        }
        return mutableListOf()
    }

    fun updateEventsAtomically(
        transform: (MutableList<PlaybackEvent>) -> MutableList<PlaybackEvent>
    ): Boolean {
        repeat(MAX_FILE_UPDATE_RETRIES) {
            val rawSnapshot = synchronized(fileLock) { readRawHistoryLocked() }
            val updatedEvents = transform(parseEvents(rawSnapshot))
            val payload = serializeEvents(updatedEvents)

            val writeSucceeded = synchronized(fileLock) {
                val latestRaw = readRawHistoryLocked()
                if (latestRaw != rawSnapshot) {
                    return@synchronized false
                }
                writePayloadLocked(payload)
            }
            if (writeSucceeded) {
                return true
            }
        }

        val fallbackRawSnapshot = synchronized(fileLock) { readRawHistoryLocked() }
        val payload = serializeEvents(transform(parseEvents(fallbackRawSnapshot)))
        return synchronized(fileLock) {
            writePayloadLocked(payload)
        }
    }

    private fun serializeEvents(events: List<PlaybackEvent>): ByteArray {
        val sanitized = events.map { sanitizeEvent(it) }
        return gson.toJson(sanitized).toByteArray(Charsets.UTF_8)
    }

    private fun writePayloadLocked(payload: ByteArray): Boolean {
        var outputStream: FileOutputStream? = null
        return runCatching {
            val parent = historyFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Timber.w("Unable to ensure playback history directory: ${parent.absolutePath}")
            }
            outputStream = atomicHistoryFile.startWrite()
            outputStream?.write(payload)
            outputStream?.fd?.sync()
            outputStream?.let { atomicHistoryFile.finishWrite(it) }
            outputStream = null
            true
        }.onFailure { throwable ->
            outputStream?.let { stream -> atomicHistoryFile.failWrite(stream) }
            Timber.e(throwable, "Failed to persist playback history")
        }.getOrDefault(false)
    }

    fun readSkipsLocked(): MutableList<SkipEvent> {
        val raw = runCatching {
            atomicSkipFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        if (raw.isNullOrBlank()) return mutableListOf()
        return runCatching {
            val parsed: MutableList<SkipEvent>? = gson.fromJson(raw, skipsType)
            parsed?.filterTo(mutableListOf()) { it.songId.isNotBlank() } ?: mutableListOf()
        }.getOrElse { throwable ->
            Timber.e(throwable, "Failed parsing skip history")
            mutableListOf()
        }
    }

    fun writeSkipsLocked(skips: List<SkipEvent>): Boolean {
        var stream: FileOutputStream? = null
        return runCatching {
            stream = atomicSkipFile.startWrite()
            stream?.write(gson.toJson(skips).toByteArray(Charsets.UTF_8))
            stream?.let { atomicSkipFile.finishWrite(it) }
            stream = null
            true
        }.onFailure { throwable ->
            stream?.let { atomicSkipFile.failWrite(it) }
            Timber.e(throwable, "Failed to persist skip history")
        }.getOrDefault(false)
    }

    companion object {
        private const val MAX_FILE_UPDATE_RETRIES = 3
    }
}
