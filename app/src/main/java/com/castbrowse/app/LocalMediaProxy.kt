package com.castbrowse.app

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

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    
    // Store the base URL of the last proxied media request to resolve relative paths (like HLS segments)
    @Volatile
    private var lastProxyBaseUrl: String? = null

    // URL to custom headers map for passing Referer, Cookie, Origin, and User-Agent upstream
    private val urlHeadersMap = ConcurrentHashMap<String, Map<String, String>>()

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
}
