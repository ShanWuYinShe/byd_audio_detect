package com.byd.audiochanneldetector.domain.model

/**
 * 音频通道的数据来源，用于标识检测结果的可靠性。
 *
 * - [ANDROID]：通过 Android 公开 API（AudioManager.getDevices）获取，无需 root，最可靠。
 * - [ALSA]：通过读取 /proc/asound 与 /dev/snd 节点获取，属辅助来源；
 *   部分节点在无 root 设备上可能不可读或信息不全。
 */
enum class ChannelSource {
    ANDROID,
    ALSA
}
