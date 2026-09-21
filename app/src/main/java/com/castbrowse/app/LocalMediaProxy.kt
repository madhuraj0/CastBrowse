package com.castbrowse.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object LocalMediaProxy {
    private const val TAG = "LocalMediaProxy"
    const val DEFAULT_PROXY_PORT = 8085

    @Volatile
    var proxyPort: Int = DEFAULT_PROXY_PORT
        private set

    @Volatile
    var activeSubtitleContent: String? = null

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    
    // Store the base URL of the last proxied media request to resolve relative paths (like HLS segments)
    @Volatile
    private var lastProxyBaseUrl: String? = null

    // URL to custom headers map for passing Referer, Cookie, Origin, and User-Agent upstream
    private val urlHeadersMap = ConcurrentHashMap<String, Map<String, String>>()

    data class LocalMediaItem(
        val id: String,
        val uri: android.net.Uri,
        val title: String,
        val mimeType: String,
        val size: Long
    )

    private var appContext: Context? = null
    private val localMediaRegistry = ConcurrentHashMap<String, LocalMediaItem>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun registerLocalMedia(
        uri: android.net.Uri,
        title: String,
        mimeType: String = "video/mp4",
        size: Long = 0L,
        receiverIp: String? = null
    ): String {
        start()
        val id = java.util.UUID.randomUUID().toString().substring(0, 8)
        localMediaRegistry[id] = LocalMediaItem(id, uri, title, mimeType, size)
        val ip = getLocalIpAddress(receiverIp)
        return "http://$ip:$proxyPort/local?id=$id"
    }

    @Volatile
    var verifiedLocalIp: String? = null

    fun registerUrlHeaders(targetUrl: String, headers: Map<String, String>) {
        urlHeadersMap[targetUrl] = headers
        try {
            val uri = URI(targetUrl)
            val path = uri.path ?: ""
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash != -1) {
                val base = targetUrl.substringBefore(path) + path.substring(0, lastSlash + 1)
                urlHeadersMap[base] = headers
            }
            uri.host?.let { host ->
                urlHeadersMap["host:$host"] = headers
            }
        } catch (e: Exception) {
            // Ignore URI parsing issues
        }
    }

    private fun getHeadersForUrl(url: String): Map<String, String>? {
        urlHeadersMap[url]?.let { return it }
        try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash != -1) {
                val base = url.substringBefore(path) + path.substring(0, lastSlash + 1)
                urlHeadersMap[base]?.let { return it }
            }
            uri.host?.let { host ->
                urlHeadersMap["host:$host"]?.let { return it }
            }
        } catch (e: Exception) {}
        return lastProxyBaseUrl?.let { urlHeadersMap[it] }
    }

    @Synchronized
    fun start() {
        if (serverSocket != null && serverSocket?.isClosed == false) return
        try {
            val ss = try {
                ServerSocket(DEFAULT_PROXY_PORT)
            } catch (e: Exception) {
                Log.w(TAG, "Default port $DEFAULT_PROXY_PORT busy, binding ephemeral port: ${e.message}")
                ServerSocket(0)
            }
            serverSocket = ss
            proxyPort = ss.localPort
            Log.d(TAG, "LocalMediaProxy server started on port $proxyPort")

            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                while (serverSocket != null && serverSocket?.isClosed == false) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        try {
                            socket.tcpNoDelay = true
                        } catch (e: Exception) {}
                        launch(Dispatchers.IO) {
                            handleConnection(socket)
                        }
                    } catch (e: Exception) {
                        if (serverSocket?.isClosed == true) break
                        Log.w(TAG, "LocalMediaProxy accept error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LocalMediaProxy start error: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        job?.cancel()
        job = null
        urlHeadersMap.clear()
        lastProxyBaseUrl = null
        verifiedLocalIp = null
    }

    fun getLocalIpAddress(targetReceiverIp: String? = null): String {
        // 0. Check if device is acting as Wi-Fi Hotspot / AP
        val hotspotIp = NetworkDiagnostics.getHotspotIp()
        if (hotspotIp != null && (targetReceiverIp == null || targetReceiverIp.startsWith("192.168.43.") || targetReceiverIp.startsWith("192.168.49."))) {
            return hotspotIp
        }

        // 1. If we have a verified local IP from an active socket connection to the receiver
        verifiedLocalIp?.let { return it }

        // 2. Query kernel routing table via UDP connect to the target receiver (no actual network packets sent)
        if (!targetReceiverIp.isNullOrEmpty()) {
            try {
                java.net.DatagramSocket().use { s ->
                    s.connect(java.net.InetAddress.getByName(targetReceiverIp), 53)
                    val addr = s.localAddress?.hostAddress
                    if (!addr.isNullOrEmpty() && addr != "0.0.0.0" && !addr.startsWith("127.")) {
                        return addr
                    }
                }
            } catch (e: Exception) {}
        }

        // 3. Fallback UDP connect to default outbound route
        try {
            java.net.DatagramSocket().use { s ->
                s.connect(java.net.InetAddress.getByName("8.8.8.8"), 53)
                val addr = s.localAddress?.hostAddress
                if (!addr.isNullOrEmpty() && addr != "0.0.0.0" && !addr.startsWith("127.")) {
                    return addr
                }
            }
        } catch (e: Exception) {}

        // 4. Inspect network interfaces prioritizing active Wi-Fi and Ethernet
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Pass A: Up and running Wi-Fi / Ethernet interfaces
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("eth") || name.contains("en")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val sAddr = addr.hostAddress ?: continue
                            if (sAddr.isNotEmpty() && !sAddr.startsWith("127.")) return sAddr
                        }
                    }
                }
            }
            // Pass B: Fallback to any non-cellular IPv4 address
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.contains("rmnet") || name.contains("ccmni") || name.contains("tun") || name.contains("dummy")) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val sAddr = addr.hostAddress ?: continue
                        if (sAddr.isNotEmpty() && !sAddr.startsWith("127.")) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP address", ex)
        }
        return "127.0.0.1"
    }

    fun getProxyUrl(targetUrl: String, headers: Map<String, String>? = null, receiverIp: String? = null): String {
        start() // Guarantee proxy server is bound and running
        if (headers != null && headers.isNotEmpty()) {
            registerUrlHeaders(targetUrl, headers)
        }
        val ip = getLocalIpAddress(receiverIp)
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
        return "http://$ip:$proxyPort/proxy?url=$encodedUrl"
    }

    fun openUpstreamConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val context = appContext
        if (context != null) {
            val isVpn = NetworkDiagnostics.isVpnActive(context)
            if (!isVpn) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    if (cm != null) {
                        val wifiNetwork = cm.allNetworks.firstOrNull { net ->
                            val caps = cm.getNetworkCapabilities(net)
                            caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        }
                        if (wifiNetwork != null) {
                            return wifiNetwork.openConnection(url) as HttpURLConnection
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed binding upstream to Wi-Fi network: ${e.message}")
                }
            } else {
                Log.d(TAG, "VPN is active: preserving VPN route for leak-proof streaming of geo/IP-locked content")
            }
        }
        return url.openConnection() as HttpURLConnection
    }

    fun resolveDnsFallback(host: String): String? {
        if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return host
        return try {
            java.net.DatagramSocket().use { s ->
                s.soTimeout = 2000
                val packet = java.io.ByteArrayOutputStream().apply {
                    write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    for (part in host.split('.')) {
                        val b = part.toByteArray(Charsets.US_ASCII)
                        write(b.size)
                        write(b)
                    }
                    write(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01))
                }.toByteArray()
                val dest = java.net.InetSocketAddress("8.8.8.8", 53)
                s.send(java.net.DatagramPacket(packet, packet.size, dest))
                val buf = ByteArray(512)
                val resp = java.net.DatagramPacket(buf, buf.size)
                s.receive(resp)
                val data = resp.data
                val ancount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
                if (ancount == 0) return null
                var idx = 12
                while (idx < data.size && data[idx].toInt() != 0) {
                    idx += 1 + (data[idx].toInt() and 0xFF)
                }
                idx += 5
                for (i in 0 until ancount) {
                    if (idx >= data.size) break
                    if ((data[idx].toInt() and 0xC0) == 0xC0) {
                        idx += 2
                    } else {
                        while (idx < data.size && data[idx].toInt() != 0) idx += 1 + (data[idx].toInt() and 0xFF)
                        idx += 1
                    }
                    if (idx + 10 > data.size) break
                    val atype = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
                    val rdlen = ((data[idx + 8].toInt() and 0xFF) shl 8) or (data[idx + 9].toInt() and 0xFF)
                    idx += 10
                    if (atype == 1 && rdlen == 4 && idx + 4 <= data.size) {
                        return "${data[idx].toInt() and 0xFF}.${data[idx + 1].toInt() and 0xFF}.${data[idx + 2].toInt() and 0xFF}.${data[idx + 3].toInt() and 0xFF}"
                    }
                    idx += rdlen
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS fallback lookup failed for $host: ${e.message}")
            null
        }
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()
            val requestLines = mutableListOf<String>()
            var line = reader.readLine()
            if (line.isNullOrEmpty()) return
            requestLines.add(line)
            
            // Read headers from receiver client
            val clientHeaders = mutableMapOf<String, String>()
            while (true) {
                line = reader.readLine()
                if (line.isNullOrEmpty()) break
                requestLines.add(line)
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    clientHeaders[key] = value
                }
            }

            val parts = requestLines.first().split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1]
            Log.i(TAG, "Incoming: $method $path from ${socket.inetAddress?.hostAddress}")

            if (path == "/tv" || path == "/tv/") {
                socket.inetAddress?.hostAddress?.let { clientIp ->
                    WebReceiverController.recordHeartbeat(clientIp)
                }
                val html = getTvReceiverHtml()
                val bytes = html.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n").toByteArray())
                out.write(bytes)
                out.flush()
                return
            }

            if (path.startsWith("/tv/api/state")) {
                socket.inetAddress?.hostAddress?.let { clientIp ->
                    WebReceiverController.recordHeartbeat(clientIp)
                }
                val json = WebReceiverController.getStateJson()
                val bytes = json.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n").toByteArray())
                out.write(bytes)
                out.flush()
                return
            }

            if (path.startsWith("/tv/api/progress")) {
                val contentLength = clientHeaders["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = reader.read(bodyChars, read, contentLength - read)
                        if (count == -1) break
                        read += count
                    }
                    val bodyStr = String(bodyChars, 0, read)
                    try {
                        val element = kotlinx.serialization.json.Json.parseToJsonElement(bodyStr)
                        if (element is kotlinx.serialization.json.JsonObject) {
                            WebReceiverController.handleProgressUpdate(element)
                        }
                    } catch (e: Exception) {}
                }
                val out = socket.getOutputStream()
                val response = "{\"ok\":true}"
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${response.length}\r\n" +
                        "Connection: close\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n$response").toByteArray())
                out.flush()
                return
            }

            if (path.startsWith("/subtitles.vtt")) {
                val vtt = activeSubtitleContent ?: "WEBVTT\n\n"
                val bytes = vtt.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/vtt; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n").toByteArray())
                out.write(bytes)
                out.flush()
                return
            }

            if (path.startsWith("/local")) {
                val idParamIndex = path.indexOf("id=")
                if (idParamIndex != -1) {
                    val rawVal = path.substring(idParamIndex + 3)
                    val id = if (rawVal.contains("&")) rawVal.substringBefore("&") else rawVal
                    val localItem = localMediaRegistry[id]
                    if (localItem != null) {
                        handleLocalFile(socket, localItem, method, clientHeaders)
                        return
                    }
                }
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                out.flush()
                return
            }
            
            val targetUrl = if (path.startsWith("/proxy")) {
                val urlParamIndex = path.indexOf("url=")
                if (urlParamIndex == -1) return
                val rawVal = path.substring(urlParamIndex + 4)
                val encodedUrl = if (rawVal.contains("&")) rawVal.substringBefore("&") else rawVal
                val decoded = URLDecoder.decode(encodedUrl, "UTF-8")
                
                // Extract and store the base URL of this target
                try {
                    val uri = URI(decoded)
                    val p = uri.path ?: ""
                    val lastSlash = p.lastIndexOf('/')
                    if (lastSlash != -1) {
                        lastProxyBaseUrl = decoded.substringBefore(p) + p.substring(0, lastSlash + 1)
                        Log.d(TAG, "Updated lastProxyBaseUrl: $lastProxyBaseUrl")
                    }
                } catch (e: Exception) {}
                decoded
            } else {
                // Resolve relative path using stored base URL RFC 3986
                val base = lastProxyBaseUrl
                if (base != null) {
                    try {
                        URI(base).resolve(path).toString()
                    } catch (e: Exception) {
                        if (path.startsWith("/")) {
                            val uri = URI(base)
                            "${uri.scheme}://${uri.authority}$path"
                        } else {
                            base + path
                        }
                    }
                } else {
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
            }
            
            var redirectUrl = targetUrl
            var redirectCount = 0
            var connection: HttpURLConnection? = null
            var responseCode = 0
            
            while (redirectCount < 5) {
                var conn = openUpstreamConnection(redirectUrl)
                conn.requestMethod = if (method == "HEAD") "HEAD" else "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = false

                // Attach registered custom headers (Referer, Cookie, User-Agent, Origin, Sec-Fetch-*) to bypass 403
                val registeredHeaders = getHeadersForUrl(redirectUrl) ?: getHeadersForUrl(targetUrl)
                val regLowerKeys = registeredHeaders?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()
                if (registeredHeaders != null) {
                    for ((key, value) in registeredHeaders) {
                        val lower = key.lowercase()
                        if (lower == "host" || lower == "connection" || lower == "range") continue
                        conn.setRequestProperty(key, value)
                    }
                }
                
                // Forward client headers, excluding Host/Connection/Range
                for ((key, value) in clientHeaders) {
                    val lowerKey = key.lowercase()
                    if (lowerKey == "host" || lowerKey == "connection" || lowerKey == "range") continue
                    if (lowerKey == "user-agent" && regLowerKeys.contains("user-agent")) continue
                    if (!regLowerKeys.contains(lowerKey)) {
                        conn.setRequestProperty(key, value)
                    }
                }
                
                // Forward Range explicitly if requested by client
                val rangeEntry = clientHeaders.entries.firstOrNull { it.key.lowercase() == "range" }
                if (rangeEntry != null) {
                    conn.setRequestProperty("Range", rangeEntry.value)
                }
                
                // Ensure essential CDN security & anti-hotlinking headers are set
                if (conn.getRequestProperty("User-Agent") == null) {
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }
                if (conn.getRequestProperty("Accept") == null) {
                    conn.setRequestProperty("Accept", "*/*")
                }
                if (conn.getRequestProperty("Sec-Fetch-Mode") == null) {
                    conn.setRequestProperty("Sec-Fetch-Mode", "cors")
                }
                if (conn.getRequestProperty("Sec-Fetch-Site") == null) {
                    conn.setRequestProperty("Sec-Fetch-Site", "cross-site")
                }
                if (conn.getRequestProperty("Sec-Fetch-Dest") == null) {
                    conn.setRequestProperty("Sec-Fetch-Dest", "video")
                }
                if (conn.getRequestProperty("Origin") == null) {
                    val ref = conn.getRequestProperty("Referer")
                    if (!ref.isNullOrEmpty()) {
                        try {
                            val refUri = URI(ref)
                            conn.setRequestProperty("Origin", "${refUri.scheme}://${refUri.authority}")
                        } catch (e: Exception) {}
                    }
                }
                
                try {
                    conn.connect()
                    responseCode = conn.responseCode
                } catch (e: Exception) {
                    Log.e(TAG, "Proxy error connecting to $redirectUrl: ${e.javaClass.simpleName} - ${e.message}", e)
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == 307 || responseCode == 308) {
                    
                    val location = conn.getHeaderField("Location")
                    if (location != null) {
                        conn.disconnect()
                        redirectUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            val baseUri = URL(redirectUrl)
                            URL(baseUri, location).toString()
                        }
                        redirectCount++
                        continue
                    }
                }
                connection = conn
                break
            }
            
            if (connection == null) {
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
                out.flush()
                return
            }

            val contentType = connection.contentType?.lowercase() ?: ""
            val isM3u8Manifest = targetUrl.contains(".m3u8", ignoreCase = true) ||
                    targetUrl.contains(".m3u", ignoreCase = true) ||
                    contentType.contains("mpegurl")

            val out = socket.getOutputStream()

            // If manifest, rewrite child URLs to route strictly through this local proxy
            if (method != "HEAD" && responseCode in 200..299 && isM3u8Manifest) {
                val inputStream = connection.inputStream
                val manifestContent = inputStream.bufferedReader().readText()
                inputStream.close()

                val receiverIp = (socket.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress
                val localIp = getLocalIpAddress(receiverIp)
                val proxyBase = "http://$localIp:$proxyPort/proxy?url="
                val registeredHeaders = getHeadersForUrl(targetUrl)

                val keyUriRegex = Regex("""(URI\s*=\s*["'])([^"']+)(["'])""")
                val rewrittenLines = manifestContent.lines().map { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isEmpty() -> rawLine
                        line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP") -> {
                            keyUriRegex.replace(rawLine) { match ->
                                val prefix = match.groupValues[1]
                                val uriVal = match.groupValues[2]
                                val suffix = match.groupValues[3]
                                val resolvedUri = try {
                                    URI(targetUrl).resolve(uriVal).toString()
                                } catch (e: Exception) {
                                    uriVal
                                }
                                if (registeredHeaders != null) {
                                    registerUrlHeaders(resolvedUri, registeredHeaders)
                                }
                                "$prefix$proxyBase${URLEncoder.encode(resolvedUri, "UTF-8")}$suffix"
                            }
                        }
                        line.startsWith("#") -> rawLine
                        else -> {
                            // Segment or sub-playlist URL
                            var resolvedUrl = try {
                                URI(targetUrl).resolve(line).toString()
                            } catch (e: Exception) {
                                if (line.startsWith("http://") || line.startsWith("https://")) line
                                else "${lastProxyBaseUrl ?: ""}$line"
                            }
                            // Inherit query tokens from parent manifest if missing
                            if (!resolvedUrl.contains("?") && targetUrl.contains("?")) {
                                val query = targetUrl.substringAfter("?")
                                resolvedUrl = "$resolvedUrl?$query"
                            }
                            if (registeredHeaders != null) {
                                registerUrlHeaders(resolvedUrl, registeredHeaders)
                            }
                            "$proxyBase${URLEncoder.encode(resolvedUrl, "UTF-8")}"
                        }
                    }
                }

                val rewrittenBody = rewrittenLines.joinToString("\n").toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 $responseCode OK\r\n".toByteArray())
                out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                out.write("Access-Control-Allow-Headers: *\r\n".toByteArray())
                out.write("Content-Type: application/vnd.apple.mpegurl\r\n".toByteArray())
                out.write("Content-Length: ${rewrittenBody.size}\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())
                out.write(rewrittenBody)
                out.flush()
                return
            }
            
            out.write("HTTP/1.1 $responseCode ${connection.responseMessage}\r\n".toByteArray())
            out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
            out.write("Access-Control-Allow-Headers: *\r\n".toByteArray())
            
            for (headerKey in connection.headerFields.keys) {
                if (headerKey == null) continue
                val lowerKey = headerKey.lowercase()
                if (lowerKey == "access-control-allow-origin" || 
                    lowerKey == "access-control-allow-headers" || 
                    lowerKey == "connection" ||
                    lowerKey == "transfer-encoding") continue
                
                val headerValue = connection.getHeaderField(headerKey)
                out.write("$headerKey: $headerValue\r\n".toByteArray())
            }
            if (connection.getHeaderField("Accept-Ranges") == null) {
                out.write("Accept-Ranges: bytes\r\n".toByteArray())
            }
            out.write("Connection: close\r\n\r\n".toByteArray())
            
            if (method != "HEAD") {
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                if (stream != null) {
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    stream.close()
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error proxying request", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun handleLocalFile(
        socket: Socket,
        item: LocalMediaItem,
        method: String,
        clientHeaders: Map<String, String>
    ) {
        val context = appContext
        if (context == null) {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".toByteArray())
            out.flush()
            return
        }

        var pfd: android.os.ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
            val totalLength = if (pfd != null && pfd.statSize > 0) pfd.statSize else item.size
            val out = socket.getOutputStream()

            val rangeHeader = clientHeaders.entries.firstOrNull { it.key.lowercase() == "range" }?.value
            if (rangeHeader != null && rangeHeader.startsWith("bytes=") && totalLength > 0) {
                val rangeVal = rangeHeader.substring(6).trim()
                val parts = rangeVal.split("-")
                val start = parts[0].toLongOrNull() ?: 0L
                val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                    parts[1].toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
                } else {
                    totalLength - 1
                }
                val contentLength = (end - start + 1).coerceAtLeast(0L)

                out.write("HTTP/1.1 206 Partial Content\r\n".toByteArray())
                out.write("Content-Range: bytes $start-$end/$totalLength\r\n".toByteArray())
                out.write("Content-Length: $contentLength\r\n".toByteArray())
                out.write("Content-Type: ${item.mimeType}\r\n".toByteArray())
                out.write("Accept-Ranges: bytes\r\n".toByteArray())
                out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                out.write("Connection: keep-alive\r\n\r\n".toByteArray())

                if (method != "HEAD" && pfd != null) {
                    java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
                        fis.channel.position(start)
                        val buffer = ByteArray(65536)
                        var bytesRemaining = contentLength
                        while (bytesRemaining > 0) {
                            val toRead = bytesRemaining.coerceAtMost(buffer.size.toLong()).toInt()
                            val read = fis.read(buffer, 0, toRead)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            bytesRemaining -= read
                        }
                    }
                }
            } else {
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                if (totalLength > 0) {
                    out.write("Content-Length: $totalLength\r\n".toByteArray())
                }
                out.write("Content-Type: ${item.mimeType}\r\n".toByteArray())
                out.write("Accept-Ranges: bytes\r\n".toByteArray())
                out.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                out.write("Connection: keep-alive\r\n\r\n".toByteArray())

                if (method != "HEAD") {
                    val stream = if (pfd != null) java.io.FileInputStream(pfd.fileDescriptor)
                                 else context.contentResolver.openInputStream(item.uri)
                    stream?.use { input ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error serving local file: ${e.message}")
        } finally {
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    private fun getTvReceiverHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>CastBrowse TV Receiver</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
  body, html { width: 100%; height: 100%; background: #000; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, Helvetica, Arial, sans-serif; overflow: hidden; }
  #player-container { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #000; }
  video { width: 100%; height: 100%; object-fit: contain; background: #000; }

  /* Apple TV / AirPlay Ambient Standby Screen */
  #idle-screen {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: radial-gradient(circle at 50% 35%, #182030 0%, #0c0f17 55%, #050608 100%);
    z-index: 10;
    text-align: center;
    padding: 32px;
  }
  .hero-icon-wrapper {
    width: 96px;
    height: 96px;
    border-radius: 28px;
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(255, 255, 255, 0.14);
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.5), inset 0 1px 1px rgba(255, 255, 255, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24px;
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
  }
  .hero-icon-wrapper svg {
    width: 48px;
    height: 48px;
    color: #38bdf8;
  }
  .receiver-title {
    font-size: 2.8rem;
    font-weight: 800;
    letter-spacing: -0.5px;
    color: #f8fafc;
    margin-bottom: 8px;
  }
  .receiver-subtitle {
    font-size: 1.15rem;
    font-weight: 400;
    color: rgba(255, 255, 255, 0.65);
    max-width: 540px;
    line-height: 1.5;
    margin-bottom: 28px;
  }
  .status-pill {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    background: rgba(34, 197, 94, 0.12);
    border: 1px solid rgba(34, 197, 94, 0.3);
    border-radius: 9999px;
    padding: 8px 22px;
    font-size: 0.95rem;
    font-weight: 600;
    color: #4ade80;
    margin-bottom: 28px;
    letter-spacing: 0.2px;
  }
  .status-dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: #22c55e;
    box-shadow: 0 0 12px #22c55e;
    animation: pulse 2s infinite ease-in-out;
  }
  @keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.45; transform: scale(0.85); }
  }
  .url-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 12px;
    padding: 8px 16px;
    font-size: 0.9rem;
    color: rgba(255, 255, 255, 0.75);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
  .url-badge svg {
    width: 16px;
    height: 16px;
    color: #38bdf8;
  }

  /* Buffering Spinner */
  #spinner {
    display: none;
    position: absolute;
    width: 60px;
    height: 60px;
    border: 4px solid rgba(255, 255, 255, 0.15);
    border-top-color: #0071e3;
    border-radius: 50%;
    animation: spin 0.9s cubic-bezier(0.4, 0, 0.2, 1) infinite;
    z-index: 15;
    pointer-events: none;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  /* OSD Overlay */
  #osd-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 36px 44px;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    z-index: 20;
    background: linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent 24%, transparent 65%, rgba(0,0,0,0.85) 100%);
  }
  #osd-overlay.show { opacity: 1; pointer-events: auto; }

  /* Top Bar */
  .osd-top { display: flex; justify-content: space-between; align-items: center; }
  .top-pill {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    background: rgba(24, 24, 28, 0.75);
    backdrop-filter: blur(24px) saturate(180%);
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 16px;
    padding: 8px 18px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  }
  .top-pill svg { width: 18px; height: 18px; color: #38bdf8; flex-shrink: 0; }
  #osd-title {
    font-size: 1.15rem;
    font-weight: 600;
    color: #f8fafc;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 65vw;
  }

  /* Bottom Floating Pill Player (Apple TV / AirPlay Style) */
  .osd-bottom {
    display: flex;
    flex-direction: column;
    gap: 14px;
    background: rgba(22, 22, 26, 0.82);
    backdrop-filter: blur(32px) saturate(190%);
    -webkit-backdrop-filter: blur(32px) saturate(190%);
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 28px;
    padding: 16px 24px;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.7);
    max-width: 1000px;
    width: 100%;
    margin: 0 auto;
  }

  /* Timeline / Seek Bar */
  .seek-bar-container {
    position: relative;
    width: 100%;
    height: 14px;
    display: flex;
    align-items: center;
    cursor: pointer;
    border-radius: 7px;
    outline: none;
  }
  .seek-track {
    position: relative;
    width: 100%;
    height: 5px;
    background: rgba(255, 255, 255, 0.22);
    border-radius: 4px;
    overflow: hidden;
    transition: height 0.15s ease;
  }
  .seek-bar-container:hover .seek-track, .seek-bar-container:focus .seek-track {
    height: 8px;
  }
  .seek-buffer {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 0%;
    background: rgba(255, 255, 255, 0.35);
    border-radius: 4px;
  }
  .seek-progress {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 0%;
    background: linear-gradient(90deg, #0071e3, #38bdf8);
    border-radius: 4px;
  }
  .seek-thumb {
    position: absolute;
    left: 0%;
    top: 50%;
    transform: translate(-50%, -50%);
    width: 15px;
    height: 15px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
    opacity: 0;
    transition: opacity 0.15s ease;
    pointer-events: none;
  }
  .seek-bar-container:hover .seek-thumb, .seek-bar-container:focus .seek-thumb {
    opacity: 1;
  }

  /* Controls Row */
  .controls-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
  }
  .controls-left, .controls-right {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  /* Apple TV Style Circular Glass Buttons */
  .ctrl-btn {
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #f8fafc;
    border-radius: 50%;
    width: 44px;
    height: 44px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    outline: none;
    transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    flex-shrink: 0;
  }
  .ctrl-btn svg {
    width: 20px;
    height: 20px;
    fill: currentColor;
    stroke: currentColor;
  }
  .ctrl-btn:hover {
    background: rgba(255, 255, 255, 0.18);
    border-color: rgba(255, 255, 255, 0.25);
    transform: scale(1.04);
  }

  /* Primary Play Button */
  .ctrl-btn-primary {
    background: #0071e3;
    border-color: #38bdf8;
    width: 48px;
    height: 48px;
    box-shadow: 0 4px 16px rgba(0, 113, 227, 0.4);
  }
  .ctrl-btn-primary:hover {
    background: #0077ed;
    border-color: #60a5fa;
  }
  .ctrl-btn-primary svg {
    width: 22px;
    height: 22px;
  }

  /* Pill-shaped button (for Aspect Ratio) */
  .ctrl-btn-pill {
    border-radius: 20px;
    padding: 0 14px;
    width: auto;
    gap: 6px;
    font-size: 0.88rem;
    font-weight: 600;
  }
  .ctrl-btn-pill svg {
    width: 16px;
    height: 16px;
  }

  /* Remote D-Pad Focus Indicator for 10-foot TV experience */
  .ctrl-btn:focus, .seek-bar-container:focus, .remote-focusable:focus {
    outline: none !important;
    background: rgba(0, 113, 227, 0.35) !important;
    border-color: #0071e3 !important;
    box-shadow: 0 0 0 3px #0071e3, 0 0 24px rgba(0, 113, 227, 0.65) !important;
    transform: scale(1.1) !important;
  }

  #osd-time {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 0.95rem;
    color: rgba(255, 255, 255, 0.75);
    padding: 0 8px;
    letter-spacing: 0.5px;
  }
  .remote-hint {
    font-size: 0.75rem;
    color: rgba(255, 255, 255, 0.4);
    text-align: center;
    letter-spacing: 0.2px;
  }
