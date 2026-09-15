package com.castbrowse.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

// Inline Pause icon (avoids extended-icons dependency)
private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); verticalLineTo(19f)
        moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); horizontalLineTo(14f); close()
    }.build()
}

// Inline Contrast/Theme icon (avoids extended-icons dependency)
private val ThemeIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        
        moveTo(12f, 20f)
        verticalLineTo(4f)
        curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
        curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
        close()
    }.build()
}

// Inline Cast icon (avoids extended-icons dependency)
private val CastIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        // TV Outline
        moveTo(21f, 3f)
        horizontalLineTo(3f)
        curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
        verticalLineTo(8f)
        horizontalLineTo(3f)
        verticalLineTo(5f)
        horizontalLineTo(21f)
        verticalLineTo(19f)
        horizontalLineTo(14f)
        verticalLineTo(21f)
        horizontalLineTo(21f)
        curveTo(22.1f, 21f, 23f, 20.1f, 23f, 19f)
        verticalLineTo(5f)
        curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
        close()
        
        // Cast Waves
        moveTo(1f, 18f)
        verticalLineTo(21f)
        horizontalLineTo(4f)
        curveTo(4f, 19.34f, 2.66f, 18f, 1f, 18f)
        close()
        
        moveTo(1f, 14f)
        verticalLineTo(16f)
        curveTo(3.76f, 16f, 6f, 18.24f, 6f, 21f)
        horizontalLineTo(8f)
        curveTo(8f, 17.13f, 4.87f, 14f, 1f, 14f)
        close()
        
        moveTo(1f, 10f)
        verticalLineTo(12f)
        curveTo(5.97f, 12f, 10f, 16.03f, 10f, 21f)
        horizontalLineTo(12f)
        curveTo(12f, 14.92f, 7.08f, 10f, 1f, 10f)
        close()
    }.build()
}

// Inline Up Arrow icon for Find in Page
private val UpArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(7.41f, 15.41f)
        lineTo(12f, 10.83f)
        lineTo(16.59f, 15.41f)
        lineTo(18f, 14f)
        lineTo(12f, 8f)
        lineTo(6f, 14f)
        close()
    }.build()
}

// Inline Down Arrow icon for Find in Page
private val DownArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(7.41f, 8.59f)
        lineTo(12f, 13.17f)
        lineTo(16.59f, 8.59f)
        lineTo(18f, 10f)
        lineTo(12f, 16f)
        lineTo(6f, 10f)
        close()
    }.build()
}

// Inline Share icon for Stream Details dialog
private val ShareIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(18f, 16.08f)
        curveTo(17.24f, 16.08f, 16.56f, 16.38f, 16.04f, 16.85f)
        lineTo(8.91f, 12.7f)
        curveTo(8.96f, 12.47f, 9f, 12.24f, 9f, 12f)
        curveTo(9f, 11.76f, 8.96f, 11.53f, 8.91f, 11.3f)
        lineTo(15.96f, 7.19f)
        curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
        curveTo(19.66f, 8f, 21f, 6.66f, 21f, 5f)
        curveTo(21f, 3.34f, 19.66f, 2f, 18f, 2f)
        curveTo(16.34f, 2f, 15f, 3.34f, 15f, 5f)
        curveTo(15f, 5.24f, 15.04f, 5.47f, 15.09f, 5.7f)
        lineTo(8.04f, 9.81f)
        curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
        curveTo(4.34f, 9f, 3f, 10.34f, 3f, 12f)
        curveTo(3f, 13.66f, 4.34f, 15f, 6f, 15f)
        curveTo(6.79f, 15f, 7.5f, 14.69f, 8.04f, 14.19f)
        lineTo(15.16f, 18.35f)
        curveTo(15.11f, 18.56f, 15.08f, 18.78f, 15.08f, 19f)
        curveTo(15.08f, 20.61f, 16.39f, 21.92f, 18f, 21.92f)
        curveTo(19.61f, 21.92f, 20.92f, 20.61f, 20.92f, 19f)
        curveTo(20.92f, 17.39f, 19.61f, 16.08f, 18f, 16.08f)
        close()
    }.build()
}

// Inline Lock icon for Page Info security dialog
private val LockIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(18f, 8f)
        horizontalLineTo(17f)
        verticalLineTo(6f)
        curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
        curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
        verticalLineTo(8f)
        horizontalLineTo(6f)
        curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
        verticalLineTo(20f)
        curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
        horizontalLineTo(18f)
        curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
        verticalLineTo(10f)
        curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
        close()
        moveTo(12f, 17f)
        curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
        curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
        curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
        curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
        close()
        moveTo(15.1f, 8f)
        horizontalLineTo(8.9f)
        verticalLineTo(6f)
        curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
        curveTo(13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f)
        verticalLineTo(8f)
        close()
    }.build()
}

// Inline Download icon
private val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(19f, 9f)
        horizontalLineTo(15f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineTo(9f)
        horizontalLineTo(5f)
        lineTo(12f, 16f)
        lineTo(19f, 9f)
        close()
        moveTo(5f, 18f)
        verticalLineTo(20f)
        horizontalLineTo(19f)
        verticalLineTo(18f)
        horizontalLineTo(5f)
        close()
    }.build()
}

// Inline Tab / Window icon
private val TabWindowIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(19f, 4f)
        horizontalLineTo(5f)
        curveTo(3.89f, 4f, 3f, 4.89f, 3f, 6f)
        verticalLineTo(18f)
        curveTo(3f, 19.1f, 3.89f, 20f, 5f, 20f)
        horizontalLineTo(19f)
        curveTo(20.1f, 20f, 21f, 19.1f, 21f, 18f)
        verticalLineTo(6f)
        curveTo(21f, 4.89f, 20.1f, 4f, 19f, 4f)
        close()
        moveTo(19f, 7f)
        horizontalLineTo(5f)
        verticalLineTo(6f)
        horizontalLineTo(19f)
        verticalLineTo(7f)
        close()
        moveTo(19f, 18f)
        horizontalLineTo(5f)
        verticalLineTo(9f)
        horizontalLineTo(19f)
        verticalLineTo(18f)
        close()
    }.build()
}

@Composable
private fun NavCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shadowElevation = 2.dp,
        modifier = Modifier.size(38.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.35f)
            )
        }
    }
}

class MainActivity : ComponentActivity() {

    private var webView: SecureWebView? = null
    private var defaultUserAgent: String? = null

    // State for Browser Tab Management
    private val tabs = mutableStateListOf(BrowserTab(1, "DuckDuckGo", "https://html.duckduckgo.com"))
    private var activeTabId by mutableStateOf(1)
    private val tabStates = mutableMapOf<Int, Bundle>()
    private val tabVideos = mutableMapOf<Int, List<ExtractedVideo>>()

    // Extracted video links
    private val extractedVideos = mutableStateListOf<ExtractedVideo>()

    // Cast picker dialog visibility
    private var showCastDialog by mutableStateOf(false)
    private var selectedVideoToCast by mutableStateOf<ExtractedVideo?>(null)

    // Requested navigation tab from external intents (0 = Browser, 1 = Streams Hub)
    private var requestedNavTab by mutableStateOf<Int?>(null)
    private var pendingCheckClipboardOnFocus = false

    data class BrowserTab(val id: Int, val title: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalMediaProxy.init(this)
        LocalMediaProxy.start()
        
        // Load Adblock hosts and check for updates asynchronously
        MediaExtractorClient.loadAdHosts(this)
        MediaExtractorClient.checkAutoUpdate(this)
        
        // Security Hardening: Block screenshots and video capture of this app
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Handle incoming shared link from other apps (process before setContent for immediate state binding)
        handleIntent(intent)

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { EncryptedStorage.getPreferences(context) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
            val dynamicColor = prefs.getBoolean("dynamic_color", false)

            CastBrowseTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                MainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putString("theme_mode", newMode).apply()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingCheckClipboardOnFocus) {
            pendingCheckClipboardOnFocus = false
            checkAndHandleClipboard()
        }
    }

