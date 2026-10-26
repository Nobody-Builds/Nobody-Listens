package com.nobodybuilds.listens.presentation.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.stream.StreamingCollection
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.stream.StreamingTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StreamingTab {
    FEED,
    LIBRARY,
    SEARCH,
}

/** An open playlist, album or artist page: a song list, or shelves for an artist. */
data class StreamingPage<T : StreamingTrack, C : StreamingCollection>(
    val title: String,
    val tracks: List<T> = emptyList(),
    val shelves: List<StreamingShelf<T, C>> = emptyList(),
    /** The playlist or album shown, when it can be imported as a Nobody Listens playlist. */
    val source: C? = null,
)

data class StreamingDashboardState<T : StreamingTrack, C : StreamingCollection>(
    val tab: StreamingTab = StreamingTab.FEED,
    val loading: Boolean = false,
    val error: String? = null,
    val feed: List<StreamingShelf<T, C>> = emptyList(),
    val library: List<StreamingShelf<T, C>> = emptyList(),
    val searchResults: List<StreamingShelf<T, C>> = emptyList(),
    val openPage: StreamingPage<T, C>? = null,
    /** Search suggestions for what is being typed. */
    val suggestions: List<String> = emptyList(),
    val status: String? = null,
    val loggedOut: Boolean = false,
)

/**
 * Feed, library and search of a streaming service, with playlist, album and artist
 * pages that back returns through. Each service supplies the calls; the paging,
 * suggestions, downloads and imports work the same for all of them.
 */
abstract class StreamingDashboardViewModel<T : StreamingTrack, C : StreamingCollection>(
    /** Shown in errors and as the queue name, e.g. "YouTube Music". */
    val serviceName: String,
    private val downloadStatus: StateFlow<String?>,
    private val clearDownloadStatus: () -> Unit,
) : ViewModel() {

    abstract val isLoggedIn: StateFlow<Boolean>
    abstract val accountName: StateFlow<String>

    protected abstract val loggedInNow: Boolean
    protected abstract suspend fun loadFeed(): List<StreamingShelf<T, C>>
    protected abstract suspend fun loadLibrary(): List<StreamingShelf<T, C>>
    protected abstract suspend fun loadSearch(query: String): List<StreamingShelf<T, C>>
    protected abstract suspend fun loadPage(collection: C): StreamingPage<T, C>
    protected abstract suspend fun loadSuggestions(input: String): List<String>
    protected abstract fun signOut()

    /** Remembers the queue; each song is saved to the library when it starts to play. */
    abstract fun prepareQueue(tracks: List<T>, start: T)
    abstract fun download(tracks: List<T>)

    /** Saves [tracks] and makes them a Nobody Listens playlist named [title]; returns how many were added. */
    protected abstract suspend fun importAsPlaylist(collection: C, title: String, tracks: List<T>): Int

    private val _state = MutableStateFlow(StreamingDashboardState<T, C>())
    val state: StateFlow<StreamingDashboardState<T, C>> = combine(_state, downloadStatus) { state, download ->
        state.copy(status = download ?: state.status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamingDashboardState())

    /** Pages opened from a list, so back returns to the one before (artist → album → back). */
    private val pageStack = ArrayDeque<StreamingPage<T, C>>()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            downloadStatus.collect { status ->
                if (status != null && (status.startsWith("Saved") || status.contains("failed", ignoreCase = true))) {
                    delay(3000)
                    clearDownloadStatus()
                }
            }
        }
    }

    fun loadFeedTab() {
        refresh(StreamingTab.FEED)
    }

    fun reload() {
        val tab = _state.value.tab
        if (tab == StreamingTab.SEARCH) return
        if (tab == StreamingTab.LIBRARY && !loggedInNow) return
        refresh(tab)
    }

    fun selectTab(tab: StreamingTab) {
        pageStack.clear()
        _state.update { it.copy(tab = tab, openPage = null, error = null) }
        when (tab) {
            StreamingTab.FEED -> if (_state.value.feed.isEmpty()) refresh(tab)
            StreamingTab.LIBRARY -> if (_state.value.library.isEmpty() && loggedInNow) refresh(tab)
            StreamingTab.SEARCH -> Unit
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            suggestJob?.cancel()
            pageStack.clear()
            _state.update {
                it.copy(tab = StreamingTab.SEARCH, loading = true, error = null, openPage = null, suggestions = emptyList())
            }
            runCatching { loadSearch(trimmed) }
                .onSuccess { shelves -> _state.update { it.copy(loading = false, searchResults = shelves) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message ?: "Search failed") } }
        }
    }

    fun openCollection(collection: C) {
        // Keep the page being left so back can return to it.
        _state.value.openPage?.let(pageStack::addLast)
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, openPage = StreamingPage(title = collection.title)) }
            runCatching { loadPage(collection) }
                .onSuccess { page ->
                    val title = page.title.ifBlank { collection.title }
                    _state.update { it.copy(loading = false, openPage = page.copy(title = title)) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "Could not open ${collection.title}") }
                }
        }
    }

    /** Back from an open page: to the previous page, or to the tab. */
    fun closePage() {
        val previous = pageStack.removeLastOrNull()
        _state.update { it.copy(openPage = previous, error = null) }
    }

    /** Suggestions while typing in search. */
    fun suggest(input: String) {
        suggestJob?.cancel()
        val trimmed = input.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            val suggestions = runCatching { loadSuggestions(trimmed) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = suggestions.take(6)) }
        }
    }

    /** Adds the open playlist or album to Nobody Listens playlists; importing it again refreshes it. */
    fun importOpenPage() {
        val page = _state.value.openPage ?: return
        val collection = page.source ?: return
        if (page.tracks.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(status = "Importing ${page.title}…") }
            val message = runCatching { importAsPlaylist(collection, page.title, page.tracks.distinctBy { it.key }) }
                .fold(
                    onSuccess = { count -> "Imported ${page.title} · $count songs" },
                    onFailure = { error -> "Import failed: ${error.message ?: page.title}" },
                )
            _state.update { it.copy(status = message) }
            delay(3000)
            _state.update { if (it.status == message) it.copy(status = null) else it }
        }
    }

    fun logout() {
        signOut()
        _state.value = StreamingDashboardState(loggedOut = true)
    }

    private fun refresh(tab: StreamingTab) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, tab = tab) }
            runCatching { if (tab == StreamingTab.LIBRARY) loadLibrary() else loadFeed() }
                .onSuccess { shelves ->
                    _state.update {
                        if (tab == StreamingTab.LIBRARY) it.copy(loading = false, library = shelves)
                        else it.copy(loading = false, feed = shelves)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "Could not load $serviceName") }
                }
        }
    }
}
