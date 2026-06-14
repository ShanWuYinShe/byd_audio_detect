package com.byd.audiochanneldetector.domain.model

data class AudioChannel(
    val id: String,                    // 唯一标识
    val name: String,                   // 显示名称
    val type: AudioChannelType,        // 通道类型
    val channelConfig: ChannelConfig,  // 声道配置
    val isActive: Boolean,              // 是否正在使用
    val isValid: Boolean,              // 是否有效
    val devicePath: String?,           // ALSA 设备路径
    val cardName: String?,              // 声卡名称
    val sampleRate: Int?,              // 采样率
    val bitDepth: Int?,                // 位深
    val source: ChannelSource          // 数据来源（Android 公开 API / ALSA 节点）
)