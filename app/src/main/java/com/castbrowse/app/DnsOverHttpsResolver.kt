package com.castbrowse.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance DNS-over-HTTPS (DoH) Resolver.
 * Resolves domain names securely over HTTPS (bypassing ISP hijacking and throttling),
 * caches responses in-memory with TTL, and supports speculative DNS prefetching.
 */
object DnsOverHttpsResolver : Dns {
    private const val TAG = "DnsOverHttpsResolver"

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiryMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Internal client for bootstrap DoH queries (direct IP addresses, no recursion)
    private val dohClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .dns(Dns.SYSTEM)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Fast-path: numeric IPv4 / IPv6 or localhost
        if (hostname.equals("localhost", ignoreCase = true) ||
            hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) ||
            hostname.contains(":")
        ) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        if (cached != null && cached.expiryMs > now) {
            return cached.addresses
        }

        // Try Cloudflare DoH first
        val resolved = resolveViaCloudflare(hostname)
            ?: resolveViaGoogle(hostname)
            ?: fallbackSystem(hostname)

        if (resolved.isNotEmpty()) {
            // Cache for 5 minutes
            cache[hostname] = CacheEntry(resolved, now + TimeUnit.MINUTES.toMillis(5))
        }

        return resolved
    }

    private fun resolveViaCloudflare(hostname: String): List<InetAddress>? {
        return try {
            val url = "https://1.1.1.1/dns-query?name=$hostname&type=A"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/dns-json")
                .build()

            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseDnsJson(hostname, body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaGoogle(hostname: String): List<InetAddress>? {
        return try {
            val url = "https://dns.google/resolve?name=$hostname&type=A"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseDnsJson(hostname, body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDnsJson(hostname: String, json: String): List<InetAddress>? {
        return try {
            val root = JSONObject(json)
            val answers = root.optJSONArray("Answer") ?: return null
            val result = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val item = answers.getJSONObject(i)
                val type = item.optInt("type")
                // Type 1 is DNS 'A' record
                if (type == 1) {
                    val ip = item.optString("data")
                    if (ip.isNotBlank()) {
                        result.add(InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address))
                    }
                }
            }
            if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fallbackSystem(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.w(TAG, "System DNS lookup failed for $hostname: ${e.message}")
            emptyList()
        }
    }

    /**
     * Speculatively prefetch DNS for a hostname in the background.
     */
    fun prefetch(hostname: String) {
        if (hostname.isBlank()) return
        scope.launch {
            try {
                val cleanHost = if (hostname.contains("://")) {
                    android.net.Uri.parse(hostname).host ?: hostname
                } else {
                    hostname.substringBefore("/").substringBefore(":")
                }
                if (cleanHost.contains(".")) {
                    lookup(cleanHost)
                }
            } catch (e: Exception) {}
        }
    }
}
