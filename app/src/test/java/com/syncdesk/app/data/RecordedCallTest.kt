package com.syncdesk.app.data

import com.syncdesk.app.data.ScriptedMeetingSource.ScriptLine
import com.syncdesk.app.domain.MeetingNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordedCallTest {

    private val script = ScriptedMeetingSource(
        title = "meeting",
        materials = emptyList(),
        script = listOf(
            ScriptLine(speaker = "A", text = "scripted one", gist = "gist one"),
            ScriptLine(speaker = "Me", text = "공문은 제가 오후에 바로 보내겠습니다", gist = null, driver = true),
            ScriptLine(speaker = "B", text = "scripted two", gist = null),
        ),
    )

    /** One second of 16 kHz, 16-bit mono audio per clip. */
    private val clips: (Int) -> ByteArray? = { ByteArray(32_000) }

    /**
     * Hears words 200 ms into each clip and closes the sentence at its end, as the on-device
     * engine does with a short recording.
     */
    private class FakeRecognizer(
        private val available: Boolean = true,
        /** How long after the end of the clip the result comes. */
        private val latencyMillis: Long = 0L,
        private val answer: (Int) -> String? = { "heard ${it + 1}" },
    ) : SpeechRecognizer {
        var calls = 0
        override suspend fun isAvailable() = available
        override suspend fun transcribe(pcm: ByteArray, onSpeech: (Boolean) -> Unit): String? {
            val call = calls++
            delay(200L)
            onSpeech(true)
            delay(pcm.size / 32L - 200L)
            onSpeech(false)
            delay(latencyMillis)
            return answer(call)
        }
    }

    private fun recordedCall(recognizer: SpeechRecognizer, audio: (Int) -> ByteArray? = clips) =
        RecordedCall(script, audio, recognizer, pauseMillis = 100L, fallbackMillis = 500L, now = { "09:00" })

    /** Collects the notes and the driver's speaking state while the call plays. */
    private class Session(scope: TestScope, call: RecordedCall) {
        val notes = mutableListOf<MeetingNote>()
        val speaking = mutableListOf<Boolean>()
        val promises = mutableListOf<MeetingNote>()
        private val jobs = listOf(
            scope.launch { call.transcript().collect { notes += it } },
            scope.launch { call.promises().collect { promises += it } },
            scope.launch { call.driverSpeaking.collect { speaking += it } },
        )
        private val playing = scope.launch { call.play() }

        suspend fun hangUp() {
            playing.cancelAndJoin()
            jobs.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun othersBecomeNotesWithWhatTheRecognizerHeard() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer()))
        // A (1 s), pause, driver (1 s), pause, B (1 s)
        advanceTimeBy(3_250L)
        runCurrent()
        session.hangUp()

        assertEquals(
            listOf(
                MeetingNote(1, "09:00", "A", "heard 1", null),
                MeetingNote(2, "09:00", "B", "heard 3", null),
            ),
            session.notes,
        )
    }

    @Test
    fun summaryLineIsPickedFromWhatWasHeardNotFromTheScript() = runTest {
        val heard = "자료는 확인했습니다 샘플 3개를 목요일까지 보내주세요"
        val session = Session(this, recordedCall(FakeRecognizer { heard }))
        advanceTimeBy(1_050L)
        runCurrent()
        session.hangUp()

        assertEquals("샘플 3개를 목요일까지 보내기", session.notes.single().gist)
    }

    @Test
    fun driverIsSpeakingOnlyWhileTheRecognizerHearsTheDriver() = runTest {
        val call = recordedCall(FakeRecognizer())
        val session = Session(this, call)
        runCurrent()

        advanceTimeBy(1_050L) // A is done, the driver's clip has just started
        runCurrent()
        assertFalse(call.driverSpeaking.value)

        advanceTimeBy(300L) // words heard
        runCurrent()
        assertTrue(call.driverSpeaking.value)

        advanceTimeBy(800L) // the sentence closed
        runCurrent()
        assertFalse(call.driverSpeaking.value)

        session.hangUp()
        assertEquals(listOf(false, true, false), session.speaking)
    }

    @Test
    fun theVoiceLevelIsThatOfTheOthersClipBeingPlayedAndStaysThroughTheDriversOwnLine() = runTest {
        /** One second at a constant amplitude; the driver's clip is the loudest. */
        fun tone(amplitude: Int) = ByteArray(32_000).also { bytes ->
            for (i in 0 until 16_000) {
                val sample = if (i % 2 == 0) amplitude else -amplitude
                bytes[2 * i] = (sample and 0xff).toByte()
                bytes[2 * i + 1] = (sample shr 8).toByte()
            }
        }
        val audio: (Int) -> ByteArray? = { index -> listOf(tone(1_638), tone(20_000), null)[index] }
        val call = recordedCall(FakeRecognizer(), audio)
        assertEquals(VoiceLevel.NOMINAL, call.voiceLevel.value)
        val session = Session(this, call)
        runCurrent()

        // A's clip is quiet, and known as soon as it starts playing.
        assertEquals(VoiceLevel.NOMINAL - 6, call.voiceLevel.value)
        advanceTimeBy(1_500L) // the driver is talking
        runCurrent()
        assertEquals(VoiceLevel.NOMINAL - 6, call.voiceLevel.value)
        advanceTimeBy(1_000L) // B's line has no clip: an ordinary voice
        runCurrent()
        assertEquals(VoiceLevel.NOMINAL, call.voiceLevel.value)
        session.hangUp()
    }

    @Test
    fun aPromiseInTheDriversWordsIsReportedAsTheirOwnSentenceWithThePromiseAsItsKeyPoint() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer { "공문은 제가 오후에 바로 보내겠습니다" }))
        advanceTimeBy(2_200L) // A, then the driver
        runCurrent()
        session.hangUp()

        assertEquals(
            listOf(MeetingNote(0, "09:00", "Me", "공문은 제가 오후에 바로 보내겠습니다", gist = "공문을 오후에 보내기", byDriver = true)),
            session.promises,
        )
        // It is not among the others' sentences.
        assertEquals(listOf("A"), session.notes.map { it.speaker })
    }

    @Test
    fun withoutARecognizerTheScriptedPromiseStandsIn() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer(available = false)))
        advanceTimeBy(2_200L)
        runCurrent()
        session.hangUp()

        assertEquals(listOf("공문을 오후에 보내기"), session.promises.map { it.gist })
    }

    @Test
    fun hangingUpMidSentenceLeavesNobodyTalking() = runTest {
        val call = recordedCall(FakeRecognizer())
        val session = Session(this, call)
        advanceTimeBy(1_500L) // inside the driver's line
        runCurrent()
        assertTrue(call.driverSpeaking.value)

        session.hangUp()
        assertFalse(call.driverSpeaking.value)
    }

    @Test
    fun withoutARecognizerTheScriptStandsIn() = runTest {
        val recognizer = FakeRecognizer(available = false)
        val call = recordedCall(recognizer)
        val session = Session(this, call)
        advanceTimeBy(1_500L) // the driver counts as talking for the length of the clip
        runCurrent()
        assertTrue(call.driverSpeaking.value)
        advanceTimeBy(1_750L)
        runCurrent()
        session.hangUp()

        assertEquals(listOf("scripted one", "scripted two"), session.notes.map { it.text })
        assertEquals(0, recognizer.calls)
    }

    @Test
    fun scriptedSentenceIsUsedWhenNothingIsRecognized() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer(answer = { null })))
        advanceTimeBy(3_250L)
        runCurrent()
        session.hangUp()

        assertEquals(listOf("scripted one", "scripted two"), session.notes.map { it.text })
    }

    @Test
    fun eachCallCarriesTheNextMeetingFromTheStart() = runTest {
        val second = ScriptedMeetingSource(
            title = "second",
            materials = emptyList(),
            script = listOf(ScriptLine(speaker = "C", text = "other meeting", gist = null)),
        )
        val call = RecordedCall(
            meetings = listOf(RecordedCall.Meeting(script, clips), RecordedCall.Meeting(second, clips)),
            recognizer = FakeRecognizer { "heard" },
            pauseMillis = 100L,
            now = { "09:00" },
        )
        val meetings = mutableListOf<String>()
        val watching = launch { call.meetings().collect { meetings += it.title } }

        val first = Session(this, call)
        advanceTimeBy(500L) // hang up halfway through A's line
        runCurrent()
        first.hangUp()
        assertEquals(emptyList<MeetingNote>(), first.notes)

        val next = Session(this, call)
        advanceTimeBy(1_050L)
        runCurrent()
        next.hangUp()
        assertEquals(listOf("C"), next.notes.map { it.speaker })

        // Back to the first meeting, from its first line.
        val third = Session(this, call)
        advanceTimeBy(1_050L)
        runCurrent()
        third.hangUp()
        assertEquals(listOf("A"), third.notes.map { it.speaker })

        watching.cancelAndJoin()
        assertEquals(listOf("meeting", "second", "meeting"), meetings)
    }

    @Test
    fun aMeetingIsPlayedOnceAndThenTheLineIsQuiet() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer()))
        // Three clips of 1 s with 100 ms pauses, then nothing more however long the call lasts.
        advanceTimeBy(60_000L)
        runCurrent()
        session.hangUp()
        assertEquals(listOf("A", "B"), session.notes.map { it.speaker })
    }

    @Test
    fun aLineSaidToTheEndBeforeTheHangUpIsWrittenFromTheScriptWhileTheRecognizerIsStillAtIt() = runTest {
        // The recognizer gives each result 1.5 s after the end of the clip: A's clip ends at 1 s.
        val session = Session(this, recordedCall(FakeRecognizer(latencyMillis = 1_500L)))
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(emptyList<MeetingNote>(), session.notes)

        session.hangUp()
        assertEquals(listOf(MeetingNote(1, "09:00", "A", "scripted one", null)), session.notes)
    }

    @Test
    fun aPromiseSaidToTheEndBeforeTheHangUpIsStillReported() = runTest {
        // A: 0 to 2.5 s with its result; the driver's clip: 2.6 to 3.6 s, its result due at 5.1 s.
        val session = Session(this, recordedCall(FakeRecognizer(latencyMillis = 1_500L)))
        advanceTimeBy(4_000L)
        runCurrent()
        session.hangUp()

        assertEquals(listOf("heard 1"), session.notes.map { it.text })
        assertEquals(listOf("공문을 오후에 보내기"), session.promises.map { it.gist })
    }

    @Test
    fun aLineCutOffInTheMiddleOfItsClipIsLeftOut() = runTest {
        val session = Session(this, recordedCall(FakeRecognizer(latencyMillis = 1_500L)))
        advanceTimeBy(3_000L) // A written at 2.5 s; the driver's clip is halfway
        runCurrent()
        session.hangUp()

        assertEquals(listOf("heard 1"), session.notes.map { it.text })
        assertEquals(emptyList<MeetingNote>(), session.promises)
    }

    @Test
    fun aHangUpReachesTheAppOnlyAfterTheLineSaidBeforeItHasBeenWrittenDown() = runTest {
        val simulated = SimulatedCar()
        val call = recordedCall(FakeRecognizer(latencyMillis = 1_500L))
        val monitorScope = TestScope(testScheduler)
        val monitor = call.monitor(simulated, monitorScope.backgroundScope)
        // What the app sees, in order.
        val seen = mutableListOf<String>()
        val watching = listOf(
            backgroundScope.launch { monitor.call.collect { seen += "active=${it.active}" } },
            backgroundScope.launch { call.transcript().collect { seen += it.text } },
        )
        runCurrent()

        simulated.setCallActive(true)
        advanceTimeBy(1_500L) // A's clip has played; the recognizer is not done
        runCurrent()
        simulated.setCallActive(false)
        runCurrent()

        assertEquals(listOf("active=false", "active=true", "scripted one", "active=false"), seen)
        watching.forEach { it.cancelAndJoin() }
    }

    @Test
    fun monitorReportsTheDriverHeardInTheRecording() = runTest {
        val simulated = SimulatedCar()
        val call = recordedCall(FakeRecognizer())
        val monitorScope = TestScope(testScheduler)
        val monitor = call.monitor(simulated, monitorScope.backgroundScope)

        simulated.setCallActive(true)
        advanceTimeBy(1_400L) // the driver's words are being heard
        runCurrent()
        assertEquals(CallSignal(active = true, driverSpeaking = true), monitor.call.value)

        simulated.setCallActive(false)
        runCurrent()
        assertEquals(CallSignal(active = false, driverSpeaking = false), monitor.call.value)
    }

    @Test
    fun scriptedMeetingLeavesOutTheDriversLines() = runTest {
        val notes = mutableListOf<MeetingNote>()
        val job = launch { script.transcript().collect { notes += it } }
        advanceTimeBy(2_600L * 2 + 1)
        runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("A", "B"), notes.map { it.speaker })
    }
}
