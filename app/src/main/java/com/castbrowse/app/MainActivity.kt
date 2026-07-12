package com.castbrowse.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {

    private var webView: SecureWebView? = null
    private var defaultUserAgent: String? = null

    // State for Browser Tab Management
    private val tabs = mutableStateListOf(BrowserTab(1, "DuckDuckGo", "https://html.duckduckgo.com"))
    private var activeTabId by mutableStateOf(1)

    // Extracted video links
    private val extractedVideos = mutableStateListOf<ExtractedVideo>()

    // Cast picker dialog visibility
    private var showCastDialog by mutableStateOf(false)
    private var selectedVideoToCast by mutableStateOf<ExtractedVideo?>(null)

    data class BrowserTab(val id: Int, val title: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalMediaProxy.start()
        
        // Load Adblock hosts and check for updates asynchronously
        MediaExtractorClient.loadAdHosts(this)
        MediaExtractorClient.checkAutoUpdate(this)
        
        // Security Hardening: Block screenshots and video capture of this app
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { EncryptedStorage.getPreferences(context) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }

            CastBrowseTheme(themeMode = themeMode) {
                MainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putString("theme_mode", newMode).apply()
                    }
                )
            }
        }

        // Handle incoming shared link from other apps
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    val url = extractUrl(sharedText)
                    if (url.isNotEmpty()) {
                        handleUrlInput(url)
                    }
                }
            }
        }
    }

    private fun extractUrl(text: String): String {
        val index = text.indexOf("http://")
        val secureIndex = text.indexOf("https://")
        val start = if (index != -1 && secureIndex != -1) {
            Math.min(index, secureIndex)
        } else if (index != -1) {
            index
        } else {
            secureIndex
        }
        if (start != -1) {
            val sub = text.substring(start)
            val end = sub.indexOfAny(charArrayOf(' ', '\n', '\t', '\r'))
            return if (end != -1) sub.substring(0, end) else sub
        }
        return text.trim()
    }

    override fun onDestroy() {
        LocalMediaProxy.stop()
        super.onDestroy()
    }

    private fun triggerPanicWipe() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Wiping all session data...", Toast.LENGTH_SHORT).show()
            webView?.wipeAllData()
            tabs.clear()
            extractedVideos.clear()
            CastSessionManager.castingDevice = null
            CastSessionManager.isMediaPlaying = false
            CastSessionManager.activeMediaUrl = null
            delay(800)
            finishAffinity()
            System.exit(0)
        }
    }

    private fun handleUrlInput(input: String) {
        var formattedUrl = input.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = if (formattedUrl.contains(".") && !formattedUrl.contains(" ")) {
                "https://$formattedUrl"
            } else {
                "https://html.duckduckgo.com/html/?q=" + Uri.encode(formattedUrl)
            }
        }

        if (isDirectVideoLink(formattedUrl)) {
            val filename = formattedUrl.substringBefore("?").substringAfterLast("/")
            val video = ExtractedVideo(
                url = formattedUrl,
                title = if (filename.isNotEmpty()) filename else "Direct Stream"
            )
            selectedVideoToCast = video
            if (extractedVideos.none { it.url == formattedUrl }) {
                extractedVideos.add(video)
            }
            showCastDialog = true
        } else {
            val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
            if (activeTabIdx != -1) {
                tabs[activeTabIdx] = tabs[activeTabIdx].copy(url = formattedUrl)
                webView?.loadUrl(formattedUrl)
            }
        }
    }

    private fun isDirectVideoLink(url: String): Boolean {
        return try {
            val path = URI(url).path?.lowercase() ?: return false
            path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".m3u") ||
                    path.endsWith(".webm") || path.endsWith(".mpd") || path.endsWith(".mkv")
        } catch (e: Exception) {
            false
        }
    }

    private fun castToDevice(device: CastDevice, videoUrl: String, videoTitle: String, customFCastPort: Int) {
        lifecycleScope.launch {
            CastSessionManager.isCasting = true
            Toast.makeText(this@MainActivity, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()

            val headers = mutableMapOf<String, String>()
            webView?.settings?.userAgentString?.let { ua ->
                headers["User-Agent"] = ua
            }
            val activeTabUrl = tabs.firstOrNull { it.id == activeTabId }?.url
            if (!activeTabUrl.isNullOrEmpty()) {
                headers["Referer"] = activeTabUrl
            }
            val cookies = android.webkit.CookieManager.getInstance().getCookie(videoUrl)
            if (!cookies.isNullOrEmpty()) {
                headers["Cookie"] = cookies
            }

            val proxiedUrl = LocalMediaProxy.getProxyUrl(videoUrl)
            android.util.Log.d("MainActivity", "Proxying URL: $videoUrl -> $proxiedUrl")
            val result = FCastClient.play(
                ipAddress = device.ipAddress,
                url = proxiedUrl,
                title = videoTitle,
                port = customFCastPort,
                headers = headers
            ) {
                lifecycleScope.launch {
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.activeMediaUrl = null
                }
            }

            CastSessionManager.isCasting = false
            result.onSuccess {
                CastSessionManager.isMediaPlaying = true
                CastSessionManager.activeMediaUrl = videoUrl
                Toast.makeText(this@MainActivity, "Playing on ${device.name}!", Toast.LENGTH_LONG).show()
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

        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val coroutineScope = rememberCoroutineScope()
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        LaunchedEffect(addressBarFocused) {
            if (addressBarFocused) {
                delay(50)
                urlTextFieldValue = urlTextFieldValue.copy(
                    selection = TextRange(0, urlTextFieldValue.text.length)
                )
            }
        }

        var showMoreActionsSheet by remember { mutableStateOf(false) }
        var showThemeDialog by remember { mutableStateOf(false) }
        var showCreditsDialog by remember { mutableStateOf(false) }
        var showHistoryDialog by remember { mutableStateOf(false) }
        var detailedVideoForDialog by remember { mutableStateOf<ExtractedVideo?>(null) }
        val prefs = remember { EncryptedStorage.getPreferences(context) }
        var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", false)) }
        var isAdBlockEnabled by remember { mutableStateOf(true) }
        var isPopupsEnabled by remember { mutableStateOf(false) }
        var isDesktopMode by remember { mutableStateOf(false) }
        var loadingProgress by remember { mutableStateOf(0f) }
        var isLoading by remember { mutableStateOf(false) }
        var lastLoadedTabId by remember { mutableStateOf(activeTabId) }

        Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Chrome-style Horizontal Tab Bar (top-most)
                    ScrollableTabRow(
                        selectedTabIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicator = {}, // No standard line indicator, tabs are self-contained shapes like Chrome desktop tabs
                        divider = {}
                    ) {
                        tabs.forEach { tab ->
                            val isActive = tab.id == activeTabId
                            Tab(
                                selected = isActive,
                                onClick = {
                                    activeTabId = tab.id
                                    extractedVideos.clear()
                                },
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
                                            onClick = {
                                                val idx = tabs.indexOf(tab)
                                                tabs.remove(tab)
                                                if (isActive) {
                                                    activeTabId = tabs.getOrNull(idx)?.id ?: tabs.last().id
                                                }
                                                extractedVideos.clear()
                                            },
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
                            onClick = {
                                val nextId = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                                tabs.add(BrowserTab(nextId, "New Tab", "https://html.duckduckgo.com"))
                                activeTabId = nextId
                                extractedVideos.clear()
                            },
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
 
                        // Address / search field container
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
                                        
                                        // Keep Compose state in sync
                                        editText.addTextChangedListener(object : android.text.TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                                val currentText = s?.toString() ?: ""
                                                if (urlTextFieldValue.text != currentText) {
                                                    urlTextFieldValue = urlTextFieldValue.copy(text = currentText)
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
                            Spacer(modifier = Modifier.width(8.dp))
 
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
                                // 1. NavigationControls (Chrome-style comfortable row)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { webView?.let { if (it.canGoBack()) it.goBack() }; showMoreActionsSheet = false },
                                                enabled = webView?.canGoBack() == true,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (webView?.canGoBack() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                )
                                            }
                                            IconButton(
                                                onClick = { webView?.reload(); showMoreActionsSheet = false },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Reload",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { webView?.let { if (it.canGoForward()) it.goForward() }; showMoreActionsSheet = false },
                                                enabled = webView?.canGoForward() == true,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Forward",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (webView?.canGoForward() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {}
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)
                                
                                // 2. Adblock Shield
                                DropdownMenuItem(
                                    text = { Text("Adblock Shield") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (isAdBlockEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = isAdBlockEnabled,
                                            onCheckedChange = { isAdBlockEnabled = it },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    onClick = { isAdBlockEnabled = !isAdBlockEnabled }
                                )

                                // 3. Allow Popups
                                DropdownMenuItem(
                                    text = { Text("Allow Popups") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = null,
                                            tint = if (isPopupsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = isPopupsEnabled,
                                            onCheckedChange = { isPopupsEnabled = it },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    onClick = { isPopupsEnabled = !isPopupsEnabled }
                                )

                                // 4. Desktop Site
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
                                                showMoreActionsSheet = false
                                            },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    onClick = { 
                                        isDesktopMode = !isDesktopMode
                                        showMoreActionsSheet = false
                                    }
                                )

                                // 4b. View History
                                DropdownMenuItem(
                                    text = { Text("View History") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        showHistoryDialog = true
                                    }
                                )

                                // 4c. Record History
                                DropdownMenuItem(
                                    text = { Text("Record History") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = if (isHistoryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = isHistoryEnabled,
                                            onCheckedChange = { 
                                                isHistoryEnabled = it
                                                prefs.edit().putBoolean("history_enabled", it).apply()
                                            },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    onClick = { 
                                        isHistoryEnabled = !isHistoryEnabled
                                        prefs.edit().putBoolean("history_enabled", isHistoryEnabled).apply()
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                                // Theme selector
                                DropdownMenuItem(
                                    text = { Text("Theme") },
                                    leadingIcon = { Icon(ThemeIcon, contentDescription = null) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        showThemeDialog = true
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                                // 7. Credits & Privacy
                                DropdownMenuItem(
                                    text = { Text("Credits & Privacy") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        showCreditsDialog = true
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                                // 7.5. Update Adblock List
                                DropdownMenuItem(
                                    text = { Text("Update Adblock List") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        android.widget.Toast.makeText(context, "Updating adblock list...", android.widget.Toast.LENGTH_SHORT).show()
                                        MediaExtractorClient.updateAdHosts(context) { success, count ->
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Adblock list updated! $count hosts loaded.", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to update adblock list.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                                // 5. Setup Wizard
                                DropdownMenuItem(
                                    text = { Text("Setup Wizard") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        val intent = android.content.Intent(context, CastWizardActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )

                                // 6. Cast Control Panel
                                val activeDeviceInMenu = CastSessionManager.castingDevice
                                DropdownMenuItem(
                                    text = { Text("Cast Control Panel") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (activeDeviceInMenu != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    enabled = activeDeviceInMenu != null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        val intent = android.content.Intent(context, CastControlActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                                // 8. Clear Session
                                DropdownMenuItem(
                                    text = { Text("Clear Session", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    onClick = {
                                        showMoreActionsSheet = false
                                        triggerPanicWipe()
                                    }
                                )
                            }
                        }
                    }
                    }
                }
            }
        },
            bottomBar = {
                // Only the mini-player casting strip lives here
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
                                    .navigationBarsPadding()
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
            },
            floatingActionButton = {
                // "Cast Discovered Streams" Floating Action Button
                AnimatedVisibility(
                    visible = extractedVideos.isNotEmpty(),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (selectedVideoToCast == null) {
                                selectedVideoToCast = extractedVideos.firstOrNull()
                            }
                            showCastDialog = true
                        },
                        icon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Cast", tint = Color.White)
                        },
                        text = {
                            Text("Cast Detected (${extractedVideos.size})", fontWeight = FontWeight.Bold, color = Color.White)
                        },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SecureWebView(ctx).apply {
                            webView = this
                            webViewClient = MediaExtractorClient(
                                isAdBlockEnabled = { isAdBlockEnabled },
                                isDesktopMode = { isDesktopMode },
                                onPageStarted = { newUrl ->
                                    extractedVideos.clear()
                                    val activeTabIdx = tabs.indexOfFirst { it.id == activeTabId }
                                    if (activeTabIdx != -1) {
                                        tabs[activeTabIdx] = tabs[activeTabIdx].copy(url = newUrl)
                                    }
                                    if (isHistoryEnabled) {
                                        recordUrlToHistory(context, newUrl)
                                    }
                                }
                            ) { video ->
                                lifecycleScope.launch {
                                    if (extractedVideos.none { it.url == video.url }) {
                                        extractedVideos.add(video)
                                    }
                                }
                            }
                            
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    loadingProgress = newProgress / 100f
                                    isLoading = newProgress < 100
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
                                    if (extra != null) {
                                        lifecycleScope.launch {
                                            if (extractedVideos.none { it.url == extra }) {
                                                val filename = extra.substringBefore("?").substringAfterLast("/")
                                                val video = ExtractedVideo(url = extra, title = "Link: $filename")
                                                extractedVideos.add(video)
                                                Toast.makeText(ctx, "Extracted: $filename", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                false
                            }

                            addJavascriptInterface(
                                MediaExtractorClient.WebAppInterface { list ->
                                    lifecycleScope.launch {
                                        list.forEach { video ->
                                            if (extractedVideos.none { it.url == video.url }) {
                                                extractedVideos.add(video)
                                            }
                                        }
                                    }
                                },
                                "AndroidApp"
                            )

                            if (defaultUserAgent == null) {
                                defaultUserAgent = settings.userAgentString
                            }
                            loadUrl(activeTab.url)
                        }
                    },
                    update = { view ->
                        if (defaultUserAgent == null) {
                            defaultUserAgent = view.settings.userAgentString
                        }
                        val targetUA = if (isDesktopMode) {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        } else {
                            defaultUserAgent
                        }
                        if (targetUA != null && view.settings.userAgentString != targetUA) {
                            view.settings.userAgentString = targetUA
                            view.settings.useWideViewPort = isDesktopMode
                            view.settings.loadWithOverviewMode = isDesktopMode
                            view.reload()
                        }

                        if (lastLoadedTabId != activeTabId) {
                            lastLoadedTabId = activeTabId
                            val currentActiveTab = tabs.firstOrNull { it.id == activeTabId }
                            if (currentActiveTab != null) {
                                view.loadUrl(currentActiveTab.url)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
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
        }



        // Theme selection dialog
        if (showThemeDialog) {
            Dialog(onDismissRequest = { showThemeDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Choose Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        listOf(
                            "light" to "Light",
                            "dark" to "Dark",
                            "amoled" to "AMOLED Black",
                            "system" to "System Default",
                            "dynamic" to "Material You"
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onThemeModeChange(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = {
                                        onThemeModeChange(mode)
                                        showThemeDialog = false
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // About & Credits dialog
        if (showCreditsDialog) {
            AlertDialog(
                onDismissRequest = { showCreditsDialog = false },
                title = { Text("About & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "How it works:\nExtracts HTML5 video URLs from pages and streams them to your FCast receiver (e.g. Android TV).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Privacy:\nOperates 100% locally. Session cookies/headers are proxied purely for video authorization. No tracking, no external collection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Credits:\n• FCast protocol & client\n• Steven Black's Adblock Hosts\n• Kotlin Coroutines\n• Jetpack Compose\n• OkHttp & SSDP Discovery\n\nCollaborators:\n• madhuraj0 (Idea, Design direction, Testing)\n• Antigravity (AI Assistant)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCreditsDialog = false }) {
                        Text("Dismiss")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // History Dialog
        if (showHistoryDialog) {
            val historyEntries = remember(showHistoryDialog) {
                val raw = prefs.getString("history_json", "[]") ?: "[]"
                val arr = try { org.json.JSONArray(raw) } catch (e: Exception) { org.json.JSONArray() }
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    Pair(obj.getString("url"), obj.getLong("ts"))
                }
            }
            var historyList by remember { mutableStateOf(historyEntries) }

            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (historyList.isNotEmpty()) {
                            TextButton(onClick = {
                                prefs.edit().putString("history_json", "[]").apply()
                                historyList = emptyList()
                            }) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                text = {
                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No history yet. Enable \"Record History\" in the menu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(historyList) { (url, ts) ->
                                val dateStr = remember(ts) {
                                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showHistoryDialog = false
                                            webView?.loadUrl(url)
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = url,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Redesigned Cast Streams Selection Dialog
        if (showCastDialog) {
            Dialog(onDismissRequest = { showCastDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
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
                                modifier = Modifier.heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(extractedVideos) { video ->
                                    val isSelected = selectedVideoToCast?.url == video.url
                                    OutlinedCard(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                                            AsyncImage(
                                                url = video.poster,
                                                modifier = Modifier
                                                    .size(width = 72.dp, height = 48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        detailedVideoForDialog = video
                                                    }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = video.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = video.url,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedVideoToCast = video }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

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
                                    Button(
                                        onClick = {
                                            selectedVideoToCast?.let { video ->
                                                castToDevice(activeDevice, video.url, video.title, CastSessionManager.customFcastPort)
                                            }
                                        },
                                        enabled = selectedVideoToCast != null,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Cast Now")
                                    }
                                }
                            }
                        } else {
                            OutlinedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("No receiver connected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("Pair a target in wizard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(
                                        onClick = {
                                            showCastDialog = false
                                            val intent = android.content.Intent(context, CastWizardActivity::class.java)
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Setup Wizard")
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
