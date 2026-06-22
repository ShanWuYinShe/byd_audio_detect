# BYD Audio Channel Detector - 技术规范

## 1. 项目概述

- **项目名称**: BYD Audio Channel Detector
- **项目类型**: Android 原生应用
- **核心功能**: 检测比亚迪车机上的音频通道，识别有效通道，区分媒体/导航通道，标识声道组合（单声道/双声道/5.1）
- **目标用户**: 比亚迪车主、Android 车机开发者

## 2. 技术栈

| 组件 | 技术选择 |
|------|----------|
| 开发语言 | Kotlin 2.2.10 |
| UI 框架 | Jetpack Compose (BOM 2026.05.01) + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 状态管理 | StateFlow + Compose State |
| 依赖注入 | Hilt |
| 并发 | Kotlin Coroutines |
| 最低 SDK | Android API 26 (Android 8.0) |
| 目标 SDK | Android API 37 |
| 编译 SDK | Android API 37 |

## 3. 功能列表

### 3.1 核心功能

- [x] **音频通道扫描**: 自动扫描并枚举车机上所有音频输入/输出通道
- [x] **通道类型识别**: 根据通道名称和属性判断是媒体/导航/语音/系统通道
- [x] **声道配置检测**: 识别单声道、双声道(立体声)、5.1环绕声
- [x] **通道状态显示**: 显示通道是否有效、是否正在使用
- [x] **设备路径显示**: 显示 ALSA 设备节点路径

### 3.2 用户界面

- [x] **通道列表视图**: 以列表形式展示所有检测到的通道
- [x] **通道详情卡片**: 每个通道显示详细信息（类型、声道配置、设备路径）
- [x] **刷新功能**: 支持手动刷新通道列表
- [x] **过滤功能**: 支持按通道类型过滤显示
- [x] **通道详情页**: 点击卡片弹出 ModalBottomSheet 展示完整字段（来源、活跃、采样率、位深等）
- [x] **检测结果分享**: 经系统分享面板（Intent.ACTION_SEND）导出纯文本报告，无需新权限

## 4. 数据模型

```kotlin
data class AudioChannel(
    val id: String,                    // 唯一标识
    val name: String,                  // 显示名称
    val type: AudioChannelType,       // 通道类型
    val channelConfig: ChannelConfig,  // 声道配置
    val isActive: Boolean,             // 是否正在使用
    val isValid: Boolean,              // 是否有效
    val devicePath: String?,          // ALSA 设备路径
    val cardName: String?,             // 声卡名称
    val sampleRate: Int?,             // 采样率
    val bitDepth: Int?,               // 位深
    val source: ChannelSource         // 数据来源（ANDROID 公开 API / ALSA 节点）
)

enum class ChannelSource {
    ANDROID,   // 通过 AudioManager 获取（主源，无需 root）
    ALSA       // 通过 /proc/asound 获取（辅助源）
}

enum class AudioChannelType {
    MEDIA,          // 媒体通道
    NAVIGATION,     // 导航通道
    VOICE_CALL,     // 语音通话
    SYSTEM,         // 系统音效
    ALARM,          // 警报
    UNKNOWN         // 未知
}

enum class ChannelConfig {
    MONO,           // 单声道
    STEREO,         // 双声道/立体声
    SURROUND_5_1,   // 5.1环绕声
    SURROUND_7_1,   // 7.1环绕声
    UNKNOWN
}
```

## 5. 技术实现方案

### 5.1 音频通道检测路径

```
1. /proc/asound/cards         - 读取声卡列表
2. /proc/asound/pcm           - 读取 PCM 设备信息
3. /dev/snd/                  - 设备节点目录
4. AudioManager.getDevices()  - Android 标准 API（主源）
```

> 活跃状态判定基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），不依赖非公开的 `AudioMixer.getActiveTracks()`。

### 5.2 通道类型识别策略

| 通道名称关键词 | 类型判定 |
|----------------|----------|
| media, music, audio, playback | MEDIA |
| nav, navigation, gps | NAVIGATION |
| voice, call, phone, voip | VOICE_CALL |
| system, ui, notification | SYSTEM |
| alarm, ringtone, alert | ALARM |

### 5.3 声道配置识别

- **Android 源**：`AudioDeviceInfo.channelMasks` 中每个掩码的置位位数（`Integer.bitCount(mask)`）即声道数，取所有支持配置的最大值映射（MONO/STEREO/5.1/7.1）
- **ALSA 源**：解析 `/proc/asound/pcm` 中的 `N channels` 长格式，回退到设备名的 `Nch` 短格式（如 2ch、6ch）

## 6. 项目结构

```
app/
├── src/main/
│   ├── java/com/byd/audiochanneldetector/
│   │   ├── di/                     # Hilt 模块
│   │   ├── data/
│   │   │   ├── repository/         # 数据仓库
│   │   │   └── scanner/            # ALSA 扫描器
│   │   ├── domain/
│   │   │   ├── model/              # 领域模型
│   │   │   └── usecase/            # 用例
│   │   └── ui/
│   │       ├── theme/              # Compose 主题
│   │       ├── components/         # 通用组件
│   │       ├── screens/            # 页面
│   │       └── viewmodel/          # ViewModel
│   └── res/
└── build.gradle.kts
```

## 7. 验证标准

- 编译通过，无错误
- UI 正常显示通道列表
- 能够正确区分媒体/导航通道
- 能够识别声道配置（单声道/立体声/5.1）