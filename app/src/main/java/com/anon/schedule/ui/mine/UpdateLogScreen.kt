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
        date = "2026-11-06",
        items = listOf(
            "版本：升级到 1.0.5，把这一轮液态玻璃重构、滑块与性能优化、构建链升级打包发布",
            "修复：在「我的」页上下滚动时手指划过滑块会误改卡片不透明度，现在只有横向拖动才生效",
            "玻璃：卡片与底栏改用真实高斯模糊（Haze），去掉降采样马赛克、边缘折射、跟手光斑与换页脉冲，整体更安静",
            "底栏：换成墨色玻璃药丸 + 细亮边 + 圆形选中指示，形状改标准胶囊，修掉玻璃角部两道斜纹",
            "滑块：改为粗胶囊轨道 + 内嵌实心圆点，按下会弹簧变粗；配色换成浅蓝轨 / 深蓝点",
            "修复：开启「液态玻璃」时「卡片背景不透明度」滑杆不起作用，现在开 / 关两种模式都实时生效",
            "修复：拖动不透明度滑杆时整页重绘造成的卡顿（实测掉帧从 22% 降到 0.2% 以下）",
            "设置：「液态玻璃」说明改成与当前效果一致，去掉已移除的「折射」描述",
            "版本：升级到 1.0.4，把这一轮「我的」页改版、小部件深色极简与配色调整打包发布",
            "我的：学习通入口的「实验性功能」提示里注明，现阶段只推荐作为查看作业 / 考试归属使用"
        )
    ),
    UpdateEntry(
        date = "2026-11-05",
        items = listOf(
            "我的：新增「卡片背景不透明度」滑杆，40%~100% 实时生效，深色/浅色模式都跟随",
            "小部件：改为深色极简风，去掉整块课程色背景，左侧只留一条细彩色竖线作为分类标识",
            "小部件：课程名改白色、地点/教师改灰色，行距加大，右侧显示起止时间",
            "小部件：头部统一为「课表名 + 日期 + 周次」，4×2、4×4、2×2 与今日·明日同步更新",
            "小部件：补上浅色模式版本，跟随系统浅色/深色自动切换白底或黑底",
            "修复：「我的」页内容变高后整页不能滚动，「关于」被挤到屏幕外够不着（现在可以正常上滑）",
            "我的：点「学习通」入口先弹出「实验性功能」提示，确认后才进入",
            "配色：全站去掉高饱和粉，强调色改为浅色近黑 / 深色近白，图标、分区标题、滑块、导航高亮统一转中性",
            "配色：浅色模式底色、卡片、描边等偏粉的中间色一并转成中性灰，启动闪屏同步",
            "我的：列表型卡片改为「行内边距」，去掉卡片内边距与行内边距叠加造成的上下空白，文字离卡片边缘不再悬空",
            "我的：关于卡片的分隔线改为通到卡片两侧，行高与左右内边距对齐",
            "修复：设置背景图后，「我的」页顶部标题在深色模式下仍是黑色、压在图上几乎看不清（透明底导致内容色回落到黑色）",
            "玻璃：有背景图时卡片底色不透明度从 62% 提到 85%，背景图的亮斑不再透到文字底下，模糊与边缘折射保留"
        )
    ),
    UpdateEntry(
        date = "2026-10-26",
        items = listOf(
            "我的：新增「澡堂空位 / 账户余额」，入口放在「教务」与「学习通」之间",
            "澡堂：列表显示各楼栋澡堂的空闲数与总数，点开可看每个坑位状态（空闲 / 使用中 / 已预约）",
            "余额：显示总余额、本金、赠送与押金，右上角可刷新，底部显示上次更新时间",
            "登录：用「大白U帮」App 的手机号和密码登录，凭据只存在本机；token 过期会自动重登一次",
            "修复：夜间模式下关闭「液态玻璃」后，底部药丸栏仍带白色描边与光泽（高光层未受开关控制）",
            "我的：卡片标题字重加粗，说明文字改为 60% 白并加大行高，列表项上下留出更多呼吸感",
            "深色模式：页面底色改为纯黑，卡片与文字改成中性灰，粉色调只留在强调色上",
            "开关：胶囊开关重新绘制，轨道更饱满、滑块更大，开启态绿色更鲜亮",
            "滑块：轨道加粗，已填充部分改为渐变，未填充部分用明显的深色底区分",
            "底部导航：选中项改为品牌色渐变的半透明胶囊高亮，圆角加大，悬浮感更强"
        )
    ),
    UpdateEntry(
        date = "2026-10-24",
        items = listOf(
            "小组件 / 今日课表：调课覆盖的当天原课不再重复显示，与课表网格保持一致",
            "小部件：补充小米桌面所需权限，一键添加被系统拦截时给出桌面手动添加路径",
            "小部件：按小米小部件规范试改后，因暂无企业开发者主体，维持安卓原生小部件形态（miuiWidget 标识已撤回）",
            "小部件：尺寸统一为 2×2 / 4×2 / 4×4 并补上预览图；今日课程 2×4 调整为 4×4，今日·明日改为 4×4 双列展示",
            "提醒：新增「上课前提醒」，每节课前可提前 5 / 10 / 15 / 30 分钟（默认 10）发通知，设置页可开关；重启或改时间后自动重排"
        )
    ),
    UpdateEntry(
        date = "2026-10-23",
        items = listOf(
            "设置：新增「备份与恢复」，可把课表、课程、设置与成绩导出成 JSON 文件，也能从文件整体恢复",
            "设置：导入失败时的提示改成中文说明，不再直接抛英文报错",
            "课表：更多菜单新增「分享课表」，把本周课程整理成文字发给同学"
        )
    ),
    UpdateEntry(
        date = "2026-10-22",
        items = listOf(
            "课表：桌面长按应用图标新增「添加小部件」快捷方式，不用再翻「安卓小部件」文件夹"
        )
    ),
    UpdateEntry(
        date = "2026-10-21",
        items = listOf(
            "我的：关于区域新增「检测更新」，可直接从 GitHub 检查新版本并跳转下载页",
            "课表：功能区新增「添加小部件」，可直接选择尺寸把今日课程小组件加到桌面",
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
