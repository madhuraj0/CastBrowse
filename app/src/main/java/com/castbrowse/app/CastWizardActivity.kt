package com.castbrowse.app

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URI

class CastWizardActivity : ComponentActivity() {

    private val discoveredDevices = mutableStateListOf<CastDevice>()
    private var discoveryJob: Job? = null
    private var isScanning by mutableStateOf(false)
    private lateinit var discoveryService: SsdpDiscoveryService

    companion object {
        private const val TEST_VIDEO_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots and screen recording on the setup wizard (contains device IP/port info)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        LocalMediaProxy.start()
        discoveryService = SsdpDiscoveryService(this)

        val prefs = EncryptedStorage.getPreferences(this)
        val themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
        val dynamicColor = prefs.getBoolean("dynamic_color", false)
        val accentColor = prefs.getString("accent_color", "default") ?: "default"
        setContent {
            CastBrowseTheme(themeMode = themeMode, dynamicColor = dynamicColor, accentColor = accentColor) {
                WizardScreen()
            }
        }
        startDeviceDiscovery()
    }

    private fun startDeviceDiscovery() {
        discoveredDevices.clear()
        isScanning = true
        discoveryJob?.cancel()
        discoveryJob = lifecycleScope.launch {
            discoveryService.discoverUniversalDevices().collectLatest { devList ->
                devList.forEach { device ->
                    val existingIndex = discoveredDevices.indexOfFirst { it.ipAddress == device.ipAddress }
                    if (existingIndex != -1) {
                        discoveredDevices[existingIndex] = device
                    } else {
                        discoveredDevices.add(device)
                    }
                }
            }
            delay(5000)
            isScanning = false
        }
    }

    private fun stopDeviceDiscovery() {
        discoveryJob?.cancel()
        isScanning = false
    }

    override fun onDestroy() {
        stopDeviceDiscovery()
        super.onDestroy()
    }

