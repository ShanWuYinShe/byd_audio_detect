package com.byd.audiochanneldetector.ui

import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource

object DisplayLabels {
    fun typeText(type: AudioChannelType): String = when (type) {
        AudioChannelType.MEDIA -> "媒体"
        AudioChannelType.NAVIGATION -> "导航"
        AudioChannelType.VOICE_CALL -> "语音通话"
        AudioChannelType.SYSTEM -> "系统"
        AudioChannelType.ALARM -> "警报"
        AudioChannelType.UNKNOWN -> "未知"
    }

    fun configText(config: ChannelConfig): String = when (config) {
        ChannelConfig.MONO -> "单声道"
        ChannelConfig.STEREO -> "立体声"
        ChannelConfig.SURROUND_5_1 -> "5.1 环绕"
        ChannelConfig.SURROUND_7_1 -> "7.1 环绕"
        ChannelConfig.UNKNOWN -> "未知"
    }

    fun sourceText(source: ChannelSource): String = when (source) {
        ChannelSource.ANDROID -> "Android API"
        ChannelSource.ALSA -> "ALSA 节点"
    }
}
