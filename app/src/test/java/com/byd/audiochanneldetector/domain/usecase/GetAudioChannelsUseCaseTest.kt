package com.byd.audiochanneldetector.domain.usecase

import com.byd.audiochanneldetector.data.repository.AudioChannelRepository
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GetAudioChannelsUseCase 单元测试：对仓库 Flow 的透传行为（正常数据 / 空列表 / 多次发射按序）。
 */
class GetAudioChannelsUseCaseTest {

    private fun channel(id: String, name: String) = AudioChannel(
        id = id, name = name, type = AudioChannelType.UNKNOWN,
        channelConfig = ChannelConfig.UNKNOWN, isActive = false, isValid = true,
        devicePath = null, cardName = null, address = null,
        sampleRate = null, bitDepth = null, source = ChannelSource.ANDROID,
        typeEvidence = ChannelTypeEvidence.INFERRED
    )

    /** 直接返回预置 Flow 的仓库 fake（无 mockito 依赖，与现有纯函数测试风格一致） */
    private class FakeRepository(
        private val flow: Flow<List<AudioChannel>>
    ) : AudioChannelRepository {
        override fun getAudioChannels(): Flow<List<AudioChannel>> = flow
    }

    @Test
    fun invoke_returnsRepositoryData() {
        val channels = listOf(channel("a", "Speaker"), channel("b", "Nav Bus"))
        val useCase = GetAudioChannelsUseCase(FakeRepository(flowOf(channels)))
        runBlocking {
            assertEquals(channels, useCase().first())
        }
    }

    @Test
    fun invoke_emptyListPassthrough() {
        val useCase = GetAudioChannelsUseCase(FakeRepository(flowOf(emptyList())))
        runBlocking {
            assertEquals(emptyList<AudioChannel>(), useCase().first())
        }
    }

    @Test
    fun invoke_multipleEmissionsInOrder() {
        // Flow 是多次发射的冷流，UseCase 必须原样透传而不是只取首个值
        val first = listOf(channel("a", "Speaker"))
        val second = emptyList<AudioChannel>()
        val third = listOf(channel("a", "Speaker"), channel("b", "Nav Bus"))
        val useCase = GetAudioChannelsUseCase(
            FakeRepository(flow { emit(first); emit(second); emit(third) })
        )
        runBlocking {
            assertEquals(listOf(first, second, third), useCase().toList())
        }
    }
}
