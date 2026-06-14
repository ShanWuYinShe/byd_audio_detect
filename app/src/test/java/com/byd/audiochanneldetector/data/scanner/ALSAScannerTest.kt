package com.byd.audiochanneldetector.data.scanner

import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ALSAScanner 纯文本解析函数的单元测试。
 * 仅覆盖与 Android Framework 无关的纯函数：类型识别、声道配置、采样率、位深。
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
    fun inferChannelType_navigationKeywords() {
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("Nav Guidance"))
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("navigation"))
        assertEquals(AudioChannelType.NAVIGATION, scanner.inferChannelType("GPS Track"))
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

    // ---------------- resolveChannelConfig ----------------

    @Test
    fun resolveChannelConfig_longFormat() {
        assertEquals(ChannelConfig.MONO, scanner.resolveChannelConfig("1 channel", "name"))
        assertEquals(ChannelConfig.STEREO, scanner.resolveChannelConfig("2 channels", "name"))
        assertEquals(ChannelConfig.SURROUND_5_1, scanner.resolveChannelConfig("6 channels", "name"))
        assertEquals(ChannelConfig.SURROUND_7_1, scanner.resolveChannelConfig("8 channels", "name"))
    }

    @Test
    fun resolveChannelConfig_shortFormatFallback() {
        // 长格式未命中时回退到设备名中的 "Nch" 短格式
        assertEquals(ChannelConfig.STEREO, scanner.resolveChannelConfig("rate 48000", "PCM 2ch"))
        assertEquals(ChannelConfig.SURROUND_5_1, scanner.resolveChannelConfig("rate 48000", "PCM 6ch"))
    }

    @Test
    fun resolveChannelConfig_unknownWhenUnmatched() {
        assertEquals(ChannelConfig.UNKNOWN, scanner.resolveChannelConfig("no channel info", "raw pcm"))
    }

    // ---------------- extractSampleRate ----------------

    @Test
    fun extractSampleRate_kHz() {
        assertEquals(48000, scanner.extractSampleRate("rate: 48 kHz"))
        assertEquals(96000, scanner.extractSampleRate("rate: 96 kHz"))
    }

    @Test
    fun extractSampleRate_nullWhenNoKHzSuffix() {
        // 正则要求 "N kHz" 后缀；纯数字无 kHz 时不匹配
        assertEquals(null, scanner.extractSampleRate("rate: 48000"))
        assertEquals(null, scanner.extractSampleRate("no rate here"))
    }

    // ---------------- extractBitDepth ----------------

    @Test
    fun extractBitDepth_bit() {
        assertEquals(16, scanner.extractBitDepth("format: 16 bit"))
        assertEquals(24, scanner.extractBitDepth("24bit"))
    }

    @Test
    fun extractBitDepth_nullWhenAbsent() {
        assertEquals(null, scanner.extractBitDepth("no depth info"))
    }
}
