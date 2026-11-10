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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 是否启用液态玻璃（透出底层背景的半透明质感） */
val LocalGlass = staticCompositionLocalOf { false }

/** 当前配色的点缀色：滑块填充、深色底栏高亮等「小面积强调」用它 */
val LocalPaletteAccent = staticCompositionLocalOf { Color(0xFFAAC8EA) }

/** 点缀色上的深色（滑块圆点） */
val LocalPaletteAccentDeep = staticCompositionLocalOf { Color(0xFF21435A) }

/** 深色悬浮底栏的选中色 */
val LocalPaletteDarkBarAccent = staticCompositionLocalOf { Color(0xFFA2C0E1) }

/**
 * 卡片不透明度的「实时预览值」：拖动滑块时只改这个 snapshot state。
 * 千万不要写 Repo.state —— 那会 copy 整个 AppState，让 setContent 与 AppRoot 两处
 * collectAsState 全树重组（连常驻的课表网格都每帧重画），实测会明显掉帧。
 * 松手后由 MineScreen 提交到 Repo 防抖落盘。
 */
val CardAlphaPreview = mutableFloatStateOf(1f)

/**
 * 高频变化的值必须传 State、不传 Float：provider 的 value 永远是同一个对象，
 * 拖动时 provider 和所有卡片都不进入 composition 重组，
 * 只有 draw 阶段读 .value 的那层矩形重绘（Compose 官方的 phase 优化）。
 */
val LocalCardAlpha = staticCompositionLocalOf<State<Float>> { CardAlphaPreview }

/** 保留独立一层只为语义清晰；它自身不会再因滑块重组 */
@Composable
fun CardAlphaProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCardAlpha provides CardAlphaPreview, content = content)
}

/** 是否设置了背景图：有背景图时卡片需要更「托底」，否则文字会糊在图上看不清 */
val LocalHasBackground = staticCompositionLocalOf { false }

/**
 * 悬浮底栏的毛玻璃采样源：整屏内容（壁纸 + 页面），内容从玻璃下方滚过。
 * 为 null 表示液态玻璃已关闭。
 */
val LocalContentHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/**
 * 卡片专用采样源：只包「壁纸 + 遮罩」。
 * 与底栏分开是故意的：共用一个源会让卡片把别的卡片和文字也模糊进去，出现重影。
 */
val LocalWallpaperHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

// 玻璃配方由库自带的 LocalHazeStyle 统一提供（MainActivity 里 provide 一份），
// 底栏和卡片自动共享同一组 blurRadius / tint / noise / fallbackTint。

// 默认配色「石墨」：强调色不用饱和色，浅色模式走近黑、深色模式走近白。
// 课程色、错误色等「语义色」不受配色方案影响。
private val GraphiteLight = lightColorScheme(
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

private val GraphiteDark = darkColorScheme(
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

/**
 * 一套主题配色：明暗两套 ColorScheme + 点缀色。
 * 深色底统一保持纯黑，只换强调色和卡片的微色，避免破坏「纯黑 + 中性分层」的既有观感。
 */
data class AppPalette(
    val id: Int,
    val label: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    /** 滑块填充的浅色点缀 */
    val accent: Color,
    /** 滑块圆点：点缀上的深色 */
    val accentDeep: Color,
    /** 深色悬浮底栏的选中色 */
    val darkBarAccent: Color
) {
    /** 设置页色卡预览：品牌色、点缀色、深色模式品牌色 */
    val preview: List<Color> get() = listOf(light.primary, accent, dark.primary)
}

private val SakuraLight = lightColorScheme(
    primary = Color(0xFFC93E70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE3EB),
    onPrimaryContainer = Color(0xFF7A2346),
    secondary = Color(0xFF8A5A6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E4EA),
    onSecondaryContainer = Color(0xFF5A2C3C),
    tertiary = Color(0xFFB5567E),
    onTertiary = Color.White,
    background = Color(0xFFFBF6F8),
    onBackground = Color(0xFF1C1418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1418),
    surfaceVariant = Color(0xFFF4EBEF),
    onSurfaceVariant = Color(0xFF72636A),
    outline = Color(0xFFDCCFD5),
    error = Color(0xFFE5484D)
)

private val SakuraDark = darkColorScheme(
    primary = Color(0xFFFF9EC0),
    onPrimary = Color(0xFF56122F),
    primaryContainer = Color(0xFF4A1F31),
    onPrimaryContainer = Color(0xFFFFD7E5),
    secondary = Color(0xFFD9A9B8),
    onSecondary = Color(0xFF3A1C26),
    secondaryContainer = Color(0xFF3A2830),
    onSecondaryContainer = Color(0xFFF1DDE4),
    tertiary = Color(0xFFFFB3C9),
    onTertiary = Color(0xFF541A2E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF6EEF1),
    surface = Color(0xFF141214),
    onSurface = Color(0xFFF6EEF1),
    surfaceVariant = Color(0xFF201D1F),
    onSurfaceVariant = Color(0xFFBCAEB4),
    outline = Color(0xFF5E5458),
    error = Color(0xFFFF8A8A)
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF2E6FD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1ECFB),
    onPrimaryContainer = Color(0xFF173E75),
    secondary = Color(0xFF5A6B85),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EBF5),
    onSecondaryContainer = Color(0xFF2F3D53),
    tertiary = Color(0xFF3E7E9E),
    onTertiary = Color.White,
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF131820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131820),
    surfaceVariant = Color(0xFFEDF2F9),
    onSurfaceVariant = Color(0xFF647085),
    outline = Color(0xFFCFD8E4),
    error = Color(0xFFE5484D)
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF9CC0FF),
    onPrimary = Color(0xFF0E2A56),
    primaryContainer = Color(0xFF1C3252),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFFA9B8CE),
    onSecondary = Color(0xFF1B2635),
    secondaryContainer = Color(0xFF27313F),
    onSecondaryContainer = Color(0xFFDDE5EF),
    tertiary = Color(0xFF8FC6DD),
    onTertiary = Color(0xFF0C2E3B),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEFF3F9),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFEFF3F9),
    surfaceVariant = Color(0xFF1D2127),
    onSurfaceVariant = Color(0xFFAFB8C6),
    outline = Color(0xFF525A66),
    error = Color(0xFFFF8A8A)
)

