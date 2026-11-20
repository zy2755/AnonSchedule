package com.anon.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 课表纯逻辑的回归测试。
 *
 * 覆盖的都是过去靠真机试出来的规则：周次换算、单双周、整日/单课调课、
 * 跨天加课、非本周课程是否展示，以及"被调课卡覆盖的原课不再展示"。
 */
class CourseLogicTest {

    private val table = 7L

    /** 2026-09-07 是周一，用作第一周周一 */
    private val start = LocalDate.of(2026, 9, 7)

    private fun courseA() = DetailBean(
        id = 1, courseId = 1, day = 1, startNode = 1, step = 2,
        startWeek = 1, endWeek = 16, type = 0, tableId = table
    )

    private fun courseB() = DetailBean(
        id = 2, courseId = 2, day = 1, startNode = 3, step = 2,
        startWeek = 1, endWeek = 16, type = 0, tableId = table
    )

    private fun state(
        details: List<DetailBean> = listOf(courseA(), courseB()),
        records: List<ChangeRecord> = emptyList()
    ) = AppState(
        tables = listOf(TableBean(id = table)),
        currentTableId = table,
        courses = listOf(
            CourseBean(id = 1, name = "高数", tableId = table),
            CourseBean(id = 2, name = "英语", tableId = table)
        ),
        details = details,
        records = records,
        settings = Settings(startDate = start.toEpochDay(), showOtherWeek = true)
    )

    private fun List<ShownCourse>.names() = map { it.course.name }

    // ---------- 周次 ----------

    @Test
    fun `开学当天是第一周 第七天仍属第一周 第八天进入第二周`() {
        assertEquals(1, CourseLogic.weekOf(start, start))
        assertEquals(1, CourseLogic.weekOf(start, start.plusDays(6)))
        assertEquals(2, CourseLogic.weekOf(start, start.plusDays(7)))
    }

    @Test
    fun `早于开学日的日期按第一周处理 不会出现第零周`() {
        assertEquals(1, CourseLogic.weekOf(start, start.minusDays(3)))
    }

    @Test
    fun `周次与星期可以往返换算`() {
        val date = CourseLogic.dateOfWeekDay(start, week = 3, day = 5)
        assertEquals(5, date.dayOfWeek.value)
        assertEquals(start.plusDays(18), date)
        assertEquals(3, CourseLogic.weekOf(start, date))
    }

    @Test
    fun `周次范围与单双周共同决定课程是否有效`() {
        val every = courseA()
        assertTrue(CourseLogic.validInWeek(every, 5))
        assertFalse(CourseLogic.validInWeek(every, 17))
        assertFalse(CourseLogic.validInWeek(every, 0))

        val odd = courseA().copy(startWeek = 1, endWeek = 20, type = 1)
        assertTrue(CourseLogic.validInWeek(odd, 3))
        assertFalse(CourseLogic.validInWeek(odd, 4))

        val even = courseA().copy(startWeek = 1, endWeek = 20, type = 2)
        assertTrue(CourseLogic.validInWeek(even, 4))
        assertFalse(CourseLogic.validInWeek(even, 3))
    }

    // ---------- 当日课程 ----------

    @Test
    fun `没有临时变动时按星期返回当天课程`() {
        val list = CourseLogic.dayCourses(state(), start, 1, table, start, true)
        assertEquals(listOf("高数", "英语"), list.names())
        assertTrue(list.all { it.record == null && !it.isOther })
    }

    @Test
    fun `整日调课把原日期清空 并挪到目标日期`() {
        val tuesday = start.plusDays(1)
        val st = state(
            records = listOf(
                ChangeRecord(id = 1, type = 0, fromDate = start.toEpochDay(), toDate = tuesday.toEpochDay())
            )
        )
        assertTrue(CourseLogic.dayCourses(st, start, 1, table, start, true).isEmpty())

        val moved = CourseLogic.dayCourses(st, tuesday, 1, table, start, true)
        assertEquals(listOf("高数", "英语"), moved.names())
        assertTrue(moved.all { it.record != null })
    }

