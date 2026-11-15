package com.syncdesk.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {

    /** 100 ms of 16 kHz mono at a constant amplitude. */
    private fun chunk(amplitude: Int): ByteArray = ByteArray(3_200).also { bytes ->
        for (i in 0 until 1_600) {
            val sample = if (i % 2 == 0) amplitude else -amplitude
            bytes[2 * i] = (sample and 0xff).toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
    }

    private val silence = chunk(0)
    private val voice = chunk(6_000) // about -15 dBFS
    private val hum = chunk(200) // about -44 dBFS

    @Test
    fun loudnessIsMeasuredInDecibelsOfFullScale() {
        assertEquals(-14.7, SpeechGate.dbfs(voice), 0.1)
        assertEquals(Double.NEGATIVE_INFINITY, SpeechGate.dbfs(silence), 0.0)
    }

    @Test
    fun talkingStartsOnTheFirstLoudChunkAndEndsWithTheSentence() {
        val gate = SpeechGate()
        assertNull(gate.onChunk(silence))
        assertNull(gate.onChunk(hum))
        assertEquals(true, gate.onChunk(voice))
        // The recognizer catches up; nothing changes.
        assertNull(gate.onWords())
        // Quiet between words does not end it.
        repeat(20) { assertNull(gate.onChunk(silence)) }
        assertTrue(gate.speaking)
        assertEquals(false, gate.onSentenceEnd())
        assertFalse(gate.speaking)
    }

    @Test
    fun loudSoundWithoutWordsStopsAfterTheConfirmationWindow() {
        val gate = SpeechGate(confirmChunks = 3)
        assertEquals(true, gate.onChunk(voice))
        assertNull(gate.onChunk(voice))
        assertNull(gate.onChunk(voice))
        assertEquals(false, gate.onChunk(voice))
        // A new loud chunk starts over.
        assertEquals(true, gate.onChunk(voice))
    }

    @Test
    fun wordsStartTalkingWhenTheAudioWasTooQuietToNotice() {
        val gate = SpeechGate()
        assertNull(gate.onChunk(hum))
        assertEquals(true, gate.onWords())
        assertEquals(false, gate.onStreamEnd())
        assertNull(gate.onStreamEnd())
    }
}
