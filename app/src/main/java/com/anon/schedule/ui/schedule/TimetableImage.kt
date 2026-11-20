package com.anon.schedule.ui.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.anon.schedule.data.AppState
import com.anon.schedule.data.CourseLogic
import com.anon.schedule.data.ShownCourse
import com.anon.schedule.data.Settings
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.courseColor
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 把某一周的课表画成一张长图（不依赖 Compose 截图，离屏也能出图）。
 *
 * 之所以自己用 Canvas 画：屏幕上的课表是可滚动的、受可视区域裁剪，
 * 直接截屏只能拿到当前可见的一屏；这里按数据和设置重新排一遍版，
 * 想导出多久的表格都能完整落进一张图里。
 */
object TimetableImage {

    /** 输出缩放：按 2.5 倍画，发到手机上放大看文字依然清晰 */
    private const val SCALE = 2.5f

    private const val PAD = 16f          // 外边距
    private const val TIME_W = 50f       // 时间列宽
    private const val DAY_W = 68f        // 每天一列的宽（比屏幕上的格子宽一些，长课名不至于一行只排两三个字）
    private const val TITLE_H = 54f      // 标题区
    private const val HEAD_H = 40f       // 星期表头
    private const val FOOT_H = 26f       // 底部落款

    private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val dateFmt = DateTimeFormatter.ofPattern("M.d")

    private val textMain = 0xFF1A1A1A.toInt()
    private val textSub = 0xFF8A8F99.toInt()
    private val lineColor = 0xFFE8EAEE.toInt()

    /** 按设置里选中的星期范围排（与周视图一致） */
    private fun visibleDays(s: Settings): List<Int> {
        val days = buildList {
            addAll(1..5)
            if (s.showSaturday) add(6)
            if (s.showSunday) add(7)
        }
        return if (s.sundayFirst && s.showSunday) listOf(7) + days.filter { it != 7 } else days
    }

    /**
     * 画一张第 [week] 周的课表图。
     *
     * @param showTimeColumn 是否带左侧的节次 / 时间列
     */
    fun render(st: AppState, week: Int, tableId: Long, showTimeColumn: Boolean): Bitmap {
        val s = st.tableSettings(tableId)
        val start = s.startDate?.let { LocalDate.ofEpochDay(it) }
        val days = visibleDays(s)
        val rows = s.nodeCount.coerceAtLeast(1)
        val rowH = s.cellHeightDp.coerceAtLeast(40).toFloat()

        val widthDp = PAD * 2 + (if (showTimeColumn) TIME_W else 0f) + DAY_W * days.size
        val heightDp = PAD * 2 + TITLE_H + HEAD_H + rowH * rows + FOOT_H
        val w = (widthDp * SCALE).toInt()
        val h = (heightDp * SCALE).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)   // 之后全部按 dp 画

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawRect(0f, 0f, widthDp, heightDp, bg)

        val left = PAD + (if (showTimeColumn) TIME_W else 0f)
        val gridTop = PAD + TITLE_H + HEAD_H
        val gridW = DAY_W * days.size

