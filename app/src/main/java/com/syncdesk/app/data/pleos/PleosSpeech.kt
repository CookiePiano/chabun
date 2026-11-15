package com.syncdesk.app.data.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.syncdesk.app.BuildConfig
import com.syncdesk.app.data.LineTracker
import com.syncdesk.app.data.Narrator
import com.syncdesk.app.data.SpeechGate
import com.syncdesk.app.data.SpeechRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

private const val TAG = "SyncDeskSpeech"

/** How long to wait for an SDK to initialize, and how long to leave it alone after it failed. */
private const val INIT_TIMEOUT_MILLIS = 5_000L
private const val RETRY_AFTER_MILLIS = 30_000L

/**
 * Creates and initializes an SDK object once, on first use. A failure is remembered for
 * [RETRY_AFTER_MILLIS], so a device without the speech service does not wait on every request,
 * while a service that was still starting is tried again later.
 */
private class LazyEngine<T : Any>(
    private val name: String,
    private val create: () -> T,
    private val initialize: (T, (Boolean) -> Unit) -> Unit,
    private val release: (T) -> Unit,
    private val configure: (T) -> Unit,
) {
    private val lock = Mutex()
    private var engine: T? = null
    private var retryAt = 0L

    suspend fun get(): T? = lock.withLock {
        engine?.let { return it }
        if (SystemClock.elapsedRealtime() < retryAt) return null
        val created = runCatching(create).getOrElse {
            Log.w(TAG, "$name unavailable: $it")
            retryAt = SystemClock.elapsedRealtime() + RETRY_AFTER_MILLIS
            return null
        }
        val ready = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val once = AtomicReference(continuation)
                runCatching { initialize(created) { ok -> once.getAndSet(null)?.resume(ok) } }
                    .onFailure { once.getAndSet(null)?.resume(false) }
            }
        } ?: false
        if (!ready) {
            Log.w(TAG, "$name did not initialize")
            runCatching { release(created) }
            retryAt = SystemClock.elapsedRealtime() + RETRY_AFTER_MILLIS
            return null
        }
        runCatching { configure(created) }.onFailure { Log.w(TAG, "$name configuration failed: $it") }
        Log.i(TAG, "$name ready (on-device)")
        engine = created
        created
    }
}

/**
 * Speech recognition with the Pleos SpeechToText SDK, on the vehicle's on-device engine.
 * On-device recognition needs no network and no app registration, and accepts recorded audio
 * through `sendAudio`. Checked on the Pleos Connect v2.1.0 emulator on 2026-11-13.
 */