private val MintLight = lightColorScheme(
    primary = Color(0xFF17805F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3EA),
    onPrimaryContainer = Color(0xFF0B4A36),
    secondary = Color(0xFF5A7368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4F0EA),
    onSecondaryContainer = Color(0xFF2C433A),
    tertiary = Color(0xFF3E8E7D),
    onTertiary = Color.White,
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF111A16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111A16),
    surfaceVariant = Color(0xFFEBF4EF),
    onSurfaceVariant = Color(0xFF5F7168),
    outline = Color(0xFFCCDBD4),
    error = Color(0xFFE5484D)
)

private val MintDark = darkColorScheme(
    primary = Color(0xFF74DCB2),
    onPrimary = Color(0xFF073D2B),
    primaryContainer = Color(0xFF17402F),
    onPrimaryContainer = Color(0xFFCCF2E0),
    secondary = Color(0xFFA3C4B4),
    onSecondary = Color(0xFF14261E),
    secondaryContainer = Color(0xFF24352E),
    onSecondaryContainer = Color(0xFFD7E9E0),
    tertiary = Color(0xFF8FD3B4),
    onTertiary = Color(0xFF0A2F24),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDF5F1),
    surface = Color(0xFF111614),
    onSurface = Color(0xFFEDF5F1),
    surfaceVariant = Color(0xFF1C2420),
    onSurfaceVariant = Color(0xFFADBBB3),
    outline = Color(0xFF4F5C55),
    error = Color(0xFFFF8A8A)
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFFB25C12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBEADB),
    onPrimaryContainer = Color(0xFF6B3308),
    secondary = Color(0xFF806A57),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E9DD),
    onSecondaryContainer = Color(0xFF4E3B2B),
    tertiary = Color(0xFFA97A3C),
    onTertiary = Color.White,
    background = Color(0xFFFBF7F2),
    onBackground = Color(0xFF1C1611),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1611),
    surfaceVariant = Color(0xFFF4EDE4),
    onSurfaceVariant = Color(0xFF75685C),
    outline = Color(0xFFDDD2C4),
    error = Color(0xFFE5484D)
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB77A),
    onPrimary = Color(0xFF52270B),
    primaryContainer = Color(0xFF4A2A12),
    onPrimaryContainer = Color(0xFFFFE0C4),
    secondary = Color(0xFFD5BBA4),
    onSecondary = Color(0xFF332416),
    secondaryContainer = Color(0xFF382C22),
    onSecondaryContainer = Color(0xFFEFDFD0),
    tertiary = Color(0xFFE5B586),
    onTertiary = Color(0xFF3E2510),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF7F1EB),
    surface = Color(0xFF151210),
    onSurface = Color(0xFFF7F1EB),
    surfaceVariant = Color(0xFF201B17),
    onSurfaceVariant = Color(0xFFC0B3A6),
    outline = Color(0xFF61564B),
    error = Color(0xFFFF8A8A)
)

