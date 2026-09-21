package com.castbrowse.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Material 3 Expressive Frosted Glass & Surface Design System
 * Provides high-contrast, accessible Material 3 surfaces with subtle, crisp
 * frosted-glass translucency, tonal elevation, and specular rim borders.
 * Ensures all foreground text, icons, and buttons remain razor-sharp and unblurred.
 */
object GlassmorphicTheme {

    fun liquidGlassColor(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        alphaMultiplier: Float = 1.0f
    ): Color {
        return when {
            isAmoled -> Color(0xFF101012)
            isDark -> Color(0xFF1E2028)
            else -> Color(0xFFEFF1F4)
        }
    }

    fun glassColor(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        alphaMultiplier: Float = 1.0f
    ): Color = liquidGlassColor(isDark, isAmoled, alphaMultiplier)

    fun specularBorder(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        width: Dp = 1.dp
    ): BorderStroke {
        val color = when {
            isAmoled -> Color(0xFF26262B)
            isDark -> Color(0xFF333644)
            else -> Color(0xFFE2E4EB)
        }
        return BorderStroke(width = width, color = color)
    }

    fun cardGlassGradient(
        isDark: Boolean = true,
        isAmoled: Boolean = false
    ): Brush {
        val base = liquidGlassColor(isDark, isAmoled)
        return SolidColor(base)
    }

    fun refractiveBorder(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        width: Dp = 1.dp
    ): BorderStroke = specularBorder(isDark, isAmoled, width)

    fun refractiveGlassBrush(
        isDark: Boolean = true,
        isAmoled: Boolean = false
    ): Brush {
        val base = liquidGlassColor(isDark, isAmoled)
        return SolidColor(base)
    }
}

/**
 * Standard Material 3 Expressive Shape for docked bottom bars
 */
class ChocolateBarShape(
    val cornerRadius: Dp = 20.dp,
    val notchDepth: Dp = 0.dp,
    val notchHeight: Dp = 0.dp,
    val waistFraction: Float = 0.50f
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 2f)
        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    radiusX = r,
                    radiusY = r
                )
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * Clean Material 3 Expressive Frosted Glass modifier.
 * Applies subtle tonal elevation, soft drop shadow, translucent frosted container,
 * and crisp specular border. All foreground contents stay 100% sharp.
 */
fun Modifier.frostedGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    glowColor: Color? = null,
    alpha: Float = 1.0f
): Modifier = composed {
    val containerColor = when {
        isAmoled -> Color(0xFF101012)
        isDark -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = glowColor?.copy(alpha = 0.5f)
        ?: if (isAmoled) Color(0xFF26262B)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    this
        .shadow(elevation = elevation, shape = shape)
        .clip(shape)
        .background(containerColor)
        .border(borderWidth, borderColor, shape)
}

/**
 * Modifier extension: Clean Material 3 Expressive Container
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp
): Modifier = frostedGlass(
    shape = shape,
    isDark = isDark,
    isAmoled = isAmoled,
    borderWidth = borderWidth,
    elevation = elevation
)

/**
 * Modifier extension: Clean Material 3 Expressive Container Plate
 */
fun Modifier.glassBackdropPlate(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    glowColor: Color? = null
): Modifier = frostedGlass(
    shape = shape,
    isDark = isDark,
    isAmoled = isAmoled,
    borderWidth = borderWidth,
    elevation = elevation,
    glowColor = glowColor
)

/**
 * Clean Material 3 Expressive Surface
 */
@Composable
fun RefractiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    glowColor: Color? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val containerColor = when {
        isAmoled -> Color(0xFF101012)
        isDark -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = glowColor?.copy(alpha = 0.5f)
        ?: if (isAmoled) Color(0xFF26262B)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
    ) {
        Box(contentAlignment = contentAlignment) {
            content()
        }
    }
}

/**
 * Compatibility alias for frostedGlass
 */
fun Modifier.refractiveGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    glowColor: Color? = null,
    enableBlur: Boolean = false
): Modifier = frostedGlass(
    shape = shape,
    isDark = isDark,
    isAmoled = isAmoled,
    borderWidth = borderWidth,
    elevation = elevation,
    glowColor = glowColor
)

