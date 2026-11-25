package com.anon.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 待办清单的分组/排序/截止文案规则 */
class TodoLogicTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val now = LocalTime.of(12, 0)

    private fun todo(
        id: Long,
        dueDate: Long = 0L,
        dueMinute: Int = -1,
        done: Boolean = false,
        createdAt: Long = id
    ) = CourseTodo(
        id = id,
        courseId = 0L,
        text = "t$id",
        done = done,
        createdAt = createdAt,
        dueDate = dueDate,
        dueMinute = dueMinute
    )

    private fun day(offset: Long) = today.plusDays(offset).toEpochDay()

    @Test
    fun `当天的截止时刻过了算逾期`() {
        val before = todo(1, dueDate = day(0), dueMinute = 11 * 60 + 30)
        val after = todo(2, dueDate = day(0), dueMinute = 12 * 60 + 30)
        val allDay = todo(3, dueDate = day(0))
        assertEquals(TodoLogic.Bucket.OVERDUE, TodoLogic.bucketOf(before, today, now))
        assertEquals(TodoLogic.Bucket.TODAY, TodoLogic.bucketOf(after, today, now))
        assertEquals(TodoLogic.Bucket.TODAY, TodoLogic.bucketOf(allDay, today, now))
    }

    @Test
    fun `昨天逾期 明天即将到期 无日期单独一组 已完成优先`() {
        assertEquals(TodoLogic.Bucket.OVERDUE, TodoLogic.bucketOf(todo(1, day(-1)), today, now))
        assertEquals(TodoLogic.Bucket.UPCOMING, TodoLogic.bucketOf(todo(2, day(1)), today, now))
        assertEquals(TodoLogic.Bucket.NO_DATE, TodoLogic.bucketOf(todo(3), today, now))
        assertEquals(TodoLogic.Bucket.DONE, TodoLogic.bucketOf(todo(4, done = true), today, now))
    }

    @Test
    fun `分组内按截止时间由近到远排序`() {
        val g = TodoLogic.grouped(
            listOf(
                todo(1, dueDate = day(3)),
                todo(2, dueDate = day(1)),
                todo(3, dueDate = day(2), dueMinute = 8 * 60),
                todo(4, dueDate = day(2), dueMinute = 10 * 60)
            ),
            today, now
        )
        assertEquals(listOf(2L, 3L, 4L, 1L), g.upcoming.map { it.id })
    }

    @Test
    fun `今日页预览先逾期再今天`() {
        val preview = TodoLogic.preview(
            listOf(
                todo(1, dueDate = day(1)),
                todo(2, dueDate = day(-1)),
                todo(3, dueDate = day(0), dueMinute = 23 * 60),
                todo(4, done = true)
            ),
            today, now, limit = 3
        )
        assertEquals(listOf(2L, 3L, 1L), preview.map { it.id })
    }

    @Test
    fun `截止文案覆盖今天明天和具体日期`() {
        assertEquals("今天 18:00", TodoLogic.dueLabel(todo(1, day(0), 18 * 60), today))
        assertEquals("今天", TodoLogic.dueLabel(todo(1, day(0)), today))
        assertEquals("明天 09:00", TodoLogic.dueLabel(todo(1, day(1), 9 * 60), today))
        assertEquals("12月3日", TodoLogic.dueLabel(todo(1, day(8)), today))
        assertEquals("无日期", TodoLogic.dueLabel(todo(1), today))
    }

    @Test
    fun `全天待办按当天九点提醒`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val millis = TodoLogic.dueMillis(todo(1, day(0)), zone)!!
        val time = TodoLogic.localDateTimeOf(millis, zone)
        assertEquals(9, time.hour)
        assertEquals(0, time.minute)
        assertEquals(today, time.toLocalDate())
        assertNull(TodoLogic.dueMillis(todo(2), zone))
    }

    @Test
    fun `未完成计数排除已完成`() {
        val list = listOf(todo(1), todo(2, done = true), todo(3))
        assertEquals(2, TodoLogic.pendingCount(list))
        assertTrue(TodoLogic.isOverdue(todo(4, day(-2)), today, now))
    }
}