        // ---------- 标题 ----------
        val tableName = st.tables.firstOrNull { it.id == tableId }?.name ?: "我的课表"
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain; textSize = 19f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("$tableName · 第 $week 周", PAD, PAD + 22f, title)

        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textSub; textSize = 10.5f }
        val first = start?.let { CourseLogic.dateOfWeekDay(it, week, 1) }
        val last = start?.let { CourseLogic.dateOfWeekDay(it, week, 7) }
        val rangeText = if (first != null && last != null) "${first.format(dateFmt)} - ${last.format(dateFmt)}　" else ""
        val dayItems = days.associateWith { d ->
            dayCourses(st, week, tableId, start, d)
        }
        val courseCount = dayItems.values.flatten().map { it.course.id }.distinct().size
        c.drawText("${rangeText}本周 $courseCount 门课", PAD, PAD + 40f, meta)

        // ---------- 表头 ----------
        val headBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain; textSize = 11.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSub; textSize = 9f; textAlign = Paint.Align.CENTER
        }
        if (showTimeColumn) {
            c.drawText("节次", PAD + TIME_W / 2, PAD + TITLE_H + 18f, headSub)
        }
        days.forEachIndexed { i, d ->
            val cx = left + DAY_W * i + DAY_W / 2
            c.drawText(dayNames[d - 1], cx, PAD + TITLE_H + 17f, headBold)
            start?.let {
                val date = CourseLogic.dateOfWeekDay(it, week, d)
                c.drawText(date.format(dateFmt), cx, PAD + TITLE_H + 31f, headSub)
            }
        }

        // ---------- 网格 ----------
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor; strokeWidth = 0.8f
        }
        for (r in 0..rows) {
            val y = gridTop + rowH * r
            c.drawLine(PAD, y, PAD + (if (showTimeColumn) TIME_W else 0f) + gridW, y, line)
        }
        val vTop = PAD + TITLE_H + HEAD_H - 4f
        for (i in 0..days.size) {
            val x = left + DAY_W * i
            c.drawLine(x, vTop, x, gridTop + rowH * rows, line)
        }
        if (showTimeColumn) c.drawLine(PAD, vTop, PAD, gridTop + rowH * rows, line)

        // ---------- 节次列 ----------
        if (showTimeColumn) {
            val nodeBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSub; textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val nodeTime = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSub; textSize = 7.5f; textAlign = Paint.Align.CENTER
            }
            for (r in 1..rows) {
                val cy = gridTop + rowH * (r - 1) + rowH / 2
                c.drawText(r.toString(), PAD + TIME_W / 2, cy, nodeBold)
                val clock = st.slots.getOrNull(r - 1)?.start
                if (clock != null && rowH >= 46f) {
                    c.drawText(clock, PAD + TIME_W / 2, cy + 11f, nodeTime)
                }
            }
        }

        // ---------- 课程卡片 ----------
        dayItems.forEach { (day, items) ->
            val col = days.indexOf(day)
            if (col < 0) return@forEach
            val x = left + DAY_W * col
            for (item in items) {
                val d = item.detail
                if (d.startNode < 1 || d.startNode > rows) continue
                val span = d.step.coerceAtMost(rows - d.startNode + 1).coerceAtLeast(1)
                val top = gridTop + rowH * (d.startNode - 1) + 1.2f
                val bottom = top + rowH * span - 2.4f
                drawCourse(c, item, s, x + 1.2f, top, x + DAY_W - 1.2f, bottom)
            }
        }

        // ---------- 落款 ----------
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSub; textSize = 8f; textAlign = Paint.Align.RIGHT
        }
        c.drawText(
            "由 AnonSchedule 生成 · ${LocalDate.now().format(dateFmt)}",
            widthDp - PAD, heightDp - PAD + 2f, foot
        )

        return bmp
    }

    /** 这一列要画的课程（只算本周真正要上的，调课/停课/加课都已叠加） */
    private fun dayCourses(
        st: AppState, week: Int, tableId: Long, start: LocalDate?, day: Int
    ): List<ShownCourse> {
        val date = start?.let { CourseLogic.dateOfWeekDay(it, week, day) }
        if (date != null) {
            return CourseLogic.displayDayCourses(st, date, week, tableId, start, showOtherWeek = false)
                .sortedBy { it.detail.startNode }
        }
        // 没设开学日期：没法算临时变动，只按「星期 + 周次」静态筛
        return st.details
            .filter { it.tableId == tableId && it.day == day && CourseLogic.validInWeek(it, week) }
            .sortedBy { it.startNode }
            .mapNotNull { d ->
                st.courses.firstOrNull { it.id == d.courseId }?.let { ShownCourse(d, it, false, null) }
            }
    }

    private fun drawCourse(
        c: Canvas, item: ShownCourse, s: Settings,
        l: Float, t: Float, r: Float, b: Float
    ) {
        val base = courseColor(item.course.color).toArgb()
        val alpha = (s.cellAlphaPercent / 100f).coerceIn(0.2f, 1f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = base
            this.alpha = (alpha * 255).toInt()
        }
        val rect = RectF(l, t, r, b)
        c.drawRoundRect(rect, s.cellRadiusDp.toFloat(), s.cellRadiusDp.toFloat(), fill)

        // 文字颜色：与周视图一致（统一深色字，可自定义，可叠加格子色）
        val custom = s.courseTextColorArgb
        val fg = when {
            custom == null -> 0xFF222222.toInt()
            s.courseTextOverlay -> multiply(custom, base)
            else -> custom
        }

        val padX = 4f
        val textW = (r - l - padX * 2).coerceAtLeast(8f)
        val prefix = when (item.record?.type) {
            1 -> "【加】"
            2 -> "【停】"
            0 -> "【调】"
            else -> ""
        }
        val nameSize = s.courseTextSp.toFloat().coerceIn(9f, 16f)
        val subSize = 8f
        var y = t + 4f

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; textSize = nameSize; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = subSize }

        // 副信息先算好占几行，剩下的高度留给课名
        val subLines = buildList {
            if (s.showTimeInCell) {
                val first = item.detail.startNode
                add("第 $first-${first + item.detail.step.coerceAtLeast(1) - 1} 节")
            }
            if (s.showRoomInCell && item.detail.room.isNotBlank()) add("@" + item.detail.room)
            if (s.showTeacherInCell && item.detail.teacher.isNotBlank()) add(item.detail.teacher)
        }
        val nameBottom = b - 4f - subLines.size * (subSize + 2f)

        val nameLayout = StaticLayout.Builder
            .obtain(prefix + item.course.name, 0, (prefix + item.course.name).length, namePaint, textW.toInt())
            .setMaxLines(((nameBottom - y) / (nameSize + 1.5f)).toInt().coerceAtLeast(1))
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        c.save()
        c.translate(l + padX, y)
        nameLayout.draw(c)
        c.restore()

        y = b - 4f - subLines.size * (subSize + 2f)
        for (text in subLines) {
            c.drawText(ellipsize(text, subPaint, textW), l + padX, y + subSize, subPaint)
            y += subSize + 2f
        }
    }

    /** 单行超宽时补省略号（Canvas 没有现成的 ellipsize） */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    /** 正片叠底（文字色 × 格子色），与周视图的文字叠加规则一致 */
    private fun multiply(fg: Int, bg: Int): Int {
        val fr = (fg shr 16) and 0xFF; val fgG = (fg shr 8) and 0xFF; val fb = fg and 0xFF
        val br = (bg shr 16) and 0xFF; val bgG = (bg shr 8) and 0xFF; val bb = bg and 0xFF
        return (0xFF shl 24) or ((fr * br / 255) shl 16) or ((fgG * bgG / 255) shl 8) or (fb * bb / 255)
    }

    /**
     * 存进相册。Android 10+ 走 MediaStore 免权限，更老的系统写到应用图片目录再让相册扫描一次。
     * @return 成功与否
     */
    fun saveToGallery(ctx: Context, bmp: Bitmap, fileName: String): Boolean = runCatching {
        val q = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        if (q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/课表")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(
                ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir,
                "课表"
            ).apply { mkdirs() }
            val file = File(dir, "$fileName.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        }
        true
    }.getOrDefault(false)
}
