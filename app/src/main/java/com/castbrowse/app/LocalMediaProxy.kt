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

    // URL to custom headers map for passing Referer, Cookie, and User-Agent upstream
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
        } catch (e: Exception) {
            // Ignore URI parsing issues
        }
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

                // Attach registered custom headers (Referer, Cookie, User-Agent) to bypass 403 anti-hotlinking
                val registeredHeaders = urlHeadersMap[targetUrl] ?: lastProxyBaseUrl?.let { urlHeadersMap[it] }
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
                    conn.setRequestProperty(key, value)
                }
                
                // Forward Range explicitly if requested by client
                val rangeEntry = clientHeaders.entries.firstOrNull { it.key.lowercase() == "range" }
                if (rangeEntry != null) {
                    conn.setRequestProperty("Range", rangeEntry.value)
                }
                
                // Fallback default User-Agent if client and registered headers didn't supply one
                if (conn.getRequestProperty("User-Agent") == null) {
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
            
            val out = socket.getOutputStream()
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
                    val buffer = ByteArray(16384)
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
