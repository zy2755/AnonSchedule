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

/** 某个日期当天的改动 */
private data class UpdateDay(val date: String, val items: List<String>)

/** 一个版本的更新记录：版本号 + 该版本内按日期分开的改动 */
private data class UpdateEntry(val version: String, val days: List<UpdateDay>)

/** 更新内容（新的在最上面；改动按版本归类，同一版本内再按日期分段） */
private val updateEntries = listOf(
    UpdateEntry(
        version = "1.7.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-28",
                items = listOf(
                    "课程编辑页新增「课程备注」：群号、课件链接、考试范围都能写，课程详情里直接看，链接点一下就打开",
                    "新增「日历文件 (.ics) 导入」：把系统日历或其它课表 App 导出的 .ics 解析回课表，导入前先预览确认（需先填开学日期）",
                    "今日页新增「考试倒计时」卡片：显示学习通同步来的最近一场考试与剩余天数，3 天内标红，点卡片直达待办",
                    "新增「考试倒计时 · 2×2」桌面小组件：最近一场考试 + 剩余天数，点击打开待办清单",
                    "版本：升级到 1.7.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.6.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-27",
                items = listOf(
                    "「待办 DDL」小组件每行左侧新增方形勾选框，点一下直接完成，勾完自动刷新并撤掉到期提醒",
                    "课程编辑页新增「上课提醒」开关，可以单独关掉某门课的课前提醒，保存后立即重排",
                    "版本：升级到 1.6.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.5.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-26",
                items = listOf(
                    "新增「待办 DDL · 4×2」小组件：最近到期的两条待办直接摆在桌面，逾期标红、今天到期标粉，点击直达待办清单",
                    "新增「本学期进度 · 2×2」小组件：显示第几周 / 共几周、已过百分比、剩余周数与结课日期，进度条跟随主题点缀色",
                    "小组件会在待办截止时刻自动刷新，过点立刻变成已逾期",
                    "待办 DDL / 本学期进度 / 本周课表 4×4 补上了小部件选择面板的缩略图（小米桌面只认预览图）",
                    "版本：升级到 1.5.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.4.4",
        days = listOf(
            UpdateDay(
                date = "2026-11-25",
                items = listOf(
                    "待办清单：课程名太长时自动省略，截止时间不再被挤成一字一行的竖排",
                    "版本：升级到 1.4.4"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.4.3",
        days = listOf(
            UpdateDay(
                date = "2026-11-25",
                items = listOf(
                    "AI实践自动核对作答状态：已经做完的不再进待办，旧的已完成条目会自动勾掉",
                    "AI实践列表里已完成的沉到底部并标注「已完成」，进行中的排在前面",
                    "翻到别的周时，星期表头下的日期会带上月份（如 11/23），不再分不清是哪个月",
                    "版本：升级到 1.4.3"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.4.2",
        days = listOf(
            UpdateDay(
                date = "2026-11-25",
                items = listOf(
                    "学习通任务页新增「AI实践」一栏：作业列表里看不到的 AI实践（如思维导图、实践活动）也能在这里查看，点开直达作答页",
                    "AI实践的截止时间按接口给的时间戳换算，自动同步进待办清单，结束后自动勾掉",
                    "版本：升级到 1.4.2"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.4.1",
        days = listOf(
            UpdateDay(
                date = "2026-11-25",
                items = listOf(
                    "学习通联动：刷新作业 / 考试时，未完成的任务会自动加入待办清单，并带上剩余时间对应的截止时刻",
                    "学习通里已完成的任务，对应待办会自动勾掉；重复刷新不会重复添加",
                    "本周课表小部件只显示课程名，按格子宽度自动折行、缩小字号，长课程名也能看全",
                    "版本：升级到 1.4.1"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.4.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-25",
                items = listOf(
                    "待办升级为独立 DDL 清单：可设截止日期与时刻、到期提醒，按「已逾期 / 今天 / 即将到期 / 没有日期 / 已完成」分组；今日页和月历都会显示",
                    "多学期归档：多课表管理里「结束本学期并新建」，旧学期连同课程一起归档，新学期按现在的样式新建；归档学期可随时恢复或删除",
                    "新增「本周课表」桌面小部件（4×4，可自由拉伸），按当前配色绘制整周课程，点击直达课表",
                    "课表顶栏新增搜索：按课程名 / 老师 / 教室查找，点结果直接跳到对应周并打开课程详情",
                    "翻到别的周时顶栏出现「回本周」，一键回到今天所在的周",
                    "设置 → 提醒新增「下节课常驻通知」：通知栏常挂下一节课，上下课自动更新",
                    "桌面图标换成四款新的渐变插画（默认粉蓝渐变），可在 设置 → 软件图标 里切换",
                    "版本：升级到 1.4.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.3.1",
        days = listOf(
            UpdateDay(
                date = "2026-11-23",
                items = listOf(
                    "课表页右上角「更多」改成悬浮面板：从屏幕底部升起，周数、课表与功能一屏可见，点外面或返回键收起",
                    "功能改成两行图标：上课时间 / 已添课程 / 课表设置 / 夜间模式 / 小部件 / 月历 / 分享 / 全局设置",
                    "修复：原来底部面板每次打开时上边界位置不一致（贴着状态栏或钻到状态栏下面）的问题，随面板形式一起消失",
                    "版本：升级到 1.3.1"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.3.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-23",
                items = listOf(
                    "今日页新增「本周概览」：本周节数、最忙的日子（几天一样忙时会标出并列天数）、本学期累计节数与学期进度，一屏看完",
                    "课表长图支持四种样式：竖向经典网格、横向（一个星期一行）、深色底、极简（去掉网格线、只留淡色块和左侧色条），在长图对话框里直接切换",
                    "新增「月历视图」：课表页「更多」里进入，整月看每天几门课，点某天看当天的课程和课程待办",
                    "版本：升级到 1.3.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.2.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-20",
                items = listOf(
                    "分享课表新增「课表长图」：把当前周排成一张图片，可选带不带左侧节次时间列，直接发群或存进相册",
                    "分享课表新增「日历文件（.ics）」：整学期按周展开导进系统日历，单双周、停课、调课都会如实转换，手机、手表、电脑都能看到",
                    "日历里每条课程默认带上课前提醒，导入后系统日历会按你的提醒时间提示",
                    "添加课程、改课或拖动调课时，如果目标时间已经有别的课，会先提示「时间冲突」，可以选保留或取消",
                    "成绩页汇总卡支持切换口径：「学分加权」或「算术平均」，平均分和平均绩点会跟着重算",
                    "版本：升级到 1.2.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.1.1",
        days = listOf(
            UpdateDay(
                date = "2026-11-20",
                items = listOf(
                    "学业情况查询：进来直接显示上次的完整页面（含课程树），不用先看一屏登录页，断网也能看全",
                    "学业情况查询：和成绩查询一样以本地数据为准，不会每次进来都去教务拉一遍，要看最新的点右上角刷新",
                    "学业情况查询：刷新时会话过期就用本机保存的学号密码在后台自动登录，登录页、过期弹窗、跳转页都不会露出来",
                    "学业情况查询：点课程卡片可以展开/收起下面的课程明细表",
                    "学业情况查询：右上角新增账号入口，可保存/清除自动登录的账号密码（加密存本机，不上传）",
                    "版本：升级到 1.1.1"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.1.0",
        days = listOf(
            UpdateDay(
                date = "2026-11-19",
                items = listOf(
                    "今日页新增主角卡：一眼看到正在上哪节、还剩多少分钟，还没开课就显示下一节和倒计时",
                    "今日页的课程列表改为时间轴：已上过的自动划掉，正在上的标「进行中」，下一节标出还有几分钟",
                    "修复：重启后仍停留在上次退出时翻到的那一周，现在每次启动都回到真实的当前周",
                    "修复：长按拖动调课时，原位置还留着一张被按住课程的残影，现在只会跟着手指走一张",
                    "修复：长按拖动后又放回原位，原格子里的课程会变成透明（只剩文字），现在会完整恢复原样",
                    "版本：升级到 1.1.0"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.19",
        days = listOf(
            UpdateDay(
                date = "2026-11-19",
                items = listOf(
                    "重构：周次换算、单双周、调课/加课/停课叠加、非本周课程显示规则抽成独立的纯逻辑，界面行为保持不变",
                    "测试：为这套规则补了 11 组单元测试，以后改课表规则前先跑一遍，避免旧问题反复出现",
                    "版本：升级到 1.0.19"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.18",
        days = listOf(
            UpdateDay(
                date = "2026-11-19",
                items = listOf(
                    "安全：澡堂账号的手机号、密码、登录凭证改为加密保存，不再以明文躺在手机里",
                    "加密密钥由系统安全模块托管，App 自己也无法导出，备份或文件被读取都拿不到原始内容",
                    "升级后第一次打开澡堂页面会自动把旧的明文账号迁进加密存储并删除，登录状态不受影响",
                    "版本：升级到 1.0.18"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.17",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "主页：深色主题下在顶部状态栏区域加一层很淡的渐变，白色状态栏图标压在偏亮壁纸上也能看清",
                    "渐变只压状态栏这一条，往下很快淡出，不影响壁纸观感；浅色主题不加",
                    "版本：升级到 1.0.17"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.16",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "修复：深色主题下二级页面状态栏图标几乎看不见（只剩电量）",
                    "状态栏与导航栏图标改成跟随 App 主题，不再按系统深浅色判断",
                    "版本：升级到 1.0.16"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.15",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "学业情况查询：课程明细的表头（修读状态 / 成绩 / 学年 / 学期 / 课程号）滚动时吸在顶部",
                    "表头跟着整张表一起走，表格滚完自动松开，不会盖住下面的卡片",
                    "版本：升级到 1.0.15"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.14",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "学业情况查询：跟随 App 主题切换深浅色，深色下是暗色卡片，不再和成绩查询一个亮一个暗",
                    "学业情况查询：切换主题后页面即时换色，不用重新进入",
                    "学业情况查询：页面底色与 App 对齐，加载时不再先闪一下白",
                    "版本：升级到 1.0.14"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.13",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "教务：成绩查询与学业情况查询共用同一套 WebView 配置与登录 Cookie，在一边登录过，另一边进来直接是登录态",
                    "学业情况查询：整页重新排版，改成卡片树，每层缩进收窄，手机上不再越往下越窄",
                    "学业情况查询：卡片统一成浅色 + 浅灰底，展开的明细表也一并包进卡片，不再白底撞色",
                    "学业情况查询：学分压成「要求 / 获得 / 未获得 6.0」，不会再断成「未获 / 得」",
                    "学业情况查询：展开的课程明细改为小字号并支持横向滑动，不再挤成一团",
                    "学业情况查询：折叠按钮改成统一的圆形加减，深色下也看得清",
                    "版本：升级到 1.0.13"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.12",
        days = listOf(
            UpdateDay(
                date = "2026-11-17",
                items = listOf(
                    "教务：新增「学业情况查询」，直接打开学校教务系统的学业情况页面",
                    "教务：登录状态由 Cookie 保持，登录过一次之后再进来不用重登",
                    "教务：页面内可以逐级返回，右上角可刷新",
                    "版本：升级到 1.0.12"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.11",
        days = listOf(
            UpdateDay(
                date = "2026-11-14",
                items = listOf(
                    "小组件：新增自刷闹钟，跨零点自动换成当天日期，不用再点一下才更新",
                    "小组件：上下课时刻也会自动重画，「正在上 / 下一节」不再停在早上的状态",
                    "小组件：手机重启、覆盖安装或改时间后会立刻刷新并重新排程",
                    "版本：升级到 1.0.11"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.10",
        days = listOf(
            UpdateDay(
                date = "2026-11-11",
                items = listOf(
                    "小部件：收紧行高与块间距，课程名和地点不再被拉得很散",
                    "小部件：关掉系统给文字预留的上下额外留白，四款尺寸都更紧凑",
                    "小部件：4×2 尺寸下第二节课不再被裁掉半行",
                    "小部件：周次显示由「第 10 周」改为「第10周」，日期行不再松散",
                    "版本：升级到 1.0.10"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.9",
        days = listOf(
            UpdateDay(
                date = "2026-11-11",
                items = listOf(
                    "课程规划：点开任意课程即可添加待办，勾掉表示完成，计数实时显示",
                    "课程规划：待办会跟着课程保存，删掉待办或课程都不会影响其它课的计划",
                    "设置：新增「课程待办」开关，关掉后课程详情不再显示待办区域",
                    "分享课表：点击后可选择「纯文字」「本周 CSV」或「整学期 CSV」，后者带起止周与单双周",
                    "版本：升级到 1.0.9"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.8",
        days = listOf(
            UpdateDay(
                date = "2026-11-10",
                items = listOf(
                    "液态玻璃：折射收拢到底部导航栏，边缘一圈用着色器真实弯折背景（Android 13 及以上，更低版本自动退回普通模糊）",
                    "底部导航栏：每个格子按下会果冻按压，并透出一圈柔光，拖选时光晕跟着手指走",
                    "卡片：改回简单的磨砂 / 高斯模糊，不再叠加液态玻璃的光学层",
                    "修复：不开启液态玻璃时，「我的」页的卡片不透明度滑块也能实时生效",
                    "版本：升级到 1.0.8"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.7",
        days = listOf(
            UpdateDay(
                date = "2026-11-10",
                items = listOf(
                    "设置：外观新增「主题配色」，内置石墨、樱花粉、海盐蓝、薄荷绿、暖阳橙、紫丁香六款配色",
                    "配色：每套配色都单独调好浅色与深色版本，跟随系统 / 手动切换明暗时自动适配，深色模式底色仍保持纯黑",
                    "配色：底部导航栏选中色、滑块填充与圆点、设置弹窗的选中项都会跟着配色一起变",
                    "设置：「主题」弹窗的选中高亮不再固定浅蓝，改为跟随当前配色",
                    "版本：升级到 1.0.7"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.6",
        days = listOf(
            UpdateDay(
                date = "2026-11-08",
                items = listOf(
                    "设置：外观新增「软件图标」，可在原版插画、天空蓝、薄荷绿、暖橙四款内置图标之间切换",
                    "图标：选中项保存在本机，重启手机或覆盖安装后依然保持",
                    "图标：切换实际是启用 / 禁用清单里的启动入口，桌面会短暂刷新；MIUI / 澎湃OS 等定制桌面可能要回桌面等几秒才更新（页面内已写明原因）"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.5",
        days = listOf(
            UpdateDay(
                date = "2026-11-08",
                items = listOf(
                    "修复：浅色模式下悬浮底栏仍是墨色玻璃药丸，现在底栏跟随主题，浅色模式回到浅色玻璃样式",
                    "修复：课表「更多」面板里向上滑时内容会来回抖动，现在面板不再抢竖直手势，滚动完全交给内容"
                )
            ),
            UpdateDay(
                date = "2026-11-06",
                items = listOf(
                    "我的：更新内容改为按版本归类，同一版本里的改动再按具体日期分段列出",
                    "修复：在「我的」页上下滚动时手指划过滑块会误改卡片不透明度，现在只有横向拖动才生效",
                    "玻璃：卡片与底栏改用真实高斯模糊（Haze），去掉降采样马赛克、边缘折射、跟手光斑与换页脉冲，整体更安静",
                    "底栏：换成墨色玻璃药丸 + 细亮边 + 圆形选中指示，形状改标准胶囊，修掉玻璃角部两道斜纹",
                    "滑块：改为粗胶囊轨道 + 内嵌实心圆点，按下会弹簧变粗；配色换成浅蓝轨 / 深蓝点",
                    "修复：开启「液态玻璃」时「卡片背景不透明度」滑杆不起作用，现在开 / 关两种模式都实时生效",
                    "修复：拖动不透明度滑杆时整页重绘造成的卡顿（实测掉帧从 22% 降到 0.2% 以下）",
                    "设置：「液态玻璃」说明改成与当前效果一致，去掉已移除的「折射」描述",
                    "版本：升级到 1.0.5，把这一轮液态玻璃重构、滑块与性能优化、构建链升级打包发布"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.4",
        days = listOf(
            UpdateDay(
                date = "2026-11-06",
                items = listOf(
                    "版本：升级到 1.0.4，把这一轮「我的」页改版、小部件深色极简与配色调整打包发布",
                    "我的：学习通入口的「实验性功能」提示里注明，现阶段只推荐作为查看作业 / 考试归属使用"
                )
            ),
            UpdateDay(
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
            )
        )
    ),
    UpdateEntry(
        version = "1.0.3",
        days = listOf(
            UpdateDay(
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
            )
        )
    ),
    UpdateEntry(
        version = "1.0.2",
        days = listOf(
            UpdateDay(
                date = "2026-10-24",
                items = listOf(
                    "小组件 / 今日课表：调课覆盖的当天原课不再重复显示，与课表网格保持一致",
                    "小部件：补充小米桌面所需权限，一键添加被系统拦截时给出桌面手动添加路径",
                    "小部件：按小米小部件规范试改后，因暂无企业开发者主体，维持安卓原生小部件形态（miuiWidget 标识已撤回）",
                    "小部件：尺寸统一为 2×2 / 4×2 / 4×4 并补上预览图；今日课程 2×4 调整为 4×4，今日·明日改为 4×4 双列展示",
                    "提醒：新增「上课前提醒」，每节课前可提前 5 / 10 / 15 / 30 分钟（默认 10）发通知，设置页可开关；重启或改时间后自动重排"
                )
            ),
            UpdateDay(
                date = "2026-10-23",
                items = listOf(
                    "设置：新增「备份与恢复」，可把课表、课程、设置与成绩导出成 JSON 文件，也能从文件整体恢复",
                    "设置：导入失败时的提示改成中文说明，不再直接抛英文报错",
                    "课表：更多菜单新增「分享课表」，把本周课程整理成文字发给同学"
                )
            ),
            UpdateDay(
                date = "2026-10-22",
                items = listOf(
                    "课表：桌面长按应用图标新增「添加小部件」快捷方式，不用再翻「安卓小部件」文件夹"
                )
            )
        )
    ),
    UpdateEntry(
        version = "1.0.1",
        days = listOf(
            UpdateDay(
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
                    "我的：新增本页，记录每次改动",
                    "设置：深浅色切换加入渐变动画，页面、卡片与主题弹窗一起平滑过渡",
                    "成绩：改为原生接口读取（复用网页登录状态）并自动翻页；空学期不显示，整个学年没成绩就不再往前读",
                    "学习通：作业与考试列表未完成的排在前面，剩余时间短的在上，已完成沉底"
                )
            )
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
                        "v${entry.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    entry.days.forEachIndexed { index, day ->
                        Spacer(Modifier.height(if (index == 0) 4.dp else 16.dp))
                        Text(
                            day.date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(6.dp))
                        day.items.forEach { line ->
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
}
