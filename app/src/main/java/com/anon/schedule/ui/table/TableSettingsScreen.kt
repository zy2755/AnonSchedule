package com.anon.schedule.ui.table

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.SectionTitle

/** 课表设置：三入口（默认配置弹窗 / 课表数据 / 课表外观），照 WakeUp 图三 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableSettingsScreen(
    tableId: Long,
    onBack: () -> Unit,
    onTableData: (Long) -> Unit,
    onTableStyle: (Long) -> Unit
) {
    val context = LocalContext.current
    var showDefaultConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表设置") },
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
        ) {
            SectionTitle("课表")
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                OptionBlock(
                    title = "将此课表配置用作默认配置",
                    desc = "应用此项后，新建的课表会使用此课表中除了上课时间、课表名称、开学日期以外的配置，" +
                            "包括但不限于课表外观样式、一天节数等。但是后续对此课表配置的修改并不会同步到新的课表中",
                    onClick = { showDefaultConfirm = true }
                )
                OptionDivider()
                OptionBlock(
                    title = "课表数据",
                    desc = "设置学期周数、当前周、上课时间、一天节数、一周开始日等，课表名称也是在这里改哦",
                    onClick = { onTableData(tableId) }
                )
                OptionDivider()
                OptionBlock(
                    title = "课表外观",
                    desc = "背景、文字颜色和大小、小格子高度和不透明度、是否显示周末……这些都可以在这里自定义哦，" +
                            "未来会陆续开放更多的自定义设置",
                    onClick = { onTableStyle(tableId) }
                )
            }
        }
    }

    if (showDefaultConfirm) {
        AlertDialog(
            onDismissRequest = { showDefaultConfirm = false },
            title = { Text("提示") },
            text = {
                Text("确定要将此课表配置用作默认配置吗？确定之前请仔细阅读对此功能的说明")
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.update { st ->
                        st.copy(
                            settings = st.tableSettings(tableId)
                                .copy(startDate = null, currentWeek = 1)
                        )
                    }
                    showDefaultConfirm = false
                    Toast.makeText(context, "已用作默认配置～", Toast.LENGTH_SHORT).show()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun OptionBlock(title: String, desc: String, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptionDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}
