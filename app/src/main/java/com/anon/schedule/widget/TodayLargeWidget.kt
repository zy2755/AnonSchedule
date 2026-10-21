package com.anon.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.anon.schedule.R
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 桌面小组件 · 今日课程（2×4）：可滚动的今日课程列表 */
class TodayLargeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        mgr.updateAppWidget(ids, build(context))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
        mgr.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.widget_list)
    }

    companion object {
        private val dateFmt = DateTimeFormatter.ofPattern("M月d日")

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayLargeWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val today = LocalDate.now()
            val week = Repo.weekOf(st, today) ?: st.tableSettings().currentWeek
            val weekPart = if (st.tableSettings().startDate != null) " · 第 $week 周" else ""

            val views = RemoteViews(ctx.packageName, R.layout.widget_today_large)
            views.setTextViewText(
                R.id.widget_header,
                "今日课程 · ${today.format(dateFmt)} 周${"一二三四五六日"[today.dayOfWeek.value - 1]}$weekPart"
            )
            views.setRemoteAdapter(R.id.widget_list, Intent(ctx, TodayLargeService::class.java))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setPendingIntentTemplate(R.id.widget_list, widgetLaunchIntent(ctx))
            views.setOnClickPendingIntent(R.id.widget_root, widgetLaunchIntent(ctx))
            return views
        }
    }
}

/** 给上面的 ListView 提供数据 */
class TodayLargeService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayLargeFactory(applicationContext)
}

class TodayLargeFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetCourse> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val st = WidgetData.load(ctx)
        items = WidgetData.today(st)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val c = items.getOrNull(position) ?: return null
        val v = RemoteViews(ctx.packageName, R.layout.widget_today_item)
        v.setTextViewText(R.id.item_time, c.timeText)
        v.setTextViewText(R.id.item_name, c.name)
        v.setTextViewText(R.id.item_room, WidgetData.subText(c))
        v.setInt(R.id.item_accent, "setBackgroundColor", c.colorArgb)
        // 点某一节课也打开 App
        v.setOnClickFillInIntent(R.id.item_root, Intent())
        return v
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
