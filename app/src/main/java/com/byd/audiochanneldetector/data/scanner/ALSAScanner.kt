package com.byd.audiochanneldetector.data.scanner

import android.util.Log
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
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
        val cardRegex = """(\d+)\s+\[(\S+)\s+]\s+:\s+(.+)""".toRegex()
        cardRegex.findAll(content).forEach { match ->
            val (index, id, name) = match.destructured
            // 检查声卡目录是否存在且可读
            val cardDir = File("/proc/asound/card$index")
            val isValid = cardDir.exists() && cardDir.canRead()
            channels.add(
                AudioChannel(
                    id = "card-$index",
                    name = name.ifEmpty { "Card $index" },
                    type = inferChannelType(name),
                    channelConfig = ChannelConfig.UNKNOWN,
                    isActive = false, // 占位：由 Repository 基于 getActivePlaybackConfigurations 覆写
                    isValid = isValid,
                    devicePath = "/dev/snd/pcmC$index",
                    cardName = id,
                    sampleRate = null,
                    bitDepth = null
                )
            )
        }
    }

    private fun parsePCM(content: String, channels: MutableList<AudioChannel>) {
        val pcmRegex = """(\d+)-(\d+)\s+\[(\S+)\]:\s+(.+)""".toRegex()
        pcmRegex.findAll(content).forEach { match ->
            val (card, device, name, details) = match.destructured
            val channelConfig = resolveChannelConfig(details, name)
            // 设备路径用于验证有效性
            val devicePath = "/dev/snd/pcmC${card}D${device}"
            val isValid = checkDeviceStatus(card.toInt(), device.toInt())
            channels.add(
                AudioChannel(
                    id = "pcm-$card-$device",
                    name = name,
                    type = inferChannelType(name),
                    channelConfig = channelConfig,
                    isActive = false, // 占位：由 Repository 基于 getActivePlaybackConfigurations 覆写
                    isValid = isValid,
                    devicePath = devicePath,
                    cardName = name,
                    sampleRate = extractSampleRate(details),
                    bitDepth = extractBitDepth(details)
                )
            )
        }
    }

    /**
     * SPEC §5.3：先识别 /proc/asound/pcm details 中的 "N channels" 长格式（精度更高），
     * 未命中再回退到设备名/详情中的 "Nch" 短格式（如 "2ch"、"6ch"）。
     */
    private fun resolveChannelConfig(details: String, name: String): ChannelConfig {
        // 长格式优先
        when {
            details.contains("6 channels") -> return ChannelConfig.SURROUND_5_1
            details.contains("8 channels") -> return ChannelConfig.SURROUND_7_1
            details.contains("2 channels") -> return ChannelConfig.STEREO
            details.contains("1 channel") -> return ChannelConfig.MONO
        }
        // 短格式回退（在 details + name 联合文本上匹配第一个 Nch）
        val shortMatch = SHORT_CH_REGEX.find("$details $name")
        val count = shortMatch?.groupValues?.get(1)?.toIntOrNull()
        return when (count) {
            1 -> ChannelConfig.MONO
            2 -> ChannelConfig.STEREO
            6 -> ChannelConfig.SURROUND_5_1
            8 -> ChannelConfig.SURROUND_7_1
            else -> ChannelConfig.UNKNOWN
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
                        val device = parts[1].takeWhile { it.isDigit() }
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
                                    sampleRate = null,
                                    bitDepth = null
                                )
                            )
                        }
                        // device 编号目前未参与字段构造，仅做命名约定占位
                        @Suppress("UNUSED_VARIABLE")
                        val unused = device
                    }
                }
                file.name.startsWith("controlC") -> {
                    // 跳过控制设备
                }
            }
        }
    }

    /**
     * SPEC §5.2 通道名称关键词识别表（严格对齐，不扩展）：
     * - MEDIA       : media / music / audio / playback
     * - NAVIGATION  : nav / navigation / gps
     * - VOICE_CALL  : voice / call / phone / voip
     * - SYSTEM      : system / ui / notification
     * - ALARM       : alarm / ringtone / alert
     */
    private fun inferChannelType(name: String): AudioChannelType {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("media") || lowerName.contains("music") ||
                lowerName.contains("audio") || lowerName.contains("playback") ->
                AudioChannelType.MEDIA

            lowerName.contains("nav") || lowerName.contains("navigation") ||
                lowerName.contains("gps") ->
                AudioChannelType.NAVIGATION

            lowerName.contains("voice") || lowerName.contains("call") ||
                lowerName.contains("phone") || lowerName.contains("voip") ->
                AudioChannelType.VOICE_CALL

            lowerName.contains("system") || lowerName.contains("ui") ||
                lowerName.contains("notification") ->
                AudioChannelType.SYSTEM

            lowerName.contains("alarm") || lowerName.contains("ringtone") ||
                lowerName.contains("alert") ->
                AudioChannelType.ALARM

            else -> AudioChannelType.UNKNOWN
        }
    }

    private fun extractSampleRate(details: String): Int? {
        val rateRegex = """(\d+)\s*kHz""".toRegex()
        return rateRegex.find(details)?.groupValues?.get(1)?.toIntOrNull()?.times(1000)
    }

    private fun extractBitDepth(details: String): Int? {
        val bitRegex = """(\d+)\s*bit""".toRegex()
        return bitRegex.find(details)?.groupValues?.get(1)?.toIntOrNull()
    }

    private companion object {
        private const val TAG = "ALSAScanner"
        private val SHORT_CH_REGEX = Regex("""(\d+)\s*ch\b""", RegexOption.IGNORE_CASE)
    }
}
