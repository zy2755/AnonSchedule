package com.anon.schedule.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 课表相关的纯逻辑：周次换算、课程有效性、临时变动（调课 / 停课 / 加课）叠加。
 *
 * 这里刻意不碰 Android、也不读 [Repo] 的内存状态，所有输入都从参数传入——
 * 课表最容易出错的正是这些规则（调课覆盖、单双周、跨周加课、非本周课程是否展示），
 * 抽成纯函数后可以在 JVM 上直接跑单元测试，改规则前先用测试兜住。
 */
object CourseLogic {

    /** 某日期属于第几周（第 1 周从 [start] 当天算起）；早于开学日按第 1 周处理 */
    fun weekOf(start: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, date)
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    /** 第 [week] 周的星期 [day]（1=周一 ... 7=周日）对应的日期 */
    fun dateOfWeekDay(start: LocalDate, week: Int, day: Int): LocalDate =
        start.plusDays(((week - 1) * 7 + (day - 1)).toLong())

    /** 该安排在第 [week] 周是否有效（周次范围 + 单双周） */
    fun validInWeek(d: DetailBean, week: Int): Boolean {
        if (week < d.startWeek || week > d.endWeek) return false
        return parityMatches(d.type, week)
    }

    /** 单双周匹配：0=每周 1=单周 2=双周 */
    fun parityMatches(parity: Int, week: Int): Boolean = when (parity) {
        1 -> week % 2 == 1
        2 -> week % 2 == 0
        else -> true
    }

    /**
     * 某一天要展示的课程卡片（含临时变动处理）。
     *
     * @param startDate     开学日期，用于推算"被调过来的课"原本在第几周；null 时按第 1 周处理
     * @param showOtherWeek 是否展示非本周课程
     */
    fun dayCourses(
        st: AppState,
        date: LocalDate,
        week: Int,
        tableId: Long,
        startDate: LocalDate?,
        showOtherWeek: Boolean
    ): List<ShownCourse> {
        val tableDetails = st.details.filter { it.tableId == tableId }
        val day = date.dayOfWeek.value // 1=周一 ... 7=周日

        var base = tableDetails.filter { it.day == day }
        val shown = ArrayList<ShownCourse>()

        // 调课：fromDate 当天的课整体挪走 → 从当天移除，并在 toDate 当天出现
        val movedOut = st.records.filter { it.type == 0 && it.fromDate == date.toEpochDay() }
        val movedIn = st.records.filter { it.type == 0 && it.toDate == date.toEpochDay() }
        // 停课
        val suspended = st.records.filter { it.type == 2 && it.fromDate == date.toEpochDay() }
        // 临时加课：支持跨多天（endDate）与单双周过滤，旧数据（endDate=0, parity=0）行为不变
        val today = date.toEpochDay()
        val added = st.records.filter { r ->
            r.type == 1 && today >= r.fromDate &&
                    today <= (if (r.endDate > 0L) r.endDate else r.fromDate) &&
                    parityMatches(r.parity, week)
        }

        // 调课：fromDate 当天被挪走的课 → 从当天移除（name 为空 = 当天全部课程整体挪走）
        for (m in movedOut) {
            base = if (m.name.isEmpty()) emptyList()
            else base.filter { courseOf(st, it)?.name != m.name }
        }

        for (s in suspended) {
            base = if (s.name.isEmpty()) emptyList()
            else base.filter { courseOf(st, it)?.name != s.name }
        }

        for (d in base) {
            val c = courseOf(st, d) ?: continue
            val other = !validInWeek(d, week)
            // 非本周课程：设置里关掉就不显示；另外"已经上完的"（本周已超过它的结束周）也不再显示
            if (other && (!showOtherWeek || d.endWeek < week)) continue
            shown += ShownCourse(d, c, other, null)
        }

        // 被挪到今天的课：取"原日期那天"本该上的课
        for (r in movedIn) {
            val from = LocalDate.ofEpochDay(r.fromDate)
            val fromWeek = startDate?.let { weekOf(it, from) } ?: 1
            for (d in tableDetails.filter { it.day == from.dayOfWeek.value && validInWeek(it, fromWeek) }) {
                val c = courseOf(st, d) ?: continue
                // 指定课程名的调课只挪这一门，并采用目标节次
                if (r.name.isNotEmpty() && c.name != r.name) continue
                val nd = if (r.name.isNotEmpty() && r.startNode >= 1) {
                    d.copy(startNode = r.startNode, step = r.step)
                } else d
                shown += ShownCourse(nd, c, false, r)
            }
        }

        // 临时加课
        for (r in added) {
            val ghost = CourseBean(id = -r.id, name = r.name, color = 3, tableId = tableId)
            val d = DetailBean(
                id = -r.id, courseId = ghost.id, day = day, room = r.room,
                teacher = r.teacher, startNode = r.startNode, step = r.step
            )
            shown += ShownCourse(d, ghost, false, r)
        }

        return shown
    }

