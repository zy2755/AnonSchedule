package com.anon.schedule.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 把整学期课表导出成 iCalendar（.ics）。
 *
 * 用「逐日展开」而不是 RRULE：单双周、起始周、调课、停课、跨天加课这些规则
 * 都已经在 [CourseLogic] 里算好了，逐天问一遍就能保证导出的日程和 App 里看到的一致，
 * 也省得和各家的 RRULE 兼容性较劲。一学期最多几百条事件，体积可以忽略。
 *
 * 时间用「浮动时间」（不带 Z、不带 TZID），这样导进任何设备都按当地时间显示 08:40，
 * 不会因为时区换算成别的钟点。
 */
object IcsExport {

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * @return .ics 文本；没设置开学日期时返回 null（没有日期就没法排到日历上）
     */
    fun build(
        st: AppState,
        tableId: Long = st.currentTableId,
        now: LocalDateTime = LocalDateTime.now()
    ): String? {
        val s = st.tableSettings(tableId)
        val start = s.startDate?.let { LocalDate.ofEpochDay(it) } ?: return null
        val tableName = st.tables.firstOrNull { it.id == tableId }?.name ?: "我的课表"
        val stamp = STAMP.format(now)

        val sb = StringBuilder()
        line(sb, "BEGIN:VCALENDAR")
        line(sb, "VERSION:2.0")
        line(sb, "PRODID:-//AnonSchedule//Timetable//CN")
        line(sb, "CALSCALE:GREGORIAN")
        line(sb, "METHOD:PUBLISH")
        line(sb, "X-WR-CALNAME:${escape(tableName)}")

        for (week in 1..s.maxWeek.coerceAtLeast(1)) {
            for (day in 1..7) {
                val date = CourseLogic.dateOfWeekDay(start, week, day)
                // showOtherWeek = false：只导出这一周真正要上的课，非本周课程不进日历
                val items = CourseLogic.displayDayCourses(
                    st = st, date = date, week = week, tableId = tableId,
                    startDate = start, showOtherWeek = false
                )
                for (item in items.sortedBy { it.detail.startNode }) {
                    val (from, to) = CourseLogic.courseRange(item.detail, st.slots) ?: continue
                    val begin = date.atTime(from)
                    val end = date.atTime(to).let { if (it.isAfter(begin)) it else begin.plusMinutes(45) }
                    val d = item.detail

                    line(sb, "BEGIN:VEVENT")
                    // UID 带日期：重复导入时日历会覆盖同一条，而不是堆出一份重复
                    line(sb, "UID:${tableId}-${d.id}-${DAY.format(date)}@anon.schedule")
                    line(sb, "DTSTAMP:${stamp}Z")
                    line(sb, "DTSTART:${STAMP.format(begin)}")
                    line(sb, "DTEND:${STAMP.format(end)}")
                    line(sb, "SUMMARY:${escape(item.course.name)}")
                    if (d.room.isNotBlank()) line(sb, "LOCATION:${escape(d.room)}")
                    val desc = buildList {
                        add("第 ${d.startNode}-${d.startNode + d.step.coerceAtLeast(1) - 1} 节")
                        if (d.teacher.isNotBlank()) add("教师：${d.teacher}")
                        if (item.record?.type == 1) add("临时加课")
                        if (item.record?.type == 0) add("调课")
                    }.joinToString(" · ")
                    line(sb, "DESCRIPTION:${escape(desc)}")
                    line(sb, "BEGIN:VALARM")
                    line(sb, "ACTION:DISPLAY")
                    line(sb, "DESCRIPTION:${escape(item.course.name)}")
                    line(sb, "TRIGGER:-PT${s.reminderLeadMinutes.coerceAtLeast(1)}M")
                    line(sb, "END:VALARM")
                    line(sb, "END:VEVENT")
                }
            }
        }

        line(sb, "END:VCALENDAR")
        return sb.toString()
    }

    /** 课程在整学期里一共上多少次（给导出前的提示用，也方便单测） */
    fun occurrenceCount(st: AppState, tableId: Long = st.currentTableId): Int {
        val s = st.tableSettings(tableId)
        val start = s.startDate?.let { LocalDate.ofEpochDay(it) } ?: return 0
        var n = 0
        for (week in 1..s.maxWeek.coerceAtLeast(1)) {
            for (day in 1..7) {
                val date = CourseLogic.dateOfWeekDay(start, week, day)
                n += CourseLogic.displayDayCourses(st, date, week, tableId, start, false)
                    .count { CourseLogic.courseRange(it.detail, st.slots) != null }
            }
        }
        return n
    }

    // ---------- iCalendar 文本规则 ----------

    /** 文本值转义：反斜杠、分号、逗号、换行 */
    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /**
     * 按 RFC 5545 折行：一行最多 75 字节（UTF-8），超出部分折到下一行并以一个空格开头。
     * 中文字符占 3 字节，所以不能按字符数切。
     */
    private fun line(sb: StringBuilder, text: String) {
        var bytes = 0
        for (ch in text) {
            val w = utf8Len(ch)
            if (bytes + w > 75) {
                sb.append("\r\n ")
                bytes = 1
            }
            sb.append(ch)
            bytes += w
        }
        sb.append("\r\n")
    }

    private fun utf8Len(ch: Char): Int = when {
        ch.code < 0x80 -> 1
        ch.code < 0x800 -> 2
        else -> 3
    }
}
