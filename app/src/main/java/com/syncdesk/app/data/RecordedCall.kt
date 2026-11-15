package com.syncdesk.app.data

import android.content.res.AssetManager
import com.syncdesk.app.domain.KeyPoints
import com.syncdesk.app.domain.MeetingInfo
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.MeetingNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The audio of a phone call, heard by a real speech recognizer.
 *
 * A regular app cannot reach call audio: the SpeechToText SDK's call API, which takes the
 * driver's side and the other side separately, answers "available only for privileged apps"
 * (seen on the Pleos Connect v2.1.0 emulator on 2026-11-13). Until the app ships with the
 * vehicle, the call is a recording of a demo meeting, one clip per sentence. Each call carries the
 * next of [meetings] from its start, plays it once, and stays quiet until it is hung up. Every
 * clip goes through [recognizer]:
 *
 * - The driver's lines tell when the driver is talking. [driverSpeaking] turns on when the
 *   recognizer starts hearing words and off when it closes the sentence.
 *   A promise in what the driver said ("제가 ... 보내겠습니다") goes out on [promises].
 * - Everyone else's lines become notes with the text the recognizer heard. [transcript] carries
 *   the notes heard while it is collected, which is for as long as the call lasts. How loud each
 *   of their clips is tells how loud the other party's voice is ([voiceLevel]).
 *
 * Each note's summary line is picked from the text that was heard ([KeyPoints]). Speaker names
 * come from [script], because speaker recognition is privileged too. Where no recognizer is
 * available, the script stands in: the driver counts as talking for as long as the clip lasts,
 * and the scripted sentence is the note.
 */
