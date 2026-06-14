package com.byd.audiochanneldetector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
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
    val selectedFilter: AudioChannelType? = null
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
}