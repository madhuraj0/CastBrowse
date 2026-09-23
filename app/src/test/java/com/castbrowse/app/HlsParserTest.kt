package com.castbrowse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsParserTest {

    @Test
    fun parseMasterPlaylist_extractsVariantsCorrectly() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080p.m3u8
        """.trimIndent()

        val variants = HlsParser.parseMasterPlaylist("https://example.com/live/master.m3u8", master)

        assertEquals(3, variants.size)
        // Check sorted by bandwidth descending
        assertEquals("1080p (1920x1080)", variants[0].resolution)
        assertEquals("https://example.com/live/1080p.m3u8", variants[0].url)
        assertEquals(5000000L, variants[0].bandwidth)

        assertEquals("720p (1280x720)", variants[1].resolution)
        assertEquals("https://example.com/live/720p.m3u8", variants[1].url)

        assertEquals("360p (640x360)", variants[2].resolution)
        assertEquals("https://example.com/live/360p.m3u8", variants[2].url)
    }

    @Test
    fun parseMasterPlaylist_preservesQueryParameters() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
            stream_720.m3u8
        """.trimIndent()

        val variants = HlsParser.parseMasterPlaylist("https://example.com/live/master.m3u8?token=xyz123&expire=9999", master)

        assertEquals(1, variants.size)
        assertEquals("https://example.com/live/stream_720.m3u8?token=xyz123&expire=9999", variants[0].url)
    }

    @Test
    fun parseMasterPlaylist_returnsEmptyForMediaPlaylist() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            seg1.ts
            #EXTINF:10.0,
            seg2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val variants = HlsParser.parseMasterPlaylist("https://example.com/stream.m3u8", mediaPlaylist)
        assertTrue(variants.isEmpty())
    }
}
