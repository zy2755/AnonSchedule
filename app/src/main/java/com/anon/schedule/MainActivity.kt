package com.anon.schedule

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.notify.ClassReminder
import com.anon.schedule.ui.course.EditCourseScreen
import com.anon.schedule.ui.importer.ImportCsvScreen
import com.anon.schedule.ui.importer.ImportZfScreen
import com.anon.schedule.ui.mine.ChaoxingScreen
import com.anon.schedule.ui.mine.GradesScreen
import com.anon.schedule.ui.mine.MineScreen
import com.anon.schedule.ui.mine.UBangScreen
import com.anon.schedule.ui.mine.UpdateLogScreen
import com.anon.schedule.ui.schedule.ScheduleScreen
import com.anon.schedule.ui.settings.CourseListScreen
import com.anon.schedule.ui.settings.QuickActionsScreen
import com.anon.schedule.ui.settings.SettingsScreen
import com.anon.schedule.ui.settings.TimeTableScreen
import com.anon.schedule.ui.table.TableDataScreen
import com.anon.schedule.ui.table.TableManageScreen
import com.anon.schedule.ui.table.TableSettingsScreen
import com.anon.schedule.ui.table.TableStyleScreen
import com.anon.schedule.ui.theme.AppTheme
import com.anon.schedule.ui.theme.GlassParams
import com.anon.schedule.ui.theme.LocalBackdrop
import com.anon.schedule.ui.theme.LocalHasBackground
import com.anon.schedule.ui.theme.SuperellipseShape
import com.anon.schedule.ui.theme.glassGlow
import com.anon.schedule.ui.theme.glassSpecular
import com.anon.schedule.ui.theme.rememberGlassMotion
import com.anon.schedule.ui.theme.rememberGlassRefraction
import com.anon.schedule.ui.today.TodayScreen
import com.anon.schedule.widget.Widgets
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 玻璃快照节流：连续帧最多每 SNAP_MIN_NS 录一次（≈30fps，滚动时也保持实时折射） */
private const val SNAP_MIN_NS = 33_000_000L

/** 快照只录药丸附近这条横带，上下各留这么多余量（折射采样出界 + 模糊半径） */
private val SNAP_BAND_MARGIN = 48.dp

/** 其余 tab 的延迟挂载等待：先把首帧留给当前 tab，之后再预组合其它 tab */
private const val TAB_MOUNT_DELAY_MS = 120L

/**
 * 磨砂降采样倍率：把背景横带按 1/N 录制再线性放大回原尺寸，得到与设备/GPU 无关的磨砂底纹。
 * 不用 RenderEffect.createBlurEffect（部分模拟器 GPU 通道上静默失效），N 越大越糊越省。
 */
private const val FROST_SCALE = 6f

