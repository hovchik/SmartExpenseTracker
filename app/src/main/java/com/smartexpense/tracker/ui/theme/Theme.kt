package com.smartexpense.tracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.smartexpense.tracker.data.model.ThemeMode

// Brand colors
val GreenPrimary = Color(0xFF10B981)
val GreenDark = Color(0xFF059669)
val GreenLight = Color(0xFF34D399)
val BluePrimary = Color(0xFF3B82F6)
val RedExpense = Color(0xFFEF4444)
val GreenIncome = Color(0xFF10B981)
val OrangeWarning = Color(0xFFF59E0B)
val PurpleAccent = Color(0xFF8B5CF6)

val SurfaceDark = Color(0xFF0F172A)
val SurfaceDarkCard = Color(0xFF1E293B)
val SurfaceLight = Color(0xFFF8FAFC)
val SurfaceLightCard = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenDark,
    secondary = BluePrimary,
    background = SurfaceDark,
    surface = SurfaceDarkCard,
    onBackground = Color.White,
    onSurface = Color(0xFFE2E8F0),
    error = RedExpense,
    surfaceVariant = Color(0xFF334155),
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    secondary = BluePrimary,
    background = SurfaceLight,
    surface = SurfaceLightCard,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF334155),
    error = RedExpense,
    surfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1)
)

/**
 * Resolves whether dark theme should be active based on ThemeMode.
 */
@Composable
fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

@Composable
fun SmartExpenseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveIsDarkTheme(themeMode)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
