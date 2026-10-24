package com.anon.schedule.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.courseColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun TodayScreen() {
    val st by Repo.state.collectAsState()
    val s = st.tableSettings()
    val today = LocalDate.now()
    val week = Repo.weekOf(st, today) ?: s.currentWeek
    // 今日课表只展示本周课程：过滤掉非本周（isOther）的卡片
    val items = Repo.displayDayCourses(st, today, week)
        .filterNot { it.isOther }
        .sortedBy { it.detail.startNode }
    val formatter = DateTimeFormatter.ofPattern("M月d日")
    // 当前时间：每 30 秒刷新一次，用于把今天「已经上过」的课程名划掉
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000L)
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 6.dp)) {
                Text(
                    "今日课表",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${today.format(formatter)} · ${"周" + "一二三四五六日"[today.dayOfWeek.value - 1]}" +
                        if (s.startDate != null) " · 第 $week 周" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (items.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        "今天没有课～去玩吧！（要是这不对劲，记得先在设置里设置开学日期哦）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(items, key = { it.detail.id }) { shown ->
            val d = shown.detail
            // 该课最后一节的结束时间已过 → 视为今天已经上过，课程名加删除线
            val endTime = st.slots.getOrNull(d.startNode + d.step - 2)?.end
                ?: st.slots.getOrNull(d.startNode - 1)?.end
            val passed = endTime?.let { parseTimeOrNull(it) }?.let { it <= now } == true
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(end = 14.dp)
                    ) {
                        Text(
                            "${d.startNode}-${d.startNode + d.step - 1}节",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = courseColor(shown.course.color)
                        )
                        val first = st.slots.getOrNull(d.startNode - 1)
                        val last = st.slots.getOrNull(d.startNode + d.step - 2) ?: first
                        if (first != null) {
                            Text(first.start, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(last?.end ?: first.end, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        // 已上过的课：课程名加深色删除线。
                        // TextDecoration 自带的线太细（约等于字号/10），这里按每行实测范围手绘一条明显更粗的线压上去。
                        var nameLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                        val strikeColor = LocalContentColor.current
                        Text(
                            shown.course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (passed) TextDecoration.LineThrough else null,
                            onTextLayout = { nameLayout = it },
                            modifier = if (passed) Modifier.drawWithContent {
                                drawContent()
                                val lr = nameLayout
                                if (lr != null) {
                                    val stroke = 2.5.dp.toPx()
                                    for (i in 0 until lr.lineCount) {
                                        val y = (lr.getLineTop(i) + lr.getLineBottom(i)) / 2f
                                        drawLine(
                                            color = strikeColor,
                                            start = Offset(lr.getLineLeft(i), y),
                                            end = Offset(lr.getLineRight(i), y),
                                            strokeWidth = stroke
                                        )
                                    }
                                }
                            } else Modifier
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                d.teacher.ifBlank { "未填写" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                d.room.ifBlank { "未填写" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { androidx.compose.foundation.layout.Spacer(Modifier.size(96.dp)) }
    }
}

/** 解析 "HH:mm" 格式的节次时间；格式不对时返回 null（不影响其它课程） */
private fun parseTimeOrNull(text: String): LocalTime? =
    runCatching { LocalTime.parse(text.trim()) }.getOrNull()
