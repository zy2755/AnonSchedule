package com.anon.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 学习通任务 → 待办清单的自动同步规则 */
class ChaoxingTodoSyncTest {

    private val now = LocalDateTime.of(2026, 11, 25, 12, 0)
    private var nextId = 100L

    private fun remote(
        title: String = "第 3 章作业",
        course: String = "高等数学",
        type: String = "work",
        url: String = "https://mooc1-api.chaoxing.com/work/stu-work?id=1",
        remain: String = "剩余1天2小时30分钟",
        finished: Boolean = false
    ) = ChaoxingTodoSync.Remote(title, course, type, url, remain, finished)

    private fun merge(
        todos: List<CourseTodo> = emptyList(),
        items: List<ChaoxingTodoSync.Remote>
    ) = ChaoxingTodoSync.merge(
        todos = todos,
        items = items,
        now = now,
        idFactory = { nextId++ },
        courseIdOf = { name -> if (name == "高等数学") 7L else 0L }
    )

    @Test
    fun `未完成作业会自动加入待办并带上截止时间`() {
        val out = merge(items = listOf(remote()))
        assertEquals(1, out.size)
        val t = out.first()
        assertEquals("第 3 章作业", t.text)
        assertEquals(7L, t.courseId)
        assertEquals("cx_work", t.source)
        assertEquals(now.plusMinutes(26 * 60 + 30).toLocalDate().toEpochDay(), t.dueDate)
        assertEquals(14 * 60 + 30, t.dueMinute)
        assertTrue(t.sourceKey.isNotBlank())
    }

    @Test
    fun `重复刷新不会重复添加`() {
        val once = merge(items = listOf(remote()))
        val twice = merge(todos = once, items = listOf(remote()))
        assertEquals(1, twice.size)
        assertEquals(once.first().id, twice.first().id)
    }

    @Test
    fun `远端已完成的任务不新增`() {
        val out = merge(items = listOf(remote(finished = true)))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `远端完成后把已有待办勾掉`() {
        val added = merge(items = listOf(remote()))
        val out = merge(todos = added, items = listOf(remote(finished = true)))
        assertEquals(1, out.size)
        assertTrue(out.first().done)
    }

    @Test
    fun `没有剩余时间时没有截止日期`() {
        val out = merge(items = listOf(remote(remain = "")))
        assertEquals(0L, out.first().dueDate)
        assertEquals(-1, out.first().dueMinute)
    }

    @Test
    fun `本机没有这门课时把课程名写进标题`() {
        val out = merge(items = listOf(remote(course = "大学物理")))
        assertEquals("【大学物理】第 3 章作业", out.first().text)
        assertEquals(0L, out.first().courseId)
    }

    @Test
    fun `没有链接时用类型课程标题兜底去重`() {
        val a = remote(url = "", title = "期中考试", type = "exam", course = "大学物理")
        val out = merge(items = listOf(a, a))
        assertEquals(1, out.size)
        assertEquals("cx_exam", out.first().source)
    }

    @Test
    fun `剩余时间解析不出来就没有截止日期`() {
        assertNull(ChaoxingTodoSync.dueOf("已截止", now))
        val due = ChaoxingTodoSync.dueOf("剩余0分钟", now)
        assertEquals(now.toLocalDate().toEpochDay(), due?.first)
        assertEquals(12 * 60, due?.second)
    }
}
