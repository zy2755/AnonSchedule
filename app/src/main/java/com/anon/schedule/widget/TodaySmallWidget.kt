package com.anon.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.AppState
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import java.time.LocalDate
import java.time.LocalTime

/** 桌面小组件 · 今日课程（2×2）：只显示正在上 / 下一节要上的那一节 */
class TodaySmallWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodaySmallWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val list = WidgetData.today(st)
            val now = LocalTime.now()
            val cur = WidgetData.currentOrNext(list, now)
            val today = LocalDate.now()
            val week = Repo.weekOf(st, today) ?: st.tableSettings().currentWeek

            val views = RemoteViews(ctx.packageName, R.layout.widget_today_small)
            when {
                list.isEmpty() -> {
                    views.setTextViewText(R.id.widget_header, "今日课表")
                    views.setTextViewText(R.id.widget_name, "今天没有课")
                    views.setTextViewText(R.id.widget_sub, "好好休息一下吧～")
                }
                cur == null -> {
                    views.setTextViewText(R.id.widget_header, "今日课表${weekSuffix(st, week)}")
                    views.setTextViewText(R.id.widget_name, "今天的课都上完啦")
                    views.setTextViewText(R.id.widget_sub, "共 ${list.size} 节课")
                }
                else -> {
                    views.setTextViewText(R.id.widget_header, WidgetData.statusOf(cur, now) + weekSuffix(st, week))
                    views.setTextViewText(R.id.widget_name, cur.name)
                    views.setTextViewText(
                        R.id.widget_sub,
                        listOf(cur.timeText, WidgetData.subText(cur)).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                    views.setInt(R.id.widget_accent, "setBackgroundColor", cur.colorArgb)
                }
            }
            views.setOnClickPendingIntent(R.id.widget_root, widgetLaunchIntent(ctx))
            return views
        }

        private fun weekSuffix(st: AppState, week: Int): String =
            if (st.tableSettings().startDate != null) " · 第 $week 周" else ""
    }
}

/** 点击小组件打开 App（2×2 与 2×4 共用同一份 PendingIntent） */
internal fun widgetLaunchIntent(ctx: Context): PendingIntent {
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        ctx, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
