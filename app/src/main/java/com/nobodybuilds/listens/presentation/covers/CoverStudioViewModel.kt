package com.nobodybuilds.listens.presentation.covers

import android.content.Context
import android.content.IntentSender
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.autofill.OfflineMetadataGuesser
import com.nobodybuilds.listens.data.covers.CoverGenerator
import com.nobodybuilds.listens.data.covers.CoverPaletteChoice
import com.nobodybuilds.listens.data.covers.CoverRenderer
import com.nobodybuilds.listens.data.covers.CoverRequest
import com.nobodybuilds.listens.data.covers.CoverStyle
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics
import com.nobodybuilds.listens.presentation.health.HealthSection
import com.nobodybuilds.listens.presentation.health.LibraryHealthAnalyzer
import com.nobodybuilds.listens.presentation.viewmodel.LibraryStateHolder
import com.nobodybuilds.listens.utils.MediaStorePermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Songs that share one cover: an album, or a single song without an album. */
data class CoverAlbum(
    val id: String,
    /** Seed key: album name, or the title when there is no album. */
    val key: String,
    val label: String,
    val artist: String,
    val songs: List<Song>,
) {
    val artModel: String? get() = songs.firstNotNullOfOrNull { it.albumArtUriString?.takeIf(String::isNotBlank) }
    val missingArtwork: Boolean get() = songs.any { LibraryHealthAnalyzer.needsFix(it, HealthSection.MISSING_ARTWORK) }
}

enum class CoverStudioMode { PICK, SINGLE, BATCH }

data class CoverOptions(
    /** null = Mixed (batch only). */
    val style: CoverStyle? = CoverStyle.SHAPES,
    val palette: CoverPaletteChoice = CoverPaletteChoice.AUTO,
    val variant: Int = 0,
    val showText: Boolean = true,
)

data class CoverProgress(val total: Int, val done: Int, val current: Song?)

data class CoverSummary(
    val saved: Int,
    val skipped: List<Pair<Song, CoverSkipReason>>,
    val cancelled: Boolean,
)

data class BatchPreview(val album: CoverAlbum, val image: ImageBitmap)

data class CoverStudioState(
    val mode: CoverStudioMode = CoverStudioMode.PICK,
    val loading: Boolean = true,
    val albums: List<CoverAlbum> = emptyList(),
    val showAllInPicker: Boolean = false,
    /** Single mode: the album (or lone song) being designed for. */
    val target: CoverAlbum? = null,
    /** Single mode: the song the studio was opened for, if any. */
    val song: Song? = null,
    val applyToAlbum: Boolean = true,
    val options: CoverOptions = CoverOptions(),
    val preview: ImageBitmap? = null,
    val batchAlbums: List<CoverAlbum> = emptyList(),
    val batchPreviews: List<BatchPreview> = emptyList(),
    val renderingPreview: Boolean = false,
    val progress: CoverProgress? = null,
    val summary: CoverSummary? = null,
) {
    val missingAlbums: List<CoverAlbum> get() = albums.filter { it.missingArtwork }
    val batchSongCount: Int get() = batchAlbums.sumOf { it.songs.size }

    /** Songs a single-mode save will write. */
    val singleTargets: List<Song>
        get() {
            val album = target ?: return emptyList()
            val lone = song
            return if (lone != null && !applyToAlbum) listOf(lone) else album.songs
        }
}

