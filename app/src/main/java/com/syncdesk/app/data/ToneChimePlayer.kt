package com.syncdesk.app.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays each [Cue] as plain sine tones made on the spot, so no sound file (and no licence) is
 * involved. Each cue lasts at most 0.3 s and is played as a notification sound at a low volume,
 * below the call it marks.
 */
class ToneChimePlayer(private val volume: Float = 0.35f) : ChimePlayer {

    override suspend fun play(cue: Cue) {
        val notes = when (cue) {
            Cue.MissedPartMarked -> listOf(HIGH to NOTE_MILLIS, LOW to NOTE_MILLIS)
        }
        Log.i(TAG, "cue $cue")
        val pcm = tones(notes)
        withContext(Dispatchers.Default) {
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "no sound: ${e.message}")
                return@withContext
            }
            try {
                track.write(pcm, 0, pcm.size)
                track.setVolume(volume)
                track.play()
                delay(pcm.size * 1_000L / SAMPLE_RATE)
            } finally {
                track.release()
            }
        }
    }

    /** The notes one after another, each faded in and out so it does not click. */
    private fun tones(notes: List<Pair<Double, Int>>): ShortArray {
        val samples = notes.flatMap { (frequency, millis) ->
            val count = SAMPLE_RATE * millis / 1_000
            val fade = SAMPLE_RATE * FADE_MILLIS / 1_000
            (0 until count).map { i ->
                val envelope = min(1.0, min(i, count - 1 - i).toDouble() / fade)
                (sin(2 * PI * frequency * i / SAMPLE_RATE) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
            }
        }
        return samples.toShortArray()
    }

    private companion object {
        const val TAG = "SyncDeskChime"
        const val SAMPLE_RATE = 44_100
        const val NOTE_MILLIS = 120
        const val FADE_MILLIS = 12
        const val LOW = 659.25
        const val HIGH = 880.0
    }
}
