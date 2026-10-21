package com.anon.schedule.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.anon.schedule.data.AppState
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.courseColor
import java.time.LocalDate
import java.time.LocalTime

/** 小组件展示用的一条课程：把节次、时间、颜色等提前解析成可直接显示的文本 */
data class WidgetCourse(
    val name: String,
    val timeText: String,      // "13:30-15:05"
    val startText: String,     // "13:30"
    val endText: String,       // "15:05"
    val startNode: Int,        // 起始节次
    val endNode: Int,          // 结束节次
    val roomText: String,
    val teacherText: String,
    val colorArgb: Int,
    val start: LocalTime?,
    val end: LocalTime?
)

/**
 * 小组件的取数逻辑。小组件进程不一定经过 MainActivity，
 * 所以每次都要先确保 Repo 已初始化，再去读它内存里的 AppState。
 */
object WidgetData {

    fun load(ctx: Context): AppState {
        Repo.init(ctx)
        return Repo.state.value
    }

    /** 今天要展示的课程 */
    fun today(st: AppState): List<WidgetCourse> = day(st, LocalDate.now())

    /** 明天要展示的课程（「今日·明日」小组件的右列用） */
    fun tomorrow(st: AppState): List<WidgetCourse> = day(st, LocalDate.now().plusDays(1))

    /** 指定某天的课程：与「今日课表」页口径一致（只取本周、按节次排序） */
    fun day(st: AppState, date: LocalDate): List<WidgetCourse> {
        val week = Repo.weekOf(st, date) ?: st.tableSettings().currentWeek
        return Repo.dayCourses(st, date, week)
            .filterNot { it.isOther }
            .sortedBy { it.detail.startNode }
            .map { shown ->
                val d = shown.detail
                val first = st.slots.getOrNull(d.startNode - 1)
                val last = st.slots.getOrNull(d.startNode + d.step - 2) ?: first
                WidgetCourse(
                    name = shown.course.name,
                    timeText = if (first != null) "${first.start}-${last?.end ?: first.end}" else "",
                    startText = first?.start ?: "",
                    endText = (last ?: first)?.end ?: "",
                    startNode = d.startNode,
                    endNode = d.startNode + d.step - 1,
                    roomText = d.room,
                    teacherText = d.teacher,
                    colorArgb = courseColor(shown.course.color).toArgb(),
                    start = first?.let { parseTime(it.start) },
                    end = (last ?: first)?.let { parseTime(it.end) }
                )
            }
    }

    /** 正在上 / 下一节要上的课；今天已全部上完则返回 null */
    fun currentOrNext(list: List<WidgetCourse>, now: LocalTime = LocalTime.now()): WidgetCourse? =
        list.firstOrNull { it.end == null || !it.end.isBefore(now) }

    /** 还没上完的课（含正在上的），按时间从前到后 */
    fun remaining(list: List<WidgetCourse>, now: LocalTime = LocalTime.now()): List<WidgetCourse> =
        list.filter { it.end == null || !it.end.isBefore(now) }

    /** 该课此刻是「进行中」还是还没开始的「下一节」 */
    fun statusOf(c: WidgetCourse, now: LocalTime = LocalTime.now()): String =
        if (c.start != null && !now.isBefore(c.start) && (c.end == null || !now.isAfter(c.end))) "进行中" else "下一节"

    /** 把位置/教师拼成一行副信息 */
    fun subText(c: WidgetCourse): String =
        listOf(c.roomText, c.teacherText).filter { it.isNotBlank() }.joinToString(" · ")

    private fun parseTime(text: String): LocalTime? =
        runCatching { LocalTime.parse(text.trim()) }.getOrNull()
}
