package com.nobodybuilds.listens.presentation.viewmodel

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.SortOption

internal fun findFolder(
    targetPath: String,
    folders: List<com.nobodybuilds.listens.data.model.MusicFolder>
): com.nobodybuilds.listens.data.model.MusicFolder? {
    val queue: ArrayDeque<com.nobodybuilds.listens.data.model.MusicFolder> = ArrayDeque(folders)
    while (queue.isNotEmpty()) {
        val folder = queue.removeFirst()
        if (folder.path == targetPath) {
            return folder
        }
        folder.subFolders.forEach { queue.addLast(it) }
    }
    return null
}

internal fun com.nobodybuilds.listens.data.model.MusicFolder.collectAllSongs(): List<Song> {
    return songs + subFolders.flatMap { it.collectAllSongs() }
}

internal fun applySortToSongs(songs: List<Song>, sortOption: SortOption): List<Song> {
    return sortSongsList(songs, sortOption)
}

internal fun sortPlaylistsList(
    playlists: List<com.nobodybuilds.listens.data.model.Playlist>,
    sortOption: SortOption
): List<com.nobodybuilds.listens.data.model.Playlist> {
    return when (sortOption) {
        SortOption.PlaylistNameAZ -> playlists.sortedWith(
            compareBy<com.nobodybuilds.listens.data.model.Playlist> { it.name.lowercase() }
                .thenByDescending { it.lastModified }
                .thenBy { it.id }
        )
        SortOption.PlaylistNameZA -> playlists.sortedWith(
            compareByDescending<com.nobodybuilds.listens.data.model.Playlist> { it.name.lowercase() }
                .thenByDescending { it.lastModified }
                .thenBy { it.id }
        )
        SortOption.PlaylistDateCreated -> playlists.sortedWith(
            compareByDescending<com.nobodybuilds.listens.data.model.Playlist> { it.lastModified }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        SortOption.PlaylistDateCreatedAsc -> playlists.sortedWith(
            compareBy<com.nobodybuilds.listens.data.model.Playlist> { it.lastModified }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        else -> playlists.sortedWith(
            compareBy<com.nobodybuilds.listens.data.model.Playlist> { it.name.lowercase() }
                .thenByDescending { it.lastModified }
                .thenBy { it.id }
        )
    }
}

internal fun sortSongsList(
    songs: List<Song>,
    sortOption: SortOption
): List<Song> {
    return when (sortOption) {
        SortOption.SongTitleAZ -> songs.sortedWith(
            compareBy<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongTitleZA -> songs.sortedWith(
            compareByDescending<Song> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongArtist -> songs.sortedWith(
            compareBy<Song> { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongArtistDesc -> songs.sortedWith(
            compareByDescending<Song> { it.artist.lowercase() }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongAlbum -> songs.sortedWith(
            compareBy<Song> { it.album.lowercase() }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongAlbumDesc -> songs.sortedWith(
            compareByDescending<Song> { it.album.lowercase() }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongDuration -> songs.sortedWith(
            compareByDescending<Song> { it.duration }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongDurationAsc -> songs.sortedWith(
            compareBy<Song> { it.duration }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongDateAdded -> songs.sortedWith(
            compareByDescending<Song> { it.dateAdded }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        SortOption.SongDateAddedAsc -> songs.sortedWith(
            compareBy<Song> { it.dateAdded }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
        else -> songs
    }
}
