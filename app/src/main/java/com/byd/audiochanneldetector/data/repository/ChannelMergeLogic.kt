package com.byd.audiochanneldetector.data.repository

import com.byd.audiochanneldetector.domain.model.AudioChannel

internal object ChannelMergeLogic {

    private val CARD_PATH_REGEX = Regex("""pcmC(\d+)""")

    fun mergeChannels(
        androidChannels: List<AudioChannel>,
        alsaChannels: List<AudioChannel>
    ): List<AudioChannel> {
        val result = mutableListOf<AudioChannel>()
        val seenKeys = mutableSetOf<String>()

        val allChannels = androidChannels + alsaChannels

        allChannels.forEach { channel ->
            if (!channel.isValid) return@forEach
            val key = dedupKey(channel)
            if (key in seenKeys) return@forEach
            result.add(channel)
            seenKeys.add(key)
        }

        return result.sortedBy { it.type.ordinal }
    }

    fun dedupKey(channel: AudioChannel): String {
        val n = channel.name.trim()
        return if (n.isEmpty() || n.equals("Unknown Device", ignoreCase = true)) {
            channel.id
        } else {
            n.lowercase()
        }
    }

    fun resolveIsActive(channel: AudioChannel, activeKeys: Set<String>): Boolean {
        if (activeKeys.isEmpty()) return false

        if (channel.id.startsWith("android-")) {
            val deviceId = channel.id.removePrefix("android-")
            if ("id:$deviceId" in activeKeys) return true
        }

        channel.name.takeIf { it.isNotBlank() }?.let {
            if ("name:$it" in activeKeys) return true
        }
        channel.cardName?.takeIf { it.isNotBlank() }?.let {
            if ("name:$it" in activeKeys || "addr:$it" in activeKeys) return true
        }

        channel.devicePath?.let { path ->
            val cardMatch = CARD_PATH_REGEX.find(path)
            cardMatch?.groupValues?.get(1)?.let { cardIdx ->
                if ("addr:card$cardIdx" in activeKeys) return true
            }
        }

        return false
    }
}
