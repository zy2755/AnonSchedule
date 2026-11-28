package com.anon.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * .ics 导入的回归测试。
 *
 * 最关键的是和 [IcsExport] 对得上：自己导出的文件必须能原样导回来
 * （星期、节次、周次、单双周、老师、地点都不能丢）。
 */
class IcsImportTest {

    private val table = 9L

    /** 2026-09-07 是周一 */
    private val start = LocalDate.of(2026, 9, 7)

    private fun state(
        detail: DetailBean,
        courseName: String = "高等数学"
    ) = AppState(
        tables = listOf(TableBean(id = table)),
        currentTableId = table,
        courses = listOf(
            CourseBean(id = 1, name = courseName, tableId = table)
        ),
        details = listOf(detail),
        settings = Settings(startDate = start.toEpochDay(), maxWeek = 8, showOtherWeek = false)
    )

    private fun weekly() = DetailBean(
        id = 1, courseId = 1, day = 1, startNode = 1, step = 2,
        startWeek = 1, endWeek = 4, tableId = table,
        room = "教三-201", teacher = "王老师"
    )

    @Test
    fun `导出再导入 能还原课程名 星期 节次 周次与老师地点`() {
        val st = state(weekly())
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        val parsed = IcsImport.parse(ics, st, table).getOrThrow()

        assertEquals(1, parsed.size)
        val p = parsed.single()
        assertEquals("高等数学", p.name)
        assertEquals(1, p.day)
        assertEquals(1, p.startNode)
        assertEquals(2, p.step)
        assertEquals(1, p.startWeek)
        assertEquals(4, p.endWeek)
        assertEquals(0, p.type)
        assertEquals("王老师", p.teacher)
        assertEquals("教三-201", p.room)
    }

    @Test
    fun `单周课导出后仍还原成单周`() {
        val st = state(weekly().copy(type = 1, endWeek = 7))
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        val p = IcsImport.parse(ics, st, table).getOrThrow().single()

        assertEquals(1, p.type)
        assertEquals(1, p.startWeek)
        assertEquals(7, p.endWeek)
    }

    @Test
    fun `RRULE WEEKLY 按 UNTIL 展开成多周`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:大学英语
            DTSTART:20260909T100500
            DTEND:20260909T112500
            RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20260930T235959Z
            LOCATION:外语楼101
            DESCRIPTION:第 3-4 节 · 教师：李老师
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val st = state(weekly())
        val p = IcsImport.parse(ics, st, table).getOrThrow().single()

        assertEquals("大学英语", p.name)
        assertEquals(3, p.day)        // 周三
        assertEquals(3, p.startNode)  // 10:05 是第 3 节
        assertEquals(2, p.step)
        assertEquals(1, p.startWeek)
        assertEquals(4, p.endWeek)
        assertEquals("李老师", p.teacher)
        assertEquals("外语楼101", p.room)
    }

    @Test
    fun `没填开学日期时提示先设置`() {
        val st = state(weekly()).let { it.copy(settings = it.settings.copy(startDate = null)) }
        val result = IcsImport.parse("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n", st, table)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("开学日期"))
    }

    @Test
    fun `全天事件排不进课表时给出提示`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:国庆节
            DTSTART;VALUE=DATE:20261001
            DTEND;VALUE=DATE:20261002
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val result = IcsImport.parse(ics, state(weekly()), table)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("没有解析到"))
    }
}
