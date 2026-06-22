package com.byd.audiochanneldetector.ui

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType

internal object ShareReportFormatter {

    fun format(channels: List<AudioChannel>): String = buildString {
        appendLine("比亚迪音频通道检测报告")
        appendLine("BYD Audio Channel Detector")
        appendLine()
        appendLine("总通道: ${channels.size}    活跃: ${channels.count { it.isActive }}")
        appendLine(
            "媒体: ${channels.count { it.type == AudioChannelType.MEDIA }}    " +
                "导航: ${channels.count { it.type == AudioChannelType.NAVIGATION }}"
        )
        appendLine()
        if (channels.isEmpty()) {
            appendLine("（未检测到通道）")
        } else {
            appendLine("----- 通道明细 -----")
            channels.forEachIndexed { index, ch ->
                appendLine()
                appendLine("[${index + 1}] ${ch.name}")
                appendLine(
                    "    类型: ${DisplayLabels.typeText(ch.type)} | 声道: ${DisplayLabels.configText(ch.channelConfig)} | " +
                        "来源: ${DisplayLabels.sourceText(ch.source)}"
                )
                appendLine(
                    "    有效: ${if (ch.isValid) "是" else "否"} | " +
                        "活跃: ${if (ch.isActive) "是" else "否"}"
                )
                ch.devicePath?.let { appendLine("    设备路径: $it") }
                ch.cardName?.let { appendLine("    声卡: $it") }
                ch.sampleRate?.let { appendLine("    采样率: ${it}Hz") }
                ch.bitDepth?.let { appendLine("    位深: ${it}bit") }
                appendLine("    ID: ${ch.id}")
            }
        }
    }
}
