package com.castbrowse.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Accent color option model for Material 3 Expressive theme selection
 */
data class AccentColorOption(
    val key: String,
    val name: String,
    val previewColor: Color
)

val ACCENT_OPTIONS = listOf(
    AccentColorOption("default", "Slate", Color(0xFF64748B)),
    AccentColorOption("blue", "Blue", Color(0xFF3B82F6)),
    AccentColorOption("indigo", "Indigo", Color(0xFF6366F1)),
    AccentColorOption("teal", "Teal", Color(0xFF14B8A6)),
    AccentColorOption("green", "Green", Color(0xFF22C55E)),
    AccentColorOption("amber", "Amber", Color(0xFFF59E0B)),
    AccentColorOption("rose", "Rose", Color(0xFFF43F5E)),
    AccentColorOption("purple", "Purple", Color(0xFFA855F7))
)

// --- Preset Color Palettes ---

private fun buildLightScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color = Color(0xFF64748B)
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F5),
    onSecondaryContainer = Color(0xFF1E293B),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color(0xFFEFF1F4),
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626)
)

private fun buildDarkScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color = Color(0xFF94A3B8)
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF0B0E14),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color(0xFF0B0E14),
    secondaryContainer = Color(0xFF272832),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF111216),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF17181E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF20222A),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF1E2028),
    surfaceContainerLow = Color(0xFF16171D),
    surfaceContainerHigh = Color(0xFF262832),
    outline = Color(0xFF3F4250),
    outlineVariant = Color(0xFF2D303C),
    error = Color(0xFFEF4444)
)

private fun buildOledScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color = Color(0xFFA1A1AA)
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.Black,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF18181C),
    onSecondaryContainer = Color(0xFFE4E4E7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF141416),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0xFF101012),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF18181B),
    outline = Color(0xFF2A2A2E),
    outlineVariant = Color(0xFF1C1C20),
    error = Color(0xFFEF4444)
)

fun getAccentColorScheme(themeMode: String, accentKey: String): ColorScheme {
    val isLight = themeMode == "light"
    val isOled = themeMode == "oled" || themeMode == "amoled"

    return when (accentKey.lowercase()) {
        "blue" -> {
            if (isLight) buildLightScheme(Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF1E3A8A))
            else if (isOled) buildOledScheme(Color(0xFF60A5FA), Color(0xFF101F38), Color(0xFFDBEAFE))
            else buildDarkScheme(Color(0xFF60A5FA), Color(0xFF1E3A8A), Color(0xFFDBEAFE))
        }
        "indigo" -> {
            if (isLight) buildLightScheme(Color(0xFF4338CA), Color(0xFFE0E7FF), Color(0xFF312E81))
            else if (isOled) buildOledScheme(Color(0xFF818CF8), Color(0xFF181836), Color(0xFFE0E7FF))
            else buildDarkScheme(Color(0xFF818CF8), Color(0xFF312E81), Color(0xFFE0E7FF))
        }
        "teal" -> {
            if (isLight) buildLightScheme(Color(0xFF0F766E), Color(0xFFCCFBF1), Color(0xFF115E59))
            else if (isOled) buildOledScheme(Color(0xFF2DD4BF), Color(0xFF0A2624), Color(0xFFCCFBF1))
            else buildDarkScheme(Color(0xFF2DD4BF), Color(0xFF134E4A), Color(0xFFCCFBF1))
        }
        "green" -> {
            if (isLight) buildLightScheme(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF14532D))
            else if (isOled) buildOledScheme(Color(0xFF4ADE80), Color(0xFF0B2412), Color(0xFFDCFCE7))
            else buildDarkScheme(Color(0xFF4ADE80), Color(0xFF14532D), Color(0xFFDCFCE7))
        }
        "amber" -> {
            if (isLight) buildLightScheme(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFF78350F))
            else if (isOled) buildOledScheme(Color(0xFFFBBF24), Color(0xFF2B1D08), Color(0xFFFEF3C7))
            else buildDarkScheme(Color(0xFFFBBF24), Color(0xFF78350F), Color(0xFFFEF3C7))
        }
        "rose" -> {
            if (isLight) buildLightScheme(Color(0xFFBE123C), Color(0xFFFFE4E6), Color(0xFF881337))
            else if (isOled) buildOledScheme(Color(0xFFFB7185), Color(0xFF2E0F17), Color(0xFFFFE4E6))
            else buildDarkScheme(Color(0xFFFB7185), Color(0xFF881337), Color(0xFFFFE4E6))
        }
        "purple" -> {
            if (isLight) buildLightScheme(Color(0xFF7E22CE), Color(0xFFF3E8FF), Color(0xFF581C87))
            else if (isOled) buildOledScheme(Color(0xFFC084FC), Color(0xFF230D36), Color(0xFFF3E8FF))
            else buildDarkScheme(Color(0xFFC084FC), Color(0xFF581C87), Color(0xFFF3E8FF))
        }
        else -> { // "default" - Monochromatic Slate
            if (isLight) buildLightScheme(Color(0xFF18181B), Color(0xFFE4E4E7), Color(0xFF18181B))
            else if (isOled) buildOledScheme(Color(0xFFF4F4F5), Color(0xFF222226), Color(0xFFFFFFFF))
            else buildDarkScheme(Color(0xFFF4F4F5), Color(0xFF272832), Color(0xFFFAFAFA))
        }
    }
}

@Composable
fun CastBrowseTheme(
    themeMode: String = "dark",
    dynamicColor: Boolean = false,
    accentColor: String = "default",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isLight = themeMode == "light"
    val isOled = themeMode == "oled" || themeMode == "amoled"

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isLight) {
            dynamicLightColorScheme(context)
        } else if (isOled) {
            dynamicDarkColorScheme(context).copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color(0xFF101012),
                surfaceContainerLow = Color(0xFF080808),
                surfaceContainerHigh = Color(0xFF18181B),
                outline = Color(0xFF2A2A2E),
                outlineVariant = Color(0xFF1C1C20)
            )
        } else {
            dynamicDarkColorScheme(context)
        }
    } else {
        getAccentColorScheme(themeMode, accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
