package com.byd.audiochanneldetector.data.audio

import com.byd.audiochanneldetector.domain.model.ToneMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * 纯函数正弦波 PCM 音频生成器。
 * 用于通道试音（无需依赖外部音频资源或文件系统）。
 */
object ToneGenerator {
    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_DURATION_MS = 1000
    const val LEFT_FREQ = 440.0   // 标准 A4 音
    const val RIGHT_FREQ = 880.0  // 高八度 A5 音

    /**
     * 生成 16-bit PCM 立体声交错采样数据 [L0, R0, L1, R1, ...]。
     *
     * @param sampleRate 采样率 (Hz)
     * @param durationMs 持续时间 (毫秒)
     * @param mode 声道模式：STEREO、LEFT_ONLY、RIGHT_ONLY
     * @param amplitude 幅值缩放 (0.0 .. 1.0)，默认 0.8
     * @return ShortArray 包含交错的 PCM 采样点
     */
    fun generateStereoTone(
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        durationMs: Int = DEFAULT_DURATION_MS,
        mode: ToneMode = ToneMode.STEREO,
        amplitude: Double = 0.8
    ): ShortArray {
        if (durationMs <= 0 || sampleRate <= 0) return ShortArray(0)

        val totalFrames = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(totalFrames * 2)
        val maxVal = (Short.MAX_VALUE * amplitude.coerceIn(0.0, 1.0))

        for (i in 0 until totalFrames) {
            val t = i.toDouble() / sampleRate
            val leftSample = when (mode) {
                ToneMode.STEREO, ToneMode.LEFT_ONLY ->
                    (sin(2.0 * PI * LEFT_FREQ * t) * maxVal).toInt().toShort()
                ToneMode.RIGHT_ONLY -> 0.toShort()
            }
            val rightSample = when (mode) {
                ToneMode.STEREO ->
                    (sin(2.0 * PI * LEFT_FREQ * t) * maxVal).toInt().toShort()
                ToneMode.RIGHT_ONLY ->
                    (sin(2.0 * PI * RIGHT_FREQ * t) * maxVal).toInt().toShort()
                ToneMode.LEFT_ONLY -> 0.toShort()
            }
            buffer[i * 2] = leftSample
            buffer[i * 2 + 1] = rightSample
        }
        return buffer
    }
}
