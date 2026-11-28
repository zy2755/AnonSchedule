package com.anon.schedule.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 把 .ics 日历文件解析回课程安排（[IcsExport] 的反向操作）。
 *
 * 思路和导出对称：把每个 VEVENT（含 RRULE 展开后的每一次）落到「星期 + 节次 + 周次」上，
 * 再按「课程名 / 星期 / 节次 / 老师 / 地点」归并成一条条 [Parsed]。
 *
 * 几个宽容处理：
 * - 时区：带 Z 的按 UTC 转本地；带 TZID / 浮动时间按本地时间直接用；
 * - 全天事件（VALUE=DATE）没有具体钟点，排不进课表，直接跳过；
 * - 起止时间和本机作息不完全一致时，找最接近的节次（误差 30 分钟内）；
 * - RRULE 只处理最常见的 FREQ=WEEKLY（INTERVAL / COUNT / UNTIL / EXDATE）。
 */
object IcsImport {

    /** 解析后的一条课程安排（字段含义与 [DetailBean] 一致） */
    data class Parsed(
        val name: String,
        val day: Int,
        val startNode: Int,
        val step: Int,
        val startWeek: Int,
        val endWeek: Int,
        val type: Int,
        val teacher: String,
        val room: String
    )

    /**
     * @return 解析结果；开学日期没填、文件里没有可用的课程时返回 failure，message 可直接展示给用户
     */
    fun parse(text: String, st: AppState, tableId: Long = st.currentTableId): Result<List<Parsed>> = runCatching {
        val settings = st.tableSettings(tableId)
        val start = settings.startDate?.let { LocalDate.ofEpochDay(it) }
            ?: error("先在「课表设置」里填好开学日期，才能把日历事件排到周次")
        val maxWeek = settings.maxWeek.coerceAtLeast(1)
        val slots = st.slots.mapNotNull { slot ->
            val from = parseClock(slot.start) ?: return@mapNotNull null
            val to = parseClock(slot.end) ?: from.plusMinutes(45)
            Slot(from, to)
        }
        require(slots.isNotEmpty()) { "上课时间表是空的，先在设置里补一下节次时间" }

        val groups = LinkedHashMap<Key, MutableSet<Int>>()
        for (event in events(text)) {
            val name = event.summary.trim()
            val begin = event.start ?: continue
            if (name.isBlank()) continue
            val node = nodeOf(slots, begin.toLocalTime()) ?: continue
            val step = stepOf(slots, node, event.end?.toLocalTime())
            val teacher = TeacherRegex.find(event.description)?.groupValues?.get(1)?.trim().orEmpty()
            val room = event.location.trim()
            val key = Key(name, begin.dayOfWeek.value, node, step, teacher, room)
            val weeks = groups.getOrPut(key) { LinkedHashSet() }
            for (occurrence in occurrences(event)) {
                if (event.exDates.contains(occurrence.toLocalDate())) continue
                val week = CourseLogic.weekOf(start, occurrence.toLocalDate())
                if (week in 1..maxWeek) weeks.add(week)
            }
        }

        val result = ArrayList<Parsed>()
        for ((key, weeks) in groups) {
            if (weeks.isEmpty()) continue
            for ((from, to, type) in compressWeeks(weeks.sorted())) {
                result += Parsed(
                    name = key.name,
                    day = key.day,
                    // node 是 slots 的下标（0 起），课表里的节次从 1 开始
                    startNode = key.node + 1,
                    step = key.step,
                    startWeek = from,
                    endWeek = to,
                    type = type,
                    teacher = key.teacher,
                    room = key.room
                )
            }
        }
        result.sortWith(compareBy({ it.day }, { it.startNode }, { it.name }))
        require(result.isNotEmpty()) {
            "没有解析到能排进课表的课程：检查文件里是否有带具体时间的课程事件，以及开学日期是否正确"
        }
        result
    }

    // ---------- 内部结构 ----------

    private data class Slot(val start: LocalTime, val end: LocalTime)

    private data class Key(
        val name: String,
        val day: Int,
        val node: Int,
        val step: Int,
        val teacher: String,
        val room: String
    )

    private class Event {
        var summary = ""
        var location = ""
        var description = ""
        var start: LocalDateTime? = null
        var end: LocalDateTime? = null
        var rrule = ""
        var exDates = HashSet<LocalDate>()
    }

    private val TeacherRegex = Regex("""教师[：:]\s*([^·\n]+)""")
    private val StampRegex = Regex("""(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?)?(Z?)""")

    // ---------- iCalendar 解析 ----------

