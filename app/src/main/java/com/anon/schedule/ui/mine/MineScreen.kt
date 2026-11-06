package com.anon.schedule.ui.mine

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.data.Repo
import com.anon.schedule.data.UpdateChecker
import com.anon.schedule.data.tableSettings
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.GradientSlider
import com.anon.schedule.ui.common.SectionTitle
import com.anon.schedule.ui.common.cardSecondaryColor
import com.anon.schedule.ui.theme.CardAlphaPreview
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 列表型卡片的统一内边距：横纵向都交给行自己，避免「卡片 16 + 行 14」叠成一大块空白 */
private val listCardPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)

/** 列表行内边距：左右 16 和卡片对齐，上下 12 让文字与卡片边缘留出一点呼吸但不再悬空 */
private val listRowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun MineScreen(
    onSettings: () -> Unit,
    onGrades: () -> Unit,
    onUBang: () -> Unit,
    onChaoxing: () -> Unit,
    onUpdateLog: () -> Unit
) {
    val st by Repo.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showChaoxingTip by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            // 内容比一屏高了，必须能滚：不然「关于」会被挤到底部之下够不着
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            "当前课表：${st.tables.firstOrNull { it.id == st.currentTableId }?.name ?: "我的课表"} · 共 ${st.courses.count { it.tableId == st.currentTableId }} 门课",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = cardSecondaryColor()
        )

        SectionTitle("通用")
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSettings,
            contentPadding = listCardPadding
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "设置",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("外观")
        CardOpacityRow()

        SectionTitle("教务")
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onGrades,
            contentPadding = listCardPadding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "成绩查询",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("澡堂")
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onUBang,
            contentPadding = listCardPadding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.Bathtub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "澡堂空位 / 账户余额",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("学习通")
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showChaoxingTip = true },
            contentPadding = listCardPadding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "我的作业 / 考试",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("关于")
        // 两张独立卡片紧贴堆叠会出现「双边缘」的割裂感；合并成一张、用分隔线划行，和其他分组保持一致
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = listCardPadding) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                val result = UpdateChecker.check(currentVersion)
                                checking = false
                                updateResult = result
                            }
                        }
                    }
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (checking) "正在检查…" else "检测更新",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUpdateLog)
                    .then(listRowPadding)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "更新内容",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(96.dp))
    }

    if (showChaoxingTip) {
        AlertDialog(
            onDismissRequest = { showChaoxingTip = false },
            title = { Text("实验性功能") },
            text = {
                Text(
                    "学习通模块还在打磨中，作业与考试的进入、作答、提交可能偶尔出状况，请以学习通 App 里的状态为准。现阶段只推荐作为查看作业 / 考试归属使用。\n\n" +
                        "遇到问题的话，先回学习通 App 核对一下再提交哦～",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChaoxingTip = false
                        onChaoxing()
                    }
                ) { Text("仍然进入") }
            },
            dismissButton = {
                TextButton(onClick = { showChaoxingTip = false }) { Text("先等等") }
            }
        )
    }

    updateResult?.let { result ->
        UpdateResultDialog(
            result = result,
            currentVersion = currentVersion,
            onDismiss = { updateResult = null }
        )
    }
}

@Composable
private fun UpdateResultDialog(
    result: UpdateChecker.Result,
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    when (result) {
        is UpdateChecker.Result.Newer -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本 ${result.release.version}") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "当前版本 $currentVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        plainReleaseNotes(result.release.notes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(result.release.pageUrl))
                            )
                        }
                    }
                ) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        )

        is UpdateChecker.Result.Latest -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已是最新版本") },
            text = { Text("当前版本 $currentVersion，已经是最新版本了。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        )

        is UpdateChecker.Result.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新失败") },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        )
    }
}

/** Release 说明是 Markdown，弹窗里只做最轻量的降级：去掉标题井号、列表符号与强调标记 */
private fun plainReleaseNotes(raw: String): String {
    if (raw.isBlank()) return "新版本已发布，详情请前往 GitHub 查看。"
    return raw.lineSequence()
        .filter { line ->
            val t = line.trim()
            t.isNotEmpty() && !t.startsWith("|") && !t.startsWith("---")
        }
        .joinToString("\n") { line ->
            line.trim()
                .removePrefix("### ").removePrefix("## ").removePrefix("# ")
                .removePrefix("- ").removePrefix("* ")
                .replace("**", "")
                .replace("`", "")
        }
        .trim()
}


/**
 * 单独一个 Composable：预览态只在这里订阅。
 * 放在 MineScreen 顶层会让每次拖动都重组整个「我的」页（十几张卡片 + 列表），
 * 下沉到这一行后，拖动只重组这一行 + 各卡片里随 alpha 变化的那层底色。
 */
@Composable
private fun CardOpacityRow() {
    val previewAlpha by CardAlphaPreview
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "卡片背景不透明度",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "调低会让卡片更透，露出底下的背景",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = cardSecondaryColor()
                )
            }
            Text(
                "${(previewAlpha * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        GradientSlider(
            value = previewAlpha,
            // 只改轻量预览态：不写 Repo.state，就不会每帧全树重组
            onValueChange = { f -> CardAlphaPreview.floatValue = f },
            // 松手才提交一次，走 Repo 的 150ms 防抖落盘
            onValueChangeFinished = {
                Repo.updateSettings {
                    it.copy(
                        cardAlphaPercent = (CardAlphaPreview.floatValue * 100)
                            .roundToInt().coerceIn(40, 100)
                    )
                }
            },
            valueRange = 0.4f..1f,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
