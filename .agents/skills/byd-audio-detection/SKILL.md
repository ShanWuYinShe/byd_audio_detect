---
name: byd-audio-detection
description: 比亚迪车机音频通道检测领域规则：检测路径、类型识别优先级、设备地址、声道判定、来源标识、活跃判定。触发：修改检测逻辑、类型识别规则、声道/活跃判定、ALSA 解析时加载。
---

# 比亚迪车机音频通道检测领域规则

- **检测路径**：`AudioManager.getDevices()`（仅输出设备，主源）→ `/proc/asound/cards` → `/proc/asound/pcm` → `/dev/snd/`（ALSA 辅助源）。
- **通道类型识别**（优先级由高到低）：
  1. **实测**：`AudioPlaybackConfiguration.getAudioAttributes()` 的用途映射（`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`=导航、`USAGE_MEDIA`=媒体等），结果跨刷新累积并标注「实测」；设备关联 API 33+ 经 `AudioManager.getAudioDevicesForAttributes()` 路由查询（公开免权限，返回与 `getDevices()` 枚举同源），31-32 依赖 `audioDeviceInfo`（系统侧从未填充，普通 App 恒 null，尽力而为）；
  2. **名称/地址关键词**：顺序为导航 → 语音 → 警报 → 系统 → 媒体（媒体词最泛化故最后），规则集中在 `data/ChannelTypeRules`；
  3. **`AudioDeviceInfo.type` 推断**：`TYPE_BUS` 不做臆断映射，诚实回落 `UNKNOWN`。
- **设备地址**：Android 源采集 `AudioDeviceInfo.address`（API 28+，即 audio_policy 端口 tag），是区分同名 BUS 端口的关键，参与去重 key、活跃匹配与关键词判定。
- **跨源 ID 格式差异**：`/proc/asound/pcm` 行号是 `%02i` 补零格式（`00-00`），`/dev/snd/` 节点名无前导零（`pcmC0D0p`）；跨源比对 card/device 必须按数值（`toInt`）而非字符串，否则同一设备会被误判为不同设备、跨源去重失效。PCM 子流的 proc 目录带方向后缀（`pcmYp`/`pcmYc`），不存在无后缀路径。验证：`ALSAScannerTest` 的 `scanDevSnd_skipsDeviceAlreadyListedByPcm`、`scanDevSnd_addsNormalizedPathAndFoldsDirectionNodes`。
- **ALSA 辅助源的权限模型**：`/dev/snd/` 节点属 `system:audio` 组（`crw-rw----`），普通应用 uid 不在组内 → `canRead()` false → `scanDevSnd` 静默跳过该节点、兜底列表为空，属预期降级而非缺陷；production 镜像 `adb root` 被拒、`/proc/asound` 对 shell 亦 Permission denied。受限环境（模拟器/非 root 真机）通道列表仅含 Android 源即为此表现，车机系统环境下节点可读时兜底路径才产生数据。验证：模拟器上 `ls -la /dev/snd` 看节点属组，`logcat -s ALSAScanner` 无 warning（静默跳过不报错）。
- **声道配置判定**：Android 源用 `AudioDeviceInfo.channelMasks`，对每个掩码 `Integer.bitCount(mask)` 求声道数（掩码置位位数即声道数）取最大值映射；ALSA 源尽力读取 `/proc/asound/cardX/pcmY{p,c}/sub0/hw_params`（仅流打开时有数据），回退设备名 `Nch` 短格式。`/proc/asound/pcm` 本身不含声道/采样率/位深（内核 `snd_pcm_proc_read` 格式：`NN-NN: id : name[ : playback N]`），解析格式以内核源码为准。
- **数据来源标识**：`AudioChannel.source`（`ChannelSource.ANDROID`=公开 API 主源 / `ALSA`=/proc 节点辅助源），`AudioChannel.typeEvidence`（`MEASURED`=usage 实测 / `INFERRED`=关键词或设备类型推断）区分判定可靠性。
- **活跃判定**：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 返回空集、`isActive` 诚实回落 `false`；播放变化经 `registerAudioPlaybackCallback`（API 26+）事件驱动刷新（ViewModel 防抖 400ms），另有每 30s 轮询兜底。
