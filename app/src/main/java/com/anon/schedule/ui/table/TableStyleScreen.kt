package com.anon.schedule.ui.table

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.NumberPickerDialog
import com.anon.schedule.ui.common.SectionTitle
import com.anon.schedule.ui.common.TextRow
import com.anon.schedule.ui.common.courseColors
import com.anon.schedule.ui.schedule.WeekGrid
import java.io.File
import kotlin.math.roundToInt

private val presetColors = courseColors + listOf(
    Color.Black, Color.White, Color(0xFF424242), Color(0xFFBDBDBD)
)

/** 课表外观：整体 + 课程格子共 23 项自定义，照 WakeUp 图六 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableStyleScreen(tableId: Long, onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings(tableId)
    val context = LocalContext.current

    var pickUiColor by remember { mutableStateOf(false) }
    var pickCourseColor by remember { mutableStateOf(false) }
    var pickBorderColor by remember { mutableStateOf(false) }
    var pickHeaderSp by remember { mutableStateOf(false) }
    var pickCourseSp by remember { mutableStateOf(false) }
    var pickCellHeight by remember { mutableStateOf(false) }
    var pickCellRadius by remember { mutableStateOf(false) }
    var pickCellAlpha by remember { mutableStateOf(false) }
    var pickOtherAlpha by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val file = File(context.filesDir, "bg_$tableId.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                Repo.updateSettings(tableId) { it.copy(backgroundPath = file.absolutePath) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表外观") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 上半屏：真实课表实时预览，设置一改立刻反映
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            ) {
                val bg by produceState<ImageBitmap?>(null, s.backgroundPath) {
                    value = s.backgroundPath?.let {
                        runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
                    }
                }
                bg?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.9f,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp)
                ) {
                    WeekGrid(
                        st = st,
                        week = s.currentWeek,
                        settings = s,
                        tableId = tableId,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 下半屏：设置列表（独立滚动，预览始终停在上半屏）
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionTitle("整体")
            GlassCard {
                TextRow(
                    title = "课程表背景",
                    desc = "长按可以恢复默认哦~",
                    onClick = { pickImage.launch("image/*") },
                    onLongClick = {
                        Repo.updateSettings(tableId) { it.copy(backgroundPath = null) }
                    }
                )
                RowDivider()
                TextRow(
                    title = "显示网格辅助线",
                    desc = "颜色跟随标题颜色哦，也就是下面这个选项",
                    trailing = {
                        Switch(
                            checked = s.showGridLines,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showGridLines = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "界面文字颜色",
                    desc = "界面文字、辅助线的颜色 还可以调颜色的透明度哦 (●´ω｀●)",
                    onClick = { pickUiColor = true },
                    trailing = { ColorSwatch(s.uiTextColorArgb) }
                )
                RowDivider()
                TextRow(
                    title = "表头文字大小",
                    value = "${s.headerTextSp} sp",
                    onClick = { pickHeaderSp = true }
                )
                RowDivider()
                TextRow(
                    title = "节次栏显示时间",
                    trailing = {
                        Switch(
                            checked = s.showSlotTime,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showSlotTime = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "显示周六",
                    trailing = {
                        Switch(
                            checked = s.showSaturday,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showSaturday = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "显示周日",
                    trailing = {
                        Switch(
                            checked = s.showSunday,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showSunday = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "显示非本周课程",
                    trailing = {
                        Switch(
                            checked = s.showOtherWeek,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showOtherWeek = on) } }
                        )
                    }
                )
            }

            SectionTitle("课程格子")
            GlassCard {
                TextRow(
                    title = "课程文字颜色",
                    desc = "指课程格子内的文字颜色 还可以调颜色的透明度哦",
                    onClick = { pickCourseColor = true },
                    trailing = { ColorSwatch(s.courseTextColorArgb) }
                )
                RowDivider()
                TextRow(
                    title = "课程文字颜色叠加格子颜色",
                    desc = "文字颜色设为半透明的黑色，效果会更明显哦",
                    trailing = {
                        Switch(
                            checked = s.courseTextOverlay,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(courseTextOverlay = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "格子边框颜色",
                    desc = "将不透明度调到最低就可以隐藏边框了哦",
                    onClick = { pickBorderColor = true },
                    trailing = { ColorSwatch(s.cellBorderColorArgb) }
                )
                RowDivider()
                TextRow(
                    title = "边框使用格子颜色",
                    desc = "不透明度跟随上面的设置",
                    trailing = {
                        Switch(
                            checked = s.borderUseCellColor,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(borderUseCellColor = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "格子使用虚线边框",
                    trailing = {
                        Switch(
                            checked = s.borderDashed,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(borderDashed = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "课程格子高度",
                    value = "${s.cellHeightDp} dp",
                    onClick = { pickCellHeight = true }
                )
                RowDivider()
                TextRow(
                    title = "格子圆角半径",
                    value = "${s.cellRadiusDp} dp",
                    onClick = { pickCellRadius = true }
                )
                RowDivider()
                TextRow(
                    title = "课程格子不透明度",
                    value = "${s.cellAlphaPercent} %",
                    onClick = { pickCellAlpha = true }
                )
                RowDivider()
                TextRow(
                    title = "非本周课程不透明比",
                    value = "${s.otherWeekAlphaPercent} %",
                    onClick = { pickOtherAlpha = true }
                )
                RowDivider()
                TextRow(
                    title = "课程显示文字大小",
                    value = "${s.courseTextSp} sp",
                    onClick = { pickCourseSp = true }
                )
                RowDivider()
                TextRow(
                    title = "格子文字水平居中",
                    trailing = {
                        Switch(
                            checked = s.textHorizontalCenter,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(textHorizontalCenter = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "格子文字竖直居中",
                    trailing = {
                        Switch(
                            checked = s.textVerticalCenter,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(textVerticalCenter = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "在格子内显示上课时间",
                    trailing = {
                        Switch(
                            checked = s.showTimeInCell,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showTimeInCell = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "在格子内显示上课地点",
                    trailing = {
                        Switch(
                            checked = s.showRoomInCell,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showRoomInCell = on) } }
                        )
                    }
                )
                RowDivider()
                TextRow(
                    title = "在格子内显示授课老师",
                    trailing = {
                        Switch(
                            checked = s.showTeacherInCell,
                            onCheckedChange = { on -> Repo.updateSettings(tableId) { it.copy(showTeacherInCell = on) } }
                        )
                    }
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (pickUiColor) {
        ColorPickerDialog(
            title = "界面文字颜色",
            current = s.uiTextColorArgb,
            onPick = { argb ->
                Repo.updateSettings(tableId) { it.copy(uiTextColorArgb = argb) }
                pickUiColor = false
            },
            onDismiss = { pickUiColor = false }
        )
    }
    if (pickCourseColor) {
        ColorPickerDialog(
            title = "课程文字颜色",
            current = s.courseTextColorArgb,
            onPick = { argb ->
                Repo.updateSettings(tableId) { it.copy(courseTextColorArgb = argb) }
                pickCourseColor = false
            },
            onDismiss = { pickCourseColor = false }
        )
    }
    if (pickBorderColor) {
        ColorPickerDialog(
            title = "格子边框颜色",
            current = s.cellBorderColorArgb,
            onPick = { argb ->
                Repo.updateSettings(tableId) { it.copy(cellBorderColorArgb = argb) }
                pickBorderColor = false
            },
            onDismiss = { pickBorderColor = false }
        )
    }
    if (pickHeaderSp) {
        NumberPickerDialog(
            title = "表头文字大小",
            value = s.headerTextSp,
            range = 8..20,
            suffix = " sp",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(headerTextSp = v) }; pickHeaderSp = false },
            onDismiss = { pickHeaderSp = false }
        )
    }
    if (pickCourseSp) {
        NumberPickerDialog(
            title = "课程显示文字大小",
            value = s.courseTextSp,
            range = 8..20,
            suffix = " sp",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(courseTextSp = v) }; pickCourseSp = false },
            onDismiss = { pickCourseSp = false }
        )
    }
    if (pickCellHeight) {
        NumberPickerDialog(
            title = "课程格子高度",
            value = s.cellHeightDp,
            range = 40..100,
            suffix = " dp",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(cellHeightDp = v) }; pickCellHeight = false },
            onDismiss = { pickCellHeight = false }
        )
    }
    if (pickCellRadius) {
        NumberPickerDialog(
            title = "格子圆角半径",
            value = s.cellRadiusDp,
            range = 0..20,
            suffix = " dp",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(cellRadiusDp = v) }; pickCellRadius = false },
            onDismiss = { pickCellRadius = false }
        )
    }
    if (pickCellAlpha) {
        NumberPickerDialog(
            title = "课程格子不透明度",
            value = s.cellAlphaPercent,
            range = 0..100,
            suffix = " %",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(cellAlphaPercent = v) }; pickCellAlpha = false },
            onDismiss = { pickCellAlpha = false }
        )
    }
    if (pickOtherAlpha) {
        NumberPickerDialog(
            title = "非本周课程不透明比",
            value = s.otherWeekAlphaPercent,
            range = 0..100,
            suffix = " %",
            onConfirm = { v -> Repo.updateSettings(tableId) { it.copy(otherWeekAlphaPercent = v) }; pickOtherAlpha = false },
            onDismiss = { pickOtherAlpha = false }
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
}

/** 取色小方块；null = 默认 */
@Composable
private fun ColorSwatch(argb: Int?) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (argb != null) Color(argb) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(7.dp)
            )
    )
}

/** 预设色板 + 透明度滑条的取色弹窗；「默认」= 跟随主题 */
@Composable
private fun ColorPickerDialog(
    title: String,
    current: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var rgb by remember { mutableIntStateOf((current ?: 0xFF000000.toInt()) and 0x00FFFFFF) }
    var alpha by remember { mutableIntStateOf(if (current == null) 255 else (current ushr 24) and 0xFF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color((alpha shl 24) or rgb))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                    Text(
                        "不透明度 $alpha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Slider(
                    value = alpha / 255f,
                    onValueChange = { alpha = (it * 255f).roundToInt().coerceIn(0, 255) }
                )
                presetColors.chunked(6).forEach { rowColors ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        rowColors.forEach { c ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { rgb = c.toArgb() and 0x00FFFFFF }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick((alpha shl 24) or rgb) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
