package com.byd.audiochanneldetector.ui.viewmodel

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainUiLogic 纯函数单测：过滤行为与详情弹层跟随刷新（按 id 重查/消失关闭）。
 */
class MainUiLogicTest {

    private fun channel(
        id: String,
        name: String,
        type: AudioChannelType = AudioChannelType.UNKNOWN
    ) = AudioChannel(
        id = id, name = name, type = type,
        channelConfig = ChannelConfig.UNKNOWN, isActive = false, isValid = true,
        devicePath = null, cardName = null, address = null,
        sampleRate = null, bitDepth = null, source = ChannelSource.ANDROID,
        typeEvidence = ChannelTypeEvidence.INFERRED
    )

    private val channels = listOf(
        channel("a", "Media Bus", AudioChannelType.MEDIA),
        channel("b", "Nav Bus", AudioChannelType.NAVIGATION),
        channel("c", "Voice", AudioChannelType.VOICE_CALL)
    )

    // ---------------- filterChannels ----------------

    @Test
    fun filterChannels_nullFilterReturnsAll() {
        assertEquals(channels, MainUiLogic.filterChannels(channels, null))
    }

    @Test
    fun filterChannels_filtersByType() {
        val filtered = MainUiLogic.filterChannels(channels, AudioChannelType.NAVIGATION)
        assertEquals(listOf("b"), filtered.map { it.id })
    }

    @Test
    fun filterChannels_noMatchReturnsEmpty() {
        assertTrue(MainUiLogic.filterChannels(channels, AudioChannelType.ALARM).isEmpty())
    }

    @Test
    fun filterChannels_emptyChannelsSafe() {
        assertTrue(MainUiLogic.filterChannels(emptyList(), AudioChannelType.MEDIA).isEmpty())
        assertEquals(emptyList<AudioChannel>(), MainUiLogic.filterChannels(emptyList(), null))
    }

    // ---------------- resolveSelectedChannel ----------------

    @Test
    fun resolveSelectedChannel_nullSelectedReturnsNull() {
        assertNull(MainUiLogic.resolveSelectedChannel(null, channels))
    }

    @Test
    fun resolveSelectedChannel_updatesToLatestInstance() {
        val stale = channel("b", "Nav Bus", AudioChannelType.NAVIGATION).copy(isActive = true)
        val resolved = MainUiLogic.resolveSelectedChannel(stale, channels)
        // 返回的是最新列表中的实例，而非过期引用
        assertSame(channels[1], resolved)
        assertEquals(false, resolved?.isActive)
    }

    @Test
    fun resolveSelectedChannel_disappearedChannelReturnsNull() {
        val removed = channel("gone", "USB DAC")
        assertNull(MainUiLogic.resolveSelectedChannel(removed, channels))
    }

    // ---------------- isWideScreen ----------------

    @Test
    fun isWideScreen_belowThresholdReturnsFalse() {
        assertEquals(false, MainUiLogic.isWideScreen(360))
        assertEquals(false, MainUiLogic.isWideScreen(719))
    }

    @Test
    fun isWideScreen_atOrAboveThresholdReturnsTrue() {
        assertEquals(true, MainUiLogic.isWideScreen(720))
        assertEquals(true, MainUiLogic.isWideScreen(1080))
        assertEquals(true, MainUiLogic.isWideScreen(1920))
    }
}
