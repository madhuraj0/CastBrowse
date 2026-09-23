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
    AIRPLAY,
    DIAL,
    ROKU,
    GOOGLE_CAST,
    WEB_RECEIVER
}

data class CastDevice(
    val name: String,
    val ipAddress: String,
    val port: Int = FCastClient.FCAST_DEFAULT_PORT,
    val protocol: CastProtocol = CastProtocol.FCAST,
    val controlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val modelName: String? = null,
    val applicationUrl: String? = null
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
        private const val AIRPLAY_SERVICE_TYPE = "_airplay._tcp."
        private const val GOOGLECAST_SERVICE_TYPE = "_googlecast._tcp."
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }

    /**
     * Backward-compatible alias for universal device discovery.
     */
    fun discoverFcastDevices(): Flow<List<CastDevice>> = discoverUniversalDevices()

    /**
     * Discovers all supported casting devices on the local network:
     * 1. FCast receivers via mDNS (_fcast._tcp.)
     * 2. AirPlay video receivers via mDNS (_airplay._tcp.)
     * 3. Google Cast / Chromecast via mDNS (_googlecast._tcp.)
     * 4. DLNA / UPnP MediaRenderers via SSDP multicast
     * 5. DIAL Smart TVs (Samsung, LG, Sony, Roku, etc.) via SSDP multicast
     * 6. Connected HTML5 Web Receivers via LocalMediaProxy
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
                    val existing = discoveredDevices[existingIndex]
                    // Prefer DLNA/AirPlay with enriched names over generic entries
                    if (existing.protocol == CastProtocol.FCAST && device.protocol != CastProtocol.FCAST) {
                        discoveredDevices[existingIndex] = device
                    } else if (device.applicationUrl != null && existing.applicationUrl == null) {
                        discoveredDevices[existingIndex] = existing.copy(applicationUrl = device.applicationUrl)
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

        // Helper to register an NSD discovery listener for a given service type
        fun createNsdListener(protocol: CastProtocol, defaultPort: Int, defaultName: String): NsdManager.DiscoveryListener {
            return object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e(TAG, "$serviceType Discovery start failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e(TAG, "$serviceType Discovery stop failed: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.d(TAG, "$serviceType discovery started")
                }

                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.d(TAG, "$serviceType discovery stopped")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo != null) {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.e(TAG, "mDNS Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                            }

                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                val host = resolvedInfo?.host?.hostAddress ?: return
                                val port = resolvedInfo.port
                                val name = resolvedInfo.serviceName ?: defaultName
                                val device = CastDevice(
                                    name = name,
                                    ipAddress = host,
                                    port = if (port > 0) port else defaultPort,
                                    protocol = protocol
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
        }

        val fcastListener = createNsdListener(CastProtocol.FCAST, FCastClient.FCAST_DEFAULT_PORT, "FCast Receiver")
        val airplayListener = createNsdListener(CastProtocol.AIRPLAY, AirPlayClient.AIRPLAY_DEFAULT_PORT, "AirPlay Receiver")
        val googleCastListener = createNsdListener(CastProtocol.GOOGLE_CAST, 8009, "Google Cast Receiver")

        try { nsdManager.discoverServices(FCAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, fcastListener) } catch (e: Exception) {}
        try { nsdManager.discoverServices(AIRPLAY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, airplayListener) } catch (e: Exception) {}
        try { nsdManager.discoverServices(GOOGLECAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, googleCastListener) } catch (e: Exception) {}

        // --- SSDP Multicast Discovery for DLNA / UPnP and DIAL ---
        val ssdpJob: Job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                MulticastSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(0))
                    socket.soTimeout = 4000

                    val searchTargets = listOf(
                        "urn:schemas-upnp-org:device:MediaRenderer:1",
                        "urn:schemas-upnp-org:service:AVTransport:1",
                        "urn:dial-multiscreen-org:service:dial:1",
                        "roku:ecp"
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
                            val appUrl = extractHeader(response, "APPLICATION-URL") ?: extractHeader(response, "X-APPLICATION-URL")
                            if (!location.isNullOrEmpty() && parsedLocations.add(location)) {
                                launch(Dispatchers.IO) {
                                    val dlnaDevice = resolveDlnaDevice(location, appUrl)
                                    if (dlnaDevice != null) {
                                        updateAndEmit(dlnaDevice)
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            break
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during SSDP scan", e)
            }
        }

        awaitClose {
            try { nsdManager.stopServiceDiscovery(fcastListener) } catch (e: Exception) {}
            try { nsdManager.stopServiceDiscovery(airplayListener) } catch (e: Exception) {}
            try { nsdManager.stopServiceDiscovery(googleCastListener) } catch (e: Exception) {}
            ssdpJob.cancel()
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

    private fun resolveDlnaDevice(locationUrl: String, discoveredAppUrl: String? = null): CastDevice? {
        return try {
            val request = Request.Builder().url(locationUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val appUrlHeader = response.header("Application-URL") ?: response.header("X-Application-URL")
            val xml = response.body?.string() ?: return null

            val friendlyName = extractXmlValue(xml, "friendlyName") ?: return null
            val modelName = extractXmlValue(xml, "modelName")

            val uri = URI(locationUrl)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 80

            // Extract AVTransport controlURL (matches AVTransport:1, AVTransport:2, etc.)
            val avControlRelative = extractControlUrlForService(xml, "AVTransport")
            val fullControlUrl = avControlRelative?.let { resolveUrl(locationUrl, it) }

            // Extract RenderingControl controlURL (optional, for volume)
            val renderingControlRelative = extractControlUrlForService(xml, "RenderingControl")
            val fullRenderingUrl = renderingControlRelative?.let { resolveUrl(locationUrl, it) }

            val appUrl = discoveredAppUrl ?: appUrlHeader ?: extractXmlValue(xml, "Application-URL")

            val isRoku = locationUrl.contains(":8060") ||
                    (modelName?.contains("Roku", ignoreCase = true) == true) ||
                    friendlyName.contains("Roku", ignoreCase = true)

            val protocol = when {
                isRoku -> CastProtocol.ROKU
                fullControlUrl != null -> CastProtocol.DLNA
                appUrl != null -> CastProtocol.DIAL
                else -> null
            } ?: return null

            CastDevice(
                name = friendlyName,
                ipAddress = host,
                port = port,
                protocol = protocol,
                controlUrl = fullControlUrl,
                renderingControlUrl = fullRenderingUrl,
                modelName = modelName,
                applicationUrl = appUrl
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

    private fun extractControlUrlForService(xml: String, serviceTypePattern: String): String? {
        var idx = xml.indexOf(serviceTypePattern, ignoreCase = true)
        while (idx != -1) {
            // Find start of <service> block preceding the matched pattern
            val serviceBlockStart = xml.lastIndexOf("<service>", idx).let { if (it == -1) xml.lastIndexOf("<service ", idx) else it }
            val serviceBlockEnd = xml.indexOf("</service>", idx)
            if (serviceBlockStart != -1 && serviceBlockEnd != -1 && serviceBlockEnd > serviceBlockStart) {
                val serviceBlock = xml.substring(serviceBlockStart, serviceBlockEnd)
                val controlUrl = extractXmlValue(serviceBlock, "controlURL")
                if (!controlUrl.isNullOrEmpty()) {
                    return controlUrl
                }
            }
            idx = xml.indexOf(serviceTypePattern, idx + serviceTypePattern.length, ignoreCase = true)
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
