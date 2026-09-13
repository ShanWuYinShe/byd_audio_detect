package com.byd.audiochanneldetector.data.repository

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergeLogicTest {

    private fun channel(
        id: String,
        name: String,
        type: AudioChannelType = AudioChannelType.UNKNOWN,
        source: ChannelSource = ChannelSource.ANDROID,
        isValid: Boolean = true,
        cardName: String? = null,
        devicePath: String? = null,
        address: String? = null
    ) = AudioChannel(
        id = id, name = name, type = type,
        channelConfig = ChannelConfig.UNKNOWN, isActive = false, isValid = isValid,
        devicePath = devicePath, cardName = cardName, address = address,
        sampleRate = null, bitDepth = null, source = source,
        typeEvidence = ChannelTypeEvidence.INFERRED
    )

    @Test
    fun mergeChannels_androidSourcePriorityOnNameClash() {
        val android = channel(id = "android-1", name = "Speaker", source = ChannelSource.ANDROID)
        val alsa = channel(id = "pcm-0-0", name = "Speaker", source = ChannelSource.ALSA)
        val merged = ChannelMergeLogic.mergeChannels(listOf(android), listOf(alsa))
        assertEquals(1, merged.size)
        assertEquals(ChannelSource.ANDROID, merged[0].source)
    }

    @Test
    fun mergeChannels_keepsDisjointNames() {
        val android = channel(id = "android-1", name = "Speaker")
        val alsa = channel(id = "pcm-0-0", name = "HDMI", source = ChannelSource.ALSA)
        val merged = ChannelMergeLogic.mergeChannels(listOf(android), listOf(alsa))
        assertEquals(2, merged.size)
    }

    @Test
    fun mergeChannels_dropsInvalid() {
        val valid = channel(id = "a", name = "A", isValid = true)
        val invalid = channel(id = "b", name = "B", isValid = false)
        val merged = ChannelMergeLogic.mergeChannels(listOf(valid), listOf(invalid))
        assertEquals(1, merged.size)
        assertEquals("a", merged[0].id)
    }

    @Test
    fun mergeChannels_nameClashIsCaseInsensitive() {
        val a = channel(id = "android-1", name = "Speaker")
        val b = channel(id = "pcm-0", name = "speaker", source = ChannelSource.ALSA)
        val merged = ChannelMergeLogic.mergeChannels(listOf(a), listOf(b))
        assertEquals(1, merged.size)
    }

    @Test
    fun mergeChannels_sameNameDifferentAddressBothKept() {
        // 车机多个 BUS 端口 productName 相同，仅地址不同：必须视为不同通道
        val media = channel(id = "android-1", name = "BYD Audio", address = "media_bus")
        val nav = channel(id = "android-2", name = "BYD Audio", address = "nav_bus")
        val merged = ChannelMergeLogic.mergeChannels(listOf(media), listOf(nav))
        assertEquals(2, merged.size)
        assertNotEquals(ChannelMergeLogic.dedupKey(media), ChannelMergeLogic.dedupKey(nav))
    }

    @Test
    fun mergeChannels_sortedByTypeOrdinal() {
        val nav = channel(id = "1", name = "Nav", type = AudioChannelType.NAVIGATION)
        val media = channel(id = "2", name = "Media", type = AudioChannelType.MEDIA)
        val merged = ChannelMergeLogic.mergeChannels(listOf(nav, media), emptyList())
        assertEquals(AudioChannelType.MEDIA, merged[0].type)
        assertEquals(AudioChannelType.NAVIGATION, merged[1].type)
    }

    @Test
    fun dedupKey_normalName() {
        assertEquals("speaker", ChannelMergeLogic.dedupKey(channel("1", "Speaker")))
    }

    @Test
    fun dedupKey_unknownDeviceFallsBackToId() {
        assertEquals("android-1", ChannelMergeLogic.dedupKey(channel("android-1", "Unknown Device")))
    }

    @Test
    fun dedupKey_blankNameFallsBackToId() {
        assertEquals("x", ChannelMergeLogic.dedupKey(channel("x", "   ")))
    }

    @Test
    fun dedupKey_appendsAddress() {
        assertEquals(
            "byd@nav_bus",
            ChannelMergeLogic.dedupKey(channel("1", "BYD", address = "nav_bus"))
        )
    }

    // ---------------- deviceKeys ----------------

    @Test
    fun deviceKeys_androidIdAndName() {
        val keys = ChannelMergeLogic.deviceKeys(channel("android-42", "A"))
        assertTrue("id:42" in keys)
        assertTrue("name:A" in keys)
    }

    @Test
    fun deviceKeys_includesAddress() {
        val keys = ChannelMergeLogic.deviceKeys(channel("android-1", "A", address = "nav_bus"))
        assertTrue("addr:nav_bus" in keys)
    }

    @Test
    fun deviceKeys_includesCardPathIndex() {
        val keys = ChannelMergeLogic.deviceKeys(
            channel("pcm-0", "X", devicePath = "/dev/snd/pcmC0D0p", source = ChannelSource.ALSA)
        )
        assertTrue("addr:card0" in keys)
    }

    // ---------------- resolveIsActive ----------------

    @Test
    fun resolveIsActive_emptyKeysReturnsFalse() {
        assertFalse(ChannelMergeLogic.resolveIsActive(channel("android-1", "A"), emptySet()))
    }

    @Test
    fun resolveIsActive_androidIdMatch() {
        val ch = channel("android-42", "A")
        assertTrue(ChannelMergeLogic.resolveIsActive(ch, setOf("id:42")))
    }

    @Test
    fun resolveIsActive_nameMatch() {
        val ch = channel("pcm-0", "Speaker", source = ChannelSource.ALSA)
        assertTrue(ChannelMergeLogic.resolveIsActive(ch, setOf("name:Speaker")))
    }

    @Test
    fun resolveIsActive_addressMatch() {
        val ch = channel("android-1", "A", address = "nav_bus")
        assertTrue(ChannelMergeLogic.resolveIsActive(ch, setOf("addr:nav_bus")))
    }

    @Test
    fun resolveIsActive_cardNameAddressMatch() {
        val ch = channel("pcm-0", "X", cardName = "card0")
        assertTrue(ChannelMergeLogic.resolveIsActive(ch, setOf("addr:card0")))
    }

    @Test
    fun resolveIsActive_devicePathCardMatch() {
        val ch = channel("pcm-0", "X", devicePath = "/dev/snd/pcmC0D0p", source = ChannelSource.ALSA)
        assertTrue(ChannelMergeLogic.resolveIsActive(ch, setOf("addr:card0")))
    }

    @Test
    fun resolveIsActive_noMatchReturnsFalse() {
        val ch = channel("android-1", "A")
        assertFalse(ChannelMergeLogic.resolveIsActive(ch, setOf("id:99")))
    }
}
