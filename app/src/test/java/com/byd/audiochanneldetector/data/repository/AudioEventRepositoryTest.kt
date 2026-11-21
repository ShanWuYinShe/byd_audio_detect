package com.byd.audiochanneldetector.data.repository

import com.byd.audiochanneldetector.domain.model.AudioEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioEventRepositoryTest {

    private lateinit var repository: AudioEventRepository

    @Before
    fun setUp() {
        repository = AudioEventRepository()
    }

    @Test
    fun initialEvents_isEmpty() {
        assertTrue(repository.events.value.isEmpty())
    }

    @Test
    fun log_addsEventInOrder() {
        repository.log(AudioEventType.DEVICE_CONNECTED, "Device A connected")
        repository.log(AudioEventType.PLAYBACK_CHANGE, "Playback active")

        val list = repository.events.value
        assertEquals(2, list.size)
        assertEquals(AudioEventType.DEVICE_CONNECTED, list[0].type)
        assertEquals("Device A connected", list[0].message)
        assertEquals(AudioEventType.PLAYBACK_CHANGE, list[1].type)
        assertEquals("Playback active", list[1].message)
    }

    @Test
    fun log_exceedingMaxCapacity_dropsOldestEvents() {
        for (i in 1..60) {
            repository.log(AudioEventType.DETECTION_REFRESH, "Refresh #$i")
        }

        val list = repository.events.value
        assertEquals(AudioEventRepository.MAX_CAPACITY, list.size)
        // 最旧的 10 条已被丢弃，第一条应为 #11
        assertEquals("Refresh #11", list.first().message)
        assertEquals("Refresh #60", list.last().message)
    }

    @Test
    fun clear_resetsEventsToEmpty() {
        repository.log(AudioEventType.SOUND_TEST, "Testing tone")
        assertEquals(1, repository.events.value.size)

        repository.clear()
        assertTrue(repository.events.value.isEmpty())
    }
}
