package com.anon.schedule.data

import java.time.LocalDateTime

/**
 * 学习通作业 / 考试 → 待办清单的自动同步。
 *
 * 每次刷新学习通任务时调用 [merge]：未完成的任务自动补进待办，已完成的任务把对应待办勾掉；
 * 用 [CourseTodo.sourceKey] 去重，反复刷新不会产生重复条目。
 */
object ChaoxingTodoSync {

    /** 一条学习通任务里同步需要的信息 */
    data class Remote(
        val title: String,
        val course: String,
        val type: String,      // "work" / "exam"
        val url: String,
        val remain: String,    // 「剩余1天2小时30分钟」原文
        val finished: Boolean
    )

    /** 去重用的稳定 key：优先用任务链接，没有链接时退回「类型+课程+标题」 */
    fun keyOf(r: Remote): String =
        if (r.url.isNotBlank()) "${r.type}|${r.url.trim()}"
        else "${r.type}|${r.course.trim()}|${r.title.trim()}"

    /** 「剩余…」→ (截止日期 epochDay, 当天第几分钟)；没有可用的剩余时间返回 null */
    fun dueOf(remain: String, now: LocalDateTime): Pair<Long, Int>? {
        if (remain.isBlank()) return null
        fun n(pattern: String) = Regex(pattern).find(remain)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val minutes = n("(\\d+)天") * 1440 + n("(\\d+)小时") * 60 + n("(\\d+)分")
        if (minutes <= 0L && remain.none { it.isDigit() }) return null
        val due = now.plusMinutes(minutes)
        return due.toLocalDate().toEpochDay() to due.hour * 60 + due.minute
    }

    /**
     * 把远端任务并进待办：新任务加入，已有任务按远端状态勾完成、刷新截止时间。
     * [idFactory] 生成新待办 id；[courseIdOf] 按课程名匹配本机课程，匹配不到给 0。
     * 返回值和入参相等时说明没有任何变化。
     */
    fun merge(
        todos: List<CourseTodo>,
        items: List<Remote>,
        now: LocalDateTime,
        idFactory: () -> Long,
        courseIdOf: (String) -> Long = { 0L }
    ): List<CourseTodo> {
        var list = todos
        for (item in items) {
            val key = keyOf(item)
            val existing = list.firstOrNull { it.sourceKey == key }
            if (existing == null) {
                if (item.finished) continue
                val due = dueOf(item.remain, now)
                val courseId = courseIdOf(item.course)
                list = list + CourseTodo(
                    id = idFactory(),
                    courseId = courseId,
                    // 本机课表里没有这门课时，把课程名放进标题，避免看不出是哪门课的作业
                    text = if (courseId == 0L && item.course.isNotBlank())
                        "【${item.course}】${item.title}" else item.title,
                    createdAt = System.currentTimeMillis(),
                    dueDate = due?.first ?: 0L,
                    dueMinute = due?.second ?: -1,
                    source = if (item.type == "exam") "cx_exam" else "cx_work",
                    sourceKey = key
                )
            } else {
                val due = dueOf(item.remain, now)
                val patched = existing.copy(
                    done = existing.done || item.finished,
                    dueDate = if (existing.done || due == null) existing.dueDate else due.first,
                    dueMinute = if (existing.done || due == null) existing.dueMinute else due.second
                )
                if (patched != existing) list = list.map { if (it.id == existing.id) patched else it }
            }
        }
        return list
    }
}