/**
 * Tactile fluid micro-interaction: Subtle scale down on click
 */
fun Modifier.tactilePress(
    pressedScale: Float = 0.97f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1.0f,
        animationSpec = tween(
            durationMillis = 140,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
        ),
        label = "tactileScale"
    )

    this
        .scale(scale)
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val upOrCancel = waitForUpOrCancellation()
                    isPressed = false
                    if (upOrCancel != null && onClick != null) {
                        onClick()
                    }
                }
            }
        }
}

/**
 * No-op breathing modifier (replaces liquid animation with clean static Material Design)
 */
fun Modifier.liquidBreathing(
    minAlpha: Float = 0.82f,
    maxAlpha: Float = 1.0f,
    durationMillis: Int = 2400
): Modifier = this

/**
 * Data Model for Speed Dial Bookmarks
 */
data class SpeedDialItem(
    val id: String,
    val title: String,
    val url: String,
    val iconEmoji: String,
    val isDefault: Boolean = false,
    val accentColor: Long = 0xFF6366F1
)

/**
 * Manager for Speed Dial shortcuts
 */
object SpeedDialManager {
    private const val PREFS_NAME = "speed_dial_prefs"
    private const val KEY_SHORTCUTS = "speed_dial_shortcuts"

    val DEFAULT_SHORTCUTS = listOf(
        SpeedDialItem("1", "YouTube", "https://m.youtube.com", "play", isDefault = true, accentColor = 0xFFEF4444),
        SpeedDialItem("2", "Twitch", "https://m.twitch.tv", "game", isDefault = true, accentColor = 0xFFA855F7),
        SpeedDialItem("3", "Reddit", "https://reddit.com", "forum", isDefault = true, accentColor = 0xFFF97316),
        SpeedDialItem("4", "Vimeo", "https://vimeo.com", "movie", isDefault = true, accentColor = 0xFF06B6D4),
        SpeedDialItem("5", "SoundCloud", "https://m.soundcloud.com", "music", isDefault = true, accentColor = 0xFFF59E0B),
        SpeedDialItem("6", "Internet Archive", "https://archive.org", "folder", isDefault = true, accentColor = 0xFF10B981)
    )

    fun loadShortcuts(context: Context): List<SpeedDialItem> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SHORTCUTS, null) ?: return DEFAULT_SHORTCUTS

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SpeedDialItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SpeedDialItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        iconEmoji = obj.optString("iconEmoji", "🌐"),
                        isDefault = obj.optBoolean("isDefault", false),
                        accentColor = obj.optLong("accentColor", 0xFF6366F1)
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_SHORTCUTS else list
        } catch (e: Exception) {
            DEFAULT_SHORTCUTS
        }
    }

    fun saveShortcut(context: Context, item: SpeedDialItem) {
        val current = loadShortcuts(context).toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        saveList(context, current)
    }

    fun deleteShortcut(context: Context, id: String) {
        val current = loadShortcuts(context).toMutableList()
        current.removeAll { it.id == id }
        saveList(context, current)
    }

    private fun saveList(context: Context, list: List<SpeedDialItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("iconEmoji", item.iconEmoji)
                put("isDefault", item.isDefault)
                put("accentColor", item.accentColor)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SHORTCUTS, jsonArray.toString()).apply()
    }
}

/**
 * Material 3 Expressive Bottom Bar (Two-Row Combined Dock for bottom address bar mode)
 */
