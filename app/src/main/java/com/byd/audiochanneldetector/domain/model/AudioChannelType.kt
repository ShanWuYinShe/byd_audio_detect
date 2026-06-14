package com.byd.audiochanneldetector.domain.model

enum class AudioChannelType {
    MEDIA,          // 媒体通道
    NAVIGATION,     // 导航通道
    VOICE_CALL,     // 语音通话
    SYSTEM,         // 系统音效
    ALARM,          // 警报
    UNKNOWN         // 未知
}