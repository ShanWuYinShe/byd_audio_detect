package com.byd.audiochanneldetector.data

import android.media.AudioAttributes
import com.byd.audiochanneldetector.domain.model.AudioChannelType

/**
 * 通道类型判定规则（纯函数，集中供 ALSAScanner 与 AudioChannelRepositoryImpl 复用）。
 */
internal object ChannelTypeRules {

    /**
     * 通道名称关键词识别（名称与地址统一走本表）。
     *
     * 检查顺序为 NAVIGATION → VOICE_CALL → ALARM → SYSTEM → MEDIA：
     * 语义专属的关键词（nav/gps、voice/phone、alarm…）优先于最泛化的媒体词
     * （audio/playback 出现在几乎所有声卡名中，放最后避免抢占）。
     * 与 SPEC §5.2 的差异：仅调整检查顺序，关键词集合不变。
     *
     * 已知局限：子串匹配，"ui" 会误命中含 guide/quick 等词的名称；
     * 车机名称多为大写缩写词，该误报概率低，故保持简单子串策略。
     */
    fun inferTypeFromKeywords(vararg texts: String?): AudioChannelType {
        val lowerTexts = texts.filterNotNull().joinToString(" ") { it.lowercase() }
        return when {
            NAV_KEYWORDS.any(lowerTexts::contains) -> AudioChannelType.NAVIGATION
            VOICE_KEYWORDS.any(lowerTexts::contains) -> AudioChannelType.VOICE_CALL
            ALARM_KEYWORDS.any(lowerTexts::contains) -> AudioChannelType.ALARM
            SYSTEM_KEYWORDS.any(lowerTexts::contains) -> AudioChannelType.SYSTEM
            MEDIA_KEYWORDS.any(lowerTexts::contains) -> AudioChannelType.MEDIA
            else -> AudioChannelType.UNKNOWN
        }
    }

    /** 单个 AudioAttributes 用途 → 通道类型；无法明确分类的用途返回 null（不覆写推断结果）。 */
    fun usageToType(usage: Int): AudioChannelType? = when (usage) {
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_GAME -> AudioChannelType.MEDIA

        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> AudioChannelType.NAVIGATION

        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> AudioChannelType.VOICE_CALL

        AudioAttributes.USAGE_ALARM -> AudioChannelType.ALARM

        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_NOTIFICATION_EVENT,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY -> AudioChannelType.SYSTEM

        // USAGE_ASSISTANT / USAGE_UNKNOWN 语义不明确，不做实测覆写
        else -> null
    }

    /**
     * 从一台设备上观察到的多个用途中判定通道类型。
     * 多用途并存时按 优先级：导航 > 语音通话 > 警报 > 媒体 > 系统。
     * 理由：语义专属且罕见者胜出——同时承载导航与媒体的端口按导航上报，
     * 提醒使用者该端口并非纯媒体通道；系统音最常搭车，排最后。
     */
    fun typeFromUsages(usages: Collection<Int>): AudioChannelType? {
        val types = usages.mapNotNull(::usageToType).toSet()
        return PRIORITY.firstOrNull { it in types }
    }

    private val PRIORITY = listOf(
        AudioChannelType.NAVIGATION,
        AudioChannelType.VOICE_CALL,
        AudioChannelType.ALARM,
        AudioChannelType.MEDIA,
        AudioChannelType.SYSTEM
    )

    private val NAV_KEYWORDS = listOf("nav", "gps")
    private val VOICE_KEYWORDS = listOf("voice", "call", "phone", "voip")
    private val ALARM_KEYWORDS = listOf("alarm", "ringtone", "alert")
    private val SYSTEM_KEYWORDS = listOf("system", "ui", "notification")
    private val MEDIA_KEYWORDS = listOf("media", "music", "audio", "playback")
}
