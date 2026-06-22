package com.byd.audiochanneldetector.data.repository

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.byd.audiochanneldetector.data.scanner.ALSAScanner
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
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

    override fun getAudioChannels(): Flow<List<AudioChannel>> = flow {
        // 1. 首先获取 Android 系统音频设备
        val androidChannels = getAndroidAudioDevices()

        // 2. 获取 ALSA 设备
        val alsaChannels = alsaScanner.scanChannels()

        // 3. 获取当前活跃播放配置，构造活跃通道标识集合
        val activeKeys = collectActivePlaybackKeys()

        // 4. 合并并去重，最后基于活跃集合覆写 isActive
        val mergedChannels = ChannelMergeLogic.mergeChannels(androidChannels, alsaChannels)
            .map { it.copy(isActive = ChannelMergeLogic.resolveIsActive(it, activeKeys)) }

        emit(mergedChannels)
    }

    private fun getAndroidAudioDevices(): List<AudioChannel> {
        // 同时枚举输出与输入设备（GET_DEVICES_ALL 在 lint 的 @IntDef 检查下不被接受，
        // 改为显式按位或，运行时等价）
        val devices = audioManager.getDevices(
            AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS
        )

        return devices.map { device ->
            AudioChannel(
                id = "android-${device.id}",
                name = device.productName?.toString() ?: "Unknown Device",
                type = inferAndroidChannelType(device),
                channelConfig = inferChannelConfig(device),
                isActive = false,
                isValid = true,
                devicePath = null,
                cardName = null,
                sampleRate = device.sampleRates.firstOrNull(),
                bitDepth = null,
                source = ChannelSource.ANDROID
            )
        }
    }

    /**
     * 基于 AudioDeviceInfo.type 推断通道类型。覆盖比亚迪车机常见的设备类别：
     * 输出（扬声器/耳机/HDMI/蓝牙 A2DP/BLE/USB/AUX/LINE/DOCK/FM/IP/SUBMIX）→ MEDIA
     * 输入（内置 MIC/SCO/BLE_HEADSET）→ VOICE_CALL
     * TELEPHONY → VOICE_CALL；BUS（车机内部总线）→ SYSTEM
     */
    private fun inferAndroidChannelType(device: AudioDeviceInfo): AudioChannelType {
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

            // 语音输入
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioChannelType.VOICE_CALL

            // 通话
            AudioDeviceInfo.TYPE_TELEPHONY -> AudioChannelType.VOICE_CALL

            // 车机总线
            AudioDeviceInfo.TYPE_BUS -> AudioChannelType.SYSTEM

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
     * 收集当前正在播放的音频流所在设备的可比对标识。
     * 使用 address / id / productName 三种 key 形成集合，兼容不同 OEM 的 AudioDeviceInfo 表现差异。
     *
     * API 等级约束：
     * - getActivePlaybackConfigurations() 自 API 26 起可用；
     * - AudioPlaybackConfiguration.audioDeviceInfo 自 API 31 起可用（公开 API；API 36+ 被标 deprecated
     *   但仍存在；该 API 的未来公开替代 audioDevices() 当前为 @SystemApi，普通 App 不可用）；
     * - AudioDeviceInfo.address 自 API 28 起可用。
     * 故仅 API 31+ 才能拿到"哪台设备在播放"的信息；低于 31 的版本返回空集合，
     * isActive 将回到 false（UI 诚实呈现"未检测到活跃通道"，不崩溃）。
     */
    private fun collectActivePlaybackKeys(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return emptySet()
        }
        val configs = runCatching { audioManager.activePlaybackConfigurations }
            .getOrDefault(emptyList())
        val keys = mutableSetOf<String>()
        configs.forEach { config ->
            @Suppress("DEPRECATION") // audioDeviceInfo 公开 API 持续可用；公开替代 audioDevices 当前为 @SystemApi
            val device = config.audioDeviceInfo ?: return@forEach
            keys += "id:${device.id}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                device.address.takeIf { it.isNotBlank() }?.let { keys += "addr:$it" }
            }
            device.productName?.toString()?.takeIf { it.isNotBlank() }?.let { keys += "name:$it" }
        }
        return keys
    }
}
