package com.castbrowse.app

import android.os.Bundle
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
        discoveryService = SsdpDiscoveryService(this)

        val prefs = EncryptedStorage.getPreferences(this)
        val themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
        setContent {
            CastBrowseTheme(themeMode = themeMode) {
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
            discoveryService.discoverFcastDevices().collectLatest { fcastList ->
                fcastList.forEach { device ->
                    if (discoveredDevices.none { it.ipAddress == device.ipAddress }) {
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
            Toast.makeText(this@CastWizardActivity, "Loading test stream...", Toast.LENGTH_SHORT).show()
            
            val proxiedUrl = LocalMediaProxy.getProxyUrl(TEST_VIDEO_URL)
            val res = FCastClient.play(device.ipAddress, proxiedUrl, "FCast Test Stream", CastSessionManager.customFcastPort) {
                lifecycleScope.launch {
                    CastSessionManager.isMediaPlaying = false
                    CastSessionManager.activeMediaUrl = null
                }
            }
            
            CastSessionManager.isCasting = false
            res.onSuccess {
                CastSessionManager.isMediaPlaying = true
                CastSessionManager.activeMediaUrl = TEST_VIDEO_URL
                Toast.makeText(this@CastWizardActivity, "Test video playing!", Toast.LENGTH_LONG).show()
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

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Receiver Setup Wizard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        Color.Transparent
                                    )
                                )
                            )
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

                // 1. Current Active Session Card
                item {
                    val activeDevice = CastSessionManager.castingDevice
                    if (activeDevice != null) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle, 
                                        contentDescription = "Active Connection", 
                                        tint = MaterialTheme.colorScheme.secondary, 
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Active FCast Connection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            context.startActivity(android.content.Intent(context, CastControlActivity::class.java))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Controls", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Controller")
                                    }
                                    
                                    Button(
                                        onClick = { playTestStream(activeDevice) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Test Video", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Stream")
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedCard(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info, 
                                    contentDescription = "No connection", 
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Not Connected", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Pair with a TV receiver below to start casting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 2. Network Discovered Targets (Moved to Top!)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Network Targets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
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
                                Text("Scan Wifi")
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
                                text = if (isScanning) "Searching Wifi..." else "No active receivers found on network",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(discoveredDevices) { device ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CastSessionManager.castingDevice = device
                                    Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Receiver",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "${device.ipAddress}:${device.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Manual Pairing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                                                        CastSessionManager.saveRecentIp(context, device.ipAddress)
                                                        Toast.makeText(context, "✓ Connected to ${device.name}", Toast.LENGTH_SHORT).show()
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(QrScannerIcon, contentDescription = "Scan QR", tint = Color.Unspecified)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val ip = manualIpText.trim()
                            if (ip.isNotEmpty()) {
                                val device = CastDevice("Manual Target", ip)
                                CastSessionManager.castingDevice = device
                                CastSessionManager.saveRecentIp(context, ip)
                                recentIps = CastSessionManager.getRecentIps(context)
                                Toast.makeText(context, "Paired receiver: $ip", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Receiver")
                    }
                }

                // 5. Saved IPs History List
                if (recentIps.isNotEmpty()) {
                    item {
                        Text("Recent Connections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(recentIps) { ip ->
                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { manualIpText = ip }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "History", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(ip, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // 6. Configuration Ports settings
                item {
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
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
