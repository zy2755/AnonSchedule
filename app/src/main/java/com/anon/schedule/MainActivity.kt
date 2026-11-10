package com.anon.schedule

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.AppIcons
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.notify.ClassReminder
import com.anon.schedule.ui.common.liquidGlassFinish
import com.anon.schedule.ui.course.EditCourseScreen
import com.anon.schedule.ui.theme.GlassParams
import com.anon.schedule.ui.theme.rememberGlassRefraction
import com.anon.schedule.ui.importer.ImportCsvScreen
import com.anon.schedule.ui.importer.ImportZfScreen
import com.anon.schedule.ui.mine.ChaoxingScreen
import com.anon.schedule.ui.mine.GradesScreen
import com.anon.schedule.ui.mine.MineScreen
import com.anon.schedule.ui.mine.UBangScreen
import com.anon.schedule.ui.mine.UpdateLogScreen
import com.anon.schedule.ui.schedule.ScheduleScreen
import com.anon.schedule.ui.settings.AppIconScreen
import com.anon.schedule.ui.settings.CourseListScreen
import com.anon.schedule.ui.settings.QuickActionsScreen
import com.anon.schedule.ui.settings.SettingsScreen
import com.anon.schedule.ui.settings.TimeTableScreen
import com.anon.schedule.ui.table.TableDataScreen
import com.anon.schedule.ui.table.TableManageScreen
import com.anon.schedule.ui.table.TableSettingsScreen
import com.anon.schedule.ui.table.TableStyleScreen
import com.anon.schedule.ui.theme.AppTheme
import com.anon.schedule.ui.theme.CardAlphaPreview
import com.anon.schedule.ui.theme.CardAlphaProvider
import com.anon.schedule.ui.theme.LocalContentHaze
import com.anon.schedule.ui.theme.LocalHasBackground
import com.anon.schedule.ui.theme.LocalPaletteDarkBarAccent
import com.anon.schedule.ui.theme.LocalWallpaperHaze
import com.anon.schedule.ui.theme.isDarkMode
import com.anon.schedule.ui.theme.paletteBackgroundColor
import com.anon.schedule.ui.today.TodayScreen
import com.anon.schedule.widget.Widgets
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 其余 tab 的延迟挂载等待：先把首帧留给当前 tab，之后再预组合其它 tab */
private const val TAB_MOUNT_DELAY_MS = 120L

/** 拖选胶囊吸附弹簧：接近临界阻尼，吸附快且几乎不过冲 */
private val CAPSULE_SPRING = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)

// ---- 玻璃配方（Haze 1.7.1，NPatch 同代）------------------------------------
// HazeDefaults.blurRadius 就是 20.dp；NPatch 的 dex 里也写着 blurRadius 上限 25dp。
// 这一档能把底下的文字/卡片糊成色块，图标才不会和背景打架。
private val GLASS_BLUR = 20.dp
// tint 压在模糊层之上：42% 时卡片和背景几乎同亮度（实测 #F4F3ED vs #F4F4F4），
// 提到 58% 并换成略灰的 surfaceContainer，卡片才像一块「浮起来的实色块」。
private const val GLASS_TINT_ALPHA = 0.58f
// 极轻颗粒，给一点材质感；多了会显脏。
private const val GLASS_NOISE = 0.05f
// 平台不支持真模糊时 Haze 画的兜底实底，避免变成一层透明塑料片。
private const val GLASS_FALLBACK_ALPHA = 0.92f

// ---- 悬浮底栏（NPatch 那种墨色玻璃）----------------------------------------
/**
 * 深色模式走「墨色玻璃」：比内容沉一档、靠一圈细亮边勾轮廓，选中项是一颗发亮的玻璃珠；
 * 浅色模式自动回到跟随主题的浅色玻璃，图标 / 指示块颜色一并跟着切。
 */
/** 采样自 NPatch 药丸玻璃 #232830 */
private val BAR_DARK_TINT = Color(0xFF232830)
// 底栏的 tint 浓度：太透时背后内容的糊色会顶上来，图标和文字都发灰，
// 0.86 这一档既保住了玻璃的层次，又保证三格文字清晰。
private const val BAR_DARK_TINT_ALPHA = 0.86f

/** 桌面长按「添加小部件」快捷方式带进来的标记（对应 res/xml/shortcuts.xml 里的 extra） */
private const val EXTRA_ADD_WIDGET = "com.anon.schedule.extra.ADD_WIDGET"

