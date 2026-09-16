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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val prefs = EncryptedStorage.getPreferences(this)
        var themeMode by mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark")
        var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", false))
        var accentColor by mutableStateOf(prefs.getString("accent_color", "default") ?: "default")

        setContent {
            CastBrowseTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accentColor = accentColor
            ) {
                SettingsScreen(
                    currentTheme = themeMode,
                    currentAccent = accentColor,
                    isDynamicColor = dynamicColor,
                    onThemeChange = { newTheme ->
                        themeMode = newTheme
                        prefs.edit().putString("theme_mode", newTheme).apply()
                    },
                    onAccentChange = { newAccent ->
                        accentColor = newAccent
                        prefs.edit().putString("accent_color", newAccent).apply()
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
    currentAccent: String,
    isDynamicColor: Boolean,
    onThemeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { EncryptedStorage.getPreferences(context) }

    val isAmoled = currentTheme == "oled" || currentTheme == "amoled"

    var isAdBlockEnabled by remember { mutableStateOf(prefs.getBoolean("adblock_enabled", true)) }
    var isPopupsEnabled by remember { mutableStateOf(prefs.getBoolean("popups_enabled", false)) }
    var isDesktopDefault by remember { mutableStateOf(prefs.getBoolean("desktop_mode", false)) }
    var isBottomBarEnabled by remember { mutableStateOf(prefs.getBoolean("bottom_address_bar", true)) }
    var isTabBarEnabled by remember { mutableStateOf(prefs.getBoolean("show_tab_bar", true)) }
    var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", false)) }

    var showCreditsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearSessionDialog by remember { mutableStateOf(false) }
    var isUpdatingAdblock by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = if (isAmoled) Color(0xFF101012) else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
            // 1. Theme & Appearance Section (Material 3 Expressive 2026)
            SettingsSection(title = "Theme & Appearance") {
                // Base Theme Segmented Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Base Theme",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple("light", "Light", "☀️"),
                            Triple("dark", "Dark", "🌙"),
                            Triple("oled", "OLED Black", "⬛")
                        )
                        themeOptions.forEach { (mode, label, icon) ->
                            val isSelected = when (mode) {
                                "oled" -> currentTheme == "oled" || currentTheme == "amoled"
                                "dark" -> currentTheme == "dark"
                                else -> currentTheme == "light"
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onThemeChange(mode) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(icon, fontSize = 18.sp)
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                // Accent Color Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Dynamic Color (Material You) Toggle on Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onDynamicColorChange(!isDynamicColor) }
                                .background(
                                    if (isDynamicColor) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("✨", fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Dynamic Accent (Material You)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Match system wallpaper palette",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isDynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }

                    // Accent Palette Swatches
                    Text(
                        text = if (isDynamicColor) "Preset Palettes (Overrides Dynamic):" else "Select Preset Accent:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ACCENT_OPTIONS.forEach { opt ->
                            val isChosen = !isDynamicColor && (currentAccent.equals(opt.key, ignoreCase = true) || (opt.key == "default" && currentAccent.isBlank()))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isChosen) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable {
                                        onDynamicColorChange(false)
                                        onAccentChange(opt.key)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(opt.previewColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChosen) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = opt.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
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
                    subtitle = if (isUpdatingAdblock) "Downloading..." else "Check for updated blocklists",
                    icon = Icons.Default.Settings,
                    onClick = {
                        if (!isUpdatingAdblock) {
                            isUpdatingAdblock = true
                            MediaExtractorClient.updateAdHosts(context) { ok, count ->
                                isUpdatingAdblock = false
                                Toast.makeText(
                                    context,
                                    if (ok) "Adblock filters updated ($count domains)" else "Filter update failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Block Popups",
                    subtitle = "Block unexpected popups",
                    icon = Icons.Default.Star,
                    checked = isPopupsEnabled,
                    onCheckedChange = {
                        isPopupsEnabled = it
                        prefs.edit().putBoolean("popups_enabled", it).apply()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsToggleItem(
                    title = "Browsing History",
                    subtitle = "Save visited sites",
                    icon = Icons.Default.Settings,
                    checked = isHistoryEnabled,
                    onCheckedChange = {
                        isHistoryEnabled = it
                        prefs.edit().putBoolean("history_enabled", it).apply()
                    }
                )
            }

            // 3. About Section
            SettingsSection(title = "About") {
                SettingsActionItem(
                    title = "Privacy Policy",
                    subtitle = "Zero tracking details",
                    icon = Icons.Default.Star,
                    onClick = { showPrivacyDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Credits & Open Source",
                    subtitle = "Third-party libraries",
                    icon = Icons.Default.Settings,
                    onClick = { showCreditsDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), thickness = 0.5.dp)

                SettingsActionItem(
                    title = "Version",
                    subtitle = "1.14.1 (Material 2026 Expressive)",
                    icon = Icons.Default.Settings,
                    onClick = {}
                )
            }

            // 4. Danger Zone Section
            SettingsSection(title = "Danger Zone") {
                SettingsActionItem(
                    title = "Panic Wipe Session",
                    subtitle = "Clear cookies, tabs & active casts",
                    icon = Icons.Default.Settings,
                    onClick = { showClearSessionDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Credits", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• FCast Protocol", style = MaterialTheme.typography.bodyMedium)
                    Text("• Steven Black Hosts", style = MaterialTheme.typography.bodyMedium)
                    Text("• Android Jetpack Compose & Material 3", style = MaterialTheme.typography.bodyMedium)
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
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
