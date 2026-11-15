package com.syncdesk.app.runtime

import com.syncdesk.app.data.AmbientNoise
import com.syncdesk.app.data.AppSettings
import com.syncdesk.app.data.CallMemoStore
import com.syncdesk.app.data.ChimePlayer
import com.syncdesk.app.data.Cue
import com.syncdesk.app.data.DemoReplies
import com.syncdesk.app.data.HearingSource
import com.syncdesk.app.data.MeetingSource
import com.syncdesk.app.data.MemoAnswerer
import com.syncdesk.app.data.Narrator
import com.syncdesk.app.data.PresetStore
import com.syncdesk.app.data.QuietStore
import com.syncdesk.app.data.RecordedCall
import com.syncdesk.app.data.ScriptedMeetingSource
import com.syncdesk.app.data.SavedTrip
import com.syncdesk.app.data.SettingsStore
import com.syncdesk.app.data.SpeechRecognizer
import com.syncdesk.app.data.SimulatedCar
import com.syncdesk.app.data.SimulatedContacts
import com.syncdesk.app.data.SimulatedHearing
import com.syncdesk.app.data.SimulatedNarrator
import com.syncdesk.app.data.SimulatedUsbPower
import com.syncdesk.app.domain.BatteryGuard
import kotlinx.coroutines.launch
import com.syncdesk.app.data.VehicleFeature
import com.syncdesk.app.data.VehicleGateway
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinLimits
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinRanges
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.DialogPhase
import com.syncdesk.app.domain.DialogStep
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.Hearing
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.OfficeStep
import com.syncdesk.app.domain.RoadDemand
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskMode
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SyncDeskRuntimeTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val initialCabin = CabinState(fanLevel = 6, windowOpenPercent = 30, recirculation = false)

    /** Says one sentence every second; each sentence is a key point. */
    private val meeting = object : MeetingSource {
        override val title = "meeting"
        override val materials = emptyList<MeetingMaterial>()
        override fun transcript(): Flow<MeetingNote> = flow {
            var id = 1
            while (true) {
                delay(1_000L)
                emit(MeetingNote(id, "14:00", "speaker", "text $id", gist = "gist $id"))
                id++
            }
        }
    }

    /** Remembers the last saved arrangement, as the app's preferences do. */
    private val presets = object : PresetStore {
        var saved: OfficePreset? = null
        override fun load() = saved ?: OfficePreset()
        override fun save(preset: OfficePreset) {
            saved = preset
        }
    }

    /** Takes one second a line, like [SimulatedNarrator], and remembers each line as it begins. */
    private class RecordingNarrator : Narrator {
        val spoken = mutableListOf<String>()
        override suspend fun speak(lines: List<String>) {
            for (line in lines) {
                spoken += line
                delay(1_000L)
            }
        }
    }

    private val narrator = RecordingNarrator()

    /** The driver's replies, given as the demo controls give them. */
    private val replies = DemoReplies()

    private val phrases = DialogPhrases(
        offer = "offer",
        summaryIntro = "key points",
        noKeyPoints = "no key points",
        missedIntro = "missed",
        ask = "ask",
        notMentioned = "not mentioned",
    )

    /** Knows one answer; nothing else came up on the call. */
    private val answerer = object : MemoAnswerer {
        override suspend fun answer(memo: CallMemo, question: String): String? =
            "November 28".takeIf { question == "when is the delivery" }
    }

    /** Remembers each sound and when it was asked for. */
    private class HeardChimes : ChimePlayer {
        val heard = mutableListOf<Pair<Cue, Long>>()
        var clock: () -> Long = { 0L }
        override suspend fun play(cue: Cue) {
            heard += cue to clock()
        }
    }

    private val chimes = HeardChimes()

    /** Holds what was saved last, as the app's preferences do. */
    private class RememberedMemos(
        private val initial: List<CallMemo> = emptyList(),
        var trip: SavedTrip? = null,
    ) : CallMemoStore {
        val saved = mutableListOf<List<CallMemo>>()
        override fun load() = saved.lastOrNull() ?: initial
        override fun save(memos: List<CallMemo>) {
            saved += memos
        }
        override fun loadTrip() = trip
        override fun saveTrip(trip: SavedTrip) {
            this.trip = trip
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Nothing measures how well the call is heard, so the cabin is not adjusted beyond answering. */
    private val unmeasured = object : HearingSource {
        override fun levels() = emptyFlow<HearingLevels>()
    }

    private fun runtime(
        car: SimulatedCar,
        meeting: MeetingSource = this.meeting,
        memoStore: CallMemoStore = RememberedMemos(),
        hearing: HearingSource = unmeasured,
    ) = SyncDeskRuntime(
        gateway = car,
        callMonitor = car,
        meeting = meeting,
        narrator = narrator,
        presetStore = presets,
        replyTimeoutMillis = 8_000L,
        phrases = phrases,
        answerer = answerer,
        memoStore = memoStore,
        hearing = hearing,
        tuneIntervalMillis = 3_000L,
        freshAirAfterMillis = 600_000L,
        freshAirMillis = 60_000L,
        defrostAfterMillis = 300_000L,
        demandHoldMillis = 4_000L,
        chimes = chimes,
        replies = replies,
        clock = { LocalDateTime.of(2026, 11, 15, 14, 5) },
    )

    /**
     * Runs [body] against a runtime wired to [car], then stops the runtime's coroutines.
     * Without that, its once-a-second call timer would keep the test scheduler busy forever.
     */
    private fun runtimeTest(
        car: SimulatedCar,
        meeting: MeetingSource = this.meeting,
        memoStore: CallMemoStore = RememberedMemos(),
        hearing: HearingSource = unmeasured,
        body: suspend TestScope.(SimulatedCar, SyncDeskRuntime) -> Unit,
    ) = scope.runTest {
        chimes.clock = { testScheduler.currentTime }
        val runtime = runtime(car, meeting, memoStore, hearing)
        try {
            body(car, runtime)
        } finally {
            runtime.close()
        }
    }

    private fun drivingCar() = SimulatedCar(VehicleSignals(gear = Gear.Drive), initialCabin)

    private fun parkedCar() = SimulatedCar(VehicleSignals(gear = Gear.Park), initialCabin)

    private fun conversing(step: DialogStep, phase: DialogPhase) = SyncDeskMode.Conversing(step, phase)

    // region Tuning the cabin for a call

    /** Nobody says anything, so the tests of the cabin are not about the memo. */
    private val silence = object : MeetingSource {
        override val title = "meeting"
        override val materials = emptyList<MeetingMaterial>()
        override fun transcript(): Flow<MeetingNote> = emptyFlow()
    }

    /** The cabin's own noise against a voice of ordinary loudness; the test sets the noise around the car. */
    private fun hearingOf(car: SimulatedCar) = SimulatedHearing(car.cabin, car.signals)

    @Test
    fun `answering closes the window and keeps outside air out, and hanging up puts it back`() = runtimeTest(drivingCar()) { car, runtime ->
        runCurrent()

        car.setCallActive(true)
        runCurrent()

        assertEquals(
            initialCabin.copy(windowOpenPercent = CabinLimits.QUIET_WINDOW_OPEN, recirculation = true, airCleanerOn = true),
            car.cabin.value,
        )
        assertTrue(runtime.state.value.mode is SyncDeskMode.Quieting)

        // Whether the driver talks or not no longer moves anything, and neither does time alone.
        car.setDriverSpeaking(true)
        advanceTimeBy(10_000L)
        car.setDriverSpeaking(false)
        advanceTimeBy(10_000L)
        assertEquals(initialCabin.fanLevel, car.cabin.value.fanLevel)

        car.setCallActive(false)
        runCurrent()

        assertEquals(initialCabin, car.cabin.value)
        assertFalse(runtime.state.value.isTuning)
    }

    @Test
    fun `when it gets loud the fan goes down a step every three seconds, comes back when it gets quiet, and all returns on hanging up`() {
        val car = drivingCar()
        val hearing = hearingOf(car)
        runtimeTest(car, meeting = silence, hearing = hearing) { _, runtime ->
            car.setCallActive(true)
            runCurrent()
            // Window closed, ordinary noise: heard well, and nothing moves.
            assertEquals(Hearing.Good, runtime.state.value.hearing?.hearing)
            advanceTimeBy(9_100L)
            assertEquals(initialCabin.fanLevel, car.cabin.value.fanLevel)

            hearing.setAmbientNoise(AmbientNoise.High)
            runCurrent()
            assertEquals(Hearing.Bad, runtime.state.value.hearing?.hearing)
            assertEquals(6, car.cabin.value.fanLevel)

            // One step at each tuning moment, and each step really makes the cabin quieter.
            val fan = mutableListOf<Int>()
            repeat(5) {
                advanceTimeBy(3_000L)
                fan += car.cabin.value.fanLevel
            }
            assertEquals(listOf(5, 4, 3, 2, 2), fan)
            assertEquals(AirDirection.Feet, car.cabin.value.airDirection)
            // Two steps down, the seat and the temperature made up for the air.
            assertEquals(1, car.cabin.value.seatVentilation)
            assertEquals(21f, car.cabin.value.temperatureC)
            // As low as the fan goes it is heard with effort, not hardly: the call is not made louder.
            assertEquals(Hearing.Poor, runtime.state.value.hearing?.hearing)
            assertEquals(initialCabin.callVolume, car.cabin.value.callVolume)

            hearing.setAmbientNoise(AmbientNoise.Low)
            runCurrent()
            assertEquals(Hearing.Good, runtime.state.value.hearing?.hearing)
            // Slowly: three good moments for each step up.
            fan.clear()
            repeat(5) {
                advanceTimeBy(9_000L)
                fan += car.cabin.value.fanLevel
            }
            assertEquals(listOf(3, 4, 5, 6, 6), fan)
            assertEquals(0, car.cabin.value.seatVentilation)
            assertEquals(22f, car.cabin.value.temperatureC)

            hearing.setAmbientNoise(AmbientNoise.High)
            advanceTimeBy(6_100L)
            assertEquals(4, car.cabin.value.fanLevel)

            car.setCallActive(false)
            runCurrent()
            assertEquals(initialCabin, car.cabin.value)
            assertNull(runtime.state.value.hearing)
            // Nothing is tuned once the call is over.
            advanceTimeBy(30_000L)
            assertEquals(initialCabin, car.cabin.value)
        }
    }

    @Test
    fun `hardly heard with the fan as low as it goes, the call is made louder`() {
        val car = drivingCar()
        val hearing = hearingOf(car)
        runtimeTest(car, meeting = silence, hearing = hearing) { _, _ ->
            car.setCallActive(true)
            hearing.setAmbientNoise(AmbientNoise.High)
            car.setWiper(WiperSpeed.High)
            runCurrent()

            // Four moments for the fan, then the volume.
            advanceTimeBy(12_100L)
            assertEquals(CabinRanges().quietFanLevel, car.cabin.value.fanLevel)
            assertEquals(initialCabin.callVolume, car.cabin.value.callVolume)
            advanceTimeBy(3_000L)
            assertEquals(initialCabin.callVolume + 1, car.cabin.value.callVolume)

            car.setCallActive(false)
            runCurrent()
            assertEquals(initialCabin, car.cabin.value)
        }
    }

    @Test
    fun `what the driver changes by hand during the call is left alone and not put back`() {
        val car = drivingCar()
        val hearing = hearingOf(car)
        runtimeTest(car, meeting = silence, hearing = hearing) { _, runtime ->
            car.setCallActive(true)
            hearing.setAmbientNoise(AmbientNoise.High)
            advanceTimeBy(3_100L)
            assertEquals(5, car.cabin.value.fanLevel)

            // The driver turns the fan up: the simulated car carries it out like any request.
            car.apply(CabinChange(fanLevel = 7))
            runCurrent()
            assertEquals(setOf(CabinPart.Fan), runtime.state.value.tuning?.overridden)

            advanceTimeBy(30_000L)
            assertEquals(7, car.cabin.value.fanLevel)

            car.setCallActive(false)
            runCurrent()
            assertEquals(initialCabin.copy(fanLevel = 7), car.cabin.value)
        }
    }

    @Test
    fun `a sentence said once the call has been hardly heard for a whole tuning interval is marked as missed for the noise`() {
        val car = drivingCar()
        val hearing = hearingOf(car)
        // One sentence a second; tuning moments at 3, 6 and 9 seconds into the call.
        runtimeTest(car, hearing = hearing) { _, runtime ->
            car.setCallActive(true)
            advanceTimeBy(1_500L)
            hearing.setAmbientNoise(AmbientNoise.High)
            // Hardly heard until the fan is down three steps, nine seconds into the call.
            advanceTimeBy(3_000L)
            assertEquals(Hearing.Bad, runtime.state.value.hearing?.hearing)
            // One moment has found it so: not yet.
            assertFalse(runtime.state.value.markingMissed)
            advanceTimeBy(3_000L)
            assertTrue(runtime.state.value.markingMissed)
            advanceTimeBy(3_000L)
            assertEquals(Hearing.Poor, runtime.state.value.hearing?.hearing)
            assertFalse(runtime.state.value.markingMissed)

            val notes = runtime.state.value.memos.single().notes
            assertEquals(10, notes.size)
            // Up to five seconds in, the noise had not lasted a whole interval yet. The sentence
            // at six seconds comes with the second moment, so it is left out of this check.
            assertEquals(List(5) { null }, notes.take(5).map { it.missed })
            assertEquals(List(3) { MissedReason.Noise }, notes.subList(6, 9).map { it.missed })
            assertNull(notes.last().missed)
        }
    }

    @Test
    fun `a sentence said to the end just before hanging up is still in the call's memo`() = scope.runTest {
        val car = drivingCar()
        val script = ScriptedMeetingSource(
            title = "meeting",
            materials = emptyList(),
            script = listOf(
                ScriptedMeetingSource.ScriptLine("A", "부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다", gist = null),
                ScriptedMeetingSource.ScriptLine("B", "다음 회의는 같은 시간에 이어서 하겠습니다", gist = null),
            ),
        )
        // Hears each one-second clip and gives its result a second and a half after the clip ends.
        val recognizer = object : SpeechRecognizer {
            var calls = 0
            override suspend fun isAvailable() = true
            override suspend fun transcribe(pcm: ByteArray, onSpeech: (Boolean) -> Unit): String? {
                calls++
                delay(pcm.size / 32L + 1_500L)
                return "heard $calls"
            }
        }
        val recorded = RecordedCall(script, { ByteArray(32_000) }, recognizer, pauseMillis = 100L, now = { "14:05" })
        val runtime = SyncDeskRuntime(
            gateway = car,
            callMonitor = recorded.monitor(car, backgroundScope),
            meeting = recorded,
            narrator = narrator,
            presetStore = presets,
            phrases = phrases,
        )
        try {
            car.setCallActive(true)
            // A: its clip 0 to 1 s, written at 2.5 s. B: its clip 2.6 to 3.6 s, its result due at 5.1 s.
            advanceTimeBy(4_000L)
            runCurrent()
            car.setCallActive(false)
            runCurrent()

            val state = runtime.state.value
            assertFalse(state.call.active)
            val memo = state.memos.single()
            assertEquals(listOf("heard 1", "다음 회의는 같은 시간에 이어서 하겠습니다"), memo.notes.map { it.text })
            assertEquals(memo.id, state.dialog?.memoId)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `the noise is counted in tuning moments on a call the cabin is not tuned for`() {
        val car = parkedCar()
        val hearing = hearingOf(car)
        runtimeTest(car, hearing = hearing) { _, runtime ->
            car.setCallActive(true)
            hearing.setAmbientNoise(AmbientNoise.High)
            car.setWiper(WiperSpeed.High)
            runCurrent()
            assertFalse(runtime.state.value.isTuning)
            assertEquals(Hearing.Bad, runtime.state.value.hearing?.hearing)
            assertFalse(runtime.state.value.markingMissed)

            advanceTimeBy(6_100L)
            assertEquals(2, runtime.state.value.badTicks)
            assertTrue(runtime.state.value.markingMissed)
            assertEquals(MissedReason.Noise, runtime.state.value.memos.single().notes.last().missed)
            assertNull(runtime.state.value.memos.single().notes.first().missed)
        }
    }

    @Test
    fun `after ten minutes on inside air, outside air is let in for a minute`() = runtimeTest(drivingCar(), meeting = silence) { car, runtime ->
        car.setCallActive(true)
        runCurrent()
        assertTrue(car.cabin.value.recirculation)

        advanceTimeBy(599_000L)
        assertTrue(car.cabin.value.recirculation)
        advanceTimeBy(2_000L)
        assertFalse(car.cabin.value.recirculation)
        assertTrue(car.cabin.value.airCleanerOn)

        advanceTimeBy(58_000L)
        assertFalse(car.cabin.value.recirculation)
        advanceTimeBy(2_000L)
        assertTrue(car.cabin.value.recirculation)

        // The next ten minutes count from here.
        advanceTimeBy(598_000L)
        assertTrue(car.cabin.value.recirculation)
        advanceTimeBy(2_000L)
        assertFalse(car.cabin.value.recirculation)

        car.setCallActive(false)
        runCurrent()
        assertEquals(initialCabin, car.cabin.value)
        // The timers end with the call.
        advanceTimeBy(700_000L)
        assertEquals(initialCabin, car.cabin.value)
        assertFalse(runtime.state.value.isTuning)
    }

    @Test
    fun `five minutes on inside air with the wipers on switch the defroster on until the call ends`() = runtimeTest(drivingCar(), meeting = silence) { car, _ ->
        car.setCallActive(true)
        car.setWiper(WiperSpeed.Low)
        runCurrent()

        advanceTimeBy(299_000L)
        assertFalse(car.cabin.value.defrosterOn)
        advanceTimeBy(2_000L)
        assertTrue(car.cabin.value.defrosterOn)

        car.setWiper(WiperSpeed.Off)
        advanceTimeBy(60_000L)
        assertTrue(car.cabin.value.defrosterOn)

        car.setCallActive(false)
        runCurrent()
        assertFalse(car.cabin.value.defrosterOn)
    }

    // endregion

    // region The call memo

    @Test
    fun `the whole call is written down and what is said in heavy rain is marked as missed`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        runCurrent()
        advanceTimeBy(1_500L)
        assertTrue(runtime.state.value.mode is SyncDeskMode.Quieting)

        car.setWiper(WiperSpeed.High)
        advanceTimeBy(2_000L)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.HeavyRain, 2), runtime.state.value.mode)

        // The road clears: nothing is read during the call, and the call goes on being written down.
        car.setWiper(WiperSpeed.Off)
        runCurrent()
        assertTrue(runtime.state.value.mode is SyncDeskMode.Quieting)
        advanceTimeBy(1_000L)

        val memo = runtime.state.value.memos.single()
        assertEquals(listOf("text 1", "text 2", "text 3", "text 4"), memo.notes.map { it.text })
        assertEquals(listOf(null, MissedReason.Driving, MissedReason.Driving, null), memo.notes.map { it.missed })
        assertEquals(Triple("meeting", "2026-11-15", "14:05"), Triple(memo.title, memo.date, memo.startedAt))
        assertEquals(emptyList<String>(), narrator.spoken)
    }

    @Test
    fun `what is said just after a turn still counts as missed`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        car.setTurnSignal(TurnSignal.Left)
        runCurrent()
        advanceTimeBy(1_500L)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningLeft, 1), runtime.state.value.mode)

        car.setTurnSignal(TurnSignal.Off)
        advanceTimeBy(3_900L)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningLeft, 5), runtime.state.value.mode)

        advanceTimeBy(200L)
        assertTrue(runtime.state.value.mode is SyncDeskMode.Quieting)

        advanceTimeBy(1_000L)
        val memo = runtime.state.value.memos.single()
        assertEquals(6, memo.notes.size)
        assertEquals(5, memo.missed.size)
    }

    @Test
    fun `what the driver promises goes into the memo between the sentences around it`() {
        val withPromise = object : MeetingSource by meeting {
            override fun promises(): Flow<MeetingNote> = flow {
                delay(1_500L)
                emit(MeetingNote(0, "14:06", "me", "I will send the notice this afternoon", gist = "Send the notice this afternoon", byDriver = true))
            }
        }
        runtimeTest(drivingCar(), meeting = withPromise) { car, runtime ->
            car.setCallActive(true)
            runCurrent()
            advanceTimeBy(2_500L)

            val memo = runtime.state.value.memos.single()
            assertEquals(listOf(false, true, false), memo.notes.map { it.byDriver })
            assertEquals(listOf("gist 1", "Send the notice this afternoon", "gist 2"), memo.summary)
        }
    }

    @Test
    fun `memos are loaded at start and saved with every sentence and once explained`() {
        val earlier = CallMemo(id = 4, title = "earlier", notes = listOf(MeetingNote(1, "09:00", "speaker", "old", "old gist")))
        val store = RememberedMemos(listOf(earlier))
        runtimeTest(drivingCar(), memoStore = store) { car, runtime ->
            runCurrent()
            assertEquals(listOf(earlier), runtime.state.value.memos)
            assertEquals(emptyList<List<CallMemo>>(), store.saved)

            car.setCallActive(true)
            advanceTimeBy(1_100L)
            assertEquals(listOf(4, 5), store.saved.last().map { it.id })
            assertEquals(1, store.saved.last().last().notes.size)
            advanceTimeBy(1_000L)
            assertEquals(2, store.saved.last().last().notes.size)

            // Hung up, offered, agreed, explained: the memo is saved as heard.
            car.setCallActive(false)
            advanceTimeBy(1_100L)
            replies.sendReply(DriverReply.Yes)
            advanceTimeBy(3_100L)
            assertEquals(listOf(false, true), store.saved.last().map { it.explained })
            assertEquals(store.saved.last(), runtime(car, memoStore = store).also { it.close() }.state.value.memos)
        }
    }

    // endregion

    // region The talk after a call

    /** A call of five sentences, the last two in heavy rain, ended on a clear road. */
    private suspend fun TestScope.callWithAMissedPart(car: SimulatedCar) {
        car.setCallActive(true)
        runCurrent()
        advanceTimeBy(3_500L)
        car.setWiper(WiperSpeed.High)
        advanceTimeBy(2_000L)
        car.setWiper(WiperSpeed.Off)
        runCurrent()
        car.setCallActive(false)
        runCurrent()
    }

    @Test
    fun `after the call the car offers, explains, asks and answers from the memo`() = runtimeTest(drivingCar()) { car, runtime ->
        callWithAMissedPart(car)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Speaking), runtime.state.value.mode)
        assertEquals(listOf("offer"), narrator.spoken)

        // Said over the car's own voice: not heard.
        replies.sendReply(DriverReply.Yes)
        runCurrent()
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Speaking), runtime.state.value.mode)

        advanceTimeBy(1_100L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)

        replies.sendReply(DriverReply.Yes)
        runCurrent()
        assertEquals(conversing(DialogStep.Explain, DialogPhase.Speaking), runtime.state.value.mode)

        // The key summary, then what was missed and not already said: seven lines at a second each.
        advanceTimeBy(7_100L)
        assertEquals(
            listOf("offer", "key points", "gist 1", "gist 2", "gist 3", "missed", "gist 4", "gist 5", "ask"),
            narrator.spoken,
        )
        assertTrue(runtime.state.value.memos.single().explained)
        advanceTimeBy(1_000L)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), runtime.state.value.mode)

        replies.sendReply(DriverReply.Other("when is the delivery"))
        runCurrent()
        assertEquals(conversing(DialogStep.Answer, DialogPhase.Speaking), runtime.state.value.mode)
        advanceTimeBy(1_100L)
        assertEquals("November 28", narrator.spoken.last())
        // It listens for the next question without asking again.
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), runtime.state.value.mode)

        replies.sendReply(DriverReply.Other("who pays for it"))
        advanceTimeBy(1_100L)
        assertEquals("not mentioned", narrator.spoken.last())
        assertEquals(1, narrator.spoken.count { it == "ask" })

        // Eight seconds without another question end the talk; the memo stays.
        advanceTimeBy(7_800L)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), runtime.state.value.mode)
        advanceTimeBy(200L)
        assertEquals(SyncDeskMode.Standby, runtime.state.value.mode)
        assertEquals(1, runtime.state.value.memos.size)
    }

    @Test
    fun `an offer left unanswered for eight seconds ends the talk`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setCallActive(false)
        advanceTimeBy(1_100L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)

        advanceTimeBy(7_800L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)
        advanceTimeBy(200L)
        assertEquals(SyncDeskMode.Standby, runtime.state.value.mode)
        assertFalse(runtime.state.value.memos.single().explained)
        assertEquals(listOf("offer"), narrator.spoken)
    }

    @Test
    fun `no and stop end the talk at once`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setCallActive(false)
        advanceTimeBy(1_100L)
        replies.sendReply(DriverReply.No)
        runCurrent()
        assertEquals(SyncDeskMode.Standby, runtime.state.value.mode)

        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setCallActive(false)
        advanceTimeBy(1_100L)
        replies.sendReply(DriverReply.Yes)
        advanceTimeBy(4_200L)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), runtime.state.value.mode)
        replies.sendReply(DriverReply.Stop)
        runCurrent()
        assertEquals(SyncDeskMode.Standby, runtime.state.value.mode)
    }

    @Test
    fun `a call that ends on a demanding road is offered once the road clears`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setWiper(WiperSpeed.High)
        car.setCallActive(false)
        runCurrent()

        advanceTimeBy(10_000L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Waiting), runtime.state.value.mode)
        assertEquals(emptyList<String>(), narrator.spoken)

        car.setWiper(WiperSpeed.Off)
        advanceTimeBy(1_100L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)
        assertEquals(listOf("offer"), narrator.spoken)
    }

    @Test
    fun `a demanding road stops the voice and the listening, and the step is said again from the start`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setCallActive(false)
        // Half a second into the offer.
        advanceTimeBy(500L)
        car.setWiper(WiperSpeed.High)
        advanceTimeBy(20_000L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Waiting), runtime.state.value.mode)
        assertEquals(listOf("offer"), narrator.spoken)

        car.setWiper(WiperSpeed.Off)
        advanceTimeBy(1_100L)
        assertEquals(listOf("offer", "offer"), narrator.spoken)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)

        // While the car waits for a reply: it stops listening, and waiting does not count as silence.
        car.setTurnSignal(TurnSignal.Left)
        runCurrent()
        replies.sendReply(DriverReply.Yes)
        advanceTimeBy(20_000L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Waiting), runtime.state.value.mode)

        car.setTurnSignal(TurnSignal.Off)
        advanceTimeBy(1_100L)
        assertEquals(listOf("offer", "offer", "offer"), narrator.spoken)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)
    }

    @Test
    fun `a new call and parking each end the talk`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        advanceTimeBy(1_500L)
        car.setCallActive(false)
        advanceTimeBy(500L)
        car.setCallActive(true)
        runCurrent()
        assertNull(runtime.state.value.dialog)

        advanceTimeBy(1_500L)
        car.setCallActive(false)
        advanceTimeBy(1_100L)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), runtime.state.value.mode)
        car.setGear(Gear.Park)
        runCurrent()
        assertNull(runtime.state.value.dialog)
        assertTrue(runtime.state.value.mode is SyncDeskMode.Desk)
        // Both calls are there to read once parked.
        assertEquals(2, runtime.state.value.memos.size)
    }

    @Test
    fun `a call on which nothing was said ends quietly`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        runCurrent()
        car.setCallActive(false)
        runCurrent()

        assertEquals(SyncDeskMode.Standby, runtime.state.value.mode)
        assertEquals(emptyList<CallMemo>(), runtime.state.value.memos)
    }

    // endregion

    // region Sounds

    @Test
    fun `the end of a missed stretch is marked with one sound, and only when something was marked`() = runtimeTest(drivingCar()) { car, _ ->
        car.setCallActive(true)
        car.setWiper(WiperSpeed.High)
        runCurrent()
        // The start of the stretch makes no sound.
        assertEquals(emptyList<Pair<Cue, Long>>(), chimes.heard)
        advanceTimeBy(1_500L)
        car.setWiper(WiperSpeed.Off)
        runCurrent()
        assertEquals(listOf(Cue.MissedPartMarked to 1_500L), chimes.heard)

        // A stretch in which nobody spoke marks nothing.
        car.setWiper(WiperSpeed.High)
        runCurrent()
        car.setWiper(WiperSpeed.Off)
        runCurrent()
        assertEquals(1, chimes.heard.size)

        // A stretch ended by hanging up is followed by the talk after the call, not by the sound.
        car.setWiper(WiperSpeed.High)
        advanceTimeBy(1_000L)
        car.setCallActive(false)
        runCurrent()
        assertEquals(1, chimes.heard.size)
    }

    @Test
    fun `a sound waits while the driver talks, and none is made with sounds turned off`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        car.setDriverSpeaking(true)
        car.setWiper(WiperSpeed.High)
        runCurrent()
        advanceTimeBy(1_500L)
        car.setWiper(WiperSpeed.Off)
        advanceTimeBy(1_500L)
        assertEquals(emptyList<Pair<Cue, Long>>(), chimes.heard)

        car.setDriverSpeaking(false)
        runCurrent()
        assertEquals(listOf(Cue.MissedPartMarked to 3_000L), chimes.heard)

        runtime.onEvent(SyncDeskEvent.ChimesToggled(false))
        car.setWiper(WiperSpeed.High)
        advanceTimeBy(1_100L)
        car.setWiper(WiperSpeed.Off)
        runCurrent()
        assertEquals(1, chimes.heard.size)
    }

    // endregion

    @Test
    fun `a refused command is shown for six seconds once the road is clear`() = scope.runTest {
        val car = drivingCar()
        val refusals = MutableSharedFlow<CabinPart>(extraBufferCapacity = 1)
        val gateway = object : VehicleGateway by car {
            override val failures = refusals
        }
        val runtime = SyncDeskRuntime(
            gateway = gateway,
            callMonitor = car,
            meeting = meeting,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            refusalMillis = 6_000L,
        )
        try {
            car.setWiper(WiperSpeed.High)
            runCurrent()
            refusals.emit(CabinPart.Window)
            runCurrent()
            // Waits out the demanding stretch.
            advanceTimeBy(10_000L)
            assertEquals(CabinPart.Window, runtime.state.value.refusal)

            car.setWiper(WiperSpeed.Off)
            advanceTimeBy(5_900L)
            assertEquals(CabinPart.Window, runtime.state.value.shownRefusal)
            advanceTimeBy(200L)
            assertEquals(null, runtime.state.value.refusal)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `agreeing on first use is saved and speech that cannot be used is reported`() = scope.runTest {
        val saved = mutableListOf<AppSettings>()
        val settings = object : SettingsStore {
            override fun load() = saved.lastOrNull() ?: AppSettings(consented = false)
            override fun save(settings: AppSettings) {
                saved += settings
            }
        }
        val runtime = SyncDeskRuntime(
            gateway = parkedCar(),
            callMonitor = SimulatedCar(),
            meeting = meeting,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            settingsStore = settings,
            speechAvailable = { false },
        )
        try {
            runCurrent()
            assertTrue(runtime.state.value.asksConsent)
            assertFalse(runtime.state.value.speechAvailable)

            runtime.onEvent(SyncDeskEvent.ConsentGiven)
            runCurrent()
            assertEquals(true, saved.last().consented)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `call time advances once a second during a call`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setCallActive(true)
        runCurrent()

        advanceTimeBy(3_100L)

        assertEquals(3, runtime.state.value.call.elapsedSeconds)
    }

    @Test
    fun `office mode moves one actuator group at a time, wheel first, and puts it back wheel last`() = runtimeTest(parkedCar()) { car, runtime ->
        runCurrent()

        runtime.onEvent(SyncDeskEvent.OfficeRequested)
        runCurrent()
        assertEquals(OfficePhase.Applying, runtime.state.value.office.phase)
        assertEquals(initialCabin, car.cabin.value)

        // The wheel arrives first; the seat has not moved yet.
        advanceTimeBy(1_300L)
        assertEquals(setOf(OfficeStep.SteeringWheel), runtime.state.value.office.completed)
        assertEquals(OfficePreset().wheel, car.cabin.value.wheel)
        assertEquals(initialCabin.seatSlide, car.cabin.value.seatSlide)

        advanceTimeBy(10_000L)
        assertEquals(OfficePhase.On, runtime.state.value.office.phase)
        assertTrue(car.cabin.value.readingLightOn)
        assertEquals(CabinLimits.OFFICE_AMBIENT, car.cabin.value.ambientPercent)
        assertEquals(CabinLimits.OFFICE_SEAT_SLIDE, car.cabin.value.seatSlide)

        runtime.onEvent(SyncDeskEvent.OfficeRestoreRequested)
        advanceTimeBy(6_500L)
        // Everything but the wheel is back; the wheel goes last.
        assertEquals(initialCabin.seatSlide, car.cabin.value.seatSlide)
        assertEquals(OfficePreset().wheel, car.cabin.value.wheel)
        advanceTimeBy(2_000L)
        assertEquals(OfficePhase.Off, runtime.state.value.office.phase)
        assertEquals(initialCabin, car.cabin.value)
    }

    @Test
    fun `stopping in the middle of a step leaves it where it is and carrying on finishes the sequence`() = runtimeTest(parkedCar()) { car, runtime ->
        runCurrent()
        runtime.onEvent(SyncDeskEvent.OfficeRequested)
        advanceTimeBy(1_300L)
        // The seat is moving (it takes longer than this).
        advanceTimeBy(500L)
        runtime.onEvent(SyncDeskEvent.OfficePauseRequested)
        advanceTimeBy(10_000L)

        assertTrue(runtime.state.value.office.paused)
        assertEquals(setOf(OfficeStep.SteeringWheel), runtime.state.value.office.completed)
        assertEquals(initialCabin.seatSlide, car.cabin.value.seatSlide)

        runtime.onEvent(SyncDeskEvent.OfficeResumeRequested)
        advanceTimeBy(10_000L)
        assertEquals(OfficePhase.On, runtime.state.value.office.phase)
    }

    @Test
    fun `a changed office arrangement is saved and used by the next runtime`() = runtimeTest(parkedCar()) { car, runtime ->
        runCurrent()
        val own = OfficePreset(seatRecline = 60, readingLightOn = true, ambientPercent = 50, seatSlide = 90, shadePercent = 70)

        runtime.onEvent(SyncDeskEvent.PresetChanged(own))
        runCurrent()

        assertEquals(own, presets.saved)
        assertEquals(own, runtime(car).state.value.preset)
    }

    @Test
    fun `office mode moves the cabin to the saved arrangement`() = runtimeTest(parkedCar()) { car, runtime ->
        runCurrent()
        runtime.onEvent(SyncDeskEvent.PresetChanged(OfficePreset(seatRecline = 60, ambientPercent = 50)))
        runtime.onEvent(SyncDeskEvent.OfficeRequested)
        advanceTimeBy(15_000L)

        assertEquals(OfficePhase.On, runtime.state.value.office.phase)
        assertEquals(60, car.cabin.value.seatRecline)
        assertEquals(50, car.cabin.value.ambientPercent)
    }

    @Test
    fun `handing the wheel back mid-drive puts the cabin back`() = runtimeTest(drivingCar()) { car, runtime ->
        car.setAutonomous(true)
        runCurrent()
        runtime.onEvent(SyncDeskEvent.OfficeRequested)
        advanceTimeBy(15_000L)
        assertEquals(OfficePhase.On, runtime.state.value.office.phase)

        car.setAutonomous(false)
        advanceTimeBy(15_000L)

        assertEquals(OfficePhase.Off, runtime.state.value.office.phase)
        // The doors locked for working stay locked while driving.
        assertEquals(initialCabin, car.cabin.value.copy(doorsLocked = false))
    }

    @Test
    fun `driving off in the middle of the office sequence leaves the cabin as it was`() = runtimeTest(parkedCar()) { car, runtime ->
        runCurrent()

        runtime.onEvent(SyncDeskEvent.OfficeRequested)
        advanceTimeBy(1_000L)
        car.setGear(Gear.Drive)
        advanceTimeBy(15_000L)

        assertEquals(OfficePhase.Off, runtime.state.value.office.phase)
        assertEquals(initialCabin, car.cabin.value)
        assertEquals(initialCabin, runtime.state.value.cabin)
    }

    @Test
    fun `the USB-C port charges the laptop once its step is reached, follows the battery and is given back`() = scope.runTest {
        val car = parkedCar()
        val usb = SimulatedUsbPower()
        usb.setDeviceWatts(140)
        val runtime = SyncDeskRuntime(
            gateway = car,
            callMonitor = car,
            meeting = silence,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            usbPower = usb,
        )
        try {
            runCurrent()
            assertFalse(usb.port.value.on)
            runtime.onEvent(SyncDeskEvent.OfficeRequested)
            advanceTimeBy(15_000L)
            assertEquals(140, usb.port.value.suppliedWatts)
            assertEquals(usb.port.value, runtime.state.value.usb)

            car.setBatteryPercent(25)
            runCurrent()
            assertEquals(BatteryGuard.REDUCED_WATTS, usb.port.value.suppliedWatts)
            car.setBatteryPercent(15)
            runCurrent()
            assertFalse(usb.port.value.on)

            car.setBatteryPercent(80)
            runtime.onEvent(SyncDeskEvent.OfficeRestoreRequested)
            advanceTimeBy(15_000L)
            assertEquals(OfficePhase.Off, runtime.state.value.office.phase)
            assertFalse(usb.port.value.on)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `parking with something of the drive to show asks for the screen, and only then`() = scope.runTest {
        val car = drivingCar()
        val phone = SimulatedContacts(callers = listOf("Kim"), senders = listOf("Park"))
        val runtime = SyncDeskRuntime(
            gateway = car,
            callMonitor = car,
            meeting = silence,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            contacts = phone,
        )
        val asked = mutableListOf<Long>()
        val listening = launch { runtime.parkedWithDigest.collect { asked += testScheduler.currentTime } }
        try {
            runCurrent()
            car.setGear(Gear.Park)
            runCurrent()
            assertEquals(0, asked.size)

            car.setGear(Gear.Drive)
            phone.addMissedCall()
            phone.addMessage()
            runCurrent()
            // Nothing of it is shown while driving.
            assertNull(runtime.state.value.contactDigest)
            car.setGear(Gear.Park)
            runCurrent()
            assertEquals(1, asked.size)
            assertEquals(1, runtime.state.value.contactDigest?.missedCalls?.count)

            phone.markAllSeen()
            runCurrent()
            assertNull(runtime.state.value.contactDigest)
        } finally {
            listening.cancel()
            runtime.close()
        }
    }

    @Test
    fun `a new run counts on from the drives its saved memos came from`() = scope.runTest {
        val saved = RememberedMemos(listOf(CallMemo(id = 1, trip = 0), CallMemo(id = 2, trip = 3)))
        val runtime = runtime(parkedCar(), memoStore = saved)
        try {
            assertEquals(4, runtime.state.value.trip)
            assertTrue(runtime.state.value.tripMemos.isEmpty())
            assertEquals(listOf(2, 1), runtime.state.value.earlierMemos.map { it.id })
        } finally {
            runtime.close()
        }
    }

    /** A vehicle that, like Pleos Connect, says Park until it is linked; the test links it. */
    private fun linkingRuntime(car: SimulatedCar, linkFlow: MutableStateFlow<VehicleLink>, memos: RememberedMemos) = SyncDeskRuntime(
        gateway = object : VehicleGateway by car {
            override val link: StateFlow<VehicleLink> = linkFlow
        },
        callMonitor = car,
        meeting = silence,
        narrator = SimulatedNarrator(millisPerLine = 1_000L),
        presetStore = presets,
        memoStore = memos,
    )

    @Test
    fun `a run started in the middle of a drive carries on with that drive and its calls`() = scope.runTest {
        val memos = RememberedMemos(listOf(CallMemo(id = 1, trip = 4), CallMemo(id = 2, trip = 5)), trip = SavedTrip(5, parked = false))
        val car = SimulatedCar(VehicleSignals(gear = Gear.Park), initialCabin)
        val linkFlow = MutableStateFlow(VehicleLink(pending = true))
        val runtime = linkingRuntime(car, linkFlow, memos)
        try {
            runCurrent()
            assertEquals(5, runtime.state.value.trip)
            // The vehicle links and reports the gear it was in all along.
            car.setGear(Gear.Drive)
            linkFlow.value = VehicleLink(live = setOf(VehicleFeature.Gear), pending = false)
            runCurrent()
            assertEquals(5, runtime.state.value.trip)
            assertEquals(listOf(2), runtime.state.value.tripMemos.map { it.id })
            assertEquals(SavedTrip(5, parked = false), memos.trip)

            // From then on a stop and a new start make a new drive, as always.
            car.setGear(Gear.Park)
            runCurrent()
            assertEquals(SavedTrip(5, parked = true), memos.trip)
            car.setGear(Gear.Drive)
            runCurrent()
            assertEquals(6, runtime.state.value.trip)
            assertEquals(SavedTrip(6, parked = false), memos.trip)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `a run started parked counts the next start as a new drive`() = scope.runTest {
        val memos = RememberedMemos(listOf(CallMemo(id = 1, trip = 5)), trip = SavedTrip(5, parked = true))
        val car = SimulatedCar(VehicleSignals(gear = Gear.Park), initialCabin)
        val linkFlow = MutableStateFlow(VehicleLink(pending = true))
        val runtime = linkingRuntime(car, linkFlow, memos)
        try {
            runCurrent()
            // Still parked where the last run stopped: that drive's calls are still this stop's.
            assertEquals(listOf(1), runtime.state.value.tripMemos.map { it.id })
            car.setGear(Gear.Drive)
            linkFlow.value = VehicleLink(live = setOf(VehicleFeature.Gear), pending = false)
            runCurrent()
            assertEquals(6, runtime.state.value.trip)
            assertTrue(runtime.state.value.tripMemos.isEmpty())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `a run started mid-drive whose car was parked meanwhile still counts the next start`() = scope.runTest {
        val memos = RememberedMemos(listOf(CallMemo(id = 1, trip = 5)), trip = SavedTrip(5, parked = false))
        val car = SimulatedCar(VehicleSignals(gear = Gear.Park), initialCabin)
        val linkFlow = MutableStateFlow(VehicleLink(pending = true))
        val runtime = linkingRuntime(car, linkFlow, memos)
        try {
            runCurrent()
            // Linked: the car really is in Park.
            linkFlow.value = VehicleLink(live = setOf(VehicleFeature.Gear), pending = false)
            runCurrent()
            assertEquals(5, runtime.state.value.trip)
            car.setGear(Gear.Drive)
            runCurrent()
            assertEquals(6, runtime.state.value.trip)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `a cabin left tuned by a stopped app is put back and the note is cleared`() = scope.runTest {
        val left = CabinChange(
            fanLevel = 6, windowOpenPercent = 30, recirculation = false, airDirection = AirDirection.Face,
            airCleanerOn = false, callVolume = 3,
        )
        val saved = mutableListOf<CabinChange?>()
        val store = object : QuietStore {
            override fun load() = left
            override fun save(restore: CabinChange?) {
                saved += restore
            }
        }
        val found = CabinState(
            fanLevel = 2, windowOpenPercent = 0, recirculation = true, airDirection = AirDirection.Feet,
            airCleanerOn = true, callVolume = 5,
        )
        val car = SimulatedCar(VehicleSignals(gear = Gear.Drive), found)
        val hearing = hearingOf(car)
        val runtime = SyncDeskRuntime(
            gateway = car,
            callMonitor = car,
            meeting = silence,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            quietStore = store,
            hearing = hearing,
            vehicleLinkTimeoutMillis = 2_000L,
        )
        try {
            runCurrent()
            // Nothing is sent before the vehicle is linked (here: the wait runs out).
            assertEquals(found, car.cabin.value)
            advanceTimeBy(2_100L)
            runCurrent()
            assertEquals(initialCabin, car.cabin.value)
            assertEquals(listOf<CabinChange?>(null), saved)

            // From then on, every part the app moves is written down with the value to go back to.
            car.setCallActive(true)
            runCurrent()
            assertEquals(CabinChange(windowOpenPercent = 30, recirculation = false, airCleanerOn = false), saved.last())
            hearing.setAmbientNoise(AmbientNoise.High)
            advanceTimeBy(3_100L)
            assertEquals(
                CabinChange(
                    windowOpenPercent = 30, recirculation = false, airCleanerOn = false,
                    fanLevel = 6, airDirection = AirDirection.Face,
                ),
                saved.last(),
            )
            // A part the driver takes over is no longer the app's to put back.
            car.apply(CabinChange(windowOpenPercent = 40))
            runCurrent()
            assertNull(saved.last()?.windowOpenPercent)
            assertEquals(6, saved.last()?.fanLevel)

            car.setCallActive(false)
            runCurrent()
            assertEquals(null, saved.last())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `a cabin left tuned waits for every part it puts back to be live, not only the fan and the window`() = scope.runTest {
        val left = CabinChange(fanLevel = 6, windowOpenPercent = 30, airDirection = AirDirection.Face, temperatureC = 22f)
        val store = object : QuietStore {
            override fun load() = left
            override fun save(restore: CabinChange?) = Unit
        }
        val found = CabinState(fanLevel = 2, windowOpenPercent = 0, airDirection = AirDirection.Feet, temperatureC = 21f)
        val car = SimulatedCar(VehicleSignals(gear = Gear.Drive), found)
        val linkFlow = MutableStateFlow(VehicleLink(live = setOf(VehicleFeature.Fan, VehicleFeature.Window), pending = true))
        val linking = object : VehicleGateway by car {
            override val link: StateFlow<VehicleLink> = linkFlow
        }
        val runtime = SyncDeskRuntime(
            gateway = linking,
            callMonitor = car,
            meeting = silence,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            quietStore = store,
            vehicleLinkTimeoutMillis = 10_000L,
        )
        try {
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(found, car.cabin.value)

            linkFlow.value = VehicleLink(live = linkFlow.value.live + VehicleFeature.AirDirection, pending = true)
            runCurrent()
            assertEquals(found, car.cabin.value)

            // Linking ends without the set temperature: it stays simulated, and the restore goes out.
            linkFlow.value = linkFlow.value.copy(pending = false)
            runCurrent()
            assertEquals(found.copy(fanLevel = 6, windowOpenPercent = 30, airDirection = AirDirection.Face, temperatureC = 22f), car.cabin.value)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `a cabin left tuned in the middle of a call goes back when that call ends`() = scope.runTest {
        val left = CabinChange(fanLevel = 6, windowOpenPercent = 30, recirculation = false, airCleanerOn = false)
        val store = object : QuietStore {
            override fun load() = left
            override fun save(restore: CabinChange?) = Unit
        }
        val found = CabinState(fanLevel = 3, windowOpenPercent = 0, recirculation = true, airCleanerOn = true)
        val car = SimulatedCar(VehicleSignals(gear = Gear.Drive), found)
        car.setCallActive(true)
        val runtime = SyncDeskRuntime(
            gateway = car,
            callMonitor = car,
            meeting = silence,
            narrator = SimulatedNarrator(millisPerLine = 1_000L),
            presetStore = presets,
            quietStore = store,
            vehicleLinkTimeoutMillis = 2_000L,
        )
        try {
            advanceTimeBy(2_100L)
            // Still on the call: the cabin stays as it was found.
            assertEquals(found, car.cabin.value)
            assertEquals(left, runtime.state.value.tuning?.restore)

            car.setCallActive(false)
            runCurrent()
            assertEquals(initialCabin, car.cabin.value)
        } finally {
            runtime.close()
        }
    }
}