    /**
     * 展示用的一天课程：在 [dayCourses] 基础上按课表网格的叠放规则去掉被覆盖的原课——
     * 调课 / 加课卡优先占位，同一节次上的当天原课不再展示（撤销变动后自动恢复）。
     */
    fun displayDayCourses(
        st: AppState,
        date: LocalDate,
        week: Int,
        tableId: Long,
        startDate: LocalDate?,
        showOtherWeek: Boolean
    ): List<ShownCourse> = dropCovered(dayCourses(st, date, week, tableId, startDate, showOtherWeek))

    /** 去掉被调课 / 加课卡占住节次的原课 */
    fun dropCovered(list: List<ShownCourse>): List<ShownCourse> {
        val used = HashSet<Int>()
        for (item in list) {
            if (item.record == null || item.isOther) continue
            val d = item.detail
            if (d.startNode < 1) continue
            for (n in d.startNode until d.startNode + d.step.coerceAtLeast(1)) used.add(n)
        }
        if (used.isEmpty()) return list
        return list.filter { item ->
            if (item.record != null || item.isOther) true
            else {
                val d = item.detail
                d.startNode < 1 || (d.startNode until d.startNode + d.step.coerceAtLeast(1)).none { it in used }
            }
        }
    }

    private fun courseOf(st: AppState, detail: DetailBean): CourseBean? =
        st.courses.firstOrNull { it.id == detail.courseId }

    // ---------- 冲突检测 ----------

    /**
     * 候选安排在学期内会撞上的课：同一天、节次区间有交集、而且两边至少有一周同时生效。
     *
     * 只比较正式课表（[DetailBean]），临时加课 / 调课记录属于"用户明知故犯"的操作，不在这里拦。
     *
     * @param ignoreDetailId 编辑或拖动时传入自己的 id，免得提示"和自己冲突"
     */
    fun conflictsOf(
        st: AppState,
        tableId: Long,
        day: Int,
        startNode: Int,
        step: Int,
        startWeek: Int = 1,
        endWeek: Int = Int.MAX_VALUE,
        type: Int = 0,
        ignoreDetailId: Long? = null
    ): List<DetailBean> {
        if (startNode < 1 || day !in 1..7) return emptyList()
        val cand = startNode until (startNode + step.coerceAtLeast(1))
        return st.details.filter { d ->
            d.tableId == tableId &&
                d.id != ignoreDetailId &&
                d.day == day &&
                d.startNode >= 1 &&
                (d.startNode until d.startNode + d.step.coerceAtLeast(1)).any { it in cand } &&
                weeksOverlap(d.startWeek, d.endWeek, d.type, startWeek, endWeek, type)
        }
    }

