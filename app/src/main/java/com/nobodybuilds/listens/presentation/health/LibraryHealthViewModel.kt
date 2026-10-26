package com.nobodybuilds.listens.presentation.health

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.autofill.AutofillCandidate
import com.nobodybuilds.listens.data.autofill.AutofillField
import com.nobodybuilds.listens.data.autofill.AutofillNetworkException
import com.nobodybuilds.listens.data.autofill.MetadataFields
import com.nobodybuilds.listens.data.autofill.MusicBrainzAutofill
import com.nobodybuilds.listens.data.autofill.OfflineMetadataGuesser
import com.nobodybuilds.listens.data.media.AudioMetadata
import com.nobodybuilds.listens.data.media.AudioMetadataReader
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import com.nobodybuilds.listens.data.media.ExtendedTagReader
import com.nobodybuilds.listens.data.media.ExtendedTagUpdate
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.presentation.components.AutofillResult
import com.nobodybuilds.listens.presentation.viewmodel.LibraryStateHolder
import com.nobodybuilds.listens.presentation.viewmodel.MetadataEditStateHolder
import com.nobodybuilds.listens.utils.MediaStorePermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Why batch autofill left a song alone. */
enum class SkipReason { FILE_MISSING, NO_TITLE, NO_MATCH, LOW_CONFIDENCE, NOTHING_NEW, NETWORK, SAVE_FAILED, PERMISSION_DENIED, UNREADABLE }

data class SkippedSong(val song: Song, val reason: SkipReason)

data class FixedSong(val song: Song, val fields: List<AutofillField>, val addedArtwork: Boolean)

/** A match that wasn't strong enough to apply on its own. */
data class ReviewSong(val song: Song, val confidence: Int?)

data class BatchProgress(
    val total: Int,
    val done: Int,
    val current: Song?,
)

