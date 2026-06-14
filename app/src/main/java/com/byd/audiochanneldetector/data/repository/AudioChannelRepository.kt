package com.byd.audiochanneldetector.data.repository

import com.byd.audiochanneldetector.domain.model.AudioChannel
import kotlinx.coroutines.flow.Flow

interface AudioChannelRepository {
    fun getAudioChannels(): Flow<List<AudioChannel>>
}