package com.castbrowse.app

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

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

        // Fallback static list — used as emergency safety net when adHostsSet is empty
        private val AD_DOMAINS = hashSetOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "adservice.google.com", "securepubads.g.doubleclick.net", "pubads.g.doubleclick.net",
            "taboola.com", "outbrain.com", "adnxs.com", "pubmatic.com", "criteo.com",
            "amazon-adsystem.com", "rubiconproject.com", "openx.net", "casalemedia.com",
            "bidswitch.net", "applovin.com", "adcolony.com", "unityads.com", "popads.net",
            "propellerads.com", "exoclick.com", "juicyads.com", "adkey.biz"
        )

        // Thread-safe set backed by ConcurrentHashMap — safe to read/write from any thread
        private val adHostsSet: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile private var isLoaded = false

        // App-lifetime managed scope — no lifecycle leaks; SupervisorJob prevents cascading failure
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Singleton OkHttpClient shared across all update calls and thumbnail loading — reuses connection pool & thread pool
        internal val httpClient by lazy { OkHttpClient() }

        fun loadAdHosts(context: Context) {
            if (isLoaded) return
            scope.launch {
                try {
                    val localFile = File(context.filesDir, "hosts.txt")
                    val inputStream: InputStream = if (localFile.exists() && localFile.length() > 0) {
                        localFile.inputStream()
                    } else {
                        context.assets.open("hosts.txt")
                    }

                    parseHostsStream(inputStream)
                    isLoaded = true
                    Log.d(TAG, "Adblock list loaded successfully: ${adHostsSet.size} domains")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load adblock hosts", e)
                }
            }
        }

        fun updateAdHosts(context: Context, onResult: (Boolean, Int) -> Unit) {
            // Connectivity check before attempting network request
            if (!isNetworkAvailable(context)) {
                scope.launch {
                    withContext(Dispatchers.Main) { onResult(false, 0) }
                }
                return
            }

            scope.launch {
                try {
                    val request = Request.Builder()
                        .url("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            withContext(Dispatchers.Main) { onResult(false, 0) }
                            return@launch
                        }

                        val bodyString = response.body?.string()
                        if (bodyString.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) { onResult(false, 0) }
                            return@launch
                        }

                        // Persist the downloaded file locally
                        val localFile = File(context.filesDir, "hosts.txt")
                        localFile.writeText(bodyString)

                        // Re-parse from the new content
                        adHostsSet.clear()
                        parseHostsString(bodyString)
                        isLoaded = true

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

        private fun parseHostsStream(stream: InputStream) {
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line -> parseLine(line) }
            }
        }

        private fun parseHostsString(content: String) {
            content.lineSequence().forEach { line -> parseLine(line) }
        }

        private fun parseLine(line: String) {
            val trimmed = line.trim()
            if (trimmed.startsWith("0.0.0.0 ") || trimmed.startsWith("127.0.0.1 ")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].trim()
                    if (domain.isNotEmpty() && !domain.startsWith("#") && domain != "localhost") {
                        adHostsSet.add(domain)
                    }
                }
            }
        }

        private fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun extractFilenameFromUrl(url: String): String {
            return try {
                val clean = url.substringBefore("#").substringBefore("?")
                val lastPart = clean.substringAfterLast("/")
                val decoded = java.net.URLDecoder.decode(lastPart, "UTF-8")
                if (decoded.isNotBlank()) decoded else "video_stream"
            } catch (e: Exception) {
                url.substringBefore("?").substringAfterLast("/").ifEmpty { "video_stream" }
            }
        }

        private val DOM_SCRAPER_SCRIPT = """
            (function() {
                if (window.__castbrowseScraperInitialized) {
                    if (window.__castbrowseScan) window.__castbrowseScan();
                    return;
                }
                window.__castbrowseScraperInitialized = true;

                function getFilename(u) {
                    try {
                        var clean = u.split('?')[0].split('#')[0];
                        var name = clean.substring(clean.lastIndexOf('/') + 1);
                        var decoded = decodeURIComponent(name);
                        return (decoded && decoded.trim().length > 0) ? decoded : "video_stream";
                    } catch(e) {
                        return "video_stream";
                    }
                }

                var reportedUrls = new Set();
                var videoRegex = /\.(mp4|m3u8|m3u|webm|mpd|ogg|mkv)(\?.*)?$/i;

                function reportVideo(src, poster, title, resolution, sizeText) {
                    if (!src || (!src.startsWith('http://') && !src.startsWith('https://'))) return;
                    if (reportedUrls.has(src)) return;
                    reportedUrls.add(src);
                    var cleanTitle = getFilename(src);
                    var payload = [{
                        url: src,
                        poster: poster || "",
                        title: cleanTitle,
                        resolution: resolution || "",
                        size: sizeText || ""
                    }];
                    if (window.AndroidApp && window.AndroidApp.postMessage) {
                        window.AndroidApp.postMessage(JSON.stringify(payload));
                    }
                }

                function checkVideoElement(v) {
                    if (!v) return;
                    var src = v.src || v.currentSrc;
                    var w = v.videoWidth || 0;
                    var h = v.videoHeight || 0;
                    var resolution = (w > 0 && h > 0) ? w + "x" + h : "";
                    var dur = v.duration;
                    var sizeText = (dur && !isNaN(dur)) ? Math.floor(dur / 60) + "m " + Math.floor(dur % 60) + "s" : "";
                    var poster = v.poster || "";

                    if (src) {
                        reportVideo(src, poster, "", resolution, sizeText);
                    }
                    var sources = v.getElementsByTagName('source');
                    for (var j = 0; j < sources.length; j++) {
                        if (sources[j].src) {
                            reportVideo(sources[j].src, poster, "", resolution, sizeText);
                        }
                    }
                }

                function scanDocument() {
                    var videoTags = document.getElementsByTagName('video');
                    for (var i = 0; i < videoTags.length; i++) {
                        checkVideoElement(videoTags[i]);
                    }

                    var aTags = document.getElementsByTagName('a');
                    for (var k = 0; k < aTags.length; k++) {
                        var href = aTags[k].href;
                        if (href && videoRegex.test(href)) {
                            reportVideo(href, "", "", "", "");
                        }
                    }

                    var iframes = document.getElementsByTagName('iframe');
                    for (var m = 0; m < iframes.length; m++) {
                        var isrc = iframes[m].src;
                        if (isrc && videoRegex.test(isrc)) {
                            reportVideo(isrc, "", "", "", "");
                        }
                    }
                }

                window.__castbrowseScan = scanDocument;
                scanDocument();

                // Hook HTMLMediaElement.prototype.play and src setter to catch dynamically attached players
                try {
                    var origPlay = HTMLMediaElement.prototype.play;
                    HTMLMediaElement.prototype.play = function() {
                        checkVideoElement(this);
                        return origPlay.apply(this, arguments);
                    };

                    var srcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
                    if (srcDesc && srcDesc.set) {
                        var origSet = srcDesc.set;
                        srcDesc.set = function(val) {
                            origSet.call(this, val);
                            checkVideoElement(this);
                        };
                        Object.defineProperty(HTMLMediaElement.prototype, 'src', srcDesc);
                    }
                } catch(e) {}

                // Listen to playback and media events
                document.addEventListener('play', function(e) { checkVideoElement(e.target); }, true);
                document.addEventListener('loadeddata', function(e) { checkVideoElement(e.target); }, true);
                document.addEventListener('canplay', function(e) { checkVideoElement(e.target); }, true);

                // MutationObserver for dynamically added videos or iframes
                try {
                    var observer = new MutationObserver(function(mutations) {
                        for (var i = 0; i < mutations.length; i++) {
                            var mut = mutations[i];
                            for (var j = 0; j < mut.addedNodes.length; j++) {
                                var node = mut.addedNodes[j];
                                if (node && node.nodeType === 1) {
                                    if (node.tagName === 'VIDEO') {
                                        checkVideoElement(node);
                                    } else if (node.getElementsByTagName) {
                                        var vids = node.getElementsByTagName('video');
                                        for (var v = 0; v < vids.length; v++) checkVideoElement(vids[v]);
                                    }
                                }
                            }
                        }
                    });
                    observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
                } catch(e) {}
            })();
        """.trimIndent()

        private val COSMETIC_ADBLOCK_CSS = """
            (function() {
                if (document.getElementById('__castbrowse_cosmetic_adblock')) return;
                var style = document.createElement('style');
                style.id = '__castbrowse_cosmetic_adblock';
                style.type = 'text/css';
                style.appendChild(document.createTextNode(`
                    .ad, .ads, .adsbygoogle, .advertisement, [id^="ad-"], [class*="ad-box"],
                    [class*="banner-ad"], [class*="ad-container"], [id*="google_ads"],
                    iframe[src*="doubleclick.net"], iframe[src*="googleads"],
                    div[class*="sponsored-post"], .taboola-ad, .outbrain-ad {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        min-height: 0 !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                `));
                (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        // Block external schemes (intent://, market://, tel:, etc.) from rogue ad redirects
        val scheme = request.url?.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "javascript") {
            Log.d(TAG, "Blocked external scheme navigation: $url")
            return true
        }
        
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
                val filename = extractFilenameFromUrl(url)
                onMediaDiscovered(ExtractedVideo(url = url, title = filename))
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isAdHost(host: String): Boolean {
        // Fast path: check the dynamic ConcurrentHashMap set (thread-safe O(1) reads)
        if (adHostsSet.isNotEmpty()) {
            var current = host
            while (current.contains(".")) {
                if (adHostsSet.contains(current)) return true
                current = current.substringAfter(".")
            }
            if (adHostsSet.contains(current)) return true
        }
        // Fallback: static emergency list (covers startup window before async load finishes)
        if (AD_DOMAINS.contains(host)) return true
        for (adDomain in AD_DOMAINS) {
            if (host.endsWith(".$adDomain")) return true
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            onPageStarted(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Inject DOM Parser script — runs in page context, only after page fully loads
        view?.evaluateJavascript(DOM_SCRAPER_SCRIPT, null)

        // Cosmetic ad blocking: hide blocked ad containers and empty placeholders
        if (isAdBlockEnabled()) {
            view?.evaluateJavascript(COSMETIC_ADBLOCK_CSS, null)
        }
        
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