</style>
</head>
<body>
<div id="player-container">
  <!-- AirPlay / TV Ambient Standby Screen -->
  <div id="idle-screen">
    <div class="hero-icon-wrapper">
      <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <rect x="4" y="6" width="40" height="26" rx="5"/>
        <polygon points="24,24 16,36 32,36" fill="currentColor"/>
      </svg>
    </div>
    <div class="status-pill"><div class="status-dot"></div> Web Receiver Ready</div>
    <div class="receiver-title">CastBrowse TV</div>
    <p class="receiver-subtitle">Ready to stream media directly from your phone, tablet, or browser.</p>
    <div class="url-badge">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="2" y="7" width="20" height="15" rx="2" ry="2"/>
        <polyline points="17 2 12 7 7 2"/>
      </svg>
      <span id="standby-url">http://.../tv</span>
    </div>
  </div>

  <div id="spinner"></div>
  <img id="photo" style="display:none; width:100%; height:100%; object-fit:contain; background:#000; position:absolute; inset:0; z-index:5;" alt="Photo" />
  <video id="video" playsinline webkit-playsinline></video>

  <!-- Complete TV Receiver Player UI (OSD) -->
  <div id="osd-overlay">
    <div class="osd-top">
      <div class="top-pill">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 17H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-1"/>
          <polygon points="12 15 8 21 16 21" fill="currentColor"/>
        </svg>
        <div id="osd-title">Media Stream</div>
      </div>
      <button id="btn-fullscreen-top" class="ctrl-btn remote-focusable" tabindex="0" title="Toggle Fullscreen" aria-label="Toggle Fullscreen">
        <span id="fs-top-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
          </svg>
        </span>
      </button>
    </div>

    <div class="osd-bottom">
      <!-- Seek Bar -->
      <div id="seek-container" class="seek-bar-container remote-focusable" tabindex="0" role="slider" aria-label="Seek slider" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
        <div class="seek-track">
          <div id="seek-buffer" class="seek-buffer"></div>
          <div id="seek-progress" class="seek-progress"></div>
        </div>
        <div id="seek-thumb" class="seek-thumb"></div>
      </div>

      <!-- Controls Row -->
      <div class="controls-row">
        <div class="controls-left">
          <!-- Play / Pause -->
          <button id="btn-play" class="ctrl-btn ctrl-btn-primary remote-focusable" tabindex="0" title="Play / Pause (OK / Space)" aria-label="Play / Pause">
            <span id="play-icon">
              <svg viewBox="0 0 24 24" fill="currentColor" stroke="none">
                <path d="M8 5.14v13.72a1 1 0 001.5.86l11-6.86a1 1 0 000-1.72l-11-6.86A1 1 0 008 5.14z"/>
              </svg>
            </span>
          </button>
          <!-- Rewind 10s -->
          <button id="btn-rw" class="ctrl-btn remote-focusable" tabindex="0" title="Rewind 10s (Left Arrow)" aria-label="Rewind 10s">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 4V1L8 5l4 4V6a7 7 0 1 1-6.9 8.1"/>
              <text x="12" y="14.5" text-anchor="middle" font-size="7" font-weight="bold" fill="currentColor" stroke="none" font-family="system-ui">10</text>
            </svg>
          </button>
          <!-- Forward 10s -->
          <button id="btn-ff" class="ctrl-btn remote-focusable" tabindex="0" title="Forward 10s (Right Arrow)" aria-label="Forward 10s">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 4V1l4 4-4 4V6a7 7 0 1 0 6.9 8.1"/>
              <text x="12" y="14.5" text-anchor="middle" font-size="7" font-weight="bold" fill="currentColor" stroke="none" font-family="system-ui">10</text>
            </svg>
          </button>
          <div id="osd-time">00:00 / 00:00</div>
        </div>

        <div class="controls-right">
          <!-- Aspect Ratio Toggle -->
          <button id="btn-aspect" class="ctrl-btn ctrl-btn-pill remote-focusable" tabindex="0" title="Cycle Aspect Ratio" aria-label="Cycle Aspect Ratio">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="5" width="18" height="14" rx="2"/>
              <path d="M3 12h18M12 5v14"/>
            </svg>
            <span id="aspect-label">Fit</span>
          </button>
          <!-- Mute / Unmute -->
          <button id="btn-mute" class="ctrl-btn remote-focusable" tabindex="0" title="Mute / Unmute" aria-label="Mute / Unmute">
            <span id="mute-icon">
              <svg viewBox="0 0 24 24" fill="currentColor" stroke="none">
                <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
              </svg>
            </span>
          </button>
          <!-- Fullscreen Toggle -->
          <button id="btn-fullscreen" class="ctrl-btn remote-focusable" tabindex="0" title="Toggle Fullscreen (F)" aria-label="Toggle Fullscreen">
            <span id="fs-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
              </svg>
            </span>
          </button>
        </div>
      </div>

      <div class="remote-hint">Remote: OK: Play/Pause • Arrows: Seek/Navigate • F / Green: Fullscreen • Back: Hide UI</div>
    </div>
  </div>
