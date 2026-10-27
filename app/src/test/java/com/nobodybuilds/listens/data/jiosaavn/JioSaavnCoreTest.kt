package com.nobodybuilds.listens.data.jiosaavn

import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.model.LibrarySourceFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JioSaavnCoreTest {

    @Test
    fun `html entities in titles are decoded`() {
        assertEquals("Hawayein (From \"Jab Harry Met Sejal\")", JioSaavnParsers.decode("Hawayein (From &quot;Jab Harry Met Sejal&quot;)"))
        assertEquals("Rock & Roll's", JioSaavnParsers.decode("Rock &amp; Roll&#039;s"))
        assertEquals("é", JioSaavnParsers.decode("&#xe9;"))
        assertEquals("&unknown;", JioSaavnParsers.decode("&unknown;"))
    }

    @Test
    fun `artwork is asked for at 500px over https`() {
        assertEquals(
            "https://c.saavncdn.com/423/Kesariya-500x500.jpg",
            JioSaavnParsers.artwork("http://c.saavncdn.com/423/Kesariya-150x150.jpg"),
        )
        assertEquals(
            "https://c.saavncdn.com/artists/Arijit_500x500.jpg",
            JioSaavnParsers.artwork("https://c.saavncdn.com/artists/Arijit_50x50.jpg"),
        )
        assertNull(JioSaavnParsers.artwork(""))
    }

    @Test
    fun `bitrate is read from the file name`() {
        assertEquals(320_000, JioSaavnParsers.bitrateOf("https://aac.saavncdn.com/423/abc_320.mp4?Expires=1&Signature=x"))
        assertEquals(160_000, JioSaavnParsers.bitrateOf("https://aac.saavncdn.com/423/abc_160.mp4"))
        assertNull(JioSaavnParsers.bitrateOf("https://aac.saavncdn.com/423/abc.mp4"))
    }

    @Test
    fun `search songs carry artists, album, duration and big art`() {
        val tracks = JioSaavnParsers.parseSearchSongs(SEARCH)
        assertEquals(2, tracks.size)
        val first = tracks.first()
        assertEquals("KGfDrbWe", first.id)
        assertEquals("Kesariya", first.title)
        assertEquals("Pritam, Arijit Singh", first.artist)
        assertEquals("Brahmastra", first.album)
        assertEquals("81718905", first.albumId)
        assertEquals(268_000L, first.durationMs)
        assertEquals("2022", first.year)
        assertEquals("https://c.saavncdn.com/423/Kesariya-500x500.jpg", first.thumbnailUrl)
        // Without an artist map, the artists come from the subtitle before " - Album".
        assertEquals("Shreya Ghoshal, Sonu Nigam", tracks[1].artist)
    }

    @Test
    fun `launch modules follow their position and skip what cannot be played`() {
        val shelves = JioSaavnParsers.parseLaunch(LAUNCH)
        assertEquals(listOf("Trending Now", "Top Charts"), shelves.map { it.title })
        assertEquals(1, shelves[0].tracks.size)
        assertEquals(JioSaavnItemKind.ALBUM, shelves[0].playlists.single().kind)
        assertEquals(JioSaavnItemKind.PLAYLIST, shelves[1].playlists.single().kind)
        assertEquals("50 songs", shelves[1].playlists.single().subtitle)
    }

    @Test
    fun `media tells plain, 320 and drm only songs apart`() {
        val media = JioSaavnParsers.parseMedia(SONG_DETAILS)
        assertTrue(media.getValue("KGfDrbWe").has320)
        assertFalse(media.getValue("KGfDrbWe").drmOnly)
        assertEquals("ID2ieOjCrwfg", media.getValue("KGfDrbWe").encryptedUrl)
        assertTrue(media.getValue("drmOnly1").drmOnly)
        assertFalse(media.getValue("drmOnly1").has320)
    }

    @Test
    fun `radio songs come in key order with numeric play counts`() {
        val tracks = JioSaavnParsers.parseStationSongs(STATION)
        assertEquals(listOf("first001", "second02"), tracks.map { it.id })
        assertEquals("abc123def", JioSaavnParsers.parseStationId("""{"stationid":"abc123def"}"""))
    }

    @Test
    fun `lyrics turn breaks into lines`() {
        val lyrics = JioSaavnParsers.parseLyrics("""{"lyrics":"Line one<br>Line &amp; two<br/>","lyrics_copyright":"Writers"}""")
        assertEquals("Line one\nLine & two", lyrics?.text)
        assertEquals("Writers", lyrics?.copyright)
        assertNull(JioSaavnParsers.parseLyrics("""{"status":"failure","error":{"msg":"Something went wrong"}}"""))
    }

    @Test
    fun `auth token gives the signed url and its bitrate`() {
        val stream = JioSaavnParsers.parseAuthToken(
            """{"auth_url":"https://ac.cf.saavncdn.com/423/abc_160.mp4?Expires=1","type":"mp4","status":"success"}""",
            "KGfDrbWe",
        )
        assertEquals("KGfDrbWe", stream?.id)
        assertEquals(160_000, stream?.bitrate)
        assertNull(JioSaavnParsers.parseAuthToken("""{"status":"failure"}""", "KGfDrbWe"))
    }

    @Test
    fun `errors are told apart from replies`() {
        assertEquals("LOGIN_REQUIRED" to "Login required to call this API.", JioSaavnParsers.errorOf(
            """{"error":{"code":"LOGIN_REQUIRED","msg":"Login required to call this API."}}"""
        ))
        assertNull(JioSaavnParsers.errorOf("""{"songs":[]}"""))
        assertNull(JioSaavnParsers.errorOf("[]"))
    }

    @Test
    fun `library ids read plain ids and objects`() {
        val ids = JioSaavnParsers.parseLibraryIds("""{"song":["a1b2c3d4",{"id":"e5f6g7h8"}],"playlist":[{"id":"123"}],"album":[]}""")
        assertEquals(listOf("a1b2c3d4", "e5f6g7h8"), ids["song"])
        assertEquals(listOf("123"), ids["playlist"])
        assertFalse("album" in ids)
    }

    @Test
    fun `saved playlists are found wherever the reply nests them`() {
        val found = JioSaavnParsers.parseAnyCollections(
            """{"data":{"123":{"id":"123","type":"playlist","title":"Road &amp; Trip","image":"x-150x150.jpg"}},"other":[{"id":"9","type":"album","title":"No"}]}""",
            JioSaavnItemKind.PLAYLIST,
        )
        assertEquals(listOf("Road & Trip"), found.map { it.title })
    }

    @Test
    fun `suggestions are distinct titles across kinds`() {
        val suggestions = JioSaavnParsers.parseSuggestions(
            """{"topquery":{"data":[{"title":"Arijit Singh"}]},"songs":{"data":[{"title":"Kesariya"},{"title":"kesariya"}]},"albums":{"data":[]},"artists":{"data":[{"title":"Arijit Singh"}]}}"""
        )
        assertEquals(listOf("Arijit Singh", "Kesariya"), suggestions)
    }

    @Test
    fun `artist pages become shelves of songs, albums and playlists`() {
        val page = JioSaavnParsers.parseArtist(ARTIST)
        assertEquals("Arijit Singh", page.title)
        assertEquals(listOf("Top songs", "Albums", "Playlists"), page.shelves.map { it.title })
    }

    @Test
    fun `playlists report their full length for paging`() {
        val page = JioSaavnParsers.parsePlaylist("""{"title":"Malayalam 2000s","list_count":"50","list":[]}""")
        assertEquals(50, page.totalSongs)
        assertEquals("Malayalam 2000s", page.title)
    }

    @Test
    fun `content and proxy uris round trip to the song id`() {
        val uri = JioSaavnUris.contentUri("KGfDrbWe")
        assertEquals("jiosaavn://track/KGfDrbWe", uri)
        assertEquals("KGfDrbWe", JioSaavnUris.songIdFrom(uri))
        assertEquals("KGfDrbWe", JioSaavnUris.songIdFromPlaybackUri("http://127.0.0.1:41234/jiosaavn/KGfDrbWe"))
        assertNull(JioSaavnUris.songIdFromPlaybackUri("http://127.0.0.1:41234/ytmusic/dQw4w9WgXcQ"))
        assertNull(JioSaavnUris.songIdFrom("ytmusic://track/dQw4w9WgXcQ"))
        assertEquals(SourceType.JIOSAAVN, SourceType.fromContentUri(uri))
        assertEquals(SourceType.JIOSAAVN, JioSaavnUris.toEntity(JioSaavnTrack(id = "KGfDrbWe", title = "Kesariya")).sourceType)
    }

    @Test
    fun `library ids stay in their own negative range`() {
        val id = JioSaavnUris.songId("KGfDrbWe")
        assertTrue(id <= -18_000_000_000_000L && id > -19_000_000_000_000L)
        assertEquals(id, JioSaavnUris.songId("KGfDrbWe"))
        assertTrue(JioSaavnUris.albumId("Brahmastra") <= -19_000_000_000_000L)
        assertTrue(JioSaavnUris.artistId("Arijit Singh") <= -20_000_000_000_000L)
    }

    @Test
    fun `pasted links give their kind and token`() {
        assertEquals("song" to "Oy8NdQZSYFY", JioSaavnUris.linkFrom("https://www.jiosaavn.com/song/kesariya/Oy8NdQZSYFY"))
        assertEquals("album" to "iE95prsWZoc_", JioSaavnUris.linkFrom("https://www.jiosaavn.com/album/arijit-singh/iE95prsWZoc_"))
        assertEquals(
            "playlist" to "1e3vU4q7bbbExeh5N5JWFg__",
            JioSaavnUris.linkFrom("https://www.jiosaavn.com/featured/malayalam-top-50/1e3vU4q7bbbExeh5N5JWFg__?referrer=x"),
        )
        assertEquals(
            "song" to "SRgDaxUGelA",
            JioSaavnUris.linkFrom("https://www.jiosaavn.com/s/song/hindi/dunki/lutt-putt-gaya/SRgDaxUGelA"),
        )
        assertNull(JioSaavnUris.linkFrom("kesariya arijit"))
    }

    @Test
    fun `jiosaavn songs are cloud songs`() {
        val song = JioSaavnUris.toSong(JioSaavnTrack(id = "KGfDrbWe", title = "Kesariya", artist = "Arijit Singh"))
        assertFalse(LibrarySourceFilter.isLocal(song))
        assertEquals("Arijit Singh", JioSaavnUris.toTrack(song, "KGfDrbWe").artist)
        assertEquals("", JioSaavnUris.toTrack(JioSaavnUris.toSong(JioSaavnTrack(id = "KGfDrbWe", title = "X")), "KGfDrbWe").artist)
    }

    private companion object {
        val SEARCH = """
            {"total":2,"start":1,"results":[
              {"id":"KGfDrbWe","title":"Kesariya","subtitle":"Pritam, Arijit Singh - Brahmastra","type":"song",
               "image":"http://c.saavncdn.com/423/Kesariya-150x150.jpg","language":"hindi","year":"2022","play_count":"213951502",
               "more_info":{"album_id":"81718905","album":"Brahmastra","duration":"268","320kbps":"true",
                 "encrypted_media_url":"ID2ieOjCrwfg",
                 "artistMap":{"primary_artists":[{"id":"456323","name":"Pritam"},{"id":"459320","name":"Arijit Singh"}]}}},
              {"id":"aaM63gE_","title":"Tum Hi Ho &amp; More","subtitle":"Shreya Ghoshal, Sonu Nigam - Some Album","type":"song",
               "image":"https://c.saavncdn.com/1/x-150x150.jpg","more_info":{"duration":"200"}}
            ]}
        """.trimIndent()

        val LAUNCH = """
            {"new_trending":[
               {"id":"KGfDrbWe","title":"Kesariya","type":"song","subtitle":"Arijit Singh - Brahmastra","more_info":{"duration":"268"}},
               {"id":"82394739","title":"Ramayana","type":"album","subtitle":"","more_info":{"song_count":"5"}}],
             "charts":[{"id":"1134705865","title":"Malayalam Top 50","type":"playlist","more_info":{"song_count":"50"}}],
             "radio":[{"id":"1","title":"Hindi Hits","type":"radio_station"}],
             "promo:vx:data:107":{"data":[{"id":"2","title":"A Podcast","type":"show"}]},
             "modules":{
               "charts":{"title":"Top Charts","position":2},
               "new_trending":{"title":"Trending Now","position":1},
               "radio":{"title":"Radio Stations","position":3},
               "promo:vx:data:107":{"title":"Trending Podcasts","position":4}}}
        """.trimIndent()

        val SONG_DETAILS = """
            {"songs":[
              {"id":"KGfDrbWe","title":"Kesariya","type":"song","more_info":{"320kbps":"true","encrypted_media_url":"ID2ieOjCrwfg","encrypted_drm_media_url":"drm"}},
              {"id":"drmOnly1","title":"Locked","type":"song","more_info":{"320kbps":"false","encrypted_media_url":"","encrypted_drm_media_url":"drm"}}
            ]}
        """.trimIndent()

        val STATION = """
            {"1":{"song":{"id":"second02","title":"Second","type":"song","play_count":31950529,"more_info":{"duration":"200"}}},
             "0":{"song":{"id":"first001","title":"First","type":"song","play_count":12,"more_info":{"duration":"180"}}},
             "stationid":"abc123def"}
        """.trimIndent()

        val ARTIST = """
            {"name":"Arijit Singh",
             "topSongs":[{"id":"KGfDrbWe","title":"Kesariya","type":"song","more_info":{"duration":"268"}}],
             "topAlbums":[{"id":"81718905","title":"Brahmastra","type":"album"}],
             "singles":[],
             "dedicated_artist_playlist":[{"id":"802336660","title":"Arijit Sad Songs","type":"playlist","subtitle":"25 Songs"}],
             "featured_artist_playlist":[],
             "similarArtists":[]}
        """.trimIndent()
    }
}
