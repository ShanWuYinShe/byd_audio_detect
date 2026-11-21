package com.byd.audiochanneldetector.data.repository

import com.byd.audiochanneldetector.domain.model.AudioEvent
import com.byd.audiochanneldetector.domain.model.AudioEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEventRepository @Inject constructor() {

    private val _events = MutableStateFlow<List<AudioEvent>>(emptyList())
    val events: StateFlow<List<AudioEvent>> = _events.asStateFlow()

    @Synchronized
    fun log(type: AudioEventType, message: String): AudioEvent {
        val event = AudioEvent(type = type, message = message)
        val current = _events.value
        val updated = if (current.size >= MAX_CAPACITY) {
            current.drop(current.size - MAX_CAPACITY + 1) + event
        } else {
            current + event
        }
        _events.value = updated
        return event
    }

    fun clear() {
        _events.value = emptyList()
    }

    companion object {
        const val MAX_CAPACITY = 50
    }
}
