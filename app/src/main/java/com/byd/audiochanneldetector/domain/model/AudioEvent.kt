package com.byd.audiochanneldetector.domain.model

enum class AudioEventType {
    DEVICE_CONNECTED,      // 物理/虚拟音频设备挂载
    DEVICE_DISCONNECTED,   // 设备移除
    PLAYBACK_CHANGE,       // 播放流活跃状态变更（开始/停止/用途）
    SOUND_TEST,            // 通道测试发声
    DETECTION_REFRESH      // 通道检测刷新
}

data class AudioEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: AudioEventType,
    val message: String
)
