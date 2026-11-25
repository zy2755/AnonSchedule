package com.anon.schedule.ui.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.CourseBean
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.Repo
import com.anon.schedule.data.TodoLogic
import com.anon.schedule.notify.TodoReminder
import com.anon.schedule.ui.common.GlassCard
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * 待办清单：独立于课程的 DDL 清单。
 * 按「已逾期 / 今天 / 即将到期 / 没有日期 / 已完成」分组，支持截止时间与到期提醒。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CourseTodo?>(null) }
    // 每分钟刷新一次「现在」，跨过截止时刻后自动挪进已逾期
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000L)
        }
    }
    val today = LocalDate.now()
    val grouped = TodoLogic.grouped(st.todos, today, now)
    val doneCount = st.todos.count { it.done }
    // LazyListScope 里不是 Composable 作用域，颜色先在外面取好
    val errorColor = MaterialTheme.colorScheme.error
    val accentColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun mutate(transform: (List<CourseTodo>) -> List<CourseTodo>) {
        Repo.update { s -> s.copy(todos = transform(s.todos)) }
        // 待办变了就重排一次到期提醒（勾掉/删除的闹钟会被撤掉）
        TodoReminder.scheduleAll(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待办清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加待办")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    if (st.todos.isEmpty()) "还没有待办" else
                        "未完成 ${TodoLogic.pendingCount(st.todos)} · 已完成 $doneCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (st.todos.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "作业、报告、复习计划都可以记在这里：设一个截止时间，到点会提醒你。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            todoSection("已逾期", grouped.overdue, st.courses, errorColor,
                onToggle = { t, checked -> mutate { list -> list.map { if (it.id == t.id) it.copy(done = checked) else it } } },
                onClick = { editing = it })
            todoSection("今天", grouped.today, st.courses, accentColor,
                onToggle = { t, checked -> mutate { list -> list.map { if (it.id == t.id) it.copy(done = checked) else it } } },
                onClick = { editing = it })
            todoSection("即将到期", grouped.upcoming, st.courses, mutedColor,
                onToggle = { t, checked -> mutate { list -> list.map { if (it.id == t.id) it.copy(done = checked) else it } } },
                onClick = { editing = it })
            todoSection("没有日期", grouped.noDate, st.courses, mutedColor,
                onToggle = { t, checked -> mutate { list -> list.map { if (it.id == t.id) it.copy(done = checked) else it } } },
                onClick = { editing = it })
            todoSection("已完成", grouped.done, st.courses, mutedColor,
                onToggle = { t, checked -> mutate { list -> list.map { if (it.id == t.id) it.copy(done = checked) else it } } },
                onClick = { editing = it })
        }
    }

    if (creating || editing != null) {
        TodoEditDialog(
            todo = editing,
            courses = st.courses.filter { it.tableId == st.currentTableId },
            onDismiss = { creating = false; editing = null },
            onSave = { saved ->
                mutate { list ->
                    if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it }
                    else list + saved
                }
                creating = false
                editing = null
            },
            onDelete = { target ->
                mutate { list -> list.filterNot { it.id == target.id } }
                editing = null
            }
        )
    }
}

