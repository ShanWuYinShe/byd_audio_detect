# BYD Audio Channel Detector / 比亚迪音频通道检测

Android 原生应用，用于检测比亚迪车机（及通用 Android 设备）上的音频通道：枚举有效通道、区分媒体/导航/语音/系统等类型、识别声道组合（单声道 / 立体声 / 5.1 / 7.1），并标注当前是否正在播放。

## 功能

- **多源扫描**：Android 公开 API（`AudioManager.getDevices()`）为主源，`/proc/asound`、`/dev/snd` ALSA 节点为辅助源；跨源去重，标注每个通道的数据来源与可靠性。
- **类型识别**：关键词匹配 + Android 设备类型推断（媒体/导航/语音通话/系统/警报）。
- **声道检测**：Android 源按 `channelMasks` 的 `Integer.bitCount` 取最大值映射；ALSA 源解析 `N channels` / `Nch` 格式。
- **活跃判定**：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 诚实回落为"未检测到"。
- **UI**：Jetpack Compose + Material 3；列表 + 类型过滤；点击卡片弹出详情；顶栏分享纯文本报告（`Intent.ACTION_SEND`，无需权限）。

## 构建与测试

> 本机 shell 无独立 JDK，使用 Android Studio 自带 JBR（JDK 21）。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug   # 编译 Debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test             # 运行单元测试
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew lint             # Lint 检查
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew clean            # 清理构建产物
```

## 技术栈

- Kotlin 2.2.10 + Coroutines
- Jetpack Compose（BOM 2026.05.01）+ Material 3
- Hilt（DI）+ KSP
- 架构：MVVM + Clean Architecture（data / domain / ui 三层）
- minSdk 26 / targetSdk 37 / compileSdk 37

## 项目结构

```
app/src/main/java/com/byd/audiochanneldetector/
├── AudioChannelDetectorApp.kt        # @HiltAndroidApp 入口
├── MainActivity.kt
├── data/                             # 数据层
│   ├── repository/                   # Repository 接口与实现
│   └── scanner/                      # ALSA 节点扫描器
├── di/                               # Hilt 模块
├── domain/                           # 领域层
│   ├── model/                        # 领域模型（AudioChannel 等）
│   └── usecase/                      # 用例
└── ui/                               # UI 层
    ├── components/                   # 通用组件（卡片等）
    ├── screens/                      # 页面
    ├── theme/                        # Compose 主题
    └── viewmodel/                    # ViewModel
```

## 说明

- 应用不申请任何运行时权限。`/proc/asound`、`/dev/snd` 在非 root 设备上可能不可读，此时结果以 Android API 数据源为准。
- 详细设计见 `SPEC.md`，开发约束见 `AGENTS.md`。
