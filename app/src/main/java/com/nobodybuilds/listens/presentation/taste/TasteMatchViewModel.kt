package com.nobodybuilds.listens.presentation.taste

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.party.PartyQr
import com.nobodybuilds.listens.data.taste.SharedArtist
import com.nobodybuilds.listens.data.taste.SongRef
import com.nobodybuilds.listens.data.taste.TasteCodec
import com.nobodybuilds.listens.data.taste.TasteFingerprint
import com.nobodybuilds.listens.data.taste.TasteMatchHistoryEntry
import com.nobodybuilds.listens.data.taste.TasteMatcher
import com.nobodybuilds.listens.data.taste.TastePreferences
import com.nobodybuilds.listens.data.taste.TasteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class TasteMode { Home, Scan, Result }

data class TasteResultUi(
    val friendName: String,
    val percent: Int,
    val label: String,
    val sharedArtists: List<SharedArtist>,
    val songsToPlay: List<Song>,
    val songsMissing: List<SongRef>,
    val usedSmart: Boolean,
    val friendTopArtists: List<String>,
)

data class TasteUiState(
    val loading: Boolean = true,
    val name: String = "",
    /** False until the user has confirmed a display name once. */
    val nameConfirmed: Boolean = true,
    val myQrText: String? = null,
    val myShareText: String? = null,
    val qr: ImageBitmap? = null,
    val topArtists: List<String> = emptyList(),
    val fromListening: Boolean = true,
    val isEmpty: Boolean = false,
    val mode: TasteMode = TasteMode.Home,
    val matching: Boolean = false,
    val result: TasteResultUi? = null,
    val error: String? = null,
    val history: List<TasteMatchHistoryEntry> = emptyList(),
)

@HiltViewModel
class TasteMatchViewModel @Inject constructor(
    private val repository: TasteRepository,
    private val preferences: TastePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TasteUiState())
    val state: StateFlow<TasteUiState> = _state.asStateFlow()

    private var mine: TasteFingerprint? = null
    private var library: List<Song> = emptyList()

    init {
        viewModelScope.launch {
            preferences.history.collect { h -> _state.update { it.copy(history = h) } }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val stored = preferences.displayName.first()
                val name = stored ?: repository.defaultName()
                library = repository.librarySongs()
                val taste = repository.buildMyTaste(name, library)
                mine = taste.fingerprint
                val fp = taste.fingerprint
                val qrText = withContext(Dispatchers.Default) { TasteCodec.encodeForQr(fp) }
                val shareText = withContext(Dispatchers.Default) { TasteCodec.encodeForText(fp) }
                val qr = withContext(Dispatchers.Default) { qrBitmap(qrText) }
                _state.update {
                    it.copy(
                        loading = false,
                        name = name,
                        nameConfirmed = stored != null,
                        myQrText = qrText,
                        myShareText = shareText,
                        qr = qr,
                        topArtists = fp.artists.take(3).map { a -> a.name },
                        fromListening = taste.fromListening,
                        isEmpty = fp.artists.isEmpty() && fp.songs.isEmpty(),
                    )
                }
            }.onFailure { t ->
                Timber.w(t, "Taste: failed to build fingerprint")
                _state.update { it.copy(loading = false, isEmpty = true, error = "Couldn't read your listening history") }
            }
        }
    }

    fun setName(name: String) {
        val clean = name.trim().ifBlank { repository.defaultName() }
        viewModelScope.launch {
            preferences.setDisplayName(clean)
            val fp = mine?.copy(name = clean) ?: return@launch
            mine = fp
            val qrText = withContext(Dispatchers.Default) { TasteCodec.encodeForQr(fp) }
            val shareText = withContext(Dispatchers.Default) { TasteCodec.encodeForText(fp) }
            val qr = withContext(Dispatchers.Default) { qrBitmap(qrText) }
            _state.update { it.copy(name = clean, nameConfirmed = true, myQrText = qrText, myShareText = shareText, qr = qr) }
        }
    }

    fun openScanner() = _state.update { it.copy(mode = TasteMode.Scan, error = null) }

    fun backToHome() = _state.update { it.copy(mode = TasteMode.Home, matching = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Called with a scanned QR value or pasted text. Returns false when it isn't a taste code. */
    fun onCode(raw: String): Boolean {
        if (_state.value.matching) return true
        val theirs = try {
            TasteCodec.decode(raw)
        } catch (e: TasteCodec.InvalidCodeException) {
            _state.update { it.copy(error = e.message) }
            return false
        } catch (t: Throwable) {
            Timber.w(t, "Taste: decode failed")
            _state.update { it.copy(error = "This code looks damaged") }
            return false
        }
        val me = mine ?: run {
            _state.update { it.copy(error = "Still reading your taste, try again in a second") }
            return false
        }
        _state.update { it.copy(matching = true, error = null) }
        viewModelScope.launch {
            val ui = withContext(Dispatchers.Default) {
                val match = TasteMatcher.match(me, theirs, TasteRepository.libraryKeys(library))
                val byId = library.associateBy { it.id }
                TasteResultUi(
                    friendName = theirs.name,
                    percent = match.percent,
                    label = match.label,
                    sharedArtists = match.sharedArtists,
                    songsToPlay = match.songsInLibrary.mapNotNull { byId[it] },
                    songsMissing = match.songsMissing,
                    usedSmart = match.genreSimilarity != null || match.moodSimilarity != null,
                    friendTopArtists = theirs.artists.take(5).map { it.name },
                )
            }
            preferences.addHistory(TasteMatchHistoryEntry(ui.friendName, ui.percent, System.currentTimeMillis()))
            _state.update { it.copy(matching = false, result = ui, mode = TasteMode.Result) }
        }
        return true
    }

    private fun qrBitmap(text: String): ImageBitmap? = runCatching {
        val (w, h, px) = PartyQr.pixels(text, scale = 6, dark = 0xFF111113.toInt(), light = 0xFFFFFFFF.toInt(), margin = 2)
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.onFailure { Timber.w(it, "Taste: QR encode failed") }.getOrNull()
}
