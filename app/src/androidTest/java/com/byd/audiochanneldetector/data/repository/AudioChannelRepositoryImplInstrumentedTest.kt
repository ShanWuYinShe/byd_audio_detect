package com.byd.audiochanneldetector.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byd.audiochanneldetector.data.scanner.ALSAScanner
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ChannelTypeEvidence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AudioChannelRepositoryImpl 插桩测试：在真实 Android 系统上验证依赖
 * AudioManager / AudioDeviceInfo / AudioPlaybackConfiguration 的设备枚举、
 * usage 实测判定与跨刷新累积语义——这些逻辑无法在 JVM 单测中覆盖。
 *
 * 实现说明：AudioDeviceInfo 与 AudioPlaybackConfiguration 均为 final 类，
 * 插桩环境（dexmaker 子类化 mock）无法 mock，故采用真实行为测试——
 * 用 AudioTrack 播放指定用途（usage）的静音音频，让
 * getActivePlaybackConfigurations() 返回真实播放配置来驱动被测逻辑。
 */
@RunWith(AndroidJUnit4::class)
class AudioChannelRepositoryImplInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun newRepository() = AudioChannelRepositoryImpl(
        context = context,
        alsaScanner = ALSAScanner(),
        audioManager = audioManager
    )

    private fun scanOnce(repo: AudioChannelRepositoryImpl) = runBlocking {
        repo.getAudioChannels().first()
    }

    /** 播放指定用途的静音音频，返回已开始播放的 AudioTrack（调用方负责 stop/release）。 */
    private fun playSilent(usage: Int, contentType: Int): AudioTrack {
        val sampleRate = 44_100
        val frameBytes = 2 * 2 // 立体声 × 16bit
        val durationSeconds = 12
        val bufferBytes = sampleRate * durationSeconds * frameBytes
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        track.write(ShortArray(bufferBytes / 2), 0, bufferBytes / 2)
        track.play()
        return track
    }

    /** 轮询等待活跃播放配置达到预期状态；模拟器注册/注销播放配置存在延迟，不能即调即断言。 */
    private fun awaitPlaybackActive(expected: Boolean, timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (audioManager.activePlaybackConfigurations.isNotEmpty() == expected) return true
            Thread.sleep(200)
        }
        return audioManager.activePlaybackConfigurations.isNotEmpty() == expected
    }

    @Test
    fun initialScan_androidDevicesInferredAndInactive() = runBlocking {
        val androidChannels = scanOnce(newRepository())
            .filter { it.source == ChannelSource.ANDROID }

        assertTrue("模拟器应至少枚举出一个系统输出设备", androidChannels.isNotEmpty())
        androidChannels.forEach { channel ->
            assertTrue("无实测记录时应为推断判定：$channel", channel.typeEvidence == ChannelTypeEvidence.INFERRED)
            assertFalse("无播放时设备不应标记为活跃：$channel", channel.isActive)
        }
    }

    @Test
    fun navigationPlayback_measuredAsNavigationAndActive() {
        val repo = newRepository()
        val track = playSilent(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.CONTENT_TYPE_SPEECH
        )
        try {
            assertTrue("播放后应观察到活跃播放配置", awaitPlaybackActive(expected = true))

            val measured = scanOnce(repo)
                .filter { it.typeEvidence == ChannelTypeEvidence.MEASURED }
            assertTrue("活跃播放应产生实测判定通道", measured.isNotEmpty())
            val nav = measured.first { it.type == AudioChannelType.NAVIGATION }
            assertTrue("正在播放导航的设备应标记为活跃：$nav", nav.isActive)
        } finally {
            track.stop()
            track.release()
        }
    }

    @Test
    fun playbackStopped_measuredTypeRetainedAndActiveCleared() {
        val repo = newRepository()
        val track = playSilent(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.CONTENT_TYPE_SPEECH
        )
        try {
            awaitPlaybackActive(expected = true)
            scanOnce(repo)
        } finally {
            track.stop()
            track.release()
        }

        assertTrue("停止播放后活跃配置应清空", awaitPlaybackActive(expected = false))
        val measured = scanOnce(repo)
            .filter { it.typeEvidence == ChannelTypeEvidence.MEASURED }
        assertTrue("停止播放后应凭历史实测保持类型判定（跨刷新累积）", measured.isNotEmpty())
        val nav = measured.first { it.type == AudioChannelType.NAVIGATION }
        assertFalse("已停播的设备不应标记为活跃：$nav", nav.isActive)
    }

    @Test
    fun accumulatedNavigationAndMedia_navigationWinsByPriority() {
        val repo = newRepository()

        val navTrack = playSilent(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.CONTENT_TYPE_SPEECH
        )
        try {
            awaitPlaybackActive(expected = true)
            scanOnce(repo)
        } finally {
            navTrack.stop()
            navTrack.release()
        }
        awaitPlaybackActive(expected = false)

        val mediaTrack = playSilent(
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.CONTENT_TYPE_MUSIC
        )
        try {
            awaitPlaybackActive(expected = true)
            scanOnce(repo)
        } finally {
            mediaTrack.stop()
            mediaTrack.release()
        }
        awaitPlaybackActive(expected = false)

        // 同一输出端口先后实测到导航与媒体两种用途，按优先级（导航 > 媒体）判定为导航
        val measured = scanOnce(repo)
            .filter { it.typeEvidence == ChannelTypeEvidence.MEASURED }
        assertTrue(
            "累积导航与媒体用途后应按优先级判定为导航：${measured.map { it.type }}",
            measured.any { it.type == AudioChannelType.NAVIGATION }
        )
    }
}
