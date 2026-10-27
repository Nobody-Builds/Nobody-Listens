package com.nobodybuilds.listens.presentation.jiosaavn.dashboard

import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnCollection
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnDownloader
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnItemKind
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnLibrary
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnRepository
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnShelf
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnTrack
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.stream.StreamingPlaylists
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.presentation.streaming.StreamingDashboardViewModel
import com.nobodybuilds.listens.presentation.streaming.StreamingPage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class JioSaavnDashboardViewModel @Inject constructor(
    private val repository: JioSaavnRepository,
    private val downloader: JioSaavnDownloader,
    private val library: JioSaavnLibrary,
    private val playlists: StreamingPlaylists,
) : StreamingDashboardViewModel<JioSaavnTrack, JioSaavnCollection>(
    serviceName = JioSaavnUris.SERVICE_NAME,
    downloadStatus = downloader.status,
    clearDownloadStatus = downloader::clearStatus,
) {

    override val isLoggedIn: StateFlow<Boolean> = repository.session
        .map { it.isLoggedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.isLoggedIn)

    override val accountName: StateFlow<String> = repository.session
        .map { it.accountName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.accountName.orEmpty())

    override val loggedInNow: Boolean
        get() = repository.isLoggedIn

    override suspend fun loadFeed(): List<StreamingShelf<JioSaavnTrack, JioSaavnCollection>> = repository.home()

    override suspend fun loadLibrary(): List<StreamingShelf<JioSaavnTrack, JioSaavnCollection>> = repository.library()

    override suspend fun loadSearch(query: String): List<StreamingShelf<JioSaavnTrack, JioSaavnCollection>> {
        val (type, token) = JioSaavnUris.linkFrom(query) ?: return repository.search(query)
        val page = repository.linkPage(type, token)
        val tracks = page.shelves.flatMap { it.tracks }
        return listOf(JioSaavnShelf(title = page.title.orEmpty(), tracks = tracks))
    }

    override suspend fun loadPage(collection: JioSaavnCollection): StreamingPage<JioSaavnTrack, JioSaavnCollection> {
        val page = when (collection.kind) {
            JioSaavnItemKind.ARTIST -> return repository.artist(collection.id).let {
                StreamingPage(title = it.title.orEmpty(), shelves = it.shelves)
            }
            JioSaavnItemKind.ALBUM -> repository.album(collection.id)
            JioSaavnItemKind.PLAYLIST -> repository.playlist(collection.id)
        }
        return StreamingPage(title = page.title.orEmpty(), tracks = page.shelves.flatMap { it.tracks }, source = collection)
    }

    override suspend fun loadSuggestions(input: String): List<String> = repository.searchSuggestions(input)

    override fun signOut() = repository.logout()

    override fun prepareQueue(tracks: List<JioSaavnTrack>, start: JioSaavnTrack) {
        library.remember(tracks)
        viewModelScope.launch { library.saveIfQueued(start.id) }
    }

    override fun download(tracks: List<JioSaavnTrack>) = downloader.enqueue(tracks)

    override suspend fun importAsPlaylist(collection: JioSaavnCollection, title: String, tracks: List<JioSaavnTrack>): Int {
        val songIds = library.saveAll(tracks)
        playlists.importOrUpdate(StreamingPlaylists.jioSaavnId(collection), title, songIds, source = "JIOSAAVN")
        return songIds.size
    }
}