class RecordedCall(
    private val meetings: List<Meeting>,
    private val recognizer: SpeechRecognizer,
    private val pauseMillis: Long = 350L,
    private val fallbackMillis: Long = 2_600L,
    private val now: () -> String = { LocalTime.now().format(TIME_FORMAT) },
) : MeetingSource {

    /** One recorded meeting: its script, and the clip for each line of it. */
    class Meeting(val script: ScriptedMeetingSource, val audio: (index: Int) -> ByteArray?)

    /** A call that always carries the same meeting. */
    constructor(
        script: ScriptedMeetingSource,
        audio: (index: Int) -> ByteArray?,
        recognizer: SpeechRecognizer,
        pauseMillis: Long = 350L,
        fallbackMillis: Long = 2_600L,
        now: () -> String = { LocalTime.now().format(TIME_FORMAT) },
    ) : this(listOf(Meeting(script, audio)), recognizer, pauseMillis, fallbackMillis, now)

    // Before the first call, the materials shown are those of the first meeting.
    override val title: String get() = meetings.firstOrNull()?.script?.title.orEmpty()
    override val materials: List<MeetingMaterial> get() = meetings.firstOrNull()?.script?.materials.orEmpty()

    private val _driverSpeaking = MutableStateFlow(false)

    /** Whether the recognizer currently hears the driver talking. */
    val driverSpeaking: StateFlow<Boolean> = _driverSpeaking.asStateFlow()

    private val _voiceLevel = MutableStateFlow(VoiceLevel.NOMINAL)

    /**
     * How loud the other party's voice is, measured from the clip being played (see [VoiceLevel]).
     * It keeps the level of their last sentence between sentences.
     */
    val voiceLevel: StateFlow<Int> = _voiceLevel.asStateFlow()

    private val notes = MutableSharedFlow<MeetingNote>(extraBufferCapacity = 16)
    private val promised = MutableSharedFlow<MeetingNote>(extraBufferCapacity = 16)

    override fun transcript(): Flow<MeetingNote> = notes.asSharedFlow()

    override fun promises(): Flow<MeetingNote> = promised.asSharedFlow()

    private val started = MutableSharedFlow<MeetingInfo>(extraBufferCapacity = 4)

    override fun meetings(): Flow<MeetingInfo> = started.asSharedFlow()

    private var calls = 0
    private var nextId = 1

    /**
     * The line whose clip has been played to the end but which the recognizer has not finished
     * with yet; null between lines and while a clip is still playing.
     */
    private var awaitingRecognition: ScriptedMeetingSource.ScriptLine? = null

    /**
     * One call: the next meeting, from its first line to its last, then silence until the call is
     * cancelled by hanging up.
     *
     * The recognizer closes a sentence only after its last word, so a call hung up right after a
     * sentence would lose it. A line that was said to the end before the hang-up is therefore
     * still written down, from the script, if the recognizer had not finished with it. A line cut
     * off in the middle was not said in full and is left out.
     */
    suspend fun play() {
        if (meetings.isEmpty()) return
        val meeting = meetings[calls % meetings.size]
        calls++
        started.tryEmit(MeetingInfo(meeting.script.title, meeting.script.materials))
        try {
            meeting.script.script.forEachIndexed { index, line ->
                val clip = meeting.audio(index)
                if (line.driver) speakAsDriver(line, clip) else listenTo(line, clip)
                delay(pauseMillis)
            }
            awaitCancellation()
        } finally {
            _driverSpeaking.value = false
            awaitingRecognition?.let { line -> if (line.driver) promiseIn(line, line.text) else write(line, line.text) }
            awaitingRecognition = null
        }
    }

    private suspend fun speakAsDriver(line: ScriptedMeetingSource.ScriptLine, clip: ByteArray?) {
        val heard = if (clip != null && recognizer.isAvailable()) {
            recognize(line, clip) { speaking -> _driverSpeaking.value = speaking }.also {
                _driverSpeaking.value = false
            }
        } else {
            _driverSpeaking.value = true
            try {
                delay(clipMillis(clip))
            } finally {
                _driverSpeaking.value = false
            }
            null
        }
        promiseIn(line, heard ?: line.text)
    }

    /** Reports the promise in what the driver [said], if there is one. */
    private fun promiseIn(line: ScriptedMeetingSource.ScriptLine, said: String) {
        KeyPoints.commitment(said)?.let { task ->
            // Its place in the call memo is given where the memo is written.
            promised.tryEmit(MeetingNote(id = 0, time = now(), speaker = line.speaker, text = said, gist = task, byDriver = true))
        }
    }

    private suspend fun listenTo(line: ScriptedMeetingSource.ScriptLine, clip: ByteArray?) {
        _voiceLevel.value = clip?.let { VoiceLevel.of(it) } ?: VoiceLevel.NOMINAL
        val heard = if (clip != null && recognizer.isAvailable()) {
            recognize(line, clip)
        } else {
            delay(clipMillis(clip))
            null
        }
        write(line, heard ?: line.text)
    }

    private fun write(line: ScriptedMeetingSource.ScriptLine, text: String) {
        notes.tryEmit(MeetingNote(id = nextId++, time = now(), speaker = line.speaker, text = text, gist = KeyPoints.of(text)))
    }

    /**
     * [clip] through the recognizer. Once the clip has had time to play to the end, [line] counts
     * as said ([awaitingRecognition]) until the recognizer returns.
     */
    private suspend fun recognize(
        line: ScriptedMeetingSource.ScriptLine,
        clip: ByteArray,
        onSpeech: (Boolean) -> Unit = {},
    ): String? = coroutineScope {
        val played = launch {
            delay(clipMillis(clip))
            awaitingRecognition = line
        }
        try {
            recognizer.transcribe(clip, onSpeech)
        } finally {
            played.cancel()
            // Still set on cancellation (the call was hung up): play() writes the line down.
            if (isActive) awaitingRecognition = null
        }
    }

    private fun clipMillis(clip: ByteArray?): Long = clip?.let { it.size / BYTES_PER_MILLI } ?: fallbackMillis

    /**
     * The call as the app sees it: connected or not as [call] says, and the driver talking when
     * the recording says so or when [call] says so (the demo controls can still force it). The
     * recording plays in [scope] for as long as the call is connected.
     *
     * A hang-up reaches the app only once the recording has stopped, so a line said to the end
     * before it (see [play]) is written down while the call is still on: the app keeps nothing
     * that is heard after a call.
     */
    fun monitor(call: CallMonitor, scope: CoroutineScope): CallMonitor {
        val connected = MutableStateFlow(call.call.value.active)
        scope.launch {
            call.call.map { it.active }.distinctUntilChanged().collectLatest { active ->
                // collectLatest has let the recording of the call before finish stopping.
                connected.value = active
                if (active) play()
            }
        }
        val merged = combine(call.call, driverSpeaking, connected) { signal, heard, on ->
            CallSignal(active = on, driverSpeaking = on && (signal.driverSpeaking || heard))
        }.stateIn(scope, SharingStarted.Eagerly, call.call.value)
        return object : CallMonitor {
            override val call: StateFlow<CallSignal> = merged
        }
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** 16 kHz, 16-bit mono: 32 bytes of audio per millisecond. */
        private const val BYTES_PER_MILLI = 32L

        /** Reads clip `meeting/<folder>/note_NN.pcm` for line NN (1-based) from the app's assets. */
        fun assetClips(assets: AssetManager, folder: String): (Int) -> ByteArray? = { index ->
            try {
                assets.open(String.format(Locale.ROOT, "meeting/%s/note_%02d.pcm", folder, index + 1)).use { it.readBytes() }
            } catch (_: IOException) {
                null
            }
        }
    }
}
