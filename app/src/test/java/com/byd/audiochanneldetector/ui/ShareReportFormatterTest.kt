package com.byd.audiochanneldetector.ui

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareReportFormatterTest {

    private fun channel(
        name: String,
        type: AudioChannelType = AudioChannelType.MEDIA,
        active: Boolean = false,
        source: ChannelSource = ChannelSource.ANDROID,
        devicePath: String? = null,
        cardName: String? = null,
        address: String? = null,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
        evidence: ChannelTypeEvidence = ChannelTypeEvidence.INFERRED
    ) = AudioChannel(
        id = "id-$name", name = name, type = type,
        channelConfig = ChannelConfig.STEREO, isActive = active, isValid = true,
        devicePath = devicePath, cardName = cardName, address = address,
        sampleRate = sampleRate, bitDepth = bitDepth, source = source,
        typeEvidence = evidence
    )

    @Test
    fun format_emptyChannelsShowsPlaceholder() {
        val text = ShareReportFormatter.format(emptyList())
        assertTrue(text.contains("未检测到通道"))
        assertTrue(text.contains("总通道: 0"))
    }

    @Test
    fun format_includesCounts() {
        val channels = listOf(
            channel("Media1", AudioChannelType.MEDIA),
            channel("Media2", AudioChannelType.MEDIA, active = true),
            channel("Nav1", AudioChannelType.NAVIGATION)
        )
        val text = ShareReportFormatter.format(channels)
        assertTrue(text.contains("总通道: 3"))
        assertTrue(text.contains("活跃: 1"))
        assertTrue(text.contains("实测判定: 0"))
        assertTrue(text.contains("媒体: 2"))
        assertTrue(text.contains("导航: 1"))
    }

    @Test
    fun format_includesDetails() {
        val channels = listOf(
            channel(
                "Speaker", type = AudioChannelType.MEDIA, source = ChannelSource.ANDROID,
                devicePath = "/dev/snd/pcmC0D0p", sampleRate = 48000, bitDepth = 16
            )
        )
        val text = ShareReportFormatter.format(channels)
        assertTrue(text.contains("[1] Speaker"))
        assertTrue(text.contains("媒体"))
        assertTrue(text.contains("立体声"))
        assertTrue(text.contains("Android API"))
        assertTrue(text.contains("/dev/snd/pcmC0D0p"))
        assertTrue(text.contains("48000Hz"))
        assertTrue(text.contains("16bit"))
    }

    @Test
    fun format_includesEvidenceAndAddress() {
        val channels = listOf(
            channel(
                "Nav Port", type = AudioChannelType.NAVIGATION,
                address = "nav_bus", evidence = ChannelTypeEvidence.MEASURED
            ),
            channel("Speaker", type = AudioChannelType.MEDIA)
        )
        val text = ShareReportFormatter.format(channels)
        assertTrue(text.contains("类型: 导航（实测）"))
        assertTrue(text.contains("地址: nav_bus"))
        assertTrue(text.contains("类型: 媒体（推断）"))
        assertTrue(text.contains("实测判定: 1"))
    }

    // ---------------- formatJson ----------------

    @Test
    fun formatJson_emptyChannels() {
        val json = ShareReportFormatter.formatJson(emptyList())
        assertTrue(json.contains("\"version\": \"1.0\""))
        assertTrue(json.contains("\"total\": 0"))
        assertTrue(json.contains("\"channels\": [\n  ]"))
    }

    @Test
    fun formatJson_containsCountsAndTypes() {
        val channels = listOf(
            channel("Media1", AudioChannelType.MEDIA, active = true, evidence = ChannelTypeEvidence.MEASURED),
            channel("Nav1", AudioChannelType.NAVIGATION)
        )
        val json = ShareReportFormatter.formatJson(channels)
        assertTrue(json.contains("\"total\": 2"))
        assertTrue(json.contains("\"activeCount\": 1"))
        assertTrue(json.contains("\"measuredCount\": 1"))
        assertTrue(json.contains("\"media\": 1"))
        assertTrue(json.contains("\"navigation\": 1"))
    }

    @Test
    fun formatJson_fullChannelProperties() {
        val ch = channel(
            "Speaker",
            type = AudioChannelType.MEDIA,
            source = ChannelSource.ANDROID,
            devicePath = "/dev/snd/pcmC0D0p",
            cardName = "card0",
            address = "bus0_media",
            sampleRate = 48000,
            bitDepth = 16,
            evidence = ChannelTypeEvidence.MEASURED
        )
        val json = ShareReportFormatter.formatJson(listOf(ch))
        assertTrue(json.contains("\"id\": \"id-Speaker\""))
        assertTrue(json.contains("\"name\": \"Speaker\""))
        assertTrue(json.contains("\"type\": \"MEDIA\""))
        assertTrue(json.contains("\"channelConfig\": \"STEREO\""))
        assertTrue(json.contains("\"address\": \"bus0_media\""))
        assertTrue(json.contains("\"devicePath\": \"/dev/snd/pcmC0D0p\""))
        assertTrue(json.contains("\"cardName\": \"card0\""))
        assertTrue(json.contains("\"sampleRate\": 48000"))
        assertTrue(json.contains("\"bitDepth\": 16"))
        assertTrue(json.contains("\"source\": \"ANDROID\""))
        assertTrue(json.contains("\"typeEvidence\": \"MEASURED\""))
    }

    @Test
    fun formatJson_escapesQuotesAndBackslashes() {
        val ch = channel("Test \"Quote\" \\Slash")
        val json = ShareReportFormatter.formatJson(listOf(ch))
        assertTrue(json.contains("\"name\": \"Test \\\"Quote\\\" \\\\Slash\""))
    }
}
