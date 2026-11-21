package com.byd.audiochanneldetector.ui

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence

internal object ShareReportFormatter {

    fun format(channels: List<AudioChannel>): String = buildString {
        appendLine("比亚迪音频通道检测报告")
        appendLine("BYD Audio Channel Detector")
        appendLine()
        val measured = channels.count { it.typeEvidence == ChannelTypeEvidence.MEASURED }
        appendLine("总通道: ${channels.size}    活跃: ${channels.count { it.isActive }}    实测判定: $measured")
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
                    "    类型: ${DisplayLabels.typeText(ch.type)}（${DisplayLabels.evidenceText(ch.typeEvidence)}） | " +
                        "声道: ${DisplayLabels.configText(ch.channelConfig)} | " +
                        "来源: ${DisplayLabels.sourceText(ch.source)}"
                )
                appendLine(
                    "    有效: ${if (ch.isValid) "是" else "否"} | " +
                        "活跃: ${if (ch.isActive) "是" else "否"}"
                )
                ch.address?.let { appendLine("    地址: $it") }
                ch.devicePath?.let { appendLine("    设备路径: $it") }
                ch.cardName?.let { appendLine("    声卡: $it") }
                ch.sampleRate?.let { appendLine("    采样率: ${it}Hz") }
                ch.bitDepth?.let { appendLine("    位深: ${it}bit") }
                appendLine("    ID: ${ch.id}")
            }
        }
    }

    /**
     * 将检测到的音频通道格式化为标准 JSON 字符串，供第三方盒子、伴侣程序与自动化脚本解析。
     */
    fun formatJson(channels: List<AudioChannel>): String = buildString {
        appendLine("{")
        appendLine("  \"version\": \"1.0\",")
        appendLine("  \"total\": ${channels.size},")
        appendLine("  \"activeCount\": ${channels.count { it.isActive }},")
        appendLine("  \"measuredCount\": ${channels.count { it.typeEvidence == ChannelTypeEvidence.MEASURED }},")
        appendLine("  \"summary\": {")
        appendLine("    \"media\": ${channels.count { it.type == AudioChannelType.MEDIA }},")
        appendLine("    \"navigation\": ${channels.count { it.type == AudioChannelType.NAVIGATION }},")
        appendLine("    \"voiceCall\": ${channels.count { it.type == AudioChannelType.VOICE_CALL }},")
        appendLine("    \"system\": ${channels.count { it.type == AudioChannelType.SYSTEM }},")
        appendLine("    \"alarm\": ${channels.count { it.type == AudioChannelType.ALARM }}")
        appendLine("  },")
        appendLine("  \"channels\": [")
        channels.forEachIndexed { index, ch ->
            appendLine("    {")
            appendLine("      \"id\": ${escapeJson(ch.id)},")
            appendLine("      \"name\": ${escapeJson(ch.name)},")
            appendLine("      \"type\": \"${ch.type.name}\",")
            appendLine("      \"channelConfig\": \"${ch.channelConfig.name}\",")
            appendLine("      \"isActive\": ${ch.isActive},")
            appendLine("      \"isValid\": ${ch.isValid},")
            ch.address?.let { appendLine("      \"address\": ${escapeJson(it)},") }
            ch.devicePath?.let { appendLine("      \"devicePath\": ${escapeJson(it)},") }
            ch.cardName?.let { appendLine("      \"cardName\": ${escapeJson(it)},") }
            ch.sampleRate?.let { appendLine("      \"sampleRate\": $it,") }
            ch.bitDepth?.let { appendLine("      \"bitDepth\": $it,") }
            appendLine("      \"source\": \"${ch.source.name}\",")
            appendLine("      \"typeEvidence\": \"${ch.typeEvidence.name}\"")
            val isLast = index == channels.lastIndex
            appendLine("    }${if (isLast) "" else ","}")
        }
        appendLine("  ]")
        append("}")
    }

    private fun escapeJson(str: String): String =
        "\"" + str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
}
