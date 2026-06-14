# 比亚迪音频通道检测 - 改进设计文档

**日期**: 2026-05-31
**状态**: 已确认

---

## 1. 项目概述

- **目标**: 开发一个 Android App，安装在比亚迪车机盒子上，用于检测和筛选真正可用的音频通道
- **约束**: 不需要 root 权限，使用标准 Android API
- **数据源**: Android AudioManager 为主，ALSA 为辅助

---

## 2. 技术方案

### 2.1 整体架构

```
用户启动 App
       ↓
AudioChannelRepositoryImpl.getAudioChannels()
       ↓
    ┌─────────────────────────────────────┐
    │  1. AudioManager.getDevices()      │ ← 主数据源（无需root）
    │     → 获取 AudioDeviceInfo 列表    │
    │     → 解析 type、channelMasks、    │
    │       sampleRates、productName     │
    └─────────────────────────────────────┘
    ┌─────────────────────────────────────┐
    │  2. ALSAScanner.scanChannels()      │ ← 辅助（可选，无root时跳过）
    │     → 读取 /proc/asound/pcm         │
    │     → 补充 devicePath 等信息       │
    └─────────────────────────────────────┘
       ↓
    mergeChannels() → 合并去重
       ↓
GetAudioChannelsUseCase
       ↓
MainViewModel (StateFlow)
       ↓
MainScreen (Compose List)
```

### 2.2 数据源优先级

| 优先级 | 数据源 | 权限需求 | 说明 |
|--------|--------|----------|------|
| **1 (主)** | AudioManager.getDevices() | 无 | 获取 Android 系统音频设备信息 |
| **2 (辅)** | /proc/asound/pcm | 可能需要 root | 补充 ALSA 底层设备路径 |

---

## 3. 核心改动

### 3.1 ALSAScanner 降级为辅助角色

- **当前问题**: ALSAScanner 是主要数据源，过度依赖 /proc/asound/ 节点
- **改进方案**:
  - 降低为辅助角色，仅在 /proc/asound/ 可读时使用
  - 不影响主流程：即使 ALSA 完全不可用，AudioManager 仍能提供有效通道列表
  - 捕获异常：ALSA 读取失败时不崩溃，静默跳过

```kotlin
// ALSAScanner.kt 改进
suspend fun scanChannels(): List<AudioChannel> = withContext(Dispatchers.IO) {
    try {
        // 原有 ALSA 扫描逻辑
        // ...
    } catch (e: Exception) {
        // ALSA 读取失败时返回空列表，不影响主流程
        emptyList()
    }
}
```

### 3.2 AudioChannel 模型更新

新增字段用于标识数据来源：

```kotlin
data class AudioChannel(
    val id: String,                    // 唯一标识
    val name: String,                  // 显示名称
    val type: AudioChannelType,        // 通道类型
    val channelConfig: ChannelConfig,  // 声道配置
    val isActive: Boolean,             // 是否正在使用
    val isValid: Boolean,              // 是否有效
    val devicePath: String?,           // ALSA 设备路径（可选）
    val cardName: String?,             // 声卡名称（可选）
    val sampleRate: Int?,              // 采样率
    val bitDepth: Int?,                // 位深
    val deviceType: Int = -1,          // 新增：AudioDeviceInfo.type
    val source: Source = Source.ANDROID // 新增：数据来源
)

enum class Source {
    ANDROID,   // 通过 AudioManager 获取（主）
    ALSA       // 通过 /proc/asound/ 获取（辅助）
}
```

### 3.3 通道类型识别（新逻辑）

使用 `AudioDeviceInfo.type` 枚举值进行可靠判断：

```kotlin
// AudioChannelRepositoryImpl.kt
private fun mapDeviceType(type: Int): AudioChannelType {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioChannelType.VOICE_CALL
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_FM_TRANSMITTER -> AudioChannelType.MEDIA
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioChannelType.MEDIA
        else -> AudioChannelType.UNKNOWN
    }
}
```

### 3.4 声道配置判断（新逻辑）

使用 `AudioDeviceInfo.channelMasks` 获取可靠的声道配置：

```kotlin
private fun inferChannelConfig(device: AudioDeviceInfo): ChannelConfig {
    if (device.channelMasks.isEmpty()) {
        return ChannelConfig.UNKNOWN
    }

    val primaryMask = device.channelMasks[0]
    val channelCount = AudioDeviceInfo.getChannelCountFromMask(primaryMask)

    return when {
        channelCount >= 8 -> ChannelConfig.SURROUND_7_1
        channelCount >= 6 -> ChannelConfig.SURROUND_5_1
        channelCount == 2 -> ChannelConfig.STEREO
        channelCount == 1 -> ChannelConfig.MONO
        else -> ChannelConfig.UNKNOWN
    }
}
```

### 3.5 合并去重逻辑改进

```kotlin
private fun mergeChannels(
    androidChannels: List<AudioChannel>,
    alsaChannels: List<AudioChannel>
): List<AudioChannel> {
    val result = mutableListOf<AudioChannel>()
    val seenIds = mutableSetOf<String>()

    // 优先添加 Android 音频设备（更可靠）
    androidChannels.forEach { channel ->
        if (channel.isValid && channel.id !in seenIds) {
            result.add(channel)
            seenIds.add(channel.id)
        }
    }

    // ALSA 作为补充：添加 Android 未覆盖的设备
    alsaChannels.forEach { channel ->
        if (channel.isValid && channel.id !in seenIds) {
            result.add(channel.copy(source = Source.ALSA))
            seenIds.add(channel.id)
        }
    }

    return result.sortedBy { it.type.ordinal }
}
```

---

## 4. 权限配置

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH" />
```

注：以上权限主要用于获取更完整的设备信息，不需要 root。

---

## 5. 文件修改清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `domain/model/AudioChannel.kt` | 修改 | 新增 `deviceType` 和 `source` 字段 |
| `domain/model/AudioChannelType.kt` | 保持 | 无需修改 |
| `domain/model/ChannelConfig.kt` | 保持 | 无需修改 |
| `data/repository/AudioChannelRepositoryImpl.kt` | 修改 | 改为 AudioManager 优先 |
| `data/scanner/ALSAScanner.kt` | 修改 | 添加异常处理，降级为辅助 |
| `domain/usecase/GetAudioChannelsUseCase.kt` | 保持 | 无需修改 |
| `ui/viewmodel/MainViewModel.kt` | 保持 | 无需修改 |
| `ui/screens/MainScreen.kt` | 保持 | 无需修改 |
| `AndroidManifest.xml` | 修改 | 添加必要权限 |

---

## 6. 测试计划

1. **单元测试**: 测试 AudioDeviceInfo.type → AudioChannelType 映射
2. **单元测试**: 测试 channelMasks → ChannelConfig 转换
3. **集成测试**: 测试 Repository 合并逻辑
4. **手动测试**: 在车机盒子上验证检测结果

---

## 7. 验证标准

- [ ] App 在没有 root 的车机盒子上正常运行
- [ ] 音频通道列表完整显示所有可用通道
- [ ] 通道类型（媒体/导航/语音/系统）判断准确
- [ ] 声道配置（单声道/立体声/5.1/7.1）识别正确
- [ ] ALSA 不可用时不影响主功能
- [ ] UI 正常显示通道列表，支持刷新和过滤