/** 一组待办（标题 + 卡片） */
private fun LazyListScope.todoSection(
    title: String,
    list: List<CourseTodo>,
    courses: List<CourseBean>,
    titleColor: Color,
    onToggle: (CourseTodo, Boolean) -> Unit,
    onClick: (CourseTodo) -> Unit
) {
    if (list.isEmpty()) return
    item(key = "header_$title") {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
        )
    }
    items(list, key = { "${title}_${it.id}" }) { todo ->
        val today = LocalDate.now()
        val dueLabel = TodoLogic.dueLabel(todo, today)
        val overdue = TodoLogic.isOverdue(todo, today, LocalTime.now())
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClick(todo) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle(todo, it) })
            Column(Modifier.weight(1f)) {
                Text(
                    todo.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                val courseName = courses.firstOrNull { it.id == todo.courseId }?.name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!courseName.isNullOrBlank()) {
                        Text(
                            courseName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        dueLabel,
                        fontSize = 11.sp,
                        color = when {
                            overdue -> MaterialTheme.colorScheme.error
                            todo.dueDate == today.toEpochDay() -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (todo.remind && todo.dueDate > 0L && !todo.done) {
                        Spacer(Modifier.width(6.dp))
                        Text("提醒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
    }
}

/** 新建 / 编辑待办：标题、所属课程、截止日期、截止时刻、到期提醒 */
@Composable
private fun TodoEditDialog(
    todo: CourseTodo?,
    courses: List<CourseBean>,
    onDismiss: () -> Unit,
    onSave: (CourseTodo) -> Unit,
    onDelete: (CourseTodo) -> Unit
) {
    val context = LocalContext.current
    var text by remember(todo?.id) { mutableStateOf(todo?.text.orEmpty()) }
    var courseId by remember(todo?.id) { mutableLongStateOf(todo?.courseId ?: 0L) }
    var dueDate by remember(todo?.id) { mutableLongStateOf(todo?.dueDate ?: 0L) }
    var dueMinute by remember(todo?.id) { mutableIntStateOf(todo?.dueMinute ?: -1) }
    var remind by remember(todo?.id) { mutableStateOf(todo?.remind ?: false) }
    var showCourseMenu by remember { mutableStateOf(false) }

    val selectedCourse = courses.firstOrNull { it.id == courseId }
    val date = if (dueDate > 0L) LocalDate.ofEpochDay(dueDate) else null
    val dateLabel = date?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: "不设截止日期"
    val timeLabel = if (dueMinute >= 0) "%02d:%02d".format(dueMinute / 60, dueMinute % 60) else "全天"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "添加待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("待办内容") },
                    placeholder = { Text("比如「交实验报告」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                // 所属课程（可选）：默认是通用待办
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showCourseMenu = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "所属课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            selectedCourse?.name ?: "通用待办",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = showCourseMenu, onDismissRequest = { showCourseMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("通用待办") },
                            onClick = { courseId = 0L; showCourseMenu = false }
                        )
                        courses.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { courseId = c.id; showCourseMenu = false }
                            )
                        }
                    }
                }
                // 截止日期
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val base = date ?: LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    dueDate = LocalDate.of(y, m + 1, d).toEpochDay()
                                },
                                base.year, base.monthValue - 1, base.dayOfMonth
                            ).show()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "截止日期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(dateLabel, style = MaterialTheme.typography.bodyMedium)
                }
                // 截止时刻（设了日期才可点）
                if (dueDate > 0L) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val h = if (dueMinute >= 0) dueMinute / 60 else 18
                                val m = if (dueMinute >= 0) dueMinute % 60 else 0
                                TimePickerDialog(
                                    context,
                                    { _, hh, mm -> dueMinute = hh * 60 + mm },
                                    h, m, true
                                ).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "截止时刻",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(timeLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("到期提醒", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (dueMinute >= 0) "到点发通知提醒你" else "当天 9:00 提醒（全天待办）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { dueDate = 0L; dueMinute = -1; remind = false }) {
                            Text("清除截止时间")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalText = text.trim()
                    if (finalText.isEmpty()) return@TextButton
                    onSave(
                        CourseTodo(
                            id = todo?.id ?: Repo.nextId(),
                            courseId = courseId,
                            text = finalText,
                            done = todo?.done ?: false,
                            createdAt = todo?.createdAt ?: System.currentTimeMillis(),
                            dueDate = dueDate,
                            dueMinute = if (dueDate > 0L) dueMinute else -1,
                            remind = remind && dueDate > 0L
                        )
                    )
                },
                enabled = text.isNotBlank()
            ) { Text(if (todo == null) "添加" else "保存") }
        },
        dismissButton = {
            Row {
                if (todo != null) {
                    TextButton(onClick = { onDelete(todo) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
