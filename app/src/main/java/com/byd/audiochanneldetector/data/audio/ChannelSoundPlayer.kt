package com.byd.audiochanneldetector.data.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ToneMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface ChannelSoundPlayer {
    val isPlaying: StateFlow<Boolean>
    val playingChannelId: StateFlow<String?>

    fun play(channel: AudioChannel, mode: ToneMode = ToneMode.STEREO): Boolean
    fun stop()
}

@Singleton
class ChannelSoundPlayerImpl @Inject constructor(
    private val audioManager: AudioManager
) : ChannelSoundPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentTrack: AudioTrack? = null
    private var autoStopJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingChannelId = MutableStateFlow<String?>(null)
    override val playingChannelId: StateFlow<String?> = _playingChannelId.asStateFlow()

    @Synchronized
    override fun play(channel: AudioChannel, mode: ToneMode): Boolean {
        stop()

        if (channel.source != ChannelSource.ANDROID) {
            return false
        }

        val deviceId = channel.id.removePrefix("android-").toIntOrNull()
        val targetDevice = deviceId?.let { id ->
            runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
            }.getOrNull()
        }

        return runCatching {
            val pcmData = ToneGenerator.generateStereoTone(mode = mode)
            if (pcmData.isEmpty()) return false

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(ToneGenerator.DEFAULT_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcmData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (targetDevice != null) {
                track.setPreferredDevice(targetDevice)
            }

            val written = track.write(pcmData, 0, pcmData.size)
            if (written <= 0) {
                track.release()
                return false
            }

            track.play()
            currentTrack = track
            _isPlaying.value = true
            _playingChannelId.value = channel.id

            autoStopJob = scope.launch {
                delay(ToneGenerator.DEFAULT_DURATION_MS.toLong() + 200L)
                stop()
            }
            true
        }.getOrElse {
            stop()
            false
        }
    }

    @Synchronized
    override fun stop() {
        autoStopJob?.cancel()
        autoStopJob = null

        currentTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        }
        currentTrack = null
        _isPlaying.value = false
        _playingChannelId.value = null
    }
}
