package com.castbrowse.app

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
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
    val size: String = "",
    val isDrmProtected: Boolean = false,
    val drmKeySystem: String? = null
)

class MediaExtractorClient(
    private val isAdBlockEnabled: () -> Boolean,
    private val isDesktopMode: () -> Boolean,
    private val onPageStarted: (String) -> Unit,
    private val onDrmDetected: ((keySystem: String, url: String) -> Unit)? = null,
    private val onMediaDiscovered: (ExtractedVideo) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "MediaExtractorClient"
        internal val MEDIA_REGEX = Regex("\\.(mp4|webm|m3u8|m3u|mpd|ogg|mkv)(\\?.*)?$", RegexOption.IGNORE_CASE)
        private val DYNAMIC_STREAM_REGEX = Regex("(?i)(\\.m3u8|\\.mp4|\\.webm|\\.mpd|/playlist|/manifest|/master|/chunklist)")
        private val SEGMENT_REGEX = Regex("(?i)(\\.m4s|\\.ts|init\\.mp4|init\\.m4s|/seg-\\d+|/chunk-\\d+|/fragment-\\d+|/segment/|/fragment/|/chunk/|/seg_|/chunk_|/fragment_)")

        fun isSegmentUrl(url: String): Boolean {
            return SEGMENT_REGEX.containsMatchIn(url)
        }

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

        // Singleton OkHttpClient shared across all update calls and thumbnail loading — uses DNS-over-HTTPS resolver
        internal val httpClient by lazy {
            OkHttpClient.Builder()
                .dns(DnsOverHttpsResolver)
                .build()
        }

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

                        val localFile = File(context.filesDir, "hosts.txt")
                        localFile.writeText(bodyString)

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

        /**
         * Deep iFrame, Shadow DOM, and DRM EME detection JavaScript scraper.
         * Injected into WebView contexts to capture embedded players, nested frames,
         * and report Widevine/PlayReady DRM-encrypted media streams.
         */
        internal val DOM_SCRAPER_SCRIPT = """
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
                var segmentRegex = /(\.m4s|\.ts|init\.mp4|init\.m4s|\/segment|\/fragment|\/chunk|\/seg-|\/chunk-)([\?#].*)?$/i;
                var manifestRegex = /(\.m3u8|\.mpd|\/playlist|\/manifest|\/master|\/chunklist)([\?#].*)?$/i;
                var videoRegex = /\.(mp4|m3u8|m3u|webm|mpd|ogg|mkv)(\?.*)?$/i;

                function reportVideo(src, poster, title, resolution, sizeText, isDrm, drmKeySystem) {
                    if (!src || (!src.startsWith('http://') && !src.startsWith('https://'))) return;
                    if (segmentRegex.test(src)) return;
                    if (reportedUrls.has(src)) return;
                    reportedUrls.add(src);
                    var cleanTitle = title || getFilename(src);
                    var payload = [{
                        url: src,
                        poster: poster || "",
                        title: cleanTitle,
                        resolution: resolution || "",
                        size: sizeText || "",
                        isDrmProtected: !!isDrm,
                        drmKeySystem: drmKeySystem || ""
                    }];
                    if (window.AndroidApp && window.AndroidApp.postMessage) {
                        window.AndroidApp.postMessage(JSON.stringify(payload));
                    }
                }

                // EME / DRM Interceptor: Catch Widevine / PlayReady / FairPlay encryption requests
                try {
                    if (navigator.requestMediaKeySystemAccess) {
                        var origReqKey = navigator.requestMediaKeySystemAccess;
                        navigator.requestMediaKeySystemAccess = function(keySystem, configs) {
                            try {
                                if (typeof keySystem === 'string') {
                                    var lower = keySystem.toLowerCase();
                                    if (lower.indexOf('widevine') !== -1 || lower.indexOf('playready') !== -1 || lower.indexOf('fairplay') !== -1) {
                                        var detectedName = lower.indexOf('widevine') !== -1 ? 'Widevine' : (lower.indexOf('playready') !== -1 ? 'PlayReady' : 'FairPlay');
                                        if (window.AndroidApp && window.AndroidApp.postDrmDetected) {
                                            window.AndroidApp.postDrmDetected(detectedName, window.location.href);
                                        }
                                    }
                                }
                            } catch(err) {}
                            return origReqKey.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                document.addEventListener('encrypted', function(e) {
                    try {
                        var ks = (e && e.initDataType) ? ('EME (' + e.initDataType + ')') : 'Widevine / EME';
                        if (window.AndroidApp && window.AndroidApp.postDrmDetected) {
                            window.AndroidApp.postDrmDetected(ks, (e.target && (e.target.src || e.target.currentSrc)) || window.location.href);
                        }
                    } catch(err) {}
                }, true);

                // Intercept fetch() calls to capture root manifests (.m3u8, .mpd) from MSE/DASH players
                try {
                    var origFetch = window.fetch;
                    if (origFetch) {
                        window.fetch = function(input, init) {
                            var u = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                            if (u && (manifestRegex.test(u) || videoRegex.test(u)) && !segmentRegex.test(u)) {
                                reportVideo(u, "", "", "", "");
                            }
                            return origFetch.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // Intercept XMLHttpRequest.open() to capture root manifests from AJAX players
                try {
                    var origOpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        if (url && typeof url === 'string') {
                            if ((manifestRegex.test(url) || videoRegex.test(url)) && !segmentRegex.test(url)) {
                                reportVideo(url, "", "", "", "");
                            }
                        }
                        return origOpen.apply(this, arguments);
                    };
                } catch(e) {}

                // PostMessage Listener: Extract stream URLs communicated across iframes and players
                window.addEventListener('message', function(event) {
                    try {
                        var data = event.data;
                        if (typeof data === 'string') {
                            if ((manifestRegex.test(data) || videoRegex.test(data)) && !segmentRegex.test(data)) {
                                reportVideo(data, "", "", "", "");
                            }
                            try { data = JSON.parse(data); } catch(e) {}
                        }
                        if (typeof data === 'object' && data !== null) {
                            parseObjectForStreams(data, 0);
                        }
                    } catch(e) {}
                }, true);

                function parseObjectForStreams(obj, depth) {
                    if (!obj || depth > 4) return;
                    for (var key in obj) {
                        try {
                            var val = obj[key];
                            if (typeof val === 'string') {
                                if ((manifestRegex.test(val) || videoRegex.test(val)) && !segmentRegex.test(val)) {
                                    reportVideo(val, "", "", "", "");
                                }
                            } else if (typeof val === 'object' && val !== null) {
                                parseObjectForStreams(val, depth + 1);
                            }
                        } catch(e) {}
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
                    var sources = v.getElementsByTagName ? v.getElementsByTagName('source') : [];
                    for (var j = 0; j < sources.length; j++) {
                        if (sources[j].src) {
                            reportVideo(sources[j].src, poster, "", resolution, sizeText);
                        }
                    }
                }

                // Deep Shadow DOM and iFrame recursive scanner
                function deepTraverseElements(root, depth) {
                    if (!root || depth > 6) return;

                    // 1. Direct videos
                    if (root.getElementsByTagName) {
                        var videoTags = root.getElementsByTagName('video');
                        for (var i = 0; i < videoTags.length; i++) {
                            checkVideoElement(videoTags[i]);
                        }
                    }

                    // 2. Direct iframes & embed parameters
                    if (root.getElementsByTagName) {
                        var iframes = root.getElementsByTagName('iframe');
                        for (var m = 0; m < iframes.length; m++) {
                            var ifr = iframes[m];
                            var isrc = ifr.src;
                            if (isrc) {
                                if (videoRegex.test(isrc) || manifestRegex.test(isrc)) {
                                    reportVideo(isrc, "", "", "", "");
                                }
                                checkUrlParametersForStreams(isrc);
                            }
                            // Attempt recursive same-origin child iframe scan
                            try {
                                var childDoc = ifr.contentDocument || (ifr.contentWindow && ifr.contentWindow.document);
                                if (childDoc && childDoc !== root) {
                                    deepTraverseElements(childDoc, depth + 1);
                                }
                            } catch(e) {}
                        }
                    }

                    // 3. Shadow DOM traversal
                    if (root.querySelectorAll) {
                        var allEls = root.querySelectorAll('*');
                        for (var k = 0; k < allEls.length; k++) {
                            var el = allEls[k];
                            if (el.shadowRoot) {
                                deepTraverseElements(el.shadowRoot, depth + 1);
                            }
                        }
                    }
                }

                function checkUrlParametersForStreams(urlStr) {
                    try {
                        var questionIdx = urlStr.indexOf('?');
                        if (questionIdx === -1) return;
                        var qs = urlStr.substring(questionIdx + 1);
                        var pairs = qs.split('&');
                        for (var i = 0; i < pairs.length; i++) {
                            var p = pairs[i].split('=');
                            if (p.length === 2) {
                                var val = decodeURIComponent(p[1]);
                                if ((videoRegex.test(val) || manifestRegex.test(val)) && !segmentRegex.test(val)) {
                                    reportVideo(val, "", "", "", "");
                                }
                            }
                        }
                    } catch(e) {}
                }

                function scanDocument() {
                    deepTraverseElements(document, 0);

                    // Scan links that directly link to media files
                    var aTags = document.getElementsByTagName('a');
                    for (var k = 0; k < aTags.length; k++) {
                        var href = aTags[k].href;
                        if (href && (videoRegex.test(href) || manifestRegex.test(href))) {
                            reportVideo(href, "", "", "", "");
                        }
                    }
                }

                window.__castbrowseScan = scanDocument;
                scanDocument();

                // Dynamic playback interception: Hook HTMLMediaElement prototype
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

                // Hook Element.prototype.attachShadow for web components
                try {
                    var origAttachShadow = Element.prototype.attachShadow;
                    if (origAttachShadow) {
                        Element.prototype.attachShadow = function() {
                            var sRoot = origAttachShadow.apply(this, arguments);
                            try {
                                var shadowObserver = new MutationObserver(function(muts) {
                                    for (var i = 0; i < muts.length; i++) {
                                        for (var j = 0; j < muts[i].addedNodes.length; j++) {
                                            var n = muts[i].addedNodes[j];
                                            if (n && n.nodeType === 1) {
                                                if (n.tagName === 'VIDEO') checkVideoElement(n);
                                                else deepTraverseElements(n, 0);
                                            }
                                        }
                                    }
                                });
                                shadowObserver.observe(sRoot, { childList: true, subtree: true });
                            } catch(e) {}
                            return sRoot;
                        };
                    }
                } catch(e) {}

                // Media event listeners
                document.addEventListener('play', function(e) { checkVideoElement(e.target); }, true);
                document.addEventListener('loadeddata', function(e) { checkVideoElement(e.target); }, true);
                document.addEventListener('canplay', function(e) { checkVideoElement(e.target); }, true);

                // MutationObserver for DOM changes
                try {
                    var observer = new MutationObserver(function(mutations) {
                        for (var i = 0; i < mutations.length; i++) {
                            var mut = mutations[i];
                            for (var j = 0; j < mut.addedNodes.length; j++) {
                                var node = mut.addedNodes[j];
                                if (node && node.nodeType === 1) {
                                    if (node.tagName === 'VIDEO') {
                                        checkVideoElement(node);
                                    } else if (node.tagName === 'IFRAME') {
                                        if (node.src) {
                                            if (videoRegex.test(node.src) || manifestRegex.test(node.src)) {
                                                reportVideo(node.src, "", "", "", "");
                                            }
                                            checkUrlParametersForStreams(node.src);
                                        }
                                    } else {
                                        deepTraverseElements(node, 0);
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

        val scheme = request.url?.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "javascript") {
            Log.d(TAG, "Blocked external scheme navigation: $url")
            return true
        }
        
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
                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
            }

            // DRM Signature check in network requests
            val lowerUrl = url.lowercase()
            if (lowerUrl.contains("widevine") || lowerUrl.contains("license.uat") || lowerUrl.contains("/widevine/")) {
                onDrmDetected?.invoke("Widevine DRM", url)
            } else if (lowerUrl.contains("playready")) {
                onDrmDetected?.invoke("PlayReady DRM", url)
            } else if (lowerUrl.contains("fairplay")) {
                onDrmDetected?.invoke("FairPlay DRM", url)
            }

            // Cache request headers for anti-hotlink proxy and downloads
            val headers = request.requestHeaders?.toMutableMap() ?: mutableMapOf()
            val cookies = try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (e: Exception) { null }
            if (!cookies.isNullOrEmpty() && !headers.containsKey("Cookie")) {
                headers["Cookie"] = cookies
            }
            if (headers.isNotEmpty()) {
                LocalMediaProxy.registerUrlHeaders(url, headers)
            }

            // Deep query parameter unpacking: extract nested streaming URLs and base64 strings
            inspectQueryParameters(request.url)

            if (isMediaUrl(url, headers)) {
                val filename = extractFilenameFromUrl(url)
                val isDrm = lowerUrl.contains("widevine") || lowerUrl.contains("playready") || lowerUrl.contains("fairplay")
                val drmSystem = if (isDrm) (if (lowerUrl.contains("widevine")) "Widevine" else "PlayReady") else null
                onMediaDiscovered(ExtractedVideo(
                    url = url,
                    title = filename,
                    isDrmProtected = isDrm,
                    drmKeySystem = drmSystem
                ))
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun inspectQueryParameters(uri: Uri?) {
        if (uri == null || !uri.isHierarchical) return
        try {
            for (paramName in uri.queryParameterNames) {
                val paramVal = uri.getQueryParameter(paramName) ?: continue
                if (paramVal.startsWith("http://") || paramVal.startsWith("https://")) {
                    if (isMediaUrl(paramVal)) {
                        val clean = extractFilenameFromUrl(paramVal)
                        onMediaDiscovered(ExtractedVideo(url = paramVal, title = clean))
                    }
                } else if (paramVal.length > 20 && !paramVal.contains(" ")) {
                    // Try decoding base64-encoded stream parameter
                    try {
                        val decoded = String(Base64.decode(paramVal, Base64.DEFAULT), Charsets.UTF_8)
                        if ((decoded.startsWith("http://") || decoded.startsWith("https://")) && isMediaUrl(decoded)) {
                            val clean = extractFilenameFromUrl(decoded)
                            onMediaDiscovered(ExtractedVideo(url = decoded, title = clean))
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
    }

    private fun isAdHost(host: String): Boolean {
        if (adHostsSet.isNotEmpty()) {
            var current = host
            while (current.contains(".")) {
                if (adHostsSet.contains(current)) return true
                current = current.substringAfter(".")
            }
            if (adHostsSet.contains(current)) return true
        }
        if (AD_DOMAINS.contains(host)) return true
        for (adDomain in AD_DOMAINS) {
            if (host.endsWith(".$adDomain")) return true
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            onPageStarted(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(DOM_SCRAPER_SCRIPT, null)

        if (isAdBlockEnabled()) {
            view?.evaluateJavascript(COSMETIC_ADBLOCK_CSS, null)
        }
        
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

    private fun isMediaUrl(url: String, headers: Map<String, String>? = null): Boolean {
        if (isSegmentUrl(url)) {
            return false
        }
        val path = try { Uri.parse(url).path ?: "" } catch (e: Exception) { "" }
        if (MEDIA_REGEX.containsMatchIn(path) || DYNAMIC_STREAM_REGEX.containsMatchIn(url)) {
            return true
        }
        val accept = headers?.get("Accept") ?: headers?.get("accept")
        if (accept != null && (accept.contains("video/") || accept.contains("mpegurl") || accept.contains("dash+xml"))) {
            return true
        }
        return false
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
     * Securely decodes extracted streams and receives EME DRM detection events.
     */
    class WebAppInterface(
        private val onVideosFound: (List<ExtractedVideo>) -> Unit,
        private val onDrmDetected: ((keySystem: String, url: String) -> Unit)? = null
    ) {
        @JavascriptInterface
        fun postMessage(json: String) {
            try {
                val list = Json.decodeFromString<List<ExtractedVideo>>(json)
                onVideosFound(list)
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing extracted videos JSON", e)
            }
        }

        @JavascriptInterface
        fun postDrmDetected(keySystem: String, url: String) {
            onDrmDetected?.invoke(keySystem, url)
        }
    }
}
