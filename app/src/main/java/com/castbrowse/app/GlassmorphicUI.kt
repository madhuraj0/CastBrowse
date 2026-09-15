package com.castbrowse.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Premium Refractive Liquid Glass Design System
 * Provides multi-layer specular rim gradients, caustic highlights,
 * two-row chocolate bar bottom navigation, speed dial bookmarks,
 * and tactile fluid physics micro-animations.
 */
object GlassmorphicTheme {

    /**
     * Generate specular rim gradient border for acrylic glass effect.
     * Light reflects off top-left and fades toward bottom-right.
     */
    fun specularBorder(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        width: Dp = 1.dp
    ): BorderStroke {
        val topHighlight = when {
            isAmoled -> Color.White.copy(alpha = 0.25f)
            isDark -> Color.White.copy(alpha = 0.28f)
            else -> Color.White.copy(alpha = 0.65f)
        }
        val bottomShadow = when {
            isAmoled -> Color.White.copy(alpha = 0.04f)
            isDark -> Color.White.copy(alpha = 0.06f)
            else -> Color.Black.copy(alpha = 0.06f)
        }
        return BorderStroke(
            width = width,
            brush = Brush.linearGradient(
                colors = listOf(topHighlight, bottomShadow)
            )
        )
    }

    /**
     * Get acrylic translucent surface background color
     */
    fun glassColor(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        alphaMultiplier: Float = 1.0f
    ): Color {
        return when {
            isAmoled -> Color(0xFF08080C).copy(alpha = 0.88f * alphaMultiplier)
            isDark -> Color(0xFF161822).copy(alpha = 0.82f * alphaMultiplier)
            else -> Color(0xFFFCFCFD).copy(alpha = 0.85f * alphaMultiplier)
        }
    }

    /**
     * Card background with subtle ambient vertical gradient for depth
     */
    fun cardGlassGradient(
        isDark: Boolean = true,
        isAmoled: Boolean = false
    ): Brush {
        return if (isAmoled) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF14141E).copy(alpha = 0.75f),
                    Color(0xFF08080C).copy(alpha = 0.85f)
                )
            )
        } else if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF232738).copy(alpha = 0.70f),
                    Color(0xFF141622).copy(alpha = 0.80f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.90f),
                    Color(0xFFF1F3F9).copy(alpha = 0.80f)
                )
            )
        }
    }
    /**
     * Refractive specular rim gradient border simulating liquid glass refraction.
     * High specular reflection at top-left edge, subtle chromatic dispersion along the curve,
     * and caustic depth towards bottom-right.
     */
    fun refractiveBorder(
        isDark: Boolean = true,
        isAmoled: Boolean = false,
        width: Dp = 1.dp
    ): BorderStroke {
        val topHighlight = when {
            isAmoled -> Color.White.copy(alpha = 0.38f)
            isDark -> Color.White.copy(alpha = 0.42f)
            else -> Color.White.copy(alpha = 0.85f)
        }
        val midCaustic = when {
            isAmoled -> Color(0xFF6366F1).copy(alpha = 0.18f)
            isDark -> Color(0xFF818CF8).copy(alpha = 0.22f)
            else -> Color(0xFF6366F1).copy(alpha = 0.15f)
        }
        val bottomShadow = when {
            isAmoled -> Color.White.copy(alpha = 0.05f)
            isDark -> Color.White.copy(alpha = 0.08f)
            else -> Color.Black.copy(alpha = 0.08f)
        }
        return BorderStroke(
            width = width,
            brush = Brush.linearGradient(
                colors = listOf(topHighlight, midCaustic, bottomShadow),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        )
    }

    /**
     * Translucent liquid glass gradient with caustics and highlights.
     */
    fun refractiveGlassBrush(
        isDark: Boolean = true,
        isAmoled: Boolean = false
    ): Brush {
        return if (isAmoled) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF161822).copy(alpha = 0.85f),
                    Color(0xFF0C0D14).copy(alpha = 0.90f),
                    Color(0xFF131520).copy(alpha = 0.82f),
                    Color(0xFF07080C).copy(alpha = 0.94f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF282D42).copy(alpha = 0.78f),
                    Color(0xFF1B1E2E).copy(alpha = 0.85f),
                    Color(0xFF22263A).copy(alpha = 0.75f),
                    Color(0xFF141624).copy(alpha = 0.90f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    Color(0xFFF1F5FB).copy(alpha = 0.84f),
                    Color.White.copy(alpha = 0.88f),
                    Color(0xFFE5ECF6).copy(alpha = 0.86f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        }
    }
}

