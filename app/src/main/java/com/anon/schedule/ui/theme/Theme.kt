package com.anon.schedule.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 是否启用液态玻璃（透出底层背景的半透明质感） */
val LocalGlass = staticCompositionLocalOf { false }

/** 是否设置了背景图：有背景图时卡片需要更「托底」，否则文字会糊在图上看不清 */
val LocalHasBackground = staticCompositionLocalOf { false }

/**
 * 玻璃采样源：AppRoot 把「背景图 + 遮罩」单独录进一个图层，卡片从这里取像素做折射/模糊。
 * 为 null（未开启液态玻璃 / 低版本）时卡片回退到半透明底色。
 */
val LocalBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

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

/** 当前主题模式最终是不是深色（0 跟随系统 / 1 浅色 / 2 深色） */
@Composable
fun isDarkMode(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

/** 该主题模式下窗口/页面底色，启动窗口与切换过渡都靠它对齐 */
@Composable
fun themeBackgroundColor(themeMode: Int): Color =
    if (isDarkMode(themeMode)) DarkColors.background else LightColors.background

/** 主题切换渐变的时长：整套配色同时插值，短了像闪、长了拖沓 */
private const val THEME_FADE_MS = 450

@Composable
fun AppTheme(themeMode: Int, liquidGlass: Boolean, content: @Composable () -> Unit) {
    val target = if (isDarkMode(themeMode)) DarkColors else LightColors
    // 深浅切换：把整套 ColorScheme 从旧配色逐帧插值到新配色。
    // 走主题层而不是全屏遮罩，是因为 Dialog 等独立窗口盖不住，插值才能让弹窗一起渐变。
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (to == target) return@LaunchedEffect
        // 动画中途再切换时，从「当前看起来的颜色」接着走，避免跳变
        from = lerpScheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(THEME_FADE_MS, easing = FastOutSlowInEasing))
    }
    val scheme = if (progress.value >= 1f) to else lerpScheme(from, to, progress.value)
    CompositionLocalProvider(LocalGlass provides liquidGlass) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

/** 两套配色按 t（0 旧 → 1 新）逐槽插值；lerp 走 Oklab，过渡中间不会发灰发脏 */
private fun lerpScheme(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    fun c(from: Color, to: Color) = lerp(from, to, t)
    return ColorScheme(
        primary = c(a.primary, b.primary),
        onPrimary = c(a.onPrimary, b.onPrimary),
        primaryContainer = c(a.primaryContainer, b.primaryContainer),
        onPrimaryContainer = c(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = c(a.inversePrimary, b.inversePrimary),
        secondary = c(a.secondary, b.secondary),
        onSecondary = c(a.onSecondary, b.onSecondary),
        secondaryContainer = c(a.secondaryContainer, b.secondaryContainer),
        onSecondaryContainer = c(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = c(a.tertiary, b.tertiary),
        onTertiary = c(a.onTertiary, b.onTertiary),
        tertiaryContainer = c(a.tertiaryContainer, b.tertiaryContainer),
        onTertiaryContainer = c(a.onTertiaryContainer, b.onTertiaryContainer),
        background = c(a.background, b.background),
        onBackground = c(a.onBackground, b.onBackground),
        surface = c(a.surface, b.surface),
        onSurface = c(a.onSurface, b.onSurface),
        surfaceVariant = c(a.surfaceVariant, b.surfaceVariant),
        onSurfaceVariant = c(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = c(a.surfaceTint, b.surfaceTint),
        inverseSurface = c(a.inverseSurface, b.inverseSurface),
        inverseOnSurface = c(a.inverseOnSurface, b.inverseOnSurface),
        error = c(a.error, b.error),
        onError = c(a.onError, b.onError),
        errorContainer = c(a.errorContainer, b.errorContainer),
        onErrorContainer = c(a.onErrorContainer, b.onErrorContainer),
        outline = c(a.outline, b.outline),
        outlineVariant = c(a.outlineVariant, b.outlineVariant),
        scrim = c(a.scrim, b.scrim),
        surfaceBright = c(a.surfaceBright, b.surfaceBright),
        surfaceDim = c(a.surfaceDim, b.surfaceDim),
        surfaceContainer = c(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = c(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = c(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = c(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainerLowest = c(a.surfaceContainerLowest, b.surfaceContainerLowest),
        primaryFixed = c(a.primaryFixed, b.primaryFixed),
        primaryFixedDim = c(a.primaryFixedDim, b.primaryFixedDim),
        onPrimaryFixed = c(a.onPrimaryFixed, b.onPrimaryFixed),
        onPrimaryFixedVariant = c(a.onPrimaryFixedVariant, b.onPrimaryFixedVariant),
        secondaryFixed = c(a.secondaryFixed, b.secondaryFixed),
        secondaryFixedDim = c(a.secondaryFixedDim, b.secondaryFixedDim),
        onSecondaryFixed = c(a.onSecondaryFixed, b.onSecondaryFixed),
        onSecondaryFixedVariant = c(a.onSecondaryFixedVariant, b.onSecondaryFixedVariant),
        tertiaryFixed = c(a.tertiaryFixed, b.tertiaryFixed),
        tertiaryFixedDim = c(a.tertiaryFixedDim, b.tertiaryFixedDim),
        onTertiaryFixed = c(a.onTertiaryFixed, b.onTertiaryFixed),
        onTertiaryFixedVariant = c(a.onTertiaryFixedVariant, b.onTertiaryFixedVariant)
    )
}