    /**
     * Parses any FCast QR code format into a CastDevice.
     *
     * Supported formats:
     *  1. fcast://r/<base64_json>  — modern FCast receiver QR (addresses + services arrays)
     *  2. fcast://<host>:<port>    — simple URI
     *  3. {"host":"...","port":...} — legacy flat JSON
     *  4. 192.168.x.x:port        — plain host:port
     *
     * Returns null if the raw string cannot be parsed into a valid host.
     */
    private fun parseFcastQr(raw: String): CastDevice? {
        val trimmed = raw.trim()
        android.util.Log.d("CastWizardActivity", "Raw QR content: $trimmed")

        // ── Format 1: fcast://r/<base64> ─────────────────────────────────────
        // e.g. fcast://r/eyJuYW1lIjoi...
        if (trimmed.startsWith("fcast://r/", ignoreCase = true)) {
            val b64 = trimmed.substringAfter("fcast://r/").trimEnd('/')
            return try {
                val json = String(android.util.Base64.decode(b64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
                android.util.Log.d("CastWizardActivity", "Decoded QR JSON: $json")

                val jsonObj = org.json.JSONObject(json)
                val name = jsonObj.optString("name", "FCast Receiver")

                // port comes from services[0].port
                val port = jsonObj.optJSONArray("services")
                    ?.optJSONObject(0)
                    ?.optInt("port", FCastClient.FCAST_DEFAULT_PORT)
                    ?: FCastClient.FCAST_DEFAULT_PORT

                // pick first IPv4 address; fall back to first address if none
                val addresses = jsonObj.optJSONArray("addresses")
                var chosenIp: String? = null
                if (addresses != null) {
                    for (i in 0 until addresses.length()) {
                        val addr = addresses.getString(i)
                        if (!addr.contains(":")) { // IPv4 has no colon
                            chosenIp = addr
                            break
                        }
                    }
                    if (chosenIp == null && addresses.length() > 0) {
                        chosenIp = addresses.getString(0) // fall back to first (IPv6)
                    }
                }

                if (chosenIp != null) {
                    android.util.Log.d("CastWizardActivity", "QR resolved → $chosenIp:$port ($name)")
                    CastDevice(name, chosenIp, port)
                } else null
            } catch (e: Exception) {
                android.util.Log.e("CastWizardActivity", "Failed to decode QR base64/JSON: ${e.message}")
                null
            }
        }

        // ── Format 2: fcast://<host>:<port> or fcastr://<host>:<port> ─────────
        if (trimmed.contains("://")) {
            val afterScheme = trimmed.substringAfter("://")
                .substringBefore("/").substringBefore("?").substringBefore("#")
            val (host, port) = if (afterScheme.startsWith("[")) {
                val ipv6 = afterScheme.substringAfter("[").substringBefore("]")
                val p = afterScheme.substringAfter("]").trimStart(':').toIntOrNull() ?: FCastClient.FCAST_DEFAULT_PORT
                ipv6 to p
            } else {
                val lastColon = afterScheme.lastIndexOf(":")
                if (lastColon != -1 && afterScheme.substring(lastColon + 1).toIntOrNull() != null) {
                    afterScheme.substring(0, lastColon) to afterScheme.substring(lastColon + 1).toInt()
                } else {
                    afterScheme to FCastClient.FCAST_DEFAULT_PORT
                }
            }
            if (host.isNotEmpty()) return CastDevice("FCast Receiver", host, port)
        }

        // ── Format 3: flat JSON {"host":"...","port":...} ─────────────────────
        if (trimmed.startsWith("{")) {
            return try {
                val j = org.json.JSONObject(trimmed)
                val host = j.optString("host").ifEmpty { j.optString("ip").ifEmpty { j.optString("address") } }
                val port = j.optInt("port", FCastClient.FCAST_DEFAULT_PORT)
                if (host.isNotEmpty()) CastDevice("FCast Receiver", host, port) else null
            } catch (e: Exception) { null }
        }

        // ── Format 4: plain host:port ─────────────────────────────────────────
        if (trimmed.contains(":")) {
            val lastColon = trimmed.lastIndexOf(":")
            val portPart = trimmed.substring(lastColon + 1)
            if (portPart.toIntOrNull() != null) {
                return CastDevice("FCast Receiver", trimmed.substring(0, lastColon), portPart.toInt())
            }
        }

        // ── Format 5: bare hostname/IP ────────────────────────────────────────
        if (trimmed.isNotEmpty()) {
            return CastDevice("FCast Receiver", trimmed, FCastClient.FCAST_DEFAULT_PORT)
        }

        return null
    }

    private fun playTestStream(device: CastDevice) {
        lifecycleScope.launch {
            CastSessionManager.isCasting = true
            Toast.makeText(this@CastWizardActivity, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()
            
            val targetPort = if (device.port > 0) device.port else CastSessionManager.customFcastPort
            val res = CastSessionManager.play(device, TEST_VIDEO_URL, "${device.name} Test Stream") {
                lifecycleScope.launch {
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.activeMediaUrl = null
                }
            }
            
            CastSessionManager.isCasting = false
            res.onSuccess {
                CastSessionManager.castingDevice = device
                CastSessionManager.customFcastPort = targetPort
                CastSessionManager.isMediaPlaying = true
                CastSessionManager.activeMediaUrl = TEST_VIDEO_URL
                CastSessionManager.saveRecentIp(this@CastWizardActivity, device.ipAddress)
                Toast.makeText(this@CastWizardActivity, "Test video playing on ${device.name}!", Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                Toast.makeText(this@CastWizardActivity, "Test connection failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WizardScreen() {
        val context = LocalContext.current
        var manualIpText by remember { mutableStateOf("") }
        var fcastPortText by remember { 
            mutableStateOf(CastSessionManager.customFcastPort.toString()) 
        }
        var recentIps by remember { 
            mutableStateOf(CastSessionManager.getRecentIps(context)) 
        }
        var reachabilityStatus by remember { mutableStateOf<String?>(null) }
        val isVpnActive = remember { NetworkDiagnostics.isVpnActive(context) }
        val localIp = remember { LocalMediaProxy.getLocalIpAddress() }
        val tvUrl = remember(localIp) { "http://$localIp:${LocalMediaProxy.proxyPort}/tv" }
        val isHotspot = remember { NetworkDiagnostics.isHotspotActive() }
        val hotspotIp = remember { NetworkDiagnostics.getHotspotIp() }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = { Text("Receiver Setup Wizard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 0. VPN Alert Banner (if active)
                if (isVpnActive) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .refractiveGlass(
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = 6.dp,
                                    glowColor = MaterialTheme.colorScheme.error
                                )
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 2.dp, glowColor = MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Active VPN Detected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                    Text(
                                        "Local TVs and casting devices might not be reachable unless Local Network Sharing or Split-Tunneling is enabled in your VPN app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Current Active Session Card
                item {
                    val activeDevice = CastSessionManager.castingDevice
                    if (activeDevice != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .refractiveGlass(
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = 8.dp,
                                    glowColor = MaterialTheme.colorScheme.primary
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .refractiveGlass(shape = CircleShape, elevation = 2.dp, glowColor = MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle, 
                                            contentDescription = "Active Connection", 
                                            tint = MaterialTheme.colorScheme.secondary, 
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val protoName = when (activeDevice.protocol) {
                                            CastProtocol.DLNA -> "DLNA / Smart TV"
                                            CastProtocol.AIRPLAY -> "AirPlay"
                                            CastProtocol.DIAL -> "DIAL / Smart TV"
                                            CastProtocol.GOOGLE_CAST -> "Google Cast"
                                            CastProtocol.WEB_RECEIVER -> "Web Receiver"
                                            CastProtocol.FCAST -> "FCast Receiver"
                                        }
                                        Text(if (CastSessionManager.isMediaPlaying) "Active Stream ($protoName)" else "Selected Receiver ($protoName)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text(activeDevice.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("${activeDevice.ipAddress}:${activeDevice.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = { 
                                            CastSessionManager.castingDevice = null 
                                            CastSessionManager.isMediaPlaying = false
                                            CastSessionManager.activeMediaUrl = null
                                            Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Disconnect", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                if (CastSessionManager.isMediaPlaying) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, CastControlActivity::class.java)
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Cast Controller")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { playTestStream(activeDevice) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play Test Stream")
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .refractiveGlass(
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = 4.dp
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info, 
                                        contentDescription = "No connection", 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Not Connected", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Select a Smart TV, Web Receiver, or FCast below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 2. Smart TV Web Receiver Card (/tv)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .refractiveGlass(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 6.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 2.dp, glowColor = MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(WebIcon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Smart TV Web Receiver", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Works on any TV or console browser (webOS, Tizen, PlayStation, Xbox, FireTV)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .refractiveGlass(
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = 2.dp
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tvUrl,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clip.setPrimaryClip(ClipData.newPlainText("TV URL", tvUrl))
                                        Toast.makeText(context, "URL copied: $tvUrl", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Copy TV URL")
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Hotspot / Travel Mode Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .refractiveGlass(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 6.dp,
                                glowColor = if (isHotspot) MaterialTheme.colorScheme.secondary else null
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        TetheringIcon,
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hotspot / Travel Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        if (isHotspot) "Hotspot Active (Phone Gateway: $hotspotIp)" else "Cast in hotels without Wi-Fi router by enabling your phone hotspot.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isHotspot) {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                Toast.makeText(context, "Scanning hotspot devices...", Toast.LENGTH_SHORT).show()
                                                val found = NetworkDiagnostics.scanHotspotSubnet()
                                                found.forEach { dev ->
                                                    if (discoveredDevices.none { it.ipAddress == dev.ipAddress }) {
                                                        discoveredDevices.add(dev)
                                                    }
                                                }
                                                Toast.makeText(context, "Found ${found.size} receiver(s)", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Scan Hotspot")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Open Hotspot in Android Settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = if (isHotspot) Modifier else Modifier.fillMaxWidth()
                                ) {
                                    Text("Hotspot Settings")
                                }
                            }
                        }
                    }
                }

                // 4. Discovered Devices & TVs
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Discovered Devices & TVs", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        if (isScanning) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val angle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rotate"
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scanning",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(angle)
                            )
                        } else {
                            TextButton(onClick = { startDeviceDiscovery() }) {
                                Text("Scan Network")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (discoveredDevices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isScanning) "Searching DLNA, AirPlay, FCast & Smart TVs..." else "No active receivers found on network",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(discoveredDevices) { device ->
                        val protocolLabel = when (device.protocol) {
                            CastProtocol.DLNA -> "DLNA / Smart TV"
                            CastProtocol.AIRPLAY -> "AirPlay"
                            CastProtocol.DIAL -> "DIAL"
                            CastProtocol.GOOGLE_CAST -> "Google Cast"
                            CastProtocol.WEB_RECEIVER -> "Web Receiver"
                            CastProtocol.FCAST -> "FCast"
                        }
                        val icon = when (device.protocol) {
                            CastProtocol.DLNA -> TvIcon
                            CastProtocol.AIRPLAY -> AirPlayIcon
                            CastProtocol.DIAL -> TvIcon
                            CastProtocol.GOOGLE_CAST -> CastIcon
                            CastProtocol.WEB_RECEIVER -> WebIcon
                            CastProtocol.FCAST -> CastIcon
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .refractiveGlass(
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = 4.dp
                                )
                                .clickable {
                                    CastSessionManager.castingDevice = device
                                    CastSessionManager.customFcastPort = device.port
                                    Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = protocolLabel,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    if (!device.modelName.isNullOrEmpty()) {
                                        Text(
                                            device.modelName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${device.ipAddress}:${device.port} • $protocolLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. FCast Receiver Website Link (For New Users)
                item {
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://fcast.org"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Don't have a receiver? Download from fcast.org")
                    }
                }

                // 4. Manual Connection Setup
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .refractiveGlass(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 6.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Manual Pairing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualIpText,
                                    onValueChange = { manualIpText = it },
                                    label = { Text("Receiver IP (e.g. 192.168.1.50)") },
                                    shape = RoundedCornerShape(12.dp),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        try {
                                            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                                                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                                .build()
                                            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
                                            scanner.startScan()
                                                .addOnSuccessListener { barcode ->
                                                    val raw = barcode.rawValue ?: ""
                                                    if (raw.isNotEmpty()) {
                                                        val device = parseFcastQr(raw)
                                                        if (device == null) {
                                                            Toast.makeText(context, "Could not parse QR code", Toast.LENGTH_LONG).show()
                                                            return@addOnSuccessListener
                                                        }
                                                        manualIpText = device.ipAddress
                                                        android.util.Log.d("CastWizardActivity", "QR device: ${device.name} ${device.ipAddress}:${device.port}")
                                                        
                                                        lifecycleScope.launch {
                                                            Toast.makeText(context, "Connecting to ${device.ipAddress}…", Toast.LENGTH_SHORT).show()
                                                            val reachable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                                try {
                                                                    kotlinx.coroutines.withTimeout(3000) {
                                                                        val socket = java.net.Socket()
                                                                        socket.connect(java.net.InetSocketAddress(device.ipAddress, device.port), 2500)
                                                                        socket.close()
                                                                        true
                                                                    }
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e("CastWizardActivity", "QR target unreachable: ${device.ipAddress}:${device.port} — ${e.message}")
                                                                    false
                                                                }
                                                            }
                                                            if (reachable) {
                                                                CastSessionManager.castingDevice = device
                                                                CastSessionManager.customFcastPort = device.port
                                                                CastSessionManager.saveRecentIp(context, device.ipAddress)
                                                                Toast.makeText(context, "✓ Selected ${device.name}", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Cannot reach ${device.ipAddress}:${device.port}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Scanner unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .refractiveGlass(
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = 2.dp
                                        )
                                ) {
                                    Icon(QrScannerIcon, contentDescription = "Scan QR", tint = Color.Unspecified)
                                }
                            }

                            if (reachabilityStatus != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = reachabilityStatus!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (reachabilityStatus!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val ip = manualIpText.trim()
                                        if (ip.isNotEmpty()) {
                                            val port = fcastPortText.trim().toIntOrNull() ?: CastSessionManager.customFcastPort
                                            val device = CastDevice("Manual Target", ip, port)
                                            CastSessionManager.castingDevice = device
                                            CastSessionManager.customFcastPort = port
                                            CastSessionManager.saveRecentIp(context, ip)
                                            recentIps = CastSessionManager.getRecentIps(context)
                                            Toast.makeText(context, "Selected receiver: $ip:$port", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Connect")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val ip = manualIpText.trim()
                                        if (ip.isNotEmpty()) {
                                            val port = fcastPortText.trim().toIntOrNull() ?: CastSessionManager.customFcastPort
                                            lifecycleScope.launch {
                                                reachabilityStatus = "Pinging $ip:$port..."
                                                val result = NetworkDiagnostics.testReachability(ip, port)
                                                reachabilityStatus = if (result.isReachable) "✓ ${result.message}" else "✗ ${result.message}"
                                            }
                                        } else {
                                            reachabilityStatus = "Enter an IP address to test"
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Test Ping")
                                }
                            }
                        }
                    }
                }

                // 5. Saved IPs History List
                if (recentIps.isNotEmpty()) {
                    item {
                        Text("Recent Connections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(recentIps) { ip ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .refractiveGlass(
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = 3.dp
                                )
                                .clickable { manualIpText = ip }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .refractiveGlass(shape = CircleShape, elevation = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "History", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(ip, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // 6. Configuration Ports settings
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .refractiveGlass(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 6.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = fcastPortText,
                                onValueChange = { 
                                    fcastPortText = it
                                    it.toIntOrNull()?.let { port ->
                                        CastSessionManager.customFcastPort = port
                                    }
                                },
                                label = { Text("Default TCP Port") },
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    platformImeOptions = androidx.compose.ui.text.input.PlatformImeOptions(
                                        privateImeOptions = "com.google.android.inputmethod.latin.noPersonalizedLearning"
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

private val QrScannerIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "QrScanner",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    // Corner top-left
    path(
        stroke = SolidColor(Color(0xFF8B5CF6)), // ThemePrimary
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 8f)
        lineTo(4f, 4f)
        lineTo(8f, 4f)
    }
    // Corner top-right
    path(
        stroke = SolidColor(Color(0xFF8B5CF6)),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(20f, 8f)
        lineTo(20f, 4f)
        lineTo(16f, 4f)
    }
    // Corner bottom-left
    path(
        stroke = SolidColor(Color(0xFF8B5CF6)),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 16f)
        lineTo(4f, 20f)
        lineTo(8f, 20f)
    }
    // Corner bottom-right
    path(
        stroke = SolidColor(Color(0xFF8B5CF6)),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(20f, 16f)
        lineTo(20f, 20f)
        lineTo(16f, 20f)
    }
    // Cyan scan line in middle
    path(
        stroke = SolidColor(Color(0xFF06B6D4)), // ThemeSecondary
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
    ) {
        moveTo(6f, 12f)
        lineTo(18f, 12f)
    }
}.build()

private val TvIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "TvIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color(0xFF38BDF8)),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(21f, 3f)
        horizontalLineTo(3f)
        curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
        verticalLineTo(17f)
        curveTo(1f, 18.1f, 1.9f, 19f, 3f, 19f)
        horizontalLineTo(8f)
        verticalLineTo(21f)
        horizontalLineTo(16f)
        verticalLineTo(19f)
        horizontalLineTo(21f)
        curveTo(22.1f, 19f, 23f, 18.1f, 23f, 17f)
        verticalLineTo(5f)
        curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
        close()
        moveTo(21f, 17f)
        horizontalLineTo(3f)
        verticalLineTo(5f)
        horizontalLineTo(21f)
        verticalLineTo(17f)
        close()
    }
}.build()

private val CastIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "CastIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color(0xFF818CF8)),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
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
        curveTo(12f, 14.92f, 7.07f, 10f, 1f, 10f)
        close()
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
    }
}.build()

private val WebIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "WebIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color(0xFF34D399)),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
        moveTo(11f, 19.93f)
        curveTo(7.05f, 19.44f, 4f, 16.08f, 4f, 12f)
        curveTo(4f, 11.38f, 4.08f, 10.79f, 4.21f, 10.21f)
        lineTo(9f, 15f)
        verticalLineTo(16f)
        curveTo(9f, 17.1f, 9.9f, 18f, 11f, 18f)
        verticalLineTo(19.93f)
        close()
        moveTo(17.9f, 17.39f)
        curveTo(17.64f, 16.58f, 16.9f, 16f, 16f, 16f)
        horizontalLineTo(15f)
        verticalLineTo(13f)
        curveTo(15f, 12.45f, 14.55f, 12f, 14f, 12f)
        horizontalLineTo(8f)
        verticalLineTo(10f)
        horizontalLineTo(10f)
        curveTo(10.55f, 10f, 11f, 9.55f, 11f, 9f)
        verticalLineTo(7f)
        horizontalLineTo(14f)
        curveTo(15.1f, 7f, 16f, 6.1f, 16f, 5f)
        verticalLineTo(4.59f)
        curveTo(18.93f, 6.15f, 20f, 9.17f, 20f, 12f)
        curveTo(20f, 14.08f, 19.2f, 15.97f, 17.9f, 17.39f)
        close()
    }
}.build()

private val TetheringIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "TetheringIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color(0xFFF59E0B)),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(12f, 11f)
        curveTo(10.34f, 11f, 9f, 12.34f, 9f, 14f)
        curveTo(9f, 15.66f, 10.34f, 17f, 12f, 17f)
        curveTo(13.66f, 17f, 15f, 15.66f, 15f, 14f)
        curveTo(15f, 12.34f, 13.66f, 11f, 12f, 11f)
        close()
        moveTo(12f, 7f)
        curveTo(8.13f, 7f, 5f, 10.13f, 5f, 14f)
        curveTo(5f, 15.93f, 5.78f, 17.68f, 7.05f, 18.95f)
        lineTo(8.46f, 17.54f)
        curveTo(7.55f, 16.63f, 7f, 15.38f, 7f, 14f)
        curveTo(7f, 11.24f, 9.24f, 9f, 12f, 9f)
        curveTo(14.76f, 9f, 17f, 11.24f, 17f, 14f)
        curveTo(17f, 15.38f, 16.45f, 16.63f, 15.54f, 17.54f)
        lineTo(16.95f, 18.95f)
        curveTo(18.22f, 17.68f, 19f, 15.93f, 19f, 14f)
        curveTo(19f, 10.13f, 15.87f, 7f, 12f, 7f)
        close()
    }
}.build()

private val AirPlayIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
    name = "AirPlayIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color(0xFF38BDF8)),
        pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
    ) {
        moveTo(6f, 22f)
        lineTo(18f, 22f)
        lineTo(12f, 16f)
        close()
        moveTo(21f, 3f)
        horizontalLineTo(3f)
        curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
        verticalLineTo(16f)
        curveTo(1f, 17.1f, 1.9f, 18f, 3f, 18f)
        horizontalLineTo(7f)
        verticalLineTo(16f)
        horizontalLineTo(3f)
        verticalLineTo(5f)
        horizontalLineTo(21f)
        verticalLineTo(16f)
        horizontalLineTo(17f)
        verticalLineTo(18f)
        horizontalLineTo(21f)
        curveTo(22.1f, 18f, 23f, 17.1f, 23f, 16f)
        verticalLineTo(5f)
        curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
        close()
    }
}.build()