/** 拖选胶囊吸附弹簧：接近临界阻尼，吸附快且几乎不过冲 */
private val CAPSULE_SPRING = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)

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
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF1A1216.toInt() else 0xFFFDF7FA.toInt()))
        // 从桌面快捷方式进来：进课表页后直接弹出「添加小部件」
        val fromWidgetShortcut = intent?.getStringExtra(EXTRA_ADD_WIDGET) == "1"
        setContent {
            val st by Repo.state.collectAsState()
            val hasBackground = st.tableSettings().backgroundPath != null
            // 卡片玻璃的采样层：只录「背景图 + 遮罩」，卡片据此做模糊/折射
            val glassBackdrop = rememberLayerBackdrop()
            AppTheme(themeMode = st.settings.themeMode, liquidGlass = st.settings.liquidGlass) {
                // 有背景图时卡片需要更实的底色托住文字（夜间尤其），用 CompositionLocal 下传
                CompositionLocalProvider(
                    LocalHasBackground provides hasBackground,
                    LocalBackdrop provides if (st.settings.liquidGlass) glassBackdrop else null
                ) {
                    AppRoot(glassBackdrop, fromWidgetShortcut)
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
fun AppRoot(backdrop: LayerBackdrop, addWidgetShortcut: Boolean = false) {
    val st by Repo.state.collectAsState()
    val liquidGlass = st.settings.liquidGlass
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
        // 玻璃底栏的磨砂快照层：按 1/FROST_SCALE 录制药丸附近横带，主内容直接画保持清晰
        val frostLayer = rememberGraphicsLayer()
        // 说明：磨砂只靠「降采样再放大」，不叠 RenderEffect 模糊——个别 GPU 通道（MuMu 等 API 31-32
        // 模拟器）上模糊既不生效，又会在重录快照时引起整窗重合成的亮度频闪，得不偿失。
        // 药丸位置/尺寸（GlassPillNav 布局后上报），用于只录药丸附近的快照带
        var pillPos by remember { mutableStateOf(Offset.Zero) }
        var pillSize by remember { mutableStateOf(IntSize.Zero) }
        // 节流：连续帧最多每 SNAP_MIN_NS 录一次快照，停顿后的第一帧距上次必超间隔、必录
        val lastSnapNs = remember { LongArray(1) }
        // 个别 GPU 通道（实测 MuMu 等 API<33 模拟器）在「录快照的那一帧」会把内容往屏幕上多画一遍，
        // 节流造成的 录/不录 交替 = 整窗亮度方波 = 切换时肉眼可见的频闪；这些通道改为每帧都录消除交替。
        val snapMinNs = if (Build.VERSION.SDK_INT >= 33) SNAP_MIN_NS else 0L

        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // 滚动中也保持录制：玻璃背后的内容要实时弯折，冻结会显得「玻璃是死的」
                    if (liquidGlass && navVisible && pillSize.width > 0) {
                        val now = System.nanoTime()
                        if (now - lastSnapNs[0] >= snapMinNs) {
                            lastSnapNs[0] = now
                            // 只录药丸附近一条横带（上下各留 SNAP_BAND_MARGIN），成本远低于全屏
                            val margin = SNAP_BAND_MARGIN.toPx()
                            val bandTop = (pillPos.y - margin).coerceAtLeast(0f)
                            val bandBottom = (pillPos.y + pillSize.height + margin)
                                .coerceAtMost(size.height)
                            val bandH = (bandBottom - bandTop).coerceAtLeast(1f)
                            // 降采样录制：小层放大后即磨砂，不依赖 RenderEffect
                            frostLayer.record(
                                this,
                                layoutDirection,
                                IntSize(
                                    (size.width / FROST_SCALE).roundToInt().coerceAtLeast(1),
                                    (bandH / FROST_SCALE).roundToInt().coerceAtLeast(1)
                                )
                            ) {
                                scale(1f / FROST_SCALE) {
                                    translate(0f, -bandTop) { this@drawWithContent.drawContent() }
                                }
                            }
                        }
                    }
                    drawContent()
                }
        ) {
            // 主题渐变中 surface 亮度是连续变化的：按亮度平滑过渡遮罩色，避免中途由白突然转黑
            val wallpaperScrim = lerp(
                Color.White.copy(alpha = 0.40f),
                MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                ((0.5f - MaterialTheme.colorScheme.surface.luminance()) / 0.35f).coerceIn(0f, 1f)
            )
            // 二级页面（设置等）自带不透明底色：壁纸不绘制、也不进采样层，避免顶部状态栏漏出背景图
            val showWallpaper = sub == null
            // 玻璃采样层：壁纸 + 遮罩单独录一层，卡片从这里取像素做模糊/折射。
            // 只录背景、不录卡片自身，否则卡片会把上一帧的自己折射进去（重影）
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (liquidGlass) Modifier.layerBackdrop(backdrop) else Modifier)
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
                else MaterialTheme.colorScheme.background
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

        // 悬浮药丸底栏（拾光样式 + npatch 液态玻璃）
        if (navVisible) {
            GlassPillNav(
                selected = tab,
                onSelect = { tab = it },
                glassLayer = if (liquidGlass) frostLayer else null,
                pillPos = pillPos,
                pillSize = pillSize,
                onPillLayout = { p, s -> pillPos = p; pillSize = s },
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

/** 悬浮玻璃药丸底栏 */
@Composable
private fun GlassPillNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    glassLayer: GraphicsLayer?,
    pillPos: Offset,
    pillSize: IntSize,
    onPillLayout: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = SuperellipseShape(28.dp)
    val surface = MaterialTheme.colorScheme.surface
    val glass = glassLayer != null
    val motion = rememberGlassMotion()
    // 拖选小数下标 0..2：拖动态胶囊连续跟手，松手/外部切页弹簧吸附到整数位（分段控件手感）
    // 两套位置分工：dragPos 是拖动期的「手指真值」，普通状态写入、当帧就生效（跟手零延迟）；
    // slidePos 是弹簧，负责非拖动期的吸附动画。渲染只认一个 capsulePos，抬手时先把弹簧
    // 对齐到 dragPos 再切回去，中间不会出现「跳回上一格」的闪帧
    val slidePos = remember { Animatable(selected.toFloat()) }
    val dragPos = remember { mutableFloatStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // pointerInput 的接收者 PointerInputScope 不是 CoroutineScope，动画得发到组合级协程里
    val uiScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // 有 AGSL 折射（API 33+）时玻璃要透，让折射看清背景；无折射的低版本垫厚一点，
    // 观感从「清玻璃」退化为「磨砂玻璃」（与参考实现 Haze 的降级思路一致）
    val scrimColors = if (glass) {
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.16f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.28f)
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.30f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.40f)
            )
        }
    } else {
        listOf(surface.copy(alpha = 0.97f), surface.copy(alpha = 1f))
    }

    // 换页 → 折射/圆角脉冲 + 朝滑动方向晃一下再回弹（「液滴被拨动」）
    var prevSelected by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        val dir = (selected - prevSelected).toFloat()
        prevSelected = selected
        motion.pulse(amount = 12f, dirX = dir * 16f)
    }
    // 非拖动态时胶囊跟随切页（含返回键等外部改动）弹簧吸附到整数位
    LaunchedEffect(selected) {
        if (!dragging) slidePos.animateTo(selected.toFloat(), CAPSULE_SPRING)
    }

    // 格子几何：Row 内边距 6dp、格间距 4dp、三格等宽；胶囊中心 = pad + s*step + itemW/2
    val density = LocalDensity.current
    val padPx = with(density) { 6.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val itemW = if (pillSize.width > 0) (pillSize.width - 2 * padPx - 2 * gapPx) / 3f else 0f
    val stepPx = itemW + gapPx
    fun indexOf(x: Float) =
        if (stepPx > 0f) ((x - padPx - itemW / 2f) / stepPx).roundToInt().coerceIn(0, 2) else 0
    fun fracOf(x: Float) =
        if (stepPx > 0f) ((x - padPx - itemW / 2f) / stepPx).coerceIn(0f, 2f) else 0f
    // 唯一的渲染位置：拖动期跟手，其余时间听弹簧
    val capsulePos = if (dragging) dragPos.floatValue else slidePos.value
    val previewIndex = capsulePos.roundToInt().coerceIn(0, 2)

    // 折射带 24dp（并按药丸高度封顶 18%），位移同量取负，depthEffect 关；
    // 带寬/圆角/偏移叠加弹簧增量，静止时增量归零 → 空闲不产生重组
    val params = with(LocalDensity.current) {
        val refr = 24.dp.toPx()
        val band = if (pillSize.height > 0) refr.coerceAtMost(pillSize.height * 0.18f) else refr
        GlassParams(
            cornerRadiusPx = 28.dp.toPx() + motion.cornerBoost.value,
            refractionHeightPx = band + motion.refractBoost.value,
            refractionAmount = -refr,
            depthEffect = 0f,
            offsetX = motion.sloshX.value,
            offsetY = motion.sloshY.value,
        )
    }
    val refraction = rememberGlassRefraction(pillSize, params)
    val refractLayer = rememberGraphicsLayer()
    // 折射层的输入就是快照：快照没重录时折射结果不变，跟着快照同节流，省掉每帧整层重录
    val lastRefractNs = remember { LongArray(1) }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned {
                onPillLayout(it.positionInWindow(), it.size)
            }
            // 点/拖统一手势：横向位移超 touchSlop 且大于纵向 → 进入拖选（胶囊跟手 + 透镜晃 + 跨格震动），
            // 否则视为轻点选按下那一格；拖选中消费事件，子项不再各自 clickable，避免抬手二次响应
            .pointerInput(stepPx) {
                val slop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        motion.press(down.position.x, down.position.y)
                        // 按下先把「手指真值」对齐到当前弹簧位置，之后拖动才从这儿连续出发
                        dragPos.floatValue = slidePos.value
                        dragging = false
                        var lastX = down.position.x
                        var preview = indexOf(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            val p = ch.position
                            motion.dragTo(p.x, p.y)
                            val dxTotal = p.x - down.position.x
                            if (!dragging && abs(dxTotal) > slop &&
                                abs(dxTotal) > abs(p.y - down.position.y)
                            ) {
                                dragging = true
                            }
                            if (dragging) {
                                motion.sloshBy(p.x - lastX)
                                // 直接写状态：当帧就能画出来，胶囊和手指之间没有协程调度延迟
                                dragPos.floatValue = fracOf(p.x)
                                val pi = dragPos.floatValue.roundToInt().coerceIn(0, 2)
                                if (pi != preview) {
                                    preview = pi
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                ch.consume()
                            }
                            lastX = p.x
                        }
                        val wasDragging = dragging
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
                        motion.release()
                    }
                }
            }
            .shadow(14.dp, shape, clip = false)
            .clip(shape)
    ) {
        // 1. 垫底磨砂快照：折射采样出界处透出它，边缘平滑不露底
        // 快照按 1/FROST_SCALE 录制，这里放大回横带原尺寸 —— 放大即磨砂，任何 GPU/API 都成立
        // 快照只录了药丸附近横带，原点在窗口 y = bandTop，绘制时换算回药丸局部坐标
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val layer = glassLayer
                    if (layer != null) {
                        val bandTop = (pillPos.y - SNAP_BAND_MARGIN.toPx()).coerceAtLeast(0f)
                        scale(FROST_SCALE) {
                            translate(
                                -pillPos.x / FROST_SCALE,
                                (bandTop - pillPos.y) / FROST_SCALE
                            ) { drawLayer(layer) }
                        }
                    }
                }
        )
        // 2. AGSL 边缘折射层（API 33+）：把快照当 content 输入，真实背景折射
        if (glass && refraction != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val layer = glassLayer!!
                        val bandTop = (pillPos.y - SNAP_BAND_MARGIN.toPx()).coerceAtLeast(0f)
                        val now = System.nanoTime()
                        if (now - lastRefractNs[0] >= SNAP_MIN_NS) {
                            lastRefractNs[0] = now
                            refractLayer.renderEffect = refraction
                            refractLayer.record(
                                this,
                                layoutDirection,
                                IntSize(size.width.roundToInt(), size.height.roundToInt())
                            ) {
                                // 小快照放大回原尺寸后再喂给折射着色器：着色器坐标系不变
                                scale(FROST_SCALE) {
                                    translate(
                                        -pillPos.x / FROST_SCALE,
                                        (bandTop - pillPos.y) / FROST_SCALE
                                    ) { drawLayer(layer) }
                                }
                            }
                        }
                        drawLayer(refractLayer)
                    }
            )
        }
        // 3. scrim 色垫：压住杂色保证图标可读
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(scrimColors))
        )
        // 3.5 拖选高亮胶囊：中心跟随手指连续移动（可停在两格中间），松手弹簧吸附
        if (itemW > 0f) {
            val capsuleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            val capsuleCornerPx = with(density) { 22.dp.toPx() }
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        // 位置在绘制期现读，只触发重绘不触发重组：拖动时手指一动胶囊就到
                        val pos = if (dragging) dragPos.floatValue else slidePos.value
                        val x = padPx + pos * stepPx
                        drawRoundRect(
                            color = capsuleColor,
                            topLeft = Offset(x, padPx),
                            size = Size(itemW, size.height - 2 * padPx),
                            cornerRadius = CornerRadius(capsuleCornerPx)
                        )
                    }
            )
        }
        // 4. 跟随手指的径向高光（液滴受光点）
        // 高光/描边只在液态玻璃开启时画：关闭时药丸要是纯色实心底栏，
        // 否则夜间模式下的白色描边 + 光泽会被当成「玻璃没关掉」
        if (glass) {
            Box(
                Modifier
                    .matchParentSize()
                    .glassGlow(motion, params)
            )
            // 5. 方向高光 + 描边（高光层独立，避免被折射扭曲）
            Box(
                Modifier
                    .matchParentSize()
                    .glassSpecular(params)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.08f))
                        ),
                        shape = shape
                    )
            )
        }
        // 6. 内容（撑起药丸尺寸，其余层 matchParentSize 跟随）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillItem(Icons.Default.Today, "今日课表", previewIndex == 0) { onSelect(0) }
            PillItem(Icons.Default.DateRange, "课表", previewIndex == 1) { onSelect(1) }
            PillItem(Icons.Default.Person, "我的", previewIndex == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun RowScope.PillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .weight(1f)
            // 点按/拖选由药丸统一手势处理；这里只保留无障碍语义入口
            .semantics { onClick(label = null) { onClick(); true } }
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
