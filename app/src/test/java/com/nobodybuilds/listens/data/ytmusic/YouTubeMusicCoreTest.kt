package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.model.LibrarySourceFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeMusicCoreTest {

    @Test
    fun `mergeCookies keeps the last value for a key`() {
        val merged = YouTubeMusicAuth.mergeCookies(
            "SAPISID=old; HSID=1",
            "SAPISID=new; SSID=2",
        )
        assertTrue(YouTubeMusicAuth.hasLoginCookie(merged))
        assertEquals("new", YouTubeMusicAuth.sapisid(merged))
        assertEquals("2", YouTubeMusicAuth.parseCookie(merged)["SSID"])
    }

    @Test
    fun `login cookie requires a youtube session cookie`() {
        assertFalse(YouTubeMusicAuth.hasLoginCookie(""))
        assertFalse(YouTubeMusicAuth.hasLoginCookie("VISITOR_INFO1_LIVE=abc"))
        assertTrue(YouTubeMusicAuth.hasLoginCookie("SAPISID=token; HSID=1"))
        assertEquals("token", YouTubeMusicAuth.sapisid("HSID=1; SAPISID=token"))
    }

    @Test
    fun `sapisid hash is stable for the same second`() {
        val cookie = "SAPISID=abc123"
        val first = YouTubeMusicAuth.authorizationHeader(cookie, "https://music.youtube.com", 1_700_000_000)
        val second = YouTubeMusicAuth.authorizationHeader(cookie, "https://music.youtube.com", 1_700_000_000)
        assertEquals(first, second)
        assertTrue(first!!.startsWith("SAPISIDHASH 1700000000_"))
        assertEquals(40, first.substringAfter("_").length)
    }

    @Test
    fun `highest format prefers the best bitrate then opus`() {
        val opus = audio(itag = 251, mime = """audio/webm; codecs="opus"""", bitrate = 160_000)
        val aac = audio(itag = 140, mime = """audio/mp4; codecs="mp4a.40.2"""", bitrate = 128_000)
        val premium = audio(itag = 141, mime = """audio/mp4; codecs="mp4a.40.2"""", bitrate = 256_000)

        assertEquals(251, YouTubeMusicFormats.selectHighest(listOf(aac, opus))?.itag)
        assertEquals(141, YouTubeMusicFormats.selectHighest(listOf(opus, premium))?.itag)
    }

    @Test
    fun `highest format skips short previews of a full song`() {
        val preview = audio(itag = 140, mime = """audio/mp4; codecs="mp4a.40.2"""", bitrate = 128_000)
            .copy(approxDurationMs = 30_000)
        val full = audio(itag = 251, mime = """audio/webm; codecs="opus"""", bitrate = 160_000)
            .copy(approxDurationMs = 200_000)
        assertEquals(251, YouTubeMusicFormats.selectHighest(listOf(preview, full), expectedDurationMs = 200_000)?.itag)
    }

    @Test
    fun `ciphered urls are skipped and direct urls are kept`() {
        val ciphered = audio(itag = 251, mime = """audio/webm; codecs="opus"""", bitrate = 160_000).copy(
            url = null,
            signatureCipher = "s=abc&sp=sig&url=https%3A%2F%2Frr.googlevideo.com%2Fvideoplayback",
        )
        val direct = audio(itag = 140, mime = """audio/mp4; codecs="mp4a.40.2"""", bitrate = 128_000).copy(
            url = "https://rr.googlevideo.com/direct",
        )
        assertNull(YouTubeMusicFormats.resolveUrl(ciphered))
        assertEquals("https://rr.googlevideo.com/direct", YouTubeMusicFormats.resolveUrl(direct))
        assertEquals(140, YouTubeMusicFormats.selectHighest(listOf(ciphered, direct))?.itag)
        assertEquals(
            listOf(140, 251),
            YouTubeMusicFormats.ranked(listOf(ciphered, direct)).map { it.itag }
        )
    }

    @Test
    fun `download keeps the original container`() {
        val opus = YouTubeMusicFormats.downloadSpec("Song / One", "Artist: Name", """audio/webm; codecs="opus"""")
        assertEquals("webm", opus.extension)
        assertEquals("audio/webm", opus.mimeType)
        assertEquals("Artist Name - Song One.webm", opus.fileName)

        val aac = YouTubeMusicFormats.downloadSpec("Song", "", """audio/mp4; codecs="mp4a.40.2"""")
        assertEquals("m4a", aac.extension)
        assertEquals("audio/mp4", aac.mimeType)
        assertEquals("Song.m4a", aac.fileName)
    }

    @Test
    fun `content uri keeps the video id case`() {
        val uri = YouTubeMusicUris.contentUri("dQw4w9WgXcQ")
        assertEquals("ytmusic://track/dQw4w9WgXcQ", uri)
        assertEquals("dQw4w9WgXcQ", YouTubeMusicUris.videoIdFrom(uri))
        assertNull(YouTubeMusicUris.videoIdFrom("ytmusic://track/short"))
        val song = YouTubeMusicUris.toSong(
            YouTubeMusicTrack(videoId = "dQw4w9WgXcQ", title = "Track", artist = "Artist")
        )
        assertEquals(SourceType.YTMUSIC, LibrarySourceFilter.sourceTypeOf(song))
        assertTrue(LibrarySourceFilter.matchesSourceType(com.nobodybuilds.listens.data.model.StorageFilter.ONLINE, SourceType.YTMUSIC))
    }

    @Test
    fun `browse parser reads shelves tracks and playlists`() {
        val page = YouTubeMusicParsers.parseBrowse(
            """
            {
              "contents": {
                "musicCarouselShelfRenderer": {
                  "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Made for you" }] } } },
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Night Drive" }] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Ada" }] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "3:05" }] } } }
                        ],
                        "playlistItemData": { "videoId": "abcdefghijk" },
                        "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                          { "url": "https://lh3.googleusercontent.com/small", "width": 60 },
                          { "url": "https://lh3.googleusercontent.com/large", "width": 544 }
                        ] } } }
                      }
                    },
                    {
                      "musicTwoRowItemRenderer": {
                        "title": { "runs": [{ "text": "Liked songs" }] },
                        "subtitle": { "runs": [{ "text": "Playlist" }, { "text": " • " }, { "text": "You" }] },
                        "navigationEndpoint": { "browseEndpoint": { "browseId": "VLLIKED123" } }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(1, page.shelves.size)
        assertEquals("Made for you", page.shelves[0].title)
        val track = page.shelves[0].tracks.single()
        assertEquals("abcdefghijk", track.videoId)
        assertEquals("Night Drive", track.title)
        assertEquals("Ada", track.artist)
        assertEquals(185_000L, track.durationMs)
        assertEquals("https://lh3.googleusercontent.com/large", track.thumbnailUrl)
        val playlist = page.shelves[0].playlists.single()
        assertEquals("LIKED123", playlist.playlistId)
        assertEquals("Liked songs", playlist.title)
    }

    @Test
    fun `browse parser reads playlist rows without a video id`() {
        val page = YouTubeMusicParsers.parseBrowse(
            """
            {
              "contents": {
                "musicShelfRenderer": {
                  "header": { "musicShelfHeaderRenderer": { "title": { "runs": [{ "text": "Playlists" }] } } },
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Liked songs" }] } } }
                        ],
                        "navigationEndpoint": { "browseEndpoint": { "browseId": "VLLMplaylist" } }
                      }
                    }
                  ]
                }
              },
              "continuationContents": {
                "sectionListContinuation": {
                  "continuations": [{ "nextContinuationData": { "continuation": "NEXTTOKEN" } }]
                }
              }
            }
            """.trimIndent()
        )
        assertEquals("Playlists", page.shelves[0].title)
        assertEquals("LMplaylist", page.shelves[0].playlists.single().playlistId)
        assertEquals("NEXTTOKEN", page.continuation)
    }

    @Test
    fun `browse parser reads two-row songs and playlists`() {
        val page = YouTubeMusicParsers.parseBrowse(
            """
            {
              "contents": {
                "musicCarouselShelfRenderer": {
                  "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Quick picks" }] } } },
                  "contents": [
                    {
                      "musicTwoRowItemRenderer": {
                        "title": { "runs": [{ "text": "Night Drive" }] },
                        "subtitle": { "runs": [{ "text": "Ada" }, { "text": " • " }, { "text": "3:05" }] },
                        "navigationEndpoint": { "watchEndpoint": { "videoId": "abcdefghijk" } }
                      }
                    },
                    {
                      "musicTwoRowItemRenderer": {
                        "title": { "runs": [{ "text": "Liked songs" }] },
                        "subtitle": { "runs": [{ "text": "Playlist" }] },
                        "navigationEndpoint": { "browseEndpoint": { "browseId": "VLLIKED123" } }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        assertEquals("Quick picks", page.shelves[0].title)
        val track = page.shelves[0].tracks.single()
        assertEquals("abcdefghijk", track.videoId)
        assertEquals("Night Drive", track.title)
        assertEquals("Ada", track.artist)
        assertEquals(185_000L, track.durationMs)
        assertEquals("LIKED123", page.shelves[0].playlists.single().playlistId)
    }

    @Test
    fun `browse parser drops repeated artists and songs from the flat search list`() {
        val artist = """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [{ "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Ada" }] } } }],
                "navigationEndpoint": { "browseEndpoint": {
                  "browseId": "UC8TXnDE17HVa8j2Ko6glyAQ",
                  "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" } }
                } }
              }
            }
        """.trimIndent()
        val song = """
            {
              "musicTwoRowItemRenderer": {
                "title": { "runs": [{ "text": "Night Drive" }] },
                "navigationEndpoint": { "watchEndpoint": { "videoId": "abcdefghijk" } }
              }
            }
        """.trimIndent()
        val page = YouTubeMusicParsers.parseBrowse("""{ "contents": [$artist, $song, $artist, $song] }""")
        val playlists = page.shelves.flatMap { it.playlists }
        val tracks = page.shelves.flatMap { it.tracks }
        assertEquals(listOf("UC8TXnDE17HVa8j2Ko6glyAQ"), playlists.map { it.playlistId })
        assertEquals(listOf("abcdefghijk"), tracks.map { it.videoId })
    }

    @Test
    fun `player parser keeps audio formats and duration`() {
        val payload = YouTubeMusicParsers.parsePlayer(
            """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": { "videoId": "abcdefghijk", "title": "Night Drive", "author": "Ada", "lengthSeconds": "185" },
              "streamingData": {
                "expiresInSeconds": "21600",
                "adaptiveFormats": [
                  { "itag": 18, "mimeType": "video/mp4", "bitrate": 500000, "url": "https://rr.googlevideo.com/video" },
                  { "itag": 251, "mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 141000, "audioSampleRate": "48000", "url": "https://rr.googlevideo.com/opus" }
                ]
              }
            }
            """.trimIndent()
        )
        assertEquals("OK", payload.status)
        assertEquals(185_000L, payload.durationMs)
        assertEquals(21600, payload.expiresInSeconds)
        assertEquals(1, payload.formats.size)
        assertEquals(251, YouTubeMusicFormats.selectHighest(payload.formats, payload.durationMs)?.itag)
    }

    private fun audio(itag: Int, mime: String, bitrate: Int) = YouTubeMusicAudioFormat(
        itag = itag,
        mimeType = mime,
        bitrate = bitrate,
        url = "https://rr.googlevideo.com/$itag",
    )
}
