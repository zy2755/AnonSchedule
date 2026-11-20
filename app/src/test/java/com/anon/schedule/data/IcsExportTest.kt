package com.anon.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * .ics 导出的回归测试。
 *
 * 导出的日程必须和 App 里看到的完全一致：单双周、停课、调课都要体现在展开结果里，
 * 所以这里逐条数事件、对时间，而不是只检查文件头。
 */
class IcsExportTest {

    private val table = 9L

    /** 2026-09-07 是周一 */
    private val start = LocalDate.of(2026, 9, 7)

    private fun state(
        details: List<DetailBean>,
        records: List<ChangeRecord> = emptyList(),
        courseName: String = "高等数学"
    ) = AppState(
        tables = listOf(TableBean(id = table)),
        currentTableId = table,
        courses = listOf(CourseBean(id = 1, name = courseName, tableId = table)),
        details = details,
        records = records,
        settings = Settings(startDate = start.toEpochDay(), maxWeek = 4, showOtherWeek = false)
    )

    /** 每周一第 1-2 节，上 4 周 */
    private fun weekly() = DetailBean(
        id = 1, courseId = 1, day = 1, startNode = 1, step = 2,
        startWeek = 1, endWeek = 4, tableId = table
    )

    private fun countEvents(ics: String) = Regex("BEGIN:VEVENT").findAll(ics).count()

    @Test
    fun `没设开学日期时导出为空`() {
        val st = state(listOf(weekly())).let { it.copy(settings = it.settings.copy(startDate = null)) }
        assertNull(IcsExport.build(st, table))
    }

    @Test
    fun `每周的课按周展开 起止时间与地点正确`() {
        val st = state(listOf(weekly().copy(room = "教三-201", teacher = "王老师")))
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        assertEquals(4, countEvents(ics))
        assertTrue(ics.contains("DTSTART:20260907T084000"))
        assertTrue(ics.contains("DTEND:20260907T100000"))
        assertTrue(ics.contains("LOCATION:教三-201"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }

    @Test
    fun `单周课只导出奇数周`() {
        val st = state(listOf(weekly().copy(type = 1)))
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        assertEquals(2, countEvents(ics))
        assertTrue(ics.contains("DTSTART:20260907T084000"))   // 第 1 周
        assertTrue(ics.contains("DTSTART:20260921T084000"))   // 第 3 周
    }

    @Test
    fun `停课的那一次不进日历`() {
        val st = state(
            details = listOf(weekly()),
            records = listOf(
                ChangeRecord(id = 1, type = 2, fromDate = start.plusDays(7).toEpochDay())
            )
        )
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        assertEquals(3, countEvents(ics))
        assertTrue(!ics.contains("DTSTART:20260914T084000"))
    }

    @Test
    fun `调课会把那一次挪到目标日期`() {
        val st = state(
            details = listOf(weekly()),
            records = listOf(
                ChangeRecord(
                    id = 1, type = 0,
                    fromDate = start.plusDays(7).toEpochDay(),   // 第 2 周周一
                    toDate = start.plusDays(9).toEpochDay()      // 第 2 周周三
                )
            )
        )
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        assertEquals(4, countEvents(ics))
        assertTrue(!ics.contains("DTSTART:20260914T084000"))
        assertTrue(ics.contains("DTSTART:20260916T084000"))
    }

    @Test
    fun `长课名按 75 字节折行 每行都不超限`() {
        val st = state(listOf(weekly()), courseName = "这是一门名字特别长的课程".repeat(4))
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        for (line in ics.split("\r\n")) {
            assertTrue("行过长：$line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
        // 折行后仍是合法内容：第一行是 SUMMARY 开头
        assertTrue(ics.contains("SUMMARY:这是一门"))
    }

    @Test
    fun `出现次数统计与事件数一致`() {
        val st = state(listOf(weekly()))
        val ics = IcsExport.build(st, table, LocalDateTime.of(2026, 9, 7, 10, 0))!!
        assertEquals(countEvents(ics), IcsExport.occurrenceCount(st, table))
    }
}
