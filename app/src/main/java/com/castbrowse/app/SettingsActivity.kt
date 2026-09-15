package com.castbrowse.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", false))

        setContent {
            CastBrowseTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                SettingsScreen(
                    currentTheme = themeMode,
                    isDynamicColor = dynamicColor,
                    onThemeChange = { newTheme ->
                        themeMode = newTheme
                        prefs.edit().putString("theme_mode", newTheme).apply()
                    },
                    onDynamicColorChange = { enabled ->
                        dynamicColor = enabled
                        prefs.edit().putBoolean("dynamic_color", enabled).apply()
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
    isDynamicColor: Boolean,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { EncryptedStorage.getPreferences(context) }

    val isAmoled = currentTheme == "amoled"
    val isDark = currentTheme != "light"

    var isAdBlockEnabled by remember { mutableStateOf(prefs.getBoolean("adblock_enabled", true)) }
    var isPopupsEnabled by remember { mutableStateOf(prefs.getBoolean("popups_enabled", false)) }
    var isDesktopDefault by remember { mutableStateOf(prefs.getBoolean("desktop_mode", false)) }
    var isBottomBarEnabled by remember { mutableStateOf(prefs.getBoolean("bottom_address_bar", true)) }
    var isTabBarEnabled by remember { mutableStateOf(prefs.getBoolean("show_tab_bar", true)) }
    var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", false)) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearSessionDialog by remember { mutableStateOf(false) }
    var isUpdatingAdblock by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .refractiveGlass(
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        isDark = isDark,
                        isAmoled = isAmoled,
                        elevation = 8.dp
                    )
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
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Appearance Section
            SettingsSection(title = "Appearance") {
                val themeLabel = if (currentTheme == "light") "Light" else "Dark"

                SettingsActionItem(
                    title = "Theme",
                    subtitle = themeLabel,
                    icon = Icons.Default.Settings,
                    onClick = { showThemeDialog = true }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)
                    SettingsToggleItem(
                        title = "Material You",
                        subtitle = "Wallpaper accent colors",
                        icon = Icons.Default.Star,
                        checked = isDynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Top Tab Bar",
                    subtitle = "Show tab bar strip",
                    icon = Icons.Default.PlayArrow,
                    checked = isTabBarEnabled,
                    onCheckedChange = {
                        isTabBarEnabled = it
                        prefs.edit().putBoolean("show_tab_bar", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Desktop Site",
                    subtitle = "Request desktop version",
                    icon = Icons.Default.Settings,
                    checked = isDesktopDefault,
                    onCheckedChange = {
                        isDesktopDefault = it
                        prefs.edit().putBoolean("desktop_mode", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Bottom Address Bar",
                    subtitle = "Controls at bottom",
                    icon = Icons.Default.Settings,
                    checked = isBottomBarEnabled,
                    onCheckedChange = {
                        isBottomBarEnabled = it
                        prefs.edit().putBoolean("bottom_address_bar", it).apply()
                    }
                )
            }

            // 2. Privacy & Security Section
            SettingsSection(title = "Privacy & Security") {
                SettingsToggleItem(
                    title = "Adblock Shield",
                    subtitle = "Block ads and trackers",
                    icon = Icons.Default.Star,
                    checked = isAdBlockEnabled,
                    onCheckedChange = {
                        isAdBlockEnabled = it
                        prefs.edit().putBoolean("adblock_enabled", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Update Adblock Filters",
                    subtitle = if (isUpdatingAdblock) "Updating..." else "Steven Black hosts",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        if (!isUpdatingAdblock) {
                            isUpdatingAdblock = true
                            MediaExtractorClient.updateAdHosts(context) { success, count ->
                                isUpdatingAdblock = false
                                Toast.makeText(
                                    context,
                                    if (success) "$count hosts loaded" else "Update failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Block Popups",
                    subtitle = "Prevent popups and redirects",
                    icon = Icons.Default.Close,
                    checked = !isPopupsEnabled,
                    onCheckedChange = {
                        isPopupsEnabled = !it
                        prefs.edit().putBoolean("popups_enabled", !it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Browsing History",
                    subtitle = "Save visited links",
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
                    subtitle = "History and cookies",
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
                    title = "FCast Wizard",
                    subtitle = "Pair receivers",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(context, CastWizardActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Cast Control",
                    subtitle = "Playback controls",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val intent = Intent(context, CastControlActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Local Media Proxy",
                    subtitle = "Port ${LocalMediaProxy.proxyPort} • VPN leak safe",
                    icon = Icons.Default.Check,
                    onClick = {
                        Toast.makeText(context, "Proxy active on port ${LocalMediaProxy.proxyPort}", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 4. About
            SettingsSection(title = "About") {
                SettingsActionItem(
                    title = "Privacy",
                    subtitle = "100% on-device, zero tracking",
                    icon = Icons.Default.Info,
                    onClick = { showPrivacyDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Credits",
                    subtitle = "FCast, Steven Black, Jetpack",
                    icon = Icons.Default.Info,
                    onClick = { showCreditsDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "GitHub",
                    subtitle = "Source code & releases",
                    icon = Icons.Default.Info,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/madhuraj0/CastBrowse")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Version",
                    subtitle = "CastBrowse v1.5.0",
                    icon = Icons.Default.Info,
                    onClick = {}
                )
            }

            // 5. Clear Session
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showClearSessionDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Session",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Clear Session & Exit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Wipe tabs, cookies, and exit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Theme Picker Dialog (Restricted strictly to Light & Dark)
    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.width(260.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    listOf(
                        "dark" to "Dark (OLED Black)",
                        "light" to "Light"
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
                                selected = (currentTheme == mode) || (mode == "dark" && currentTheme != "light"),
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

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = Color.Transparent,
            modifier = Modifier.refractiveGlass(
                shape = RoundedCornerShape(24.dp),
                isDark = isDark,
                isAmoled = isAmoled,
                elevation = 20.dp
            ),
            title = { Text("Privacy", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• 100% on-device operation", style = MaterialTheme.typography.bodyMedium)
                    Text("• Zero tracking or analytics", style = MaterialTheme.typography.bodyMedium)
                    Text("• Local proxy for stream casting", style = MaterialTheme.typography.bodyMedium)
                    Text("• Screen recording protection enabled", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Credits Dialog
    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            containerColor = Color.Transparent,
            modifier = Modifier.refractiveGlass(
                shape = RoundedCornerShape(24.dp),
                isDark = isDark,
                isAmoled = isAmoled,
                elevation = 20.dp
            ),
            title = { Text("Credits", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• FCast Protocol", style = MaterialTheme.typography.bodyMedium)
                    Text("• Steven Black Hosts", style = MaterialTheme.typography.bodyMedium)
                    Text("• Android Jetpack & Compose", style = MaterialTheme.typography.bodyMedium)
                    Text("• madhuraj0 & Antigravity", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Clear Session Dialog
    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            containerColor = Color.Transparent,
            modifier = Modifier.refractiveGlass(
                shape = RoundedCornerShape(24.dp),
                isDark = isDark,
                isAmoled = isAmoled,
                elevation = 20.dp,
                glowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            ),
            title = { Text("Clear Session?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Wipes all cookies, cached tabs, active casts, and exits.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSessionDialog = false
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_PANIC_WIPE", true)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Clear & Exit", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
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
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .refractiveGlass(
                    shape = RoundedCornerShape(20.dp),
                    elevation = 6.dp
                )
                .padding(vertical = 4.dp)
        ) {
            Column {
                content()
            }
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .refractiveGlass(shape = CircleShape, elevation = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .refractiveGlass(shape = CircleShape, elevation = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
