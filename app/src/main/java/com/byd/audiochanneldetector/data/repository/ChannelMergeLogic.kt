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

    /**
     * 去重 key：非通用名称 + 地址。
     * 车机上多个 BUS 输出端口的 productName 往往相同（或为空），仅按名称去重会把
     * 不同端口折叠成一条；address 是区分端口的关键字段，必须参与 key。
     */
    fun dedupKey(channel: AudioChannel): String {
        val n = channel.name.trim()
        val base = if (n.isEmpty() || n.equals("Unknown Device", ignoreCase = true)) {
            channel.id
        } else {
            n.lowercase()
        }
        val addr = channel.address?.trim().orEmpty()
        return if (addr.isEmpty()) base else "$base@$addr"
    }

    /**
     * 通道与活跃播放设备之间的可比对标识集合，同时供活跃判定与实测用途匹配使用。
     * 覆盖 id / 名称 / 声卡名 / 地址 / ALSA 卡号五种 key，兼容不同 OEM 的差异。
     */
    fun deviceKeys(channel: AudioChannel): Set<String> = buildSet {
        if (channel.id.startsWith("android-")) {
            add("id:${channel.id.removePrefix("android-")}")
        }
        channel.name.takeIf { it.isNotBlank() }?.let { add("name:$it") }
        channel.cardName?.takeIf { it.isNotBlank() }?.let {
            add("name:$it")
            add("addr:$it")
        }
        channel.address?.takeIf { it.isNotBlank() }?.let { add("addr:$it") }
        channel.devicePath?.let { path ->
            CARD_PATH_REGEX.find(path)?.groupValues?.get(1)?.let { cardIdx ->
                add("addr:card$cardIdx")
            }
        }
    }

    fun resolveIsActive(channel: AudioChannel, activeKeys: Set<String>): Boolean {
        if (activeKeys.isEmpty()) return false
        return ChannelMergeLogic.deviceKeys(channel).any { it in activeKeys }
    }
}
