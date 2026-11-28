package com.anon.schedule.ui.schedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import com.anon.schedule.SubPage
import com.anon.schedule.data.AppState
import com.anon.schedule.data.ChangeRecord
import com.anon.schedule.data.CourseLogic
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.DetailBean
import com.anon.schedule.data.IcsExport
import com.anon.schedule.data.Repo
import com.anon.schedule.data.Settings
import com.anon.schedule.data.ShownCourse
import com.anon.schedule.data.TableBean
import com.anon.schedule.data.TimeSlot
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GradientSlider
import com.anon.schedule.ui.common.TileBlue
import com.anon.schedule.ui.common.TileCyan
import com.anon.schedule.ui.common.TileGreen
import com.anon.schedule.ui.common.TileOrange
import com.anon.schedule.ui.common.TilePink
import com.anon.schedule.ui.common.TilePurple
import com.anon.schedule.ui.common.courseColor
import com.anon.schedule.ui.theme.LocalGlass
import com.anon.schedule.widget.ExamCountdownWidget
import com.anon.schedule.widget.SemesterProgressWidget
import com.anon.schedule.widget.TodayLargeWidget
import com.anon.schedule.widget.TodaySmallWidget
import com.anon.schedule.widget.TodayTomorrowWidget
import com.anon.schedule.widget.TodayWideWidget
import com.anon.schedule.widget.TodoDdlWidget
import com.anon.schedule.widget.WeekTimetableWidget
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import kotlin.math.roundToInt

private val TimeColWidth = 52.dp
private val HeaderHeight = 40.dp

/** 每周起始日/周末显示设置下的星期顺序（1=周一 ... 7=周日） */
private fun dayOrder(sundayFirst: Boolean, showSaturday: Boolean, showSunday: Boolean): List<Int> {
    val days = buildList {
        addAll(1..5)
        if (showSaturday) add(6)
        if (showSunday) add(7)
    }
    return if (sundayFirst && showSunday) listOf(7) + days.filter { it != 7 } else days
}

/** 正片叠底（课程文字颜色叠加格子颜色） */
private fun multiplyBlend(fg: Color, bg: Color): Color =
    Color(fg.red * bg.red, fg.green * bg.green, fg.blue * bg.blue, fg.alpha)

/** 课程文字统一色：所有课程同一种字体颜色（WakeUp 风格） */
private val UniformCourseText = Color(0xFF222222)

/** 课程名统一字号下的可用行数：按格子高度算，尽量显示全 */
private fun courseNameMaxLines(nameSp: Float, heightDp: Float): Int {
    val lineHeight = nameSp + 1.5f
    return (heightDp / lineHeight).toInt().coerceAtLeast(1)
}