    @Test
    fun `指定课程名的调课只挪这一门 并采用目标节次`() {
        val tuesday = start.plusDays(1)
        val st = state(
            records = listOf(
                ChangeRecord(
                    id = 1, type = 0, fromDate = start.toEpochDay(), toDate = tuesday.toEpochDay(),
                    name = "高数", startNode = 5, step = 2
                )
            )
        )
        // 原日期只少了高数，英语还在
        assertEquals(listOf("英语"), CourseLogic.dayCourses(st, start, 1, table, start, true).names())

        val moved = CourseLogic.dayCourses(st, tuesday, 1, table, start, true)
        assertEquals(listOf("高数"), moved.names())
        assertEquals(5, moved.first().detail.startNode)
        assertEquals(2, moved.first().detail.step)
    }

    @Test
    fun `停课按课程名过滤 其他课照常显示`() {
        val st = state(
            records = listOf(
                ChangeRecord(id = 1, type = 2, fromDate = start.toEpochDay(), name = "英语")
            )
        )
        assertEquals(listOf("高数"), CourseLogic.dayCourses(st, start, 1, table, start, true).names())
    }

    @Test
    fun `跨天加课按单双周生效`() {
        val st = state(
            records = listOf(
                ChangeRecord(
                    id = 1, type = 1, fromDate = start.toEpochDay(),
                    endDate = start.plusDays(7).toEpochDay(), parity = 1,
                    name = "临时课", startNode = 7, step = 1
                )
            )
        )
        val week1 = CourseLogic.dayCourses(st, start, 1, table, start, true)
        assertTrue(week1.any { it.course.name == "临时课" })

        // 第二周周一是双周，parity=1（单周）不该生效
        val week2Date = start.plusDays(7)
        val week2 = CourseLogic.dayCourses(st, week2Date, 2, table, start, true)
        assertFalse(week2.any { it.course.name == "临时课" })
    }

    @Test
    fun `非本周课程按设置决定是否展示 已上完的周次始终隐藏`() {
        val other = DetailBean(
            id = 3, courseId = 1, day = 1, startNode = 5, step = 2,
            startWeek = 5, endWeek = 8, type = 0, tableId = table
        )
        val finished = DetailBean(
            id = 4, courseId = 2, day = 1, startNode = 7, step = 2,
            startWeek = 1, endWeek = 1, type = 0, tableId = table
        )
        val st = state(details = listOf(other, finished))
        val week3Date = start.plusDays(14)

        val withOther = CourseLogic.dayCourses(st, week3Date, 3, table, start, showOtherWeek = true)
        assertTrue(withOther.any { it.detail.id == 3L && it.isOther })
        assertFalse(withOther.any { it.detail.id == 4L })

        val withoutOther = CourseLogic.dayCourses(st, week3Date, 3, table, start, showOtherWeek = false)
        assertFalse(withoutOther.any { it.detail.id == 3L })
    }

    // ---------- 覆盖规则 ----------

    @Test
    fun `被加课卡覆盖的原课不出现在展示口径里`() {
        val st = state(
            records = listOf(
                ChangeRecord(
                    id = 1, type = 1, fromDate = start.toEpochDay(),
                    name = "加课", startNode = 1, step = 2
                )
            )
        )
        val raw = CourseLogic.dayCourses(st, start, 1, table, start, true)
        // 原始口径里高数与加课并存（网格叠放交给展示层处理）
        assertTrue(raw.any { it.course.name == "高数" })
        assertTrue(raw.any { it.course.name == "加课" })

        val shown = CourseLogic.displayDayCourses(st, start, 1, table, start, true)
        assertFalse(shown.any { it.course.name == "高数" })
        assertTrue(shown.any { it.course.name == "加课" })
        // 未被占位的英语仍在
        assertTrue(shown.any { it.course.name == "英语" })
        assertNotNull(shown.firstOrNull { it.course.name == "加课" }?.record)
    }

    // ---------- 今日主角卡 ----------

