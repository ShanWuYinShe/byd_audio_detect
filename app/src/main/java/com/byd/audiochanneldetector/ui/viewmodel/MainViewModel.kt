package com.byd.audiochanneldetector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.usecase.GetAudioChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val channels: List<AudioChannel> = emptyList(),
    val filteredChannels: List<AudioChannel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFilter: AudioChannelType? = null,
    val selectedChannel: AudioChannel? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getAudioChannelsUseCase: GetAudioChannelsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getAudioChannelsUseCase()
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Unknown error")
                    }
                }
                .collect { channels ->
                    _uiState.update { state ->
                        state.copy(
                            channels = channels,
                            filteredChannels = filterChannels(channels, state.selectedFilter),
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun setFilter(type: AudioChannelType?) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = type,
                filteredChannels = filterChannels(state.channels, type)
            )
        }
    }

    /** 选中某个通道以展示详情；传 null 关闭详情。 */
    fun selectChannel(channel: AudioChannel?) {
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    /**
     * 生成可分享的纯文本检测报告，供系统分享面板（Intent.ACTION_SEND）使用，无需任何权限。
     */
    fun buildShareText(): String {
        val channels = _uiState.value.channels
        return buildString {
            appendLine("比亚迪音频通道检测报告")
            appendLine("BYD Audio Channel Detector")
            appendLine()
            appendLine("总通道: ${channels.size}    活跃: ${channels.count { it.isActive }}")
            appendLine(
                "媒体: ${channels.count { it.type == AudioChannelType.MEDIA }}    " +
                    "导航: ${channels.count { it.type == AudioChannelType.NAVIGATION }}"
            )
            appendLine()
            if (channels.isEmpty()) {
                appendLine("（未检测到通道）")
            } else {
                appendLine("----- 通道明细 -----")
                channels.forEachIndexed { index, ch ->
                    appendLine()
                    appendLine("[${index + 1}] ${ch.name}")
                    appendLine(
                        "    类型: ${typeText(ch.type)} | 声道: ${configText(ch.channelConfig)} | " +
                            "来源: ${sourceText(ch.source)}"
                    )
                    appendLine(
                        "    有效: ${if (ch.isValid) "是" else "否"} | " +
                            "活跃: ${if (ch.isActive) "是" else "否"}"
                    )
                    ch.devicePath?.let { appendLine("    设备路径: $it") }
                    ch.cardName?.let { appendLine("    声卡: $it") }
                    ch.sampleRate?.let { appendLine("    采样率: ${it}Hz") }
                    ch.bitDepth?.let { appendLine("    位深: ${it}bit") }
                    appendLine("    ID: ${ch.id}")
                }
            }
        }
    }

    private fun filterChannels(
        channels: List<AudioChannel>,
        filter: AudioChannelType?
    ): List<AudioChannel> {
        return if (filter == null) {
            channels
        } else {
            channels.filter { it.type == filter }
        }
    }

    private fun typeText(type: AudioChannelType): String = when (type) {
        AudioChannelType.MEDIA -> "媒体"
        AudioChannelType.NAVIGATION -> "导航"
        AudioChannelType.VOICE_CALL -> "语音"
        AudioChannelType.SYSTEM -> "系统"
        AudioChannelType.ALARM -> "警报"
        AudioChannelType.UNKNOWN -> "未知"
    }

    private fun configText(config: ChannelConfig): String = when (config) {
        ChannelConfig.MONO -> "单声道"
        ChannelConfig.STEREO -> "立体声"
        ChannelConfig.SURROUND_5_1 -> "5.1环绕"
        ChannelConfig.SURROUND_7_1 -> "7.1环绕"
        ChannelConfig.UNKNOWN -> "未知"
    }

    private fun sourceText(source: ChannelSource): String = when (source) {
        ChannelSource.ANDROID -> "Android(API)"
        ChannelSource.ALSA -> "ALSA(节点)"
    }
}