@Composable
fun ScheduleScreen(
    onImportZf: () -> Unit,
    onImportCsv: () -> Unit,
    onImportIcs: () -> Unit,
    onOpenSub: (SubPage) -> Unit,
    onScrollBusy: (Boolean) -> Unit = {},
    showWidgetDialog: Boolean = false,
    onWidgetDialogShown: () -> Unit = {}
) {
    val st by Repo.state.collectAsState()
    var detail by remember { mutableStateOf<ShownCourse?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ShownCourse?>(null) }
    var addingWidget by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 桌面长按图标 →「添加小部件」快捷方式进来：直接弹出选择框，不用先开菜单面板
    LaunchedEffect(showWidgetDialog) {
        if (showWidgetDialog) {
            addingWidget = true
            onWidgetDialogShown()
        }
    }
    // 三击空白格加课：记录 周/星期/起始节次
    var addAt by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    // 拖动落点已有课：先攒下这次移动，等用户确认「保留」再执行
    var pendingMove by remember { mutableStateOf<MoveConflict?>(null) }

    val s = st.tableSettings()
    val maxWeek = s.maxWeek.coerceAtLeast(1)
    val week = s.currentWeek.coerceIn(1, maxWeek)
    val pagerState = rememberPagerState(initialPage = week - 1) { maxWeek }
    // 顶栏周数跟着滑动实时走（翻过半页立即更新），落库仍等停稳，避免拖慢滑动
    val shownWeek by remember { derivedStateOf { pagerState.currentPage + 1 } }

    // 滑动中上报给外层：期间暂停玻璃底栏的快照/折射重录，省掉周期性整屏重绘的掉帧尖峰
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { onScrollBusy(it) }
    }

    // 滑动停稳 → 记录正在查看的周；等滑动彻底停下再落库，避免连续滑动时写状态引发整表重组掉帧
    LaunchedEffect(pagerState.settledPage, maxWeek) {
        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        val w = (pagerState.settledPage + 1).coerceIn(1, maxWeek)
        if (Repo.state.value.tableSettings().currentWeek != w) {
            Repo.updateSettings { it.copy(currentWeek = w) }
        }
    }
    // 更多面板改周 → 翻页跟随
    LaunchedEffect(week, maxWeek) {
        if (pagerState.settledPage + 1 != week && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(week - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- 顶部栏 ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box {
                IconButton(onClick = { showImportMenu = true }) {
                    Icon(Icons.Default.Download, contentDescription = "导入")
                }
                DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("教务导入") },
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = { showImportMenu = false; onImportZf() }
                    )
                    DropdownMenuItem(
                        text = { Text("CSV 导入") },
                        leadingIcon = { Icon(Icons.Default.ViewList, null) },
                        onClick = { showImportMenu = false; onImportCsv() }
                    )
                    DropdownMenuItem(
                        text = { Text("日历文件 (.ics) 导入") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        onClick = { showImportMenu = false; onImportIcs() }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        weekRangeText(st, shownWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 点今天日期 → 快速返回当前周（今天所在周）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        todayDateText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val todayWeek = Repo.weekOf(st, LocalDate.now()) ?: week
                                if (todayWeek != week) {
                                    Repo.updateSettings { it.copy(currentWeek = todayWeek) }
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    // 翻到了别的周：给一个显眼的「回本周」，不用去点小字日期
                    val realWeek = Repo.weekOf(st, LocalDate.now())
                    if (realWeek != null && realWeek != shownWeek) {
                        Text(
                            "回本周",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .clickable {
                                    Repo.updateSettings { it.copy(currentWeek = realWeek) }
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            (realWeek - 1).coerceIn(0, maxWeek - 1)
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, contentDescription = "搜索课程")
            }

            Box {
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                // 锚在三点按钮下方，点外面 / 返回键收起
                MoreMenu(
                    expanded = showMore,
                    onDismiss = { showMore = false },
                    onOpenSub = { showMore = false; onOpenSub(it) },
                    onAddWidget = { showMore = false; addingWidget = true }
                )
            }
        }

        // ---------- 课表网格（左右滑动切换周） ----------
        if (showSearch) {
            CourseSearchDialog(
                st = st,
                onDismiss = { showSearch = false },
                onPick = { shown, targetWeek ->
                    showSearch = false
                    detail = shown
                    // 跳到这门课开课的周次，同时把课程详情打开
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (targetWeek - 1).coerceIn(0, maxWeek - 1)
                        )
                    }
                }
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it }
        ) { page ->
            WeekPage(
                st = st,
                week = page + 1,
                onClick = { detail = it },
                onMoveCourse = { item, day, node ->
                    val w = page + 1
                    val d = item.detail
                    // 有开学日期时只是"这一周调过去"，冲突只看这一周；否则是长期改课表，按课程原周次范围判断
                    val weekly = Repo.dateOfWeekDay(st, w, d.day) != null
                    val names = CourseLogic.conflictsOf(
                        st = st, tableId = st.currentTableId, day = day, startNode = node, step = d.step,
                        startWeek = if (weekly) w else d.startWeek,
                        endWeek = if (weekly) w else d.endWeek,
                        type = if (weekly) 0 else d.type,
                        ignoreDetailId = d.id
                    ).mapNotNull { c -> st.courses.firstOrNull { it.id == c.courseId }?.name }.distinct()
                    if (names.isEmpty()) quickMoveCourse(st, w, item, day, node)
                    else pendingMove = MoveConflict(item, w, day, node, names)
                },
                onAddAt = { day, node -> addAt = Triple(page + 1, day, node) }
            )
        }
    }

    detail?.let { shown ->
        CourseDetailSheet(
            shown = shown,
            onDismiss = { detail = null },
            onEdit = { id ->
                detail = null
                onOpenSub(SubPage.EditCourse(id))
            },
            onCopy = { shown ->
                detail = null
                val stNow = Repo.state.value
                val oldCourse = shown.course
                val newCourse = oldCourse.copy(id = Repo.nextId(), name = oldCourse.name)
                val newDetails = stNow.details.filter { it.courseId == oldCourse.id }
                    .map { it.copy(id = Repo.nextId(), courseId = newCourse.id) }
                Repo.update { s -> s.copy(courses = s.courses + newCourse, details = s.details + newDetails) }
            },
            onDelete = { detail = null; deleteTarget = it }
        )
    }

    deleteTarget?.let { shown ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除课程") },
            text = { Text("确定删除「${shown.course.name}」吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = shown
                    deleteTarget = null
                    if (target.record != null) {
                        Repo.update { s -> s.copy(records = s.records.filterNot { it.id == target.record.id }) }
                    } else {
                        Repo.update { s ->
                            s.copy(
                                courses = s.courses.filterNot { it.id == target.course.id },
                                details = s.details.filterNot { it.courseId == target.course.id },
                                // 课程没了，挂在它上面的待办一起清掉，避免留下孤儿数据
                                todos = s.todos.filterNot { it.courseId == target.course.id }
                            )
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    pendingMove?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("时间冲突") },
            text = {
                Text(
                    "${dayLabel(m.day)}第 ${m.node} 节已经有「${m.names.joinToString("、")}」了，" +
                        "还要把「${m.item.course.name}」调过去吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMove = null
                    quickMoveCourse(Repo.state.value, m.week, m.item, m.day, m.node)
                }) { Text("调过去") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) { Text("取消") }
            }
        )
    }

    if (addingWidget) {
        AlertDialog(
            onDismissRequest = { addingWidget = false },
            title = { Text("添加小部件") },
            text = {
                Column {
                    Text(
                        "点一下想要的尺寸，确认后就会加到桌面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    WidgetPinRow("今日课程 · 2×2", TodaySmallWidget::class.java) { addingWidget = false }
                    WidgetPinRow("今日课程 · 4×4", TodayLargeWidget::class.java) { addingWidget = false }
                    WidgetPinRow("今日课程 · 4×2", TodayWideWidget::class.java) { addingWidget = false }
                    WidgetPinRow("今日 · 明日 · 4×4", TodayTomorrowWidget::class.java) { addingWidget = false }
                    WidgetPinRow("本周课表 · 4×4", WeekTimetableWidget::class.java) { addingWidget = false }
                    WidgetPinRow("待办 DDL · 4×2", TodoDdlWidget::class.java) { addingWidget = false }
                    WidgetPinRow("本学期进度 · 2×2", SemesterProgressWidget::class.java) { addingWidget = false }
                    WidgetPinRow("考试倒计时 · 2×2", ExamCountdownWidget::class.java) { addingWidget = false }
                }
            },
            confirmButton = {
                TextButton(onClick = { addingWidget = false }) { Text("关闭") }
            }
        )
    }

    // 三击空白格 → 临时加课弹窗（起始节次已由所点格子确定）
    addAt?.let { t ->
        BlankAddDialog(
            st = st,
            week = t.first,
            day = t.second,
            startNode = t.third,
            onDismiss = { addAt = null },
            onConfirm = { rec ->
                addAt = null
                Repo.update { s -> s.copy(records = s.records + rec) }
            }
        )
    }
}

/** 拖动落点已有课程：先让用户确认，再真正执行调课 */
private class MoveConflict(
    val item: ShownCourse,
    val week: Int,
    val day: Int,
    val node: Int,
    val names: List<String>
)

/** 长按拖动快速调课：有开学日期 → 记一条只影响该周的调课记录；否则直接调整课程的上课星期/节次 */
private fun quickMoveCourse(st: AppState, week: Int, item: ShownCourse, targetDay: Int, targetNode: Int) {
    val d = item.detail
    val rec = item.record
    if (rec != null && rec.type == 0) {
        // 调课卡再拖动：改写这条调课记录；拖回原位 = 撤销调课，恢复原状
        val newTo = Repo.dateOfWeekDay(st, week, targetDay) ?: return
        // 同名课可能有多个节次的 detail，落点与任意原始节次一致即视为拖回原位
        val backToOrig = st.details.any {
            it.courseId == item.course.id && it.day == targetDay && it.startNode == targetNode
        }
        if (backToOrig) {
            Repo.update { s -> s.copy(records = s.records.filterNot { it.id == rec.id }) }
            return
        }
        if (newTo.toEpochDay() == rec.toDate && d.startNode == targetNode) return
        Repo.update { s ->
            s.copy(records = s.records.map {
                if (it.id != rec.id) it
                // 整天调课（name 为空）只改目标日期；单门课调课同时更新目标节次
                else if (it.name.isEmpty()) it.copy(toDate = newTo.toEpochDay())
                else it.copy(toDate = newTo.toEpochDay(), startNode = targetNode, step = d.step)
            })
        }
        return
    }
    if (d.day == targetDay && d.startNode == targetNode) return
    val from = Repo.dateOfWeekDay(st, week, d.day)
    val to = Repo.dateOfWeekDay(st, week, targetDay)
    if (from != null && to != null) {
        Repo.update { s ->
            // 先清掉这门课在该日期附近的旧调课记录，避免重复叠加
            val cleaned = s.records.filterNot { r ->
                r.type == 0 && r.name == item.course.name &&
                        (r.fromDate == from.toEpochDay() || r.toDate == to.toEpochDay())
            }
            s.copy(
                records = cleaned + ChangeRecord(
                    id = Repo.nextId(), type = 0,
                    fromDate = from.toEpochDay(), toDate = to.toEpochDay(),
                    name = item.course.name, teacher = d.teacher, room = d.room,
                    startNode = targetNode, step = d.step
                )
            )
        }
    } else {
        Repo.update { s ->
            s.copy(details = s.details.map {
                if (it.id == d.id) it.copy(day = targetDay, startNode = targetNode) else it
            })
        }
    }
}

/** 1..7 → 周一..周日 */
private fun dayLabel(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day - 1) { "" }

/**
 * 课程搜索：按课程名 / 老师 / 教室匹配当前课表的安排，
 * 点结果跳到该课开课的周次并直接打开课程详情。
 */
@Composable
private fun CourseSearchDialog(
    st: AppState,
    onDismiss: () -> Unit,
    onPick: (ShownCourse, Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, st) {
        if (q.isEmpty()) emptyList()
        else st.details.filter { d ->
            if (d.tableId != st.currentTableId) return@filter false
            val c = st.courses.firstOrNull { it.id == d.courseId } ?: return@filter false
            c.name.contains(q, ignoreCase = true) ||
                d.teacher.contains(q, ignoreCase = true) ||
                d.room.contains(q, ignoreCase = true)
        }.mapNotNull { d ->
            st.courses.firstOrNull { it.id == d.courseId }?.let { c -> ShownCourse(d, c, false, null) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("搜索课程") },
                    placeholder = { Text("课程名 / 老师 / 教室") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                when {
                    q.isEmpty() -> Text(
                        "输入关键字，找出这门课排在哪几天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    results.isEmpty() -> Text(
                        "没找到相关课程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(results, key = { it.detail.id }) { shown ->
                            val d = shown.detail
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(shown, d.startWeek) }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(courseColor(shown.course.color))
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    Text(shown.course.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOf(
                                            dayLabel(d.day),
                                            "第${d.startNode}节起",
                                            d.teacher,
                                            d.room,
                                            "第${d.startWeek}-${d.endWeek}周"
                                        ).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 顶栏副标题：今天日期（如 10月19日 周一） */
private fun todayDateText(): String {    val today = LocalDate.now()
    val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${today.monthValue}月${today.dayOfMonth}日 ${names[today.dayOfWeek.value - 1]}"
}

/** 顶栏副标题：第 X 周 / 共 M 周 */
private fun weekRangeText(st: AppState, week: Int): String =
    "第 $week 周 / 共 ${st.tableSettings().maxWeek} 周"

/** 长按拖动调课时的临时状态：被拖卡片、原列与原节次、累计位移 */
private class CourseDragState {
    var item by mutableStateOf<ShownCourse?>(null)
    var dayIdx by mutableIntStateOf(-1)
    var startNode by mutableIntStateOf(1)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    fun start(shown: ShownCourse, col: Int, node: Int) {
        item = shown; dayIdx = col; startNode = node; offsetX = 0f; offsetY = 0f
    }

    fun clear() {
        item = null; dayIdx = -1; offsetX = 0f; offsetY = 0f
    }
}

/**
 * 非本周课与非本周课撞在同一格时的优先级：分数越小越优先。
 * 取"离当前周最近的一次真实开课周"，同距离时未来的周优先于过去的周。
 */
private fun nearestWeekScore(d: DetailBean, week: Int): Int {
    var best = Int.MAX_VALUE
    for (w in d.startWeek..d.endWeek) {
        if (!Repo.validInWeek(d, w)) continue
        val score = kotlin.math.abs(w - week) * 2 + if (w >= week) 0 else 1
        if (score < best) best = score
    }
    return best
}

/** 一周课表的真实网格：表头 + 时间列 + 各天卡片（网格线一笔画）；可复用于外观实时预览 */
@Composable
fun WeekGrid(
    st: AppState,
    week: Int,
    settings: Settings,
    modifier: Modifier = Modifier,
    tableId: Long = st.currentTableId,
    onClick: (ShownCourse) -> Unit = {},
    onMoveCourse: ((ShownCourse, Int, Int) -> Unit)? = null,
    onAddAt: ((Int, Int) -> Unit)? = null
) {
    val s = settings
    val rowHeight = s.cellHeightDp.dp
    // 显示行数跟随"一天课程节数"设置（不超出已有的节次时间）
    val total = s.nodeCount.coerceIn(1, st.slots.size)
    val (days, lists) = remember(st, week, s, tableId, total) {
        val ds = dayOrder(s.sundayFirst, s.showSaturday, s.showSunday)
        val ls = ds.map { day ->
            val date = Repo.dateOfWeekDay(st, week, day, tableId)
            val list = date?.let { Repo.dayCourses(st, it, week, tableId) }
                ?: st.details.filter { it.tableId == tableId && it.day == day }
                    .mapNotNull { d -> Repo.courseOf(st, d)?.let { ShownCourse(d, it, !Repo.validInWeek(d, week), null) } }
                    // 非本周课程：设置里关掉就不显示；已经上完的（本周超过其结束周）也不再显示
                    .filter { !it.isOther || (s.showOtherWeek && it.detail.endWeek >= week) }
            // 防重叠：调课/加课卡优先绘制并占位，同一节点上的当天原课不再绘制（撤销调课即自动恢复）；
            // 本周课之间、以及非本周课的原有叠放规则保持不变
            val usedChange = BooleanArray(total + 1)
            val usedNormal = BooleanArray(total + 1)
            val result = ArrayList<ShownCourse>(list.size)
            /** 校验节点区间；avoid(n)=true 时放弃绘制；target 非空则把区间记为已占用 */
            fun occupy(item: ShownCourse, target: BooleanArray?, avoid: (Int) -> Boolean): Boolean {
                val d = item.detail
                if (d.startNode < 1 || d.startNode > total) return false
                val step = d.step.coerceAtMost(total - d.startNode + 1)
                for (n in d.startNode until d.startNode + step) if (avoid(n)) return false
                target?.let { t -> for (n in d.startNode until d.startNode + step) t[n] = true }
                return true
            }
            for (item in list) if (item.record != null && !item.isOther && occupy(item, usedChange) { false }) result.add(item)
            for (item in list) if (item.record == null && !item.isOther && occupy(item, usedNormal) { usedChange[it] }) result.add(item)
            // 非本周课：同一节点只画一门，优先显示"离本周最近的那一周"真正会上课的那一门
            val others = list.filter { it.isOther }
                .sortedWith(
                    compareBy(
                        { nearestWeekScore(it.detail, week) },
                        { it.detail.startNode }
                    )
                )
            val usedOther = BooleanArray(total + 1)
            for (item in others) if (occupy(item, usedOther) { usedChange[it] || usedNormal[it] || usedOther[it] }) result.add(item)
            result.sortedBy { it.detail.startNode }
        }
        ds to ls
    }
    val lineColor = s.uiTextColorArgb?.let { Color(it) }
        ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val headerTextColor = s.uiTextColorArgb?.let { Color(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val strokeWidth = 0.7.dp
    val dayCount = days.size

    // 长按拖动调课：仅当调用方允许移动时启用
    val dragEnabled = onMoveCourse != null
    val drag = remember { CourseDragState() }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val timeColPx = with(density) { TimeColWidth.toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val headerPx = with(density) { HeaderHeight.toPx() }
    val colWidthPx = if (dayCount > 0) (gridWidthPx - timeColPx) / dayCount else 0f
    // 被拖动的卡片在绘制阶段跳过绘制（见 DayColumn 的 drawWithContent）

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (s.showGridLines) Modifier.drawBehind {
                        val w = size.width
                        val gridBottom = HeaderHeight.toPx() + rowHeight.toPx() * total
                        val sw = strokeWidth.toPx()
                        // 横线
                        drawLine(lineColor, Offset(0f, 0f), Offset(w, 0f), sw)
                        drawLine(lineColor, Offset(0f, HeaderHeight.toPx()), Offset(w, HeaderHeight.toPx()), sw)
                        for (r in 1..total) {
                            val y = HeaderHeight.toPx() + rowHeight.toPx() * r
                            drawLine(lineColor, Offset(0f, y), Offset(w, y), sw)
                        }
                        // 竖线
                        val colW = (w - TimeColWidth.toPx()) / dayCount
                        for (c in 0..dayCount) {
                            val x = (TimeColWidth.toPx() + colW * c).coerceIn(0f, w)
                            drawLine(lineColor, Offset(x, 0f), Offset(x, gridBottom), sw)
                        }
                    } else Modifier
                )
        ) {
            // 表头
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(TimeColWidth)
                        .height(HeaderHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "节次",
                        fontSize = s.headerTextSp.sp,
                        lineHeight = (s.headerTextSp + 2).sp,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColor
                    )
                }
                for (day in days) {
                    DayHeader(
                        st = st,
                        day = day,
                        week = week,
                        settings = s,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // 课表主体
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { gridWidthPx = it.width }
            ) {
                TimeColumn(
                    slots = st.slots.take(total),
                    rowHeight = rowHeight,
                    showTime = s.showSlotTime,
                    textColor = headerTextColor,
                    numberSp = s.headerTextSp
                )
                days.forEachIndexed { i, day ->
                    DayColumn(
                        items = lists[i],
                        total = total,
                        rowHeight = rowHeight,
                        slots = st.slots,
                        settings = s,
                        modifier = Modifier.weight(1f),
                        onClick = onClick,
                        dragEnabled = dragEnabled,
                        dragState = drag,
                        onDragStart = { shown, node -> drag.start(shown, i, node) },
                        onDrag = { delta -> drag.offsetX += delta.x; drag.offsetY += delta.y },
                        onTripleTap = onAddAt?.let { cb -> { node -> cb(day, node) } },
                        onDragEnd = {
                            val shown = drag.item
                            if (shown != null && colWidthPx > 0f && rowHeightPx > 0f && days.isNotEmpty()) {
                                val dCol = (drag.offsetX / colWidthPx).roundToInt()
                                val dRow = (drag.offsetY / rowHeightPx).roundToInt()
                                if (dCol != 0 || dRow != 0) {
                                    val newCol = (drag.dayIdx + dCol).coerceIn(0, days.size - 1)
                                    val maxNode = (total - shown.detail.step + 1).coerceAtLeast(1)
                                    val newNode = (drag.startNode + dRow).coerceIn(1, maxNode)
                                    onMoveCourse?.invoke(shown, days[newCol], newNode)
                                }
                            }
                            drag.clear()
                        }
                    )
                }
            }
        }
        if (dragEnabled) {
            DragGhost(
                drag = drag,
                settings = s,
                timeColPx = timeColPx,
                headerPx = headerPx,
                colWidthPx = colWidthPx,
                rowHeightPx = rowHeightPx
            )
        }
    }
}

/** 拖动时跟随手指的课程浮层（只读拖动状态，避免整表重组） */
@Composable
private fun BoxScope.DragGhost(
    drag: CourseDragState,
    settings: Settings,
    timeColPx: Float,
    headerPx: Float,
    colWidthPx: Float,
    rowHeightPx: Float
) {
    val shown = drag.item ?: return
    val density = LocalDensity.current
    val cellW = with(density) { (colWidthPx - 4.dp.toPx()).coerceAtLeast(0f).toDp() }
    val cellH = with(density) { (rowHeightPx * shown.detail.step - 4.dp.toPx()).coerceAtLeast(0f).toDp() }
    val baseColor = courseColor(shown.course.color)
    val fg = settings.courseTextColorArgb?.let { Color(it) } ?: UniformCourseText
    Surface(
        color = baseColor,
        contentColor = fg,
        shape = RoundedCornerShape(settings.cellRadiusDp.dp),
        shadowElevation = 10.dp,
        modifier = Modifier
            .offset {
                IntOffset(
                    (timeColPx + drag.dayIdx * colWidthPx + drag.offsetX).roundToInt(),
                    (headerPx + (drag.startNode - 1) * rowHeightPx + drag.offsetY).roundToInt()
                )
            }
            .width(cellW)
            .height(cellH)
            .alpha(0.95f)
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
            Text(
                shown.course.name,
                fontSize = settings.courseTextSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (settings.showRoomInCell && shown.detail.room.isNotBlank()) {
                Text("@" + shown.detail.room, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekPage(
    st: AppState,
    week: Int,
    onClick: (ShownCourse) -> Unit,
    onMoveCourse: (ShownCourse, Int, Int) -> Unit,
    onAddAt: (Int, Int) -> Unit
) {
    val s = st.tableSettings()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp)
    ) {
        WeekGrid(
            st, week, s, Modifier.fillMaxWidth(),
            onClick = onClick, onMoveCourse = onMoveCourse, onAddAt = onAddAt
        )
        Spacer(Modifier.height(96.dp))
    }
}

/** 三击空白格后的临时加课：起始节次由所点格子固定，可选持续节数/周数；与已有课冲突时禁用确定 */
@Composable
private fun BlankAddDialog(
    st: AppState,
    week: Int,
    day: Int,
    startNode: Int,
    onDismiss: () -> Unit,
    onConfirm: (ChangeRecord) -> Unit
) {
    val s = st.tableSettings()
    val total = s.nodeCount.coerceIn(1, st.slots.size)
    val maxStep = (total - startNode + 1).coerceAtLeast(1)
    val remainWeeks = (s.maxWeek - week + 1).coerceAtLeast(1)
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var weeks by remember { mutableIntStateOf(1) }
    var parity by remember { mutableIntStateOf(0) }

    val date = Repo.dateOfWeekDay(st, week, day)
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekOptions = (listOf(1, 2, 3, 4) + listOf(remainWeeks))
        .distinct().filter { it in 1..remainWeeks }.sorted()

    // 冲突检查：逐周看 [startNode, startNode+step-1] 是否已有课（含调课/加课卡、单双周）
    val conflict = remember(st, week, day, startNode, step, weeks, parity) {
        val endNode = startNode + step - 1
        var hit: String? = null
        outer@ for (w in week until week + weeks) {
            if (parity == 1 && w % 2 == 0) continue
            if (parity == 2 && w % 2 == 1) continue
            val d = Repo.dateOfWeekDay(st, w, day) ?: continue
            for (item in Repo.dayCourses(st, d, w)) {
                if (item.isOther) continue
                val ds = item.detail.startNode
                val de = ds + item.detail.step - 1
                if (ds <= endNode && de >= startNode) {
                    val range = if (de > ds) "第 $ds-$de 节" else "第 $ds 节"
                    hit = "第 $w 周${dayNames[day - 1]}$range 已有「${item.course.name}」"
                    break@outer
                }
            }
        }
        hit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("临时加课") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    buildString {
                        append("第 $week 周 ${dayNames[day - 1]}")
                        date?.let { append(" ${it.monthValue}月${it.dayOfMonth}日") }
                        append(" · 从第 $startNode 节开始")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacher, onValueChange = { teacher = it },
                        label = { Text("教师") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room, onValueChange = { room = it },
                        label = { Text("地点") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("持续节数", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (n in 1..maxStep) {
                        FilterChip(selected = step == n, onClick = { step = n }, label = { Text("$n 节") })
                    }
                }
                Text("持续周数", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (n in weekOptions) {
                        FilterChip(
                            selected = weeks == n,
                            onClick = { weeks = n },
                            label = { Text(if (n == 1) "仅本周" else "$n 周") }
                        )
                    }
                }
                if (weeks > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("每周", "单周", "双周").forEachIndexed { i, t ->
                            FilterChip(selected = parity == i, onClick = { parity = i }, label = { Text(t) })
                        }
                    }
                }
                conflict?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = conflict == null,
                onClick = {
                    val start = date ?: LocalDate.now()
                    val end = start.plusDays(((weeks - 1) * 7).toLong())
                    onConfirm(
                        ChangeRecord(
                            id = Repo.nextId(), type = 1,
                            fromDate = start.toEpochDay(),
                            endDate = if (weeks > 1) end.toEpochDay() else 0L,
                            parity = if (weeks > 1) parity else 0,
                            name = name.trim().ifBlank { "临时加课" },
                            teacher = teacher.trim(), room = room.trim(),
                            startNode = startNode, step = step
                        )
                    )
                }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 左侧节次时间列：节次数字与表头同字号同粗细 + 起止两行小字，定高裁剪防重叠 */
@Composable
private fun TimeColumn(slots: List<TimeSlot>, rowHeight: androidx.compose.ui.unit.Dp, showTime: Boolean, textColor: Color, numberSp: Int) {
    Column(Modifier.width(TimeColWidth)) {
        slots.forEachIndexed { i, slot ->
            Box(
                Modifier
                    .height(rowHeight)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${i + 1}",
                        fontSize = numberSp.sp,
                        lineHeight = (numberSp + 2).sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (showTime) {
                        Text(
                            slot.start,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            maxLines = 1,
                            color = textColor
                        )
                        Text(
                            slot.end,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            maxLines = 1,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    st: AppState,
    day: Int,
    week: Int,
    settings: Settings,
    modifier: Modifier = Modifier,
    tableId: Long = st.currentTableId
) {
    val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val date = Repo.dateOfWeekDay(st, week, day, tableId)
    val isToday = date != null && date == LocalDate.now()
    val baseColor = settings.uiTextColorArgb?.let { Color(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(HeaderHeight)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            names[day - 1],
            fontSize = settings.headerTextSp.sp,
            lineHeight = (settings.headerTextSp + 2).sp,
            fontWeight = FontWeight.Bold,
            color = if (isToday) MaterialTheme.colorScheme.onPrimary else baseColor
        )
        Text(
            // 带上月份：翻到别的周时也能一眼看出是哪个月（对齐苏大云那种 11/23 的写法）
            date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: " ",
            fontSize = (settings.headerTextSp - 3).coerceAtLeast(8).sp,
            lineHeight = (settings.headerTextSp - 1).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.onPrimary else baseColor
        )
    }
}

@Composable
private fun DayColumn(
    items: List<ShownCourse>,
    total: Int,
    rowHeight: androidx.compose.ui.unit.Dp,
    slots: List<TimeSlot>,
    settings: Settings,
    modifier: Modifier = Modifier,
    onClick: (ShownCourse) -> Unit,
    dragEnabled: Boolean = false,
    dragState: CourseDragState? = null,
    onDragStart: (ShownCourse, Int) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onTripleTap: ((Int) -> Unit)? = null,
    onDragEnd: () -> Unit = {}
) {
    // 三击空白格临时加课：同一节点 500ms 内连点三次才触发
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val context = LocalContext.current
    var tapNode by remember { mutableIntStateOf(-1) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapAt by remember { mutableLongStateOf(0L) }
    Box(
        modifier = modifier
            .height(rowHeight * total)
            .padding(horizontal = 2.dp)
            .then(
                if (onTripleTap != null) Modifier.pointerInput(total, items) {
                    detectTapGestures { pos ->
                        if (rowPx <= 0f) return@detectTapGestures
                        val node = ((pos.y / rowPx).toInt() + 1).coerceIn(1, total)
                        // 只有空白格才算：该节点已被卡片占用时直接忽略（那是在点卡片本身）
                        if (items.any { it.detail.startNode <= node && node < it.detail.startNode + it.detail.step }) {
                            tapNode = -1; tapCount = 0
                            return@detectTapGestures
                        }
                        val now = System.currentTimeMillis()
                        tapCount = if (node == tapNode && now - tapAt <= 500L) tapCount + 1 else 1
                        tapNode = node; tapAt = now
                        if (tapCount >= 3) {
                            tapNode = -1; tapCount = 0
                            onTripleTap(node)
                        }
                    }
                } else Modifier
            )
    ) {
        for (item in items) {
            val d = item.detail
            if (d.startNode < 1 || d.startNode > total) continue
            val step = d.step.coerceAtMost(total - d.startNode + 1)
            val top = rowHeight * (d.startNode - 1)
            val glass = LocalGlass.current

            val baseColor = courseColor(item.course.color)
            // 深色主题下非本周课太淡会几乎看不见，给它一个更高的不透明度下限
            val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val otherScale = if (darkTheme) {
                (settings.otherWeekAlphaPercent / 100f).coerceAtLeast(0.45f)
            } else settings.otherWeekAlphaPercent / 100f
            val cellAlpha = (settings.cellAlphaPercent / 100f) *
                    (if (item.isOther) otherScale else 1f) *
                    (if (glass) 0.9f else 1f)
            val radius = settings.cellRadiusDp.dp
            // 文字颜色：所有课程统一同色（WakeUp 风格）；设置里自定义颜色时优先
            // 深色主题下非本周课改用跟随主题的浅色字，否则深色字压在深色底上等于看不见
            val defaultFg = if (item.isOther && darkTheme) MaterialTheme.colorScheme.onSurface else UniformCourseText
            val fgBase = settings.courseTextColorArgb?.let { Color(it) } ?: defaultFg
            val fg = (if (settings.courseTextOverlay) multiplyBlend(fgBase, baseColor) else fgBase)
                .copy(alpha = if (item.isOther) 0.85f else 1f)
            // 边框：自定义色 > 使用格子色 > 非本周细边框
            val borderCol = when {
                settings.borderUseCellColor -> baseColor.copy(alpha = cellAlpha)
                settings.cellBorderColorArgb != null -> Color(settings.cellBorderColorArgb!!)
                item.isOther -> baseColor.copy(alpha = 0.5f)
                else -> null
            }

            Column(
                modifier = Modifier
                    .drawWithContent {
                        // 在绘制阶段读拖动状态：卡片被拖动时整块跳过绘制，
                        // 但节点仍然存在，手指上的手势不会被取消
                        if (dragState?.item?.detail?.id != d.id) drawContent()
                    }
                    .padding(top = top)
                    .height(rowHeight * step - 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius))
                    .background(baseColor.copy(alpha = cellAlpha.coerceIn(0f, 1f)))
                    .then(
                        if (borderCol != null) Modifier.drawWithCache {
                            val rr = CornerRadius(radius.toPx().coerceAtMost(size.minDimension / 2))
                            val sw = 1.dp.toPx()
                            // Stroke/PathEffect 只在缓存失效时重建，避免逐帧分配
                            val style = if (settings.borderDashed) {
                                Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                            } else Stroke(width = sw)
                            onDrawBehind {
                                drawRoundRect(color = borderCol, cornerRadius = rr, style = style)
                            }
                        } else Modifier
                    )
                    .then(
                        // 长按拖动调课：本周的普通课程与调课卡都可拖（调课卡能拖回原位/再调）；
                        // 加课、停课卡不参与
                        if (dragEnabled && !item.isOther && (item.record == null || item.record.type == 0)) {
                            Modifier.pointerInput(item.detail.id, d.startNode, item.record?.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(item, d.startNode) },
                                    onDrag = { change, delta -> change.consume(); onDrag(delta) },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragEnd
                                )
                            }
                        } else if (dragEnabled && item.isOther) {
                            // 非本周课不可拖动：长按只弹一条轻提示（Toast：黑色半透明、自动消失、
                            // 无按钮、不拦截其它手势），让用户知道是"不让动"而不是"没点中"
                            Modifier.pointerInput(item.detail.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        Toast.makeText(context, "非本周课程不可移动", Toast.LENGTH_SHORT).show()
                                    },
                                    onDrag = { change, _ -> change.consume() },
                                    onDragEnd = {},
                                    onDragCancel = {}
                                )
                            }
                        } else Modifier
                    )
                    .clickable { onClick(item) }
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalArrangement = if (settings.textVerticalCenter) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = if (settings.textHorizontalCenter) Alignment.CenterHorizontally else Alignment.Start
            ) {
                val prefix = when (item.record?.type) {
                    1 -> "【加】"
                    2 -> "【停】"
                    0 -> "【调】"
                    else -> ""
                }
                val nameText = prefix + item.course.name
                // 课程名统一字号/字重（所有卡片一致），行数按格子高度尽量显示全
                val subLines = (if (settings.showTimeInCell) 10 else 0) +
                        (if (settings.showRoomInCell && item.detail.room.isNotBlank()) 10 else 0) +
                        (if (settings.showTeacherInCell && item.detail.teacher.isNotBlank()) 10 else 0)
                val nameH = (rowHeight * step).value - 12f - subLines
                val nameSp = settings.courseTextSp.toFloat()
                Text(
                    nameText,
                    style = LocalTextStyle.current.copy(
                        fontSize = nameSp.sp,
                        lineHeight = (nameSp + 1.5f).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = courseNameMaxLines(nameSp, nameH),
                    overflow = TextOverflow.Ellipsis,
                    color = fg
                )
                if (settings.showTimeInCell) {
                    val first = slots.getOrNull(d.startNode - 1)
                    val last = slots.getOrNull(d.startNode + d.step - 2) ?: first
                    if (first != null) {
                        Text(
                            if (d.step > 1 && last != null) "${first.start}-${last.end}" else first.start,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = fg
                        )
                    }
                }
                if (settings.showRoomInCell && item.detail.room.isNotBlank()) {
                    Text(
                        "@" + item.detail.room,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = fg
                    )
                }
                if (settings.showTeacherInCell && item.detail.teacher.isNotBlank()) {
                    Text(
                        item.detail.teacher,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = fg
                    )
                }
            }
        }
    }
}

/** 课程详情：底部快速弹出（比 ModalBottomSheet 快、延时短；进场中不响应点击外部，避免误关） */
@Composable
fun CourseDetailSheet(
    shown: ShownCourse,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onCopy: (ShownCourse) -> Unit,
    onDelete: (ShownCourse) -> Unit
) {
    val st by Repo.state.collectAsState()
    val d = shown.detail
    val typeText = when (d.type) {
        1 -> "单周"
        2 -> "双周"
        else -> "每周"
    }
    val timeText = st.slots.getOrNull(d.startNode - 1)?.let { first ->
        val last = st.slots.getOrNull(d.startNode + d.step - 2) ?: first
        "第 ${d.startNode}-${d.startNode + d.step - 1} 节  ${first.start} - ${last.end}"
    } ?: "第 ${d.startNode}-${d.startNode + d.step - 1} 节"

    // 首帧后立即播放约 120ms 的快速进场；进场期间屏蔽"点外部关闭"，避免用户以为没点中再点一下反而关掉
    var entered by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(150)
        interactive = true
    }
    val closeIfReady = { if (interactive) onDismiss() }

    Dialog(
        onDismissRequest = closeIfReady,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            // 让弹窗自己处理系统栏内缩（卡片底部留白由 navigationBarsPadding 负责）
            decorFitsSystemWindows = false
        )
    ) {
        // Android 15 边到边下，部分系统会把 Dialog 的内容视图整体下移一个状态栏高度，
        // 但内容视图高度仍按整屏计算 —— 贴底的卡片于是探出屏幕下缘，最后一行（如「上课地点」）被截断。
        // 这里实测内容视图探出屏幕的像素数，等量补在卡片底部，卡片即可正好贴住屏幕底边。
        val dialogView = LocalView.current
        val density = LocalDensity.current
        var bottomFix by remember { mutableStateOf(0.dp) }
        LaunchedEffect(Unit) {
            delay(64) // 等内容视图完成首次布局后再量
            if (dialogView.height > 0) {
                val loc = IntArray(2)
                dialogView.getLocationOnScreen(loc)
                val screenBottom = dialogView.context.resources.displayMetrics.heightPixels
                val overflow = loc[1] + dialogView.height - screenBottom
                if (overflow > 0) bottomFix = with(density) { overflow.toDp() }
            }
        }
        Box(Modifier.fillMaxSize()) {
            // 半透明遮罩：快速淡入
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(tween(110)),
                exit = fadeOut(tween(70)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { closeIfReady() }
                )
            }
            // 底部卡片：从底部快速滑入
            AnimatedVisibility(
                visible = entered,
                enter = slideInVertically(tween(130)) { it },
                exit = slideOutVertically(tween(70)) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomFix)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        Modifier
                            // 内容高于可用高度时可滚动，避免长课名把「上课地点」挤出屏幕
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 24.dp)
                    ) {
                        // 顶部指示条（保持原来的观感）
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    shown.course.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (shown.record != null) {
                                    Text(
                                        when (shown.record.type) {
                                            1 -> "临时加课"
                                            2 -> "停课"
                                            else -> "调课安排"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(shown) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                            if (shown.record == null) {
                                IconButton(onClick = { onCopy(shown) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                }
                                IconButton(onClick = { onEdit(shown.course.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        DetailRow(Icons.Default.CalendarMonth, "上课周次", "第 ${d.startWeek}-${d.endWeek} 周 · $typeText")
                        DetailRow(Icons.Default.AccessTime, "节次时间", timeText)
                        DetailRow(Icons.Default.Person, "授课教师", shown.detail.teacher.ifBlank { "未填写" })
                        DetailRow(Icons.Default.Place, "上课地点", shown.detail.room.ifBlank { "未填写" })
                        // ---- 课程备注（群号 / 课件链接 / 备忘）----
                        if (shown.course.note.isNotBlank()) {
                            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(
                                        "课程备注",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    NoteText(shown.course.note, Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        // ---- 课程待办 / 规划（设置里可整块关掉）----
                        // 注意：设置页的开关写的是「当前课表」的 settings，
                        // 这里也必须读 tableSettings()，读全局 st.settings 会永远拿到默认值。
                        if (st.tableSettings().courseTodoEnabled) {
                            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 12.dp))
                            val todos = st.todos.filter { it.courseId == shown.course.id }
                            // 未勾掉的排在前面，其余按添加顺序
                            val ordered = todos.sortedWith(compareBy({ it.done }, { it.createdAt }))
                            var draft by remember(shown.course.id) { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "课程待办",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${todos.count { it.done }}/${todos.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (ordered.isEmpty()) {
                                Text(
                                    "还没有计划，给自己加一条吧",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            ordered.forEach { todo ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = todo.done,
                                        onCheckedChange = { checked ->
                                            Repo.update { s ->
                                                s.copy(todos = s.todos.map {
                                                    if (it.id == todo.id) it.copy(done = checked) else it
                                                })
                                            }
                                        }
                                    )
                                    Text(
                                        todo.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                                        color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        Repo.update { s ->
                                            s.copy(todos = s.todos.filterNot { it.id == todo.id })
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除待办",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("加一条计划，比如「交作业」") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = {
                                        val text = draft.trim()
                                        if (text.isNotEmpty()) {
                                            Repo.update { s ->
                                                s.copy(
                                                    todos = s.todos + CourseTodo(
                                                        id = Repo.nextId(),
                                                        courseId = shown.course.id,
                                                        text = text,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            draft = ""
                                        }
                                    },
                                    enabled = draft.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "添加待办")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 备注里的链接（http/https 开头），交给系统用浏览器或对应 App 打开 */
private val NoteUrlRegex = Regex("""https?://[^\s<>"'，。、；：）)】》]+""")

@Composable
private fun NoteText(text: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            NoteUrlRegex.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                val url = match.value
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    )
                ) { append(url) }
                last = match.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/** 右上角「更多」：悬浮卡片，从屏幕底部升起（周数 / 课表 / 功能图标） */
@Composable
fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenSub: (SubPage) -> Unit,
    onAddWidget: () -> Unit
) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings()
    val ctx = LocalContext.current
    var addingTable by remember { mutableStateOf(false) }
    var newTableName by remember { mutableStateOf("") }
    var editingWeek by remember { mutableStateOf(false) }
    var weekText by remember { mutableStateOf(s.currentWeek.toString()) }
    var showShareOptions by remember { mutableStateOf(false) }
    // 课表长图：先问要不要带时间列，再决定存相册还是直接分享
    var showImageOptions by remember { mutableStateOf(false) }
    var imageWithTime by remember { mutableStateOf(true) }
    // 长图样式：竖向 / 横向 / 深色 / 极简
    var imageStyle by remember { mutableStateOf(TimetableImage.Style.VERTICAL) }
    val dark = st.settings.themeMode == 2
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // 面板宽度：和悬浮卡片一致，两侧各留 16dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val menuWidth = (screenWidth - 32.dp).coerceAtLeast(280.dp)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    if (expanded) {
        // 悬浮卡片：从屏幕底部升起。用 Dialog 才能覆盖到系统导航栏，
        // 避免卡片和底栏之间露出一条不同颜色的背景缝。
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        val requestClose: () -> Unit = { visibleState.targetState = false }
        LaunchedEffect(visibleState.currentState, visibleState.targetState) {
            if (!visibleState.currentState && !visibleState.targetState) onDismiss()
        }
        Dialog(
            onDismissRequest = requestClose,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            // 去掉 Dialog 默认的整屏压暗：关闭时只滑走卡片，不会留下深色残影
            val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
            SideEffect { dialogWindowProvider?.window?.setDimAmount(0f) }
            Box(Modifier.fillMaxSize()) {
                // 透明点击层：点卡片外面收起
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { requestClose() }
                )
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { it }
                    ) + fadeIn(animationSpec = tween(140)),
                    exit = slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { it }
                    ) + fadeOut(animationSpec = tween(120)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .width(menuWidth)
                            .heightIn(max = screenHeight * 0.9f)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                        ) {
                            // ---------- 周数 ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text("周数", style = MaterialTheme.typography.labelMedium, color = muted)
                Spacer(Modifier.weight(1f))
                Text(
                    "修改当前周",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            weekText = s.currentWeek.toString()
                            onDismiss()
                            editingWeek = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            // 拖动时标题实时跟随滑块。DropdownMenu 内部按固有宽度测量内容，
            // 不能放 BoxWithConstraints 这类 SubcomposeLayout，所以不再做浮出的周数气泡。
            var sliderPos by remember(s.currentWeek, s.maxWeek) {
                mutableFloatStateOf(s.currentWeek.toFloat())
            }
            Text(
                "第 ${sliderPos.roundToInt()} 周 · 共 ${s.maxWeek} 周",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            // 离散档位滑块：1..maxWeek 逐周吸附
            GradientSlider(
                value = sliderPos,
                onValueChange = { sliderPos = it },
                valueRange = 1f..s.maxWeek.toFloat().coerceAtLeast(1f),
                steps = (s.maxWeek - 2).coerceAtLeast(0),
                onValueChangeFinished = {
                    Repo.updateSettings {
                        it.copy(currentWeek = sliderPos.roundToInt().coerceIn(1, it.maxWeek))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp)
            )
            if (s.startDate == null) {
                Text(
                    "未设置开学日期，周数按推算起点计；可在设置里设置开学日期",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(top = 6.dp)
            )
            // ---------- 课表 ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    "课表",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "新建课表",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onDismiss()
                            addingTable = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "管理",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSub(SubPage.TableManage) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 归档的旧学期不在切换器里出现
                st.tables.filterNot { it.archived }.forEach { table ->
                    TableThumb(
                        table = table,
                        selected = table.id == st.currentTableId,
                        count = st.details.count { it.tableId == table.id },
                        onSelect = { Repo.update { cur -> cur.copy(currentTableId = table.id) } }
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            // ---------- 功能 ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FunctionIcon("上课时间", Icons.Default.AccessTime, TileBlue, Modifier.weight(1f)) {
                    onOpenSub(SubPage.TimeTable)
                }
                FunctionIcon("已添课程", Icons.Default.ViewList, TileGreen, Modifier.weight(1f)) {
                    onOpenSub(SubPage.CourseList)
                }
                FunctionIcon("课表设置", Icons.Default.Settings, TilePurple, Modifier.weight(1f)) {
                    onOpenSub(SubPage.TableSettings(st.currentTableId))
                }
                FunctionIcon(
                    label = if (dark) "浅色模式" else "夜间模式",
                    icon = if (dark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                    tileColor = TileOrange,
                    modifier = Modifier.weight(1f)
                ) {
                    Repo.update { s -> s.copy(settings = s.settings.copy(themeMode = if (s.settings.themeMode == 2) 1 else 2)) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FunctionIcon("小部件", Icons.Default.Widgets, TilePink, Modifier.weight(1f)) {
                    onDismiss()
                    onAddWidget()
                }
                FunctionIcon("月历", Icons.Default.CalendarMonth, TileGreen, Modifier.weight(1f)) {
                    onOpenSub(SubPage.MonthCalendar)
                }
                FunctionIcon("分享", Icons.Default.Share, TileCyan, Modifier.weight(1f)) {
                    onDismiss()
                    showShareOptions = true
                }
                FunctionIcon("全局设置", Icons.Default.Tune, TileBlue, Modifier.weight(1f)) {
                    onOpenSub(SubPage.Settings)
                }
            }
                    }
                }
            }
        }
    }
    }

    if (editingWeek) {
        AlertDialog(
            onDismissRequest = { editingWeek = false },
            title = { Text("修改当前周") },
            text = {
                OutlinedTextField(
                    value = weekText,
                    onValueChange = { v -> weekText = v.filter { it.isDigit() }.take(2) },
                    label = { Text("周数（1-${s.maxWeek}）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = weekText.toIntOrNull() ?: 1
                    Repo.updateSettings { it.copy(currentWeek = w.coerceIn(1, it.maxWeek)) }
                    editingWeek = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingWeek = false }) { Text("取消") } }
        )
    }

    if (addingTable) {
        AlertDialog(
            onDismissRequest = { addingTable = false },
            title = { Text("新建课表") },
            text = {
                OutlinedTextField(value = newTableName, onValueChange = { newTableName = it }, label = { Text("课表名称") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newTableName.ifBlank { "课表 ${st.tables.size + 1}" }
                    val id = Repo.nextId()
                    Repo.update { s -> s.copy(tables = s.tables + TableBean(id, name), currentTableId = id) }
                    addingTable = false
                    newTableName = ""
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { addingTable = false }) { Text("取消") } }
        )
    }

    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { showShareOptions = false },
            title = { Text("分享课表") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "第 ${s.currentWeek} 周 · ${st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ShareMethodRow(
                        title = "纯文字",
                        subtitle = "整理成一段文字，直接发到聊天窗口",
                        onClick = {
                            showShareOptions = false
                            shareTimetable(ctx, st, s.currentWeek)
                            onDismiss()
                        }
                    )
                    ShareMethodRow(
                        title = "CSV 文件",
                        subtitle = "生成 .csv，可拖进 Excel / WPS 打开或再导入",
                        onClick = {
                            showShareOptions = false
                            shareTimetableCsv(ctx, st, s.currentWeek)
                            onDismiss()
                        }
                    )
                    ShareMethodRow(
                        title = "整学期 CSV",
                        subtitle = "全部课程含起止周与单双周，适合导入其它课表 App",
                        onClick = {
                            showShareOptions = false
                            shareTimetableFullCsv(ctx, st)
                            onDismiss()
                        }
                    )
                    ShareMethodRow(
                        title = "课表长图",
                        subtitle = "生成一张图片，直接发群或存进相册",
                        onClick = {
                            showShareOptions = false
                            showImageOptions = true
                        }
                    )
                    ShareMethodRow(
                        title = "日历文件 (.ics)",
                        subtitle = "整学期导入系统日历，手机、手表、电脑都能看到",
                        onClick = {
                            showShareOptions = false
                            val ics = IcsExport.build(st, st.currentTableId)
                            if (ics == null) {
                                Toast.makeText(
                                    ctx, "先在「课表设置」里填好开学日期，才能排进日历", Toast.LENGTH_LONG
                                ).show()
                            } else {
                                shareIcs(ctx, st, ics)
                                onDismiss()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareOptions = false }) { Text("取消") }
            }
        )
    }

    if (showImageOptions) {
        AlertDialog(
            onDismissRequest = { showImageOptions = false },
            title = { Text("课表长图") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "第 ${s.currentWeek} 周 · ${st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimetableImage.Style.entries.forEach { style ->
                            FilterChip(
                                selected = imageStyle == style,
                                onClick = { imageStyle = style },
                                label = { Text(style.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (imageStyle) {
                            TimetableImage.Style.VERTICAL -> "经典网格：星期为列，节次从上往下排"
                            TimetableImage.Style.HORIZONTAL -> "横向排版：一个星期一行，节次从左往右排"
                            TimetableImage.Style.DARK -> "深色底：夜里看着舒服，发出去也更耐看"
                            TimetableImage.Style.MINIMAL -> "极简：去掉网格线，只留淡色块和左侧色条"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (imageStyle != TimetableImage.Style.HORIZONTAL) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { imageWithTime = !imageWithTime }
                        ) {
                            Checkbox(checked = imageWithTime, onCheckedChange = { imageWithTime = it })
                            Text("包含左侧节次与时间列", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val week = s.currentWeek
                    showImageOptions = false
                    val bmp = TimetableImage.render(st, week, st.currentTableId, imageWithTime, imageStyle)
                    shareBitmap(ctx, bmp, imageFileName(st, week), "第 $week 周课表")
                    onDismiss()
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val week = s.currentWeek
                    showImageOptions = false
                    val bmp = TimetableImage.render(st, week, st.currentTableId, imageWithTime, imageStyle)
                    val ok = TimetableImage.saveToGallery(ctx, bmp, imageFileName(st, week))
                    Toast.makeText(
                        ctx, if (ok) "已保存到相册" else "保存失败，可以改用分享", Toast.LENGTH_SHORT
                    ).show()
                    if (ok) onDismiss()
                }) { Text("存相册") }
            }
        )
    }

}

/** 课表缩略卡片 */
@Composable
private fun TableThumb(
    table: TableBean,
    selected: Boolean,
    count: Int,
    onSelect: () -> Unit
) {
    Column(
        Modifier
            .width(116.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                table.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "当前课表",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            "$count 门课",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 把某一周的课表整理成一段文字，走系统分享面板发出去 */
private fun shareTimetable(ctx: Context, st: AppState, week: Int) {
    val tableId = st.currentTableId
    val tableName = st.tables.firstOrNull { it.id == tableId }?.name ?: "我的课表"
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val fmt = DateTimeFormatter.ofPattern("M.d")
    val sb = StringBuilder("📚 $tableName · 第 $week 周")

    val first = Repo.dateOfWeekDay(st, week, 1, tableId)
    val last = Repo.dateOfWeekDay(st, week, 7, tableId)
    if (first != null && last != null) sb.append("（${first.format(fmt)} - ${last.format(fmt)}）")
    sb.append('\n')

    var total = 0
    for (day in 1..7) {
        val date = Repo.dateOfWeekDay(st, week, day, tableId) ?: continue
        val courses = Repo.dayCourses(st, date, week, tableId).sortedBy { it.detail.startNode }
        if (courses.isEmpty()) continue
        total += courses.size
        sb.append('\n').append(dayNames[day - 1]).append('\n')
        for (sc in courses) {
            val start = st.slots.getOrNull(sc.detail.startNode - 1)?.start.orEmpty()
            val end = st.slots.getOrNull(sc.detail.startNode + sc.detail.step - 2)?.end.orEmpty()
            sb.append("  ")
            if (start.isNotEmpty()) sb.append(start).append('-').append(end).append(' ')
            sb.append(sc.course.name)
            if (sc.detail.room.isNotEmpty()) sb.append(" @").append(sc.detail.room)
            if (sc.detail.teacher.isNotEmpty()) sb.append(' ').append(sc.detail.teacher)
            sb.append('\n')
        }
    }
    if (total == 0) sb.append("\n本周没有课程安排\n")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$tableName 第 $week 周课表")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(intent, "分享课表"))
}

/** 分享方式的一行（纯文字 / CSV 文件） */
@Composable
private fun ShareMethodRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** CSV 单元格转义：含逗号 / 引号 / 换行时用双引号包起来，内部引号翻倍 */
private fun csvCell(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value

/**
 * 把某一周的课表导出成 CSV 文件再分享。
 * 列：日期,星期,开始时间,结束时间,课程,教师,地点（Excel / WPS 可直接打开）。
 * 文件写到 cache/share/ 下，通过 FileProvider 以 content:// 交给分享面板。
 */
private fun shareTimetableCsv(ctx: Context, st: AppState, week: Int) {
    val tableId = st.currentTableId
    val tableName = st.tables.firstOrNull { it.id == tableId }?.name ?: "我的课表"
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val fmt = DateTimeFormatter.ofPattern("M.d")
    // BOM 开头，Excel 打开中文才不会乱码
    val csv = StringBuilder("\uFEFF日期,星期,开始时间,结束时间,课程,教师,地点\r\n")

    for (day in 1..7) {
        val date = Repo.dateOfWeekDay(st, week, day, tableId) ?: continue
        val courses = Repo.dayCourses(st, date, week, tableId).sortedBy { it.detail.startNode }
        for (sc in courses) {
            val start = st.slots.getOrNull(sc.detail.startNode - 1)?.start.orEmpty()
            val end = st.slots.getOrNull(sc.detail.startNode + sc.detail.step - 2)?.end.orEmpty()
            csv.append(csvCell(date.format(fmt))).append(',')
                .append(csvCell(dayNames[day - 1])).append(',')
                .append(csvCell(start)).append(',')
                .append(csvCell(end)).append(',')
                .append(csvCell(sc.course.name)).append(',')
                .append(csvCell(sc.detail.teacher)).append(',')
                .append(csvCell(sc.detail.room)).append("\r\n")
        }
    }

    val safeName = tableName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    shareCsv(ctx, "${safeName}_第${week}周.csv", csv.toString(), "$tableName 第 $week 周课表")
}

/**
 * 整学期课表导出：一条课程安排一行，带上起止周与单双周。
 * 和「当前周」那份的区别是它不展开到具体日期，适合交换数据 / 导入其它课表 App。
 */
private fun shareTimetableFullCsv(ctx: Context, st: AppState) {
    val tableId = st.currentTableId
    val tableName = st.tables.firstOrNull { it.id == tableId }?.name ?: "我的课表"
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val typeNames = listOf("每周", "单周", "双周")
    val csv = StringBuilder("\uFEFF课程,教师,地点,星期,开始节次,结束节次,开始时间,结束时间,起始周,结束周,周次\r\n")

    st.details.filter { it.tableId == tableId }
        .sortedWith(compareBy({ it.day }, { it.startNode }))
        .forEach { d ->
            val course = st.courses.firstOrNull { it.id == d.courseId } ?: return@forEach
            val start = st.slots.getOrNull(d.startNode - 1)?.start.orEmpty()
            val end = st.slots.getOrNull(d.startNode + d.step - 2)?.end.orEmpty()
            csv.append(csvCell(course.name)).append(',')
                .append(csvCell(d.teacher)).append(',')
                .append(csvCell(d.room)).append(',')
                .append(csvCell(dayNames.getOrElse(d.day - 1) { "" })).append(',')
                .append(d.startNode).append(',')
                .append(d.startNode + d.step - 1).append(',')
                .append(csvCell(start)).append(',')
                .append(csvCell(end)).append(',')
                .append(d.startWeek).append(',')
                .append(d.endWeek).append(',')
                .append(csvCell(typeNames.getOrElse(d.type) { "每周" })).append("\r\n")
        }

    val safeName = tableName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    shareCsv(ctx, "${safeName}_全学期.csv", csv.toString(), "$tableName 全学期课表")
}

/** 把 CSV 文本写到缓存目录并唤起系统分享面板 */
private fun shareCsv(ctx: Context, fileName: String, content: String, subject: String) {
    val file = writeShareFile(ctx, fileName, content)
    shareFile(ctx, file, "text/csv", subject, "分享课表 CSV")
}

/** 课表长图的文件名：表名_第N周（去掉文件系统不认的字符） */
private fun imageFileName(st: AppState, week: Int): String {
    val tableName = st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"
    return "${tableName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_第${week}周"
}

/** 分享课表长图：PNG 先落进缓存，再以 content:// 交给分享面板 */
private fun shareBitmap(ctx: Context, bmp: Bitmap, fileName: String, subject: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "$fileName.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(ctx, file, "image/png", subject, "分享课表图片")
}

/** 分享整学期日历：.ics 交给系统日历 App 或聊天工具 */
private fun shareIcs(ctx: Context, st: AppState, content: String) {
    val tableName = st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"
    val safeName = tableName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val file = writeShareFile(ctx, "$safeName.ics", content)
    shareFile(ctx, file, "text/calendar", "$tableName 课表", "导入或分享日历文件")
}

/** 把文本内容写进缓存目录的 share/ 下 */
private fun writeShareFile(ctx: Context, fileName: String, content: String): File {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    return File(dir, fileName).apply { writeText(content, Charsets.UTF_8) }
}

/** 统一的分享出口：缓存文件 → FileProvider → 系统分享面板 */
private fun shareFile(ctx: Context, file: File, mime: String, subject: String, chooser: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, chooser))
}

/**
 * 一键把小部件挂到桌面：系统 / 桌面支持时弹添加确认，不支持就给出手动添加的路径。
 * 四款小组件在 AndroidManifest 里都是 exported 的 receiver，直接按 ComponentName 请求即可。
 */
@Composable
private fun WidgetPinRow(title: String, provider: Class<*>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                val mgr = AppWidgetManager.getInstance(ctx)
                // 部分桌面（MIUI / 澎湃OS）会误报「不支持」但实际能加，所以不再拦在 isRequestPinAppWidgetSupported 上，
                // 统一尝试请求，按返回值给提示
                val ok = runCatching {
                    mgr.requestPinAppWidget(ComponentName(ctx, provider), null, null)
                }.getOrDefault(false)
                onDone()
                Toast.makeText(
                    ctx,
                    if (ok) "已发送添加请求；桌面没反应就长按桌面空白处 → 小部件 → 安卓小部件 → 今日课程"
                    else "当前桌面不支持一键添加，请长按桌面空白处 → 小部件 → 安卓小部件 → 今日课程",
                    Toast.LENGTH_LONG
                ).show()
            }
            .padding(horizontal = 4.dp, vertical = 11.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 彩色圆角方块功能图标 */
@Composable
private fun FunctionIcon(
    label: String,
    icon: ImageVector,
    tileColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
