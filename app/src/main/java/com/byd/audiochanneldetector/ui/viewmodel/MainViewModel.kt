package com.byd.audiochanneldetector.ui.viewmodel

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.audiochanneldetector.data.audio.ChannelSoundPlayer
import com.byd.audiochanneldetector.data.repository.AudioEventRepository
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.AudioEvent
import com.byd.audiochanneldetector.domain.model.AudioEventType
import com.byd.audiochanneldetector.domain.model.ToneMode
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
    val selectedChannel: AudioChannel? = null,
    val isPlayingTone: Boolean = false,
    val playingToneChannelId: String? = null,
    val events: List<AudioEvent> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getAudioChannelsUseCase: GetAudioChannelsUseCase,
    private val channelSoundPlayer: ChannelSoundPlayer,
    private val audioEventRepository: AudioEventRepository,
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
            val count = configs.size
            audioEventRepository.log(
                AudioEventType.PLAYBACK_CHANGE,
                "播放状态变化: 当前 $count 路音频流活跃"
            )
            schedulePlaybackRefresh()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val count = addedDevices?.size ?: 0
            val names = addedDevices?.mapNotNull { it.productName?.toString() }?.joinToString(", ") ?: ""
            audioEventRepository.log(
                AudioEventType.DEVICE_CONNECTED,
                "音频设备接入 ($count 台): $names"
            )
            triggerRefresh(showLoading = false)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val count = removedDevices?.size ?: 0
            val names = removedDevices?.mapNotNull { it.productName?.toString() }?.joinToString(", ") ?: ""
            audioEventRepository.log(
                AudioEventType.DEVICE_DISCONNECTED,
                "音频设备断开 ($count 台): $names"
            )
            triggerRefresh(showLoading = false)
        }
    }

    init {
        loadChannels()
        startAutoRefresh()
        observeTonePlayback()
        observeAudioEvents()
        val handler = Handler(Looper.getMainLooper())
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
    }

    private fun observeTonePlayback() {
        viewModelScope.launch {
            channelSoundPlayer.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlayingTone = playing) }
            }
        }
        viewModelScope.launch {
            channelSoundPlayer.playingChannelId.collect { id ->
                _uiState.update { it.copy(playingToneChannelId = id) }
            }
        }
    }

    private fun observeAudioEvents() {
        viewModelScope.launch {
            audioEventRepository.events.collect { eventList ->
                _uiState.update { it.copy(events = eventList) }
            }
        }
    }

    override fun onCleared() {
        channelSoundPlayer.stop()
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
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
                    audioEventRepository.log(
                        AudioEventType.DETECTION_REFRESH,
                        "通道刷新完成: 共 ${channels.size} 个通道"
                    )
                    _uiState.update { state ->
                        state.copy(
                            channels = channels,
                            filteredChannels = MainUiLogic.filterChannels(channels, state.selectedFilter),
                            isLoading = false,
                            // 详情弹层跟随刷新更新到最新数据（活跃/实测状态会变）；
                            // 通道已从列表消失则置 null 关闭弹层，避免持续展示过期设备
                            selectedChannel = MainUiLogic.resolveSelectedChannel(state.selectedChannel, channels)
                        )
                    }
                }
        }
    }

    fun setFilter(type: AudioChannelType?) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = type,
                filteredChannels = MainUiLogic.filterChannels(state.channels, type)
            )
        }
    }

    /** 选中某个通道以展示详情；传 null 关闭详情。 */
    fun selectChannel(channel: AudioChannel?) {
        if (channel == null) {
            channelSoundPlayer.stop()
        }
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    /** 向指定通道播放测试音频 */
    fun playTone(channel: AudioChannel, mode: ToneMode = ToneMode.STEREO) {
        audioEventRepository.log(
            AudioEventType.SOUND_TEST,
            "对通道 [${channel.name}] 发起试音 (${mode.name})"
        )
        channelSoundPlayer.play(channel, mode)
    }

    /** 停止测试音频播放 */
    fun stopTone() {
        channelSoundPlayer.stop()
    }

    /** 清空当前记录的事件日志 */
    fun clearEvents() {
        audioEventRepository.clear()
    }

    /**
     * 生成可分享的纯文本检测报告，供系统分享面板（Intent.ACTION_SEND）使用，无需任何权限。
     */
    fun buildShareText(): String = ShareReportFormatter.format(
        _uiState.value.channels,
        _uiState.value.events
    )

    /**
     * 生成格式化 JSON 配置报告，供第三方盒子与自动化脚本解析。
     */
    fun buildShareJson(): String = ShareReportFormatter.formatJson(
        _uiState.value.channels,
        _uiState.value.events
    )

    private companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 30_000L
        private const val PLAYBACK_DEBOUNCE_MS = 400L
    }
}