</div>

<script>
  const video = document.getElementById('video');
  const photo = document.getElementById('photo');
  const idleScreen = document.getElementById('idle-screen');
  const spinner = document.getElementById('spinner');

  const osdOverlay = document.getElementById('osd-overlay');
  const osdTitle = document.getElementById('osd-title');
  const osdTime = document.getElementById('osd-time');
  const standbyUrl = document.getElementById('standby-url');
  if (standbyUrl) {
    standbyUrl.textContent = window.location.href;
  }

  const seekContainer = document.getElementById('seek-container');
  const seekProgress = document.getElementById('seek-progress');
  const seekBuffer = document.getElementById('seek-buffer');
  const seekThumb = document.getElementById('seek-thumb');

  const btnPlay = document.getElementById('btn-play');
  const playIcon = document.getElementById('play-icon');
  const btnRw = document.getElementById('btn-rw');
  const btnFf = document.getElementById('btn-ff');
  const btnAspect = document.getElementById('btn-aspect');
  const aspectLabel = document.getElementById('aspect-label');
  const btnMute = document.getElementById('btn-mute');
  const muteIcon = document.getElementById('mute-icon');
  const btnFullscreen = document.getElementById('btn-fullscreen');
  const fsIcon = document.getElementById('fs-icon');
  const btnFullscreenTop = document.getElementById('btn-fullscreen-top');
  const fsTopIcon = document.getElementById('fs-top-icon');

  // SVG Icon Templates
  const SVG_PLAY = '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M8 5.14v13.72a1 1 0 001.5.86l11-6.86a1 1 0 000-1.72l-11-6.86A1 1 0 008 5.14z"/></svg>';
  const SVG_PAUSE = '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="6" y="5" width="4" height="14" rx="1.5"/><rect x="14" y="5" width="4" height="14" rx="1.5"/></svg>';
  const SVG_SPEAKER = '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>';
  const SVG_MUTED = '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27l4.73 4.73H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>';
  const SVG_FS_ENTER = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>';
  const SVG_FS_EXIT = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 14h6v6M20 10h-6V4M14 10l7-7M10 14l-7 7"/></svg>';

  let currentUrl = '';
  let lastCommandVersion = -1;
  let osdTimeout = null;
  const aspectModes = ['contain', 'cover', 'fill'];
  let currentAspectIdx = 0;

  function showOsd() {
    osdOverlay.classList.add('show');
    clearTimeout(osdTimeout);
    if (!video.paused && video.src && !video.ended) {
      osdTimeout = setTimeout(() => {
        if (!osdOverlay.contains(document.activeElement) || document.activeElement === document.body) {
          osdOverlay.classList.remove('show');
        } else {
          osdTimeout = setTimeout(() => osdOverlay.classList.remove('show'), 5000);
        }
      }, 4000);
    }
  }

  function hideOsd() {
    clearTimeout(osdTimeout);
    osdOverlay.classList.remove('show');
    if (document.activeElement && osdOverlay.contains(document.activeElement)) {
      document.activeElement.blur();
    }
  }

  function formatTime(sec) {
    if (!sec || isNaN(sec)) return "00:00";
    const s = Math.floor(sec % 60);
    const m = Math.floor((sec / 60) % 60);
    const h = Math.floor(sec / 3600);
    const pad = n => String(n).padStart(2, '0');
    return h > 0 ? pad(h) + ':' + pad(m) + ':' + pad(s) : pad(m) + ':' + pad(s);
  }

  function updateProgressBar() {
    if (!video.duration || isNaN(video.duration)) {
      seekProgress.style.width = '0%';
      seekThumb.style.left = '0%';
      osdTime.textContent = formatTime(video.currentTime) + ' / 00:00';
      return;
    }
    const percent = Math.min(100, Math.max(0, (video.currentTime / video.duration) * 100));
    seekProgress.style.width = percent + '%';
    seekThumb.style.left = percent + '%';
    seekContainer.setAttribute('aria-valuenow', Math.round(percent));
    osdTime.textContent = formatTime(video.currentTime) + ' / ' + formatTime(video.duration);

    if (video.buffered && video.buffered.length > 0) {
      const buffEnd = video.buffered.end(video.buffered.length - 1);
      const buffPercent = Math.min(100, (buffEnd / video.duration) * 100);
      seekBuffer.style.width = buffPercent + '%';
    }
  }

  function togglePlay() {
    if (video.paused || video.ended) {
      video.play().catch(() => {});
    } else {
      video.pause();
    }
  }

  function toggleMute() {
    video.muted = !video.muted;
    muteIcon.innerHTML = video.muted ? SVG_MUTED : SVG_SPEAKER;
  }

  function cycleAspect() {
    currentAspectIdx = (currentAspectIdx + 1) % aspectModes.length;
    const mode = aspectModes[currentAspectIdx];
    video.style.objectFit = mode;
    aspectLabel.textContent = mode === 'contain' ? 'Fit' : mode === 'cover' ? 'Zoom' : 'Fill';
  }

  function isFullscreen() {
    return !!(document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement);
  }

  function toggleFullscreen() {
    const doc = document;
    const elem = document.documentElement;
    if (!isFullscreen()) {
      if (elem.requestFullscreen) {
        elem.requestFullscreen().catch(() => {});
      } else if (elem.webkitRequestFullscreen) {
        elem.webkitRequestFullscreen();
      } else if (elem.mozRequestFullScreen) {
        elem.mozRequestFullScreen();
      } else if (elem.msRequestFullscreen) {
        elem.msRequestFullscreen();
      }
    } else {
      if (doc.exitFullscreen) {
        doc.exitFullscreen().catch(() => {});
      } else if (doc.webkitExitFullscreen) {
        doc.webkitExitFullscreen();
      } else if (doc.mozCancelFullScreen) {
        doc.mozCancelFullScreen();
      } else if (doc.msExitFullscreen) {
        doc.msExitFullscreen();
      }
    }
  }

  function updateFullscreenUi() {
    const fs = isFullscreen();
    const iconHtml = fs ? SVG_FS_EXIT : SVG_FS_ENTER;
    fsIcon.innerHTML = iconHtml;
    fsTopIcon.innerHTML = iconHtml;
    btnFullscreen.title = fs ? 'Exit Fullscreen' : 'Enter Fullscreen';
    btnFullscreenTop.title = fs ? 'Exit Fullscreen' : 'Enter Fullscreen';
  }

  ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange', 'MSFullscreenChange'].forEach(evt => {
    document.addEventListener(evt, updateFullscreenUi);
  });

  // Controls Event Listeners
  btnPlay.addEventListener('click', () => { togglePlay(); showOsd(); });
  btnRw.addEventListener('click', () => { video.currentTime = Math.max(0, video.currentTime - 10); showOsd(); });
  btnFf.addEventListener('click', () => { video.currentTime = Math.min(video.duration || 999999, video.currentTime + 10); showOsd(); });
  btnMute.addEventListener('click', () => { toggleMute(); showOsd(); });
  btnAspect.addEventListener('click', () => { cycleAspect(); showOsd(); });
  btnFullscreen.addEventListener('click', () => { toggleFullscreen(); showOsd(); });
  btnFullscreenTop.addEventListener('click', () => { toggleFullscreen(); showOsd(); });

  seekContainer.addEventListener('click', (e) => {
    if (!video.duration || isNaN(video.duration)) return;
    const rect = seekContainer.getBoundingClientRect();
    const pos = (e.clientX - rect.left) / rect.width;
    video.currentTime = Math.max(0, Math.min(video.duration, pos * video.duration));
    showOsd();
  });

  // Video State Updates
  video.addEventListener('timeupdate', updateProgressBar);
  video.addEventListener('play', () => {
    playIcon.innerHTML = SVG_PAUSE;
    idleScreen.style.display = 'none';
    showOsd();
  });
  video.addEventListener('pause', () => {
    playIcon.innerHTML = SVG_PLAY;
    showOsd();
  });
  video.addEventListener('waiting', () => { spinner.style.display = 'block'; });
  video.addEventListener('playing', () => { spinner.style.display = 'none'; });
  video.addEventListener('canplay', () => { spinner.style.display = 'none'; });

  document.addEventListener('mousemove', () => showOsd());
  document.addEventListener('touchstart', () => showOsd(), { passive: true });

  // Remote Navigation (D-Pad, Media Keys, TV remotes)
  const navItems = Array.from(document.querySelectorAll('.remote-focusable'));

  document.addEventListener('keydown', (e) => {
    showOsd();
    const active = document.activeElement;
    const keyCode = e.keyCode || e.which;

    // Fullscreen key (F or Green button 404/170)
    if (e.key === 'f' || e.key === 'F' || keyCode === 404 || keyCode === 170) {
      toggleFullscreen();
      return;
    }

    // Mute key (M or Yellow button)
    if (e.key === 'm' || e.key === 'M' || keyCode === 405) {
      toggleMute();
      return;
    }

    // Media keys
    if (e.key === 'MediaPlay' || keyCode === 415 || keyCode === 250) {
      video.play().catch(() => {});
      return;
    }
    if (e.key === 'MediaPause' || keyCode === 19) {
      video.pause();
      return;
    }
    if (e.key === ' ' || e.key === 'MediaPlayPause' || keyCode === 10252) {
      togglePlay();
      e.preventDefault();
      return;
    }
    if (e.key === 'MediaStop' || keyCode === 413) {
      video.pause();
      video.currentTime = 0;
      return;
    }
    if (e.key === 'MediaRewind' || keyCode === 412 || keyCode === 227) {
      video.currentTime = Math.max(0, video.currentTime - 15);
      return;
    }
    if (e.key === 'MediaFastForward' || keyCode === 417 || keyCode === 228) {
      video.currentTime = Math.min(video.duration || 999999, video.currentTime + 15);
      return;
    }

    // Back / Return (Escape, Tizen 10009, webOS 461)
    if (e.key === 'Escape' || keyCode === 10009 || keyCode === 461 || keyCode === 27) {
      if (isFullscreen()) {
        toggleFullscreen();
      } else {
        hideOsd();
      }
      return;
    }

    // D-Pad navigation
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (active === seekContainer) {
        btnFullscreenTop.focus();
      } else if (navItems.includes(active)) {
        seekContainer.focus();
      } else {
        btnPlay.focus();
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (active === btnFullscreenTop) {
        seekContainer.focus();
      } else if (active === seekContainer) {
        btnPlay.focus();
      } else if (!navItems.includes(active)) {
        btnPlay.focus();
      }
    } else if (e.key === 'ArrowLeft') {
      if (active === seekContainer) {
        e.preventDefault();
        video.currentTime = Math.max(0, video.currentTime - 10);
      } else if (navItems.includes(active)) {
        e.preventDefault();
        const curIdx = navItems.indexOf(active);
        if (curIdx > 0) navItems[curIdx - 1].focus();
      } else {
        video.currentTime = Math.max(0, video.currentTime - 10);
      }
    } else if (e.key === 'ArrowRight') {
      if (active === seekContainer) {
        e.preventDefault();
        video.currentTime = Math.min(video.duration || 999999, video.currentTime + 10);
      } else if (navItems.includes(active)) {
        e.preventDefault();
        const curIdx = navItems.indexOf(active);
        if (curIdx < navItems.length - 1) navItems[curIdx + 1].focus();
      } else {
        video.currentTime = Math.min(video.duration || 999999, video.currentTime + 10);
      }
    } else if (e.key === 'Enter' || keyCode === 13) {
      if (!navItems.includes(active)) {
        e.preventDefault();
        togglePlay();
      }
    }
  });

  async function pollState() {
    try {
      const res = await fetch('/tv/api/state');
      if (res.ok) {
        const data = await res.json();
        if (data.version !== lastCommandVersion) {
          lastCommandVersion = data.version;

          if (data.mediaType === 'photo' || (data.photoUrl && data.photoUrl.length > 0)) {
            if (photo.src !== data.photoUrl) {
              photo.src = data.photoUrl;
            }
            photo.style.display = 'block';
            video.style.display = 'none';
            idleScreen.style.display = 'none';
            osdTitle.textContent = data.title || 'Photo Slideshow';
            osdTime.textContent = 'Photo Slide';
            showOsd();
          } else if (!data.photoUrl && data.mediaType !== 'photo') {
            photo.style.display = 'none';
            video.style.display = 'block';
          }

          if (data.blackScreen) {
            document.body.style.background = '#000000';
            document.body.style.cursor = 'none';
            idleScreen.style.display = 'none';
            hideOsd();
            photo.style.display = 'none';
            video.style.opacity = '0';
          } else {
            video.style.opacity = '1';
            document.body.style.cursor = 'auto';
          }

          if (data.url && data.url !== currentUrl && data.mediaType !== 'photo') {
            currentUrl = data.url;
            video.src = data.url;
            osdTitle.textContent = data.title || 'Streaming';
            idleScreen.style.display = 'none';
            video.play().catch(() => {});
            if (!data.blackScreen) showOsd();
          }
          if (data.aspectRatio) {
            video.style.objectFit = data.aspectRatio === 'Fill' ? 'fill' : data.aspectRatio === 'Zoom' ? 'cover' : 'contain';
            aspectLabel.textContent = data.aspectRatio;
          }
          if (typeof data.loop === 'boolean') {
            video.loop = data.loop;
          }
          if (data.subtitleUrl) {
            let track = video.querySelector('track');
            if (!track) {
              track = document.createElement('track');
              track.kind = 'subtitles';
              track.default = true;
              video.appendChild(track);
            }
            if (track.src !== data.subtitleUrl) {
              track.src = data.subtitleUrl;
            }
          } else {
            const track = video.querySelector('track');
            if (track) track.remove();
          }
          if (data.command === 'pause') {
            video.pause();
          } else if (data.command === 'resume' || data.command === 'play') {
            if (video.src) video.play().catch(() => {});
          } else if (data.command === 'seek' && typeof data.seekTo === 'number') {
            video.currentTime = data.seekTo;
          } else if (data.command === 'stop') {
            video.pause();
            video.removeAttribute('src');
            video.load();
            currentUrl = '';
            photo.style.display = 'none';
            idleScreen.style.display = 'flex';
          }
        }
      }
    } catch (e) {}

    try {
      if (video.src && currentUrl) {
        const pState = video.ended ? 'ended' : video.paused ? 'paused' : 'playing';
        await fetch('/tv/api/progress', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            currentTime: video.currentTime || 0,
            duration: video.duration || 0,
            state: pState
          })
        });
      }
    } catch (e) {}
  }

  setInterval(pollState, 1000);
  pollState();
</script>
</body>
</html>
        """.trimIndent()
    }
}
