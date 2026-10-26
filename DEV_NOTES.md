# 课程表 App 开发笔记

> 工程：`com.anon.schedule`（Kotlin 2.0.21 + Compose，minSdk 26 / compileSdk 35，AGP 见 gradle 配置）
> 本文件记录历次改动的结论与坑，方便新开对话时快速接上上下文。

## 构建与安装

```powershell
.\gradlew.bat assembleDebug        # 产物 app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease      # 产物 app\build\outputs\apk\release\
adb -s <设备号> install -r <apk 绝对路径>
```

Release 包默认开 R8（`isMinifyEnabled + isShrinkResources`），安装前需用 debug keystore 签名：

```powershell
apksigner sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android `
  --ks-key-alias androiddebugkey --key-pass pass:android --out app-release-signed.apk app-release-unsigned.apk
```

## 2026-10-19 改动记录

### 1. 夜间模式：有背景图时「设置 / 今日课表」卡片框与字体看不清

根因（两处叠加）：

- `MainActivity.kt` 里 `Scaffold(containerColor = Color.White.copy(alpha = 0.35f))` 写死加白，**夜间也在洗亮背景图**，白色文字直接糊掉。
- `ui/common/Ui.kt` 的 `GlassCard` 在玻璃开启时只画了一层白色 10% 光晕，算出来的 `bg` 底色**根本没被使用** → 卡片近乎全透明。

修复：

- 遮罩按明暗自适应：夜间 `background.copy(alpha = 0.58f)` 压暗，浅色 `White.copy(alpha = 0.40f)`。
- `GlassCard` 先铺托底底色（有背景图时 `surface` 不透明度 0.86，无背景图 0.60），再叠高光 + 1dp 描边。
- 描边按明暗自适应：深色卡片用白色高光，浅色卡片用 `outline` 灰线（否则浅底上的白框等于没有）。
- 新增 `LocalHasBackground`（`ui/theme/Theme.kt`），在 `MainActivity` 的 `setContent` 里由背景图是否存在决定下传。

### 2. 冷启动优化

- `data/Repository.kt`：`init()` 的读盘 + JSON 解析移到后台线程，先用默认 `AppState()` 撑首帧；新增 `loaded` 标志，未读完前拒绝落盘（避免刚启动就退后台用默认值覆盖 data.json）。
- `MainActivity.kt` 的 `TabSlot`：当前 tab 立即组合，另外两个 tab 延迟 `TAB_MOUNT_DELAY_MS = 120ms` 再挂载（保留组合，避免切 tab 卡顿）。
- 冷启动统计帧数 9 → 13（3 秒内），更顺。

### 3. 液态玻璃按参考 demo 重做

目标效果链（来自逆向报告）：`saturate(1.5) → blur(4dp) → 边缘 AGSL 折射`。

- `ui/theme/LiquidGlass.kt`：`offset` 从写死 `0f, 0f` 改为 `-refractionHeightPx`（与参考实现 `offset = -refractionPx` 一致）。
- `MainActivity.kt`：
  - 快照模糊 18dp → **4dp**，并前置 `ColorMatrix` 提饱和 1.5（`createChainEffect(blur, sat)`）。
  - `GlassParams`：`refractionHeight = 24dp`（按药丸高度封顶 18%）、`refractionAmount = -24dp`、`depthEffect = 0`。
  - 垫色改 ~40% `surfaceContainer`（明暗自适应），描边收到 1dp 白色。
- 效果：药丸从「很糊」变成清透 + 边缘一圈液感折射。

### 4. Release 包与 debug 对比

| 构建 | 冷启动 TotalTime |
| --- | --- |
| debug | ~780 ms |
| release（R8） | ~135 ms |

R8 开启后无崩溃，界面渲染与 debug 一致。

## 排查手法备忘

- 抓冷启动：`adb shell am force-stop <包名>` → `am start -W -n <包名>/.MainActivity`；帧数用 `dumpsys gfxinfo <包名>` 的 `Total frames rendered`。
- 真机调试改私有数据（debug 包）：`adb shell run-as com.anon.schedule sed -i '<行号>s/旧/新/' files/data.json`。
- **release 包不可 `run-as`**（not debuggable），要改数据先临时装 debug 包。
- 切换夜间：`adb shell cmd uimode night yes|no|auto`。
- 背景图遮罩问题排查思路：先看 `Scaffold containerColor` 是否写死颜色，再看卡片底色是否真的被绘制。

## 待办 / 待确认

- `temp_20261018_03` 下的四份备份（nightmode / startup / glass / release）尚未删除，待确认稳定后清理。
- 如需进一步压缩冷启动，可补 Baseline Profile（本次未做）。

## 2026-10-24 桌面小组件（小米小部件验证与结论）

结论：**个人开发者上不了 MIUI 小部件中心**——小米开发者账号只收企业 / 政府事业单位 / 港澳台及其他组织，且小部件要求 App 先在应用商店过审（应用商店同样不接受个人开发者）。当前维持 Android 原生小部件形态，`miuiWidget` 系列标识已全部撤回。

### 小米小部件规范的几个硬点（将来有企业主体时按此还原）

- 每个 widget receiver 加 `miuiWidget=true`，application 下加 `miuiWidgetVersion`（整数，改动必须递增）。
- receiver（含 RemoteViewsService）必须 `android:process=":widgetProvider"`，进程内存 < 35MB（实测 21.7MB）。
- 布局根节点必须是 `@android:id/background`，背景不能全透明，圆角由系统加（Kotlin 点击引用要改成 `android.R.id.background`）。
- MIUI 会去掉系统定时刷新；要刷新就声明 `miuiWidgetRefresh=exposure` + `miuiWidgetRefreshMinInterval`（毫秒，最短 10 秒），刷新广播是 `miui.appwidget.action.APPWIDGET_UPDATE`，`AppWidgetProvider` 默认不认这个 action，必须自己 `onReceive` 转发。
- 只支持 2×2 / 4×2 / 4×4 三种尺寸，`targetCellWidth/Height` 与 `minWidth/minHeight` 要按此对齐。

### 踩过的坑

1. **`miuiWidget=true` 会让卡片在审核通过前从小米桌面「安卓小部件」里消失**（HyperOS 4 实测，重启桌面后依旧）。也就是说提审期间谁都加不了新卡片，已放置的实例不受影响。日常自用/开发调试时要撤掉这个标识。
2. **画廊预览用的是 `previewImage`（PNG），不是 `previewLayout`**。只写 `previewLayout` 时小米画廊里显示的是应用图标。预览图用实机截图裁出来即可（见 `res/drawable-nodpi/widget_preview_*.png`）。
3. `AppWidgetManager.requestPinAppWidget()` 在 HyperOS 4 上会被桌面拒绝（`AddItemUi, setup_widget abort: permission denied`），即使授予 `INSTALL_SHORTCUT` / `INSTALL_WIDGET` 也一样；官方说明详情页只展示通过审核的小部件，所以本地未上架时失败属预期。
4. **桌面编辑态下 `uiautomator dump` 基本拿不到层级**（只返回约 1.8KB 空壳），`input draganddrop` 在「安卓小部件」画廊里只会滚动列表。想批量摆放卡片只能人工拖。
5. 排查命令：`adb shell dumpsys appwidget`（看 provider 注册与 min 尺寸，单位是 dp×256）、`adb shell dumpsys meminfo com.<pkg>:widgetProvider`（看进程 PSS）。