    private fun checkAndHandleClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true))) {
                    val url = extractUrl(text)
                    requestedNavTab = 0
                    handleUrlInput(url)
                    Toast.makeText(this, "Loaded link from clipboard", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Error reading clipboard", e)
        }
        // If no URL found on clipboard, default to Streams Hub
        requestedNavTab = 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("EXTRA_PANIC_WIPE", false)) {
            triggerPanicWipe()
            return
        }
        if (intent.getBooleanExtra("EXTRA_CHECK_CLIPBOARD", false)) {
            if (hasWindowFocus()) {
                checkAndHandleClipboard()
            } else {
                pendingCheckClipboardOnFocus = true
            }
            return
        }
        if (intent.getBooleanExtra("EXTRA_OPEN_STREAMS", false)) {
            requestedNavTab = 1
            return
        }
        val extraUrl = intent.getStringExtra("EXTRA_LOAD_URL")
        if (!extraUrl.isNullOrEmpty()) {
            requestedNavTab = 0
            handleUrlInput(extraUrl)
            return
        }
        val dataUri = intent.data
        if (dataUri != null) {
            requestedNavTab = 0
            handleUrlInput(dataUri.toString())
            return
        }
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_PROCESS_TEXT == action) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.data?.toString()
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) {
                val url = extractUrl(text)
                requestedNavTab = 0
                if (url.isNotEmpty()) {
                    handleUrlInput(url)
                } else {
                    handleUrlInput(text)
                }
            }
            return
        }
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type || type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                if (!sharedText.isNullOrEmpty()) {
                    val url = extractUrl(sharedText)
                    requestedNavTab = 0
                    if (url.isNotEmpty()) {
                        handleUrlInput(url)
                    }
                }
            }
            return
        }
    }

    private fun extractUrl(text: String): String {
        val trimmed = text.trim()
        val index = trimmed.indexOf("http://", ignoreCase = true)
        val secureIndex = trimmed.indexOf("https://", ignoreCase = true)
        val start = if (index != -1 && secureIndex != -1) {
            Math.min(index, secureIndex)
        } else if (index != -1) {
            index
        } else {
            secureIndex
        }
        if (start != -1) {
            val sub = trimmed.substring(start)
            val end = sub.indexOfAny(charArrayOf(' ', '\n', '\t', '\r', '"', '\'', '<', '>', '`', '\u0000'))
            val candidate = if (end != -1) sub.substring(0, end) else sub
            return candidate.trimEnd('.', ',', ';', ':', ')', ']', '}', '"', '\'', '`', '>')
        }
        return trimmed.trim('"', '\'', '<', '>', '(', ')', '[', ']', '{', '}', '`')
    }

    override fun onDestroy() {
        if (!CastSessionManager.isMediaPlaying && !CastSessionManager.isCasting) {
            LocalMediaProxy.stop()
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            webView?.clearCache(false)
            if (tabStates.size > 5) {
                val keysToRemove = tabStates.keys.filter { it != activeTabId }.drop(4)
                keysToRemove.forEach {
                    tabStates.remove(it)
                    tabVideos.remove(it)
                }
            }
        }
    }

    private fun triggerPanicWipe() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Wiping all session data...", Toast.LENGTH_SHORT).show()
            CastPlaybackService.stop(this@MainActivity)
            LocalMediaProxy.stop()
            webView?.wipeAllData()
            tabStates.clear()
            tabVideos.clear()
            tabs.clear()
            extractedVideos.clear()
            CastSessionManager.castingDevice = null
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.activeMediaUrl = null
            CastSessionManager.activeMediaTitle = null
            delay(800)
            finishAffinity()
            System.exit(0)
        }
    }

    private fun handleUrlInput(input: String) {
        var formattedUrl = input.trim()
        if (!formattedUrl.startsWith("http://", ignoreCase = true) && !formattedUrl.startsWith("https://", ignoreCase = true)) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                "https://html.duckduckgo.com/html/?q=" + Uri.encode(formattedUrl)
            }
        }

        if (isDirectVideoLink(formattedUrl)) {
            val filename = MediaExtractorClient.extractFilenameFromUrl(formattedUrl)
            val video = ExtractedVideo(
                url = formattedUrl,
                title = filename
            )
            selectedVideoToCast = video
            if (extractedVideos.none { it.url == formattedUrl }) {
                extractedVideos.add(0, video)
            }
            val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
            if (activeTabIdx != -1) {
                tabs[activeTabIdx] = tabs[activeTabIdx].copy(url = formattedUrl, title = filename)
                tabVideos[activeTabId] = listOf(video)
            }
            showCastDialog = true
            Toast.makeText(this, "Stream detected: $filename", Toast.LENGTH_SHORT).show()
        } else {
            val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
            if (activeTabIdx != -1) {
                tabs[activeTabIdx] = tabs[activeTabIdx].copy(url = formattedUrl)
                tabStates.remove(activeTabId)
                tabVideos.remove(activeTabId)
                extractedVideos.clear()
                webView?.loadUrl(formattedUrl)
            }
        }
    }

    private fun isDirectVideoLink(url: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrl = url.trim()

        if (MediaExtractorClient.isSegmentUrl(cleanUrl)) {
            return false
        }

        // 1. Check path via Android Uri
        try {
            val parsedUri = android.net.Uri.parse(cleanUrl)
            val path = parsedUri.path?.lowercase() ?: ""
            if (path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".m3u") ||
                path.endsWith(".webm") || path.endsWith(".mpd") || path.endsWith(".mkv") ||
                path.endsWith(".mov") || path.endsWith(".flv") || path.endsWith(".ts") ||
                path.endsWith(".m4v") || path.endsWith(".avi") || path.endsWith(".3gp") ||
                path.endsWith(".ogv")) {
                return true
            }
        } catch (ignored: Exception) {}

        // 2. Check path before query parameters or hash
        val urlWithoutQuery = cleanUrl.substringBefore('?').substringBefore('#').lowercase()
        if (urlWithoutQuery.endsWith(".mp4") || urlWithoutQuery.endsWith(".m3u8") ||
            urlWithoutQuery.endsWith(".m3u") || urlWithoutQuery.endsWith(".webm") ||
            urlWithoutQuery.endsWith(".mpd") || urlWithoutQuery.endsWith(".mkv") ||
            urlWithoutQuery.endsWith(".mov") || urlWithoutQuery.endsWith(".flv") ||
            urlWithoutQuery.endsWith(".ts") || urlWithoutQuery.endsWith(".m4v") ||
            urlWithoutQuery.endsWith(".avi") || urlWithoutQuery.endsWith(".3gp") ||
            urlWithoutQuery.endsWith(".ogv")) {
            return true
        }

        // 3. Check for standard media regex and dynamic stream indicators
        return MediaExtractorClient.MEDIA_REGEX.containsMatchIn(cleanUrl) ||
                cleanUrl.contains(".m3u8", ignoreCase = true) ||
                cleanUrl.contains(".mpd", ignoreCase = true) ||
                cleanUrl.contains(".mp4", ignoreCase = true)
    }

    private fun castToDevice(device: CastDevice, videoUrl: String, videoTitle: String, customFCastPort: Int, resumePosition: Double = 0.0) {
        lifecycleScope.launch {
            CastSessionManager.isCasting = true
            val targetPort = if (device.port > 0) device.port else customFCastPort
            Toast.makeText(this@MainActivity, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()

            val headers = mutableMapOf<String, String>()
            webView?.settings?.userAgentString?.let { ua ->
                headers["User-Agent"] = ua
            }
            val activeTabUrl = tabs.firstOrNull { it.id == activeTabId }?.url
            if (!activeTabUrl.isNullOrEmpty()) {
                headers["Referer"] = activeTabUrl
                try {
                    val uri = Uri.parse(activeTabUrl)
                    headers["Origin"] = "${uri.scheme}://${uri.authority}"
                } catch (e: Exception) {}
            }
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
            headers["Sec-Fetch-Dest"] = "video"
            val cookies = try { android.webkit.CookieManager.getInstance().getCookie(videoUrl) } catch (e: Exception) { null }
            if (!cookies.isNullOrEmpty()) {
                headers["Cookie"] = cookies
            }

            // Always route through LocalMediaProxy so receiver never leaks IP to internet and bypasses 403
            val proxiedUrl = if (videoUrl.contains("/local?id=")) {
                videoUrl
            } else {
                LocalMediaProxy.getProxyUrl(videoUrl, headers, device.ipAddress)
            }
            android.util.Log.d("MainActivity", "Casting stream to ${device.ipAddress}:$targetPort (${device.protocol}) -> $proxiedUrl")
            val cleanTitle = videoTitle.ifEmpty { MediaExtractorClient.extractFilenameFromUrl(videoUrl) }
            val result = CastSessionManager.play(
                device = device,
                mediaUrl = proxiedUrl,
                title = cleanTitle
            ) {
                lifecycleScope.launch {
                    CastPlaybackService.stop(this@MainActivity)
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.activeMediaUrl = null
                    CastSessionManager.activeMediaTitle = null
                }
            }

            CastSessionManager.isCasting = false
            result.onSuccess {
                CastSessionManager.isMediaPlaying = true
                CastSessionManager.activeMediaUrl = videoUrl
                CastSessionManager.activeMediaTitle = cleanTitle
                CastSessionManager.castingDevice = device
                CastSessionManager.customFcastPort = targetPort
                CastSessionManager.saveRecentIp(this@MainActivity, device.ipAddress)

                // If resuming from a previous timestamp, seek after receiver initializes media
                if (resumePosition > 15.0) {
                    lifecycleScope.launch {
                        delay(1200)
                        CastSessionManager.seek(resumePosition)
                    }
                }

                // Keep screen-lock background casting alive via Foreground Service + WakeLock
                CastPlaybackService.start(
                    context = this@MainActivity,
                    title = cleanTitle,
                    deviceName = device.name,
                    ip = device.ipAddress,
                    port = targetPort,
                    url = proxiedUrl
                )

                val statusMsg = if (resumePosition > 15.0) {
                    "Resumed from ${PlaybackResumeManager.formatTime(resumePosition)} on ${device.name}!"
                } else {
                    "Playing on ${device.name}!"
                }
                Toast.makeText(this@MainActivity, statusMsg, Toast.LENGTH_LONG).show()
                showCastDialog = false
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Casting failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun MainScreen(themeMode: String, onThemeModeChange: (String) -> Unit) {
        val context = LocalContext.current
        val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull() ?: BrowserTab(1, "DuckDuckGo", "https://html.duckduckgo.com")
        
        var urlTextFieldValue by remember(activeTab.url) { 
            mutableStateOf(TextFieldValue(text = activeTab.url)) 
        }
        var addressBarFocused by remember { mutableStateOf(false) }

        LaunchedEffect(addressBarFocused) {
            if (addressBarFocused) {
                delay(50)
                urlTextFieldValue = urlTextFieldValue.copy(
                    selection = TextRange(0, urlTextFieldValue.text.length)
                )
            }
        }

        var showMoreActionsSheet by remember { mutableStateOf(false) }
        var showPageInfoDialog by remember { mutableStateOf(false) }
        var showPanicDialog by remember { mutableStateOf(false) }
        var showTabSwitcher by remember { mutableStateOf(false) }
        var detailedVideoForDialog by remember { mutableStateOf<ExtractedVideo?>(null) }
        val prefs = remember { EncryptedStorage.getPreferences(context) }
        val isBottomAddressBar = remember { prefs.getBoolean("bottom_address_bar", false) }
        val showTabBar = remember { prefs.getBoolean("show_tab_bar", true) }

        val switchTab: (Int) -> Unit = { targetId ->
            if (targetId != activeTabId) {
                val oldBundle = Bundle()
                webView?.saveState(oldBundle)
                tabStates[activeTabId] = oldBundle
                tabVideos[activeTabId] = extractedVideos.toList()

                activeTabId = targetId
            }
        }

        val createNewTab: (String) -> Unit = { url ->
            val oldBundle = Bundle()
            webView?.saveState(oldBundle)
            tabStates[activeTabId] = oldBundle
            tabVideos[activeTabId] = extractedVideos.toList()

            val nextId = (tabs.maxOfOrNull { it.id } ?: 0) + 1
            tabs.add(BrowserTab(nextId, "New Tab", url))
            activeTabId = nextId
            extractedVideos.clear()
        }

        val closeTab: (BrowserTab) -> Unit = { tab ->
            val idx = tabs.indexOf(tab)
            val closingId = tab.id
            tabStates.remove(closingId)
            tabVideos.remove(closingId)
            tabs.remove(tab)
            if (activeTabId == closingId) {
                if (tabs.isNotEmpty()) {
                    activeTabId = tabs.getOrNull(idx)?.id ?: tabs.last().id
                } else {
                    val newId = 1
                    tabs.add(BrowserTab(newId, "DuckDuckGo", "https://html.duckduckgo.com"))
                    activeTabId = newId
                }
            }
        }
        var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", false)) }
        var isAdBlockEnabled by remember { mutableStateOf(true) }
        var isPopupsEnabled by remember { mutableStateOf(false) }
        var isDesktopMode by remember { mutableStateOf(false) }
        var showUserAgentDialog by remember { mutableStateOf(false) }
        var currentUaMode by remember { mutableStateOf(UserAgentManager.getUaMode(context)) }
        var loadingProgress by remember { mutableStateOf(0f) }
        var isLoading by remember { mutableStateOf(false) }
        var lastLoadedTabId by remember { mutableStateOf(activeTabId) }

        var showFindInPage by remember { mutableStateOf(false) }
        var findQuery by remember { mutableStateOf("") }
        var findMatchIndex by remember { mutableStateOf(0) }
        var findMatchTotal by remember { mutableStateOf(0) }

        var currentNavTab by remember { mutableStateOf(0) }
        LaunchedEffect(requestedNavTab) {
            requestedNavTab?.let {
                currentNavTab = it
                requestedNavTab = null
            }
        }
        val pickedVideos = remember { mutableStateListOf<DeviceVideoItem>() }
        val pickVideoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                val (name, size) = getLocalVideoInfo(context, uri)
                if (pickedVideos.none { it.uri == uri }) {
                    pickedVideos.add(0, DeviceVideoItem(uri = uri, title = name, size = size, isDownload = false))
                }
                Toast.makeText(context, "Selected: $name", Toast.LENGTH_SHORT).show()
            }
        }

        BackHandler(enabled = showFindInPage) {
            showFindInPage = false
            findQuery = ""
            webView?.clearMatches()
            findMatchIndex = 0
            findMatchTotal = 0
        }

        BackHandler(enabled = currentNavTab == 1) {
            currentNavTab = 0
        }

        BackHandler(enabled = currentNavTab == 0 && !showFindInPage && (webView?.canGoBack() == true)) {
            webView?.goBack()
        }

        val tabsRow = @Composable {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicator = {},
                divider = {}
            ) {
                tabs.forEach { tab ->
                    val isActive = tab.id == activeTabId
                    Tab(
                        selected = isActive,
                        onClick = { switchTab(tab.id) },
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                            if (tabs.size > 1) {
                                IconButton(
                                    onClick = { closeTab(tab) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add tab button (+) at the end
                IconButton(
                    onClick = { createNewTab("https://html.duckduckgo.com") },
                    modifier = Modifier
                        .padding(start = 4.dp, end = 8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val browserControls = @Composable { isBottom: Boolean ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(modifier = if (isBottom) Modifier.navigationBarsPadding() else Modifier.statusBarsPadding()) {
                    if (!isBottom && showTabBar) {
                        tabsRow()
                    }

                    // Chrome Address Bar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Casting Connection Indicator on the left of address bar (clicking opens connection wizard)
                        if (!addressBarFocused) {
                            val activeDevice = CastSessionManager.castingDevice
                            IconButton(
                                onClick = {
                                    val intent = android.content.Intent(context, CastWizardActivity::class.java)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = CastIcon,
                                    contentDescription = "Casting Setup",
                                    tint = if (activeDevice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                        }
 
                        // Address / search field container with horizontal swipe to switch tabs
                        var accumulatedDrag by remember { mutableStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (addressBarFocused) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (addressBarFocused) 2.dp else 1.dp,
                                    color = if (addressBarFocused) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .pointerInput(tabs.size, activeTabId) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { accumulatedDrag = 0f },
                                        onDragEnd = {
                                            val threshold = 70f
                                            val currentIdx = tabs.indexOfFirst { it.id == activeTabId }
                                            if (accumulatedDrag > threshold && currentIdx > 0) {
                                                switchTab(tabs[currentIdx - 1].id)
                                            } else if (accumulatedDrag < -threshold && currentIdx >= 0 && currentIdx < tabs.size - 1) {
                                                switchTab(tabs[currentIdx + 1].id)
                                            }
                                            accumulatedDrag = 0f
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            accumulatedDrag += dragAmount
                                        }
                                    )
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                                val androidColor = remember(onSurfaceColor) {
                                    android.graphics.Color.argb(
                                        (onSurfaceColor.alpha * 255).toInt(),
                                        (onSurfaceColor.red * 255).toInt(),
                                        (onSurfaceColor.green * 255).toInt(),
                                        (onSurfaceColor.blue * 255).toInt()
                                    )
                                }
                                
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        val editText = IncognitoEditText(ctx)
                                        editText.background = null
                                        editText.setSingleLine(true)
                                        editText.maxLines = 1
                                        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                                        editText.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                                        editText.textSize = 16f
                                        
                                        // Handle focus changes
                                        editText.setOnFocusChangeListener { _, hasFocus ->
                                            addressBarFocused = hasFocus
                                        }
                                        
                                        // Handle search action
                                        editText.setOnEditorActionListener { _, actionId, _ ->
                                            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                                                handleUrlInput(editText.text.toString())
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        
                                        // Keep Compose state in sync + Speculative DNS prefetching
                                        editText.addTextChangedListener(object : android.text.TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                                val currentText = s?.toString() ?: ""
                                                if (urlTextFieldValue.text != currentText) {
                                                    urlTextFieldValue = urlTextFieldValue.copy(text = currentText)
                                                    if (currentText.length >= 3 && currentText.contains(".")) {
                                                        DnsOverHttpsResolver.prefetch(currentText)
                                                    }
                                                }
                                            }
                                            override fun afterTextChanged(s: android.text.Editable?) {}
                                        })
                                        editText
                                    },
                                    update = { view ->
                                        view.setTextColor(androidColor)
                                        val currentText = urlTextFieldValue.text
                                        if (view.text?.toString() != currentText) {
                                            view.setText(currentText)
                                            if (addressBarFocused) {
                                                view.selectAll()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (addressBarFocused && urlTextFieldValue.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            urlTextFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear address",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (!addressBarFocused) {
                            Spacer(modifier = Modifier.width(6.dp))
 
                            // Chrome-style Tab Counter Button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { showTabSwitcher = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${tabs.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Three-dot menu anchor with popover DropdownMenu
                            Box {
                                IconButton(onClick = { showMoreActionsSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreActionsSheet,
                                    onDismissRequest = { showMoreActionsSheet = false },
                                    modifier = Modifier
                                        .width(250.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                        .padding(vertical = 8.dp)
                                ) {
                                    // 1. Chrome-Style Navigation Controls Panel (with tactile Round Depth Effect)
                                    val currentUrl = activeTab.url
                                    var isBookmarked by remember(currentUrl, showMoreActionsSheet) {
                                        mutableStateOf(BookmarkHelper.isUrlBookmarked(context, currentUrl))
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Back
                                        NavCircleButton(
                                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            enabled = webView?.canGoBack() == true,
                                            onClick = {
                                                webView?.let { if (it.canGoBack()) it.goBack() }
                                                showMoreActionsSheet = false
                                            }
                                        )

                                        // Forward
                                        NavCircleButton(
                                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Forward",
                                            enabled = webView?.canGoForward() == true,
                                            onClick = {
                                                webView?.let { if (it.canGoForward()) it.goForward() }
                                                showMoreActionsSheet = false
                                            }
                                        )

                                        // Bookmark
                                        NavCircleButton(
                                            icon = Icons.Default.Star,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            onClick = {
                                                isBookmarked = BookmarkHelper.toggleBookmark(context, currentUrl, activeTab.title)
                                            }
                                        )

                                        // Information
                                        NavCircleButton(
                                            icon = Icons.Default.Info,
                                            contentDescription = "Page info",
                                            onClick = {
                                                showMoreActionsSheet = false
                                                showPageInfoDialog = true
                                            }
                                        )

                                        // Reload
                                        NavCircleButton(
                                            icon = Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            onClick = {
                                                webView?.reload()
                                                showMoreActionsSheet = false
                                            }
                                        )
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    // New Tab
                                    DropdownMenuItem(
                                        text = { Text("New Tab") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            createNewTab("https://html.duckduckgo.com")
                                        }
                                    )

                                    // Bookmarks (opens full BookmarksActivity)
                                    DropdownMenuItem(
                                        text = { Text("Bookmarks") },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            val intent = Intent(context, BookmarksActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )

                                    // History (opens full HistoryActivity)
                                    DropdownMenuItem(
                                        text = { Text("History") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            val intent = Intent(context, HistoryActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )

                                    // Downloads (opens full DownloadsActivity)
                                    DropdownMenuItem(
                                        text = { Text("Downloads") },
                                        leadingIcon = { Icon(DownloadIcon, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            val intent = Intent(context, DownloadsActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )


                                    // Find in Page
                                    DropdownMenuItem(
                                        text = { Text("Find in Page") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            showFindInPage = true
                                        }
                                    )

                                    // User-Agent Presets
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("User-Agent Presets")
                                                val activePresetName = UserAgentManager.PRESETS.firstOrNull { it.id == currentUaMode }?.name ?: "Default"
                                                Text(
                                                    activePresetName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Build,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            showUserAgentDialog = true
                                        }
                                    )

                                    // Desktop Site toggle
                                    DropdownMenuItem(
                                        text = { Text("Desktop Site") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = {
                                            Switch(
                                                checked = isDesktopMode,
                                                onCheckedChange = {
                                                    isDesktopMode = it
                                                    currentUaMode = if (it) UserAgentManager.MODE_DESKTOP else UserAgentManager.MODE_DEFAULT
                                                    UserAgentManager.setUaMode(context, currentUaMode)
                                                    showMoreActionsSheet = false
                                                },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        onClick = {
                                            val next = !isDesktopMode
                                            isDesktopMode = next
                                            currentUaMode = if (next) UserAgentManager.MODE_DESKTOP else UserAgentManager.MODE_DEFAULT
                                            UserAgentManager.setUaMode(context, currentUaMode)
                                            showMoreActionsSheet = false
                                        }
                                    )

                                    // Cast Control Panel (if device connected)
                                    val activeDeviceInMenu = CastSessionManager.castingDevice
                                    if (activeDeviceInMenu != null) {
                                        DropdownMenuItem(
                                            text = { Text("Cast Control Panel") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                            onClick = {
                                                showMoreActionsSheet = false
                                                val intent = Intent(context, CastControlActivity::class.java)
                                                context.startActivity(intent)
                                            }
                                        )
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    // Settings (opens full SettingsActivity)
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            val intent = Intent(context, SettingsActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )

                                    // Clear Session
                                    DropdownMenuItem(
                                        text = { Text("Clear Session", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        onClick = {
                                            showMoreActionsSheet = false
                                            showPanicDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isBottom && showTabBar) {
                        tabsRow()
                    }

                    // Find in Page Bar
                    AnimatedVisibility(
                        visible = showFindInPage,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Find in page",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = findQuery,
                                    onValueChange = { query ->
                                        findQuery = query
                                        if (query.isNotEmpty()) {
                                            webView?.findAllAsync(query)
                                        } else {
                                            webView?.clearMatches()
                                            findMatchIndex = 0
                                            findMatchTotal = 0
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (findQuery.isEmpty()) {
                                            Text(
                                                "Find in page...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (findQuery.isNotEmpty()) {
                                    Text(
                                        text = if (findMatchTotal > 0) "$findMatchIndex/$findMatchTotal" else "0/0",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { webView?.findNext(false) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = UpArrowIcon,
                                            contentDescription = "Previous match",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { webView?.findNext(true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = DownArrowIcon,
                                            contentDescription = "Next match",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        showFindInPage = false
                                        findQuery = ""
                                        webView?.clearMatches()
                                        findMatchIndex = 0
                                        findMatchTotal = 0
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Find in page",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val miniPlayerStrip = @Composable {
            val activeDevice = CastSessionManager.castingDevice
            val activeUrl = CastSessionManager.activeMediaUrl
            AnimatedVisibility(
                visible = activeDevice != null && activeUrl != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                if (activeDevice != null && activeUrl != null) {
                    val isAmoled = themeMode == "amoled"
                    val miniPlayerBg = if (isAmoled) Color(0xFF07050A) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    val miniPlayerContentColor = if (isAmoled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onPrimaryContainer
                    val miniPlayerBorder = if (isAmoled) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null

                    Surface(
                        color = miniPlayerBg,
                        border = miniPlayerBorder,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    val intent = android.content.Intent(this@MainActivity, CastControlActivity::class.java)
                                    startActivity(intent)
                                },
                                onLongClick = {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Cast URL", activeUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Casting",
                                tint = miniPlayerContentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeUrl.substringBefore("?").substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = miniPlayerContentColor
                                )
                                Text(
                                    text = "▶ ${activeDevice.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = miniPlayerContentColor.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = {
                                lifecycleScope.launch {
                                    if (CastSessionManager.isMediaPlaying) {
                                        FCastClient.pause(activeDevice.ipAddress, CastSessionManager.customFcastPort)
                                        CastSessionManager.isMediaPlaying = false
                                        CastSessionManager.playbackState = 2
                                    } else {
                                        FCastClient.resume(activeDevice.ipAddress, CastSessionManager.customFcastPort)
                                        CastSessionManager.isMediaPlaying = true
                                        CastSessionManager.playbackState = 1
                                    }
                                }
                            }) {
                                Icon(
                                    if (CastSessionManager.isMediaPlaying) PauseIcon else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = miniPlayerContentColor
                                )
                            }
                            IconButton(onClick = {
                                lifecycleScope.launch {
                                    FCastClient.stop(activeDevice.ipAddress, CastSessionManager.customFcastPort)
                                    CastSessionManager.castingDevice = null
                                    CastSessionManager.isMediaPlaying = false
                                    CastSessionManager.activeMediaUrl = null
                                    CastSessionManager.playbackState = 0
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                if (currentNavTab == 0) {
                    if (!isBottomAddressBar) {
                        browserControls(false)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    "Streams",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { currentNavTab = 0 }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                val activeDevice = CastSessionManager.castingDevice
                                IconButton(onClick = {
                                    val intent = Intent(context, CastWizardActivity::class.java)
                                    context.startActivity(intent)
                                }) {
                                    Icon(
                                        imageVector = CastIcon,
                                        contentDescription = "Cast Setup",
                                        tint = if (activeDevice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    miniPlayerStrip()
                    if (currentNavTab == 0 && isBottomAddressBar) {
                        browserControls(true)
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.height(64.dp)
                    ) {
                        NavigationBarItem(
                            selected = currentNavTab == 0,
                            onClick = { currentNavTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Browser"
                                )
                            },
                            label = { Text("Browser", style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = currentNavTab == 1,
                            onClick = { currentNavTab = 1 },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (extractedVideos.isNotEmpty()) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(
                                                    if (extractedVideos.size > 99) "99+" else "${extractedVideos.size}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Streams"
                                    )
                                }
                            },
                            label = { Text("Streams", style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (currentNavTab == 0) 1f else 0f)
                        .graphicsLayer {
                            alpha = if (currentNavTab == 0) 1f else 0f
                        }
                ) {
                    AndroidView(
                    factory = { ctx ->
                        val wv = SecureWebView(ctx).apply {
                            webView = this
                            webViewClient = MediaExtractorClient(
                                isAdBlockEnabled = { isAdBlockEnabled },
                                isDesktopMode = { isDesktopMode || currentUaMode == UserAgentManager.MODE_DESKTOP },
                                onPageStarted = { newUrl ->
                                    val directVideo = selectedVideoToCast?.takeIf {
                                        isDirectVideoLink(it.url) && (it.url == newUrl || newUrl.startsWith("https://html.duckduckgo.com") || newUrl == "about:blank")
                                    }
                                    extractedVideos.clear()
                                    if (directVideo != null) {
                                        extractedVideos.add(directVideo)
                                        tabVideos[activeTabId] = listOf(directVideo)
                                    }
                                    val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
                                    if (activeTabIdx != -1) {
                                        tabs[activeTabIdx] = tabs[activeTabIdx].copy(url = newUrl)
                                    }
                                    if (isHistoryEnabled) {
                                        recordUrlToHistory(context, newUrl)
                                    }
                                },
                                onDrmDetected = { keySystem, streamUrl ->
                                    lifecycleScope.launch {
                                        val idx = extractedVideos.indexOfFirst { it.url == streamUrl || streamUrl.contains(it.url) }
                                        if (idx != -1) {
                                            extractedVideos[idx] = extractedVideos[idx].copy(isDrmProtected = true, drmKeySystem = keySystem)
                                        }
                                        Toast.makeText(this@MainActivity, "🔒 $keySystem Protected Content: DRM streams cannot be cast to TV.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) { video ->
                                lifecycleScope.launch {
                                    if (!MediaExtractorClient.isSegmentUrl(video.url) && extractedVideos.none { it.url == video.url }) {
                                        extractedVideos.add(video)
                                    }
                                }
                            }
                            
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    loadingProgress = newProgress / 100f
                                    isLoading = newProgress < 100
                                    if (newProgress in 20..30) {
                                        view?.evaluateJavascript(MediaExtractorClient.DOM_SCRAPER_SCRIPT, null)
                                    }
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    if (isPopupsEnabled) {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        if (transport != null) {
                                            transport.webView = view
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                    }
                                    return false
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrEmpty()) {
                                        val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
                                        if (activeTabIdx != -1) {
                                            tabs[activeTabIdx] = tabs[activeTabIdx].copy(title = title)
                                        }
                                    }
                                }
                            }

                            setOnLongClickListener {
                                val hr = hitTestResult
                                val extra = hr.extra
                                if (hr.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                    if (extra != null && !MediaExtractorClient.isSegmentUrl(extra)) {
                                        lifecycleScope.launch {
                                            if (extractedVideos.none { it.url == extra }) {
                                                val filename = MediaExtractorClient.extractFilenameFromUrl(extra)
                                                val video = ExtractedVideo(url = extra, title = filename)
                                                extractedVideos.add(video)
                                                Toast.makeText(ctx, "Extracted: $filename", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                false
                            }

                            addJavascriptInterface(
                                MediaExtractorClient.WebAppInterface(
                                    onVideosFound = { list ->
                                        lifecycleScope.launch {
                                            list.forEach { video ->
                                                if (!MediaExtractorClient.isSegmentUrl(video.url)) {
                                                    val cleanTitle = MediaExtractorClient.extractFilenameFromUrl(video.url)
                                                    val normalized = video.copy(title = cleanTitle)
                                                    val existingIdx = extractedVideos.indexOfFirst { it.url == normalized.url }
                                                    if (existingIdx == -1) {
                                                        extractedVideos.add(normalized)
                                                    } else if (normalized.isDrmProtected) {
                                                        extractedVideos[existingIdx] = extractedVideos[existingIdx].copy(
                                                            isDrmProtected = true,
                                                            drmKeySystem = normalized.drmKeySystem
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDrmDetected = { keySystem, streamUrl ->
                                        lifecycleScope.launch {
                                            val idx = extractedVideos.indexOfFirst { it.url == streamUrl || streamUrl.contains(it.url) }
                                            if (idx != -1) {
                                                extractedVideos[idx] = extractedVideos[idx].copy(isDrmProtected = true, drmKeySystem = keySystem)
                                            }
                                            Toast.makeText(this@MainActivity, "🔒 $keySystem Protected Content: DRM streams cannot be cast to TV.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                ),
                                "AndroidApp"
                            )

                            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                findMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                                findMatchTotal = numberOfMatches
                            }

                            if (defaultUserAgent == null) {
                                defaultUserAgent = settings.userAgentString
                            }
                            loadUrl(activeTab.url)
                        }

                        SwipeRefreshLayout(ctx).apply {
                            addView(
                                wv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            setOnRefreshListener {
                                wv.reload()
                            }
                        }
                    },
                    update = { swipeRefresh ->
                        swipeRefresh.isRefreshing = isLoading
                        val view = webView ?: return@AndroidView

                        if (defaultUserAgent == null) {
                            defaultUserAgent = view.settings.userAgentString
                        }
                        val targetUA = UserAgentManager.getActiveUserAgent(context, defaultUserAgent)
                        val isDesktop = currentUaMode == UserAgentManager.MODE_DESKTOP || isDesktopMode
                        if (targetUA != null && view.settings.userAgentString != targetUA) {
                            view.settings.userAgentString = targetUA
                            view.settings.useWideViewPort = isDesktop
                            view.settings.loadWithOverviewMode = isDesktop
                            view.reload()
                        }

                        if (lastLoadedTabId != activeTabId) {
                            val prevBundle = Bundle()
                            view.saveState(prevBundle)
                            tabStates[lastLoadedTabId] = prevBundle
                            tabVideos[lastLoadedTabId] = extractedVideos.toList()

                            lastLoadedTabId = activeTabId
                            val currentActiveTab = tabs.firstOrNull { it.id == activeTabId }
                            if (currentActiveTab != null) {
                                val savedBundle = tabStates[activeTabId]
                                if (savedBundle != null) {
                                    view.restoreState(savedBundle)
                                } else {
                                    view.loadUrl(currentActiveTab.url)
                                }
                                extractedVideos.clear()
                                tabVideos[activeTabId]?.let { extractedVideos.addAll(it) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading && currentNavTab == 0) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            // Dedicated Streams Page
            if (currentNavTab == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    StreamsPage(
                        extractedVideos = extractedVideos,
                        pickedVideos = pickedVideos,
                        onPickVideo = { pickVideoLauncher.launch("video/*") },
                        onCastVideo = { url, title ->
                            val activeDevice = CastSessionManager.castingDevice
                            if (activeDevice != null) {
                                castToDevice(activeDevice, url, title, activeDevice.port)
                            } else {
                                selectedVideoToCast = ExtractedVideo(url = url, title = title)
                                showCastDialog = true
                            }
                        },
                        onClearExtracted = { extractedVideos.clear() },
                        onSwitchToBrowser = { currentNavTab = 0 }
                    )
                }
            }
        }
    }



        // Chrome-style Page Info Dialog
        if (showPageInfoDialog) {
            val uri = try { android.net.Uri.parse(activeTab.url) } catch (e: Exception) { null }
            val isHttps = uri?.scheme.equals("https", ignoreCase = true)
            val hostDisplay = uri?.host ?: activeTab.url

            AlertDialog(
                onDismissRequest = { showPageInfoDialog = false },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = if (isHttps) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isHttps) LockIcon else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isHttps) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = hostDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHttps) "Connection is secure" else "Connection is not secure",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHttps) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isHttps) "Connection is encrypted" else "Connection is not encrypted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("• Adblock: Active", style = MaterialTheme.typography.labelMedium)
                                Text("• Streams: ${extractedVideos.size}", style = MaterialTheme.typography.labelMedium)
                                Text("• Proxy: Port ${LocalMediaProxy.proxyPort}", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Copy / Share actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("URL", activeTab.url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(CopyIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                    }
                                    startActivity(Intent.createChooser(shareIntent, "Share link"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(ShareIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPageInfoDialog = false }) {
                        Text("Done")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Clear Session / Panic Wipe Confirmation Dialog
        if (showPanicDialog) {
            AlertDialog(
                onDismissRequest = { showPanicDialog = false },
                title = { Text("Clear Session?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Wipes tabs, cookies, active casts, and exits.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPanicDialog = false
                            triggerPanicWipe()
                        }
                    ) {
                        Text("Clear & Exit", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPanicDialog = false }) { Text("Cancel") }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Chrome-style Tab Switcher Dialog
        if (showTabSwitcher) {
            Dialog(
                onDismissRequest = { showTabSwitcher = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showTabSwitcher = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close tab switcher")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        createNewTab("https://html.duckduckgo.com")
                                        showTabSwitcher = false
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Tab")
                                }
                            }
                        }
                    ) { paddingVals ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = paddingVals.calculateTopPadding() + 8.dp,
                                bottom = 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(tabs, key = { it.id }) { tab ->
                                val isActive = tab.id == activeTabId
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clickable {
                                            switchTab(tab.id)
                                            showTabSwitcher = false
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    ) {
                                        // Header: Title & Close Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = tab.title.ifBlank { "New Tab" },
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    closeTab(tab)
                                                    if (tabs.isEmpty()) {
                                                        showTabSwitcher = false
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Close Tab",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Domain URL
                                        val domain = try {
                                            val host = Uri.parse(tab.url).host
                                            host ?: tab.url
                                        } catch (e: Exception) {
                                            tab.url
                                        }
                                        Text(
                                            text = domain,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Spacer(modifier = Modifier.weight(1f))
                                        
                                        // Visual Card Preview Area
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(95.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    TabWindowIcon,
                                                    contentDescription = null,
                                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (isActive) "Active" else "Tab",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showUserAgentDialog) {
            UserAgentPresetsDialog(
                context = context,
                currentUaMode = currentUaMode,
                onDismiss = { showUserAgentDialog = false },
                onPresetSelected = { newMode, customUa ->
                    currentUaMode = newMode
                    UserAgentManager.setUaMode(context, newMode, customUa)
                    isDesktopMode = newMode == UserAgentManager.MODE_DESKTOP
                    val targetUA = UserAgentManager.getActiveUserAgent(context, defaultUserAgent)
                    webView?.settings?.userAgentString = targetUA
                    webView?.settings?.useWideViewPort = isDesktopMode
                    webView?.settings?.loadWithOverviewMode = isDesktopMode
                    webView?.reload()
                    showUserAgentDialog = false
                    val presetName = UserAgentManager.PRESETS.firstOrNull { it.id == newMode }?.name ?: "Default"
                    Toast.makeText(context, "User-Agent switched to: $presetName", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Redesigned Cast Streams Selection Dialog
        if (showCastDialog) {
            Dialog(
                onDismissRequest = { showCastDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Detected Streams",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (extractedVideos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No video streams found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(extractedVideos) { video ->
                                    val isSelected = selectedVideoToCast?.url == video.url
                                    val cleanTitle = video.title.ifEmpty { MediaExtractorClient.extractFilenameFromUrl(video.url) }
                                    OutlinedCard(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedVideoToCast = video }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (video.poster.isNotEmpty()) {
                                                AsyncImage(
                                                    url = video.poster,
                                                    modifier = Modifier
                                                        .size(width = 64.dp, height = 44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            detailedVideoForDialog = video
                                                        }
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                // 1. Only filename as title
                                                Text(
                                                    text = cleanTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                // 2. Full link as subtitle/desc in a horizontally scrollable container
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                    Text(
                                                        text = video.url,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        softWrap = false
                                                    )
                                                }

                                                if (video.resolution.isNotEmpty() || video.size.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (video.resolution.isNotEmpty()) {
                                                            Text(
                                                                text = video.resolution,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.secondary,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        if (video.size.isNotEmpty()) {
                                                            Text(
                                                                text = video.size,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                if (video.isDrmProtected) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "🔒 ${video.drmKeySystem ?: "Widevine"} DRM",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedVideoToCast = video }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isDrm = selectedVideoToCast?.isDrmProtected == true
                        if (isDrm) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Protected Stream: Encrypted with ${selectedVideoToCast?.drmKeySystem ?: "Widevine"} DRM. Content security policies prevent direct network casting to standard TV receivers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Device Card
                        val activeDevice = CastSessionManager.castingDevice
                        if (activeDevice != null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Target Device",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            activeDevice.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${activeDevice.ipAddress}:${activeDevice.port}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val savedPos = selectedVideoToCast?.let { PlaybackResumeManager.getSavedPosition(context, it.url) } ?: 0.0
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedVideoToCast?.let { video ->
                                                        CastSessionManager.addToQueue(video)
                                                        Toast.makeText(context, "Added to queue (${CastSessionManager.mediaQueue.size})", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                enabled = selectedVideoToCast != null && !isDrm,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Queue")
                                            }

                                            Button(
                                                onClick = {
                                                    selectedVideoToCast?.let { video ->
                                                        val portToUse = if (activeDevice.port > 0) activeDevice.port else CastSessionManager.customFcastPort
                                                        castToDevice(activeDevice, video.url, video.title, portToUse)
                                                    }
                                                },
                                                enabled = selectedVideoToCast != null && !isDrm,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(if (isDrm) "DRM Locked" else if (savedPos > 15.0) "Cast 0:00" else "Cast Now")
                                            }
                                        }

                                        if (savedPos > 15.0 && !isDrm) {
                                            FilledTonalButton(
                                                onClick = {
                                                    selectedVideoToCast?.let { video ->
                                                        val portToUse = if (activeDevice.port > 0) activeDevice.port else CastSessionManager.customFcastPort
                                                        castToDevice(activeDevice, video.url, video.title, portToUse, resumePosition = savedPos)
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Resume (${PlaybackResumeManager.formatTime(savedPos)})")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val recentIps = remember { CastSessionManager.getRecentIps(context) }
                            OutlinedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("No receiver connected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("Pair in wizard or pick recent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Button(
                                            onClick = {
                                                showCastDialog = false
                                                val intent = android.content.Intent(context, CastWizardActivity::class.java)
                                                context.startActivity(intent)
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Wizard")
                                        }
                                    }
                                    if (recentIps.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Recent:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            recentIps.forEach { ip ->
                                                SuggestionChip(
                                                    onClick = {
                                                        CastSessionManager.castingDevice = CastDevice("FCast Receiver", ip, CastSessionManager.customFcastPort)
                                                    },
                                                    label = { Text(ip) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { showCastDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Detailed View Dialog for detected streams
        if (detailedVideoForDialog != null) {
            val video = detailedVideoForDialog!!
            AlertDialog(
                onDismissRequest = { detailedVideoForDialog = null },
                title = { Text("Stream Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (video.poster.isNotEmpty()) {
                            AsyncImage(
                                url = video.poster,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }

                        Text("Title: ${video.title.ifEmpty { "Direct Stream" }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                        val fileType = when {
                            video.url.contains(".m3u8") -> "HLS Playlist (.m3u8)"
                            video.url.contains(".mpd") -> "DASH Manifest (.mpd)"
                            video.url.contains(".mp4") -> "MPEG-4 Video (.mp4)"
                            video.url.contains(".webm") -> "WebM Video (.webm)"
                            video.url.contains(".mkv") -> "Matroska Video (.mkv)"
                            else -> "Generic Stream / Unknown"
                        }
                        Text("Type: $fileType", style = MaterialTheme.typography.bodyMedium)

                        if (video.resolution.isNotEmpty()) {
                            Text("Resolution: ${video.resolution}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (video.size.isNotEmpty()) {
                            Text("Size: ${video.size}", style = MaterialTheme.typography.bodyMedium)
                        }

                        Text("URL:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = video.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Video URL", video.url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = CopyIcon,
                                    contentDescription = "Copy URL",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Action buttons: Cast, External Player, Share Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val target = video
                                    detailedVideoForDialog = null
                                    selectedVideoToCast = target
                                    showCastDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cast", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val mime = when {
                                        video.url.contains(".m3u8") -> "application/x-mpegURL"
                                        video.url.contains(".mpd") -> "application/dash+xml"
                                        video.url.contains(".mp4") -> "video/mp4"
                                        video.url.contains(".webm") -> "video/webm"
                                        video.url.contains(".mkv") -> "video/x-matroska"
                                        else -> "video/*"
                                    }
                                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(video.url), mime)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(playIntent, "Play with external player"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Player", maxLines = 1)
                            }

                            IconButton(
                                onClick = {
                                    val filename = if (video.title.isNotBlank()) video.title else MediaExtractorClient.extractFilenameFromUrl(video.url)
                                    val finalName = if (filename.contains(".")) filename else "$filename.mp4"
                                    val cookies = try { CookieManager.getInstance().getCookie(video.url) } catch (e: Exception) { null }
                                    val ua = webView?.settings?.userAgentString
                                    val headers = mutableMapOf<String, String>()
                                    if (!cookies.isNullOrBlank()) headers["Cookie"] = cookies
                                    if (!ua.isNullOrBlank()) headers["User-Agent"] = ua
                                    DownloadHelper.enqueueDownload(
                                        context = context,
                                        url = video.url,
                                        suggestedTitle = finalName,
                                        headers = if (headers.isNotEmpty()) headers else null
                                    )
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = DownloadIcon,
                                    contentDescription = "Download Stream",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, video.url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Stream URL"))
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = ShareIcon,
                                    contentDescription = "Share URL",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { detailedVideoForDialog = null }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    private fun recordUrlToHistory(context: Context, url: String) {
        // Skip internal/blank pages
        if (url.isBlank() || url == "about:blank") return
        val prefs = EncryptedStorage.getPreferences(context)
        val raw = prefs.getString("history_json", "[]") ?: "[]"
        val arr = try { org.json.JSONArray(raw) } catch (e: Exception) { org.json.JSONArray() }
        // Build new entry with timestamp
        val entry = org.json.JSONObject().apply {
            put("url", url)
            put("ts", System.currentTimeMillis())
        }
        // Prepend newest entry at index 0
        val newArr = org.json.JSONArray()
        newArr.put(entry)
        // Keep at most 500 entries
        val limit = minOf(arr.length(), 499)
        for (i in 0 until limit) newArr.put(arr.getJSONObject(i))
        prefs.edit().putString("history_json", newArr.toString()).apply()
    }
}

data class DeviceVideoItem(
    val uri: Uri,
    val title: String,
    val size: Long,
    val isDownload: Boolean = false
)

private fun getLocalVideoInfo(context: Context, uri: Uri): Pair<String, Long> {
    var name = "video.mp4"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: "video.mp4"
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "video.mp4"
    }
    return Pair(name, size)
}

@Composable
private fun StreamsPage(
    extractedVideos: List<ExtractedVideo>,
    pickedVideos: List<DeviceVideoItem>,
    onPickVideo: () -> Unit,
    onCastVideo: (url: String, title: String) -> Unit,
    onClearExtracted: () -> Unit,
    onSwitchToBrowser: () -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf(0) } // 0 = Web Streams, 1 = Device Videos

    val downloadedVideos = remember(subTab) {
        DownloadHelper.getDownloads(context).filter {
            it.status == android.app.DownloadManager.STATUS_SUCCESSFUL && it.localUri != null
        }.mapNotNull { dl ->
            try {
                val u = Uri.parse(dl.localUri)
                DeviceVideoItem(uri = u, title = dl.title, size = dl.totalBytes, isDownload = true)
            } catch (e: Exception) { null }
        }
    }
    val allDeviceVideos = remember(pickedVideos.toList(), downloadedVideos) {
        (pickedVideos + downloadedVideos).distinctBy { it.uri.toString() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Option A: Top Tabs (Web Streams | Device Videos)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            TabRow(
                selectedTabIndex = subTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            ) {
                Tab(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    text = {
                        Text(
                            text = if (extractedVideos.isNotEmpty()) "Web Streams (${extractedVideos.size})" else "Web Streams",
                            fontWeight = if (subTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    text = {
                        Text(
                            text = if (allDeviceVideos.isNotEmpty()) "Device Videos (${allDeviceVideos.size})" else "Device Videos",
                            fontWeight = if (subTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Active Receiver Header Card (if connected or not)
        val activeDevice = CastSessionManager.castingDevice
        Surface(
            color = if (activeDevice != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = CastIcon,
                        contentDescription = null,
                        tint = if (activeDevice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (activeDevice != null) activeDevice.name else "No receiver connected",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeDevice != null) "${activeDevice.ipAddress}:${activeDevice.port}" else "Tap to pair in wizard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = {
                        val intent = Intent(context, CastWizardActivity::class.java)
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (activeDevice != null) "Switch" else "Connect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Content: Web Streams vs Device Videos
        if (subTab == 0) {
            // WEB STREAMS
            if (extractedVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            "No streams detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Navigate to video sites or play any video in the browser to extract streaming URLs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onSwitchToBrowser,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Browser")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${extractedVideos.size} streams captured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onClearExtracted) {
                        Text("Clear All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(extractedVideos, key = { it.url }) { video ->
                        WebStreamCard(
                            video = video,
                            onCast = { onCastVideo(video.url, video.title) }
                        )
                    }
                }
            }
        } else {
            // DEVICE VIDEOS
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Prominent "+ Pick Video from Device" Button
                    OutlinedCard(
                        onClick = onPickVideo,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pick Video from Device",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (allDeviceVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Text(
                                    "No device videos selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Pick MP4, MKV, or WebM files from device storage to stream directly to TV.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(allDeviceVideos, key = { it.uri.toString() }) { item ->
                        DeviceVideoCard(
                            item = item,
                            onCast = {
                                val mime = context.contentResolver.getType(item.uri) ?: "video/mp4"
                                val proxiedUrl = LocalMediaProxy.registerLocalMedia(
                                    uri = item.uri,
                                    title = item.title,
                                    mimeType = mime,
                                    size = item.size,
                                    receiverIp = CastSessionManager.castingDevice?.ipAddress
                                )
                                onCastVideo(proxiedUrl, item.title)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebStreamCard(
    video: ExtractedVideo,
    onCast: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val cleanTitle = video.title.ifEmpty { MediaExtractorClient.extractFilenameFromUrl(video.url) }
    val streamType = when {
        video.url.contains(".m3u8") -> "HLS"
        video.url.contains(".mpd") -> "DASH"
        video.url.contains(".webm") -> "WebM"
        video.url.contains(".mkv") -> "MKV"
        else -> "MP4"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (video.poster.isNotEmpty()) {
                AsyncImage(
                    url = video.poster,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Tags row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = streamType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (video.resolution.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = video.resolution,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (video.isDrmProtected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "DRM ${video.drmKeySystem ?: "Protected"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (video.size.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = video.size,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // URL row with copy button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = video.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(video.url))
                            Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = CopyIcon,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (video.isDrmProtected) {
                                Toast.makeText(context, "Cannot cast DRM-protected content (${video.drmKeySystem ?: "Encrypted"})", Toast.LENGTH_LONG).show()
                            } else {
                                onCast()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !video.isDrmProtected,
                        colors = if (video.isDrmProtected) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) else ButtonDefaults.buttonColors(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (video.isDrmProtected) LockIcon else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (video.isDrmProtected) "DRM Locked" else "Cast", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (video.isDrmProtected) {
                                Toast.makeText(context, "Cannot queue DRM-protected stream", Toast.LENGTH_SHORT).show()
                            } else {
                                CastSessionManager.addToQueue(video)
                                Toast.makeText(context, "Added to queue (${CastSessionManager.mediaQueue.size})", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !video.isDrmProtected,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Queue")
                    }

                    OutlinedButton(
                        onClick = {
                            val mime = when {
                                video.url.contains(".m3u8") -> "application/x-mpegURL"
                                video.url.contains(".mpd") -> "application/dash+xml"
                                video.url.contains(".mp4") -> "video/mp4"
                                video.url.contains(".webm") -> "video/webm"
                                video.url.contains(".mkv") -> "video/x-matroska"
                                else -> "video/*"
                            }
                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(video.url), mime)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(playIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No compatible player found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Play")
                    }

                    IconButton(
                        onClick = {
                            DownloadHelper.enqueueDownload(context, video.url, cleanTitle)
                        }
                    ) {
                        Icon(
                            imageVector = DownloadIcon,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, video.url)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Stream URL"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceVideoCard(
    item: DeviceVideoItem,
    onCast: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.isDownload) "Downloaded" else "Device Video",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.size > 0) {
                            Text(
                                text = "• ${DownloadHelper.formatBytes(item.size)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCast,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cast to TV", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val mime = context.contentResolver.getType(item.uri) ?: "video/mp4"
                        val proxiedUrl = LocalMediaProxy.registerLocalMedia(
                            uri = item.uri,
                            title = item.title,
                            mimeType = mime,
                            size = item.size,
                            receiverIp = CastSessionManager.castingDevice?.ipAddress
                        )
                        CastSessionManager.addToQueue(ExtractedVideo(proxiedUrl, item.title))
                        Toast.makeText(context, "Added to queue (${CastSessionManager.mediaQueue.size})", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Queue")
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(item.uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(playIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open")
                }

                IconButton(
                    onClick = {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Video"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot share video", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AsyncImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            if (url.startsWith("data:image")) {
                withContext(Dispatchers.IO) {
                    try {
                        val base64Data = url.substringAfter(",")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        // Fail silently
                    }
                }
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                withContext(Dispatchers.IO) {
                    try {
                        // Reuse the singleton OkHttpClient — avoids spawning new thread pools per image
                        val response = MediaExtractorClient.httpClient.newCall(
                            okhttp3.Request.Builder().url(url).build()
                        ).execute()
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }
                    } catch (e: Exception) {
                        // Fail silently
                    }
                }
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Thumbnail",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Placeholder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

val CopyIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "CopyIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path(
    fill = SolidColor(Color(0xFF8B5CF6)),
    pathFillType = PathFillType.NonZero
) {
    moveTo(16f, 1f)
    horizontalLineTo(4f)
    curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
    verticalLineTo(17f)
    horizontalLineTo(4f)
    verticalLineTo(3f)
    horizontalLineTo(16f)
    verticalLineTo(1f)
    moveTo(19f, 5f)
    horizontalLineTo(8f)
    curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
    verticalLineTo(21f)
    curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
    horizontalLineTo(19f)
    curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
    verticalLineTo(7f)
    curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
    moveTo(19f, 21f)
    horizontalLineTo(8f)
    verticalLineTo(7f)
    horizontalLineTo(19f)
    verticalLineTo(21f)
    close()
}.build()

class IncognitoEditText @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : android.widget.EditText(context, attrs, defStyleAttr) {
    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo?): android.view.inputmethod.InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (outAttrs != null) {
            // Apply standard no-personalized-learning flag and incognito private IME hint
            outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            outAttrs.privateImeOptions = "com.google.android.inputmethod.latin.noPersonalizedLearning,incognito"
        }
        return connection
    }
}

@Composable
private fun UserAgentPresetsDialog(
    context: Context,
    currentUaMode: String,
    onDismiss: () -> Unit,
    onPresetSelected: (String, String?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentUaMode) }
    var customUaText by remember { mutableStateOf(UserAgentManager.getCustomUserAgent(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "User-Agent Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Bypass mobile-only blocks and force streaming websites to provide direct, clean HLS (.m3u8) streams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                UserAgentManager.PRESETS.forEach { preset ->
                    val isSelected = selectedMode == preset.id
                    OutlinedCard(
                        onClick = { selectedMode = preset.id },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMode = preset.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedMode == UserAgentManager.MODE_CUSTOM) {
                    OutlinedTextField(
                        value = customUaText,
                        onValueChange = { customUaText = it },
                        label = { Text("Custom User-Agent String") },
                        placeholder = { Text("Mozilla/5.0...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPresetSelected(selectedMode, if (selectedMode == UserAgentManager.MODE_CUSTOM) customUaText else null)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply & Reload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
