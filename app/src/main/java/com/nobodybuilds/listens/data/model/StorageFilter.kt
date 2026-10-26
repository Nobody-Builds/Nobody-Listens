package com.nobodybuilds.listens.data.model

enum class StorageFilter(val value: Int) {
    ALL(0),
    OFFLINE(1),
    ONLINE(2),
    /** Phone files saved from YouTube Music or JioSaavn (Music/Nobody Listens/YouTube Music, Music/Nobody Listens/JioSaavn). */
    DOWNLOADED(3)
}