data class BatchSummary(
    val fixed: List<FixedSong>,
    val skipped: List<SkippedSong>,
    val cancelled: Boolean,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryHealthViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder,
    private val musicRepository: MusicRepository,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        /** Same bar the autofill dialog uses for a "strong" match. */
        const val HIGH_CONFIDENCE = 80
        private const val MAX_NETWORK_FAILURES_IN_A_ROW = 3
    }

    private val fallbackSongs = MutableStateFlow<List<Song>?>(null)

    val report: StateFlow<LibraryHealthReport?> = libraryStateHolder.allSongs
        .debounce(250)
        .map { songs ->
            val source = if (songs.isEmpty()) loadFallbackSongs() else songs
            LibraryHealthAnalyzer.analyze(source)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _progress = MutableStateFlow<BatchProgress?>(null)
    val progress: StateFlow<BatchProgress?> = _progress.asStateFlow()

    private val _summary = MutableStateFlow<BatchSummary?>(null)
    val summary: StateFlow<BatchSummary?> = _summary.asStateFlow()

    private val _review = MutableStateFlow<List<ReviewSong>>(emptyList())
    val review: StateFlow<List<ReviewSong>> = _review.asStateFlow()

    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<HealthMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<HealthMessage> = _messages.asSharedFlow()

    private sealed interface PendingWrite {
        data class Batch(val songs: List<Song>) : PendingWrite
        data class Review(val song: Song, val result: AutofillResult) : PendingWrite
    }

    private var pendingWrite: PendingWrite? = null
    private var batchJob: Job? = null

    val isRunning: Boolean get() = batchJob?.isActive == true

    private suspend fun loadFallbackSongs(): List<Song> {
        fallbackSongs.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) { runCatching { musicRepository.getAllSongsOnce() }.getOrElse { emptyList() } }
        fallbackSongs.value = loaded
        return loaded
    }

    private suspend fun currentSongs(): List<Song> =
        libraryStateHolder.allSongs.value.ifEmpty { loadFallbackSongs() }

    // ---------------------------------------------------------------- batch autofill

    /** Asks for write access to all [songs] at once, then runs autofill on them one by one. */
    fun startBatch(songs: List<Song>) {
        if (isRunning) return
        val targets = songs.filter(LibraryHealthAnalyzer::isLocal).distinctBy { it.id }
        if (targets.isEmpty()) return
        _summary.value = null
        val sender = writeRequestFor(targets)
        if (sender != null) {
            pendingWrite = PendingWrite.Batch(targets)
            _writeRequests.tryEmit(sender)
        } else {
            runBatch(targets)
        }
    }

    fun onWritePermissionResult(granted: Boolean) {
        val pending = pendingWrite ?: return
        pendingWrite = null
        when (pending) {
            is PendingWrite.Batch -> if (granted) {
                runBatch(pending.songs)
            } else {
                com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().error()
                _summary.value = BatchSummary(
                    fixed = emptyList(),
                    skipped = pending.songs.map { SkippedSong(it, SkipReason.PERMISSION_DENIED) },
                    cancelled = false,
                )
            }
            is PendingWrite.Review -> if (granted) {
                viewModelScope.launch { applyReview(pending.song, pending.result) }
            } else {
                com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().error()
                _messages.tryEmit(HealthMessage.PermissionDenied)
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
    }

    fun dismissSummary() {
        _summary.value = null
    }

    private fun writeRequestFor(songs: List<Song>): IntentSender? {
        val uris = songs.mapNotNull { song ->
            song.id.toLongOrNull()?.let { MediaStorePermissionHelper.getMediaStoreUri(it) }
        }
        if (uris.isEmpty()) return null
        return runCatching { MediaStorePermissionHelper.createWriteRequestIntentSender(context, uris) }
            .onFailure { Timber.w(it, "Library health: write request failed") }
            .getOrNull()
    }

    private fun runBatch(songs: List<Song>) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val fixed = mutableListOf<FixedSong>()
            val skipped = mutableListOf<SkippedSong>()
            var cancelled = false
            var networkFailures = 0
            // Songs by folder, built once so each song's siblings are a map lookup.
            val byFolder = withContext(Dispatchers.Default) { currentSongs().groupBy { File(it.path).parent } }
            _progress.value = BatchProgress(total = songs.size, done = 0, current = songs.firstOrNull())
            try {
                for ((index, song) in songs.withIndex()) {
                    if (!isActive) break
                    _progress.value = BatchProgress(total = songs.size, done = index, current = song)
                    if (networkFailures >= MAX_NETWORK_FAILURES_IN_A_ROW) {
                        skipped += SkippedSong(song, SkipReason.NETWORK)
                        continue
                    }
                    when (val outcome = fixOne(song, byFolder)) {
                        is Outcome.Fixed -> {
                            networkFailures = 0
                            fixed += outcome.fixed
                        }
                        is Outcome.Skipped -> {
                            networkFailures = if (outcome.reason == SkipReason.NETWORK) networkFailures + 1 else 0
                            skipped += SkippedSong(song, outcome.reason)
                            if (outcome.reason == SkipReason.LOW_CONFIDENCE) addToReview(ReviewSong(song, outcome.confidence))
                        }
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                _progress.value = null
                _summary.value = BatchSummary(fixed = fixed.toList(), skipped = skipped.toList(), cancelled = cancelled)
                when {
                    cancelled -> {}
                    fixed.isNotEmpty() -> com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
                    else -> com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().error()
                }
            }
        }
    }

    private fun addToReview(item: ReviewSong) {
        _review.update { list -> (list.filterNot { it.song.id == item.song.id } + item) }
    }

    fun removeFromReview(songId: String) {
        _review.update { list -> list.filterNot { it.song.id == songId } }
    }

    private sealed interface Outcome {
        data class Fixed(val fixed: FixedSong) : Outcome
        data class Skipped(val reason: SkipReason, val confidence: Int? = null) : Outcome
    }

    /**
     * What's already in the file (read from the file itself, never from the library database),
     * so nothing that exists gets overwritten and every field we don't fill is written back as is.
     */
    private data class CurrentTags(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val year: Int?,
        val genre: String,
        val composer: String,
        val lyrics: String,
        val trackNumber: Int,
        val discNumber: Int?,
        val hasArtwork: Boolean,
    )

    private sealed interface ReadResult {
        data object Missing : ReadResult
        /** The file exists but its tags couldn't be read, so its current values are unknown. */
        data object Unreadable : ReadResult
        data class Tags(val tags: CurrentTags) : ReadResult
    }

    private fun known(vararg values: String?): String =
        values.firstOrNull { !OfflineMetadataGuesser.isUnknown(it) }?.trim().orEmpty()

    private suspend fun readCurrent(song: Song): ReadResult = withContext(Dispatchers.IO) {
        val file = File(song.path)
        if (!file.exists()) return@withContext ReadResult.Missing
        val meta: AudioMetadata = runCatching { AudioMetadataReader.read(file) }.getOrNull()
            ?: return@withContext ReadResult.Unreadable
        val extended = runCatching { ExtendedTagReader.read(file) }.getOrNull()
            ?: return@withContext ReadResult.Unreadable
        ReadResult.Tags(
            CurrentTags(
                title = meta.title.orEmpty().trim(),
                artist = meta.artist.orEmpty().trim(),
                album = meta.album.orEmpty().trim(),
                albumArtist = extended.albumArtist.ifBlank { meta.albumArtist.orEmpty() }.trim(),
                year = meta.year?.takeIf { it in 1000..9999 }
                    ?: extended.year.take(4).toIntOrNull()?.takeIf { it in 1000..9999 },
                genre = meta.genre.orEmpty().trim(),
                composer = extended.composer.trim(),
                lyrics = meta.lyrics.orEmpty(),
                trackNumber = meta.trackNumber ?: 0,
                discNumber = meta.discNumber,
                hasArtwork = meta.artwork != null || !song.albumArtUriString.isNullOrBlank(),
            )
        )
    }

    private fun isEmpty(value: String) = OfflineMetadataGuesser.isUnknown(value) || value.isBlank()

    private suspend fun fixOne(song: Song, byFolder: Map<String?, List<Song>>): Outcome {
        val current = when (val read = readCurrent(song)) {
            ReadResult.Missing -> return Outcome.Skipped(SkipReason.FILE_MISSING)
            ReadResult.Unreadable -> return Outcome.Skipped(SkipReason.UNREADABLE)
            is ReadResult.Tags -> read.tags
        }
        val siblings = byFolder[File(song.path).parent].orEmpty()
        val guess = runCatching { OfflineMetadataGuesser.guess(song, siblings) }.getOrNull()?.fields

        val queryTitle = known(current.title, song.title).ifBlank { guess?.title.orEmpty() }
        val queryArtist = known(current.artist, song.artist).ifBlank { guess?.artist.orEmpty() }
        val queryAlbum = known(current.album, song.album).ifBlank { guess?.album.orEmpty() }
        if (queryTitle.isBlank()) return Outcome.Skipped(SkipReason.NO_TITLE)

        val best: AutofillCandidate = try {
            MusicBrainzAutofill.search(
                title = queryTitle,
                artist = queryArtist,
                album = queryAlbum,
                durationMs = song.duration.takeIf { it > 0 },
                limit = 1,
            ).firstOrNull() ?: return Outcome.Skipped(SkipReason.NO_MATCH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Library health: search failed for ${song.title}")
            return Outcome.Skipped(SkipReason.NETWORK)
        }
        if (best.confidence < HIGH_CONFIDENCE) return Outcome.Skipped(SkipReason.LOW_CONFIDENCE, best.confidence)

        val detailed = try {
            MusicBrainzAutofill.loadDetails(best)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            best
        }
        val found = detailed.fields

        // Only empty fields get a value.
        val filled = mutableListOf<AutofillField>()
        fun fill(field: AutofillField, isEmpty: Boolean, value: String): String? {
            val clean = value.trim()
            return if (isEmpty && clean.isNotEmpty()) clean.also { filled += field } else null
        }
        val newTitle = fill(AutofillField.TITLE, isEmpty(current.title), found.title)
        val newArtist = fill(AutofillField.ARTIST, isEmpty(current.artist), found.artist)
        val newAlbum = fill(AutofillField.ALBUM, isEmpty(current.album), found.album)
        val newAlbumArtist = fill(AutofillField.ALBUM_ARTIST, isEmpty(current.albumArtist), found.albumArtist)
        val newYear = fill(AutofillField.YEAR, current.year == null, found.year.take(4).takeIf { it.toIntOrNull() != null }.orEmpty())
        val newGenre = fill(AutofillField.GENRE, isEmpty(current.genre), found.genre)
        val newTrack = fill(AutofillField.TRACK, current.trackNumber <= 0, found.trackNumber.takeIf { (it.toIntOrNull() ?: 0) > 0 }.orEmpty())
        val newDisc = fill(AutofillField.DISC, (current.discNumber ?: 0) <= 0, found.discNumber.takeIf { (it.toIntOrNull() ?: 0) > 0 }.orEmpty())
        val newComposer = fill(AutofillField.COMPOSER, current.composer.isBlank(), found.composer)

        val cover = if (!current.hasArtwork) {
            try {
                MusicBrainzAutofill.loadCoverArt(detailed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        if (filled.isEmpty() && cover == null) return Outcome.Skipped(SkipReason.NOTHING_NEW)

        val title = newTitle ?: current.title.ifBlank { song.title.ifBlank { File(song.path).nameWithoutExtension } }
        val result = metadataEditStateHolder.saveMetadata(
            song = song,
            newTitle = title,
            newArtist = newArtist ?: current.artist,
            newAlbum = newAlbum ?: current.album,
            newGenre = newGenre ?: current.genre,
            newLyrics = current.lyrics,
            newTrackNumber = newTrack?.toIntOrNull() ?: current.trackNumber,
            newDiscNumber = newDisc?.toIntOrNull() ?: current.discNumber,
            coverArtUpdate = cover?.let { CoverArtUpdate(bytes = it.bytes, mimeType = it.mimeType) },
            extendedTags = ExtendedTagUpdate(
                // Always pass the album artist: leaving it null makes the writer copy the artist into it.
                albumArtist = newAlbumArtist ?: current.albumArtist,
                year = newYear,
                composer = newComposer,
                musicBrainzRecordingId = detailed.recordingId,
                musicBrainzReleaseId = detailed.releaseId,
                musicBrainzArtistId = detailed.artistId,
                musicBrainzReleaseGroupId = detailed.releaseGroupId,
            ),
        )
        val updated = result.updatedSong
        return if (result.success && updated != null) {
            libraryStateHolder.updateSong(updated)
            Outcome.Fixed(FixedSong(updated, filled.toList(), addedArtwork = cover != null))
        } else {
            Timber.w("Library health: save failed for ${song.title}: ${result.error} ${result.errorMessage}")
            Outcome.Skipped(SkipReason.SAVE_FAILED)
        }
    }

    // ---------------------------------------------------------------- review (single song)

    /** The song's current values, for the single-song autofill dialog. */
    fun currentFields(song: Song): MetadataFields = MetadataFields(
        title = song.title,
        artist = song.artist.takeUnless(OfflineMetadataGuesser::isUnknown).orEmpty(),
        album = song.album.takeUnless(OfflineMetadataGuesser::isUnknown).orEmpty(),
        albumArtist = song.albumArtist.orEmpty(),
        year = song.year.takeIf { it > 0 }?.toString().orEmpty(),
        genre = song.genre.orEmpty(),
        trackNumber = song.trackNumber.takeIf { it > 0 }?.toString().orEmpty(),
        discNumber = song.discNumber?.takeIf { it > 0 }?.toString().orEmpty(),
        composer = "",
    )

    /** Applies what the user picked in the autofill dialog (their choice, so it may replace values). */
    fun applyReviewResult(song: Song, result: AutofillResult) {
        val sender = writeRequestFor(listOf(song))
        if (sender != null) {
            pendingWrite = PendingWrite.Review(song, result)
            _writeRequests.tryEmit(sender)
        } else {
            viewModelScope.launch { applyReview(song, result) }
        }
    }

    private suspend fun applyReview(song: Song, autofill: AutofillResult) {
        val current = (readCurrent(song) as? ReadResult.Tags)?.tags
        if (current == null) {
            _messages.tryEmit(HealthMessage.SaveFailed)
            return
        }
        val picked = autofill.selected
        fun value(field: AutofillField): String? =
            if (field in picked) field.valueOf(autofill.fields).trim().takeIf { it.isNotEmpty() } else null

        val candidate = autofill.candidate
        val result = metadataEditStateHolder.saveMetadata(
            song = song,
            newTitle = value(AutofillField.TITLE) ?: current.title.ifBlank { song.title.ifBlank { File(song.path).nameWithoutExtension } },
            newArtist = value(AutofillField.ARTIST) ?: current.artist,
            newAlbum = value(AutofillField.ALBUM) ?: current.album,
            newGenre = value(AutofillField.GENRE) ?: current.genre,
            newLyrics = current.lyrics,
            newTrackNumber = value(AutofillField.TRACK)?.toIntOrNull() ?: current.trackNumber,
            newDiscNumber = value(AutofillField.DISC)?.toIntOrNull() ?: current.discNumber,
            coverArtUpdate = autofill.coverArt?.let { CoverArtUpdate(bytes = it.bytes, mimeType = it.mimeType) },
            extendedTags = ExtendedTagUpdate(
                albumArtist = value(AutofillField.ALBUM_ARTIST) ?: current.albumArtist,
                year = value(AutofillField.YEAR),
                composer = value(AutofillField.COMPOSER),
                musicBrainzRecordingId = candidate.recordingId,
                musicBrainzReleaseId = candidate.releaseId,
                musicBrainzArtistId = candidate.artistId,
                musicBrainzReleaseGroupId = candidate.releaseGroupId,
            ),
        )
        val updated = result.updatedSong
        if (result.success && updated != null) {
            libraryStateHolder.updateSong(updated)
            removeFromReview(song.id)
            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
            _messages.tryEmit(HealthMessage.Saved)
        } else {
            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().error()
            _messages.tryEmit(HealthMessage.SaveFailed)
        }
    }

    override fun onCleared() {
        batchJob?.cancel()
        super.onCleared()
    }
}

enum class HealthMessage { Saved, SaveFailed, PermissionDenied }
