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

/** 「我的」页调出的卡片背景不透明度（0.4~1.0），所有卡片共用 */
val LocalCardAlpha = staticCompositionLocalOf { 1f }

/** 是否设置了背景图：有背景图时卡片需要更「托底」，否则文字会糊在图上看不清 */
val LocalHasBackground = staticCompositionLocalOf { false }

/**
 * 玻璃采样源：AppRoot 把「背景图 + 遮罩」单独录进一个图层，卡片从这里取像素做折射/模糊。
 * 为 null（未开启液态玻璃 / 低版本）时卡片回退到半透明底色。
 */
val LocalBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

// 全站黑白化：强调色不再用饱和粉，浅色模式走近黑、深色模式走近白。
// 粉色调只从品牌层移除；课程色、错误色等「语义色」不受影响。
private val LightColors = lightColorScheme(
    primary = Color(0xFF141416),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEDF0),
    onPrimaryContainer = Color(0xFF141416),
    secondary = Color(0xFF5C5C63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEF),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF3C3C42),
    onTertiary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF141416),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141416),
    surfaceVariant = Color(0xFFEFEFF2),
    onSurfaceVariant = Color(0xFF6F6F78),
    outline = Color(0xFFD2D2D8),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F5),
    onPrimary = Color(0xFF141416),
    primaryContainer = Color(0xFF2C2C31),
    onPrimaryContainer = Color(0xFFF2F2F5),
    secondary = Color(0xFFB4B4BC),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF3A3A40),
    onSecondaryContainer = Color(0xFFE8E8EC),
    tertiary = Color(0xFFC9C9D0),
    onTertiary = Color(0xFF141416),
    // 深色模式底色纯黑，卡片/文字中性灰：靠明度分层，不靠色相
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1E1E21),
    onSurfaceVariant = Color(0xFFB4B4BC),
    outline = Color(0xFF5C5C63),
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