/** 全屏二级页面（栈式导航，可多层叠加） */
sealed class SubPage {
    data object Settings : SubPage()
    data object CourseList : SubPage()
    data object TimeTable : SubPage()
    data object Quick : SubPage()
    data class EditCourse(val courseId: Long) : SubPage()
    data object ImportZf : SubPage()
    data object ImportCsv : SubPage()
    data object Grades : SubPage()
    data object UBang : SubPage()
    data object Chaoxing : SubPage()
    data object UpdateLog : SubPage()
    data object AppIcon : SubPage()
    data object TableManage : SubPage()
    data class TableSettings(val tableId: Long) : SubPage()
    data class TableData(val tableId: Long) : SubPage()
    data class TableStyle(val tableId: Long) : SubPage()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 同步读完本地数据：首帧就能用上持久化的主题，避免"先浅色默认主题、再切深色"的闪一下
        Repo.init(applicationContext)
        // 把「软件图标」持久化的选中项同步到系统组件状态（状态一致时不做任何事）
        AppIcons.sync(applicationContext, Repo.state.value.settings.appIcon)
        // 首帧就用持久化的不透明度，不等组合后的 LaunchedEffect
        CardAlphaPreview.floatValue = Repo.state.value.tableSettings().cardAlphaPercent / 100f
        // 课程/设置变动后重排上课提醒（进程被杀或错过开机广播时也靠这里补）
        ClassReminder.scheduleAll(applicationContext)
        // 清单里窗口/闪屏底色默认写成了深色（防白屏）；浅色用户在这里改回浅色，让启动窗口和主题一致
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val dark = when (Repo.state.value.settings.themeMode) {
            1 -> false
            2 -> true
            else -> sysDark
        }
        window.setBackgroundDrawable(
            ColorDrawable(
                paletteBackgroundColor(Repo.state.value.settings.themePalette, dark).toArgb()
            )
        )
        // 从桌面快捷方式进来：进课表页后直接弹出「添加小部件」
        val fromWidgetShortcut = intent?.getStringExtra(EXTRA_ADD_WIDGET) == "1"
        setContent {
            val st by Repo.state.collectAsState()
            val hasBackground = st.tableSettings().backgroundPath != null
            // 切课表 / 导入备份时把持久化值同步回预览态；拖动过程中此值不变，不会覆盖实时预览
            val tableAlpha = st.tableSettings().cardAlphaPercent / 100f
            LaunchedEffect(tableAlpha) { CardAlphaPreview.floatValue = tableAlpha }
            // 两个采样源分工：
            //  · contentHaze   = 整屏内容（壁纸 + 页面），给悬浮底栏，内容从玻璃下滚过
            //  · wallpaperHaze = 只有壁纸，给卡片，避免把别的卡片也模糊进去
            val contentHaze = rememberHazeState()
            val wallpaperHaze = rememberHazeState()
            AppTheme(
                themeMode = st.settings.themeMode,
                paletteId = st.settings.themePalette,
                liquidGlass = st.settings.liquidGlass
            ) {
                val surface = MaterialTheme.colorScheme.surfaceContainer
                // 卡片垫色用比 surface 略深一档的 surfaceContainer：
                // NPatch 的卡片是实色 #16181E、背景 #0E0D11、玻璃 #232830，逐级提亮才有秩序。
                // 我们反向做「逐级压暗」，卡片才不会和背景糊成一片白。
                val cardTint = MaterialTheme.colorScheme.surfaceContainer
                val glassStyle = remember(surface, cardTint) {
                    HazeStyle(
                        backgroundColor = surface,
                        tints = listOf(HazeTint(cardTint.copy(alpha = GLASS_TINT_ALPHA))),
                        blurRadius = GLASS_BLUR,
                        noiseFactor = GLASS_NOISE,
                        fallbackTint = HazeTint(cardTint.copy(alpha = GLASS_FALLBACK_ALPHA)),
                    )
                }
                CompositionLocalProvider(
                    LocalHasBackground provides hasBackground,
                    LocalContentHaze provides if (st.settings.liquidGlass) contentHaze else null,
                    // 卡片只做普通磨砂：有背景图就提供采样源，不再受液态玻璃开关影响
                    LocalWallpaperHaze provides if (hasBackground) wallpaperHaze else null,
                    LocalHazeStyle provides glassStyle
                ) {
                    // 单独一层：拖动时只重组这一层 + 卡片，不牵动 AppTheme / AppRoot 整棵树
                    CardAlphaProvider {
                        AppRoot(fromWidgetShortcut)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Repo.flush()
        // 数据落盘后顺手把桌面小组件刷新一遍，保证退出 App 后小部件是最新的
        Widgets.refreshAll(applicationContext)
        // 课表可能刚被改过：按最新数据重排上课提醒
        ClassReminder.scheduleAll(applicationContext)
    }
}

@Composable
fun AppRoot(addWidgetShortcut: Boolean = false) {
    val st by Repo.state.collectAsState()
    val contentHaze = LocalContentHaze.current
    val wallpaperHaze = LocalWallpaperHaze.current
    // 快捷方式请求：交给课表页弹一次「添加小部件」，弹完就清掉
    var widgetDialogRequest by remember { mutableStateOf(addWidgetShortcut) }

    // 背景图（按当前课表）
    val bgPath = st.tableSettings().backgroundPath
    val background by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, bgPath) {
        // 解码放后台线程：produceState 默认跑在主线程，整屏大图解码会卡住首帧那几帧
        value = bgPath?.let { path ->
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }.getOrNull()
            }
            bmp?.asImageBitmap()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(1) }
    val subStack = remember { mutableStateListOf<SubPage>() }
    fun push(p: SubPage) { subStack.add(p) }
    fun pop() { if (subStack.isNotEmpty()) subStack.removeAt(subStack.lastIndex) }
    val sub = subStack.lastOrNull()
    val navVisible = subStack.isEmpty()

    BackHandler(enabled = subStack.isNotEmpty()) { pop() }

    Box(Modifier.fillMaxSize()) {
        // 药丸尺寸只用来算三等分格宽；位置不再需要（手写快照链路已删）。
        var pillSize by remember { mutableStateOf(IntSize.Zero) }

        // 主内容 = 悬浮底栏的采样源。Haze 自己按平台选 RenderEffect / RenderScript /
        // 兜底 scrim，并在滚动时逐帧同步；不再有 30fps 快照节流与模拟器频闪 workaround。
        Box(
            Modifier
                .fillMaxSize()
                .then(if (contentHaze != null) Modifier.hazeSource(state = contentHaze) else Modifier)
        ) {
            // 主题渐变中 surface 亮度是连续变化的：按亮度平滑过渡遮罩色，避免中途由白突然转黑
            val wallpaperScrim = lerp(
                Color.White.copy(alpha = 0.40f),
                MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                ((0.5f - MaterialTheme.colorScheme.surface.luminance()) / 0.35f).coerceIn(0f, 1f)
            )
            // 二级页面（设置等）自带不透明底色：壁纸不绘制、也不进采样层，避免顶部状态栏漏出背景图
            val showWallpaper = sub == null
            // 卡片专用采样源：只包壁纸 + 遮罩。与底栏分开，卡片才不会把别的卡片模糊进去。
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (wallpaperHaze != null) Modifier.hazeSource(state = wallpaperHaze) else Modifier)
            ) {
                if (showWallpaper) {
                    background?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.9f
                        )
                    }
                    // 背景图遮罩：浅色模式加白、深色模式压暗，保证图上文字始终有对比度
                    if (background != null) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(wallpaperScrim)
                        )
                    }
                }
            }

            Scaffold(
                // 遮罩已画在玻璃采样层里，这里让出底色，避免把折射层再盖灰
                containerColor = if (background != null && showWallpaper) Color.Transparent
                else MaterialTheme.colorScheme.background,
                // 底色是 Transparent 时 contentColorFor 查不到对应色、会返回 Unspecified，
                // 没显式指定颜色的文字（如「我的」页标题）就会回落成黑色，深色模式压在背景图上几乎看不见
                contentColor = MaterialTheme.colorScheme.onBackground
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 三个 tab 常驻组合：切 tab / 进出二级页面只做显隐，不再整表重组
                    TabSlot(sub == null && tab == 0) { TodayScreen() }
                    TabSlot(sub == null && tab == 1) {
                        ScheduleScreen(
                            onImportZf = { push(SubPage.ImportZf) },
                            onImportCsv = { push(SubPage.ImportCsv) },
                            onOpenSub = { push(it) },
                            showWidgetDialog = widgetDialogRequest,
                            onWidgetDialogShown = { widgetDialogRequest = false }
                        )
                    }
                    TabSlot(sub == null && tab == 2) {
                        MineScreen(
                            onSettings = { push(SubPage.Settings) },
                            onGrades = { push(SubPage.Grades) },
                            onUBang = { push(SubPage.UBang) },
                            onChaoxing = { push(SubPage.Chaoxing) },
                            onUpdateLog = { push(SubPage.UpdateLog) }
                        )
                    }
                    // 二级页面浮在 tab 之上
                    if (sub != null) when (val s: SubPage = sub) {
                        SubPage.Settings -> SettingsScreen(
                            onBack = { pop() },
                            onOpenSub = { push(it) }
                        )
                        SubPage.CourseList -> CourseListScreen(
                            onBack = { pop() },
                            onEdit = { push(SubPage.EditCourse(it)) }
                        )
                        SubPage.TimeTable -> TimeTableScreen(onBack = { pop() })
                        SubPage.Quick -> QuickActionsScreen(onBack = { pop() })
                        is SubPage.EditCourse -> EditCourseScreen(
                            courseId = s.courseId,
                            onBack = { pop() }
                        )
                        SubPage.ImportZf -> ImportZfScreen(onBack = { pop() })
                        SubPage.ImportCsv -> ImportCsvScreen(onBack = { pop() })
                        SubPage.Grades -> GradesScreen(onBack = { pop() })
                        SubPage.UBang -> UBangScreen(onBack = { pop() })
                        SubPage.Chaoxing -> ChaoxingScreen(onBack = { pop() })
                        SubPage.UpdateLog -> UpdateLogScreen(onBack = { pop() })
                        SubPage.AppIcon -> AppIconScreen(onBack = { pop() })
                        SubPage.TableManage -> TableManageScreen(
                            onBack = { pop() },
                            onEdit = { push(SubPage.TableSettings(it)) },
                            onOpenCourses = { tableId ->
                                Repo.update { it.copy(currentTableId = tableId) }
                                push(SubPage.CourseList)
                            }
                        )
                        is SubPage.TableSettings -> TableSettingsScreen(
                            tableId = s.tableId,
                            onBack = { pop() },
                            onTableData = { push(SubPage.TableData(it)) },
                            onTableStyle = { push(SubPage.TableStyle(it)) }
                        )
                        is SubPage.TableData -> TableDataScreen(
                            tableId = s.tableId,
                            onBack = { pop() },
                            onOpenSub = { push(it) }
                        )
                        is SubPage.TableStyle -> TableStyleScreen(
                            tableId = s.tableId,
                            onBack = { pop() }
                        )
                    }
                }
            }
        }

        // 悬浮药丸底栏：玻璃层交给 Haze，动效只保留跟手胶囊 + 弹簧吸附
        if (navVisible) {
            GlassPillNav(
                selected = tab,
                onSelect = { tab = it },
                hazeState = contentHaze,
                darkBar = isDarkMode(st.settings.themeMode),
                pillSize = pillSize,
                onPillLayout = { s -> pillSize = s },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** tab 容器：非选中时移出屏幕（屏蔽触摸）且不绘制，但保持组合——切 tab 零重组 */
@Composable
private fun TabSlot(selected: Boolean, content: @Composable () -> Unit) {
    // 挂载门：当前 tab 立即组合撑起首帧；其余 tab 稍后延迟组合，避免冷启动首帧同时组三份界面
    var mounted by remember { mutableStateOf(selected) }
    if (!mounted) {
        LaunchedEffect(Unit) {
            delay(TAB_MOUNT_DELAY_MS)
            mounted = true
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, if (selected) 0 else 100_000) }
            .drawWithContent { if (selected) drawContent() }
    ) {
        if (mounted) content()
    }
}

/** 悬浮玻璃药丸底栏：Haze 真模糊 + 跟手胶囊 + 弹簧吸附 */
@Composable
private fun GlassPillNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState?,
    darkBar: Boolean,
    pillSize: IntSize,
    onPillLayout: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    // 标准胶囊（半圆端）：SuperellipseShape 的角部在白描边下会露出两条 45° 斜线，
    // 看起来像划痕；NPatch 的药丸就是标准 stadium，换全圆角后斜纹自然消失。
    val shape = RoundedCornerShape(percent = 50)
    val glass = hazeState != null
    // 深色模式单独压一层墨色玻璃；浅色模式走 Unspecified，回归 LocalHazeStyle 的浅色配方
    val surface = MaterialTheme.colorScheme.surface
    val barStyle = remember(surface, darkBar) {
        if (!darkBar) HazeStyle.Unspecified else HazeStyle(
            backgroundColor = surface,
            tints = listOf(HazeTint(BAR_DARK_TINT.copy(alpha = BAR_DARK_TINT_ALPHA))),
            blurRadius = GLASS_BLUR,
            noiseFactor = GLASS_NOISE,
            fallbackTint = HazeTint(BAR_DARK_TINT.copy(alpha = 0.97f)),
        )
    }
    // 拖选小数下标 0..2：拖动期胶囊连续跟手，松手/外部切页弹簧吸附到整数位。
    // dragPos 是「手指真值」，普通状态写入、当帧就生效；slidePos 只负责非拖动期的动画。
    val slidePos = remember { Animatable(selected.toFloat()) }
    val dragPos = remember { mutableFloatStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // 当前手指压住的格子：只用来做「果冻按压」，抬手立刻复位
    var touchIndex by remember { mutableStateOf<Int?>(null) }
    // 药丸自身的像素尺寸：AGSL 折射着色器要用它算圆角与中心
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    // pointerInput 的接收者不是 CoroutineScope，动画得发到组合级协程里
    val uiScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 非拖动态时胶囊跟随切页（含返回键等外部改动）弹簧吸附到整数位
    LaunchedEffect(selected) {
        if (!dragging) slidePos.animateTo(selected.toFloat(), CAPSULE_SPRING)
    }

    // 格子几何：Row 内边距 6dp、格间距 4dp、三格等宽
    val density = LocalDensity.current
    val padPx = with(density) { 6.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val itemW = if (pillSize.width > 0) (pillSize.width - 2 * padPx - 2 * gapPx) / 3f else 0f
    val stepPx = itemW + gapPx
    // 液态玻璃折射参数（与 NPatch 底栏同一套 AGSL）：
    // 圆角取半高；折射带只留窄窄一圈（7dp），最大位移也压到 5dp，
    // 否则位移会顶到栏高的一半，整条胶囊会被"掰弯"成夸张的透镜。
    val glassParams = remember(barSize, darkBar, density) {
        GlassParams(
            cornerRadiusPx = barSize.height / 2f,
            refractionHeightPx = with(density) { 7.dp.toPx() },
            refractionAmount = -with(density) { 5.dp.toPx() },
            depthEffect = 1.2f,
            highlightColor = Color.White.copy(alpha = if (darkBar) 0.45f else 0.35f)
        )
    }
    // API 33+ 才会有；低版本返回 null，自动退回纯 Haze 模糊
    val refraction = rememberGlassRefraction(barSize, glassParams)
    fun indexOf(x: Float) =
        if (stepPx > 0f) ((x - padPx - itemW / 2f) / stepPx).roundToInt().coerceIn(0, 2) else 0
    fun fracOf(x: Float) =
        if (stepPx > 0f) ((x - padPx - itemW / 2f) / stepPx).coerceIn(0f, 2f) else 0f
    // 只在「跨格」时重组：弹簧每帧变化不该带着整棵药丸重组
    val previewIndex by remember {
        androidx.compose.runtime.derivedStateOf {
            (if (dragging) dragPos.floatValue else slidePos.value).roundToInt().coerceIn(0, 2)
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned {
                barSize = it.size
                onPillLayout(it.size)
            }
            // 点/拖统一手势：横向位移超 touchSlop 且大于纵向 → 进入拖选。
            // 拖选中消费事件，子项不再各自 clickable，避免抬手二次响应。
            .pointerInput(stepPx) {
                val slop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 按下先把「手指真值」对齐到当前弹簧位置，之后拖动才从这儿连续出发
                        dragPos.floatValue = slidePos.value
                        dragging = false
                        var preview = indexOf(down.position.x)
                        touchIndex = preview
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            val p = ch.position
                            val dxTotal = p.x - down.position.x
                            if (!dragging && abs(dxTotal) > slop &&
                                abs(dxTotal) > abs(p.y - down.position.y)
                            ) {
                                dragging = true
                            }
                            if (dragging) {
                                // 直接写状态：当帧就能画出来，胶囊和手指之间没有协程调度延迟
                                dragPos.floatValue = fracOf(p.x)
                                val pi = dragPos.floatValue.roundToInt().coerceIn(0, 2)
                                if (pi != preview) {
                                    preview = pi
                                    touchIndex = pi
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                ch.consume()
                            }
                        }
                        val wasDragging = dragging
                        touchIndex = null
                        val target = if (wasDragging) {
                            dragPos.floatValue.roundToInt().coerceIn(0, 2)
                        } else {
                            indexOf(down.position.x)
                        }
                        onSelect(target)
                        // 顺序很关键：先 snapTo 把弹簧搬到手指离开处，再摘掉 dragging，
                        // 中间任何一帧渲染的都是同一个位置，不会闪回上一格
                        uiScope.launch {
                            if (wasDragging) slidePos.snapTo(dragPos.floatValue)
                            dragging = false
                            slidePos.animateTo(target.toFloat(), CAPSULE_SPRING)
                        }
                    }
                }
            }
            .shadow(if (darkBar) 3.dp else 8.dp, shape, clip = false)
            .clip(shape)
            // 先把「折射」套在外层，再让 Haze 在里面画模糊背景：
            // RenderEffect 的 content 就是这一层的最终内容（模糊后的背景 + 色垫），
            // 边缘一圈会被 AGSL 弯掉，和 NPatch 底栏的做法一致。
            .then(
                if (glass && refraction != null) Modifier.graphicsLayer {
                    renderEffect = refraction
                } else Modifier
            )
            .then(
                if (glass) Modifier.hazeEffect(state = hazeState, style = barStyle)
                else Modifier.background(
                    if (darkBar) BAR_DARK_TINT else MaterialTheme.colorScheme.surface,
                    shape
                )
            )
    ) {
        // 拖选高亮胶囊：位置在绘制期现读，只触发重绘不触发重组
        if (itemW > 0f) {
            val primary = MaterialTheme.colorScheme.primary
            // 墨色玻璃上是「发亮的白色玻璃珠」；浅色玻璃上仍是主色渐变
            val capsuleBrush = if (darkBar) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.11f))
            ) else Brush.verticalGradient(
                listOf(primary.copy(alpha = 0.28f), primary.copy(alpha = 0.14f))
            )
            val capsuleStroke = if (darkBar) Color.White.copy(alpha = 0.46f)
                else primary.copy(alpha = 0.20f)
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val pos = if (dragging) dragPos.floatValue else slidePos.value
                        val x = padPx + pos * stepPx
                        val capsuleSize = Size(itemW, size.height - 2 * padPx)
                        drawRoundRect(
                            brush = capsuleBrush,
                            topLeft = Offset(x, padPx),
                            size = capsuleSize,
                            cornerRadius = CornerRadius(capsuleSize.height / 2f)
                        )
                        drawRoundRect(
                            color = capsuleStroke,
                            topLeft = Offset(x, padPx),
                            size = capsuleSize,
                            cornerRadius = CornerRadius(capsuleSize.height / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
            )
        }
        // 液态玻璃收尾：上亮下暗的轮廓边光、左右色散、顶部镜面、底部内阴影
        Box(
            Modifier
                .matchParentSize()
                .liquidGlassFinish(shape = shape, dark = darkBar)
        )
        // 内容（撑起药丸尺寸，其余层 matchParentSize 跟随）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillItem(Icons.Default.Today, "今日课表", previewIndex == 0, darkBar, touchIndex == 0) { onSelect(0) }
            PillItem(Icons.Default.DateRange, "课表", previewIndex == 1, darkBar, touchIndex == 1) { onSelect(1) }
            PillItem(Icons.Default.Person, "我的", previewIndex == 2, darkBar, touchIndex == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun RowScope.PillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    darkBar: Boolean = false,
    pressed: Boolean = false,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    // 墨色玻璃上未选中也要够亮，选中项用当前配色的点缀色拉开一档
    val barAccent = LocalPaletteDarkBarAccent.current
    val tint = when {
        darkBar && selected -> barAccent
        darkBar -> Color.White.copy(alpha = 0.78f)
        selected -> primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 果冻按压：横向微鼓、纵向压扁；抬手弹回。光晕随按压透出来（体积光的简化版）
    val jelly by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "pillJelly"
    )
    Row(
        modifier = Modifier
            .weight(1f)
            // 点按/拖选由药丸统一手势处理；这里只保留无障碍语义入口
            .semantics { onClick(label = null) { onClick(); true } }
            .drawBehind {
                if (jelly > 0.01f) {
                    val r = size.minDimension * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.20f * jelly),
                                Color.Transparent
                            ),
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center
                    )
                }
            }
            .graphicsLayer {
                scaleX = 1f + 0.04f * jelly
                scaleY = 1f - 0.08f * jelly
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
