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

// Clean, neutral grayscale dark theme (True Black #000000 OLED option)
private val GrayscaleDarkScheme = darkColorScheme(
    primary = Color(0xFFF1F5F9),           // Slate 100
    onPrimary = Color(0xFF09090B),         // Near black
    primaryContainer = Color(0xFF27272A),  // Charcoal container
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFA1A1AA),         // Muted silver
    onSecondary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFE4E4E7),
    background = Color(0xFF000000),        // Pure Black OLED
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF121214),           // Deep charcoal
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1E1E22),    // Crisp dark variant
    onSurfaceVariant = Color(0xFFA1A1AA),  // Neutral secondary text
    outline = Color(0xFF3F3F46),           // Neutral border
    error = Color(0xFFEF4444)
)

// Clean, neutral grayscale light theme
private val GrayscaleLightScheme = lightColorScheme(
    primary = Color(0xFF18181B),           // Pure ink slate
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E7),  // Soft zinc container
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF71717A),         // Zinc gray
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    background = Color(0xFFFAFAFA),        // Clean neutral white
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626)
)

@Composable
fun CastBrowseTheme(
    themeMode: String = "dark",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = themeMode != "light"
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) GrayscaleDarkScheme else GrayscaleLightScheme
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
