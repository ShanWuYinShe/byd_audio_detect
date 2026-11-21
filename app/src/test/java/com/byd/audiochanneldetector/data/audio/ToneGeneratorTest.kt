package com.byd.audiochanneldetector.data.audio

import com.byd.audiochanneldetector.domain.model.ToneMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneGeneratorTest {

    @Test
    fun generateStereoTone_hasCorrectLength() {
        val sampleRate = 44100
        val durationMs = 500
        val buffer = ToneGenerator.generateStereoTone(
            sampleRate = sampleRate,
            durationMs = durationMs,
            mode = ToneMode.STEREO
        )
        // 500ms at 44.1kHz = 22050 frames * 2 (L+R) = 44100 samples
        assertEquals(44100, buffer.size)
    }

    @Test
    fun generateStereoTone_durationZero_returnsEmptyArray() {
        val buffer = ToneGenerator.generateStereoTone(durationMs = 0)
        assertEquals(0, buffer.size)
    }

    @Test
    fun generateStereoTone_leftOnly_rightChannelIsSilent() {
        val buffer = ToneGenerator.generateStereoTone(
            sampleRate = 44100,
            durationMs = 100,
            mode = ToneMode.LEFT_ONLY
        )
        assertTrue(buffer.isNotEmpty())

        var nonZeroLeftCount = 0
        for (i in buffer.indices) {
            if (i % 2 == 1) {
                // 右声道必须完全静音 (0)
                assertEquals("Right channel must be silent", 0.toShort(), buffer[i])
            } else {
                if (buffer[i] != 0.toShort()) {
                    nonZeroLeftCount++
                }
            }
        }
        assertTrue("Left channel should contain audio signals", nonZeroLeftCount > 0)
    }

    @Test
    fun generateStereoTone_rightOnly_leftChannelIsSilent() {
        val buffer = ToneGenerator.generateStereoTone(
            sampleRate = 44100,
            durationMs = 100,
            mode = ToneMode.RIGHT_ONLY
        )
        assertTrue(buffer.isNotEmpty())

        var nonZeroRightCount = 0
        for (i in buffer.indices) {
            if (i % 2 == 0) {
                // 左声道必须完全静音 (0)
                assertEquals("Left channel must be silent", 0.toShort(), buffer[i])
            } else {
                if (buffer[i] != 0.toShort()) {
                    nonZeroRightCount++
                }
            }
        }
        assertTrue("Right channel should contain audio signals", nonZeroRightCount > 0)
    }

    @Test
    fun generateStereoTone_stereo_bothChannelsActive() {
        val buffer = ToneGenerator.generateStereoTone(
            sampleRate = 44100,
            durationMs = 100,
            mode = ToneMode.STEREO
        )
        var leftNonZero = 0
        var rightNonZero = 0
        for (i in buffer.indices) {
            if (i % 2 == 0 && buffer[i] != 0.toShort()) leftNonZero++
            if (i % 2 == 1 && buffer[i] != 0.toShort()) rightNonZero++
        }
        assertTrue("Left channel should be active", leftNonZero > 0)
        assertTrue("Right channel should be active", rightNonZero > 0)
    }

    @Test
    fun generateStereoTone_zeroAmplitude_returnsAllZeros() {
        val buffer = ToneGenerator.generateStereoTone(
            durationMs = 50,
            amplitude = 0.0
        )
        assertTrue(buffer.isNotEmpty())
        assertTrue("All samples should be 0 with 0 amplitude", buffer.all { it == 0.toShort() })
    }
}
