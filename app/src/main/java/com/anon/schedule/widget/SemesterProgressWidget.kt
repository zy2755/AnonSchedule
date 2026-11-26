package com.anon.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.anon.schedule.R
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.theme.appPalette
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 桌面小组件 · 本学期进度（2×2）：第几周 / 总周数、已过百分比、进度条与剩余周数。
 * 数据全部来自课表设置里的开学日期 + 总周数，没填开学日期时给一句引导。
 */
class SemesterProgressWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        mgr.updateAppWidget(id, build(context))
    }

    companion object {

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SemesterProgressWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        fun build(ctx: Context): RemoteViews {
            val st = WidgetData.load(ctx)
            val s = st.tableSettings()
            val today = LocalDate.now()
            val start = s.startDate?.let { LocalDate.ofEpochDay(it) }
            val maxWeek = s.maxWeek.coerceAtLeast(1)
            val accent = appPalette(s.themePalette).accent.toArgb()

            val views = RemoteViews(ctx.packageName, R.layout.widget_semester_progress)
            // 顶部小竖线用当前配色的点缀色，和 App 里的主题保持一致
            views.setInt(R.id.progress_accent, "setBackgroundColor", accent)

            if (start == null) {
                views.setTextViewText(R.id.progress_pct, "")
                views.setTextViewText(R.id.progress_week, "未设置")
                views.setTextViewText(R.id.progress_total, "")
                views.setTextViewText(R.id.progress_sub, "去课表设置里填开学日期")
                views.setTextViewText(R.id.progress_end, "")
                views.setViewVisibility(R.id.progress_bar, View.GONE)
            } else {
                // 开学当天算第 0 天：第 1 周周一过了 1 天就进第 1 周
                val passed = ChronoUnit.DAYS.between(start, today).toInt()
                val totalDays = maxWeek * 7L
                val elapsed = (passed + 1L).coerceIn(0L, totalDays)
                val pct = (elapsed * 100f / totalDays).toInt().coerceIn(0, 100)
                val week = passed / 7 + 1

                views.setViewVisibility(R.id.progress_bar, View.VISIBLE)
                views.setTextViewText(R.id.progress_pct, "已过 $pct%")
                views.setTextViewText(
                    R.id.progress_week,
                    when {
                        passed < 0 -> "未开学"
                        week > maxWeek -> "已结课"
                        else -> "第 $week 周"
                    }
                )
                views.setTextViewText(
                    R.id.progress_total,
                    if (passed < 0 || week > maxWeek) "" else "共 $maxWeek 周"
                )
                views.setTextViewText(
                    R.id.progress_sub,
                    when {
                        passed < 0 -> "${start.monthValue}月${start.dayOfMonth}日开学"
                        week > maxWeek -> "本学期已经结束啦"
                        maxWeek - week == 0 -> "最后一周，加油！"
                        else -> "还剩 ${maxWeek - week} 周"
                    }
                )
                // 右侧补一个结课日期，把 2×2 的底部填满
                val end = start.plusDays(maxWeek * 7L - 1)
                views.setTextViewText(
                    R.id.progress_end,
                    if (passed >= 0) "${end.monthValue}月${end.dayOfMonth}日结课" else ""
                )
                views.setImageViewBitmap(
                    R.id.progress_bar,
                    barBitmap(pct, accent, ContextCompat.getColor(ctx, R.color.widget_track))
                )
            }
            views.setOnClickPendingIntent(android.R.id.background, widgetLaunchIntent(ctx))
            return views
        }

        /** 把进度条画成位图：RemoteViews 里 ProgressBar 在部分桌面会被系统主题染成别的颜色 */
        private fun barBitmap(pct: Int, fillColor: Int, trackColor: Int): Bitmap {
            val w = 800
            val h = 28
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = h / 2f
            paint.color = trackColor
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
            if (pct > 0) {
                // 太小的百分比按一个圆点画，否则圆角矩形会被压成一条看不清的细线
                val fillW = (w * pct / 100f).coerceAtLeast(h.toFloat())
                paint.color = fillColor
                c.drawRoundRect(RectF(0f, 0f, fillW, h.toFloat()), radius, radius, paint)
            }
            return bmp
        }
    }
}
