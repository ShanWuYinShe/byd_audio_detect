package com.byd.audiochanneldetector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.usecase.GetAudioChannelsUseCase
import com.byd.audiochanneldetector.ui.ShareReportFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    private var loadJob: Job? = null

    init {
        loadChannels()
        startAutoRefresh()
    }

    fun loadChannels() = triggerRefresh(showLoading = true)

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                triggerRefresh(showLoading = false)
            }
        }
    }

    private fun triggerRefresh(showLoading: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

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
    fun buildShareText(): String = ShareReportFormatter.format(_uiState.value.channels)

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

    private companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }
}
