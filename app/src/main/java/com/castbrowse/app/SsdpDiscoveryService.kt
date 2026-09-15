package com.castbrowse.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

enum class CastProtocol {
    FCAST,
    DLNA,
    WEB_RECEIVER
}

data class CastDevice(
    val name: String,
    val ipAddress: String,
    val port: Int = FCastClient.FCAST_DEFAULT_PORT,
    val protocol: CastProtocol = CastProtocol.FCAST,
    val controlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val modelName: String? = null
)

class SsdpDiscoveryService(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "SsdpDiscoveryService"
        private const val FCAST_SERVICE_TYPE = "_fcast._tcp."
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }

    /**
     * Backward-compatible alias for universal device discovery.
     */
    fun discoverFcastDevices(): Flow<List<CastDevice>> = discoverUniversalDevices()

    /**
     * Discovers all supported casting devices on the local network:
     * 1. FCast receivers via mDNS / DNS-SD
     * 2. DLNA / UPnP MediaRenderers via SSDP multicast (Samsung, LG, Sony, Roku, etc.)
     * 3. Connected HTML5 Web Receivers via LocalMediaProxy
     */
    fun discoverUniversalDevices(): Flow<List<CastDevice>> = callbackFlow {
        val discoveredDevices = mutableListOf<CastDevice>()
        val multicastLock = try {
            wifiManager?.createMulticastLock("CastBrowseSSDP")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock", e)
            null
        }

        fun updateAndEmit(device: CastDevice) {
            synchronized(discoveredDevices) {
                val existingIndex = discoveredDevices.indexOfFirst { it.ipAddress == device.ipAddress }
                if (existingIndex != -1) {
                    // Prefer DLNA/FCast with enriched names over generic ones
                    val existing = discoveredDevices[existingIndex]
                    if (existing.name.startsWith("FCast") && !device.name.startsWith("FCast")) {
                        discoveredDevices[existingIndex] = device
                    }
                } else {
                    discoveredDevices.add(device)
                }
                trySend(discoveredDevices.toList())
            }
        }

        // Add any currently connected Web Receivers immediately
        WebReceiverController.getConnectedReceivers().forEach {
            updateAndEmit(it)
        }

        // --- 1. mDNS Discovery for FCast ---
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "mDNS discovery started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "mDNS discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo != null) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.e(TAG, "mDNS Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                            val host = resolvedInfo?.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            val device = CastDevice(
                                name = resolvedInfo.serviceName ?: "FCast Receiver",
                                ipAddress = host,
                                port = if (port > 0) port else FCastClient.FCAST_DEFAULT_PORT,
                                protocol = CastProtocol.FCAST
                            )
                            updateAndEmit(device)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo != null) {
                    synchronized(discoveredDevices) {
                        discoveredDevices.removeAll { it.name == serviceInfo.serviceName }
                        trySend(discoveredDevices.toList())
                    }
                }
            }
        }

        try {
            nsdManager.discoverServices(FCAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD for FCast", e)
        }

        // --- 2. SSDP Multicast Discovery for DLNA / UPnP ---
        var ssdpJob: Job? = CoroutineScope(Dispatchers.IO).launch {
            try {
                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                MulticastSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(0))
                    socket.soTimeout = 4000

                    val searchTargets = listOf(
                        "urn:schemas-upnp-org:device:MediaRenderer:1",
                        "urn:schemas-upnp-org:service:AVTransport:1"
                    )

                    for (target in searchTargets) {
                        val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 3\r\n" +
                                "ST: $target\r\n\r\n"
                        val data = mSearch.toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(data, data.size, group, SSDP_PORT)
                        socket.send(packet)
                    }

                    val buffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    val endTime = System.currentTimeMillis() + 6000

                    val parsedLocations = mutableSetOf<String>()

                    while (System.currentTimeMillis() < endTime) {
                        try {
                            socket.receive(recvPacket)
                            val response = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                            val location = extractHeader(response, "LOCATION")
                            if (!location.isNullOrEmpty() && parsedLocations.add(location)) {
                                launch(Dispatchers.IO) {
                                    val dlnaDevice = resolveDlnaDevice(location)
                                    if (dlnaDevice != null) {
                                        updateAndEmit(dlnaDevice)
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Timeout expected when no more packets arrive
                            break
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during SSDP DLNA scan", e)
            }
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping NSD", e)
            }
            ssdpJob?.cancel()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing multicast lock", e)
            }
        }
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.lines()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx != -1) {
                val key = line.substring(0, colonIdx).trim()
                if (key.equals(headerName, ignoreCase = true)) {
                    return line.substring(colonIdx + 1).trim()
                }
            }
        }
        return null
    }

    private fun resolveDlnaDevice(locationUrl: String): CastDevice? {
        return try {
            val request = Request.Builder().url(locationUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val xml = response.body?.string() ?: return null

            val friendlyName = extractXmlValue(xml, "friendlyName") ?: return null
            val modelName = extractXmlValue(xml, "modelName")

            val uri = URI(locationUrl)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 80

            // Extract AVTransport controlURL
            val avControlRelative = extractControlUrlForService(xml, "urn:schemas-upnp-org:service:AVTransport:1") ?: return null
            val fullControlUrl = resolveUrl(locationUrl, avControlRelative)

            // Extract RenderingControl controlURL (optional, for volume)
            val renderingControlRelative = extractControlUrlForService(xml, "urn:schemas-upnp-org:service:RenderingControl:1")
            val fullRenderingUrl = renderingControlRelative?.let { resolveUrl(locationUrl, it) }

            CastDevice(
                name = friendlyName,
                ipAddress = host,
                port = port,
                protocol = CastProtocol.DLNA,
                controlUrl = fullControlUrl,
                renderingControlUrl = fullRenderingUrl,
                modelName = modelName
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractXmlValue(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = xml.indexOf(startTag)
        if (start == -1) return null
        val end = xml.indexOf(endTag, start + startTag.length)
        if (end == -1) return null
        return xml.substring(start + startTag.length, end).trim()
    }

    private fun extractControlUrlForService(xml: String, serviceType: String): String? {
        var idx = xml.indexOf(serviceType)
        while (idx != -1) {
            val serviceBlockEnd = xml.indexOf("</service>", idx)
            if (serviceBlockEnd != -1) {
                val serviceBlock = xml.substring(idx, serviceBlockEnd)
                val controlUrl = extractXmlValue(serviceBlock, "controlURL")
                if (!controlUrl.isNullOrEmpty()) {
                    return controlUrl
                }
            }
            idx = xml.indexOf(serviceType, idx + serviceType.length)
        }
        return null
    }

    private fun resolveUrl(baseUrl: String, relative: String): String {
        return try {
            URI(baseUrl).resolve(relative).toString()
        } catch (e: Exception) {
            if (relative.startsWith("/")) {
                val u = URL(baseUrl)
                "${u.protocol}://${u.host}:${if (u.port > 0) u.port else u.defaultPort}$relative"
            } else {
                relative
            }
        }
    }
}