private val LilacLight = lightColorScheme(
    primary = Color(0xFF7A5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FB),
    onPrimaryContainer = Color(0xFF3E2A79),
    secondary = Color(0xFF6C6385),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9F7),
    onSecondaryContainer = Color(0xFF40364F),
    tertiary = Color(0xFF9660B8),
    onTertiary = Color.White,
    background = Color(0xFFF8F6FC),
    onBackground = Color(0xFF17131F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17131F),
    surfaceVariant = Color(0xFFF1EDF8),
    onSurfaceVariant = Color(0xFF6D657C),
    outline = Color(0xFFD6CFE2),
    error = Color(0xFFE5484D)
)

private val LilacDark = darkColorScheme(
    primary = Color(0xFFC0A8FF),
    onPrimary = Color(0xFF311C63),
    primaryContainer = Color(0xFF35265C),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFC0B4D8),
    onSecondary = Color(0xFF282036),
    secondaryContainer = Color(0xFF2E283C),
    onSecondaryContainer = Color(0xFFE4DBF1),
    tertiary = Color(0xFFD2A9F2),
    onTertiary = Color(0xFF3A1F4E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2EEF9),
    surface = Color(0xFF131117),
    onSurface = Color(0xFFF2EEF9),
    surfaceVariant = Color(0xFF1E1B24),
    onSurfaceVariant = Color(0xFFB4ACC4),
    outline = Color(0xFF595266),
    error = Color(0xFFFF8A8A)
)

/** 全部可选配色；顺序即设置页的展示顺序 */
val AppPalettes: List<AppPalette> = listOf(
    AppPalette(
        id = 0, label = "石墨", light = GraphiteLight, dark = GraphiteDark,
        accent = Color(0xFFAAC8EA), accentDeep = Color(0xFF21435A),
        darkBarAccent = Color(0xFFA2C0E1)
    ),
    AppPalette(
        id = 1, label = "樱花粉", light = SakuraLight, dark = SakuraDark,
        accent = Color(0xFFF3AFC5), accentDeep = Color(0xFF7A2743),
        darkBarAccent = Color(0xFFFFB9D2)
    ),
    AppPalette(
        id = 2, label = "海盐蓝", light = OceanLight, dark = OceanDark,
        accent = Color(0xFFA6C6F2), accentDeep = Color(0xFF1F3F66),
        darkBarAccent = Color(0xFFA9C8FF)
    ),
    AppPalette(
        id = 3, label = "薄荷绿", light = MintLight, dark = MintDark,
        accent = Color(0xFFA0DFC8), accentDeep = Color(0xFF1B4F3D),
        darkBarAccent = Color(0xFF9FE8C9)
    ),
    AppPalette(
        id = 4, label = "暖阳橙", light = SunsetLight, dark = SunsetDark,
        accent = Color(0xFFF5C79A), accentDeep = Color(0xFF6B3A15),
        darkBarAccent = Color(0xFFFFCE9C)
    ),
    AppPalette(
        id = 5, label = "紫丁香", light = LilacLight, dark = LilacDark,
        accent = Color(0xFFC9B6EE), accentDeep = Color(0xFF3E2C6B),
        darkBarAccent = Color(0xFFCDBBFF)
    )
)

/** 按 id 取配色，未知 id 回落到默认「石墨」 */
fun appPalette(id: Int): AppPalette = AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()

/** 该配色 + 明暗的窗口底色（非 Composable，启动窗口设置用） */
fun paletteBackgroundColor(paletteId: Int, dark: Boolean): Color =
    if (dark) appPalette(paletteId).dark.background else appPalette(paletteId).light.background

/** 当前主题模式最终是不是深色（0 跟随系统 / 1 浅色 / 2 深色） */
@Composable
fun isDarkMode(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

/** 该主题模式 + 配色下的窗口/页面底色，启动窗口与切换过渡都靠它对齐 */
@Composable
fun themeBackgroundColor(themeMode: Int, paletteId: Int = 0): Color =
    paletteBackgroundColor(paletteId, isDarkMode(themeMode))

/** 主题切换渐变的时长：整套配色同时插值，短了像闪、长了拖沓 */
private const val THEME_FADE_MS = 450

@Composable
fun AppTheme(
    themeMode: Int,
    paletteId: Int = 0,
    liquidGlass: Boolean,
    content: @Composable () -> Unit
) {
    val palette = appPalette(paletteId)
    val target = if (isDarkMode(themeMode)) palette.dark else palette.light
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
    CompositionLocalProvider(
        LocalGlass provides liquidGlass,
        LocalPaletteAccent provides palette.accent,
        LocalPaletteAccentDeep provides palette.accentDeep,
        LocalPaletteDarkBarAccent provides palette.darkBarAccent
    ) {
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
