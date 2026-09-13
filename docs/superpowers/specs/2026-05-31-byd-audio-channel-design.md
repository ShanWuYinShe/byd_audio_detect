# 比亚迪音频通道检测 - 改进设计文档

**日期**: 2026-05-31
**状态**: 已确认（历史记录）

> **2026-09 更新**：本文档为当时的设计快照，以下内容已被后续实现取代，以 `SPEC.md` 为准：
> - §4 权限配置：实现坚持零权限方案（分享走 `Intent.ACTION_SEND`，无需 `MODIFY_AUDIO_SETTINGS`/蓝牙权限）；
> - §3.3/§3.4 类型与声道判定：新增 usage 实测判定（优先于关键词/设备类型），`TYPE_BUS` 不再映射 SYSTEM；
> - 关键词检查顺序调整为 导航 → 语音 → 警报 → 系统 → 媒体。

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
    val source: ChannelSource          // 新增：数据来源（无默认值，各构造点显式传入）
)

enum class ChannelSource {
    ANDROID,   // 通过 AudioManager 获取（主源，无需 root）
    ALSA       // 通过 /proc/asound 获取（辅助源）
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

`channelMasks` 是设备支持的声道配置掩码数组，每个掩码中置位的 bit 数即该配置的声道数。
（旧方案 `AudioDeviceInfo.getChannelCountFromMask` 在 compileSdk 36 公开 API 中不存在，已弃用。）
故对每个掩码用 `Integer.bitCount` 求声道数，取最大值：

```kotlin
private fun inferChannelConfig(device: AudioDeviceInfo): ChannelConfig {
    val masks = device.channelMasks
    if (masks.isEmpty()) return ChannelConfig.UNKNOWN
    val maxChannelCount = masks.maxOf { Integer.bitCount(it) }
    return when {
        maxChannelCount >= 8 -> ChannelConfig.SURROUND_7_1
        maxChannelCount >= 6 -> ChannelConfig.SURROUND_5_1
        maxChannelCount >= 2 -> ChannelConfig.STEREO
        maxChannelCount >= 1 -> ChannelConfig.MONO
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

    // ALSA 作为补充：添加 Android 未覆盖的设备（source 已在各自构造点确定，此处不再覆写）
    alsaChannels.forEach { channel ->
        if (channel.isValid && channel.id !in seenIds) {
            result.add(channel)
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

## 5. 文件修改清单（实际实现）

| 文件 | 操作 | 说明 |
|------|------|------|
| `domain/model/AudioChannel.kt` | 修改 | 新增 `source` 字段（无默认值） |
| `domain/model/ChannelSource.kt` | 新增 | `ChannelSource` 枚举（ANDROID/ALSA） |
| `data/repository/AudioChannelRepositoryImpl.kt` | 修改 | 声道判定改用 `Integer.bitCount(mask)`；Android 源标记 `source=ANDROID` |
| `data/scanner/ALSAScanner.kt` | 修改 | 3 处构造标记 `source=ALSA`；4 个纯解析方法改 `internal` 便于测试 |
| `domain/usecase/GetAudioChannelsUseCase.kt` | 保持 | 无需修改 |
| `ui/viewmodel/MainViewModel.kt` | 修改 | 新增 `selectedChannel` 状态、`buildShareText()` |
| `ui/screens/MainScreen.kt` | 修改 | `collectAsStateWithLifecycle`；分享按钮；通道详情 `ModalBottomSheet` |
| `ui/components/AudioChannelCard.kt` | 修改 | 卡片可点击；新增 `SourceChip` |
| `app/build.gradle.kts` | 修改 | 新增 `lifecycle-runtime-compose`、`testImplementation(junit)` |
| `app/src/test/.../ALSAScannerTest.kt` | 新增 | ALSAScanner 纯函数单元测试（13 例） |
| `AndroidManifest.xml` | 保持 | 未新增权限（分享用 ACTION_SEND 无需权限） |

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