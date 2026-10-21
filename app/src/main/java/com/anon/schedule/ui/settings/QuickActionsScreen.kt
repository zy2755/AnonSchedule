package com.anon.schedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.ChangeRecord
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsScreen(onBack: () -> Unit) {
    val st by Repo.state.collectAsState()

    var showAdjust by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSuspend by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快捷操作") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---------- 调课 ----------
            item {
                SectionTitle("调课操作 · 将指定日期的课程调到另一日期")
            }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("一次可加多条，调整完会留记录", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { showAdjust = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text("添加调课", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            items(st.records.filter { it.type == 0 }, key = { "a${it.id}" }) { r ->
                RecordCard(
                    title = "${LocalDate.ofEpochDay(r.fromDate).format(dateFmt)} → ${LocalDate.ofEpochDay(r.toDate).format(dateFmt)}",
                    subtitle = if (r.name.isEmpty()) "当天全部课程整体调整" else r.name,
                    onDelete = {
                        Repo.update { s -> s.copy(records = s.records.filterNot { it.id == r.id }) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ---------- 临时加课 ----------
            item { SectionTitle("临时加课") }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("给指定日期临时加一节课", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text("添加课程", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            items(st.records.filter { it.type == 1 }, key = { "b${it.id}" }) { r ->
                RecordCard(
                    title = "${LocalDate.ofEpochDay(r.fromDate).format(dateFmt)} · ${r.name}",
                    subtitle = "第 ${r.startNode}-${r.startNode + r.step - 1} 节 · ${r.room} · ${r.teacher}",
                    onDelete = {
                        Repo.update { s -> s.copy(records = s.records.filterNot { it.id == r.id }) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ---------- 停课 ----------
            item { SectionTitle("停课") }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("指定日期停课，留空课程名 = 当天全部", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { showSuspend = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text("添加停课", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            items(st.records.filter { it.type == 2 }, key = { "c${it.id}" }) { r ->
                RecordCard(
                    title = "${LocalDate.ofEpochDay(r.fromDate).format(dateFmt)} 停课",
                    subtitle = if (r.name.isEmpty()) "当天全部课程" else r.name,
                    onDelete = {
                        Repo.update { s -> s.copy(records = s.records.filterNot { it.id == r.id }) }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item { Spacer(Modifier.size(32.dp)) }
        }
    }

    if (showAdjust) {
        AdjustDialog(
            onDismiss = { showAdjust = false },
            onConfirm = { from, to ->
                Repo.update { s ->
                    s.copy(records = s.records + ChangeRecord(
                        id = Repo.nextId(), type = 0,
                        fromDate = from.toEpochDay(), toDate = to.toEpochDay()
                    ))
                }
                showAdjust = false
            }
        )
    }

    if (showAdd) {
        AddCourseDialog(
            onDismiss = { showAdd = false },
            onConfirm = { r ->
                Repo.update { s -> s.copy(records = s.records + r) }
                showAdd = false
            }
        )
    }

    if (showSuspend) {
        SuspendDialog(
            onDismiss = { showSuspend = false },
            onConfirm = { r ->
                Repo.update { s -> s.copy(records = s.records + r) }
                showSuspend = false
            }
        )
    }
}

@Composable
private fun RecordCard(title: String, subtitle: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 日期选择 + 两个日期的调课对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    var fromDate by remember { mutableStateOf(LocalDate.now()) }
    var toDate by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var picking by remember { mutableIntStateOf(0) } // 0 无 1 from 2 to

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加调课") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fromDate.format(dateFmt),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("原日期") },
                    trailingIcon = {
                        IconButton(onClick = { picking = 1 }) {
                            Icon(Icons.Default.CalendarMonth, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toDate.format(dateFmt),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("调至日期") },
                    trailingIcon = {
                        IconButton(onClick = { picking = 2 }) {
                            Icon(Icons.Default.CalendarMonth, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "该日期当天的全部课程会整体挪到目标日期，可在课表中查看效果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(fromDate, toDate) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (picking != 0) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (picking == 1) fromDate else toDate)
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (picking == 1) fromDate = d else toDate = d
                    }
                    picking = 0
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = 0 }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseDialog(onDismiss: () -> Unit, onConfirm: (ChangeRecord) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var startNode by remember { mutableStateOf("1") }
    var step by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("临时加课") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date.format(dateFmt), onValueChange = {}, readOnly = true,
                    label = { Text("日期") },
                    trailingIcon = {
                        IconButton(onClick = { picking = true }) { Icon(Icons.Default.CalendarMonth, null) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("授课教师") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("上课地点") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startNode,
                        onValueChange = { v -> startNode = v.filter { it.isDigit() }.take(2) },
                        label = { Text("开始节次") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = step,
                        onValueChange = { v -> step = v.filter { it.isDigit() }.take(2) },
                        label = { Text("持续节数") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    ChangeRecord(
                        id = Repo.nextId(), type = 1,
                        fromDate = date.toEpochDay(),
                        name = name.ifBlank { "临时加课" },
                        teacher = teacher, room = room,
                        startNode = (startNode.toIntOrNull() ?: 1).coerceIn(1, 12),
                        step = (step.toIntOrNull() ?: 1).coerceIn(1, 6)
                    )
                )
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    picking = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuspendDialog(onDismiss: () -> Unit, onConfirm: (ChangeRecord) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("停课") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date.format(dateFmt), onValueChange = {}, readOnly = true,
                    label = { Text("日期") },
                    trailingIcon = {
                        IconButton(onClick = { picking = true }) { Icon(Icons.Default.CalendarMonth, null) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名称（留空 = 当天全部课程）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    ChangeRecord(
                        id = Repo.nextId(), type = 2,
                        fromDate = date.toEpochDay(),
                        name = name.trim()
                    )
                )
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    picking = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }
}