class PleosSpeechRecognizer(
    context: Context,
    locale: Locale = Locale.KOREA,
) : SpeechRecognizer {

    private sealed interface Heard {
        data class Sentence(val text: String) : Heard
        data class Failed(val error: SttError) : Heard
    }

    /** Where the listener delivers results; set only while [transcribe] runs. */
    @Volatile
    private var sink: SendChannel<Heard>? = null

    /** Told when talking starts and stops; set only while [transcribe] runs. */
    @Volatile
    private var speech: ((Boolean) -> Unit)? = null

    /**
     * Talking starts on the loudness of the audio sent and is confirmed and ended by the
     * recognizer. The audio loop and the recognizer's callbacks both update it, under [gateLock].
     */
    private var gate = SpeechGate()
    private val gateLock = Any()

    /** Applies [change] and reports what it caused, under the lock so reports keep their order. */
    private fun gated(change: SpeechGate.() -> Boolean?) {
        synchronized(gateLock) {
            val event = gate.change() ?: return
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "talking=$event after ${SystemClock.elapsedRealtime() - streamStartedAt} ms of audio")
            }
            speech?.invoke(event)
        }
    }

    /** When the current recording started streaming: for [stale] and the timing in the debug log. */
    @Volatile
    private var streamStartedAt = 0L

    /**
     * Results carry no request id, so one that belongs to the previous recording (cut off by
     * hanging up, say) could arrive just as the next one starts. Words cannot be recognized in the
     * first moments of a recording, so anything that soon is from the one before and is dropped.
     */
    private fun stale(): Boolean = SystemClock.elapsedRealtime() - streamStartedAt < STALE_WINDOW_MILLIS

    private val listener = object : ResultListener {
        override fun onUpdated(stt: String, completed: Boolean) {
            if (stale()) return
            if (!completed && stt.isNotBlank()) gated { onWords() }
            if (completed) {
                gated { onSentenceEnd() }
                if (stt.isNotBlank()) sink?.trySend(Heard.Sentence(stt.trim()))
            }
        }

        override fun onError(errorType: SttError) {
            if (stale()) return
            sink?.trySend(Heard.Failed(errorType))
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val engine = LazyEngine(
        name = "SpeechToText",
        create = { SpeechToText(context.applicationContext, SttMode.ON_DEVICE) },
        initialize = { stt, done ->
            stt.initialize(object : SttInitListener {
                override fun onSuccess() = done(true)
                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "SpeechToText initialization failed: $errorType")
                    done(false)
                }
            })
        },
        release = { it.release() },
        configure = {
            it.addListener(listener)
            it.setLocale(locale)
        },
    )

    /** One recording at a time: results carry no request id, so they could not be told apart. */
    private val busy = Mutex()

    override suspend fun isAvailable(): Boolean = engine.get() != null

    override suspend fun transcribe(pcm: ByteArray, onSpeech: (speaking: Boolean) -> Unit): String? = busy.withLock {
        val stt = engine.get() ?: return null
        val heard = Channel<Heard>(Channel.UNLIMITED)
        streamStartedAt = SystemClock.elapsedRealtime()
        synchronized(gateLock) { gate = SpeechGate() }
        speech = onSpeech
        sink = heard
        try {
            withContext(Dispatchers.IO) {
                var offset = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + CHUNK_BYTES, pcm.size)
                    val chunk = pcm.copyOfRange(offset, end)
                    gated { onChunk(chunk) }
                    stt.sendAudio(chunk)
                    offset = end
                    delay(CHUNK_MILLIS)
                }
                // A moment of silence lets the recognizer hear the last syllable out and close the
                // sentence itself. Without it, a clip that ends right after the voice loses its
                // last syllable ("보내겠습니다" came back as "보내겠습니" on 2026-11-13).
                repeat(TRAILING_SILENCE_CHUNKS) {
                    stt.sendAudio(SILENCE)
                    delay(CHUNK_MILLIS)
                }
                stt.completeAudioSend()
            }
            // The recognizer closes a sentence at each pause, so one clip may give several.
            // Sentences already closed while streaming are waiting in the channel.
            val sentences = mutableListOf<String>()
            var wait = RESULT_TIMEOUT_MILLIS
            while (true) {
                when (val next = withTimeoutOrNull(wait) { heard.receive() } ?: break) {
                    is Heard.Sentence -> {
                        sentences += next.text
                        wait = SETTLE_MILLIS
                    }
                    is Heard.Failed -> {
                        Log.w(TAG, "Recognition failed: ${next.error}")
                        break
                    }
                }
            }
            // What was said on a call stays out of the system log in release builds.
            if (BuildConfig.DEBUG) Log.d(TAG, "heard: $sentences")
            sentences.joinToString(" ").ifBlank { null }
        } catch (cancelled: CancellationException) {
            // The road cleared mid-sentence: drop the rest of this recording.
            runCatching { stt.stop() }
            throw cancelled
        } finally {
            // A sentence cut off or never closed still ends here, so nobody is left talking.
            gated { onStreamEnd() }
            speech = null
            sink = null
            heard.close()
        }
    }

    private companion object {
        /** 100 ms of 16 kHz, 16-bit mono audio, sent every 100 ms as the SDK guide does. */
        const val CHUNK_BYTES = 3_200
        const val CHUNK_MILLIS = 100L
        const val TRAILING_SILENCE_CHUNKS = 5

        /** The recognizer's first words come 0.5 s or more into a recording (measured 2026-11-13). */
        const val STALE_WINDOW_MILLIS = 300L
        val SILENCE = ByteArray(CHUNK_BYTES)

        /**
         * Wait for the first sentence after the audio ends, then briefly for any that follow. The
         * recognizer closes the last sentence within about 0.5 s of the voice ending, which is
         * before the audio has finished sending, so the settle time only covers a straggler.
         */
        const val RESULT_TIMEOUT_MILLIS = 6_000L
        const val SETTLE_MILLIS = 400L
    }
}

/**
 * Reads the summary aloud with the Pleos TextToSpeech SDK on the on-device engine, one line at a
 * time. Uses [fallback] where the speech service is unavailable.
 */
class PleosNarrator(
    context: Context,
    private val fallback: Narrator,
    locale: Locale = Locale.KOREA,
) : Narrator {

    /** Ties the engine's playback events to the line being spoken; see [LineTracker]. */
    private val playback = LineTracker(clock = SystemClock::elapsedRealtime)

    private val listener = object : EventListener {
        override fun onStart() = playback.started()
        override fun onDone() {
            playback.ended()
        }
        override fun onStop() {
            playback.ended()
        }
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "Speech synthesis failed: $errorType")
            playback.failed()
        }

        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val engine = LazyEngine(
        name = "TextToSpeech",
        create = { TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE) },
        initialize = { tts, done ->
            tts.initialize(object : TtsInitListener {
                override fun onSuccess() = done(true)
                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "TextToSpeech initialization failed: $errorType")
                    done(false)
                }
            })
        },
        release = { it.release() },
        configure = {
            it.addEventListener(listener)
            it.setLocale(locale)
        },
    )

    private val busy = Mutex()

    override suspend fun speak(lines: List<String>) {
        val tts = engine.get() ?: return fallback.speak(lines)
        busy.withLock {
            for (line in lines) {
                val finished = withTimeoutOrNull(LINE_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine { continuation ->
                        playback.begin { continuation.resume(Unit) }
                        // Cancelled when the road gets demanding again, the call ends, or the line
                        // timed out: stop talking, and ignore whatever the engine says about it later.
                        continuation.invokeOnCancellation {
                            playback.abandon()
                            runCatching { tts.stop() }
                        }
                        runCatching { tts.speak(line, true) }.onFailure {
                            Log.w(TAG, "speak failed: $it")
                            playback.failed()
                        }
                    }
                }
                if (finished == null) Log.w(TAG, "No playback event within $LINE_TIMEOUT_MILLIS ms")
            }
        }
    }

    private companion object {
        /** Longest a summary line may take before it is given up on. */
        const val LINE_TIMEOUT_MILLIS = 15_000L
    }
}
