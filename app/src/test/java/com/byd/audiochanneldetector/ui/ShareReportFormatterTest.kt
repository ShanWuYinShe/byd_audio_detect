package com.byd.audiochanneldetector.ui

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
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
        sampleRate: Int? = null,
        bitDepth: Int? = null
    ) = AudioChannel(
        id = "id-$name", name = name, type = type,
        channelConfig = ChannelConfig.STEREO, isActive = active, isValid = true,
        devicePath = devicePath, cardName = cardName,
        sampleRate = sampleRate, bitDepth = bitDepth, source = source
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
}
