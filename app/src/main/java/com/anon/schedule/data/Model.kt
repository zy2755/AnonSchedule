package com.anon.schedule.data

import kotlinx.serialization.Serializable

/** 单节次的起止时间，如 "08:00" - "08:45" */
@Serializable
data class TimeSlot(val start: String, val end: String)

/** 课表（支持多个课表）。settings=null 表示沿用全局默认配置 */
@Serializable
data class TableBean(
    val id: Long = 0L,
    val name: String = "我的课表",
    val settings: Settings? = null
)

/** 课程基本信息 */
@Serializable
data class CourseBean(
    val id: Long,
    val name: String,
    val color: Int = 0,
    val tableId: Long = 0L
)

/**
 * 课程的一次具体安排（同一门课可有多条）。
 * type: 0=每周  1=单周  2=双周
 */
@Serializable
data class DetailBean(
    val id: Long,
    val courseId: Long,
    val day: Int,            // 1=周一 ... 7=周日
    val room: String = "",
    val teacher: String = "",
    val startNode: Int = 1,
    val step: Int = 1,
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val type: Int = 0,
    val tableId: Long = 0L
)

/** 临时变动记录。type: 0=调课  1=临时加课  2=停课 */
@Serializable
data class ChangeRecord(
    val id: Long,
    val type: Int,
    val fromDate: Long,      // epochDay：调课/停课/加课的日期
    val toDate: Long = 0L,   // epochDay：调课目标日期
    val endDate: Long = 0L,  // epochDay：加课持续到的日期（0 = 仅 fromDate 当天）
    val parity: Int = 0,     // 加课生效周：0=每周 1=单周 2=双周
    val name: String = "",   // 空 = 当天全部课程
    val teacher: String = "",
    val room: String = "",
    val startNode: Int = 1,
    val step: Int = 1
)

/** 一条成绩记录（从教务抓取，按 学年+学期 累积） */
@Serializable
data class GradeBean(
    val id: Long,
    val name: String,                 // 课程名（kcmc）
    val credit: Double = 0.0,         // 学分（xf）
    val score: String = "",           // 原始成绩（可能是「优秀」这类文字）
    val scoreNum: Double? = null,     // 数值成绩，解析不出=null（不计入加权）
    val gradePoint: Double? = null,   // 绩点（jd）
    val xnm: String = "",             // 学年，如 "2024"
    val xqm: String = "",             // 学期，如 "12"
    val teacher: String = ""
)

/** 课表配置（全局 settings 作为默认配置，每课表可有独立配置） */
@Serializable
data class Settings(
    // ---- 课表数据 ----
    val showOtherWeek: Boolean = true,   // 显示非本周课程
    val startDate: Long? = null,         // 开学日期（第一周周一，epochDay），null=未设置
    val maxWeek: Int = 20,               // 本学期总周数
    val currentWeek: Int = 1,            // 当前周数
    val sundayFirst: Boolean = false,    // 每周起始日：false=周一 true=周日
    val nodeCount: Int = 15,             // 一天课程节数
    // ---- 整体外观 ----
    val showGridLines: Boolean = true,          // 显示网格辅助线（颜色跟随界面文字颜色）
    val uiTextColorArgb: Int? = null,           // 界面文字颜色 null=默认主题色
    val headerTextSp: Int = 12,                 // 表头文字大小
    val showSlotTime: Boolean = true,           // 节次栏显示时间
    val showSaturday: Boolean = true,           // 显示周六
    val showSunday: Boolean = true,             // 显示周日
    // ---- 课程格子 ----
    val courseTextColorArgb: Int? = null,       // 课程文字颜色 null=默认统一深色字
    val courseTextOverlay: Boolean = false,     // 文字颜色叠加格子颜色
    val cellBorderColorArgb: Int? = null,       // 格子边框颜色 null=无边框
    val borderUseCellColor: Boolean = false,    // 边框使用格子颜色
    val borderDashed: Boolean = false,          // 格子使用虚线边框
    val cellHeightDp: Int = 58,                 // 课程格子高度
    val cellRadiusDp: Int = 10,                 // 格子圆角半径
    val cellAlphaPercent: Int = 92,             // 课程格子不透明度 %
    val otherWeekAlphaPercent: Int = 20,        // 非本周课程不透明比 %（相对格子不透明度）
    val courseTextSp: Int = 12,                 // 课程显示文字大小
    val textHorizontalCenter: Boolean = false,  // 格子文字水平居中
    val textVerticalCenter: Boolean = false,    // 格子文字竖直居中
    val showTimeInCell: Boolean = false,        // 在格子内显示上课时间
    val showRoomInCell: Boolean = true,         // 在格子内显示上课地点
    val showTeacherInCell: Boolean = true,      // 在格子内显示授课老师
    // ---- 主题 / 背景 ----
    val themeMode: Int = 0,              // 0=跟随系统 1=浅色 2=深色
    val liquidGlass: Boolean = false,    // 液态玻璃
    val backgroundPath: String? = null   // 背景图
)

@Serializable
data class AppState(
    val tables: List<TableBean> = listOf(TableBean()),
    val currentTableId: Long = 0L,
    val courses: List<CourseBean> = emptyList(),
    val details: List<DetailBean> = emptyList(),
    val slots: List<TimeSlot> = defaultSlots(),
    val records: List<ChangeRecord> = emptyList(),
    val grades: List<GradeBean> = emptyList(),  // 教务成绩（多学期累积）
    val gradeTabOrder: List<String> = emptyList(),  // 成绩页标签顺序（"学年|学期"），长按调整后保存
    val settings: Settings = Settings()
)

/** 该课表的生效配置：优先课表自己的配置，否则回落到全局默认配置 */
fun AppState.tableSettings(tableId: Long = currentTableId): Settings =
    tables.firstOrNull { it.id == tableId }?.settings ?: settings

fun defaultSlots(): List<TimeSlot> = listOf(
    TimeSlot("08:40", "09:20"), TimeSlot("09:20", "10:00"),
    TimeSlot("10:05", "10:45"), TimeSlot("10:45", "11:25"),
    TimeSlot("11:30", "12:10"), TimeSlot("13:20", "14:00"),
    TimeSlot("14:00", "14:55"), TimeSlot("15:05", "15:50"),
    TimeSlot("15:55", "16:40"), TimeSlot("18:40", "20:10"),
    TimeSlot("20:30", "21:15"), TimeSlot("21:25", "22:05"),
    TimeSlot("21:35", "22:15"), TimeSlot("21:45", "22:25"),
    TimeSlot("21:55", "22:35")
)
