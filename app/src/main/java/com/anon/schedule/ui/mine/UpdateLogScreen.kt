package com.anon.schedule.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anon.schedule.ui.common.GlassCard

/** 一条更新记录：日期 + 当次改动 */
private data class UpdateEntry(val date: String, val items: List<String>)

/** 更新内容（新的在最上面；以后每次改动往这里追加一条） */
private val updateEntries = listOf(
    UpdateEntry(
        date = "2026-10-21",
        items = listOf(
            "我的：关于区域新增「检测更新」，可直接从 GitHub 检查新版本并跳转下载页",
            "学习通：作业 / 考试的进入 → 作答 → 提交 → 状态回写全链路打通",
            "学习通：修复作答页题目区高度塌成 0 被裁成空白、翻页与交卷按钮被顶出屏幕",
            "学习通：接管原生桥 CLIENT_OPEN_URL，开始答题 / 开始考试可正常跳转",
            "学习通：作答页接入系统文件选择器，为附件上传打通入口",
            "学习通：拍照按原生桥协议对接（CLIENT_CHOOSE_IMAGE → CLIENT_CHOOSE_IMAGE_RESULT），选图后自动上传并插入编辑器",
            "学习通：插入图片后限宽、编辑器高度封顶，解决图片撑爆整页、底部按钮被挤出屏幕",
            "学习通：图片校正改为事件驱动（MutationObserver），去掉定时轮询，作答页不再卡顿抖动",
            "学习通：提交成功后弹出提示并自动退回任务列表、刷新状态",
            "学习通：只保留自身诊断日志，屏蔽页面 console 刷屏造成的开销",
            "课表：液态玻璃效果上线（背景独立采样 + 毛玻璃 / 边缘折射 / 高光），低版本系统自动降级",
            "课表：右上角「更多」面板改为分组卡片风格，周数滑块、课表缩略卡与功能入口统一排版",
            "设置：改为分组圆角卡片 + 彩色图标块，开关换成绿色胶囊样式",
            "设置：主题切换改为悬浮圆角窗口，选项为胶囊按钮并高亮当前选择",
            "设置 / 课表设置：顶部改为透明大标题，去掉顶栏色块与分隔线",
            "设置等二级页面不再露出背景图，顶部状态栏区域保持纯色",
            "我的：新增本页，按日期记录每次改动",
            "设置：深浅色切换加入渐变动画，页面、卡片与主题弹窗一起平滑过渡",
            "成绩：改为原生接口读取（复用网页登录状态）并自动翻页；空学期不显示，整个学年没成绩就不再往前读",
            "学习通：作业与考试列表未完成的排在前面，剩余时间短的在上，已完成沉底"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateLogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新内容") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(updateEntries) { entry ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        entry.date,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    entry.items.forEach { line ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
