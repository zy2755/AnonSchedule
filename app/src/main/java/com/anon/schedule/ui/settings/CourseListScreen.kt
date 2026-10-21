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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.CourseBean
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.courseColor

/** 已添课程：当前课表里的课程列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val st by Repo.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<CourseBean?>(null) }
    val courses = st.courses.filter { it.tableId == st.currentTableId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已添课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(0L) }) {
                Icon(Icons.Default.Add, contentDescription = "添加课程")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "共 ${courses.size} 门课程，点击可编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(courses, key = { it.id }) { course ->
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onEdit(course.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add, contentDescription = null,
                            tint = courseColor(course.color),
                            modifier = Modifier
                                .size(10.dp)
                                .padding(0.dp)
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(course.name, style = MaterialTheme.typography.bodyLarge)
                            val ds = st.details.filter { it.courseId == course.id }
                            Text(
                                if (ds.isEmpty()) "暂无上课安排"
                                else ds.joinToString("、") {
                                    "周${"一二三四五六日"[it.day - 1]} 第${it.startNode}-${it.startNode + it.step - 1}节"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteTarget = course }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(72.dp)) }
        }
    }

    deleteTarget?.let { course ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除课程") },
            text = { Text("确定删除「${course.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.update { s ->
                        s.copy(
                            courses = s.courses.filterNot { it.id == course.id },
                            details = s.details.filterNot { it.courseId == course.id }
                        )
                    }
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}
