package com.anon.schedule.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.CourseBean
import com.anon.schedule.data.DetailBean
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.courseColor
import com.anon.schedule.ui.common.courseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseScreen(courseId: Long, onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val existing = st.courses.firstOrNull { it.id == courseId }
    val existingDetail = st.details.firstOrNull { it.courseId == courseId }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var teacher by remember { mutableStateOf(existingDetail?.teacher ?: "") }
    var room by remember { mutableStateOf(existingDetail?.room ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: (st.courses.size % courseColors.size)) }
    var day by remember { mutableIntStateOf(existingDetail?.day ?: 1) }
    var startNode by remember { mutableIntStateOf(existingDetail?.startNode ?: 1) }
    var step by remember { mutableIntStateOf(existingDetail?.step ?: 2) }
    var startWeek by remember { mutableIntStateOf(existingDetail?.startWeek ?: 1) }
    var endWeek by remember { mutableIntStateOf(existingDetail?.endWeek ?: st.tableSettings().maxWeek) }
    var type by remember { mutableIntStateOf(existingDetail?.type ?: 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "添加课程" else "编辑课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val finalName = name.ifBlank { "未命名课程" }
                        Repo.update { s ->
                            var courses = s.courses
                            var details = s.details
                            if (existing == null) {
                                val cid = Repo.nextId()
                                courses = courses + CourseBean(cid, finalName, color, s.currentTableId)
                                details = details + DetailBean(
                                    id = Repo.nextId(), courseId = cid, day = day, room = room,
                                    teacher = teacher, startNode = startNode, step = step,
                                    startWeek = startWeek, endWeek = endWeek, type = type,
                                    tableId = s.currentTableId
                                )
                            } else {
                                courses = courses.map {
                                    if (it.id == existing.id) it.copy(name = finalName, color = color) else it
                                }
                                details = if (existingDetail == null) details else details.map {
                                    if (it.id == existingDetail.id) it.copy(
                                        day = day, room = room, teacher = teacher,
                                        startNode = startNode, step = step,
                                        startWeek = startWeek, endWeek = endWeek, type = type
                                    ) else it
                                }
                            }
                            s.copy(courses = courses, details = details)
                        }
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = teacher, onValueChange = { teacher = it },
                label = { Text("授课教师") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = room, onValueChange = { room = it },
                label = { Text("上课地点") }, modifier = Modifier.fillMaxWidth()
            )

            Text("课程颜色", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                courseColors.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .clickable { color = i }
                            .then(
                                if (color == i) Modifier.background(
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.0f)
                                ) else Modifier
                            )
                    ) {
                        if (color == i) {
                            Icon(
                                Icons.Default.Check, contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            Text("上课星期", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, d ->
                    FilterChip(
                        selected = day == i + 1,
                        onClick = { day = i + 1 },
                        label = { Text(d) }
                    )
                }
            }

            Text("开始节次", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..st.slots.size).take(8).forEach { n ->
                    FilterChip(selected = startNode == n, onClick = { startNode = n }, label = { Text("$n") })
                }
            }

            Text("持续节数", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    FilterChip(selected = step == n, onClick = { step = n }, label = { Text("$n") })
                }
            }

            Text("上课周次", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startWeek.toString(),
                    onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { startWeek = it.coerceIn(1, 30) } },
                    label = { Text("开始") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endWeek.toString(),
                    onValueChange = { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { endWeek = it.coerceIn(1, 30) } },
                    label = { Text("结束") }, modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("每周", "单周", "双周").forEachIndexed { i, t ->
                    FilterChip(selected = type == i, onClick = { type = i }, label = { Text(t) })
                }
            }

            Button(
                onClick = {
                    val finalName = name.ifBlank { "未命名课程" }
                    Repo.update { s ->
                        var courses = s.courses
                        var details = s.details
                        if (existing == null) {
                            val cid = Repo.nextId()
                            courses = courses + CourseBean(cid, finalName, color, s.currentTableId)
                            details = details + DetailBean(
                                id = Repo.nextId(), courseId = cid, day = day, room = room,
                                teacher = teacher, startNode = startNode, step = step,
                                startWeek = startWeek, endWeek = endWeek, type = type,
                                tableId = s.currentTableId
                            )
                        } else {
                            courses = courses.map {
                                if (it.id == existing.id) it.copy(name = finalName, color = color) else it
                            }
                            details = if (existingDetail == null) details else details.map {
                                if (it.id == existingDetail.id) it.copy(
                                    day = day, room = room, teacher = teacher,
                                    startNode = startNode, step = step,
                                    startWeek = startWeek, endWeek = endWeek, type = type
                                ) else it
                            }
                        }
                        s.copy(courses = courses, details = details)
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }

            Box(Modifier.size(24.dp))
        }
    }
}
