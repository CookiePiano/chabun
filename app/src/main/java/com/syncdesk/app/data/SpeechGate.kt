package com.syncdesk.app.data

import kotlin.math.log10

/**
 * Decides when someone starts and stops talking in a stream of audio that is also being
 * recognized.
 *
 * The recognizer only reports words once it has heard enough of them, about 0.5 to 1 second after
 * the voice begins (measured on the Pleos Connect v2.1.0 emulator on 2026-11-13). Waiting for it
 * would let a sound be played over the driver's first words. So talking starts on the first loud chunk
 * of audio, and the recognizer then confirms it: if it has not heard a word within
 * [confirmChunks], the sound was not speech and talking stops again. Talking ends when the
 * recognizer closes the sentence, not when the audio gets quiet, because speech dips between
 * words.
 *
 * Not thread-safe; the caller serializes calls.
 */
class SpeechGate(
    private val startDbfs: Double = -35.0,
    private val confirmChunks: Int = 15,
) {
    /** Whether someone is talking. */
    var speaking: Boolean = false
        private set

    private var confirmed = false
    private var unconfirmedChunks = 0

    /**
     * One chunk of audio about to be recognized. Returns true or false when talking starts or
     * stops because of it, otherwise null.
     */
    fun onChunk(pcm: ByteArray): Boolean? {
        if (!speaking) {
            if (dbfs(pcm) < startDbfs) return null
            speaking = true
            confirmed = false
            unconfirmedChunks = 0
            return true
        }
        if (confirmed) return null
        unconfirmedChunks++
        if (unconfirmedChunks < confirmChunks) return null
        // Loud, but no words: not speech.
        speaking = false
        return false
    }

    /** The recognizer heard words. Returns true when this is what starts talking, otherwise null. */
    fun onWords(): Boolean? {
        confirmed = true
        if (speaking) return null
        speaking = true
        return true
    }

    /** The recognizer closed the sentence. Returns false when talking stops, otherwise null. */
    fun onSentenceEnd(): Boolean? {
        if (!speaking) return null
        speaking = false
        return false
    }

    /** The stream ended or was cut off. Returns false when someone was still talking. */
    fun onStreamEnd(): Boolean? = onSentenceEnd()

    companion object {
        /** Loudness of 16-bit little-endian PCM in dB relative to full scale; silence is -inf. */
        fun dbfs(pcm: ByteArray): Double {
            val samples = pcm.size / 2
            if (samples == 0) return Double.NEGATIVE_INFINITY
            var sum = 0.0
            for (i in 0 until samples) {
                val sample = ((pcm[2 * i].toInt() and 0xff) or (pcm[2 * i + 1].toInt() shl 8)).toShort().toDouble()
                sum += sample * sample
            }
            val rms = kotlin.math.sqrt(sum / samples)
            return if (rms == 0.0) Double.NEGATIVE_INFINITY else 20 * log10(rms / 32768.0)
        }
    }
}
