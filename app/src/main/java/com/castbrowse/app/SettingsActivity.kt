package com.castbrowse.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val prefs = EncryptedStorage.getPreferences(this)
        var themeMode by mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark")

        setContent {
            CastBrowseTheme(themeMode = themeMode) {
                SettingsScreen(
                    currentTheme = themeMode,
                    onThemeChange = { newTheme ->
                        themeMode = newTheme
                        prefs.edit().putString("theme_mode", newTheme).apply()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { EncryptedStorage.getPreferences(context) }

    var isAdBlockEnabled by remember { mutableStateOf(prefs.getBoolean("adblock_enabled", true)) }
    var isPopupsEnabled by remember { mutableStateOf(prefs.getBoolean("popups_enabled", false)) }
    var isDesktopDefault by remember { mutableStateOf(prefs.getBoolean("desktop_mode", false)) }
    var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", false)) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPanicDialog by remember { mutableStateOf(false) }
    var isUpdatingAdblock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Appearance Section
            SettingsSection(title = "Appearance") {
                val themeLabel = when (currentTheme) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    "amoled" -> "AMOLED Black"
                    "dynamic" -> "Material You"
                    else -> "System Default"
                }

                SettingsActionItem(
                    title = "Theme",
                    subtitle = themeLabel,
                    icon = Icons.Default.Settings,
                    onClick = { showThemeDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Desktop Site by Default",
                    subtitle = "Always request full desktop version of websites",
                    icon = Icons.Default.Settings,
                    checked = isDesktopDefault,
                    onCheckedChange = {
                        isDesktopDefault = it
                        prefs.edit().putBoolean("desktop_mode", it).apply()
                    }
                )
            }

            // 2. Privacy & Security Section
            SettingsSection(title = "Privacy & Security") {
                SettingsToggleItem(
                    title = "Adblock Shield",
                    subtitle = "Block ads, malicious trackers, and telemetry domains",
                    icon = Icons.Default.Star,
                    checked = isAdBlockEnabled,
                    onCheckedChange = {
                        isAdBlockEnabled = it
                        prefs.edit().putBoolean("adblock_enabled", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Update Adblock Filter List",
                    subtitle = if (isUpdatingAdblock) "Downloading Steven Black hosts..." else "Fetch latest hosts from Steven Black GitHub",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        if (!isUpdatingAdblock) {
                            isUpdatingAdblock = true
                            Toast.makeText(context, "Updating adblock filters...", Toast.LENGTH_SHORT).show()
                            MediaExtractorClient.updateAdHosts(context) { success, count ->
                                isUpdatingAdblock = false
                                if (success) {
                                    Toast.makeText(context, "Adblock list updated! $count hosts loaded.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to update adblock list. Check internet connection.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Block Popups & Redirects",
                    subtitle = "Prevent websites from opening unsolicited new windows",
                    icon = Icons.Default.Close,
                    checked = !isPopupsEnabled,
                    onCheckedChange = {
                        isPopupsEnabled = !it
                        prefs.edit().putBoolean("popups_enabled", !it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Record Browsing History",
                    subtitle = "Locally store visited pages for quick retrieval",
                    icon = Icons.AutoMirrored.Filled.List,
                    checked = isHistoryEnabled,
                    onCheckedChange = {
                        isHistoryEnabled = it
                        prefs.edit().putBoolean("history_enabled", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Clear Browsing Data",
                    subtitle = "View and remove search history, visited links, and cached states",
                    icon = Icons.Default.Delete,
                    onClick = {
                        val intent = Intent(context, HistoryActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            // 3. Casting & Media Controls
            SettingsSection(title = "Casting & FCast") {
                SettingsActionItem(
                    title = "FCast Connection Wizard",
                    subtitle = "Discover and pair with Android TV and FCast receivers",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(context, CastWizardActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Cast Control Panel",
                    subtitle = "Manage active playback, seek bar, and volume controls",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(context, CastControlActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Anti-Hotlink Media Proxy",
                    subtitle = "Dynamic port: ${LocalMediaProxy.proxyPort} • RFC 3986 HLS segment resolving active",
                    icon = Icons.Default.Check,
                    onClick = {
                        Toast.makeText(context, "Proxy active on port ${LocalMediaProxy.proxyPort}", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 4. About & Privacy Policy
            SettingsSection(title = "About & Privacy") {
                SettingsActionItem(
                    title = "Privacy Guarantee",
                    subtitle = "100% on-device operation. Zero telemetry, zero analytics.",
                    icon = Icons.Default.Info,
                    onClick = { showPrivacyDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Credits & Open Source Licenses",
                    subtitle = "FCast, Steven Black Hosts, Android Jetpack, OkHttp",
                    icon = Icons.Default.Info,
                    onClick = { showCreditsDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Version",
                    subtitle = "CastBrowse v1.2.0 (Build 3)",
                    icon = Icons.Default.Info,
                    onClick = {}
                )
            }

            // 5. Destructive Session Wipe
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPanicDialog = true }
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Panic Wipe",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Clear Session & Panic Wipe",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Immediately terminates active casts, wipes history, tabs, cookies and session storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Theme Picker Dialog
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
                                    onThemeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    onThemeChange(mode)
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

    // Privacy Guarantee Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "CastBrowse operates 100% locally on your Android device.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "• Zero telemetry or analytical tracking.\n" +
                        "• No account registration or external server dependencies.\n" +
                        "• Cookies and session headers are proxied purely to authorize video streams on your local FCast receiver.\n" +
                        "• Screenshot & screen-record protection is enforced via FLAG_SECURE.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Credits Dialog
    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = { Text("Credits & Collaborators", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Open Source Projects:\n" +
                        "• FCast protocol & client\n" +
                        "• Steven Black Adblock Hosts\n" +
                        "• Kotlin Coroutines & Android Jetpack\n" +
                        "• OkHttp & SSDP Discovery",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Collaborators:\n" +
                        "• madhuraj0 (Idea, Design Direction & Testing)\n" +
                        "• Antigravity (Advanced AI Assistant)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Panic Wipe Dialog
    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text("Panic Wipe Session?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will immediately terminate all casting sessions, erase all history, cookies, cached tabs, and exit the application.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPanicDialog = false
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_PANIC_WIPE", true)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Wipe Everything", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
