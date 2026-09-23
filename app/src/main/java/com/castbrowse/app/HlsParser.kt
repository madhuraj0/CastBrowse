package com.castbrowse.app

import android.net.Uri
import java.net.URI
import java.util.Locale

data class HlsVariant(
    val url: String,
    val resolution: String,
    val bandwidth: Long,
    val bandwidthLabel: String
)

object HlsParser {

    private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+x\d+)""", RegexOption.IGNORE_CASE)
    private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Parses an HLS Master Playlist manifest string into a list of [HlsVariant] entries.
     * Returns empty list if not a master playlist or if no variants are found.
     */
    fun parseMasterPlaylist(baseUrl: String, manifestContent: String): List<HlsVariant> {
        val lines = manifestContent.lines()
        val variants = mutableListOf<HlsVariant>()

        var currentResolution = ""
        var currentBandwidth = 0L

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                val resMatch = RESOLUTION_REGEX.find(line)
                currentResolution = if (resMatch != null) {
                    val dims = resMatch.groupValues[1]
                    val height = dims.substringAfter("x").toIntOrNull()
                    if (height != null) "${height}p ($dims)" else dims
                } else ""

                val bwMatch = BANDWIDTH_REGEX.find(line)
                currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            } else if (!line.startsWith("#") && (currentResolution.isNotEmpty() || currentBandwidth > 0)) {
                // Resolved variant URL
                val resolvedUrl = try {
                    URI(baseUrl).resolve(line).toString()
                } catch (e: Exception) {
                    if (line.startsWith("http://") || line.startsWith("https://")) line
                    else baseUrl.substringBeforeLast("/") + "/" + line
                }

                // Preserve query parameters if parent URL had tokens
                val finalUrl = if (!resolvedUrl.contains("?") && baseUrl.contains("?")) {
                    val query = baseUrl.substringAfter("?")
                    "$resolvedUrl?$query"
                } else resolvedUrl

                val bwLabel = when {
                    currentBandwidth >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", currentBandwidth / 1_000_000.0)
                    currentBandwidth > 0 -> "${currentBandwidth / 1000} kbps"
                    else -> ""
                }

                variants.add(
                    HlsVariant(
                        url = finalUrl,
                        resolution = currentResolution,
                        bandwidth = currentBandwidth,
                        bandwidthLabel = bwLabel
                    )
                )

                currentResolution = ""
                currentBandwidth = 0L
            }
        }

        // Sort from highest quality to lowest
        return variants.sortedByDescending { it.bandwidth }
    }
}
