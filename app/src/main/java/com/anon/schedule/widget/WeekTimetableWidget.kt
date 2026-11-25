package com.anon.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.AppState
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.courseColor
import com.anon.schedule.ui.theme.appPalette
import com.anon.schedule.ui.theme.paletteBackgroundColor
import java.time.LocalDate

/**
 * 4×4 本周课表小部件。
 *
 * 整张课表用 Canvas 画成位图塞进 ImageView（RemoteViews 里没法直接摆几十个子 View，
 * 也不想为一个小组件引入 Glance 一整套依赖）。位图比例跟着桌面格子尺寸走，
 * 用户在桌面上怎么拉都不会把文字压扁。
 */
class WeekTimetableWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            runCatching { manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id))) }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle
    ) {
        // 拉伸时按新比例重画一次
        runCatching { manager.updateAppWidget(id, build(context, newOptions)) }
    }

    companion object {

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeekTimetableWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id ->
                runCatching { mgr.updateAppWidget(id, build(ctx, mgr.getAppWidgetOptions(id))) }
            }
        }

        fun build(ctx: Context, options: Bundle? = null): RemoteViews {
            val st = WidgetData.load(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_week)
            val (w, h) = sizeOf(options)
            runCatching { rv.setImageViewBitmap(R.id.widget_week_image, render(ctx, st, w, h)) }
            rv.setOnClickPendingIntent(
                R.id.widget_week_root,
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return rv
        }

        /** 按桌面给的宽高比算位图尺寸（宽度固定 900px，高度按比例） */
        private fun sizeOf(options: Bundle?): Pair<Int, Int> {
            val wDp = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0)
                .takeIf { it > 0 } ?: 250
            val hDp = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0)
                .takeIf { it > 0 } ?: 220
            val w = 900
            val h = (w.toFloat() * hDp / wDp).toInt().coerceIn(480, 1900)
            return w to h
        }

        /** 把当前课表画成位图 */
        private fun render(ctx: Context, st: AppState, w: Int, h: Int): Bitmap {
            val s = st.tableSettings()
            val dark = resolveDark(ctx, s.themeMode)
            val bg = paletteBackgroundColor(s.themePalette, dark).toArgb()
            val accent = appPalette(s.themePalette).accent.toArgb()
            val textMain = if (dark) 0xFFF2F2F7.toInt() else 0xFF1B1B20.toInt()
            val textDim = if (dark) 0xFF9EA3AC.toInt() else 0xFF6C7077.toInt()
            val gridColor = withAlpha(textMain, 26)
            val todayBg = withAlpha(accent, 22)

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // 圆角裁切，和桌面小组件的圆角保持一致
            c.clipPath(Path().apply {
                addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 34f, 34f, Path.Direction.CW)
            })
            c.drawColor(bg)

            val today = LocalDate.now()
            val week = Repo.weekOf(st, today) ?: s.currentWeek
            val showSat = s.showSaturday
            val showSun = s.showSunday
            val days = when {
                showSat && showSun -> 7
                showSat -> 6
                else -> 5
            }
            val nodes = s.nodeCount.coerceIn(1, 20)
            val leftW = 56f
            val topH = 100f
            val padR = 16f
            val padB = 16f
            val dayW = (w - leftW - padR) / days
            val rowH = (h - topH - padB) / nodes

            // 顶部标题：第几周 + 课表名
            val titlePaint = textPaint(30f, textMain, bold = true)
            val weekText = "第 $week 周"
            c.drawText(weekText, 26f, 58f, titlePaint)
            val titleWidth = titlePaint.measureText(weekText)
            c.drawText(
                "  " + st.tables.firstOrNull { it.id == st.currentTableId }?.name.orEmpty(),
                26f + titleWidth, 58f, textPaint(20f, textDim)
            )

            // 今天所在的列高亮
            val weekStart = Repo.dateOfWeekDay(st, week, 1)
            if (weekStart != null) {
                val todayIndex = today.toEpochDay() - weekStart.toEpochDay()
                if (todayIndex in 0 until days) {
                    c.drawRect(
                        leftW + todayIndex * dayW, topH - 30f,
                        leftW + (todayIndex + 1) * dayW, h - padB, Paint().apply { color = todayBg }
                    )
                }
            }

            // 星期表头
            val headPaint = textPaint(22f, textDim, bold = true).apply { textAlign = Paint.Align.CENTER }
            val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
            for (i in 0 until days) {
                val x = leftW + (i + 0.5f) * dayW
                c.drawText(dayNames.getOrElse(i) { "" }, x, topH - 12f, headPaint)
            }

            // 网格线
            val linePaint = Paint().apply {
                color = gridColor
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            for (r in 0..nodes) {
                val y = topH + r * rowH
                c.drawLine(leftW, y, w - padR, y, linePaint)
            }
            for (d in 0..days) {
                val x = leftW + d * dayW
                c.drawLine(x, topH - 30f, x, h - padB, linePaint)
            }

            // 左侧节次号
            val nodePaint = textPaint(16f, textDim).apply { textAlign = Paint.Align.RIGHT }
            for (n in 1..nodes) {
                val y = topH + (n - 0.5f) * rowH + 6f
                c.drawText(n.toString(), leftW - 10f, y, nodePaint)
            }

            // 课程块
            var courseCount = 0
            for (d in 1..days) {
                val date = Repo.dateOfWeekDay(st, week, d) ?: continue
                val list = Repo.displayDayCourses(st, date, week).filterNot { it.isOther }
                list.forEach { shown ->
                    val detail = shown.detail
                    val startNode = detail.startNode.coerceIn(1, nodes)
                    val step = detail.step.coerceAtLeast(1)
                    val endNode = (startNode + step - 1).coerceAtMost(nodes)
                    if (startNode > nodes) return@forEach
                    val x = leftW + (d - 1) * dayW + 4f
                    val y = topH + (startNode - 1) * rowH + 3f
                    val w = dayW - 8f
                    val h = (endNode - startNode + 1) * rowH - 6f
                    val fill = withAlpha(courseColor(shown.course.color).toArgb(), 235)
                    c.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, Paint().apply {
                        color = fill
                        style = Paint.Style.FILL
                    })
                    val onFill = onColorText(shown.course.color)
                    // 只显示课程名：字号按格子自适应，放不下就折行，行数不够再慢慢缩字号
                    val textW = w - 12f
                    var nameSize = (rowH * 0.46f).coerceIn(12f, 20f)
                    var namePaint = textPaint(nameSize, onFill, bold = true)
                    var maxLines = ((h - 8f) / (nameSize * 1.2f)).toInt().coerceIn(1, 4)
                    var lines = wrapText(namePaint, shown.course.name, textW, maxLines)
                    while (nameSize > 11f &&
                        lines.sumOf { it.length } - (if (lines.lastOrNull()?.endsWith("…") == true) 1 else 0) <
                        shown.course.name.length
                    ) {
                        nameSize -= 1f
                        namePaint = textPaint(nameSize, onFill, bold = true)
                        maxLines = ((h - 8f) / (nameSize * 1.2f)).toInt().coerceIn(1, 4)
                        lines = wrapText(namePaint, shown.course.name, textW, maxLines)
                    }
                    val lineH = nameSize * 1.2f
                    var baseline = y + (h - lineH * lines.size) / 2f + nameSize
                    lines.forEach { line ->
                        c.drawText(line, x + 7f, baseline, namePaint)
                        baseline += lineH
                    }
                    courseCount++
                }
            }

            if (courseCount == 0) {
                val emptyPaint = textPaint(24f, textDim).apply { textAlign = Paint.Align.CENTER }
                c.drawText("本周没有课程", w / 2f, h / 2f, emptyPaint)
            }
            return bmp
        }

        /** 小组件进程不能调 @Composable 的 isDarkMode，这里按系统夜间模式自己判一次 */
        private fun resolveDark(ctx: Context, themeMode: Int): Boolean = when (themeMode) {
            1 -> false
            2 -> true
            else -> (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
            Paint().apply {
                isAntiAlias = true
                textSize = size
                this.color = color
                typeface = Typeface.create(
                    if (bold) "sans-serif-medium" else "sans-serif",
                    Typeface.NORMAL
                )
            }

        private fun ellipsize(p: Paint, text: String, maxWidth: Float): String {
            if (p.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && p.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return if (end <= 0) "" else text.substring(0, end) + "…"
        }

        /** 按像素宽度折行；超过 maxLines 的部分在最后一行加省略号 */
        private fun wrapText(p: Paint, text: String, maxWidth: Float, maxLines: Int): List<String> {
            if (text.isEmpty()) return emptyList()
            val out = ArrayList<String>()
            var rest = text
            while (rest.isNotEmpty() && out.size < maxLines) {
                if (out.size == maxLines - 1) {
                    out.add(ellipsize(p, rest, maxWidth))
                    break
                }
                var end = rest.length
                while (end > 0 && p.measureText(rest.substring(0, end)) > maxWidth) end--
                if (end <= 0) break
                out.add(rest.substring(0, end))
                rest = rest.substring(end)
            }
            return out
        }

        private fun withAlpha(argb: Int, alpha: Int): Int =
            (argb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        /** 格子底色偏亮时用深色字，避免白字糊成一片 */
        private fun onColorText(courseColorIndex: Int): Int {
            val argb = courseColor(courseColorIndex).toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val luminance = (r * 299 + g * 587 + b * 114) / 1000
            return if (luminance > 165) 0xFF1B1B20.toInt() else 0xFFFFFFFF.toInt()
        }
    }
}
