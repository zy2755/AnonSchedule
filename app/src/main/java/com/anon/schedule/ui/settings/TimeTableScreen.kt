package com.anon.schedule.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.TimeSlot

/** 上课时间：照 WakeUp「编辑时间表」排版，每节一行：第 N 节 + [开始] - [结束] */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTableScreen(onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val context = LocalContext.current
    var slots by remember { mutableStateOf(st.slots) }

    // 导出 CSV（UTF-8 带 BOM，Excel 直接打开不乱码）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val sb = StringBuilder("\uFEFF节次,开始,结束\r\n")
                slots.forEachIndexed { i, s -> sb.append("${i + 1},${s.start},${s.end}\r\n") }
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(sb.toString().toByteArray(Charsets.UTF_8))
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "已导出 ${slots.size} 节课时间" else "导出失败了……再试一次？",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 导入：从文本里提取每行的起止时间（UTF-8，乱码时回退 GBK）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val parsed = runCatching { readSlotsFromUri(context, uri) }.getOrNull()
            if (parsed.isNullOrEmpty()) {
                Toast.makeText(context, "没找到时间，检查下文件格式？", Toast.LENGTH_SHORT).show()
            } else {
                slots = parsed
                Toast.makeText(context, "已导入 ${parsed.size} 节，记得点右上角保存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上课时间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导入上课时间")
                    }
                    IconButton(onClick = { exportLauncher.launch("上课时间.csv") }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导出上课时间")
                    }
                    TextButton(onClick = {
                        Repo.update { s -> s.copy(slots = slots) }
                        onBack()
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "请注意是 24 小时制!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                LazyColumn {
                    itemsIndexed(slots) { i, slot ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "第 ${i + 1} 节",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(64.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            TimePill(
                                value = slot.start,
                                onValueChange = { v ->
                                    slots = slots.mapIndexed { j, s -> if (j == i) s.copy(start = v) else s }
                                }
                            )
                            Text(
                                "-",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(28.dp)
                            )
                            TimePill(
                                value = slot.end,
                                onValueChange = { v ->
                                    slots = slots.mapIndexed { j, s -> if (j == i) s.copy(end = v) else s }
                                }
                            )
                            IconButton(
                                onClick = {
                                    if (slots.size > 1) slots = slots.filterIndexed { j, _ -> j != i }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(onClick = { slots = slots + TimeSlot("08:00", "08:45") }) {
                                Icon(Icons.Default.Add, contentDescription = "添加节次")
                            }
                        }
                        Spacer(Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

/** 圆角胶囊时间输入框，照 WakeUp 的样式 */
@Composable
private fun TimePill(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= 5 && v.all { it.isDigit() || it == ':' || it == '：' }) {
                onValueChange(v.replace('：', ':'))
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .size(width = 78.dp, height = 38.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (value.isEmpty()) {
                    Text(
                        "--:--",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                inner()
            }
        }
    )
}

/** 从 uri 读文本并解析出节次时间（每行一对起止时间） */
private fun readSlotsFromUri(context: Context, uri: Uri): List<TimeSlot> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
    val trimmed = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        bytes.copyOfRange(3, bytes.size)
    } else {
        bytes
    }
    var text = String(trimmed, Charsets.UTF_8)
    if (text.contains('�')) text = String(trimmed, charset("GBK"))
    return parseTimeSlots(text)
}

private fun parseTimeSlots(text: String): List<TimeSlot> {
    val regex = Regex("(\\d{1,2})[：:](\\d{2})")
    fun times(line: String) = regex.findAll(line).map { m ->
        val h = m.groupValues[1].toInt().coerceIn(0, 23)
        val min = m.groupValues[2].toInt().coerceIn(0, 59)
        "%02d:%02d".format(h, min)
    }.toList()

    // 常规：每行 "开始,结束" 一对
    val perLine = text.lineSequence().mapNotNull { line ->
        val t = times(line)
        if (t.size >= 2) TimeSlot(t[0], t[1]) else null
    }.toList()
    if (perLine.isNotEmpty()) return perLine

    // 兜底：所有时间排成一列时，两两成对
    val all = times(text)
    return all.chunked(2).filter { it.size == 2 }.map { TimeSlot(it[0], it[1]) }
}