/**
 * Custom Compose Shape representing a dual-segment chocolate bar.
 * Features rounded corners and smooth inward waist notches on the left and right sides
 * where the two rows are joined as one continuous unit.
 */
class ChocolateBarShape(
    val cornerRadius: Dp = 26.dp,
    val notchDepth: Dp = 6.dp,
    val notchHeight: Dp = 14.dp,
    val waistFraction: Float = 0.50f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 2f)
        val nd = with(density) { notchDepth.toPx() }
        val nh = with(density) { notchHeight.toPx() }
        val w = size.width
        val h = size.height
        val waistY = h * waistFraction

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            quadraticBezierTo(w, 0f, w, r)

            val rightNotchTop = (waistY - nh / 2f).coerceAtLeast(r)
            val rightNotchBottom = (waistY + nh / 2f).coerceAtMost(h - r)
            lineTo(w, rightNotchTop)
            cubicTo(
                w - nd * 0.4f, rightNotchTop,
                w - nd, waistY - nh * 0.2f,
                w - nd, waistY
            )
            cubicTo(
                w - nd, waistY + nh * 0.2f,
                w - nd * 0.4f, rightNotchBottom,
                w, rightNotchBottom
            )

            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)

            val leftNotchBottom = (waistY + nh / 2f).coerceAtMost(h - r)
            val leftNotchTop = (waistY - nh / 2f).coerceAtLeast(r)
            lineTo(0f, leftNotchBottom)
            cubicTo(
                nd * 0.4f, leftNotchBottom,
                nd, waistY + nh * 0.2f,
                nd, waistY
            )
            cubicTo(
                nd, waistY - nh * 0.2f,
                nd * 0.4f, leftNotchTop,
                0f, leftNotchTop
            )

            lineTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Modifier extension: Apply acrylic glassmorphic surface with specular rim border and shadow
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(18.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp
): Modifier = this
    .shadow(elevation = elevation, shape = shape, clip = false)
    .border(
        border = GlassmorphicTheme.specularBorder(isDark, isAmoled, borderWidth),
        shape = shape
    )
    .background(
        brush = GlassmorphicTheme.cardGlassGradient(isDark, isAmoled),
        shape = shape
    )
    .clip(shape)

/**
 * Modifier extension: Apply refractive liquid glass surface with specular rim,
 * caustic glow shadow, and translucent refractive backdrop.
 */
fun Modifier.refractiveGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 10.dp,
    glowColor: Color? = null
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = if (isDark) Color.Black.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.12f),
        spotColor = glowColor ?: (if (isDark) Color(0xFF6366F1).copy(alpha = 0.28f) else Color(0xFF6366F1).copy(alpha = 0.15f))
    )
    .background(
        brush = GlassmorphicTheme.refractiveGlassBrush(isDark, isAmoled),
        shape = shape
    )
    .border(
        border = GlassmorphicTheme.refractiveBorder(isDark, isAmoled, borderWidth),
        shape = shape
    )
    .clip(shape)

/**
 * Tactile spring micro-interaction: subtle scale-down on press with bouncy spring release
 */
