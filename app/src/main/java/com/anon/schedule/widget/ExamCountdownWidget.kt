package com.anon.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.anon.schedule.R
import com.anon.schedule.data.AppState
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.TodoLogic
import java.time.LocalDate

/**
 * 桌面小组件 · 考试倒计时（2×2）：最近一场考试 + 剩余天数。
 * 考试数据来自学习通同步进待办的 cx_exam 条目，刷新学习通后自动更新。
 */
class ExamCountdownWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
    }

    companion object {

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ExamCountdownWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val today = LocalDate.now()
            val exams = TodoLogic.upcomingExams(st.todos, today)
            val views = RemoteViews(ctx.packageName, R.layout.widget_exam_countdown)

            if (exams.isEmpty()) {
                views.setTextViewText(R.id.exam_count, "")
                views.setTextViewText(R.id.exam_title, "暂无考试安排")
                views.setTextViewText(R.id.exam_days, "—")
                views.setTextViewText(R.id.exam_unit, "")
                views.setTextViewText(R.id.exam_meta, "去学习通刷新一下考试")
                views.setInt(
                    R.id.exam_accent, "setBackgroundColor",
                    ContextCompat.getColor(ctx, R.color.widget_idle)
                )
            } else {
                val nearest = exams.first()
                val days = TodoLogic.daysUntil(nearest, today)
                views.setTextViewText(R.id.exam_count, "${exams.size} 场")
                views.setTextViewText(R.id.exam_title, nearest.text)
                views.setTextViewText(R.id.exam_days, days.toString())
                views.setTextViewText(R.id.exam_unit, "天")
                views.setTextViewText(R.id.exam_meta, metaText(st, nearest))
                views.setTextColor(
                    R.id.exam_days,
                    ContextCompat.getColor(
                        ctx,
                        if (days <= 3) R.color.widget_overdue else R.color.widget_due_today
                    )
                )
                views.setInt(
                    R.id.exam_accent, "setBackgroundColor",
                    ContextCompat.getColor(
                        ctx,
                        if (days <= 3) R.color.widget_overdue else R.color.widget_due_today
                    )
                )
            }
            views.setOnClickPendingIntent(android.R.id.background, todosLaunchIntent(ctx))
            return views
        }

        /** 「课程名 · 11月30日 14:00」 */
        private fun metaText(st: AppState, todo: CourseTodo): String {
            val due = LocalDate.ofEpochDay(todo.dueDate)
            val time = if (todo.dueMinute >= 0) {
                " %02d:%02d".format(todo.dueMinute / 60, todo.dueMinute % 60)
            } else ""
            val course = st.courses.firstOrNull { it.id == todo.courseId }?.name
            return listOfNotNull(course, "${due.monthValue}月${due.dayOfMonth}日$time")
                .joinToString(" · ")
        }
    }
}
