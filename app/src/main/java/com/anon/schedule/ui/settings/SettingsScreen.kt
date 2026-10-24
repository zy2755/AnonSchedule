package com.anon.schedule.ui.settings

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anon.schedule.SubPage
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GroupCard
import com.anon.schedule.ui.common.GroupLabel
import com.anon.schedule.ui.common.GroupRow
import com.anon.schedule.ui.common.NumberPickerDialog
import com.anon.schedule.ui.common.PageHeader
import com.anon.schedule.ui.common.PillSwitch
import com.anon.schedule.ui.common.TileBlue
import com.anon.schedule.ui.common.TileCyan
import com.anon.schedule.ui.common.TileGreen
import com.anon.schedule.ui.common.TileOrange
import com.anon.schedule.ui.common.TilePink
import com.anon.schedule.ui.common.TilePurple
import com.anon.schedule.ui.common.TileRed
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSub: (SubPage) -> Unit) {
    val st by Repo.state.collectAsState()
    val context = LocalContext.current
    val settings = st.tableSettings()
    val global = st.settings

    var showDatePicker by remember { mutableStateOf(false) }
    var showWeekCount by remember { mutableStateOf(false) }
    var showCurrentWeek by remember { mutableStateOf(false) }
    var showStartDay by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val file = File(context.filesDir, "bg_${st.currentTableId}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                Repo.updateSettings { s -> s.copy(backgroundPath = file.absolutePath) }
            }
        }
    }

    // 备份/恢复：交给系统文件选择器，导出到任意位置、从任意位置读回来
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(Repo.exportJson().toByteArray())
            } ?: error("无法写入所选文件")
        }
        Toast.makeText(
            context,
            if (result.isSuccess) "已导出备份文件" else "导出失败：${result.exceptionOrNull()?.message}",
            Toast.LENGTH_LONG
        ).show()
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val result = if (text == null) Result.failure<Unit>(IllegalStateException("读取文件失败"))
        else Repo.restoreJson(text)
        Toast.makeText(
            context,
            result.fold({ "导入成功，已恢复全部数据" }, { e ->
                // 解析类异常是英文原文，对用户没意义，换成一句能看懂的话；
                // 自己抛的中文原因（读取文件失败 / 没有课表数据）原样展示
                val detail = e.message.orEmpty()
                val friendly = if (detail.none { it in '\u4e00'..'\u9fa5' }) "这不是有效的课表备份文件" else detail
                "导入失败：$friendly"
            }),
            Toast.LENGTH_LONG
        ).show()
    }

    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text("备份与恢复") },
            text = {
                Column {
                    Text(
                        "把课表、课程、设置和成绩一起导出成一个 JSON 文件；导入会覆盖当前全部数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            showBackup = false
                            exportBackup.launch(
                                "课表备份_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".json"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导出到文件") }
                    TextButton(
                        onClick = {
                            showBackup = false
                            importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("从文件导入") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackup = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            PageHeader("设置", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            GroupLabel("通用")
            GroupCard {
                GroupRow(
                    icon = Icons.Default.Layers,
                    tileColor = TileBlue,
                    title = "显示非本周课程",
                    subtitle = if (settings.showOtherWeek) "其它周次的课程也会展示" else "只加载当前周课程",
                    trailing = {
                        PillSwitch(
                            checked = settings.showOtherWeek,
                            onCheckedChange = { on ->
                                Repo.updateSettings { s -> s.copy(showOtherWeek = on) }
                            }
                        )
                    }
                )
                GroupRow(
                    icon = Icons.Default.ViewWeek,
                    tileColor = TileGreen,
                    title = "是否显示周末",
                    subtitle = if (settings.showSaturday && settings.showSunday) "显示周六、周日" else "只显示周一至周五",
                    trailing = {
                        PillSwitch(
                            checked = settings.showSaturday && settings.showSunday,
                            onCheckedChange = { on ->
                                Repo.updateSettings { s -> s.copy(showSaturday = on, showSunday = on) }
                            }
                        )
                    }
                )
            }

            GroupLabel("课表")
            GroupCard {
                GroupRow(
                    icon = Icons.Default.CalendarMonth,
                    tileColor = TileOrange,
                    title = "设置开学日期",
                    subtitle = settings.startDate?.let {
                        "开学日期：" + LocalDate.ofEpochDay(it).format(dateFormatter)
                    } ?: "未设置",
                    onClick = { showDatePicker = true }
                )
                GroupRow(
                    icon = Icons.Default.DateRange,
                    tileColor = TilePurple,
                    title = "本学期总周数",
                    subtitle = "${settings.maxWeek} 周",
                    onClick = { showWeekCount = true }
                )
                GroupRow(
                    icon = Icons.Default.Today,
                    tileColor = TileCyan,
                    title = "当前周数",
                    subtitle = if (settings.startDate == null) "请设置开始日期" else "第 ${settings.currentWeek} 周",
                    onClick = { showCurrentWeek = true }
                )
                GroupRow(
                    icon = Icons.Default.Loop,
                    tileColor = TilePink,
                    title = "设置每周起始日",
                    subtitle = if (settings.sundayFirst) "周日" else "周一",
                    onClick = { showStartDay = true }
                )
            }

            GroupLabel("功能")
            GroupCard {
                GroupRow(
                    icon = Icons.Default.Speed,
                    tileColor = TileRed,
                    title = "快捷操作",
                    subtitle = "调课、临时加课、停课",
                    onClick = { onOpenSub(SubPage.Quick) }
                )
                GroupRow(
                    icon = Icons.Default.AccessTime,
                    tileColor = TileBlue,
                    title = "调课操作",
                    subtitle = "将指定日期的课程调到另一日期",
                    onClick = { onOpenSub(SubPage.Quick) }
                )
                GroupRow(
                    icon = Icons.Default.Backup,
                    tileColor = TileCyan,
                    title = "备份与恢复",
                    subtitle = "导出全部设置为文件，或从文件恢复",
                    onClick = { showBackup = true }
                )
            }

            GroupLabel("外观")
            GroupCard {
                GroupRow(
                    icon = Icons.Default.Palette,
                    tileColor = TilePurple,
                    title = "液态玻璃",
                    subtitle = if (global.liquidGlass) "开启：卡片带模糊与折射" else "关闭：使用半透明纯色卡片",
                    trailing = {
                        PillSwitch(
                            checked = global.liquidGlass,
                            onCheckedChange = { on ->
                                Repo.update { s -> s.copy(settings = s.settings.copy(liquidGlass = on)) }
                            }
                        )
                    }
                )
                GroupRow(
                    icon = Icons.Default.DarkMode,
                    tileColor = TileOrange,
                    title = "主题",
                    subtitle = when (global.themeMode) {
                        1 -> "浅色"
                        2 -> "暗色"
                        else -> "跟随系统"
                    },
                    onClick = { showThemeSheet = true }
                )
                GroupRow(
                    icon = Icons.Default.Image,
                    tileColor = TileGreen,
                    title = "背景图",
                    subtitle = if (settings.backgroundPath != null) "已设置（点击更换）" else "未设置",
                    onClick = { pickImage.launch("image/*") },
                    trailing = {
                        if (settings.backgroundPath != null) {
                            TextButton(onClick = {
                                Repo.updateSettings { s -> s.copy(backgroundPath = null) }
                            }) { Text("清除") }
                        }
                    }
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 32.dp))
        }
    }

    // ---------- 弹层们 ----------

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = settings.startDate?.let {
                LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        // 校正为该日期所在周的周一
                        val monday = date.with(java.time.DayOfWeek.MONDAY)
                        Repo.updateSettings { s ->
                            s.copy(startDate = monday.toEpochDay())
                        }
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showWeekCount) {
        NumberPickerDialog(
            title = "本学期总周数",
            value = settings.maxWeek,
            range = 1..30,
            onConfirm = { v ->
                Repo.updateSettings { s ->
                    s.copy(maxWeek = v, currentWeek = s.currentWeek.coerceAtMost(v))
                }
                showWeekCount = false
            },
            onDismiss = { showWeekCount = false }
        )
    }

    if (showCurrentWeek) {
        if (settings.startDate == null) {
            AlertDialog(
                onDismissRequest = { showCurrentWeek = false },
                title = { Text("当前周数") },
                text = { Text("请设置开始日期后再调整当前周数。") },
                confirmButton = {
                    TextButton(onClick = { showCurrentWeek = false }) { Text("好的") }
                }
            )
        } else {
            NumberPickerDialog(
                title = "当前周数",
                value = settings.currentWeek,
                range = 1..settings.maxWeek,
                onConfirm = { v ->
                    Repo.updateSettings { s -> s.copy(currentWeek = v) }
                    showCurrentWeek = false
                },
                onDismiss = { showCurrentWeek = false }
            )
        }
    }

    if (showStartDay) {
        AlertDialog(
            onDismissRequest = { showStartDay = false },
            title = { Text("设置每周起始日") },
            text = {
                Column {
                    listOf(false to "周一", true to "周日").forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Repo.updateSettings { s -> s.copy(sundayFirst = value) }
                                    showStartDay = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = settings.sundayFirst == value,
                                onClick = {
                                    Repo.updateSettings { s -> s.copy(sundayFirst = value) }
                                    showStartDay = false
                                }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStartDay = false }) { Text("关闭") }
            }
        )
    }

    if (showThemeSheet) {
        val darkDialog = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val dialogBg = if (darkDialog) Color(0xFF1E1B21) else MaterialTheme.colorScheme.surface
        val optionBg = if (darkDialog) Color(0xFF2D2A31) else Color(0xFFF1ECF0)
        val selectedBg = Color(0xFFA8C7FA)
        val selectedFg = Color(0xFF0A305F)
        Dialog(
            onDismissRequest = { showThemeSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    "主题",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "更改主题会保留当前背景设置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                listOf(0 to "跟随系统", 1 to "浅色", 2 to "暗色").forEach { (value, label) ->
                    val selected = global.themeMode == value
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) selectedBg else optionBg)
                            .clickable {
                                Repo.update { s -> s.copy(settings = s.settings.copy(themeMode = value)) }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (selected) "$label · 已选择" else label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) selectedFg else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(optionBg)
                        .clickable { showThemeSheet = false }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "完成",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
