package com.byd.audiochanneldetector.domain.model

/**
 * 通道类型判定的证据来源，用于区分判定可靠程度：
 *
 * - [MEASURED]：实测判定——通过 AudioManager.getActivePlaybackConfigurations() 观察到
 *   该设备上正在播放的 AudioAttributes 用途（如导航播报 / 媒体音乐），是无需 root 条件下
 *   最可靠的类型证据；
 * - [INFERRED]：推断判定——基于设备名称/地址关键词或 AudioDeviceInfo.type 推断，
 *   可能有误，仅供参考。
 */
enum class ChannelTypeEvidence {
    MEASURED,
    INFERRED
}
