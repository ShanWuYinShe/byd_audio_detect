package com.byd.audiochanneldetector.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import androidx.annotation.RequiresApi
import com.byd.audiochanneldetector.data.ChannelTypeRules
import com.byd.audiochanneldetector.data.scanner.ALSAScanner
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioChannelRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alsaScanner: ALSAScanner,
    private val audioManager: AudioManager
) : AudioChannelRepository {

    /**
     * 跨刷新累积的"设备 key → 播放用途"实测记录（进程内存级，不落盘）。
     * 每次扫描把当前活跃播放的用途并入；设备停下后类型判定仍可依据历史实测结果，
     * 使"先播导航再播音乐"的两步实测能各自沉淀到对应端口上。
     */
    private val accumulatedUsages = mutableMapOf<String, MutableSet<Int>>()

    override fun getAudioChannels(): Flow<List<AudioChannel>> = flow {
        // 1. Android 系统音频输出设备（输入设备与"给第三方盒子提供输出通道"的目标无关，不枚举）
        val androidChannels = getAndroidOutputDevices()

        // 2. ALSA 辅助源
        val alsaChannels = alsaScanner.scanChannels()

        // 3. 当前活跃播放快照：活跃 key 集合 + 设备→用途实测（API 31+），并入累积记录
        val snapshot = collectPlaybackSnapshot()
        snapshot.usagesByDeviceKey.forEach { (key, usages) ->
            accumulatedUsages.getOrPut(key) { mutableSetOf() }.addAll(usages)
        }

        // 4. 合并去重 → 类型判定（实测 usage 优先，否则保留构造期推断） → 活跃判定
        val mergedChannels = ChannelMergeLogic.mergeChannels(androidChannels, alsaChannels)
            .map { channel ->
                val measured = ChannelTypeRules.typeFromUsages(usagesFor(channel))
                val typed = if (measured != null) {
                    channel.copy(type = measured, typeEvidence = ChannelTypeEvidence.MEASURED)
                } else {
                    channel.copy(typeEvidence = ChannelTypeEvidence.INFERRED)
                }
                typed.copy(isActive = ChannelMergeLogic.resolveIsActive(typed, snapshot.activeKeys))
            }

        emit(mergedChannels)
    }

    private fun usagesFor(channel: AudioChannel): Set<Int> =
        ChannelMergeLogic.deviceKeys(channel)
            .flatMap { accumulatedUsages[it] ?: emptySet() }
            .toSet()

    private fun getAndroidOutputDevices(): List<AudioChannel> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        return devices.map { device ->
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                device.address.takeIf { it.isNotBlank() }
            } else {
                null
            }
            AudioChannel(
                id = "android-${device.id}",
                name = device.productName?.toString() ?: "Unknown Device",
                type = inferAndroidChannelType(device, address),
                channelConfig = inferChannelConfig(device),
                isActive = false,
                isValid = true,
                devicePath = null,
                cardName = null,
                address = address,
                sampleRate = device.sampleRates.firstOrNull(),
                bitDepth = null,
                source = ChannelSource.ANDROID,
                typeEvidence = ChannelTypeEvidence.INFERRED
            )
        }
    }

    /**
     * Android 源类型推断：优先名称/地址关键词（BUS 端口 tag 常含 nav/media 语义），
     * 未命中再按 AudioDeviceInfo.type 推断。覆盖比亚迪车机常见的设备类别：
     * 输出（扬声器/耳机/HDMI/蓝牙 A2DP/BLE/USB/AUX/LINE/DOCK/FM/IP/SUBMIX）→ MEDIA，
     * SCO/TELEPHONY → VOICE_CALL。
     *
     * TYPE_BUS 是车机多路输出的主要形态（媒体/导航各占一个 BUS 端口），仅凭设备类型
     * 无法区分内容用途，统一标注反而误导——故不再映射为 SYSTEM，诚实回落 UNKNOWN，
     * 留给关键词推断与运行期 usage 实测判定。
     */
    private fun inferAndroidChannelType(device: AudioDeviceInfo, address: String?): AudioChannelType {
        ChannelTypeRules.inferTypeFromKeywords(device.productName?.toString(), address)
            .takeIf { it != AudioChannelType.UNKNOWN }
            ?.let { return it }

        return when (device.type) {
            // 媒体输出
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_FM,
            AudioDeviceInfo.TYPE_IP,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> AudioChannelType.MEDIA

            // 语音（SCO 可作为输出出现）
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioChannelType.VOICE_CALL

            // 通话
            AudioDeviceInfo.TYPE_TELEPHONY -> AudioChannelType.VOICE_CALL

            else -> AudioChannelType.UNKNOWN
        }
    }

    private fun inferChannelConfig(device: AudioDeviceInfo): ChannelConfig {
        // channelMasks 是设备支持的声道配置掩码数组，每个掩码中置位的 bit 数即为该配置的声道数
        // （本机 android-36 实测：CHANNEL_OUT_MONO=4→1 声道、STEREO=12→2、5POINT1=252→6、7POINT1=1020→8）。
        // 故用 Integer.bitCount 求声道数；旧实现误用 masks.size（仅表示"支持几种配置"）导致声道判断错误。
        val masks = device.channelMasks
        if (masks.isEmpty()) return ChannelConfig.UNKNOWN
        val maxChannelCount = masks.maxOf { Integer.bitCount(it) }
        return when {
            maxChannelCount >= 8 -> ChannelConfig.SURROUND_7_1
            maxChannelCount >= 6 -> ChannelConfig.SURROUND_5_1
            maxChannelCount >= 2 -> ChannelConfig.STEREO
            maxChannelCount >= 1 -> ChannelConfig.MONO
            else -> ChannelConfig.UNKNOWN
        }
    }

    /**
     * 收集当前正在播放的音频快照：设备可比对标识（活跃判定用）与"设备 key → 用途"映射
     * （类型实测判定用）。
     *
     * API 等级约束：
     * - getActivePlaybackConfigurations() 自 API 26 起可用；
     * - AudioPlaybackConfiguration.getAudioAttributes() 自 API 26 起可用（公开 API）；
     * - API 33+：AudioManager.getAudioDevicesForAttributes() 公开查询指定用途的路由设备，
     *   返回值与 getDevices() 枚举同源（type+address 对应），是建立"哪台设备在播放什么
     *   用途"映射的可靠途径；
     * - API 31-32：AudioPlaybackConfiguration.audioDeviceInfo 虽为公开 API，但系统侧从未
     *   填充该信息（API 36 起文档标注 deprecated "never populated"），普通 App 恒为 null，
     *   映射通常建立不起来，isActive 诚实回落 false、类型保持推断结果（不崩溃）；
     * - AudioDeviceInfo.address 自 API 28 起可用。
     */
    private fun collectPlaybackSnapshot(): PlaybackSnapshot {
        val configs = runCatching { audioManager.activePlaybackConfigurations }
            .getOrDefault(emptyList())
        val keys = mutableSetOf<String>()
        val usagesByDeviceKey = mutableMapOf<String, MutableSet<Int>>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                addUsagesByRouting(configs, keys, usagesByDeviceKey)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                addUsagesByPlaybackDevice(configs, keys, usagesByDeviceKey)
        }
        return PlaybackSnapshot(keys, usagesByDeviceKey)
    }

    /** API 33+：对每个活跃用途查询其路由设备（与枚举设备同源），建立设备 key → 用途映射。 */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun addUsagesByRouting(
        configs: List<AudioPlaybackConfiguration>,
        keys: MutableSet<String>,
        usagesByDeviceKey: MutableMap<String, MutableSet<Int>>
    ) {
        val activeUsages = configs.mapNotNull { config ->
            runCatching { config.audioAttributes.usage }.getOrNull()
        }.toSet()
        activeUsages.forEach { usage ->
            val attributes = AudioAttributes.Builder().setUsage(usage).build()
            val routedDevices = runCatching { audioManager.getAudioDevicesForAttributes(attributes) }
                .getOrDefault(emptyList())
            routedDevices.forEach { device ->
                val deviceKeys = deviceKeysOf(device)
                keys += deviceKeys
                deviceKeys.forEach { key ->
                    usagesByDeviceKey.getOrPut(key) { mutableSetOf() }.add(usage)
                }
            }
        }
    }

    /** API 31-32：依赖 audioDeviceInfo 关联播放设备（系统多数未填充，尽力而为）。 */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addUsagesByPlaybackDevice(
        configs: List<AudioPlaybackConfiguration>,
        keys: MutableSet<String>,
        usagesByDeviceKey: MutableMap<String, MutableSet<Int>>
    ) {
        configs.forEach { config ->
            val usage = runCatching { config.audioAttributes.usage }.getOrNull() ?: return@forEach
            @Suppress("DEPRECATION") // audioDeviceInfo 公开 API 持续可用；API 33+ 已由路由查询替代
            val device = config.audioDeviceInfo ?: return@forEach
            val deviceKeys = deviceKeysOf(device)
            keys += deviceKeys
            deviceKeys.forEach { key ->
                usagesByDeviceKey.getOrPut(key) { mutableSetOf() }.add(usage)
            }
        }
    }

    private fun deviceKeysOf(device: AudioDeviceInfo): Set<String> {
        val deviceKeys = mutableSetOf("id:${device.id}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.address.takeIf { it.isNotBlank() }?.let { deviceKeys += "addr:$it" }
        }
        device.productName?.toString()?.takeIf { it.isNotBlank() }?.let { deviceKeys += "name:$it" }
        return deviceKeys
    }

    private data class PlaybackSnapshot(
        val activeKeys: Set<String>,
        val usagesByDeviceKey: Map<String, Set<Int>>
    )
}
