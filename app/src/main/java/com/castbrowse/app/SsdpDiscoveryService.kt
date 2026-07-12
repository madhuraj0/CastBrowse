package com.castbrowse.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class CastDevice(
    val name: String,
    val ipAddress: String,
    val port: Int = FCastClient.FCAST_DEFAULT_PORT
)

class SsdpDiscoveryService(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        private const val TAG = "SsdpDiscoveryService"
        private const val FCAST_SERVICE_TYPE = "_fcast._tcp."
    }

    /**
     * Listens for local FCast devices via Zeroconf/mDNS broadcasts.
     */
    fun discoverFcastDevices(): Flow<List<CastDevice>> = callbackFlow {
        val discoveredDevices = mutableListOf<CastDevice>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery start failed: $errorCode")
                close(Exception("mDNS start failed"))
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
                                port = if (port > 0) port else FCastClient.FCAST_DEFAULT_PORT
                            )
                            if (discoveredDevices.none { it.ipAddress == device.ipAddress }) {
                                discoveredDevices.add(device)
                                trySend(discoveredDevices.toList())
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo != null) {
                    discoveredDevices.removeAll { it.name == serviceInfo.serviceName }
                    trySend(discoveredDevices.toList())
                }
            }
        }

        try {
            nsdManager.discoverServices(FCAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD", e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping NSD", e)
            }
        }
    }
}
