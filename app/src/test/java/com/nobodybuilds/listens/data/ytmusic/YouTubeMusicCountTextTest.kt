package com.nobodybuilds.listens.data.ytmusic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Play, view and like counts must never end up as a song's artist or album (they show in the notification). */
class YouTubeMusicCountTextTest {

    private fun row(videoId: String, vararg columns: String) = """
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [${columns.joinToString(",") { """{ "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "$it" }] } } }""" }}],
            "playlistItemData": { "videoId": "$videoId" }
          }
        }
    """

    @Test
    fun `counts written with no-break spaces are dropped from search rows`() {
        // YouTube's en-IN text, as returned: "96 crore plays", "6.7K plays".
        val page = YouTubeMusicParsers.parseBrowse(
            """{ "contents": [
                ${row("aaaaaaaaaaa", "Kesariya", "Song • Arijit Singh • 4:28", "96 crore plays")},
                ${row("bbbbbbbbbbb", "Kesariya Lofi", "Song • Glim Lofi • 3:10", "6.7K plays")},
                ${row("ccccccccccc", "Kesariya Video", "Video • Sony Music India • 8.3 lakh views • 4:30")}
            ] }"""
        )
        val tracks = page.shelves.flatMap { it.tracks }
        assertEquals(listOf("Arijit Singh", "Glim Lofi", "Sony Music India"), tracks.map { it.artist })
        assertEquals(listOf("", "", ""), tracks.map { it.album })
    }

    @Test
    fun `songs in an artist's top-result card get the card's artist`() {
        val page = YouTubeMusicParsers.parseBrowse(
            """{ "contents": [{
                "musicCardShelfRenderer": {
                  "title": { "runs": [{ "text": "Anirudh Ravichander" }] },
                  "subtitle": { "runs": [{ "text": "Artist" }, { "text": " • " }, { "text": "63.2 crore monthly audience" }] },
                  "contents": [${row("ddddddddddd", "Manasilaayo", "Song • 3:56", "29 crore plays")}]
                }
            }] }"""
        )
        val track = page.shelves.flatMap { it.tracks }.single()
        assertEquals("Anirudh Ravichander", track.artist)
        assertEquals("", track.album)
        assertEquals(236_000L, track.durationMs)
    }

    @Test
    fun `radio rows drop like counts`() {
        val radio = YouTubeMusicParsers.parseRadio(
            """{ "contents": [{ "playlistPanelVideoRenderer": {
                "videoId": "eeeeeeeeeee",
                "title": { "runs": [{ "text": "Sahiba" }] },
                "longBylineText": { "runs": [{ "text": "Aditya Rikhari" }, { "text": " • " }, { "text": "21 lakh likes" }] }
            } }] }"""
        )
        val track = radio.tracks.single()
        assertEquals("Aditya Rikhari", track.artist)
        assertEquals("", track.album)
    }
}
