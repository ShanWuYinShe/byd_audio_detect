package com.byd.audiochanneldetector.data.scanner

import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ALSAScanner 纯文本解析函数的单元测试。
 * 仅覆盖与 Android Framework 无关的纯函数：类型识别、声道配置、hw_params 解析。
 * 解析目标格式以真实内核输出为准（sound/core/pcm.c、init.c、hw_params proc）。
 */
class ALSAScannerTest {

    private val scanner = ALSAScanner()

    // ---------------- inferChannelType ----------------

    @Test
    fun inferChannelType_mediaKeywords() {
        assertEquals(AudioChannelType.MEDIA, scanner.inferChannelType("Primary Media"))
        assertEquals(AudioChannelType.MEDIA, scanner.inferChannelType("Music Bus"))
        assertEquals(AudioChannelType.MEDIA, scanner.inferChannelType("Audio Output"))
        assertEquals(AudioChannelType.MEDIA, scanner.inferChannelType("playback-0"))
    }

    @Test
    fun inferChannelType_navigationBeatsMedia() {
        // 关键词检查顺序：导航词优先于泛化的媒体词
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("Nav Guidance"))
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("navigation"))
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("GPS Track"))
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("Audio Nav"))
    }

    @Test
    fun inferChannelType_voiceKeywords() {
        assertEquals(AudioChannelType.VOICE_CALL, scanner.inferChannelType("Voice Call"))
        assertEquals(AudioChannelType.VOICE_CALL, scanner.inferChannelType("phone line"))
        assertEquals(AudioChannelType.VOICE_CALL, scanner.inferChannelType("VoIP"))
    }

    @Test
    fun inferChannelType_systemKeywords() {
        assertEquals(AudioChannelType.SYSTEM, scanner.inferChannelType("System Sound"))
        assertEquals(AudioChannelType.SYSTEM, scanner.inferChannelType("UI Click"))
        assertEquals(AudioChannelType.SYSTEM, scanner.inferChannelType("Notification"))
    }

    @Test
    fun inferChannelType_alarmKeywords() {
        assertEquals(AudioChannelType.ALARM, scanner.inferChannelType("Alarm"))
        assertEquals(AudioChannelType.ALARM, scanner.inferChannelType("Ringtone"))
        assertEquals(AudioChannelType.ALARM, scanner.inferChannelType("Alert"))
    }

    @Test
    fun inferChannelType_unknownWhenNoKeyword() {
        assertEquals(AudioChannelType.UNKNOWN, scanner.inferChannelType("HDMI"))
        assertEquals(AudioChannelType.UNKNOWN, scanner.inferChannelType("hwdep"))
        assertEquals(AudioChannelType.UNKNOWN, scanner.inferChannelType(""))
    }

    // ---------------- resolveChannelConfig / channelCountToConfig ----------------

    @Test
    fun resolveChannelConfig_shortFormatInName() {
        // /proc/asound/pcm 本身不含声道信息，此处覆盖把声道数编进名字的驱动兜底
        assertEquals(ChannelConfig.STEREO, scanner.resolveChannelConfig("PCM 2ch"))
        assertEquals(ChannelConfig.SURROUND_5_1, scanner.resolveChannelConfig("HDMI 6ch"))
        assertEquals(ChannelConfig.SURROUND_7_1, scanner.resolveChannelConfig("HDMI 8ch"))
        assertEquals(ChannelConfig.MONO, scanner.resolveChannelConfig("Mono 1ch"))
    }

    @Test
    fun resolveChannelConfig_caseInsensitiveAndUnknown() {
        assertEquals(ChannelConfig.STEREO, scanner.resolveChannelConfig("pcm 2CH"))
        assertEquals(ChannelConfig.UNKNOWN, scanner.resolveChannelConfig("raw pcm no info"))
    }

    @Test
    fun channelCountToConfig_mapsKnownCounts() {
        assertEquals(ChannelConfig.MONO, scanner.channelCountToConfig(1))
        assertEquals(ChannelConfig.STEREO, scanner.channelCountToConfig(2))
        assertEquals(ChannelConfig.SURROUND_5_1, scanner.channelCountToConfig(6))
        assertEquals(ChannelConfig.SURROUND_7_1, scanner.channelCountToConfig(8))
        assertNull(scanner.channelCountToConfig(4))
        assertNull(scanner.channelCountToConfig(0))
    }

    // ---------------- parseHwParams ----------------

    @Test
    fun parseHwParams_runningStream() {
        // 真实 hw_params 输出（流打开时）
        val content = """
            access: MMAP_INTERLEAVED
            format: S16_LE
            subformat: STD
            channels: 2
            rate: 48000 (48000/1)
            period_size: 1024
            buffer_size: 4096
        """.trimIndent()
        val hw = scanner.parseHwParams(content)
        assertEquals(48000, hw?.sampleRate)
        assertEquals(2, hw?.channelCount)
        assertEquals(16, hw?.bitDepth)
    }

    @Test
    fun parseHwParams_bitDepthFromFormatNames() {
        val s24 = scanner.parseHwParams("format: S24_3LE\nchannels: 6\nrate: 44100 (44100/1)")
        assertEquals(24, s24?.bitDepth)
        assertEquals(6, s24?.channelCount)
        assertEquals(44100, s24?.sampleRate)
        val u8 = scanner.parseHwParams("format: U8\nchannels: 1\nrate: 8000 (8000/1)")
        assertEquals(8, u8?.bitDepth)
        assertEquals(ChannelConfig.MONO, u8?.channelCount?.let { scanner.channelCountToConfig(it) })
    }

    @Test
    fun parseHwParams_closedOrGarbageReturnsNull() {
        // 流未打开时 hw_params 内容为 "closed"
        assertNull(scanner.parseHwParams("closed"))
        assertNull(scanner.parseHwParams(""))
        assertNull(scanner.parseHwParams("no relevant keys here"))
    }
}
