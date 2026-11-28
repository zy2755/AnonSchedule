package com.anon.schedule.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.CourseBean
import com.anon.schedule.data.CourseLogic
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.DetailBean
import com.anon.schedule.data.Repo
import com.anon.schedule.data.ShownCourse
import com.anon.schedule.data.TimeSlot
import com.anon.schedule.data.TodoLogic
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.courseColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(onOpenTodos: () -> Unit = {}) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings()
    val today = LocalDate.now()
    val week = Repo.weekOf(st, today) ?: s.currentWeek
    // 今日课表只展示本周课程：过滤掉非本周（isOther）的卡片
    val dayCourses = Repo.displayDayCourses(st, today, week)
        .filterNot { it.isOther }
        .sortedBy { it.detail.startNode }

    // 当前时间：每 30 秒刷新一次，驱动主角卡倒计时与时间轴状态
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000L)
        }
    }

    val overview = CourseLogic.todayOverview(dayCourses, st.slots, now)

    // 本周概览：本周节数、最忙的一天、学期累计；数据没变时不重算
    val weekStats = remember(st, week) {
        CourseLogic.weekStats(
            st = st,
            week = week,
            tableId = st.currentTableId,
            startDate = Repo.startDate(st),
            maxWeek = s.maxWeek
        )
    }

    // 考试倒计时：有学习通同步来的考试才出现
    val exams = remember(st.todos, today) { TodoLogic.upcomingExams(st.todos, today) }

    // 今天的课上完了（或本来就没课）：主角卡给一条"明天第一节"的提示
    val tomorrowFirst = if (
        overview.state == CourseLogic.TodayState.DONE ||
        overview.state == CourseLogic.TodayState.EMPTY
    ) {
        val tomorrow = today.plusDays(1)
        val tomorrowWeek = Repo.weekOf(st, tomorrow) ?: (week + 1)
        Repo.displayDayCourses(st, tomorrow, tomorrowWeek)
            .filterNot { it.isOther }
            .minByOrNull { it.detail.startNode }
    } else null

    val formatter = DateTimeFormatter.ofPattern("M月d日")

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 2.dp)) {
                Text(
                    "今日课表",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${today.format(formatter)} · ${"周" + "一二三四五六日"[today.dayOfWeek.value - 1]}" +
                        if (s.startDate != null) " · 第 $week 周" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { TodayHero(overview, tomorrowFirst, st.slots) }

        item { WeekOverviewCard(weekStats) }

        // 考试倒计时：有学习通同步来的考试才出现
        if (exams.isNotEmpty()) {
            item {
                ExamCountdownCard(
                    exams = exams,
                    today = today,
                    courses = st.courses,
                    onOpen = onOpenTodos
                )
            }
        }

        item {
            val preview = TodoLogic.preview(st.todos, today, now)
            TodoCard(
                items = preview,
                pending = TodoLogic.pendingCount(st.todos),
                today = today,
                onOpen = onOpenTodos
            )
        }

        if (dayCourses.isNotEmpty()) {
            item {
                Text(
                    "今天的安排",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
            item {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    dayCourses.forEachIndexed { index, shown ->
                        val state = CourseLogic.stateOf(shown, st.slots, now)
                        TimelineRow(
                            shown = shown,
                            slots = st.slots,
                            state = state,
                            nextId = overview.next?.detail?.id,
                            minutesUntilNext = overview.minutesUntilNext
                        )
                        if (index != dayCourses.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(start = 30.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.size(96.dp)) }
    }
}

// ---------- 主角卡 ----------

@Composable
private fun TodayHero(
    overview: CourseLogic.TodayOverview,
    tomorrowFirst: ShownCourse?,
    slots: List<TimeSlot>
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    GlassCard(Modifier.fillMaxWidth()) {
        when (overview.state) {
            CourseLogic.TodayState.NOW -> {
                val item = overview.current ?: return@GlassCard
                val accent = courseColor(item.course.color)
                HeroLabel("正在上课", accent)
                Text(
                    item.course.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                HeroMeta(item.detail, slots, muted)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "还剩 ${overview.minutesLeft} 分钟",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        nodeRange(item.detail) + "节",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
                Spacer(Modifier.height(8.dp))
                HeroProgress(overview.progress, accent)
            }

            CourseLogic.TodayState.NEXT -> {
                val item = overview.next ?: return@GlassCard
                val accent = courseColor(item.course.color)
                HeroLabel("下一节", accent)
                Text(
                    item.course.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                HeroMeta(item.detail, slots, muted)
                Spacer(Modifier.height(14.dp))
                Text(
                    "还有 ${overview.minutesUntilNext} 分钟上课",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            CourseLogic.TodayState.DONE,
            CourseLogic.TodayState.EMPTY -> {
                HeroLabel(
                    if (overview.state == CourseLogic.TodayState.DONE) "今天已结束" else "今天没课",
                    MaterialTheme.colorScheme.secondary
                )
                Text(
                    if (overview.state == CourseLogic.TodayState.DONE) "今天的课上完了" else "今天没有课～去玩吧！",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                if (tomorrowFirst != null) {
                    val firstStart = CourseLogic.courseRange(tomorrowFirst.detail, slots)?.first
                    Text(
                        "明天第一节：${tomorrowFirst.course.name}" +
                            (firstStart?.let { "  ${it}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                } else {
                    Text(
                        "记得在设置里确认开学日期，周次和日期才对得上",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }
        }
    }
}

// ---------- 本周概览 ----------

@Composable
private fun WeekOverviewCard(stats: CourseLogic.WeekStats) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    // 并列最忙时不硬挑一天：显示「5 天 / 各 8 节」
    val busiestValue = when {
        stats.busiestDays.isEmpty() -> "—"
        stats.busiestDays.size > 1 -> "${stats.busiestDays.size} 天"
        else -> "周" + "一二三四五六日"[stats.busiestDays.first() - 1]
    }
    val busiestUnit = when {
        stats.busiestDays.isEmpty() -> ""
        stats.busiestDays.size > 1 -> "各 ${stats.busiestDayNodes} 节"
        else -> "${stats.busiestDayNodes} 节"
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "本周概览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "第 ${stats.week} / ${stats.maxWeek} 周",
                style = MaterialTheme.typography.labelMedium,
                color = muted
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell(label = "本周", value = "${stats.nodeCount}", unit = "节", modifier = Modifier.weight(1f))
            StatCell(
                label = "最忙",
                value = busiestValue,
                unit = busiestUnit,
                modifier = Modifier.weight(1f)
            )
            StatCell(label = "本学期", value = "${stats.termNodes}", unit = "节", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("学期进度", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((stats.week.toFloat() / stats.maxWeek).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(accent)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${stats.week * 100 / stats.maxWeek}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

// ---------- 考试倒计时 ----------

/** 最近一场考试的大号倒计时；下面顺带预告第二场 */
@Composable
private fun ExamCountdownCard(
    exams: List<CourseTodo>,
    today: LocalDate,
    courses: List<CourseBean>,
    onOpen: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nearest = exams.first()
    val days = TodoLogic.daysUntil(nearest, today)
    val accent = if (days <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val due = LocalDate.ofEpochDay(nearest.dueDate)
    val timeText = if (nearest.dueMinute >= 0) {
        " %02d:%02d".format(nearest.dueMinute / 60, nearest.dueMinute % 60)
    } else ""
    val courseName = courses.firstOrNull { it.id == nearest.courseId }?.name
    val meta = listOfNotNull(courseName, "${due.monthValue}月${due.dayOfMonth}日$timeText")
        .joinToString(" · ")

    GlassCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "考试倒计时",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "共 ${exams.size} 场",
                style = MaterialTheme.typography.labelMedium,
                color = muted
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    nearest.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    days.toString(),
                    fontSize = 34.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    "天",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
            }
        }
        if (exams.size > 1) {
            val next = exams[1]
            val nextDue = LocalDate.ofEpochDay(next.dueDate)
            Spacer(Modifier.height(8.dp))
            Text(
                "下一场：${nextDue.monthValue}月${nextDue.dayOfMonth}日 · ${next.text}",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1
            )
        }
    }
}

// ---------- 待办卡片 ----------

/** 今日页的待办入口：最近 3 条未完成，点卡片进待办清单 */
@Composable
private fun TodoCard(
    items: List<CourseTodo>,
    pending: Int,
    today: LocalDate,
    onOpen: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    GlassCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "待办",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (pending > 0) "$pending 条未完成" else "全部完成",
                style = MaterialTheme.typography.labelMedium,
                color = if (pending > 0) accent else muted
            )
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                "还没有待办，点这里加一条 DDL 吧",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        } else {
            items.forEach { todo ->
                val overdue = TodoLogic.isOverdue(todo, today, LocalTime.now())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (overdue) MaterialTheme.colorScheme.error else accent)
                    )
                    Text(
                        todo.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    Text(
                        TodoLogic.dueLabel(todo, today),
                        fontSize = 11.sp,
                        color = if (overdue) MaterialTheme.colorScheme.error else muted
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                // 「5 天并列」这类长文本降一档字号，三等分宽度下不会被截断
                style = if (value.length > 3) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeroLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun HeroMeta(d: DetailBean, slots: List<TimeSlot>, muted: Color) {
    val range = CourseLogic.courseRange(d, slots)
    val time = range?.let { "${it.first}–${it.second}" } ?: "时间未设置"
    val place = listOf(d.room, d.teacher).filter { it.isNotBlank() }.joinToString(" · ")
    Text(
        if (place.isBlank()) time else "$time · $place",
        style = MaterialTheme.typography.bodyMedium,
        color = muted
    )
}

@Composable
private fun HeroProgress(progress: Float, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(accent.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(accent)
        )
    }
}

// ---------- 时间轴 ----------

@Composable
private fun TimelineRow(
    shown: ShownCourse,
    slots: List<TimeSlot>,
    state: CourseLogic.DayCourseState,
    nextId: Long?,
    minutesUntilNext: Int?
) {
    val d = shown.detail
    val accent = courseColor(shown.course.color)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val done = state == CourseLogic.DayCourseState.DONE
    val isNext = d.id == nextId
    val range = CourseLogic.courseRange(d, slots)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val dotColor = when {
        state == CourseLogic.DayCourseState.NOW -> accent
        done -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        else -> accent.copy(alpha = 0.5f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 左侧时间轴：竖线 + 节点圆点
        Box(
            Modifier
                .width(30.dp)
                .fillMaxHeight()
                .drawBehind {
                    val cx = size.width / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(cx, 20.dp.toPx()))
                }
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(
                range?.let { "${it.first}–${it.second}" } ?: "${d.startNode}-${d.startNode + d.step - 1}节",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (state == CourseLogic.DayCourseState.NOW) accent else muted
            )
            StrikeTitle(
                text = shown.course.name,
                struck = done,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) muted else MaterialTheme.colorScheme.onSurface
            )
            val place = listOf(d.room, d.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            if (place.isNotBlank()) {
                Text(place, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Column(
            Modifier.padding(top = 12.dp, bottom = 10.dp, start = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            when {
                state == CourseLogic.DayCourseState.NOW -> StatusPill("进行中", accent)
                isNext && minutesUntilNext != null -> {
                    Text(
                        "还有 $minutesUntilNext 分钟",
                        fontSize = 11.sp,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * 已上过的课程名：手绘一条比 TextDecoration 更明显的删除线。
 * TextDecoration 自带的线约等于字号的 1/10，小字号下几乎看不见。
 */
@Composable
private fun StrikeTitle(text: String, struck: Boolean, style: TextStyle, color: Color) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        onTextLayout = { layout = it },
        modifier = if (struck) Modifier.drawWithContent {
            drawContent()
            val lr = layout
            if (lr != null) {
                val stroke = 2.dp.toPx()
                for (i in 0 until lr.lineCount) {
                    val y = (lr.getLineTop(i) + lr.getLineBottom(i)) / 2f
                    drawLine(
                        color = color,
                        start = Offset(lr.getLineLeft(i), y),
                        end = Offset(lr.getLineRight(i), y),
                        strokeWidth = stroke
                    )
                }
            }
        } else Modifier
    )
}

private fun nodeRange(d: DetailBean): String = "${d.startNode}-${d.startNode + d.step - 1}"
