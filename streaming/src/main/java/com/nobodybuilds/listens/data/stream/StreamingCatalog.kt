package com.nobodybuilds.listens.data.stream

/**
 * What the shared streaming dashboard needs from a catalog service's songs, playlists
 * and shelves. YouTube Music and JioSaavn models implement these.
 */
interface StreamingTrack {
    /** The service's own id for the song, unique within its catalog. */
    val key: String
    val title: String
    val artist: String
    val thumbnailUrl: String?
}

/** A playlist, album or artist that opens as its own page. */
interface StreamingCollection {
    val key: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?
    /** Only playlists and albums can be imported as Nobody Listens playlists. */
    val importable: Boolean
}

interface StreamingShelf<T : StreamingTrack, C : StreamingCollection> {
    val title: String
    val tracks: List<T>
    val playlists: List<C>
}
