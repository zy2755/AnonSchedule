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
import java.time.format.DateTimeFormatter

/**
 * 桌面小组件 · 今日 · 明日（可横向 / 纵向拉伸）
 * 左边一列展示今天的课、右边一列展示明天的课；缩窄到放不下两列时自动隐藏「明天」列；
 * 纵向拉高时每列能显示的行数会跟着变多，把没显示完的课也露出来。
 */
class TodayTomorrowWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach {
            val o = mgr.getAppWidgetOptions(it)
            mgr.updateAppWidget(it, build(context, isWide(o), rowsFor(o)))
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        // 用户拖动手柄改尺寸时回调，这里按新高度重算行数
        mgr.updateAppWidget(id, build(context, isWide(newOptions), rowsFor(newOptions)))
    }

    companion object {
        /** 横向宽度 ≥ 这个 dp 时才展开「明天」列（约 3 格宽） */
        private const val WIDE_MIN_DP = 165

        /** 顶部标题栏 + 列标题 + 内边距合计占掉的高度（dp），用来推算每列能塞几行课 */
        private const val COL_OVERHEAD_DP = 62

        /** 一行课程（课程名 + 时间）大约占的高度（dp） */
        private const val ROW_HEIGHT_DP = 34

        private val dateFmt = DateTimeFormatter.ofPattern("M.dd")

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayTomorrowWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach {
                val o = mgr.getAppWidgetOptions(it)
                mgr.updateAppWidget(it, build(ctx, isWide(o), rowsFor(o)))
            }
        }

        /** 该实例当前是否够宽、能并排显示「今日 + 明日」 */
        fun isWide(options: Bundle): Boolean =
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= WIDE_MIN_DP

        /** 按当前高度算出每列能显示几行课：拉得越高显示越多，没显示完的课就能露出来 */
        fun rowsFor(options: Bundle): Int {
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val dp = if (minH > 0) minH else maxH
            if (dp <= 0) return 3
            return ((dp - COL_OVERHEAD_DP) / ROW_HEIGHT_DP).coerceIn(1, 8)
        }

        fun build(ctx: Context, wide: Boolean, rows: Int): RemoteViews {
            val st = WidgetData.load(ctx)
            val today = LocalDate.now()
            val week = Repo.weekOf(st, today) ?: st.tableSettings().currentWeek
            val weekPart = if (st.tableSettings().startDate != null) " · 第${week}周" else ""

            val views = RemoteViews(ctx.packageName, R.layout.widget_today_tomorrow)
            views.setTextViewText(
                R.id.mt_date,
                "${today.format(dateFmt)} 周${"一二三四五六日"[today.dayOfWeek.value - 1]}$weekPart"
            )
            bindColumn(ctx, views, WidgetData.today(st), R.id.mt_today_list, R.id.mt_today_empty, "今天", rows)
            bindColumn(ctx, views, WidgetData.tomorrow(st), R.id.mt_tomorrow_list, R.id.mt_tomorrow_empty, "明天", rows)

            views.setViewVisibility(R.id.mt_divider, if (wide) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.mt_tomorrow_col, if (wide) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.mt_root, widgetLaunchIntent(ctx))
            return views
        }

        /** 往某一列里塞课程行（最多 rows 行）；没课就显示「今天/明天没有课啦」 */
        private fun bindColumn(
            ctx: Context,
            views: RemoteViews,
            list: List<WidgetCourse>,
            listId: Int,
            emptyId: Int,
            dayLabel: String,
            rows: Int
        ) {
            views.removeAllViews(listId)
            if (list.isEmpty()) {
                views.setViewVisibility(listId, View.GONE)
                views.setViewVisibility(emptyId, View.VISIBLE)
                views.setTextViewText(emptyId, "( ˘▽˘ ) ${dayLabel}没有课啦")
            } else {
                views.setViewVisibility(emptyId, View.GONE)
                views.setViewVisibility(listId, View.VISIBLE)
                list.take(rows).forEach { views.addView(listId, row(ctx, it)) }
            }
        }

        private fun row(ctx: Context, c: WidgetCourse): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_mt_item)
            v.setTextViewText(R.id.mt_item_name, c.name)
            v.setTextViewText(R.id.mt_item_time, c.timeText)
            v.setInt(R.id.mt_item_accent, "setBackgroundColor", c.colorArgb)
            return v
        }
    }
}
