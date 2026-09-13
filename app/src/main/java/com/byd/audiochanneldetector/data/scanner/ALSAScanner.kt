package com.byd.audiochanneldetector.data.scanner

import android.util.Log
import com.byd.audiochanneldetector.data.ChannelTypeRules
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ALSAScanner @Inject constructor() {

    suspend fun scanChannels(): List<AudioChannel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<AudioChannel>()

        // 三条 ALSA 路径独立兜底：任一路径失败不影响其他路径与上层 AudioManager 数据源
        // 读取 /proc/asound/cards 获取声卡列表
        try {
            val cardsFile = File("/proc/asound/cards")
            if (cardsFile.exists()) {
                val cardsContent = cardsFile.readText()
                parseCards(cardsContent, channels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan /proc/asound/cards failed", e)
        }

        // 读取 /proc/asound/pcm 获取 PCM 设备信息
        try {
            val pcmFile = File("/proc/asound/pcm")
            if (pcmFile.exists()) {
                val pcmContent = pcmFile.readText()
                parsePCM(pcmContent, channels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan /proc/asound/pcm failed", e)
        }

        // 扫描 /dev/snd/ 设备节点
        try {
            val devDir = File("/dev/snd")
            if (devDir.exists() && devDir.isDirectory) {
                scanDevSnd(devDir, channels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan /dev/snd failed", e)
        }

        channels
    }

    private fun parseCards(content: String, channels: MutableList<AudioChannel>) {
        // 真实内核格式（sound/core/init.c）：" %2i [%-16s]: %s - %s"，
        // id 左对齐占 16 列，长度不足补空格、恰好占满时 `]` 紧跟其后，故 id 段用 [^\]]+ 并 trim
        val cardRegex = """(\d+)\s+\[([^\]]+)\]\s*:\s+(.+)""".toRegex()
        cardRegex.findAll(content).forEach { match ->
            val (index, id, name) = match.destructured
            // 检查声卡目录是否存在且可读
            val cardDir = File("/proc/asound/card$index")
            val isValid = cardDir.exists() && cardDir.canRead()
            channels.add(
                AudioChannel(
                    id = "card-$index",
                    name = name.ifEmpty { "Card $index" },
                    type = inferChannelType("$name $id"),
                    channelConfig = ChannelConfig.UNKNOWN,
                    isActive = false, // 占位：由 Repository 基于 getActivePlaybackConfigurations 覆写
                    isValid = isValid,
                    devicePath = "/dev/snd/pcmC$index",
                    cardName = name,
                    address = null,
                    sampleRate = null,
                    bitDepth = null,
                    source = ChannelSource.ALSA,
                    typeEvidence = ChannelTypeEvidence.INFERRED
                )
            )
        }
    }

    private fun parsePCM(content: String, channels: MutableList<AudioChannel>) {
        // 真实内核格式（sound/core/pcm.c，snd_pcm_proc_read）：
        // "%02i-%02i: %s : %s" 后按需追加 " : playback %i" / " : capture %i"。
        // 注意：该文件本身不含采样率/声道数/位深，这些信息只在
        // /proc/asound/cardX/pcmY{p,c}/sub0/hw_params 中且仅当流被打开时可读。
        val pcmRegex = """(\d{1,2})-(\d{1,2}):\s*(.+)""".toRegex()
        pcmRegex.findAll(content).forEach { match ->
            val (card, device, rest) = match.destructured
            val tokens = rest.split(" : ").map { it.trim() }
            val pcmId = tokens.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val name = tokens.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: pcmId
            val directions = tokens.drop(2)
            val hasPlayback = directions.any { it.startsWith("playback") }

            // hw_params 仅在流被打开时含有效数据，属尽力而为的实时快照
            val hw = readHwParams(
                card.toInt(),
                device.toInt(),
                when {
                    hasPlayback -> "p"
                    directions.any { it.startsWith("capture") } -> "c"
                    else -> null
                }
            )
            val devicePath = "/dev/snd/pcmC${card}D${device}"
            val isValid = checkDeviceStatus(card.toInt(), device.toInt())
            channels.add(
                AudioChannel(
                    id = "pcm-$card-$device",
                    name = name,
                    type = inferChannelType("$name $pcmId"),
                    channelConfig = hw?.channelCount?.let { channelCountToConfig(it) }
                        ?: resolveChannelConfig("$pcmId $name"),
                    isActive = false, // 占位：由 Repository 基于 getActivePlaybackConfigurations 覆写
                    isValid = isValid,
                    devicePath = devicePath,
                    cardName = "card$card",
                    address = null,
                    sampleRate = hw?.sampleRate,
                    bitDepth = hw?.bitDepth,
                    source = ChannelSource.ALSA,
                    typeEvidence = ChannelTypeEvidence.INFERRED
                )
            )
        }
    }

    /**
     * 声道数 → 声道配置；无法识别的数量返回 null（保留 UNKNOWN）。
     */
    internal fun channelCountToConfig(count: Int): ChannelConfig? = when (count) {
        1 -> ChannelConfig.MONO
        2 -> ChannelConfig.STEREO
        6 -> ChannelConfig.SURROUND_5_1
        8 -> ChannelConfig.SURROUND_7_1
        else -> null
    }

    /**
     * 设备名/ID 中尽力识别 "Nch" 短格式（如 "2ch"、"6ch"）。
     * /proc/asound/pcm 本身不含声道信息，此处仅对把声道数编进名字的驱动兜底。
     */
    internal fun resolveChannelConfig(text: String): ChannelConfig {
        val count = SHORT_CH_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
        return channelCountToConfig(count ?: 0) ?: ChannelConfig.UNKNOWN
    }

    /**
     * 解析 hw_params 文本（内核 snd_pcm_substream_proc_hw_params_read 输出）：
     * - 流未打开时内容为 "closed" → 返回 null；
     * - "rate: 48000 (48000/1)" → 48000；
     * - "channels: 2" → 2；
     * - "format: S16_LE" → 16（取格式名中的第一段数字，S24_3LE→24、U8→8、F32_LE→32）。
     */
    internal fun parseHwParams(content: String): HwParamsSnapshot? {
        if (content.contains("closed")) return null
        val rate = HW_RATE_REGEX.find(content)?.groupValues?.get(1)?.toIntOrNull()
        val channels = HW_CHANNELS_REGEX.find(content)?.groupValues?.get(1)?.toIntOrNull()
        val format = HW_FORMAT_REGEX.find(content)?.groupValues?.get(1)
        val bitDepth = format?.let { DIGIT_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        if (rate == null && channels == null && bitDepth == null) return null
        return HwParamsSnapshot(sampleRate = rate, channelCount = channels, bitDepth = bitDepth)
    }

    internal data class HwParamsSnapshot(
        val sampleRate: Int?,
        val channelCount: Int?,
        val bitDepth: Int?
    )

    private fun readHwParams(card: Int, device: Int, stream: String?): HwParamsSnapshot? {
        if (stream == null) return null
        return try {
            val hwFile = File("/proc/asound/card$card/pcm$device$stream/sub0/hw_params")
            if (hwFile.exists()) parseHwParams(hwFile.readText()) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查设备状态：通过读取 /proc/asound/cardX/pcmY/subZ/status 判断设备是否有效
     * 如果 status 文件存在且包含 RUNNING/OPEN 等状态，说明设备可用
     */
    private fun checkDeviceStatus(card: Int, device: Int): Boolean {
        try {
            // 尝试读取设备状态文件
            val statusFile = File("/proc/asound/card$card/pcm$device/sub0/status")
            if (statusFile.exists()) {
                val status = statusFile.readText()
                // 检查设备是否处于活跃状态
                return status.contains("RUNNING") || status.contains("OPEN")
            }
            // 如果没有 status 文件，检查 info 文件
            val infoFile = File("/proc/asound/card$card/pcm$device/info")
            if (infoFile.exists()) {
                return infoFile.readText().isNotBlank()
            }
            // 如果 card 目录存在，至少声卡是被识别的
            val cardDir = File("/proc/asound/card$card")
            return cardDir.exists() && cardDir.isDirectory
        } catch (e: Exception) {
            return false
        }
    }

    private fun scanDevSnd(dir: File, channels: MutableList<AudioChannel>) {
        dir.listFiles()?.forEach { file ->
            when {
                file.name.startsWith("pcmC") -> {
                    val parts = file.name.removePrefix("pcmC").split("D")
                    if (parts.size == 2) {
                        val card = parts[0]
                        // 只添加有效且可读的设备
                        val isReadable = file.canRead()
                        if (isReadable && channels.none { it.devicePath == file.absolutePath }) {
                            channels.add(
                                AudioChannel(
                                    id = "dev-${file.name}",
                                    name = file.name,
                                    type = AudioChannelType.UNKNOWN,
                                    channelConfig = ChannelConfig.UNKNOWN,
                                    isActive = false, // 占位：由 Repository 基于 getActivePlaybackConfigurations 覆写
                                    isValid = isReadable,
                                    devicePath = file.absolutePath,
                                    cardName = "card$card",
                                    address = null,
                                    sampleRate = null,
                                    bitDepth = null,
                                    source = ChannelSource.ALSA,
                                    typeEvidence = ChannelTypeEvidence.INFERRED
                                )
                            )
                        }
                    }
                }
                file.name.startsWith("controlC") -> {
                    // 跳过控制设备
                }
            }
        }
    }

    /**
     * 通道名称/ID 关键词识别，规则见 [ChannelTypeRules.inferTypeFromKeywords]
     * （检查顺序：导航 → 语音 → 警报 → 系统 → 媒体，媒体词最泛化故最后）。
     */
    internal fun inferChannelType(name: String): AudioChannelType =
        ChannelTypeRules.inferTypeFromKeywords(name)

    private companion object {
        private const val TAG = "ALSAScanner"
        private val SHORT_CH_REGEX = Regex("""(\d+)\s*ch\b""", RegexOption.IGNORE_CASE)
        private val HW_RATE_REGEX = Regex("""rate:\s*(\d+)""")
        private val HW_CHANNELS_REGEX = Regex("""channels:\s*(\d+)""")
        private val HW_FORMAT_REGEX = Regex("""format:\s*(\S+)""")
        private val DIGIT_REGEX = Regex("""(\d+)""")
    }
}
