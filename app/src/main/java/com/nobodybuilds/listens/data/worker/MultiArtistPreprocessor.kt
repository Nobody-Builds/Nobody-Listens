package com.nobodybuilds.listens.data.worker

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.serializeArtistRefs
import com.nobodybuilds.listens.data.model.ArtistRef
import java.util.concurrent.atomic.AtomicLong

/** Data class to hold the result of multi-artist preprocessing. */
internal data class MultiArtistProcessResult(
    val songs: List<SongEntity>,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val crossRefs: List<SongArtistCrossRef>
)

/**
 * Process songs with multi-artist support. Splits artist names by delimiters and creates proper
 * cross-references.
 */
internal fun preProcessAndDeduplicateWithMultiArtist(
        songs: List<SongEntity>,
        artistDelimiters: List<String>,
        wordDelimiters: List<String> = emptyList(),
        extractFromTitle: Boolean = true,
        groupByAlbumArtist: Boolean,
        existingArtistMetadata: Map<Long, Pair<String?, String?>>,
        existingAlbums: List<AlbumEntity>,
        existingArtistIdMap: MutableMap<String, Long>,
        initialMaxArtistId: Long
): MultiArtistProcessResult {
    
    val nextArtistId = AtomicLong(initialMaxArtistId + 1)
    val artistNameToId = existingArtistIdMap // Re-use the map passed in
    
    val allCrossRefs = mutableListOf<SongArtistCrossRef>()
    val artistTrackCounts = mutableMapOf<Long, Int>()
    val albumMap = mutableMapOf<AlbumGroupingKey, Long>()
    val artistSplitCache = mutableMapOf<String, List<String>>()
    val correctedSongs = ArrayList<SongEntity>(songs.size)

    existingAlbums
        .sortedBy { it.id }
        .forEach { album ->
            buildAlbumGroupingKeys(album).forEach { key ->
                albumMap.putIfAbsent(key, album.id)
            }
        }

    songs.forEach { song ->
        val rawArtistName = song.artistName
        val songArtistNameTrimmed = rawArtistName.trim()

        // Split artist field by character + word delimiters
        val allArtistsForSong =
                artistSplitCache.getOrPut("$rawArtistName\u0000${song.title}\u0000$extractFromTitle") {
                    collectArtistNames(
                        rawArtistName = rawArtistName,
                        title = song.title,
                        artistDelimiters = artistDelimiters,
                        wordDelimiters = wordDelimiters,
                        extractFromTitle = extractFromTitle
                    )
                }

        allArtistsForSong.forEach { artistName ->
            val normalizedName = artistName.trim()
            if (normalizedName.isNotEmpty() && !artistNameToId.containsKey(normalizedName)) {
                 val id = nextArtistId.getAndIncrement()
                 artistNameToId[normalizedName] = id
            }
        }

        val primaryArtistName =
                allArtistsForSong.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: songArtistNameTrimmed
        val primaryArtistId = artistNameToId[primaryArtistName] ?: song.artistId

        allArtistsForSong.forEachIndexed { index, artistName ->
            val normalizedName = artistName.trim()
            val artistId = artistNameToId[normalizedName]
            if (artistId != null) {
                val isPrimary = (index == 0) // First artist is primary
                allCrossRefs.add(
                        SongArtistCrossRef(
                                songId = song.id,
                                artistId = artistId,
                                isPrimary = isPrimary
                        )
                )
                artistTrackCounts[artistId] = (artistTrackCounts[artistId] ?: 0) + 1
            }
        }

        // --- Album Logic ---
        val albumKey = buildAlbumGroupingKey(song)
        val finalAlbumId = albumMap.getOrPut(albumKey) { song.albumId }

        // Build serialized artists JSON for efficient loading without JOINs
        val artistRefsForJson = allArtistsForSong.mapIndexed { index, name ->
            val normalizedName = name.trim()
            val artistId = artistNameToId[normalizedName] ?: 0L
            ArtistRef(id = artistId, name = normalizedName, isPrimary = index == 0)
        }.filter { it.name.isNotEmpty() }

        correctedSongs.add(
                song.copy(
                        artistId = primaryArtistId,
                        artistName = rawArtistName, // Preserving full artist string for display
                        albumId = finalAlbumId,
                        artistsJson = serializeArtistRefs(artistRefsForJson)
                )
        )
    }

    // Build Entities
    val artistEntities = artistNameToId.map { (name, id) ->
        val count = artistTrackCounts[id] ?: 0
        val metadata = existingArtistMetadata[id]
        ArtistEntity(
            id = id,
            name = name,
            trackCount = count,
            imageUrl = metadata?.first,
            customImageUri = metadata?.second
        )
    }
    
    // Re-calculate Album Entities from the corrected songs to ensure we have valid metadata (Art, Year)
    // which isn't available in the simple albumMap (which only has ID)
    val albumEntities = correctedSongs.groupBy { it.albumId }.map { (catAlbumId, songsInAlbum) ->
         val firstSong = songsInAlbum.first()
         val representativeAlbumArt = songsInAlbum.firstNotNullOfOrNull { it.albumArtUriString }
         val determinedAlbumArtist = chooseAlbumDisplayArtist(
             songs = songsInAlbum,
             preferAlbumArtist = groupByAlbumArtist
         )
         val determinedAlbumArtistId = resolveAlbumDisplayArtistId(
             displayArtist = determinedAlbumArtist,
             songs = songsInAlbum,
             artistNameToId = artistNameToId,
             artistDelimiters = artistDelimiters,
             wordDelimiters = wordDelimiters
         )

         AlbumEntity(
             id = catAlbumId,
             title = firstSong.albumName,
             artistName = determinedAlbumArtist,
             artistId = determinedAlbumArtistId,
             albumArtUriString = representativeAlbumArt,
             songCount = songsInAlbum.size,
             dateAdded = firstSong.dateAdded,
             year = firstSong.year
         )
    }

    return MultiArtistProcessResult(
            songs = correctedSongs, // Corrected songs have the right Album IDs now
            albums = albumEntities,
            artists = artistEntities,
            crossRefs = allCrossRefs
    )
}
