# AnonSchedule · 课程表

一个用 Kotlin + Jetpack Compose 写的 Android 课程表 App：周课表、教务导入、学习通作业 / 考试、成绩、桌面小组件，以及一套液态玻璃风格的界面。

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## 功能

- **今日 / 周课表**：按周查看课程，支持单双周、自定义节次时间
- **多套课表**：新建、切换、重命名多份课表，适配不同学期
- **教务系统导入**：内置 WebView 登录正方教务（zftal `kbList` 接口），一键读取课表
- **学习通**：登录后查看作业、考试列表，Cookie 本地持久化
- **成绩查询**
- **桌面小组件**：4 种规格 —— 今日 2×2 / 今日 2×4 / 今日 4×2 / 今日·明日（可横向拉伸）
- **外观**：液态玻璃（AGSL 折射 + 快照模糊）、深浅色主题、自定义背景图、课表配色
- **数据本地化**：全部数据以 JSON 存在应用私有目录，不经过任何服务器

## 技术栈

| 项 | 版本 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose（BOM 2024.09.03）+ Material 3 |
| 序列化 | kotlinx.serialization |
| minSdk / targetSdk | 26 / 35 |
| JDK | 17 |

## 构建

```bash
git clone https://github.com/zy2755/AnonSchedule.git
cd AnonSchedule
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

Release 包默认开启 R8（`isMinifyEnabled` + `isShrinkResources`），需要自行配置签名：

```powershell
.\gradlew.bat assembleRelease
apksigner sign --ks <你的 keystore> --out app-release-signed.apk app-release-unsigned.apk
```

## 使用前须知

教务导入需要填写**你自己学校**的教务系统地址（默认值仅为开发者的学校环境）；不同学校页面结构不同，能否直接读取取决于是否为正方 zftal 体系。

## 免责声明

- 本项目为个人非官方作品，与任何学校、正方教务、超星学习通均无关联，也未获其授权。
- 教务与学习通的数据读取依赖第三方页面与接口，对方改版后可能失效。
- 账号、密码、Cookie 仅保存在本机应用私有目录，不会上传到任何服务器；但请自行评估在第三方 App 中登录学校账号的风险。
- 请遵守所在学校与平台的服务条款，仅将本项目用于个人学习与自用，**使用风险自负**。

## License

[Apache License 2.0](LICENSE)

Copyright 2026 小睦 (zy2755)
