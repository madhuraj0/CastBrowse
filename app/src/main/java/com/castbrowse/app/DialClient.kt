package com.castbrowse.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DialClient {

    private const val TAG = "DialClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var activeAppInstanceUrl: String? = null

    /**
     * Launches an application on a DIAL-enabled Smart TV (Samsung, LG, Sony, Roku, Android TV).
     * If launching the built-in browser / Web Receiver, passes the Web Receiver /tv URL.
     */
    suspend fun launchApp(
        applicationUrl: String,
        appName: String = "WebBrowser",
        payload: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBase = if (applicationUrl.endsWith("/")) applicationUrl else "$applicationUrl/"
            val launchUrl = "$normalizedBase$appName"
            Log.d(TAG, "DIAL: Launching app '$appName' at $launchUrl with payload: $payload")

            val body = if (payload.isNotEmpty()) {
                payload.toRequestBody("text/plain".toMediaType())
            } else {
                "".toRequestBody()
            }

            val request = Request.Builder()
                .url(launchUrl)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val instanceUrl = response.header("LOCATION") ?: "$launchUrl/run"
            activeAppInstanceUrl = instanceUrl

            if (response.code in 200..299) {
                Log.d(TAG, "DIAL: App '$appName' launched successfully. Instance: $instanceUrl")
                instanceUrl
            } else {
                throw Exception("DIAL launch failed with HTTP ${response.code}: ${response.message}")
            }
        }
    }

    /**
     * Stops the currently running DIAL application on the Smart TV.
     */
    suspend fun stopApp(instanceUrl: String? = activeAppInstanceUrl): Boolean = withContext(Dispatchers.IO) {
        val target = instanceUrl ?: activeAppInstanceUrl ?: return@withContext false
        try {
            val request = Request.Builder()
                .url(target)
                .delete()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "DIAL: Failed to stop app at $target", e)
            false
        } finally {
            activeAppInstanceUrl = null
        }
    }

    /**
     * Checks if a specific app is installed or running on the DIAL receiver.
     */
    suspend fun getAppState(applicationUrl: String, appName: String): String? = withContext(Dispatchers.IO) {
        val normalizedBase = if (applicationUrl.endsWith("/")) applicationUrl else "$applicationUrl/"
        try {
            val request = Request.Builder()
                .url("$normalizedBase$appName")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string() ?: ""
                    extractTag(xml, "state")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTag(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = xml.indexOf(startTag)
        if (start == -1) return null
        val end = xml.indexOf(endTag, start + startTag.length)
        if (end == -1) return null
        return xml.substring(start + startTag.length, end).trim()
    }
}
