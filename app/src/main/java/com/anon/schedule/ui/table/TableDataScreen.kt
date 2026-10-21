package com.anon.schedule.ui.table

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anon.schedule.SubPage
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.NumberPickerDialog
import com.anon.schedule.ui.common.TextRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-M-d")

/** 课表数据：名称 / 上课时间 / 开学日期 / 当前周 / 节数 / 周数 / 课程管理，照 WakeUp 图五 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDataScreen(
    tableId: Long,
    onBack: () -> Unit,
    onOpenSub: (SubPage) -> Unit
) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings(tableId)
    val table = st.tables.firstOrNull { it.id == tableId }

    var showRename by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickCurrentWeek by remember { mutableStateOf(false) }
    var pickNodeCount by remember { mutableStateOf(false) }
    var pickMaxWeek by remember { mutableStateOf(false) }

    val startDate = s.startDate?.let { LocalDate.ofEpochDay(it) }
    val today = LocalDate.now()
    val semesterEnded = startDate != null &&
            today.isAfter(startDate.plusDays(s.maxWeek.toLong() * 7 - 1))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表数据") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            // 卡片一：课表名称
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                TextRow(
                    title = "课表名称",
                    value = table?.name ?: "我的课表",
                    onClick = { showRename = true }
                )
            }

            // 卡片二：学期数据
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                TextRow(
                    title = "上课时间",
                    value = "点击此处更改",
                    onClick = { onOpenSub(SubPage.TimeTable) }
                )
                TextRow(
                    title = "第一周的第一天",
                    value = if (startDate != null) {
                        "${DateFmt.format(startDate)} 星期一"
                    } else "未设置",
                    onClick = { showDatePicker = true }
                )
                TextRow(
                    title = "当前周",
                    value = if (semesterEnded) "学期已结束" else "第 ${s.currentWeek} 周",
                    onClick = { pickCurrentWeek = true }
                )
                TextRow(
                    title = "一天课程节数",
                    value = "${s.nodeCount} 节",
                    onClick = { pickNodeCount = true }
                )
                TextRow(
                    title = "学期周数",
                    value = "${s.maxWeek} 周",
                    onClick = { pickMaxWeek = true }
                )
            }

            // 卡片三：课程管理
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                TextRow(
                    title = "管理已添加课程",
                    onClick = {
                        Repo.update { it.copy(currentTableId = tableId) }
                        onOpenSub(SubPage.CourseList)
                    }
                )
            }
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(table?.name ?: "我的课表") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改课表名称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("课表名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalName = name.ifBlank { "我的课表" }
                    Repo.update { st2 ->
                        st2.copy(
                            tables = st2.tables.map {
                                if (it.id == tableId) it.copy(name = finalName) else it
                            }
                        )
                    }
                    showRename = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: today)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        // 校正到所在周的周一
                        val monday = picked.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        Repo.updateSettings(tableId) { it.copy(startDate = monday.toEpochDay()) }
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickCurrentWeek) {
        NumberPickerDialog(
            title = "当前周",
            value = s.currentWeek,
            range = 1..s.maxWeek.coerceAtLeast(1),
            suffix = " 周",
            onConfirm = {
                Repo.updateSettings(tableId) { cfg -> cfg.copy(currentWeek = it) }
                pickCurrentWeek = false
            },
            onDismiss = { pickCurrentWeek = false }
        )
    }

    if (pickNodeCount) {
        NumberPickerDialog(
            title = "一天课程节数",
            value = s.nodeCount,
            range = 1..20,
            suffix = " 节",
            onConfirm = {
                Repo.updateSettings(tableId) { cfg -> cfg.copy(nodeCount = it) }
                pickNodeCount = false
            },
            onDismiss = { pickNodeCount = false }
        )
    }

    if (pickMaxWeek) {
        NumberPickerDialog(
            title = "学期周数",
            value = s.maxWeek,
            range = 1..30,
            suffix = " 周",
            onConfirm = {
                Repo.updateSettings(tableId) { cfg ->
                    cfg.copy(maxWeek = it, currentWeek = cfg.currentWeek.coerceIn(1, it))
                }
                pickMaxWeek = false
            },
            onDismiss = { pickMaxWeek = false }
        )
    }
}
