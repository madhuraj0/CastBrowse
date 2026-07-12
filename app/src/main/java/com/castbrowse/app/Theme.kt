package com.castbrowse.app

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val FallbackDarkScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2E224F),
    onPrimaryContainer = Color(0xFFE5D5FF),
    secondary = Color(0xFF06B6D4),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0F323D),
    onSecondaryContainer = Color(0xFFCFF6FF),
    background = Color(0xFF0C0A12),
    onBackground = Color(0xFFF1EEF8),
    surface = Color(0xFF161421),
    onSurface = Color(0xFFE8E5F0),
    surfaceVariant = Color(0xFF211D31),
    onSurfaceVariant = Color(0xFFC9C5D6),
    outline = Color(0xFF2E2A40),
    error = Color(0xFFEF4444)
)

private val FallbackAmoledScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2E224F),
    onPrimaryContainer = Color(0xFFE5D5FF),
    secondary = Color(0xFF06B6D4),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0F323D),
    onSecondaryContainer = Color(0xFFCFF6FF),
    background = Color(0xFF000000), // Pure Black
    onBackground = Color(0xFFF1EEF8),
    surface = Color(0xFF07050A), // Extremely dark surface
    onSurface = Color(0xFFE8E5F0),
    surfaceVariant = Color(0xFF0C0A12),
    onSurfaceVariant = Color(0xFFC9C5D6),
    outline = Color(0xFF161421),
    error = Color(0xFFEF4444)
)

private val FallbackLightScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF0891B2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECFEFF),
    onSecondaryContainer = Color(0xFF164E63),
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626)
)

@Composable
fun CastBrowseTheme(
    themeMode: String = "dark",
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        "light" -> FallbackLightScheme
        "dark" -> FallbackDarkScheme
        "amoled" -> FallbackAmoledScheme
        "dynamic" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) FallbackDarkScheme else FallbackLightScheme
            }
        }
        "system" -> {
            if (darkTheme) FallbackDarkScheme else FallbackLightScheme
        }
        else -> FallbackDarkScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(16.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
