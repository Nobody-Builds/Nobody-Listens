package com.nobodybuilds.listens.presentation.ytmusic.dashboard

import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.stream.StreamingPlaylists
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicDownloader
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicItemKind
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicLibrary
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicPlaylist
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
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
class YouTubeMusicDashboardViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val downloader: YouTubeMusicDownloader,
    private val library: YouTubeMusicLibrary,
    private val playlists: StreamingPlaylists,
) : StreamingDashboardViewModel<YouTubeMusicTrack, YouTubeMusicPlaylist>(
    serviceName = "YouTube Music",
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

    override suspend fun loadFeed(): List<StreamingShelf<YouTubeMusicTrack, YouTubeMusicPlaylist>> =
        repository.home().shelves

    override suspend fun loadLibrary(): List<StreamingShelf<YouTubeMusicTrack, YouTubeMusicPlaylist>> =
        repository.library().shelves

    override suspend fun loadSearch(query: String): List<StreamingShelf<YouTubeMusicTrack, YouTubeMusicPlaylist>> {
        val linkedVideoId = YouTubeMusicUris.videoIdFromLink(query)
        return (if (linkedVideoId != null) repository.trackPage(linkedVideoId) else repository.search(query)).shelves
    }

    override suspend fun loadPage(collection: YouTubeMusicPlaylist): StreamingPage<YouTubeMusicTrack, YouTubeMusicPlaylist> {
        val id = collection.playlistId
        if (collection.kind == YouTubeMusicItemKind.ARTIST) {
            val page = repository.artist(id)
            return StreamingPage(title = page.title.orEmpty(), shelves = page.shelves)
        }
        val page = if (collection.kind == YouTubeMusicItemKind.ALBUM) repository.album(id) else repository.playlist(id)
        return StreamingPage(title = page.title.orEmpty(), tracks = page.shelves.flatMap { it.tracks }, source = collection)
    }

    override suspend fun loadSuggestions(input: String): List<String> = repository.searchSuggestions(input)

    override fun signOut() = repository.logout()

    override fun prepareQueue(tracks: List<YouTubeMusicTrack>, start: YouTubeMusicTrack) {
        library.remember(tracks)
        viewModelScope.launch { library.saveIfQueued(start.videoId) }
    }

    override fun download(tracks: List<YouTubeMusicTrack>) = downloader.enqueue(tracks)

    override suspend fun importAsPlaylist(collection: YouTubeMusicPlaylist, title: String, tracks: List<YouTubeMusicTrack>): Int {
        val songIds = library.saveAll(tracks)
        playlists.importOrUpdate("ytmusic_${collection.playlistId}", title, songIds, source = "YTMUSIC")
        return songIds.size
    }
}
