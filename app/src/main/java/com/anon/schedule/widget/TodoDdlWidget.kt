package com.anon.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.anon.schedule.EXTRA_OPEN_TODOS
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.TodoLogic
import java.time.LocalDate
import java.time.LocalTime

/**
 * 桌面小组件 · 待办 DDL（4×2）：最紧急的两条待办 + 未完成总数。
 * 排序沿用待办页的规则：已逾期 → 今天 → 最近到期 → 没有日期。
 */
class TodoDdlWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
    }

    companion object {
        /** 4×2 只放得下两条，再多就靠「共 N 项」提示去 App 里看 */
        private const val MAX_ROWS = 2

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodoDdlWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val today = LocalDate.now()
            val now = LocalTime.now()
            val pending = TodoLogic.pendingCount(st.todos)
            val preview = TodoLogic.preview(st.todos, today, now, MAX_ROWS)

            val views = RemoteViews(ctx.packageName, R.layout.widget_todo_ddl)
            views.setTextViewText(R.id.todo_header, "待办 DDL")
            views.setTextViewText(R.id.todo_count, if (pending > 0) "共 $pending 项" else "")

            if (preview.isEmpty()) {
                views.setViewVisibility(R.id.todo_list, View.GONE)
                views.setViewVisibility(R.id.todo_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.todo_empty,
                    if (st.todos.isEmpty()) "还没有待办～" else "待办都清空啦 ( ˘▽˘ )"
                )
            } else {
                views.setViewVisibility(R.id.todo_empty, View.GONE)
                views.setViewVisibility(R.id.todo_list, View.VISIBLE)
                views.removeAllViews(R.id.todo_list)
                preview.forEach { views.addView(R.id.todo_list, row(ctx, it, today, now)) }
            }
            views.setOnClickPendingIntent(android.R.id.background, todosLaunchIntent(ctx))
            return views
        }

        /** 一条待办：左侧竖线按紧急度染色，右侧是「今天 18:00」这类截止文案 */
        private fun row(ctx: Context, todo: CourseTodo, today: LocalDate, now: LocalTime): RemoteViews {
            val bucket = TodoLogic.bucketOf(todo, today, now)
            val v = RemoteViews(ctx.packageName, R.layout.widget_todo_ddl_row)
            v.setTextViewText(R.id.todo_text, todo.text)
            v.setTextViewText(R.id.todo_due, TodoLogic.dueLabel(todo, today))
            v.setTextColor(
                R.id.todo_due,
                ContextCompat.getColor(
                    ctx,
                    if (bucket == TodoLogic.Bucket.OVERDUE) R.color.widget_overdue else R.color.widget_card_dim
                )
            )
            v.setInt(
                R.id.todo_accent,
                "setBackgroundColor",
                ContextCompat.getColor(
                    ctx,
                    when (bucket) {
                        TodoLogic.Bucket.OVERDUE -> R.color.widget_overdue
                        TodoLogic.Bucket.TODAY -> R.color.widget_due_today
                        else -> R.color.widget_idle
                    }
                )
            )
            v.setOnClickPendingIntent(R.id.todo_check, doneIntent(ctx, todo.id))
            return v
        }

        /** 每行独立的「勾掉」广播（requestCode 按 id 区分，避免互相覆盖） */
        private fun doneIntent(ctx: Context, id: Long): PendingIntent {
            val code = ((id xor (id ushr 32)) and 0x7FFFFFFF).toInt()
            val intent = Intent(ctx, TodoWidgetActionReceiver::class.java)
                .setAction(TodoWidgetActionReceiver.ACTION_DONE)
                .putExtra(TodoWidgetActionReceiver.EXTRA_ID, id)
            return PendingIntent.getBroadcast(
                ctx, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/** 点击「待办 DDL」小组件：打开 App 并直达待办页（requestCode 与其它小组件错开，避免 extras 被复用） */
internal fun todosLaunchIntent(ctx: Context): PendingIntent {
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_TODOS, "1")
    }
    return PendingIntent.getActivity(
        ctx, 1, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
