package com.byd.audiochanneldetector.data

import android.media.AudioAttributes
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ChannelTypeRules 纯函数单元测试：关键词识别（含优先级）、播放用途 → 类型映射与多用途优先级。
 */
class ChannelTypeRulesTest {

    // ---------------- inferTypeFromKeywords ----------------

    @Test
    fun keywords_navigationBeforeMedia() {
        // 媒体关键词（audio/playback）最泛化，含导航词的名称必须优先判导航
        assertEquals(AudioChannelType.NAVIGATION, ChannelTypeRules.inferTypeFromKeywords("Audio Nav"))
        assertEquals(AudioChannelType.NAVIGATION, ChannelTypeRules.inferTypeFromKeywords("Media Nav Bus"))
        assertEquals(AudioChannelType.NAVIGATION, ChannelTypeRules.inferTypeFromKeywords("nav_audio"))
    }

    @Test
    fun keywords_mediaNames() {
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.inferTypeFromKeywords("Primary Media"))
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.inferTypeFromKeywords("Music Bus"))
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.inferTypeFromKeywords("Audio Output"))
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.inferTypeFromKeywords("playback-0"))
    }

    @Test
    fun keywords_voiceAlarmSystem() {
        assertEquals(AudioChannelType.VOICE_CALL, ChannelTypeRules.inferTypeFromKeywords("Voice Call"))
        assertEquals(AudioChannelType.VOICE_CALL, ChannelTypeRules.inferTypeFromKeywords("phone line"))
        assertEquals(AudioChannelType.ALARM, ChannelTypeRules.inferTypeFromKeywords("Ringtone"))
        assertEquals(AudioChannelType.SYSTEM, ChannelTypeRules.inferTypeFromKeywords("Notification"))
        assertEquals(AudioChannelType.SYSTEM, ChannelTypeRules.inferTypeFromKeywords("UI Click"))
    }

    @Test
    fun keywords_unknownWhenNoKeyword() {
        assertEquals(AudioChannelType.UNKNOWN, ChannelTypeRules.inferTypeFromKeywords("HDMI"))
        assertEquals(AudioChannelType.UNKNOWN, ChannelTypeRules.inferTypeFromKeywords("hwdep"))
        assertEquals(AudioChannelType.UNKNOWN, ChannelTypeRules.inferTypeFromKeywords(null, null))
    }

    @Test
    fun keywords_matchesAcrossNameAndAddress() {
        // 名称无语义、地址含端口 tag 的情况（车机 BUS 端口常见）
        assertEquals(
            AudioChannelType.NAVIGATION,
            ChannelTypeRules.inferTypeFromKeywords("BYD Audio", "nav_bus")
        )
        assertEquals(
            AudioChannelType.MEDIA,
            ChannelTypeRules.inferTypeFromKeywords("BYD Audio", "media_bus")
        )
    }

    // ---------------- usageToType ----------------

    @Test
    fun usage_mediaFamily() {
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.usageToType(AudioAttributes.USAGE_MEDIA))
        assertEquals(AudioChannelType.MEDIA, ChannelTypeRules.usageToType(AudioAttributes.USAGE_GAME))
    }

    @Test
    fun usage_navigation() {
        assertEquals(
            AudioChannelType.NAVIGATION,
            ChannelTypeRules.usageToType(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        )
    }

    @Test
    fun usage_voiceAlarmSystem() {
        assertEquals(
            AudioChannelType.VOICE_CALL,
            ChannelTypeRules.usageToType(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        )
        assertEquals(AudioChannelType.ALARM, ChannelTypeRules.usageToType(AudioAttributes.USAGE_ALARM))
        assertEquals(
            AudioChannelType.SYSTEM,
            ChannelTypeRules.usageToType(AudioAttributes.USAGE_NOTIFICATION)
        )
        assertEquals(
            AudioChannelType.SYSTEM,
            ChannelTypeRules.usageToType(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        )
    }

    @Test
    fun usage_unmappableReturnsNull() {
        assertNull(ChannelTypeRules.usageToType(AudioAttributes.USAGE_ASSISTANT))
        assertNull(ChannelTypeRules.usageToType(AudioAttributes.USAGE_UNKNOWN))
    }

    // ---------------- typeFromUsages ----------------

    @Test
    fun usages_priorityNavigationOverMedia() {
        // 同一端口同时观察到导航与媒体时按导航上报
        assertEquals(
            AudioChannelType.NAVIGATION,
            ChannelTypeRules.typeFromUsages(
                listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            )
        )
    }

    @Test
    fun usages_singleMedia() {
        assertEquals(
            AudioChannelType.MEDIA,
            ChannelTypeRules.typeFromUsages(listOf(AudioAttributes.USAGE_MEDIA))
        )
    }

    @Test
    fun usages_emptyOrUnmappableReturnsNull() {
        assertNull(ChannelTypeRules.typeFromUsages(emptyList()))
        assertNull(ChannelTypeRules.typeFromUsages(listOf(AudioAttributes.USAGE_UNKNOWN)))
    }
}
