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
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections

object LocalMediaProxy {
    private const val TAG = "LocalMediaProxy"
    const val PROXY_PORT = 8085
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    
    // Store the base URL of the last proxied media request to resolve relative paths (like HLS segments)
    @Volatile
    private var lastProxyBaseUrl: String? = null

    fun start() {
        if (serverSocket != null) return
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PROXY_PORT)
                Log.d(TAG, "Proxy server started on port $PROXY_PORT")
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    try {
                        socket.tcpNoDelay = true
                    } catch (e: Exception) {}
                    launch(Dispatchers.IO) {
                        handleConnection(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proxy server error: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        job?.cancel()
        job = null
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First pass: Prioritize Wi-Fi and Ethernet interfaces
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("eth")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress ?: continue
                            if (sAddr.indexOf(':') < 0) return sAddr
                        }
                    }
                }
            }
            // Second pass: Fallback to any non-loopback IPv4 address
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        if (sAddr.indexOf(':') < 0) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP address", ex)
        }
        return null
    }

    fun getProxyUrl(targetUrl: String): String {
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
        return "http://$ip:$PROXY_PORT/proxy?url=$encodedUrl"
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
            val path = parts[1]
            
            val targetUrl = if (path.startsWith("/proxy")) {
                val urlParamIndex = path.indexOf("url=")
                if (urlParamIndex == -1) return
                val decoded = URLDecoder.decode(path.substring(urlParamIndex + 4), "UTF-8")
                
                // Extract and store the base URL of this target
                try {
                    val lastSlash = decoded.lastIndexOf('/')
                    if (lastSlash != -1) {
                        lastProxyBaseUrl = decoded.substring(0, lastSlash + 1)
                        Log.d(TAG, "Updated lastProxyBaseUrl: $lastProxyBaseUrl")
                    }
                } catch (e: Exception) {}
                decoded
            } else {
                // Resolve relative path using stored base URL
                val base = lastProxyBaseUrl
                if (base != null) {
                    val relPath = if (path.startsWith("/")) path.substring(1) else path
                    base + relPath
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
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false
                
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
                
                // Fallback default User-Agent if client didn't supply one
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
            
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            if (stream != null) {
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                stream.close()
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
