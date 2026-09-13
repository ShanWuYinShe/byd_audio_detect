package com.byd.audiochanneldetector.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.usecase.GetAudioChannelsUseCase
import com.byd.audiochanneldetector.ui.ShareReportFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val getAudioChannelsUseCase: GetAudioChannelsUseCase,
    @ApplicationContext context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var loadJob: Job? = null
    private var playbackEventJob: Job? = null

    /**
     * 播放状态变化（开始/停止导航播报、放音乐等）即时触发扫描。
     * 导航提示音通常只有几秒，30s 轮询会整段错过；此回调让"实测通道判定"
     * 能在播报瞬间完成。带防抖：一次播报常伴随多次回调，避免重复扫描。
     */
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            schedulePlaybackRefresh()
        }
    }

    init {
        loadChannels()
        startAutoRefresh()
        audioManager.registerAudioPlaybackCallback(
            playbackCallback,
            Handler(Looper.getMainLooper())
        )
    }

    override fun onCleared() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onCleared()
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

    private fun schedulePlaybackRefresh() {
        playbackEventJob?.cancel()
        playbackEventJob = viewModelScope.launch {
            delay(PLAYBACK_DEBOUNCE_MS)
            triggerRefresh(showLoading = false)
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
        private const val PLAYBACK_DEBOUNCE_MS = 400L
    }
}
