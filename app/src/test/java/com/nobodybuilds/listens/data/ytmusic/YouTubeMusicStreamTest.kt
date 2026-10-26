package com.nobodybuilds.listens.data.ytmusic

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gagravarr.opus.OpusFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.abs

class YouTubeMusicStreamTest {

    @Test
    fun `visionOS is tried first because it streams without a PO token`() {
        assertEquals("VISIONOS", YouTubeMusicClients.STREAM.first().clientName)
        assertTrue(YouTubeMusicClients.STREAM.none { it.loginSupported })
    }

    @Test
    fun `youtube traffic drops the app client's user agent and logging interceptors`() {
        val appClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "NobodyListens/1.0").build())
            }
            .addNetworkInterceptor { chain -> chain.proceed(chain.request()) }
            .build()
        val http = YouTubeMusicClients.http(appClient)
        assertTrue(http.interceptors.isEmpty())
        assertTrue(http.networkInterceptors.isEmpty())
        assertEquals(1, appClient.interceptors.size, "the shared client is left alone")
    }

    @Test
    fun `flat search results put songs first, drop artists and clean the artist line`() {
        fun row(title: String, detail: String, endpoint: String) = """
            { "itemSectionRenderer": { "contents": [ { "musicResponsiveListItemRenderer": {
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "$title" }] } } },
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "$detail" }] } } }
              ],
              $endpoint
            } } ] } }
        """
        val page = YouTubeMusicParsers.parseBrowse(
            """{ "contents": [
              ${row("Hits", "Playlist • YouTube Music • 50 songs", "\"navigationEndpoint\": { \"browseEndpoint\": { \"browseId\": \"VLRDCLAK5uy_hits\" } }")},
              ${row("Sid Sriram", "Artist • 355M monthly audience", "\"navigationEndpoint\": { \"browseEndpoint\": { \"browseId\": \"UC7_KgmSrwM247k2lnh5\" } }, \"menu\": { \"playlistId\": \"RDEMradio\" }")},
              ${row("Kesariya", "Song • Arijit Singh • Brahmastra • 4:28", "\"playlistItemData\": { \"videoId\": \"NJAv_7lHUIU\" }")},
              ${row("Let Me Down Slowly", "Gravero • 24 crore views • 2:56", "\"playlistItemData\": { \"videoId\": \"abcdefghij1\" }")},
              ${row("Kesariya (Dance Mix)", "Sony Music India • 88M views • 3:18", "\"playlistItemData\": { \"videoId\": \"K3B8-klo5xc\" }")}
            ] }"""
        )
        val first = page.shelves.first()
        assertEquals(listOf("NJAv_7lHUIU", "abcdefghij1", "K3B8-klo5xc"), first.tracks.map { it.videoId })
        assertEquals("Gravero", first.tracks[1].artist)
        assertEquals("", first.tracks[1].album)
        assertEquals(176_000L, first.tracks[1].durationMs)
        assertEquals("Arijit Singh", first.tracks[0].artist)
        assertEquals("Brahmastra", first.tracks[0].album)
        assertEquals(268_000L, first.tracks[0].durationMs)
        assertEquals("Sony Music India", first.tracks[2].artist)
        assertEquals(listOf("RDCLAK5uy_hits"), page.shelves.flatMap { it.playlists }.map { it.playlistId })
    }

    @Test
    fun `quality settings cap the bitrate and downloads stay opus`() {
        fun format(itag: Int, mime: String, kbps: Int) =
            YouTubeMusicAudioFormat(itag = itag, mimeType = mime, bitrate = kbps * 1000, url = "https://rr.googlevideo.com/$itag")
        val opus = """audio/webm; codecs="opus""""
        val aac = """audio/mp4; codecs="mp4a.40.2""""
        val formats = listOf(format(251, opus, 148), format(250, opus, 70), format(249, opus, 52), format(140, aac, 131), format(139, aac, 49))

        fun best(quality: YouTubeMusicAudioQuality, opusOnly: Boolean = false) =
            YouTubeMusicFormats.ranked(formats, maxBitrate = quality.maxBitrate, opusOnly = opusOnly).first().itag

        assertEquals(251, best(YouTubeMusicAudioQuality.HIGH))
        assertEquals(250, best(YouTubeMusicAudioQuality.NORMAL))
        assertEquals(249, best(YouTubeMusicAudioQuality.DATA_SAVER))
        assertTrue(YouTubeMusicFormats.ranked(formats, opusOnly = true).none { it.itag == 140 || it.itag == 139 })
    }

    @Test
    fun `plain youtube search reads video rows`() {
        val tracks = YouTubeMusicParsers.parseVideoSearch(
            """{ "contents": { "sectionListRenderer": { "contents": [ { "itemSectionRenderer": { "contents": [
                { "videoRenderer": { "videoId": "lgTHGZF3BQw",
                  "title": { "runs": [{ "text": "Pal Pal Dil Ke Paas - Title" }] },
                  "ownerText": { "runs": [{ "text": "Zee Music Company" }] },
                  "lengthText": { "simpleText": "3:03" } } },
                { "shelfRenderer": {} }
            ] } } ] } } }"""
        )
        val track = tracks.single()
        assertEquals("lgTHGZF3BQw", track.videoId)
        assertEquals("Zee Music Company", track.artist)
        assertEquals(183_000L, track.durationMs)
        assertEquals("https://i.ytimg.com/vi/lgTHGZF3BQw/hqdefault.jpg", track.thumbnailUrl)
    }

    @Test
    fun `player parser keeps the hls manifest for video`() {
        val payload = YouTubeMusicParsers.parsePlayer(
            """{ "playabilityStatus": { "status": "OK" },
                 "streamingData": { "hlsManifestUrl": "https://manifest.googlevideo.com/api/manifest/hls_variant/x" } }"""
        )
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_variant/x", payload.hlsManifestUrl)
    }

    @Test
    fun `playback uris give the video id for the cache and bitrate`() {
        assertEquals("dQw4w9WgXcQ", YouTubeMusicUris.videoIdFromPlaybackUri("ytmusic://track/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeMusicUris.videoIdFromPlaybackUri("http://127.0.0.1:43369/ytmusic/dQw4w9WgXcQ"))
        assertNull(YouTubeMusicUris.videoIdFromPlaybackUri("http://127.0.0.1:43369/jellyfin/dQw4w9WgXcQ"))
        assertNull(YouTubeMusicUris.videoIdFromPlaybackUri("content://media/external/audio/media/12"))
    }

    @Test
    fun `stream user agent follows the client named in the url`() {
        val base = "https://rr1---sn-x.googlevideo.com/videoplayback?expire=1&itag=251"
        assertEquals(YouTubeMusicClients.VISIONOS.userAgent, YouTubeMusicClients.userAgentForStreamUrl("$base&c=VISIONOS&n=a"))
        assertEquals(YouTubeMusicClients.IOS.userAgent, YouTubeMusicClients.userAgentForStreamUrl("$base&c=IOS"))
        assertEquals(YouTubeMusicClients.VISIONOS.userAgent, YouTubeMusicClients.userAgentForStreamUrl(base))
    }

    @Test
    fun `probe reads past the first megabyte when the file is long enough`() {
        assertEquals(0L, YouTubeMusicFormats.probeOffset(null))
        assertEquals(0L, YouTubeMusicFormats.probeOffset(500_000))
        assertEquals(1_572_864L, YouTubeMusicFormats.probeOffset(4_605_080))
    }

    @Test
    fun `pasted links give the video id`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "  https://m.youtube.com/watch?v=$id  ",
        ).forEach { link -> assertEquals(id, YouTubeMusicUris.videoIdFromLink(link), link) }
        assertNull(YouTubeMusicUris.videoIdFromLink("despacito"))
        assertNull(YouTubeMusicUris.videoIdFromLink("https://www.youtube.com/@channel"))
    }

    @Test
    fun `library ids are negative stable and separate from other sources`() {
        val id = YouTubeMusicUris.songId("dQw4w9WgXcQ")
        assertTrue(id < -15_000_000_000_000L && id > -17_000_000_000_000L)
        assertEquals(id, YouTubeMusicUris.songId("dQw4w9WgXcQ"))
        val song = YouTubeMusicUris.toSong(YouTubeMusicTrack(videoId = "dQw4w9WgXcQ", title = "T"))
        assertEquals(id.toString(), song.id)
        assertEquals("YouTube Music", song.artist)
        assertEquals("ytmusic://track/dQw4w9WgXcQ", song.contentUriString)
    }

    @Test
    fun `remuxed downloads are saved as tagged ogg opus`() {
        val spec = YouTubeMusicFormats.downloadSpec("Song", "Artist", YouTubeMusicFormats.OGG_OPUS_MIME)
        assertEquals("Artist - Song.opus", spec.fileName)
        assertEquals("audio/ogg", spec.mimeType)
        val quoted = YouTubeMusicFormats.downloadSpec("Kesariya (From \"Brahmastra\")", "Arijit Singh", YouTubeMusicFormats.OGG_OPUS_MIME)
        assertEquals("Arijit Singh - Kesariya (From 'Brahmastra').opus", quoted.fileName)
    }

    @Test
    fun `album art is asked for big and video thumbnails are never downgraded`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w800-h800-l90-rj",
            YouTubeMusicFormats.largeThumbnail("https://lh3.googleusercontent.com/abc=w60-h60-p-l90-rj"),
        )
        val hd = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg?sqp=abc"
        assertEquals(hd, YouTubeMusicFormats.largeThumbnail(hd))
        assertNull(YouTubeMusicFormats.largeThumbnail(null))
        assertEquals(
            listOf("maxresdefault", "hq720", "sddefault", "hqdefault").map { "https://i.ytimg.com/vi/dQw4w9WgXcQ/$it.jpg" },
            YouTubeMusicThumbnails.videoThumbnailCandidates("dQw4w9WgXcQ"),
        )
        assertTrue(YouTubeMusicThumbnails.hasLetterbox("https://i.ytimg.com/vi/x/sddefault.jpg"))
        assertFalse(YouTubeMusicThumbnails.hasLetterbox("https://i.ytimg.com/vi/x/maxresdefault.jpg"))
    }

    @Test
    fun `radio rows give songs with artist, album and page ids plus the next token`() {
        val page = YouTubeMusicParsers.parseRadio(
            """{ "contents": [ { "playlistPanelVideoRenderer": {
                  "videoId": "NJAv_7lHUIU",
                  "title": { "runs": [{ "text": "Kesariya" }] },
                  "longBylineText": { "runs": [
                    { "text": "Arijit Singh", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCDxKh1gFWeYsqePvgVzmPoQ" } } },
                    { "text": " • " },
                    { "text": "Brahmastra", "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_abc" } } },
                    { "text": " • " }, { "text": "2022" } ] },
                  "lengthText": { "runs": [{ "text": "4:29" }] } } } ],
                "tabs": [ { "tabRenderer": { "endpoint": { "browseEndpoint": { "browseId": "MPLYt_lyrics" } } } } ],
                "continuations": [ { "nextRadioContinuationData": { "continuation": "TOKEN" } } ] }"""
        )
        val track = page.tracks.single()
        assertEquals("Arijit Singh", track.artist)
        assertEquals("Brahmastra", track.album)
        assertEquals(269_000L, track.durationMs)
        assertEquals("UCDxKh1gFWeYsqePvgVzmPoQ", track.artistId)
        assertEquals("MPREb_abc", track.albumId)
        assertEquals("TOKEN", page.continuation)
        assertEquals("MPLYt_lyrics", page.lyricsBrowseId)
    }

    @Test
    fun `suggestions and lyrics are read`() {
        assertEquals(
            listOf("kesariya", "kesari"),
            YouTubeMusicParsers.parseSuggestions(
                """{ "contents": [ { "searchSuggestionRenderer": { "suggestion": { "runs": [{ "text": "kesar" }, { "text": "iya" }] } } },
                   { "searchSuggestionRenderer": { "suggestion": { "runs": [{ "text": "kesari" }] } } } ] }"""
            ),
        )
        val lyrics = YouTubeMusicParsers.parseLyrics(
            """{ "contents": { "musicDescriptionShelfRenderer": {
                  "description": { "runs": [{ "text": "Line one\nLine two" }] },
                  "footer": { "runs": [{ "text": "Source: LyricFind" }] } } } }"""
        )
        assertEquals("Line one\nLine two", lyrics?.text)
        assertEquals("Source: LyricFind", lyrics?.source)
        assertNull(YouTubeMusicParsers.parseLyrics("""{ "contents": {} }"""))
    }

    @Test
    fun `cover block is a flac picture with the image at the end`() {
        val image = byteArrayOf(1, 2, 3)
        val block = Base64.getDecoder().decode(YouTubeMusicOpusRemuxer.pictureBlock(image, "image/png"))
        assertEquals(3, block[3].toInt()) // front cover
        assertEquals("image/png", String(block, 8, 9, Charsets.UTF_8))
        assertEquals(listOf<Byte>(1, 2, 3), block.takeLast(3))
    }

    /**
     * Hits YouTube for real: resolves a stream the way the app does, downloads all of it
     * and remuxes it. Run with YTMUSIC_LIVE=1.
     */
    @Test
    fun `live - a real track streams to the end and remuxes to opus`() = runBlocking {
        assumeTrue(System.getenv("YTMUSIC_LIVE") == "1", "set YTMUSIC_LIVE=1 to hit YouTube")
        val http = OkHttpClient()
        val api = YouTubeMusicApi(http)
        val session = YouTubeMusicSession(visitorData = requireNotNull(api.fetchVisitorData("")))
        val videoId = "kJQP7kiw5Fk"

        val client = YouTubeMusicClients.STREAM.first()
        val payload = YouTubeMusicParsers.parsePlayer(api.player(session, client, videoId))
        assertEquals("OK", payload.status, payload.reason)
        val format = requireNotNull(YouTubeMusicFormats.selectHighest(payload.formats, payload.durationMs))
        assertEquals(251, format.itag, "highest is Opus 251 for a free account")
        val url = requireNotNull(YouTubeMusicFormats.resolveUrl(format))
        assertTrue(api.probeStream(url, client.userAgent, format.contentLength), "probe past 1 MB")

        val total = requireNotNull(format.contentLength)
        val webm = ByteArrayOutputStream()
        var position = 0L
        while (position < total) {
            val end = minOf(total - 1, position + 4L * 1024 * 1024 - 1)
            val request = Request.Builder().url(url)
                .header("User-Agent", client.userAgent)
                .header("Range", "bytes=$position-$end")
                .build()
            http.newCall(request).execute().use { response ->
                assertEquals(206, response.code, "range $position-$end")
                position += response.body.byteStream().copyTo(webm)
            }
        }
        assertEquals(total, webm.size().toLong())

        val opus = File.createTempFile("ytmusic", ".opus")
        try {
            opus.outputStream().use { out ->
                YouTubeMusicOpusRemuxer.remux(
                    webm.toByteArray().inputStream(),
                    out,
                    YouTubeMusicOpusRemuxer.Tags(title = "Despacito", artist = "Luis Fonsi", album = "Vida"),
                )
            }
            OpusFile(opus).use { file ->
                assertEquals("Despacito", file.tags.title)
                assertEquals("Luis Fonsi", file.tags.artist)
                assertEquals("Vida", file.tags.album)
                var lastGranule = 0L
                var packet = file.nextAudioPacket
                while (packet != null) {
                    lastGranule = packet.granulePosition
                    packet = file.nextAudioPacket
                }
                val seconds = (lastGranule - file.info.preSkip) / 48_000.0
                val expected = (format.approxDurationMs ?: 0L) / 1000.0
                assertTrue(abs(seconds - expected) < 1.0, "duration $seconds s vs $expected s")
            }
            println("Live OK: ${opus.length()} byte opus, ${format.bitrate / 1000} kbps itag ${format.itag}")
        } finally {
            opus.delete()
        }
    }
}