    @Test
    fun `主角卡按时间给出下一节 正在上课 已结束 没课`() {
        val slots = AppState().slots
        val items = CourseLogic.dayCourses(state(), start, 1, table, start, true)
            .sortedBy { it.detail.startNode }
        val firstStart = CourseLogic.courseRange(items.first().detail, slots)!!.first
        val lastEnd = CourseLogic.courseRange(items.last().detail, slots)!!.second

        val before = CourseLogic.todayOverview(items, slots, firstStart.minusMinutes(10))
        assertEquals(CourseLogic.TodayState.NEXT, before.state)
        assertEquals(10, before.minutesUntilNext)

        val during = CourseLogic.todayOverview(items, slots, firstStart.plusMinutes(5))
        assertEquals(CourseLogic.TodayState.NOW, during.state)
        assertEquals(items.first().detail.id, during.current?.detail?.id)
        assertTrue(during.progress > 0f && during.progress < 1f)

        val after = CourseLogic.todayOverview(items, slots, lastEnd.plusMinutes(1))
        assertEquals(CourseLogic.TodayState.DONE, after.state)

        assertEquals(
            CourseLogic.TodayState.EMPTY,
            CourseLogic.todayOverview(emptyList(), slots, firstStart).state
        )
    }

    @Test
    fun `单节课的状态按起止时间划分 且落在边界上算正在上`() {
        val slots = AppState().slots
        val item = CourseLogic.dayCourses(state(), start, 1, table, start, true)
            .sortedBy { it.detail.startNode }
            .first()
        val (begin, end) = CourseLogic.courseRange(item.detail, slots)!!

        assertEquals(CourseLogic.DayCourseState.LATER, CourseLogic.stateOf(item, slots, begin.minusSeconds(1)))
        assertEquals(CourseLogic.DayCourseState.NOW, CourseLogic.stateOf(item, slots, begin))
        assertEquals(CourseLogic.DayCourseState.NOW, CourseLogic.stateOf(item, slots, end.minusSeconds(1)))
        assertEquals(CourseLogic.DayCourseState.DONE, CourseLogic.stateOf(item, slots, end))
    }

    // ---------- 冲突检测 ----------

    @Test
    fun `节次重叠才算冲突 相邻不算`() {
        val st = state()   // 周一 1-2 节高数、3-4 节英语

        // 只占第 2 节：只有高数撞上
        val onTop = CourseLogic.conflictsOf(st, table, day = 1, startNode = 2, step = 1)
        assertEquals(listOf(1L), onTop.map { it.id })

        // 两边都撞
        val both = CourseLogic.conflictsOf(st, table, day = 1, startNode = 2, step = 3)
        assertEquals(listOf(1L, 2L), both.map { it.id })

        val nextDoor = CourseLogic.conflictsOf(st, table, day = 1, startNode = 5, step = 2)
        assertTrue(nextDoor.isEmpty())
    }

    @Test
    fun `不同星期不算冲突`() {
        val st = state()
        assertTrue(CourseLogic.conflictsOf(st, table, day = 3, startNode = 1, step = 2).isEmpty())
    }

    @Test
    fun `单周和双周不会撞在一起`() {
        val odd = courseA().copy(id = 5, type = 1)     // 单周
        val st = state(details = listOf(odd))

        assertTrue(CourseLogic.conflictsOf(st, table, 1, 1, 2, type = 2).isEmpty())   // 双周 vs 单周
        assertEquals(1, CourseLogic.conflictsOf(st, table, 1, 1, 2, type = 1).size)   // 单周 vs 单周
        assertEquals(1, CourseLogic.conflictsOf(st, table, 1, 1, 2, type = 0).size)   // 每周 vs 单周
    }

    @Test
    fun `周次范围完全不重叠时不算冲突`() {
        val st = state(details = listOf(courseA().copy(startWeek = 1, endWeek = 4)))
        assertTrue(
            CourseLogic.conflictsOf(st, table, 1, 1, 2, startWeek = 5, endWeek = 16).isEmpty()
        )
        assertEquals(
            1,
            CourseLogic.conflictsOf(st, table, 1, 1, 2, startWeek = 4, endWeek = 16).size
        )
    }

    @Test
    fun `编辑自己时排除自身 不会自己和自己冲突`() {
        val st = state()
        assertTrue(CourseLogic.conflictsOf(st, table, 1, 1, 2, ignoreDetailId = 1L).isEmpty())
    }

    @Test
    fun `冲突不会跨课表`() {
        val otherTable = courseA().copy(id = 9, tableId = 99)
        val st = state(details = listOf(otherTable))
        assertTrue(CourseLogic.conflictsOf(st, table, 1, 1, 2).isEmpty())
    }
}
