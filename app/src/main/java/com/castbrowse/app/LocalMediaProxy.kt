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
                val conn = URL(redirectUrl).openConnection() as HttpURLConnection
                conn.requestMethod = if (method == "HEAD") "HEAD" else "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = false

                // Attach registered custom headers (Referer, Cookie, User-Agent, Origin, Sec-Fetch-*) to bypass 403
                val registeredHeaders = getHeadersForUrl(redirectUrl) ?: getHeadersForUrl(targetUrl)
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
                    if (registeredHeaders?.containsKey(key) != true) {
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
<title>CastBrowse Web Receiver</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body, html { width: 100%; height: 100%; background: #000; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; overflow: hidden; }
  #player-container { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
  video { width: 100%; height: 100%; object-fit: contain; background: #000; }
  #idle-screen { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: radial-gradient(circle at center, #181c24 0%, #08090c 100%); z-index: 10; text-align: center; padding: 24px; }
  .logo { font-size: 3.2rem; font-weight: 900; letter-spacing: -1px; background: linear-gradient(135deg, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; margin-bottom: 12px; }
  .status-pill { display: inline-flex; align-items: center; gap: 8px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15); border-radius: 9999px; padding: 6px 18px; font-size: 1rem; color: #93c5fd; margin-bottom: 24px; }
  .status-dot { width: 10px; height: 10px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 12px #22c55e; animation: pulse 2s infinite; }
  @keyframes pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.5; transform: scale(0.85); } }
  .instructions { color: rgba(255,255,255,0.7); font-size: 1.25rem; max-width: 600px; line-height: 1.6; }
  .instructions b { color: #fff; }
  #osd { position: absolute; bottom: 40px; left: 40px; right: 40px; background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.1); border-radius: 16px; padding: 18px 24px; z-index: 20; opacity: 0; transition: opacity 0.4s ease; pointer-events: none; }
  #osd.show { opacity: 1; }
  #osd-title { font-size: 1.4rem; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 6px; }
  #osd-time { font-size: 0.95rem; color: #94a3b8; font-family: monospace; }
</style>
</head>
<body>
<div id="player-container">
  <div id="idle-screen">
    <div class="logo">CastBrowse</div>
    <div class="status-pill"><div class="status-dot"></div> Web Receiver Ready</div>
    <p class="instructions">Keep this browser tab open on your TV.<br>In CastBrowse, select <b>Web Receiver</b> or cast any video link to start watching.</p>
  </div>
  <img id="photo" style="display:none; width:100%; height:100%; object-fit:contain; background:#000; position:absolute; inset:0; z-index:5;" alt="Photo" />
  <video id="video" playsinline webkit-playsinline></video>
  <div id="osd">
    <div id="osd-title">Media Stream</div>
    <div id="osd-time">00:00 / 00:00</div>
  </div>
</div>
<script>
  const video = document.getElementById('video');
  const photo = document.getElementById('photo');
  const idleScreen = document.getElementById('idle-screen');
  const osd = document.getElementById('osd');
  const osdTitle = document.getElementById('osd-title');
  const osdTime = document.getElementById('osd-time');

  let currentUrl = '';
  let lastCommandVersion = -1;
  let osdTimeout = null;

  function showOsd() {
    osd.classList.add('show');
    clearTimeout(osdTimeout);
    osdTimeout = setTimeout(() => osd.classList.remove('show'), 3500);
  }

  function formatTime(sec) {
    if (!sec || isNaN(sec)) return "00:00";
    const s = Math.floor(sec % 60);
    const m = Math.floor((sec / 60) % 60);
    const h = Math.floor(sec / 3600);
    const pad = n => String(n).padStart(2, '0');
    return h > 0 ? pad(h) + ':' + pad(m) + ':' + pad(s) : pad(m) + ':' + pad(s);
  }

  video.addEventListener('timeupdate', () => {
    osdTime.textContent = formatTime(video.currentTime) + ' / ' + formatTime(video.duration);
  });

  video.addEventListener('play', () => { idleScreen.style.display = 'none'; showOsd(); });
  video.addEventListener('pause', () => showOsd());
  document.addEventListener('mousemove', () => showOsd());
  document.addEventListener('keydown', (e) => {
    showOsd();
    if (e.key === ' ' || e.key === 'MediaPlayPause') {
      video.paused ? video.play() : video.pause();
    } else if (e.key === 'ArrowRight') {
      video.currentTime += 10;
    } else if (e.key === 'ArrowLeft') {
      video.currentTime = Math.max(0, video.currentTime - 10);
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
            osd.style.display = 'none';
            photo.style.display = 'none';
            video.style.opacity = '0';
          } else {
            osd.style.display = 'block';
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
