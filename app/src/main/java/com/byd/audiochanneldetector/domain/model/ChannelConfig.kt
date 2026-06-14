package com.byd.audiochanneldetector.domain.model

enum class ChannelConfig {
    MONO,           // 单声道
    STEREO,         // 双声道/立体声
    SURROUND_5_1,   // 5.1环绕声
    SURROUND_7_1,   // 7.1环绕声
    UNKNOWN
}