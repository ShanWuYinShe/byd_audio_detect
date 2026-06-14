package com.byd.audiochanneldetector.domain.usecase

import com.byd.audiochanneldetector.data.repository.AudioChannelRepository
import com.byd.audiochanneldetector.domain.model.AudioChannel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAudioChannelsUseCase @Inject constructor(
    private val repository: AudioChannelRepository
) {
    operator fun invoke(): Flow<List<AudioChannel>> {
        return repository.getAudioChannels()
    }
}