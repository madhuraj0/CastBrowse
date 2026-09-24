package com.castbrowse.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaProxyTest {

    @Test
    fun isProxyUrl_identifiesProxyUrlsCorrectly() {
        assertTrue(LocalMediaProxy.isProxyUrl("http://192.168.1.50:8085/proxy/stream.m3u8?url=https%3A%2F%2Fexample.com"))
        assertTrue(LocalMediaProxy.isProxyUrl("http://127.0.0.1:8085/proxy/video.mp4?url=https%3A%2F%2Fexample.com"))
        assertTrue(LocalMediaProxy.isProxyUrl("http://192.168.1.50:8085/local?id=123"))
        assertTrue(LocalMediaProxy.isProxyUrl("http://192.168.1.50:8085/tv"))
        assertTrue(LocalMediaProxy.isProxyUrl("http://192.168.1.50:8085/subtitles.vtt"))
    }

    @Test
    fun isProxyUrl_rejectsExternalDirectUrls() {
        assertFalse(LocalMediaProxy.isProxyUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
        assertFalse(LocalMediaProxy.isProxyUrl("https://example.com/live/master.m3u8"))
        assertFalse(LocalMediaProxy.isProxyUrl("https://streamingsite.org/manifest.mpd"))
        assertFalse(LocalMediaProxy.isProxyUrl(""))
    }
}
