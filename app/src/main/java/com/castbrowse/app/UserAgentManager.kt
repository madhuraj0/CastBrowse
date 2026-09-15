package com.castbrowse.app

import android.content.Context

data class UserAgentPreset(
    val id: String,
    val name: String,
    val description: String,
    val userAgentString: String
)

object UserAgentManager {
    const val PREF_KEY_UA_MODE = "user_agent_mode"
    const val PREF_KEY_CUSTOM_UA = "user_agent_custom"

    const val MODE_DEFAULT = "default"
    const val MODE_IPAD = "ipad"
    const val MODE_DESKTOP = "desktop"
    const val MODE_SMART_TV = "smart_tv"
    const val MODE_APPLE_TV = "apple_tv"
    const val MODE_CUSTOM = "custom"

    const val UA_IPAD = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    const val UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    const val UA_SMART_TV = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.196 Safari/537.36 WebAppManager"
    const val UA_APPLE_TV = "Mozilla/5.0 (AppleTV; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/21J354"

    val PRESETS = listOf(
        UserAgentPreset(
            id = MODE_DEFAULT,
            name = "Default (Mobile)",
            description = "Standard Android Mobile browser profile",
            userAgentString = ""
        ),
        UserAgentPreset(
            id = MODE_IPAD,
            name = "iPad Safari (iOS)",
            description = "Bypasses mobile blocks; forces sites to serve clean direct HLS (.m3u8) streams",
            userAgentString = UA_IPAD
        ),
        UserAgentPreset(
            id = MODE_DESKTOP,
            name = "Desktop Chrome",
            description = "Full desktop PC browser experience with desktop media players",
            userAgentString = UA_DESKTOP
        ),
        UserAgentPreset(
            id = MODE_SMART_TV,
            name = "Smart TV (LG webOS)",
            description = "Bypasses mobile app redirects and serves clean TV-optimized streams",
            userAgentString = UA_SMART_TV
        ),
        UserAgentPreset(
            id = MODE_APPLE_TV,
            name = "Apple TV",
            description = "Apple TV media profile for HLS/FairPlay-free streams",
            userAgentString = UA_APPLE_TV
        ),
        UserAgentPreset(
            id = MODE_CUSTOM,
            name = "Custom User-Agent",
            description = "Enter your own custom User-Agent string",
            userAgentString = ""
        )
    )

    fun getActiveUserAgent(context: Context, defaultSystemUA: String?): String {
        val prefs = EncryptedStorage.getPreferences(context)
        val mode = prefs.getString(PREF_KEY_UA_MODE, MODE_DEFAULT) ?: MODE_DEFAULT
        return when (mode) {
            MODE_IPAD -> UA_IPAD
            MODE_DESKTOP -> UA_DESKTOP
            MODE_SMART_TV -> UA_SMART_TV
            MODE_APPLE_TV -> UA_APPLE_TV
            MODE_CUSTOM -> {
                val custom = prefs.getString(PREF_KEY_CUSTOM_UA, "")
                if (!custom.isNullOrBlank()) custom else (defaultSystemUA ?: UA_DESKTOP)
            }
            else -> defaultSystemUA ?: UA_DESKTOP
        }
    }

    fun getUaMode(context: Context): String {
        return EncryptedStorage.getPreferences(context).getString(PREF_KEY_UA_MODE, MODE_DEFAULT) ?: MODE_DEFAULT
    }

    fun getCustomUserAgent(context: Context): String {
        return EncryptedStorage.getPreferences(context).getString(PREF_KEY_CUSTOM_UA, "") ?: ""
    }

    fun setUaMode(context: Context, mode: String, customUa: String? = null) {
        val prefs = EncryptedStorage.getPreferences(context)
        prefs.edit().apply {
            putString(PREF_KEY_UA_MODE, mode)
            if (customUa != null) {
                putString(PREF_KEY_CUSTOM_UA, customUa)
            }
            apply()
        }
    }
}