fun Modifier.tactilePress(
    pressedScale: Float = 0.95f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
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
 * Speed Dial item representing a pinned or favorite streaming shortcut on the home/new tab page
 */
data class SpeedDialItem(
    val id: String,
    val title: String,
    val url: String,
    val iconEmoji: String = "🌐",
    val accentColor: Long = 0xFF6366F1,
    val isDefault: Boolean = false
)

object SpeedDialManager {
    private const val PREFS_KEY = "speed_dial_shortcuts_json"

    val DEFAULT_SHORTCUTS = listOf(
        SpeedDialItem("yt", "YouTube", "https://m.youtube.com", "▶", 0xFFFF0000, true),
        SpeedDialItem("tw", "Twitch", "https://m.twitch.tv", "🟣", 0xFF9146FF, true),
        SpeedDialItem("vm", "Vimeo", "https://vimeo.com/watch", "🎬", 0xFF1AB7EA, true),
        SpeedDialItem("arc", "Internet Archive", "https://archive.org/details/movies", "🏛", 0xFF4B5563, true),
        SpeedDialItem("dm", "Dailymotion", "https://www.dailymotion.com", "📺", 0xFF0066DC, true),
        SpeedDialItem("sc", "SoundCloud", "https://m.soundcloud.com", "🎵", 0xFFFF5500, true)
    )

    fun loadShortcuts(context: Context): List<SpeedDialItem> {
        val prefs = EncryptedStorage.getPreferences(context)
        val raw = prefs.getString(PREFS_KEY, null)
        if (raw.isNullOrEmpty()) {
            return DEFAULT_SHORTCUTS
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SpeedDialItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SpeedDialItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        iconEmoji = obj.optString("icon", "🌐"),
                        accentColor = obj.optLong("color", 0xFF6366F1),
                        isDefault = obj.optBoolean("isDefault", false)
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
        current.removeAll { it.url == item.url || it.id == item.id }
        current.add(0, item)
        saveList(context, current)
    }

    fun deleteShortcut(context: Context, id: String) {
        val current = loadShortcuts(context).toMutableList()
        current.removeAll { it.id == id }
        saveList(context, current)
    }

    private fun saveList(context: Context, list: List<SpeedDialItem>) {
        val prefs = EncryptedStorage.getPreferences(context)
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("icon", item.iconEmoji)
                put("color", item.accentColor)
                put("isDefault", item.isDefault)
            })
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }
}

