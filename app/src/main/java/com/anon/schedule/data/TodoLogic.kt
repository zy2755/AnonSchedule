package com.anon.schedule.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 待办清单的纯逻辑：分组、排序、截止文案与提醒时刻。
 *
 * 和 [CourseLogic] 一样刻意不碰 Android / Repo，输入全部由参数传入，
 * 这样「逾期怎么算」「没日期排在哪」这类规则可以在 JVM 上直接跑单元测试。
 */
object TodoLogic {

    /** 待办在清单里的分组（按展示顺序排列） */
    enum class Bucket { OVERDUE, TODAY, UPCOMING, NO_DATE, DONE }

    data class Grouped(
        val overdue: List<CourseTodo>,
        val today: List<CourseTodo>,
        val upcoming: List<CourseTodo>,
        val noDate: List<CourseTodo>,
        val done: List<CourseTodo>
    )

    /**
     * 判断一条待办落在哪个分组。
     * 当天有具体时刻且已经过了 → 算逾期（全天待办到今天结束前都还算今天）。
     */
    fun bucketOf(todo: CourseTodo, today: LocalDate, now: LocalTime): Bucket {
        if (todo.done) return Bucket.DONE
        if (todo.dueDate <= 0L) return Bucket.NO_DATE
        val due = LocalDate.ofEpochDay(todo.dueDate)
        if (due.isBefore(today)) return Bucket.OVERDUE
        if (due.isAfter(today)) return Bucket.UPCOMING
        if (todo.dueMinute >= 0 && now.hour * 60 + now.minute > todo.dueMinute) return Bucket.OVERDUE
        return Bucket.TODAY
    }

    /** 按分组拆开并排序：未完成按截止时间由近到远，已完成按添加时间倒序 */
    fun grouped(list: List<CourseTodo>, today: LocalDate, now: LocalTime): Grouped {
        val buckets = list.groupBy { bucketOf(it, today, now) }
        return Grouped(
            overdue = (buckets[Bucket.OVERDUE].orEmpty()).sortedBy { sortKey(it) },
            today = (buckets[Bucket.TODAY].orEmpty()).sortedBy { sortKey(it) },
            upcoming = (buckets[Bucket.UPCOMING].orEmpty()).sortedBy { sortKey(it) },
            noDate = (buckets[Bucket.NO_DATE].orEmpty()).sortedBy { it.createdAt },
            done = (buckets[Bucket.DONE].orEmpty()).sortedByDescending { it.createdAt }
        )
    }

    /** 未完成待办的排序键：先日期、再时刻，同时间按添加顺序 */
    fun sortKey(todo: CourseTodo): Long =
        todo.dueDate * 10_000L + (if (todo.dueMinute >= 0) todo.dueMinute else 0).toLong()

    /** 最近的几条未完成待办（今日页卡片用）：逾期 → 今天 → 即将到期 → 无日期 */
    fun preview(list: List<CourseTodo>, today: LocalDate, now: LocalTime, limit: Int = 3): List<CourseTodo> {
        val g = grouped(list, today, now)
        return (g.overdue + g.today + g.upcoming + g.noDate).take(limit)
    }

    /** 未完成总数（底部 tab / 角标用） */
    fun pendingCount(list: List<CourseTodo>): Int = list.count { !it.done }

    /** 截止时间展示文案：今天 18:00 / 明天 / 10月3日 / 已逾期 */
    fun dueLabel(todo: CourseTodo, today: LocalDate): String {
        if (todo.dueDate <= 0L) return "无日期"
        val due = LocalDate.ofEpochDay(todo.dueDate)
        val time = if (todo.dueMinute >= 0) {
            "%02d:%02d".format(todo.dueMinute / 60, todo.dueMinute % 60)
        } else ""
        val day = when (due) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.minusDays(1) -> "昨天"
            else -> if (due.year == today.year) "${due.monthValue}月${due.dayOfMonth}日"
            else "${due.year}年${due.monthValue}月${due.dayOfMonth}日"
        }
        return if (time.isEmpty()) day else "$day $time"
    }

    /** 截止时刻的毫秒时间戳；未设日期或无法解析返回 null */
    fun dueMillis(todo: CourseTodo, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (todo.dueDate <= 0L) return null
        val date = LocalDate.ofEpochDay(todo.dueDate)
        // 没设时刻的待办按当天 09:00 提醒：默认全天待办不该在凌晨把人叫醒
        val time = if (todo.dueMinute >= 0) {
            LocalTime.of(todo.dueMinute / 60, todo.dueMinute % 60)
        } else {
            LocalTime.of(9, 0)
        }
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** 截止时刻是否已经过了（用于列表里标红） */
    fun isOverdue(todo: CourseTodo, today: LocalDate, now: LocalTime): Boolean =
        bucketOf(todo, today, now) == Bucket.OVERDUE

    /** 供调试/测试：把毫秒时间戳换回本地时间 */
    fun localDateTimeOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()) =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
}
