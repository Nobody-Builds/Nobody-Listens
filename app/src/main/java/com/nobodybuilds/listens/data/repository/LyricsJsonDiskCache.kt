package com.nobodybuilds.listens.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nobodybuilds.listens.data.database.LyricsDao
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.LyricsRepositoryLrcFormat.formatTimestamp
import com.nobodybuilds.listens.data.repository.LyricsRepositoryLrcFormat.looksLikeFlattenedWordByWordCache
import com.nobodybuilds.listens.utils.LyricsUtils
import java.io.File

/**
 * LyricsData for JSON disk cache (matches Rhythm's format)
 */
internal data class LyricsData(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val wordByWordLyrics: String? = null
) {
    fun hasLyrics(): Boolean =
        !plainLyrics.isNullOrBlank() ||
            !syncedLyrics.isNullOrBlank() ||
            !wordByWordLyrics.isNullOrBlank()
}

/** Fetched lyrics saved as JSON under filesDir/lyrics (matches Rhythm's format). */
internal class LyricsJsonDiskCache(
    private val context: Context,
    private val lyricsDao: LyricsDao,
) {

    companion object {
        private const val TAG = "LyricsRepository"
    }

    // Gson for JSON cache
    private val gson = Gson()

    /**
     * Save lyrics to JSON disk cache (matching Rhythm)
     */
    fun saveLocalLyricsJson(song: Song, lyrics: Lyrics) {
        try {
            val fileName = "${song.id}.json"
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val wordByWordLyrics = lyrics.synced
                ?.takeIf { lines -> lines.any { !it.words.isNullOrEmpty() } }
                ?.let(LyricsRepositoryLrcFormat::toWordByWordLrc)

            val lyricsData = LyricsData(
                plainLyrics = lyrics.plain?.joinToString("\n"),
                syncedLyrics = lyrics.synced?.joinToString("\n") { "[${formatTimestamp(it.time)}]${it.line}" },
                wordByWordLyrics = wordByWordLyrics
            )

            val file = File(lyricsDir, fileName)
            val json = gson.toJson(lyricsData)
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to JSON cache: ${e.message}", e)
        }
    }

    /**
     * Load lyrics from JSON disk cache (matching Rhythm)
     */
    suspend fun loadLocalLyricsJson(song: Song): Lyrics? {
        try {
            val data = readLyricsJsonCache(song) ?: return null
            if (data.hasLyrics()) {
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                val parsed = LyricsUtils.parseLyrics(rawLyrics)
                if (parsed.isValid()) {
                    val hasWordTimestamps = parsed.synced?.any { !it.words.isNullOrEmpty() } == true
                    if (!hasWordTimestamps && data.wordByWordLyrics.isNullOrBlank()) {
                        // Legacy cache may have flattened word-by-word lines.
                        // Recover richer raw lyrics from DB when available.
                        val persistedContent = song.id.toLongOrNull()
                            ?.let { lyricsDao.getLyrics(it)?.content }
                            ?.takeIf { it.isNotBlank() }
                        if (persistedContent != null) {
                            val recovered = LyricsUtils.parseLyrics(persistedContent)
                            val recoveredHasWords = recovered.synced?.any { !it.words.isNullOrEmpty() } == true
                            if (recovered.isValid() && recoveredHasWords) {
                                saveLocalLyricsJson(song, recovered)
                                return recovered
                            }
                        }

                        if (looksLikeFlattenedWordByWordCache(parsed)) {
                            // Force a remote re-fetch instead of serving degraded cache.
                            return null
                        }
                    }
                    return parsed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON cache: ${e.message}", e)
        }
        return null
    }

    fun readLyricsJsonCache(song: Song): LyricsData? {
        val fileName = "${song.id}.json"
        val file = File(context.filesDir, "lyrics/$fileName")
        if (!file.exists()) return null

        val json = file.readText()
        return gson.fromJson(json, LyricsData::class.java)
    }
}
