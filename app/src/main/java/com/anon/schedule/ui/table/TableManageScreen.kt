package com.anon.schedule.ui.table

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.TableBean
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.courseColor
import java.time.LocalDate
import kotlin.math.roundToInt

private val ThumbHeight = 118.dp
private val BarHeight = 56.dp
private val CardGap = 20.dp

/** 多课表管理：卡片列表（缩略图 + 名称 + 编辑/删除），长按拖动排序，右下新建 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TableManageScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCourses: (Long) -> Unit
) {
    val st by Repo.state.collectAsState()
    val context = LocalContext.current
    // 归档的旧学期不在主列表里，单独沉到最下面的「已归档学期」
    val active = st.tables.filterNot { it.archived }
    val archived = st.tables.filter { it.archived }
    var showCreate by remember { mutableStateOf(false) }
    var showRollover by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TableBean?>(null) }

    val itemHeightPx = with(LocalDensity.current) { (ThumbHeight + BarHeight + CardGap).toPx() }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("多课表管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建课表")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(CardGap)
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "点击卡片查看该课表的课程\n长按拖动排序",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
                    )
                    // 学期结束：归档当前课表并按样式新建一张空课表
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { showRollover = true }) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("结束本学期并新建")
                        }
                    }
                }
            }
            items(active, key = { it.id }) { table ->
                TableCard(
                    table = table,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = if (table.id == dragId) dragOffset else 0f
                            if (table.id == dragId) shadowElevation = 10.dp.toPx()
                        }
                        .pointerInput(table.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragId = table.id
                                    dragOffset = 0f
                                },
                                onDragEnd = { dragId = null; dragOffset = 0f },
                                onDragCancel = { dragId = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    // 只排未归档的课表；归档的旧学期固定沉在列表末尾
                                    val list = Repo.state.value.tables.filterNot { it.archived }
                                    val from = list.indexOfFirst { it.id == dragId }
                                    if (from >= 0) {
                                        val to = (from + (dragOffset / itemHeightPx).roundToInt())
                                            .coerceIn(0, list.lastIndex)
                                        if (to != from) {
                                            Repo.update { s ->
                                                val act = s.tables.filterNot { it.archived }.toMutableList()
                                                val arch = s.tables.filter { it.archived }
                                                act.add(to, act.removeAt(from))
                                                s.copy(tables = act + arch)
                                            }
                                            dragOffset -= (to - from) * itemHeightPx
                                        }
                                    }
                                }
                            )
                        },
                    onClick = { onOpenCourses(table.id) },
                    onEdit = { onEdit(table.id) },
                    onDelete = {
                        if (active.size <= 1) {
                            Toast.makeText(context, "至少要保留一张课表哦～", Toast.LENGTH_SHORT).show()
                        } else {
                            deleteTarget = table
                        }
                    }
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        "已归档学期",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 2.dp)
                    )
                }
                items(archived, key = { it.id }) { table ->
                    ArchivedRow(
                        table = table,
                        courseCount = st.details.count { it.tableId == table.id },
                        onRestore = {
                            Repo.update { s ->
                                s.copy(tables = s.tables.map {
                                    if (it.id == table.id) it.copy(archived = false, semesterName = "") else it
                                })
                            }
                        },
                        onDelete = { deleteTarget = table }
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("新课表") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建课表") },
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
                    val id = Repo.nextId()
                    val finalName = name.ifBlank { "新课表" }
                    Repo.update { s ->
                        s.copy(
                            tables = s.tables + TableBean(id = id, name = finalName),
                            currentTableId = id
                        )
                    }
                    showCreate = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
    }

    if (showRollover) {
        var semesterName by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("新学期课表") }
        AlertDialog(
            onDismissRequest = { showRollover = false },
            title = { Text("结束本学期") },
            text = {
                Column {
                    Text(
                        "当前课表会归档保存（课程与设置都不动），新学期按现在的样式新建一张空课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = semesterName,
                        onValueChange = { semesterName = it },
                        singleLine = true,
                        label = { Text("学期名称（如 2025 秋季）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("新学期的课表名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val stNow = Repo.state.value
                    val oldTable = stNow.tables.firstOrNull { it.id == stNow.currentTableId }
                    if (oldTable != null) {
                        // 把当前生效的配置固化到旧课表上：以后改全局默认不会影响归档学期的显示
                        val eff = stNow.tableSettings(oldTable.id)
                        val today = LocalDate.now()
                        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                        val newId = Repo.nextId()
                        val sem = semesterName.ifBlank { "未命名学期" }
                        val newTable = TableBean(
                            id = newId,
                            name = newName.ifBlank { "新学期课表" },
                            settings = eff.copy(startDate = monday.toEpochDay(), currentWeek = 1)
                        )
                        Repo.update { s ->
                            s.copy(
                                tables = s.tables.map {
                                    if (it.id == oldTable.id)
                                        it.copy(settings = eff, archived = true, semesterName = sem)
                                    else it
                                } + newTable,
                                currentTableId = newId
                            )
                        }
                    }
                    showRollover = false
                }) { Text("归档并新建") }
            },
            dismissButton = {
                TextButton(onClick = { showRollover = false }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除课表") },
            text = { Text("确定要删除「${target.name}」吗？课表里的课程也会一起删除哦。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.update { s ->
                        val rest = s.tables.filter { it.id != target.id }
                        var newTables = rest
                        var cur = s.currentTableId
                        // 删的是当前课表：优先切到另一张未归档的；只剩归档时就新建一张空课表
                        if (s.currentTableId == target.id) {
                            val firstActive = rest.firstOrNull { !it.archived }
                            if (firstActive != null) {
                                cur = firstActive.id
                            } else {
                                val fresh = TableBean()
                                newTables = rest + fresh
                                cur = fresh.id
                            }
                        }
                        if (newTables.isEmpty()) newTables = listOf(TableBean())
                        s.copy(
                            tables = newTables,
                            currentTableId = cur,
                            courses = s.courses.filter { it.tableId != target.id },
                            details = s.details.filter { it.tableId != target.id },
                            todos = s.todos.filter { t -> s.courses.none { it.id == t.courseId && it.tableId == target.id } }
                        )
                    }
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/** 已归档学期：学期名 + 课程数 + 恢复 / 删除 */
@Composable
private fun ArchivedRow(
    table: TableBean,
    courseCount: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                table.semesterName.ifBlank { "已归档学期" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${table.name} · $courseCount 门课",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRestore) { Text("恢复") }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除归档",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 课表卡片：上半缩略图 + 下半名称与编辑/删除 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableCard(
    table: TableBean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = {})
    ) {
        MiniTableThumb(
            tableId = table.id,
            modifier = Modifier
                .fillMaxWidth()
                .height(ThumbHeight)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .padding(start = 16.dp, end = 4.dp)
        ) {
            Text(
                table.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = Color(0xFF4C8DFF),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFE55353),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 课表迷你缩略图（多课表管理卡片 & 课表外观预览共用） */
@Composable
fun MiniTableThumb(tableId: Long, modifier: Modifier = Modifier) {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings(tableId)
    val details = st.details.filter { it.tableId == tableId }
    val rows = s.nodeCount.coerceAtLeast(1)
    val days = listOf(1, 2, 3, 4, 5) + buildList {
        if (s.showSaturday) add(6)
        if (s.showSunday) add(7)
    }

    Box(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFFDCE6F5), Color(0xFFF8FBFF)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val colW = size.width / days.size
            val rowH = size.height / rows

            // 网格辅助线
            if (s.showGridLines) {
                val line = Color(0x334A5568)
                for (i in 1 until days.size) {
                    drawLine(line, Offset(i * colW, 0f), Offset(i * colW, size.height), 1f)
                }
                for (i in 1 until rows) {
                    drawLine(line, Offset(0f, i * rowH), Offset(size.width, i * rowH), 1f)
                }
            }

            // 课程格子
            for (d in details) {
                val col = days.indexOf(d.day)
                if (col < 0) continue
                // 起始节越界时先夹回网格内，再算跨度：保证 coerceIn 区间非空（旧写法 startNode>rows 会抛异常）
                val start = (d.startNode - 1).coerceIn(0, rows - 1)
                val top = start * rowH
                val h = d.step.coerceIn(1, maxOf(1, rows - start)) * rowH
                val isOther = !Repo.validInWeek(d, s.currentWeek)
                val base = courseColor(Repo.courseOf(st, d)?.color ?: 0)
                val alpha = (s.cellAlphaPercent / 100f) *
                        (if (isOther) s.otherWeekAlphaPercent / 100f else 1f)
                drawRoundRect(
                    color = base.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(col * colW + 1.5f, top + 1.5f),
                    size = Size(colW - 3f, h - 3f),
                    cornerRadius = CornerRadius(minOf(s.cellRadiusDp.dp.toPx(), rowH / 3f))
                )
            }
        }
    }
}
