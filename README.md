# BYD Audio Channel Detector / 比亚迪音频通道检测

Android 原生应用，用于检测比亚迪车机（及通用 Android 设备）上的音频输出通道：枚举有效通道、区分媒体/导航/语音/系统等类型、识别声道组合（单声道 / 立体声 / 5.1 / 7.1），并标注当前是否正在播放——为第三方盒子提供可靠的导航/媒体输出通道依据。

## 功能

- **多源扫描**：Android 公开 API（`AudioManager.getDevices()`，仅输出设备）为主源，`/proc/asound`、`/dev/snd` ALSA 节点为辅助源；跨源去重（同名设备按地址区分），标注每个通道的数据来源与可靠性。
- **类型实测判定**：基于 `AudioPlaybackConfiguration.getAudioAttributes()`（API 26+，公开 API）读取正在播放内容的用途（usage）——导航播报（`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`）落在哪个端口、媒体（`USAGE_MEDIA`）落在哪个端口，即实测判定对应通道类型，无需 root；设备关联经 `getAudioDevicesForAttributes()` 路由查询（API 33+；31-32 受系统限制尽力而为）；判定结果跨刷新累积，标注"实测"徽标。
- **类型推断兜底**：无实测数据时按 名称/地址关键词 → `AudioDeviceInfo.type` 推断（标注"推断"）；车机 BUS 多路输出端口的地址 tag（如 `nav_bus`）参与关键词匹配并原样展示。
- **声道检测**：Android 源按 `channelMasks` 的 `Integer.bitCount` 取最大值映射；ALSA 源尽力读取 `hw_params`（仅流打开时可读）并回退设备名 `Nch` 短格式。
- **活跃判定与事件驱动刷新**：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 诚实回落为"未检测到"；播放开始/停止经 `registerAudioPlaybackCallback` 即时触发扫描（带防抖），避免轮询错过短促导航播报。
- **UI**：Jetpack Compose + Material 3；列表 + 类型过滤；点击卡片弹出详情（含地址与判定依据）；顶栏分享纯文本报告（`Intent.ACTION_SEND`，无需权限）。

## 构建与测试

> 本机 shell 无独立 JDK，使用 Android Studio 自带 JBR（JDK 21）。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug   # 编译 Debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test             # 运行单元测试
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew lint             # Lint 检查
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew clean            # 清理构建产物
```

### 测试

`./gradlew test` 运行 `testDebugUnitTest`：4 个测试类共 48 例纯函数单测（AGP 9 下不存在 `testReleaseUnitTest` 任务，单测仅在 debug 变体执行）。release 签名凭据通过 `ANDROID_KEYSTORE` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` 环境变量提供，全部设置时 `assembleRelease` 产出签名 APK，否则产出 unsigned APK。

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
│   ├── ChannelTypeRules.kt           # 类型判定规则（关键词优先级 + usage 映射，纯函数）
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
