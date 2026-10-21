package com.anon.schedule.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anon.schedule.SubPage
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.NumberPickerDialog
import com.anon.schedule.ui.common.SectionTitle
import com.anon.schedule.ui.common.SettingRow
import com.anon.schedule.ui.common.ThinDivider
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("通用设置")

            SettingRow(
                icon = Icons.Default.Layers,
                title = "显示非本周课程",
                value = if (settings.showOtherWeek) "开启：其它周次课程也会展示" else "关闭：仅加载当前周课程",
                trailing = {
                    Switch(
                        checked = settings.showOtherWeek,
                        onCheckedChange = { on ->
                            Repo.updateSettings { s -> s.copy(showOtherWeek = on) }
                        }
                    )
                }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.ViewWeek,
                title = "是否显示周末",
                value = if (settings.showSaturday && settings.showSunday) "显示周六、周日" else "只显示周一至周五",
                trailing = {
                    Switch(
                        checked = settings.showSaturday && settings.showSunday,
                        onCheckedChange = { on ->
                            Repo.updateSettings { s -> s.copy(showSaturday = on, showSunday = on) }
                        }
                    )
                }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.CalendarMonth,
                title = "设置开学日期",
                value = settings.startDate?.let { "开学日期：" + LocalDate.ofEpochDay(it).format(dateFormatter) }
                    ?: "未设置",
                onClick = { showDatePicker = true }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.DateRange,
                title = "本学期总周数",
                value = "${settings.maxWeek} 周",
                onClick = { showWeekCount = true }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.Today,
                title = "当前周数",
                value = if (settings.startDate == null) "请设置开始日期" else "第 ${settings.currentWeek} 周",
                onClick = {
                    if (settings.startDate == null) showCurrentWeek = true else showCurrentWeek = true
                }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.Loop,
                title = "设置每周起始日",
                value = if (settings.sundayFirst) "周日" else "周一",
                onClick = { showStartDay = true }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.Speed,
                title = "快捷操作",
                value = "调课、临时加课、停课",
                onClick = { onOpenSub(SubPage.Quick) }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.AccessTime,
                title = "调课操作",
                value = "将指定日期的课程调到另一日期",
                onClick = { onOpenSub(SubPage.Quick) }
            )

            SectionTitle("外观设置")

            SettingRow(
                icon = Icons.Default.Palette,
                title = "液态玻璃",
                value = if (global.liquidGlass) "开启" else "关闭",
                trailing = {
                    Switch(
                        checked = global.liquidGlass,
                        onCheckedChange = { on ->
                            Repo.update { s -> s.copy(settings = s.settings.copy(liquidGlass = on)) }
                        }
                    )
                }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.DarkMode,
                title = "主题",
                value = when (global.themeMode) {
                    1 -> "浅色"
                    2 -> "深色"
                    else -> "跟随系统"
                },
                onClick = { showThemeSheet = true }
            )
            ThinDivider()

            SettingRow(
                icon = Icons.Default.Image,
                title = "背景图",
                value = if (settings.backgroundPath != null) "已设置（点击更换）" else "未设置",
                onClick = { pickImage.launch("image/*") },
                trailing = {
                    if (settings.backgroundPath != null) {
                        TextButton(onClick = {
                            Repo.updateSettings { s -> s.copy(backgroundPath = null) }
                        }) { Text("清除") }
                    }
                }
            )

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
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    "主题",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                listOf(1 to "浅色", 2 to "深色", 0 to "跟随系统").forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Repo.update { s -> s.copy(settings = s.settings.copy(themeMode = value)) }
                                showThemeSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(label, Modifier.weight(1f))
                        if (global.themeMode == value) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