@Composable
fun ChocolateBottomBar(
    currentNavTab: Int,
    onTabSelected: (Int) -> Unit,
    extractedVideoCount: Int,
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
    topRowContent: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerColor = when {
            isAmoled -> Color(0xFF101012).copy(alpha = 0.94f)
            isDark -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
            else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
        }
        val borderColor = if (isAmoled) Color(0xFF26262B)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentNavTab == 0) {
                    // Top Row: Address bar & Quick actions (Only visible when on Browser tab)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topRowContent()
                    }

                    // Clean Material 3 Divider
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }

                // Bottom Row: Navigation Tabs
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    val tabWidth = maxWidth / 2
                    val indicatorOffset by animateDpAsState(
                        targetValue = if (currentNavTab == 0) 0.dp else tabWidth,
                        animationSpec = tween(
                            durationMillis = 240,
                            easing = FastOutSlowInEasing
                        ),
                        label = "m3BottomBarIndicator"
                    )

                    // Active Tab Indicator Pill
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Browser Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTabSelected(0) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isSelected = currentNavTab == 0
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(
                                    imageVector = AppIcons.Browser,
                                    contentDescription = "Browser",
                                    tint = contentColor,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "Browser",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                )
                            }
                        }

                        // Stream / Media Hub Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTabSelected(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isSelected = currentNavTab == 1
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant

                                BadgedBox(
                                    badge = {
                                        if (extractedVideoCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(
                                                    if (extractedVideoCount > 99) "99+" else "$extractedVideoCount",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = AppIcons.MediaLibrary,
                                        contentDescription = "Media Hub",
                                        tint = contentColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Text(
                                    text = "Media",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Floating Navigation Bar (Single Pill Dock for top address bar mode)
 */
@Composable
fun FloatingGlassmorphicBottomBar(
    currentNavTab: Int,
    onTabSelected: (Int) -> Unit,
    extractedVideoCount: Int,
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerColor = when {
            isAmoled -> Color(0xFF101012).copy(alpha = 0.94f)
            isDark -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
            else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
        }
        val borderColor = if (isAmoled) Color(0xFF26262B)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            tonalElevation = 4.dp,
            shadowElevation = 5.dp,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .height(54.dp)
                .fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val tabWidth = maxWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (currentNavTab == 0) 0.dp else tabWidth,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing
                    ),
                    label = "m3FloatingIndicator"
                )

                // Active Tab Indicator Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Browser Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onTabSelected(0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isSelected = currentNavTab == 0
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                imageVector = AppIcons.Browser,
                                contentDescription = "Browser",
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Browser",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }

                    // Media Hub Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onTabSelected(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isSelected = currentNavTab == 1
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            BadgedBox(
                                badge = {
                                    if (extractedVideoCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                if (extractedVideoCount > 99) "99+" else "$extractedVideoCount",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = AppIcons.MediaLibrary,
                                    contentDescription = "Media Hub",
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Media",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Crisp vector icon or clean domain monogram for Speed Dial tiles.
 * Guarantees high visual fidelity with zero naive emojis.
 */
@Composable
fun SpeedDialTileIcon(
    item: SpeedDialItem,
    isAddTile: Boolean,
    modifier: Modifier = Modifier
) {
    if (isAddTile) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Shortcut",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(24.dp)
        )
        return
    }

    val urlLower = item.url.lowercase()
    val titleLower = item.title.lowercase()
    val emoji = item.iconEmoji.lowercase()

    when {
        urlLower.contains("youtube.com") || urlLower.contains("youtu.be") || titleLower == "youtube" || emoji in listOf("▶️", "play", "youtube", "video") -> {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = item.title,
                tint = Color(0xFFEF4444),
                modifier = modifier.size(26.dp)
            )
        }
        urlLower.contains("twitch.tv") || titleLower == "twitch" || emoji in listOf("🟣", "twitch", "game", "gaming") -> {
            Icon(
                imageVector = AppIcons.Gamepad,
                contentDescription = item.title,
                tint = Color(0xFFA855F7),
                modifier = modifier.size(24.dp)
            )
        }
        urlLower.contains("reddit.com") || titleLower == "reddit" || emoji in listOf("🤖", "reddit", "forum", "chat") -> {
            Icon(
                imageVector = AppIcons.Forum,
                contentDescription = item.title,
                tint = Color(0xFFFF5700),
                modifier = modifier.size(24.dp)
            )
        }
        urlLower.contains("vimeo.com") || titleLower == "vimeo" || emoji in listOf("🎬", "vimeo", "movie", "film") -> {
            Icon(
                imageVector = AppIcons.Movie,
                contentDescription = item.title,
                tint = Color(0xFF06B6D4),
                modifier = modifier.size(24.dp)
            )
        }
        urlLower.contains("soundcloud.com") || titleLower == "soundcloud" || emoji in listOf("🎵", "soundcloud", "music", "audio") -> {
            Icon(
                imageVector = AppIcons.Music,
                contentDescription = item.title,
                tint = Color(0xFFF59E0B),
                modifier = modifier.size(24.dp)
            )
        }
        urlLower.contains("archive.org") || titleLower.contains("archive") || emoji in listOf("🏛️", "archive", "folder") -> {
            Icon(
                imageVector = AppIcons.FolderOpen,
                contentDescription = item.title,
                tint = Color(0xFF10B981),
                modifier = modifier.size(24.dp)
            )
        }
        emoji in listOf("📺", "tv") -> {
            Icon(
                imageVector = AppIcons.Tv,
                contentDescription = item.title,
                tint = Color(0xFF3B82F6),
                modifier = modifier.size(24.dp)
            )
        }
        emoji in listOf("⭐", "star", "favorite") -> {
            Icon(
                imageVector = AppIcons.Star,
                contentDescription = item.title,
                tint = Color(0xFFFBBF24),
                modifier = modifier.size(24.dp)
            )
        }
        emoji in listOf("🌐", "browser", "web") -> {
            Icon(
                imageVector = AppIcons.Browser,
                contentDescription = item.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(24.dp)
            )
        }
        else -> {
            val letter = item.title.trim().firstOrNull()?.uppercaseChar()
                ?: item.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").firstOrNull()?.uppercaseChar()
                ?: 'W'
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Material 3 Expressive Speed Dial Home Screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialHomeScreen(
    onOpenUrl: (String) -> Unit,
    onDismissToHome: () -> Unit,
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(SpeedDialManager.loadShortcuts(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<SpeedDialItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SpeedDialItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val homeBg = if (isAmoled) Color.Black else MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(homeBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Header with Material 3 Surface
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Tv,
                        contentDescription = "CastBrowse",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CastBrowse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Universal Web & Media Stream Caster",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Material 3 Expressive Quick Search / URL Bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    onOpenUrl(searchQuery.trim())
                                }
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search or type web address...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Bookmarks & Shortcuts Grid Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed Dial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Shortcut", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val columns = 4
            val allTiles = shortcuts.toMutableList()
            allTiles.add(
                SpeedDialItem(
                    id = "__add_new__",
                    title = "Add",
                    url = "",
                    iconEmoji = "add",
                    accentColor = 0xFF71717A
                )
            )

            allTiles.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        val isAddTile = item.id == "__add_new__"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isAddTile) {
                                            showAddDialog = true
                                        } else {
                                            onOpenUrl(item.url)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isAddTile) {
                                            itemToEdit = item
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isAddTile) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = if (isAddTile) 1.dp else 2.dp,
                                    shadowElevation = 2.dp,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAddTile) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SpeedDialTileIcon(item = item, isAddTile = isAddTile)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    val missing = columns - rowItems.size
                    if (missing > 0) {
                        repeat(missing) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Search Engine Shortcut Chips
            Text(
                text = "Popular Engines",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val engines = listOf(
                    Triple("DuckDuckGo", "https://duckduckgo.com", AppIcons.Browser),
                    Triple("Google", "https://www.google.com", Icons.Default.Search),
                    Triple("Bing", "https://www.bing.com", AppIcons.Browser),
                    Triple("Brave", "https://search.brave.com", AppIcons.Browser),
                    Triple("Wikipedia", "https://en.wikipedia.org", AppIcons.FolderOpen)
                )
                engines.forEach { (name, url, iconVector) ->
                    AssistChip(
                        onClick = { onOpenUrl(url) },
                        label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = name,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            TextButton(
                onClick = onDismissToHome,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    "Switch to Web View",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Add Bookmark Dialog
        if (showAddDialog) {
            AddShortcutDialog(
                onDismiss = { showAddDialog = false },
                isDark = isDark,
                isAmoled = isAmoled,
                onAdd = { newItem ->
                    SpeedDialManager.saveShortcut(context, newItem)
                    shortcuts = SpeedDialManager.loadShortcuts(context)
                    showAddDialog = false
                }
            )
        }

        // Edit Shortcut Dialog
        if (itemToEdit != null) {
            EditShortcutDialog(
                item = itemToEdit!!,
                onDismiss = { itemToEdit = null },
                onSave = { updated ->
                    SpeedDialManager.saveShortcut(context, updated)
                    shortcuts = SpeedDialManager.loadShortcuts(context)
                    itemToEdit = null
                },
                onDelete = { toDelete ->
                    SpeedDialManager.deleteShortcut(context, toDelete.id)
                    shortcuts = SpeedDialManager.loadShortcuts(context)
                    itemToEdit = null
                }
            )
        }

        // Delete Confirmation Dialog
        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Remove Shortcut") },
                text = { Text("Are you sure you want to remove '${itemToDelete?.title}' from your Speed Dial?") },
                shape = RoundedCornerShape(24.dp),
                confirmButton = {
                    TextButton(
                        onClick = {
                            itemToDelete?.let {
                                SpeedDialManager.deleteShortcut(context, it.id)
                                shortcuts = SpeedDialManager.loadShortcuts(context)
                            }
                            itemToDelete = null
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Dialog to add a custom speed dial shortcut
 */
@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    onAdd: (SpeedDialItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var selectedIconKey by remember { mutableStateOf("browser") }

    val iconOptions = listOf(
        Triple("browser", AppIcons.Browser, "Web"),
        Triple("play", Icons.Default.PlayArrow, "Video"),
        Triple("movie", AppIcons.Movie, "Movie"),
        Triple("music", AppIcons.Music, "Music"),
        Triple("game", AppIcons.Gamepad, "Game"),
        Triple("forum", AppIcons.Forum, "Forum"),
        Triple("tv", AppIcons.Tv, "TV"),
        Triple("star", AppIcons.Star, "Star")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Add Bookmark Shortcut", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Select Icon Category:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    iconOptions.forEach { (key, vector, label) ->
                        val isSelected = selectedIconKey == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedIconKey = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else url
                        val newItem = SpeedDialItem(
                            id = java.util.UUID.randomUUID().toString(),
                            title = title.trim(),
                            url = finalUrl.trim(),
                            iconEmoji = selectedIconKey,
                            isDefault = false
                        )
                        onAdd(newItem)
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog to edit, rename, or remove any speed dial shortcut (including pre-added presets)
 */
@Composable
fun EditShortcutDialog(
    item: SpeedDialItem,
    onDismiss: () -> Unit,
    onSave: (SpeedDialItem) -> Unit,
    onDelete: (SpeedDialItem) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var url by remember { mutableStateOf(item.url) }
    var selectedIconKey by remember { mutableStateOf(item.iconEmoji) }

    val iconOptions = listOf(
        Triple("browser", AppIcons.Browser, "Web"),
        Triple("play", Icons.Default.PlayArrow, "Video"),
        Triple("movie", AppIcons.Movie, "Movie"),
        Triple("music", AppIcons.Music, "Music"),
        Triple("game", AppIcons.Gamepad, "Game"),
        Triple("forum", AppIcons.Forum, "Forum"),
        Triple("tv", AppIcons.Tv, "TV"),
        Triple("star", AppIcons.Star, "Star")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Rename / Edit Shortcut", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Rename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Select Icon Category:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    iconOptions.forEach { (key, vector, label) ->
                        val isSelected = selectedIconKey == key || (key == "play" && (selectedIconKey == "▶️" || selectedIconKey == "video")) || (key == "music" && (selectedIconKey == "🎵" || selectedIconKey == "audio")) || (key == "game" && selectedIconKey == "🟣") || (key == "forum" && selectedIconKey == "🤖") || (key == "movie" && selectedIconKey == "🎬") || (key == "star" && selectedIconKey == "⭐")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedIconKey = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            "https://$url"
                        } else url
                        val updated = item.copy(
                            title = title.trim(),
                            url = finalUrl.trim(),
                            iconEmoji = selectedIconKey
                        )
                        onSave(updated)
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onDelete(item) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