    /** 两段周次里是否存在同一周、且单双周都能生效的那一周 */
    private fun weeksOverlap(
        aStart: Int, aEnd: Int, aType: Int,
        bStart: Int, bEnd: Int, bType: Int
    ): Boolean {
        // 单周 vs 双周永远不会撞在一起
        if (aType != 0 && bType != 0 && aType != bType) return false
        val from = maxOf(aStart, bStart, 1)
        val to = minOf(aEnd, bEnd)
        if (from > to) return false
        val parity = if (aType != 0) aType else bType
        if (parity == 0) return true
        for (w in from..to) if (parityMatches(parity, w)) return true
        return false
    }

    // ---------- 今日视图 ----------

    /** 一节课在时间轴上的阶段 */
    enum class DayCourseState { DONE, NOW, LATER }

    /** 今日主角卡要展示的状态：正在上课 / 下一节 / 今天已结束 / 今天没课 */
    enum class TodayState { NOW, NEXT, DONE, EMPTY }

    /**
     * 一次安排的实际起止时间（第一节的开始 → 最后一节的结束）。
     * 节次数据缺失或格式不对时返回 null，调用方按"时间未知"处理。
     */
    fun courseRange(d: DetailBean, slots: List<TimeSlot>): Pair<LocalTime, LocalTime>? {
        val start = parseClock(slots.getOrNull(d.startNode - 1)?.start) ?: return null
        val endText = slots.getOrNull(d.startNode + d.step - 2)?.end
            ?: slots.getOrNull(d.startNode - 1)?.end
        val end = parseClock(endText) ?: return null
        return start to end
    }

    /** 单节课在今天的阶段：已结束 / 正在上 / 还没开始 */
    fun stateOf(item: ShownCourse, slots: List<TimeSlot>, now: LocalTime): DayCourseState {
        val (start, end) = courseRange(item.detail, slots) ?: return DayCourseState.LATER
        return when {
            !now.isBefore(end) -> DayCourseState.DONE
            !now.isBefore(start) -> DayCourseState.NOW
            else -> DayCourseState.LATER
        }
    }

    /** 今日主角卡的数据：当前课、下一节课、倒计时与进度 */
    data class TodayOverview(
        val state: TodayState,
        val current: ShownCourse? = null,
        val next: ShownCourse? = null,
        val minutesLeft: Int? = null,       // 当前这节还剩几分钟（不足一分钟按 1 分钟）
        val minutesUntilNext: Int? = null,  // 距下一节开始还有几分钟
        val progress: Float = 0f            // 当前这节已进行的比例 0..1
    )

    /**
     * 算今日主角卡该显示什么。
     *
     * @param items 今天要上的课，需按节次升序
     * @param now   当前时刻
     */
    fun todayOverview(items: List<ShownCourse>, slots: List<TimeSlot>, now: LocalTime): TodayOverview {
        // 正在上课优先：可能有多节课时间重叠，取第一节约
        for (item in items) {
            val range = courseRange(item.detail, slots) ?: continue
            val (start, end) = range
            if (!now.isBefore(start) && now.isBefore(end)) {
                val total = Duration.between(start, end).seconds.coerceAtLeast(1)
                val elapsed = Duration.between(start, now).seconds.coerceIn(0, total)
                return TodayOverview(
                    state = TodayState.NOW,
                    current = item,
                    minutesLeft = minutesCeil(now, end),
                    progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
                )
            }
        }
        // 否则取最近的一节还没开始的
        for (item in items) {
            val start = courseRange(item.detail, slots)?.first ?: continue
            if (now.isBefore(start)) {
                return TodayOverview(
                    state = TodayState.NEXT,
                    next = item,
                    minutesUntilNext = minutesCeil(now, start)
                )
            }
        }
        return TodayOverview(state = if (items.isEmpty()) TodayState.EMPTY else TodayState.DONE)
    }

    private fun minutesCeil(from: LocalTime, to: LocalTime): Int =
        ((Duration.between(from, to).seconds + 59) / 60).toInt().coerceAtLeast(1)

    private fun parseClock(text: String?): LocalTime? =
        text?.let { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
}