    /** 折行还原：RFC 5545 里超长行会以「换行 + 空格」续到下一行 */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            if (out.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                out[out.size - 1] = out.last() + line.substring(1)
            } else {
                out.add(line)
            }
        }
        return out
    }

    private fun events(text: String): List<Event> {
        val result = ArrayList<Event>()
        var cur: Event? = null
        // VALARM 里也有 DESCRIPTION（提醒文案），不能让它覆盖事件本身的描述
        var inAlarm = false
        for (line in unfold(text)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    cur = Event()
                    inAlarm = false
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    cur?.let { result += it }
                    cur = null
                    inAlarm = false
                }
                cur != null && line.equals("BEGIN:VALARM", ignoreCase = true) -> inAlarm = true
                cur != null && line.equals("END:VALARM", ignoreCase = true) -> inAlarm = false
                cur != null && !inAlarm -> readProperty(line, cur!!)
            }
        }
        return result
    }

    /** 一行 "NAME;PARAM=...:VALUE"，只关心 VEVENT 里用得到的几个字段 */
    private fun readProperty(line: String, event: Event) {
        val sep = line.indexOf(':')
        if (sep <= 0) return
        val head = line.substring(0, sep)
        val value = line.substring(sep + 1)
        val name = head.substringBefore(';').uppercase()
        when (name) {
            "SUMMARY" -> event.summary = unescapeText(value)
            "LOCATION" -> event.location = unescapeText(value)
            "DESCRIPTION" -> event.description = unescapeText(value)
            "DTSTART" -> event.start = parseStamp(value)
            "DTEND" -> event.end = parseStamp(value)
            "RRULE" -> event.rrule = value
            "EXDATE" -> value.split(',').forEach { part ->
                parseStamp(part)?.toLocalDate()?.let { event.exDates.add(it) }
            }
        }
    }

    private fun unescapeText(text: String): String = text
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    /** 日期时间 → 本地时间；全天事件（只有日期没有 T）返回 null */
    private fun parseStamp(value: String): LocalDateTime? {
        val m = StampRegex.find(value.trim()) ?: return null
        val g = m.groupValues
        if (g[4].isEmpty()) return null
        return runCatching {
            val dt = LocalDateTime.of(
                g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0
            )
            if (g[7] == "Z") {
                dt.atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
            } else {
                dt
            }
        }.getOrNull()
    }

    private fun parseClock(text: String): LocalTime? = runCatching {
        LocalTime.parse(text.trim())
    }.getOrNull()

    // ---------- RRULE 展开 ----------

    private data class WeeklyRule(val interval: Int, val count: Int?, val until: LocalDate?)

    private fun weeklyRule(text: String): WeeklyRule? {
        if (text.isBlank()) return null
        val parts = text.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).uppercase() to part.substring(i + 1)
        }.toMap()
        if (!parts["FREQ"].equals("WEEKLY", ignoreCase = true)) return null
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
        val until = parts["UNTIL"]?.let { raw ->
            val stamp = parseStamp(raw)
            if (stamp != null) stamp.toLocalDate()
            else runCatching {
                LocalDate.of(
                    raw.take(4).toInt(), raw.substring(4, 6).toInt(), raw.substring(6, 8).toInt()
                )
            }.getOrNull()
        }
        return WeeklyRule(interval, count, until)
    }

    /** 一次事件实际发生的所有时刻（非重复事件只有 DTSTART 本身） */
    private fun occurrences(event: Event): List<LocalDateTime> {
        val start = event.start ?: return emptyList()
        val rule = weeklyRule(event.rrule) ?: return listOf(start)
        val out = ArrayList<LocalDateTime>()
        var cur = start
        var guard = 0
        while (guard < 300) {
            if (rule.until != null && cur.toLocalDate().isAfter(rule.until)) break
            if (rule.count != null && out.size >= rule.count) break
            out.add(cur)
            cur = cur.plusWeeks(rule.interval.toLong())
            guard++
        }
        return out
    }

    // ---------- 时间 → 节次 ----------

    /** 找到覆盖该时刻的节次；没有则找 30 分钟内最接近的 */
    private fun nodeOf(slots: List<Slot>, time: LocalTime): Int? {
        slots.forEachIndexed { i, slot ->
            if (!time.isBefore(slot.start) && time.isBefore(slot.end)) return i
        }
        var best = -1
        var bestDiff = Long.MAX_VALUE
        slots.forEachIndexed { i, slot ->
            val diff = abs(ChronoUnit.MINUTES.between(time, slot.start))
            if (diff < bestDiff) {
                bestDiff = diff
                best = i
            }
        }
        return if (best >= 0 && bestDiff <= 30) best else null
    }

    /** 从起始节次往后数，覆盖到结束时间的节数 */
    private fun stepOf(slots: List<Slot>, node: Int, end: LocalTime?): Int {
        if (end == null) return 1
        var step = 1
        for (i in node until slots.size) {
            if (slots[i].start.isBefore(end)) step = i - node + 1 else break
        }
        return step.coerceIn(1, 6)
    }

    // ---------- 周集合 → 起止周 + 单双周 ----------

    /**
     * 周集合压缩：连续 → 每周；同奇偶隔周 → 单周/双周。
     * 和 CSV 导入的压缩规则保持一致，避免同一种课表导两次得到不同结构。
     */
    private fun compressWeeks(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        val spans = ArrayList<Triple<Int, Int, Int>>()
        var i = 0
        while (i < weeks.size) {
            val start = weeks[i]
            if (i + 1 < weeks.size && weeks[i + 1] - start == 2) {
                var j = i + 1
                while (j < weeks.size && weeks[j] - weeks[j - 1] == 2) j++
                spans += Triple(start, weeks[j - 1], if (start % 2 == 1) 1 else 2)
                i = j
            } else if (i + 1 < weeks.size && weeks[i + 1] - start == 1) {
                var j = i + 1
                while (j < weeks.size && weeks[j] - weeks[j - 1] == 1) j++
                spans += Triple(start, weeks[j - 1], 0)
                i = j
            } else {
                spans += Triple(start, start, 0)
                i++
            }
        }
        return spans
    }
}
