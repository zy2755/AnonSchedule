package com.anon.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.anon.schedule.R
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 桌面小组件 · 今日课程（4×2）：只显示还没上完的两节（正在上 / 下一节）；都上完则显示收工提示 */
class TodayWideWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
    }

    companion object {
        /** 最多两节：正在上的一节 + 紧跟着的一节 */
        private const val MAX_CARDS = 2
        private val dateFmt = DateTimeFormatter.ofPattern("M.d")

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayWideWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val all = WidgetData.today(st)
            val now = LocalTime.now()
            val today = LocalDate.now()
            val week = Repo.weekOf(st, today) ?: st.tableSettings().currentWeek
            val weekPart = if (st.tableSettings().startDate != null) "第 $week 周 " else ""
            val weekday = "周${"一二三四五六日"[today.dayOfWeek.value - 1]}"
            val tableName = st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"

            val views = RemoteViews(ctx.packageName, R.layout.widget_today_wide)
            views.setTextViewText(R.id.widget_table, tableName)
            views.setTextViewText(R.id.widget_date, today.format(dateFmt))
            views.setTextViewText(R.id.widget_week, weekPart + weekday)

            val remain = WidgetData.remaining(all, now).take(MAX_CARDS)
            if (remain.isEmpty()) {
                // 收工（或今天本来就没课）：像 2×2 那样给一句提示
                views.setViewVisibility(R.id.widget_cards, View.GONE)
                views.setViewVisibility(R.id.widget_done, View.VISIBLE)
                if (all.isEmpty()) {
                    views.setTextViewText(R.id.widget_done_title, "今天没有课")
                    views.setTextViewText(R.id.widget_done_sub, "好好休息一下吧～")
                } else {
                    views.setTextViewText(R.id.widget_done_title, "今天的课都上完啦")
                    views.setTextViewText(R.id.widget_done_sub, "共 ${all.size} 节课")
                }
            } else {
                views.setViewVisibility(R.id.widget_done, View.GONE)
                views.setViewVisibility(R.id.widget_cards, View.VISIBLE)
                views.removeAllViews(R.id.widget_cards)
                remain.forEach { views.addView(R.id.widget_cards, card(ctx, it)) }
            }
            views.setOnClickPendingIntent(android.R.id.background, widgetLaunchIntent(ctx))
            return views
        }

        /** 一节课程 = 左侧一条细色线 + 课程信息 + 右侧起止时间，不铺大色块 */
        private fun card(ctx: Context, c: WidgetCourse): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_today_chip)
            v.setTextViewText(R.id.card_name, c.name)
            v.setTextViewText(R.id.card_room, WidgetData.subText(c))
            v.setTextViewText(R.id.card_start, c.startText)
            v.setTextViewText(R.id.card_end, c.endText)
            v.setInt(R.id.card_accent, "setBackgroundColor", c.colorArgb)
            return v
        }
    }
}
