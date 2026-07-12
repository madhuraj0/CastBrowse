package com.castbrowse.app

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

@Serializable
data class ExtractedVideo(
    val url: String,
    val title: String = "",
    val poster: String = "",
    val resolution: String = "",
    val size: String = ""
)

class MediaExtractorClient(
    private val isAdBlockEnabled: () -> Boolean,
    private val isDesktopMode: () -> Boolean,
    private val onPageStarted: (String) -> Unit,
    private val onMediaDiscovered: (ExtractedVideo) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "MediaExtractorClient"
        private val MEDIA_REGEX = Regex("\\.(mp4|webm|m3u8|m3u|mpd|ogg|mkv)(\\?.*)?$", RegexOption.IGNORE_CASE)

        private val AD_DOMAINS = hashSetOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "adservice.google.com", "securepubads.g.doubleclick.net", "pubads.g.doubleclick.net",
            "taboola.com", "outbrain.com", "adnxs.com", "pubmatic.com", "criteo.com",
            "amazon-adsystem.com", "rubiconproject.com", "openx.net", "casalemedia.com",
            "bidswitch.net", "applovin.com", "adcolony.com", "unityads.com", "popads.net",
            "propellerads.com", "exoclick.com", "juicyads.com", "adkey.biz"
        )

        private val adHostsSet = HashSet<String>(150000)
        @Volatile private var isLoaded = false
        private val loadLock = Any()

        fun loadAdHosts(context: Context) {
            synchronized(loadLock) {
                if (isLoaded) return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val localSet = HashSet<String>(160000)
                    localSet.addAll(AD_DOMAINS)

                    val localFile = File(context.filesDir, "hosts.txt")
                    val inputStream: InputStream = if (localFile.exists() && localFile.length() > 0) {
                        localFile.inputStream()
                    } else {
                        context.assets.open("hosts.txt")
                    }

                    inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.startsWith("0.0.0.0 ") || cleanLine.startsWith("127.0.0.1 ")) {
                                val parts = cleanLine.split(Regex("\\s+"))
                                if (parts.size >= 2) {
                                    val domain = parts[1].trim()
                                    if (domain.isNotEmpty() && !domain.startsWith("#") && domain != "localhost") {
                                        localSet.add(domain)
                                    }
                                }
                            }
                        }
                    }

                    synchronized(loadLock) {
                        adHostsSet.clear()
                        adHostsSet.addAll(localSet)
                        isLoaded = true
                    }
                    Log.d(TAG, "Adblock list loaded successfully: ${adHostsSet.size} domains")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load adblock hosts", e)
                }
            }
        }

        fun updateAdHosts(context: Context, onResult: (Boolean, Int) -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            withContext(Dispatchers.Main) { onResult(false, 0) }
                            return@launch
                        }

                        val bodyString = response.body?.string()
                        if (bodyString.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) { onResult(false, 0) }
                            return@launch
                        }

                        val localFile = File(context.filesDir, "hosts.txt")
                        localFile.writeText(bodyString)

                        val localSet = HashSet<String>(160000)
                        localSet.addAll(AD_DOMAINS)

                        bodyString.lineSequence().forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.startsWith("0.0.0.0 ") || cleanLine.startsWith("127.0.0.1 ")) {
                                val parts = cleanLine.split(Regex("\\s+"))
                                if (parts.size >= 2) {
                                    val domain = parts[1].trim()
                                    if (domain.isNotEmpty() && !domain.startsWith("#") && domain != "localhost") {
                                        localSet.add(domain)
                                    }
                                }
                            }
                        }

                        synchronized(loadLock) {
                            adHostsSet.clear()
                            adHostsSet.addAll(localSet)
                            isLoaded = true
                        }

                        val prefs = context.getSharedPreferences("adblock_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()

                        Log.d(TAG, "Adblock list updated successfully: ${adHostsSet.size} domains")
                        withContext(Dispatchers.Main) {
                            onResult(true, adHostsSet.size)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating adblock hosts", e)
                    withContext(Dispatchers.Main) {
                        onResult(false, 0)
                    }
                }
            }
        }

        fun checkAutoUpdate(context: Context) {
            val prefs = context.getSharedPreferences("adblock_prefs", Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong("last_update", 0L)
            val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - lastUpdate > sevenDaysMs) {
                Log.d(TAG, "Automatic adblock update triggered")
                updateAdHosts(context) { success, count ->
                    Log.d(TAG, "Auto update completed. Success: $success, count: $count")
                }
            }
        }


        private val DOM_SCRAPER_SCRIPT = """
            (function() {
                var videos = [];
                var videoTags = document.getElementsByTagName('video');
                for (var i = 0; i < videoTags.length; i++) {
                    var src = videoTags[i].src;
                    var title = videoTags[i].title || document.title || "Embedded Video";
                    
                    var w = videoTags[i].videoWidth || 0;
                    var h = videoTags[i].videoHeight || 0;
                    var resolution = (w > 0 && h > 0) ? w + "x" + h : "";
                    
                    var dur = videoTags[i].duration;
                    var sizeText = (dur && !isNaN(dur)) ? Math.floor(dur / 60) + "m " + Math.floor(dur % 60) + "s" : "";
                    
                    var poster = videoTags[i].poster || "";
                    if (!poster) {
                        try {
                            var canvas = document.createElement('canvas');
                            canvas.width = 160;
                            canvas.height = 90;
                            var ctx = canvas.getContext('2d');
                            ctx.drawImage(videoTags[i], 0, 0, canvas.width, canvas.height);
                            poster = canvas.toDataURL('image/jpeg', 0.5);
                        } catch (e) {
                            poster = "";
                        }
                    }
                    
                    if (src) {
                        videos.push({url: src, poster: poster, title: title, resolution: resolution, size: sizeText});
                    }
                    
                    var sourceTags = videoTags[i].getElementsByTagName('source');
                    for (var j = 0; j < sourceTags.length; j++) {
                        var ssrc = sourceTags[j].src;
                        if (ssrc) {
                            videos.push({url: ssrc, poster: poster, title: title, resolution: resolution, size: sizeText});
                        }
                    }
                }
                
                var aTags = document.getElementsByTagName('a');
                var videoRegex = /\.(mp4|m3u8|m3u|webm|mpd|ogg|mkv)(\?.*)?$/i;
                for (var i = 0; i < aTags.length; i++) {
                    var href = aTags[i].href;
                    if (href && videoRegex.test(href)) {
                        var label = aTags[i].innerText.trim() || href.substring(href.lastIndexOf('/') + 1);
                        videos.push({url: href, poster: "", title: label, resolution: "", size: ""});
                    }
                }
                
                var unique = [];
                var urls = new Set();
                for (var k = 0; k < videos.length; k++) {
                    var v = videos[k];
                    if (v.url && (v.url.startsWith('http://') || v.url.startsWith('https://')) && !urls.has(v.url)) {
                        urls.add(v.url);
                        unique.push(v);
                    }
                }
                
                if (unique.length > 0) {
                    window.AndroidApp.postMessage(JSON.stringify(unique));
                }
            })();
        """.trimIndent()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        
        // Hardening: Reject cleartext HTTP navigation for public (non-local) sites, force upgrade to HTTPS
        if (url.startsWith("http://") && !isLocalUrl(url)) {
            val secureUrl = url.replaceFirst("http://", "https://")
            view?.loadUrl(secureUrl)
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null) {
            // Ad blocking check
            if (isAdBlockEnabled()) {
                val host = request.url.host
                if (host != null && isAdHost(host)) {
                    // Block by returning an empty plain text response
                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
            }
            if (isMediaUrl(url)) {
                val filename = url.substringBefore("?").substringAfterLast("/")
                onMediaDiscovered(ExtractedVideo(url = url, title = filename))
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isAdHost(host: String): Boolean {
        var current = host
        while (current.contains(".")) {
            if (adHostsSet.contains(current)) {
                return true
            }
            current = current.substringAfter(".")
        }
        return adHostsSet.contains(current)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            onPageStarted(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Inject DOM Parser script
        view?.evaluateJavascript(DOM_SCRAPER_SCRIPT, null)
        
        // Inject viewport spoofing if desktop mode is enabled
        if (isDesktopMode()) {
            val desktopViewportScript = """
                (function() {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.25, maximum-scale=2.0, user-scalable=yes');
                    } else {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        meta.content = 'width=1280, initial-scale=0.35, minimum-scale=0.25, maximum-scale=2.0, user-scalable=yes';
                        document.getElementsByTagName('head')[0].appendChild(meta);
                    }
                })();
            """.trimIndent()
            view?.evaluateJavascript(desktopViewportScript, null)
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        val path = Uri.parse(url).path ?: return false
        return MEDIA_REGEX.containsMatchIn(path)
    }

    private fun isLocalUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1") ||
                    host.startsWith("192.168.") ||
                    host.startsWith("10.") ||
                    (host.startsWith("172.") && isPrivateRange172(host))
        } catch (e: Exception) {
            false
        }
    }

    private fun isPrivateRange172(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size >= 2) {
            val secondOctet = parts[1].toIntOrNull()
            if (secondOctet != null) {
                return secondOctet in 16..31
            }
        }
        return false
    }

    /**
     * Interface bound to 'AndroidApp' namespace.
     * Hardened to accept only formatted JSON payloads and parse them securely.
     */
    class WebAppInterface(private val onVideosFound: (List<ExtractedVideo>) -> Unit) {
        @JavascriptInterface
        fun postMessage(json: String) {
            try {
                // Safeguard parsing via kotlinx.serialization
                val list = Json.decodeFromString<List<ExtractedVideo>>(json)
                onVideosFound(list)
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing extracted videos JSON", e)
            }
        }
    }
}