@HiltViewModel
class CoverStudioViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder,
    private val musicRepository: MusicRepository,
    private val coverWriter: CoverWriter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        const val BATCH_PREVIEW_COUNT = 12
        private const val PREVIEW_PX = 720
        private const val GRID_PX = 320
    }

    private val _state = MutableStateFlow(CoverStudioState())
    val state: StateFlow<CoverStudioState> = _state.asStateFlow()

    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    private var started = false
    private var entryMode: CoverStudioMode = CoverStudioMode.PICK
    private var previewJob: Job? = null
    private var saveJob: Job? = null
    private var pendingSave: PendingSave? = null

    /** Albums (or lone songs) and the songs each should get a cover for. */
    private data class PendingSave(val groups: List<Pair<CoverAlbum, List<Song>>>, val options: CoverOptions, val keepExisting: Boolean)

    val isSaving: Boolean get() = saveJob?.isActive == true

    // ---------------------------------------------------------------- set-up

    /** @param mode "batch", "pick" or null; [songId] opens the studio for one song. */
    fun start(mode: String?, songId: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            val songs = loadSongs()
            val albums = withContext(Dispatchers.Default) { group(songs) }
            _state.update { it.copy(loading = false, albums = albums) }
            when {
                songId != null -> {
                    val song = songs.firstOrNull { it.id == songId }
                    entryMode = CoverStudioMode.SINGLE
                    if (song != null) openSong(song) else _state.update { it.copy(mode = CoverStudioMode.PICK) }
                }
                mode == "batch" -> {
                    entryMode = CoverStudioMode.BATCH
                    openBatch()
                }
                else -> _state.update { it.copy(mode = CoverStudioMode.PICK) }
            }
        }
    }

    private suspend fun loadSongs(): List<Song> {
        val inMemory = libraryStateHolder.allSongs.value
        val all = inMemory.ifEmpty {
            withContext(Dispatchers.IO) { runCatching { musicRepository.getAllSongsOnce() }.getOrElse { emptyList() } }
        }
        return all.filter(LibraryHealthAnalyzer::isLocal)
    }

    private fun group(songs: List<Song>): List<CoverAlbum> {
        val byAlbum = songs.groupBy { song ->
            if (!OfflineMetadataGuesser.isUnknown(song.album) && song.album.isNotBlank()) {
                if (song.albumId > 0) "a:${song.albumId}" else "n:${song.album.trim().lowercase()}"
            } else {
                "s:${song.id}"
            }
        }
        return byAlbum.map { (id, list) ->
            val first = list.first()
            val hasAlbum = id.startsWith("a:") || id.startsWith("n:")
            val label = if (hasAlbum) first.album.trim() else first.title.ifBlank { first.path.substringAfterLast('/') }
            val artist = (first.albumArtist?.takeUnless(OfflineMetadataGuesser::isUnknown) ?: first.displayArtist)
                .takeUnless(OfflineMetadataGuesser::isUnknown).orEmpty()
            CoverAlbum(
                id = id,
                key = label,
                label = label,
                artist = artist,
                songs = list.sortedWith(compareBy<Song>({ it.discNumber ?: 0 }, { it.trackNumber }, { it.title })),
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun setShowAllInPicker(all: Boolean) = _state.update { it.copy(showAllInPicker = all) }

    fun openAlbum(album: CoverAlbum) {
        _state.update {
            it.copy(
                mode = CoverStudioMode.SINGLE, target = album, song = null, applyToAlbum = true,
                options = it.options.copy(style = it.options.style ?: CoverStyle.SHAPES, variant = 0),
                preview = null, summary = null,
            )
        }
        refreshPreview()
    }

    private fun openSong(song: Song) {
        val album = _state.value.albums.firstOrNull { a -> a.songs.any { it.id == song.id } }
            ?: CoverAlbum("s:${song.id}", song.title, song.title, song.displayArtist, listOf(song))
        _state.update {
            it.copy(
                mode = CoverStudioMode.SINGLE, target = album, song = song,
                // Default to the whole album when none of its songs has artwork yet.
                applyToAlbum = album.songs.size > 1 && album.songs.all { s -> LibraryHealthAnalyzer.needsFix(s, HealthSection.MISSING_ARTWORK) },
                preview = null, summary = null,
            )
        }
        refreshPreview()
    }

    fun openBatch() {
        _state.update {
            it.copy(
                mode = CoverStudioMode.BATCH,
                batchAlbums = it.missingAlbums,
                options = it.options.copy(style = null, variant = 0),
                batchPreviews = emptyList(),
                summary = null,
            )
        }
        refreshPreview()
    }

    /** Back inside the studio: editor → picker. Returns false when the screen should close. */
    fun back(): Boolean {
        val s = _state.value
        if (isSaving) return true
        return if (s.mode != CoverStudioMode.PICK && entryMode == CoverStudioMode.PICK) {
            _state.update { it.copy(mode = CoverStudioMode.PICK, target = null, preview = null, batchPreviews = emptyList(), summary = null) }
            true
        } else false
    }

    // ---------------------------------------------------------------- options

    fun setStyle(style: CoverStyle?) = updateOptions { it.copy(style = style) }
    fun setPalette(palette: CoverPaletteChoice) = updateOptions { it.copy(palette = palette) }
    fun setShowText(show: Boolean) = updateOptions { it.copy(showText = show) }
    fun shuffle() = updateOptions { it.copy(variant = it.variant + 1) }
    fun setApplyToAlbum(apply: Boolean) = _state.update { it.copy(applyToAlbum = apply) }

    private fun updateOptions(change: (CoverOptions) -> CoverOptions) {
        _state.update { it.copy(options = change(it.options), summary = null) }
        refreshPreview()
    }

    private fun request(album: CoverAlbum, options: CoverOptions) = CoverRequest(
        key = album.key,
        label = album.label,
        style = options.style,
        palette = options.palette,
        variant = options.variant,
        showText = options.showText,
    )

    private fun refreshPreview() {
        previewJob?.cancel()
        val s = _state.value
        _state.update { it.copy(renderingPreview = true) }
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                when (s.mode) {
                    CoverStudioMode.SINGLE -> {
                        val album = s.target ?: return@launch
                        val design = CoverGenerator.design(request(album, s.options))
                        val image = CoverRenderer.render(context, design, PREVIEW_PX).asImageBitmap()
                        _state.update { it.copy(preview = image) }
                    }
                    CoverStudioMode.BATCH -> {
                        val previews = mutableListOf<BatchPreview>()
                        for (album in s.batchAlbums.take(BATCH_PREVIEW_COUNT)) {
                            if (!isActive) return@launch
                            val design = CoverGenerator.design(request(album, s.options))
                            previews += BatchPreview(album, CoverRenderer.render(context, design, GRID_PX).asImageBitmap())
                            // Show them as they come in.
                            _state.update { it.copy(batchPreviews = previews.toList()) }
                        }
                    }
                    CoverStudioMode.PICK -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Cover studio: preview failed")
            } finally {
                if (isActive) _state.update { it.copy(renderingPreview = false) }
            }
        }
    }

    // ---------------------------------------------------------------- saving

    fun saveSingle() {
        val s = _state.value
        val album = s.target ?: return
        val songs = s.singleTargets
        if (songs.isEmpty()) return
        // Picked by the user for this song/album, so it may replace artwork already in the file.
        requestSave(PendingSave(listOf(album to songs), s.options, keepExisting = false))
    }

    fun saveAll() {
        val s = _state.value
        if (s.batchAlbums.isEmpty()) return
        requestSave(PendingSave(s.batchAlbums.map { it to it.songs }, s.options, keepExisting = true))
    }

    private fun requestSave(save: PendingSave) {
        if (isSaving) return
        _state.update { it.copy(summary = null) }
        val uris = save.groups.flatMap { it.second }.mapNotNull { song ->
            song.id.toLongOrNull()?.let { MediaStorePermissionHelper.getMediaStoreUri(it) }
        }
        val sender = if (uris.isEmpty()) null else runCatching {
            MediaStorePermissionHelper.createWriteRequestIntentSender(context, uris)
        }.onFailure { Timber.w(it, "Cover studio: write request failed") }.getOrNull()
        if (sender != null) {
            pendingSave = save
            _writeRequests.tryEmit(sender)
        } else {
            runSave(save)
        }
    }

    fun onWritePermissionResult(granted: Boolean) {
        val save = pendingSave ?: return
        pendingSave = null
        if (granted) {
            runSave(save)
        } else {
            NobodyListensHaptics.get().error()
            _state.update {
                it.copy(
                    summary = CoverSummary(
                        saved = 0,
                        skipped = save.groups.flatMap { g -> g.second }.map { song -> song to CoverSkipReason.PERMISSION_DENIED },
                        cancelled = false,
                    )
                )
            }
        }
    }

    fun cancelSave() {
        saveJob?.cancel()
    }

    fun dismissSummary() = _state.update { it.copy(summary = null) }

    private fun runSave(save: PendingSave) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val total = save.groups.sumOf { it.second.size }
            var done = 0
            var saved = 0
            val skipped = mutableListOf<Pair<Song, CoverSkipReason>>()
            var cancelled = false
            _state.update { it.copy(progress = CoverProgress(total, 0, save.groups.firstOrNull()?.second?.firstOrNull())) }
            try {
                for ((album, songs) in save.groups) {
                    if (!isActive) break
                    // One render per album, so every song of it gets the very same cover.
                    val jpeg = withContext(Dispatchers.Default) {
                        runCatching { CoverRenderer.renderJpeg(context, CoverGenerator.design(request(album, save.options))) }
                            .onFailure { if (it is CancellationException) throw it; Timber.w(it, "Cover studio: render failed") }
                            .getOrNull()
                    }
                    for (song in songs) {
                        if (!isActive) break
                        _state.update { it.copy(progress = CoverProgress(total, done, song)) }
                        val outcome = if (jpeg == null) {
                            CoverWriteOutcome.Skipped(CoverSkipReason.SAVE_FAILED)
                        } else {
                            coverWriter.write(song, jpeg, keepExisting = save.keepExisting)
                        }
                        when (outcome) {
                            is CoverWriteOutcome.Saved -> saved++
                            is CoverWriteOutcome.Skipped -> skipped += song to outcome.reason
                        }
                        done++
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                val finalSummary = CoverSummary(saved = saved, skipped = skipped.toList(), cancelled = cancelled)
                val latest = libraryStateHolder.allSongs.value.associateBy { it.id }
                _state.update { st ->
                    val refreshed = st.albums.map { a ->
                        a.copy(songs = a.songs.map { song -> latest[song.id] ?: song })
                    }
                    st.copy(
                        progress = null,
                        summary = finalSummary,
                        albums = refreshed,
                        target = st.target?.let { t -> refreshed.firstOrNull { it.id == t.id } ?: t },
                    )
                }
                when {
                    cancelled -> Unit
                    saved > 0 -> NobodyListensHaptics.get().success()
                    else -> NobodyListensHaptics.get().error()
                }
            }
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        saveJob?.cancel()
        super.onCleared()
    }
}
