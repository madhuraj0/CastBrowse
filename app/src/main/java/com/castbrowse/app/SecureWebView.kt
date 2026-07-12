package com.castbrowse.app

import android.content.Context
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

class SecureWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        configureSecuritySettings()
        setupFocusAndKeyboard()
    }

    private fun setupFocusAndKeyboard() {
        // Fix focus issue with Compose WebView so that soft keyboard opens on tapping input fields
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_UP -> {
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
            }
            false
        }
    }

    private fun configureSecuritySettings() {
        val webSettings = settings

        // Enable JavaScript (essential for modern sites) but disable standard data persistence APIs
        webSettings.javaScriptEnabled = true

        // Configure popup interception settings
        webSettings.setSupportMultipleWindows(true)
        webSettings.javaScriptCanOpenWindowsAutomatically = false

        // Disable local file access pathways (crucial privacy-hardening step)
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = false
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = false

        // Do not cache form autofill or credentials
        @Suppress("DEPRECATION")
        webSettings.savePassword = false

        // Allow DOM Storage for rendering layout systems, but isolate and clear it on exit
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = false // Disables WebSQL

        // Disable geolocation to prevent physical location leaks
        webSettings.setGeolocationEnabled(false)

        // Disable third-party cookies
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, false)
    }

    /**
     * Wipes all ephemeral and cached data from this WebView instance.
     */
    fun wipeAllData() {
        try {
            clearCache(true)
            clearHistory()
            clearFormData()

            // Clear session cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
            }

            // Wipe local storage metadata
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo?): android.view.inputmethod.InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (outAttrs != null) {
            // Force keyboard's incognito mode (no personalized learning + visual theme hint) for all inputs inside WebView
            outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            outAttrs.privateImeOptions = "com.google.android.inputmethod.latin.noPersonalizedLearning,incognito"
        }
        return connection
    }
}
