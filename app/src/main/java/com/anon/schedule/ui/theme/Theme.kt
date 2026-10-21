package com.anon.schedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 是否启用液态玻璃（透出底层背景的半透明质感） */
val LocalGlass = staticCompositionLocalOf { false }

/** 是否设置了背景图：有背景图时卡片需要更「托底」，否则文字会糊在图上看不清 */
val LocalHasBackground = staticCompositionLocalOf { false }

val Pink50 = Color(0xFFFFF0F5)
val Pink100 = Color(0xFFFFD9E6)
val Pink300 = Color(0xFFFF9EC4)
val Pink500 = Color(0xFFE8629B)
val Pink700 = Color(0xFFB83A72)

private val LightColors = lightColorScheme(
    primary = Pink500,
    onPrimary = Color.White,
    primaryContainer = Pink100,
    onPrimaryContainer = Pink700,
    secondary = Color(0xFF7A6CF0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E1FF),
    onSecondaryContainer = Color(0xFF3B2FA8),
    tertiary = Color(0xFF3DBBA0),
    onTertiary = Color.White,
    background = Color(0xFFFDF7FA),
    onBackground = Color(0xFF2B2227),
    surface = Color(0xFFFFFBFD),
    onSurface = Color(0xFF2B2227),
    surfaceVariant = Color(0xFFF4E6EC),
    onSurfaceVariant = Color(0xFF5D4A52),
    outline = Color(0xFFD9C2CC),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Pink300,
    onPrimary = Color(0xFF4E0E2C),
    primaryContainer = Pink700,
    onPrimaryContainer = Pink100,
    secondary = Color(0xFFB9B1FF),
    onSecondary = Color(0xFF2A1E8F),
    secondaryContainer = Color(0xFF443BA8),
    onSecondaryContainer = Color(0xFFE6E1FF),
    tertiary = Color(0xFF6FE0C5),
    onTertiary = Color(0xFF00382C),
    background = Color(0xFF1A1216),
    onBackground = Color(0xFFF3E4EA),
    surface = Color(0xFF221920),
    onSurface = Color(0xFFF3E4EA),
    surfaceVariant = Color(0xFF3A2C33),
    onSurfaceVariant = Color(0xFFE2C7D1),
    outline = Color(0xFF6E5761),
    error = Color(0xFFFF8A8A)
)

@Composable
fun AppTheme(themeMode: Int, liquidGlass: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalGlass provides liquidGlass) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content
        )
    }
}
