package com.castbrowse.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

object NetworkDiagnostics {

    private const val TAG = "NetworkDiagnostics"

    data class ReachabilityResult(
        val isReachable: Boolean,
        val latencyMs: Long = 0,
        val message: String
    )

    /**
     * Checks if an active VPN tunnel is detected on the device.
     */
    fun isVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                @Suppress("DEPRECATION")
                val networks = cm.allNetworks
                networks.any { network ->
                    val caps = cm.getNetworkCapabilities(network)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking VPN state", e)
            false
        }
    }

    /**
     * Checks whether the device is currently acting as a Wi-Fi Hotspot or Access Point.
     */
    fun isHotspotActive(): Boolean {
        return getHotspotIp() != null
    }

    /**
     * Detects the Wi-Fi Hotspot IP address (commonly 192.168.43.1 or 192.168.49.1).
     */
    fun getHotspotIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                val isApInterface = name.contains("ap") || name.contains("softap") || name.contains("wlan1") || name.contains("swlan")
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (isApInterface || ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking hotspot interface", e)
        }
        return null
    }

    /**
     * Performs a 1-tap TCP ping / reachability test to the specified host and port.
     */
    suspend fun testReachability(host: String, port: Int, timeoutMs: Int = 2000): ReachabilityResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = System.currentTimeMillis() - startTime
                ReachabilityResult(
                    isReachable = true,
                    latencyMs = latency,
                    message = "Reachable (${latency}ms)"
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            ReachabilityResult(
                isReachable = false,
                message = "Connection timed out (${timeoutMs}ms)"
            )
        } catch (e: java.net.ConnectException) {
            ReachabilityResult(
                isReachable = false,
                message = "Connection refused (Port $port closed or host unreachable)"
            )
        } catch (e: java.net.UnknownHostException) {
            ReachabilityResult(
                isReachable = false,
                message = "Unknown host: $host"
            )
        } catch (e: Exception) {
            ReachabilityResult(
                isReachable = false,
                message = e.localizedMessage ?: "Failed to connect"
            )
        }
    }

    /**
     * Scans the hotspot subnet (192.168.43.2 to 192.168.43.30) for active receivers.
     */
    suspend fun scanHotspotSubnet(
        ports: List<Int> = listOf(FCastClient.FCAST_DEFAULT_PORT, 8080, 7676, 55000)
    ): List<CastDevice> = withContext(Dispatchers.IO) {
        val hotspotIp = getHotspotIp() ?: "192.168.43.1"
        val prefix = hotspotIp.substringBeforeLast(".") + "."
        val found = mutableListOf<CastDevice>()

        coroutineScope {
            val jobs = (2..35).map { hostPart ->
                async {
                    val targetIp = "$prefix$hostPart"
                    for (port in ports) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(targetIp, port), 250)
                                val protocol = when (port) {
                                    FCastClient.FCAST_DEFAULT_PORT -> CastProtocol.FCAST
                                    AirPlayClient.AIRPLAY_DEFAULT_PORT -> CastProtocol.AIRPLAY
                                    else -> CastProtocol.DLNA
                                }
                                val name = when (protocol) {
                                    CastProtocol.FCAST -> "FCast ($targetIp)"
                                    CastProtocol.AIRPLAY -> "AirPlay ($targetIp)"
                                    else -> "DLNA TV ($targetIp)"
                                }
                                synchronized(found) {
                                    if (found.none { it.ipAddress == targetIp }) {
                                        found.add(CastDevice(name = name, ipAddress = targetIp, port = port, protocol = protocol))
                                    }
                                }
                            }
                            break
                        } catch (ignored: Exception) {}
                    }
                }
            }
            jobs.awaitAll()
        }
        found
    }
}
