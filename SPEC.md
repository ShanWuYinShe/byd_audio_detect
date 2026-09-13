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

- [x] **音频通道扫描**: 自动扫描并枚举车机上所有音频输出通道（输入设备与第三方盒子目标无关，不枚举）
- [x] **通道类型实测判定**: 经 `AudioPlaybackConfiguration.getAudioAttributes()` 观察正在播放内容的用途（导航播报/媒体音乐等），实测判定对应端口类型，结果跨刷新累积并标注"实测"
- [x] **通道类型推断**: 无实测数据时按 名称/地址关键词 → 设备类型推断，标注"推断"
- [x] **设备地址采集**: 输出设备 `AudioDeviceInfo.address`（audio_policy 端口 tag），区分同名 BUS 端口
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
    val address: String?,              // 设备地址（audio_policy 端口 tag，如 nav_bus）
    val sampleRate: Int?,             // 采样率
    val bitDepth: Int?,               // 位深
    val source: ChannelSource,         // 数据来源（ANDROID 公开 API / ALSA 节点）
    val typeEvidence: ChannelTypeEvidence  // 类型判定依据（实测 usage / 推断）
)

enum class ChannelSource {
    ANDROID,   // 通过 AudioManager 获取（主源，无需 root）
    ALSA       // 通过 /proc/asound 获取（辅助源）
}

enum class ChannelTypeEvidence {
    MEASURED,  // 实测：由播放用途（usage）观察得出
    INFERRED   // 推断：由名称/地址关键词或设备类型得出
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
1. AudioManager.getDevices()  - Android 标准 API（主源，仅 GET_DEVICES_OUTPUTS）
2. /proc/asound/cards         - 读取声卡列表（辅助）
3. /proc/asound/pcm           - 读取 PCM 设备信息（辅助）
4. /dev/snd/                  - 设备节点目录（辅助）
```

> 活跃状态判定基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），不依赖非公开的 `AudioMixer.getActiveTracks()`。
> 播放开始/停止经 `AudioManager.registerAudioPlaybackCallback`（API 26+）事件驱动即时刷新（ViewModel 防抖 400ms），另有每 30s 轮询兜底。

### 5.2 通道类型识别策略（优先级由高到低）

**① 实测判定（`AudioPlaybackConfiguration.getAudioAttributes()`，API 26+ 公开 API）**

正在播放的音频用途（usage）与其落点设备即实测证据，结果跨刷新累积，标注"实测"。

设备关联途径（"用途落在哪个端口"）：API 33+ 经 `AudioManager.getAudioDevicesForAttributes()` 路由查询（公开、免权限，返回设备与 `getDevices()` 枚举同源，按 type+address 对应）；API 31-32 依赖 `AudioPlaybackConfiguration.audioDeviceInfo`，但该信息系统侧从未填充（API 36 起文档标注 deprecated "never populated"），普通 App 恒为 null，此区间实测映射通常无法建立，类型保持推断结果。

| AudioAttributes 用途 | 类型判定 |
|----------------------|----------|
| USAGE_ASSISTANCE_NAVIGATION_GUIDANCE | NAVIGATION |
| USAGE_MEDIA / USAGE_GAME | MEDIA |
| USAGE_VOICE_COMMUNICATION(_SIGNALLING) | VOICE_CALL |
| USAGE_ALARM | ALARM |
| USAGE_NOTIFICATION* / ASSISTANCE_SONIFICATION / ASSISTANCE_ACCESSIBILITY | SYSTEM |
| USAGE_ASSISTANT / USAGE_UNKNOWN | 不覆写（保持推断） |

同一设备观察到多用途时按 导航 > 语音通话 > 警报 > 媒体 > 系统 取优（语义专属者胜出）。

**② 名称/地址关键词推断**（实测缺失时；检查顺序如下，媒体词最泛化故最后）

| 优先级 | 关键词（子串匹配） | 类型判定 |
|--------|--------------------|----------|
| 1 | nav, gps | NAVIGATION |
| 2 | voice, call, phone, voip | VOICE_CALL |
| 3 | alarm, ringtone, alert | ALARM |
| 4 | system, ui, notification | SYSTEM |
| 5 | media, music, audio, playback | MEDIA |

匹配文本 = 设备名称 + 地址（BUS 端口 tag 如 `nav_bus`）或 ALSA 卡名/PCM 名。已知局限："ui" 子串会误命中含 guide/quick 的名称，车机命名场景概率低，保持简单策略。

**③ 设备类型推断**：`AudioDeviceInfo.type` 映射（扬声器/HDMI/USB/蓝牙 A2DP/AUX 等输出 → MEDIA，SCO/TELEPHONY → VOICE_CALL）；`TYPE_BUS` 为车机多路输出主要形态，仅凭类型无法区分内容用途，不做臆断映射、诚实回落 UNKNOWN。

### 5.3 声道配置识别

- **Android 源**：`channelMasks` 中每个掩码的置位位数（`Integer.bitCount`）即声道数，取所有支持配置的最大值映射（MONO/STEREO/5.1/7.1）
- **ALSA 源**：尽力读取 `/proc/asound/cardX/pcmY{p,c}/sub0/hw_params`（`channels:`/`rate:`/`format:` 行，仅流打开时有数据；位深取格式名中的数字段，如 `S16_LE`→16），回退设备名/ID 的 `Nch` 短格式（如 2ch、6ch）。
  注意：`/proc/asound/pcm` 本身不含声道/采样率/位深（内核 `snd_pcm_proc_read` 输出格式为 `NN-NN: id : name[ : playback N][ : capture N]`）。

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