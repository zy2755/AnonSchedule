package com.anon.schedule.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.AppState
import com.anon.schedule.data.CourseLogic
import com.anon.schedule.data.Repo
import com.anon.schedule.data.ShownCourse
import com.anon.schedule.data.TimeSlot
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.courseColor
import java.time.LocalDate
import java.time.YearMonth

/** 一个月历模型：按周排好的日期格子 + 每天要上的课 */
private data class MonthModel(
    val weeks: List<List<LocalDate?>>,
    val courses: Map<LocalDate, List<ShownCourse>>
)

/** 月历视图：整月看每天有几门课，点某一天看当天的课程与待办 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val model = remember(st, month) { buildMonth(st, month) }

    // 翻月后如果选中的日期已经不在当前月，就默认选 1 号
    fun shiftMonth(delta: Long) {
        month = month.plusMonths(delta)
        if (YearMonth.from(selected) != month) selected = month.atDay(1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${month.year}年${month.monthValue}月") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { shiftMonth(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上个月")
                    }
                    IconButton(onClick = { shiftMonth(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下个月")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "点日期看当天安排",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            item {
                MonthGridCard(
                    model = model,
                    sundayFirst = s.sundayFirst,
                    today = today,
                    selected = selected,
                    onSelect = { selected = it }
                )
            }
            item {
                DayDetailCard(
                    st = st,
                    date = selected,
                    courses = model.courses[selected].orEmpty()
                )
            }
        }
    }
}

@Composable
private fun MonthGridCard(
    model: MonthModel,
    sundayFirst: Boolean,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val labels = if (sundayFirst) listOf("日", "一", "二", "三", "四", "五", "六")
    else listOf("一", "二", "三", "四", "五", "六", "日")
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }
        }
        model.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        selected = date != null && date == selected,
                        today = date != null && date == today,
                        courses = date?.let { model.courses[it] }.orEmpty(),
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    today: Boolean,
    courses: List<ShownCourse>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .padding(2.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> primary.copy(alpha = 0.16f)
                    today -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = date != null) { date?.let(onSelect) }
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        if (date != null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 11.sp,
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    courses.take(3).forEach { item ->
                        Box(
                            Modifier
                                .width(10.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(courseColor(item.course.color))
                        )
                    }
                }
                if (courses.size > 3) {
                    Text(
                        "+${courses.size - 3}",
                        fontSize = 7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailCard(st: AppState, date: LocalDate, courses: List<ShownCourse>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val startDate = Repo.startDate(st)
    val week = startDate?.let { CourseLogic.weekOf(it, date) }
    val dayName = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]
    // 当天课程挂着的待办：直接点名「哪门课的什么事」
    val courseIds = courses.map { it.course.id }.toSet()
    val todos = st.todos.filter { !it.done && it.courseId in courseIds }
    // 当天截止的独立待办（DDL 清单）
    val dueTodos = st.todos.filter { !it.done && it.dueDate > 0L && it.dueDate == date.toEpochDay() }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${date.monthValue}月${date.dayOfMonth}日",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(dayName, style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.weight(1f))
            if (week != null && week in 1..st.tableSettings().maxWeek) {
                Text(
                    "第 $week 周",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (courses.isEmpty()) "这天没有课" else "${courses.size} 节课",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
        if (courses.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            courses.forEachIndexed { index, item ->
                DayCourseRow(item, st.slots)
                if (index != courses.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
        if (dueTodos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "当天截止",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(2.dp))
            dueTodos.forEach { todo ->
                Row(
                    Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        (if (todo.dueMinute >= 0)
                            "%02d:%02d ".format(todo.dueMinute / 60, todo.dueMinute % 60) else "") + todo.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
        if (todos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "课程待办",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = muted
            )
            Spacer(Modifier.height(2.dp))
            todos.forEach { todo ->
                val courseName = courses.firstOrNull { it.course.id == todo.courseId }?.course?.name
                Row(
                    Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        (courseName?.let { "【$it】" } ?: "") + todo.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCourseRow(item: ShownCourse, slots: List<TimeSlot>) {
    val d = item.detail
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val range = CourseLogic.courseRange(d, slots)
    val time = range?.let { "${it.first}–${it.second}" }
        ?: "第 ${d.startNode}-${d.startNode + d.step.coerceAtLeast(1) - 1} 节"
    val place = listOf(d.room, d.teacher).filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(courseColor(item.course.color))
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                item.course.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(time, place).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 按当前课表的开学日期与星期顺序，把一个月排成 7 列的周网格 */
private fun buildMonth(st: AppState, month: YearMonth): MonthModel {
    val s = st.tableSettings()
    val start = Repo.startDate(st)
    val firstDow = if (s.sundayFirst) 7 else 1
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDow + 7) % 7
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val gridStart = first.minusDays(offset.toLong())
    val weeks = (0 until rows).map { r ->
        (0 until 7).map { c ->
            val date = gridStart.plusDays((r * 7 + c).toLong())
            if (YearMonth.from(date) == month) date else null
        }
    }

    val maxWeek = s.maxWeek.coerceAtLeast(1)
    val courses = HashMap<LocalDate, List<ShownCourse>>()
    for (date in weeks.flatten().filterNotNull()) {
        val rawWeek = start?.let { CourseLogic.weekOf(it, date) }
        // 学期之外的日期不排课；没设开学日期时退回「当前周」，与今日页保持一致
        courses[date] = if (rawWeek != null && rawWeek !in 1..maxWeek) {
            emptyList()
        } else {
            val week = (rawWeek ?: s.currentWeek).coerceIn(1, maxWeek)
            Repo.displayDayCourses(st, date, week)
                .filterNot { it.isOther }
                .sortedBy { it.detail.startNode }
        }
    }
    return MonthModel(weeks, courses)
}