/**
 * Refractive Liquid Glass Chocolate Bottom Navigation Bar.
 * Joined like a chocolate bar: single floating unit with subtle side indentations (notches)
 * and an etched snap line separating the two rows.
 * Top Row: Address bar, tab counter icon, three dot menu button.
 * Bottom Row: Browser and Stream (Media Hub) tabs with liquid sliding selection indicator.
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
    val chocolateShape = remember {
        ChocolateBarShape(
            cornerRadius = 24.dp,
            notchDepth = 5.dp,
            notchHeight = 12.dp,
            waistFraction = 0.50f
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .refractiveGlass(
                    shape = chocolateShape,
                    isDark = isDark,
                    isAmoled = isAmoled,
                    borderWidth = 1.dp,
                    elevation = 14.dp
                )
        ) {
            // Top Row: Address bar, Tab count, Three-dot menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topRowContent()
            }

            // Chocolate Bar Waist Snap Groove (Etched specular horizontal divider)
            val grooveShadow = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.12f)
            val grooveHighlight = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.65f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                val nd = 5.dp.toPx()
                drawLine(
                    color = grooveShadow,
                    start = Offset(nd, 0f),
                    end = Offset(size.width - nd, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = grooveHighlight,
                    start = Offset(nd, 1f),
                    end = Offset(size.width - nd, 1f),
                    strokeWidth = 1f
                )
            }

            // Bottom Row: Browser and Stream (Media Hub) navigation tabs
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                val tabWidth = maxWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (currentNavTab == 0) 0.dp else tabWidth,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "chocolateBottomBarIndicator"
                )

                // Liquid sliding selection pill indicator
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.32f else 0.20f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        )
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
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onTabSelected(0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Browser",
                                tint = if (currentNavTab == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Browser",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentNavTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentNavTab == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Stream / Media Hub Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onTabSelected(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Stream",
                                    tint = if (currentNavTab == 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = "Stream",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentNavTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentNavTab == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Refractive Glass Bottom Navigation Bar (Single Pill for top address bar mode)
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
        val capsuleShape = RoundedCornerShape(26.dp)
        Box(
            modifier = Modifier
                .refractiveGlass(
                    shape = capsuleShape,
                    isDark = isDark,
                    isAmoled = isAmoled,
                    borderWidth = 1.dp,
                    elevation = 12.dp
                )
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
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomBarIndicator"
                )

                // Liquid sliding selection pill indicator
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.32f else 0.20f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(22.dp)
                        )
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
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onTabSelected(0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Browser",
                                tint = if (currentNavTab == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Browser",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentNavTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentNavTab == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Media Hub Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onTabSelected(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Media Hub",
                                    tint = if (currentNavTab == 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Media Hub",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentNavTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentNavTab == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Home & New Tab Speed Dial Screen
 * Glassmorphic bookmarks hub for quick streaming navigation.
 */
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
    var itemToDelete by remember { mutableStateOf<SpeedDialItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Brand Header with Specular Refractive Glass Surface
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .refractiveGlass(shape = CircleShape, isDark = isDark, isAmoled = isAmoled, elevation = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📡", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CastBrowse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Universal Web & Media Stream Caster",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Refractive Liquid Glass Quick Search / URL Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .refractiveGlass(shape = RoundedCornerShape(22.dp), isDark = isDark, isAmoled = isAmoled, elevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
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
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search or type web address...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                onOpenUrl(searchQuery.trim())
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Speed Dial Shortcuts Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites & Shortcuts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Shortcut", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bookmarks Grid (4 columns)
            val columns = 4
            val allTiles = shortcuts + listOf(
                SpeedDialItem(
                    id = "__add_new__",
                    title = "Add",
                    url = "",
                    iconEmoji = "➕",
                    accentColor = 0xFF6366F1
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
                                .tactilePress {
                                    if (isAddTile) {
                                        showAddDialog = true
                                    } else {
                                        onOpenUrl(item.url)
                                    }
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                // Refractive Glass Shortcut Icon Tile
                                val tileColor = Color(item.accentColor)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .refractiveGlass(
                                            shape = RoundedCornerShape(16.dp),
                                            isDark = isDark,
                                            isAmoled = isAmoled,
                                            elevation = 6.dp,
                                            glowColor = if (!isAddTile) tileColor.copy(alpha = 0.35f) else null
                                        )
                                        .background(
                                            if (isAddTile) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            } else {
                                                tileColor.copy(alpha = if (isDark) 0.22f else 0.14f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.iconEmoji,
                                        fontSize = 22.sp
                                    )
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

                            // Delete icon on non-default shortcuts
                            if (!isAddTile && !item.isDefault) {
                                IconButton(
                                    onClick = { itemToDelete = item },
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Fill remaining slots in row if not full
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Switch to DuckDuckGo raw page button
            OutlinedButton(
                onClick = onDismissToHome,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    "View Default Search Engine",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }

        // Add Shortcut Dialog
        if (showAddDialog) {
            AddShortcutDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { newItem ->
                    SpeedDialManager.saveShortcut(context, newItem)
                    shortcuts = SpeedDialManager.loadShortcuts(context)
                    showAddDialog = false
                }
            )
        }

        // Confirm Delete Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Remove Shortcut?") },
                text = { Text("Are you sure you want to remove '${item.title}' from your bookmarks?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            SpeedDialManager.deleteShortcut(context, item.id)
                            shortcuts = SpeedDialManager.loadShortcuts(context)
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
    onAdd: (SpeedDialItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var emoji by remember { mutableStateOf("⭐") }
    val emojiOptions = listOf("⭐", "🎬", "🎵", "📺", "🎮", "📻", "⚡", "🌐", "🍿")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bookmark Shortcut", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
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
                    text = "Select Icon:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    emojiOptions.forEach { opt ->
                        Surface(
                            shape = CircleShape,
                            color = if (emoji == opt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (emoji == opt) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { emoji = opt }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(opt, fontSize = 16.sp)
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
                            iconEmoji = emoji,
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
