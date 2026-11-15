package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDeskEngineTest {

    private val driving = SyncDeskState(
        signals = VehicleSignals(gear = Gear.Drive),
        cabin = CabinState(fanLevel = 6, windowOpenPercent = 30, recirculation = false),
    )
    private val parked = driving.copy(signals = VehicleSignals(gear = Gear.Park))

    /** Events handled with a vehicle that carries out every command at once. See [afterEvents]. */
    private fun SyncDeskState.after(vararg events: SyncDeskEvent): SyncDeskState = afterEvents(*events)

    private fun call(speaking: Boolean) =
        SyncDeskEvent.CallChanged(active = true, driverSpeaking = speaking, date = "2026-11-15", time = "14:00")

    private val callEnded = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)

    private fun signals(
        gear: Gear = Gear.Drive,
        wiper: WiperSpeed = WiperSpeed.Off,
        turn: TurnSignal = TurnSignal.Off,
    ) = SyncDeskEvent.SignalsChanged(VehicleSignals(gear, wiper, turn))

    private fun note(id: Int, gist: String? = "gist $id") =
        SyncDeskEvent.NoteCaptured(MeetingNote(id, "14:0$id", "speaker", "text $id", gist))

    /** The driver's own sentence with a promise in it, as the call source reports it. */
    private fun promise(task: String) =
        SyncDeskEvent.NoteCaptured(MeetingNote(0, "14:09", "me", "I will: $task", gist = task, byDriver = true))

    private fun reply(reply: DriverReply) = SyncDeskEvent.DriverReplied(reply)

    private val heavyRain get() = signals(wiper = WiperSpeed.High)

    // region Capture

    @Test
    fun `fast wipers during a call start a stretch that is marked as missed`() {
        val state = driving.after(call(speaking = false), heavyRain)

        assertEquals(SyncDeskMode.Capturing(RoadDemand.HeavyRain, lineCount = 0), state.mode)
    }

    @Test
    fun `a turn signal during a call starts a stretch that is marked as missed`() {
        val left = driving.after(call(speaking = false), signals(turn = TurnSignal.Left))
        val right = driving.after(call(speaking = false), signals(turn = TurnSignal.Right))

        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningLeft, lineCount = 0), left.mode)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningRight, lineCount = 0), right.mode)
    }

    @Test
    fun `turning the wheel well past a lane change starts a stretch that is marked as missed`() {
        val state = driving.after(
            call(speaking = false),
            SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive, steeringTurn = TurnSignal.Right)),
        )

        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningRight, lineCount = 0), state.mode)
    }

    @Test
    fun `every sentence of a call is written down and only those said on a demanding road are marked as missed`() {
        val state = driving.after(call(speaking = false), note(1), heavyRain, note(2), note(3), signals(), note(4))

        val memo = state.memos.single()
        assertEquals(listOf(1, 2, 3, 4), memo.notes.map { it.id })
        assertEquals(listOf(null, MissedReason.Driving, MissedReason.Driving, null), memo.notes.map { it.missed })
        assertEquals(listOf("text 2", "text 3"), memo.missed.map { it.text })
    }

    @Test
    fun `a sentence said just after a turn still counts as missed until the hold runs out`() {
        val turned = driving.after(call(speaking = false), signals(turn = TurnSignal.Left), note(1), signals())
        val held = turned.after(note(2))
        val done = held.after(SyncDeskEvent.DemandHoldExpired)
        val later = done.after(note(3))

        assertEquals(RoadDemand.TurningLeft, turned.heldDemand)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningLeft, lineCount = 2), held.mode)
        assertEquals(CaptureState.Idle, done.capture)
        assertNull(done.heldDemand)
        assertEquals(
            listOf(MissedReason.Driving, MissedReason.Driving, null),
            later.memos.single().notes.map { it.missed },
        )
    }

    @Test
    fun `a turn that comes back before the hold runs out carries on`() {
        val state = driving.after(
            call(speaking = false), signals(turn = TurnSignal.Left), note(1), signals(), signals(turn = TurnSignal.Right),
        )
        val late = state.after(SyncDeskEvent.DemandHoldExpired)

        assertNull(state.heldDemand)
        assertEquals(SyncDeskMode.Capturing(RoadDemand.TurningRight, lineCount = 1), late.mode)
    }

    @Test
    fun `heavy rain that clears ends the stretch at once, without a hold, and nothing is read during the call`() {
        val state = driving.after(call(speaking = false), heavyRain, note(1), signals())

        assertEquals(CaptureState.Idle, state.capture)
        assertNull(state.heldDemand)
        assertTrue(state.mode is SyncDeskMode.Quieting)
        assertNull(state.dialog)
    }

    @Test
    fun `slow wipers are not a demanding road`() {
        val state = driving.after(call(speaking = false), signals(wiper = WiperSpeed.Low), note(1))

        assertEquals(CaptureState.Idle, state.capture)
        assertNull(state.memos.single().notes.single().missed)
    }

    @Test
    fun `nothing is written down without a call`() {
        val state = driving.after(heavyRain, note(1))

        assertEquals(SyncDeskMode.Standby, state.mode)
        assertTrue(state.memos.isEmpty())
    }

    @Test
    fun `the stretch counts the sentences it marks`() {
        val state = driving.after(call(speaking = false), note(1), heavyRain, note(2), note(3))

        assertEquals(SyncDeskMode.Capturing(RoadDemand.HeavyRain, lineCount = 2), state.mode)
    }

    @Test
    fun `the driver's own promise is written down but never marked as missed or counted`() {
        val state = driving.after(call(speaking = false), heavyRain, note(1), promise("send the notice"))

        val memo = state.memos.single()
        assertEquals(listOf(MissedReason.Driving, null), memo.notes.map { it.missed })
        assertEquals(listOf(false, true), memo.notes.map { it.byDriver })
        assertEquals(listOf(1, 2), memo.notes.map { it.id })
        assertEquals(SyncDeskMode.Capturing(RoadDemand.HeavyRain, lineCount = 1), state.mode)
    }

    @Test
    fun `a call that is parked or driven by the car is written down with nothing marked as missed`() {
        val parkedCall = parked.after(call(speaking = false), note(1))
        val selfDriven = driving.after(
            call(speaking = false),
            SyncDeskEvent.SignalsChanged(VehicleSignals(wiper = WiperSpeed.High, autonomous = true)),
            note(1),
        )

        assertNull(parkedCall.memos.single().notes.single().missed)
        assertNull(selfDriven.memos.single().notes.single().missed)
    }

    @Test
    fun `the cabin stays tuned during a stretch that is marked as missed`() {
        val state = driving.after(call(speaking = false), heavyRain)

        assertTrue(state.mode is SyncDeskMode.Capturing)
        assertTrue(state.isTuning)
        assertEquals(CabinLimits.QUIET_WINDOW_OPEN, state.cabin.windowOpenPercent)
    }

    // endregion

    // region Call memos

    @Test
    fun `a memo begins with the first sentence and carries the call's title, day and start`() {
        val started = driving.copy(meeting = MeetingInfo("Weekly delivery")).after(call(speaking = false))
        val written = started.after(note(1))

        assertTrue(started.memos.isEmpty())
        assertNull(started.callMemo)
        assertEquals(
            CallMemo(
                id = 1,
                title = "Weekly delivery",
                date = "2026-11-15",
                startedAt = "14:00",
                notes = listOf(MeetingNote(1, "14:01", "speaker", "text 1", "gist 1")),
            ),
            written.callMemo,
        )
    }

    @Test
    fun `each call has its own memo and earlier memos stay`() {
        val first = driving.after(call(speaking = false), note(1), callEnded)
        val second = first.after(
            SyncDeskEvent.CallChanged(active = true, driverSpeaking = false, date = "2026-11-16", time = "09:30"),
            note(7),
            note(8),
        )

        assertEquals(listOf(1, 2), second.memos.map { it.id })
        assertEquals(listOf("text 1"), second.memos[0].notes.map { it.text })
        assertEquals(listOf("text 7", "text 8"), second.memos[1].notes.map { it.text })
        // A sentence takes its place in its own memo, whatever number the source gave it.
        assertEquals(listOf(1, 2), second.memos[1].notes.map { it.id })
        assertEquals("2026-11-16" to "09:30", second.memos[1].date to second.memos[1].startedAt)
        assertEquals(second.memos[1], second.latestMemo)
    }

    @Test
    fun `only the latest twenty memos are kept`() {
        val many = (1..SyncDeskEngine.MAX_MEMOS + 2).fold(driving) { state, _ ->
            state.after(call(speaking = false), note(1), callEnded)
        }

        assertEquals(SyncDeskEngine.MAX_MEMOS, many.memos.size)
        assertEquals((3..SyncDeskEngine.MAX_MEMOS + 2).toList(), many.memos.map { it.id })
    }

    @Test
    fun `the meeting names the memo of its call whichever is seen first`() {
        val before = driving.after(
            SyncDeskEvent.MeetingStarted(MeetingInfo("Line expansion")), call(speaking = false), note(1),
        )
        val after = driving.after(
            call(speaking = false), note(1), SyncDeskEvent.MeetingStarted(MeetingInfo("Line expansion")),
        )
        // A later call's meeting does not rename an earlier call's memo.
        val next = after.after(
            callEnded, call(speaking = false), SyncDeskEvent.MeetingStarted(MeetingInfo("Weekly delivery")), note(1),
        )

        assertEquals("Line expansion", before.memos.single().title)
        assertEquals("Line expansion", after.memos.single().title)
        assertEquals(listOf("Line expansion", "Weekly delivery"), next.memos.map { it.title })
    }

    @Test
    fun `a call on which nothing was said leaves no memo and no talk`() {
        val state = driving.after(call(speaking = false), callEnded)

        assertTrue(state.memos.isEmpty())
        assertNull(state.dialog)
        assertEquals(SyncDeskMode.Standby, state.mode)
    }

    // endregion

    // region The talk after a call

    private fun conversing(step: DialogStep, phase: DialogPhase) = SyncDeskMode.Conversing(step, phase)

    /** A call with one sentence has just ended on a clear road. */
    private val ended get() = driving.after(call(speaking = false), note(1), callEnded)

    /** The car has made its offer and waits for the reply. */
    private val offered get() = ended.after(SyncDeskEvent.DialogSpoken)

    /** The driver agreed, the car explained the memo and asked for questions. */
    private val asked
        get() = offered.after(reply(DriverReply.Yes), SyncDeskEvent.DialogSpoken, SyncDeskEvent.DialogSpoken)

    @Test
    fun `a call that ends with a memo is followed by the offer to sum it up`() {
        assertEquals(PostCallDialog(memoId = 1), ended.dialog)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Speaking), ended.mode)
        assertFalse(ended.listeningForReply)

        assertEquals(conversing(DialogStep.Offer, DialogPhase.Listening), offered.mode)
        assertTrue(offered.listeningForReply)
    }

    @Test
    fun `the offer waits for a demanding road to clear`() {
        val waiting = driving.after(call(speaking = false), heavyRain, note(1), callEnded)
        // Nothing the narrator reports counts while the car is supposed to be silent.
        val stillWaiting = waiting.after(SyncDeskEvent.DialogSpoken)
        val clear = stillWaiting.after(signals())

        assertEquals(conversing(DialogStep.Offer, DialogPhase.Waiting), waiting.mode)
        assertEquals(waiting.dialog, stillWaiting.dialog)
        assertEquals(conversing(DialogStep.Offer, DialogPhase.Speaking), clear.mode)
    }

    @Test
    fun `yes brings the explanation, which marks the memo as explained and leads to the question`() {
        val explaining = offered.after(reply(DriverReply.Yes))
        val asking = explaining.after(SyncDeskEvent.DialogSpoken)
        val listening = asking.after(SyncDeskEvent.DialogSpoken)

        assertEquals(conversing(DialogStep.Explain, DialogPhase.Speaking), explaining.mode)
        assertFalse(explaining.memos.single().explained)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Speaking), asking.mode)
        assertTrue(asking.memos.single().explained)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), listening.mode)
    }

    @Test
    fun `no, stop and silence each end the talk and leave the memo unexplained`() {
        listOf(
            reply(DriverReply.No), reply(DriverReply.Stop), SyncDeskEvent.DialogReplyTimedOut,
        ).forEach { ending ->
            val state = offered.after(ending)

            assertNull(ending.toString(), state.dialog)
            assertEquals(SyncDeskMode.Standby, state.mode)
            assertFalse(state.memos.single().explained)
        }
    }

    @Test
    fun `at the offer anything but yes, no or stop is not an answer`() {
        assertEquals(offered, offered.after(reply(DriverReply.Other("when is the delivery"))))
    }

    @Test
    fun `nothing the driver says is heard while the car speaks`() {
        val explaining = offered.after(reply(DriverReply.Yes))

        assertEquals(ended, ended.after(reply(DriverReply.Yes)))
        assertEquals(explaining, explaining.after(reply(DriverReply.Stop)))
        // Nor does silence end the talk while the car is the one speaking.
        assertEquals(explaining, explaining.after(SyncDeskEvent.DialogReplyTimedOut))
    }

    @Test
    fun `a question is answered and then the car listens again without asking again`() {
        val answering = asked.after(reply(DriverReply.Other("when is the delivery")))
        val listening = answering.after(SyncDeskEvent.DialogSpoken)
        val again = listening.after(reply(DriverReply.Other("who sends the notice")))

        assertEquals(PostCallDialog(1, DialogStep.Answer, question = "when is the delivery"), answering.dialog)
        assertEquals(conversing(DialogStep.Answer, DialogPhase.Speaking), answering.mode)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Listening), listening.mode)
        assertEquals("who sends the notice", again.dialog?.question)
    }

    @Test
    fun `after the question no, stop and silence end the talk, and yes alone is not a question`() {
        assertNull(asked.after(reply(DriverReply.No)).dialog)
        assertNull(asked.after(reply(DriverReply.Stop)).dialog)
        assertNull(asked.after(SyncDeskEvent.DialogReplyTimedOut).dialog)
        assertEquals(asked, asked.after(reply(DriverReply.Yes)))
    }

    @Test
    fun `a demanding road silences the car and the step is said again from the start when it clears`() {
        val explaining = offered.after(reply(DriverReply.Yes))
        val interrupted = explaining.after(heavyRain)
        val resumed = interrupted.after(SyncDeskEvent.DialogSpoken, signals())

        assertEquals(conversing(DialogStep.Explain, DialogPhase.Waiting), interrupted.mode)
        assertEquals(conversing(DialogStep.Explain, DialogPhase.Speaking), resumed.mode)
        assertFalse(resumed.memos.single().explained)
    }

    @Test
    fun `a demanding road while the car listens stops the listening and the question is said again`() {
        val interrupted = asked.after(signals(turn = TurnSignal.Left))
        val unheard = interrupted.after(reply(DriverReply.Other("when is the delivery")), SyncDeskEvent.DialogReplyTimedOut)
        val resumed = unheard.after(signals())

        assertFalse(interrupted.listeningForReply)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Waiting), unheard.mode)
        assertEquals(conversing(DialogStep.Ask, DialogPhase.Speaking), resumed.mode)
    }

    @Test
    fun `a new call ends the talk`() {
        val state = asked.after(call(speaking = false))

        assertNull(state.dialog)
        assertTrue(state.mode is SyncDeskMode.Quieting)
    }

    @Test
    fun `parking ends the talk and a call that ends while parked starts none`() {
        val parkedAfter = offered.after(signals(gear = Gear.Park))
        val endedParked = parked.after(call(speaking = false), note(1), callEnded)

        assertNull(parkedAfter.dialog)
        assertNull(endedParked.dialog)
        assertEquals(1, endedParked.memos.size)
    }

    @Test
    fun `the talk can be closed on the screen at any step`() {
        assertNull(ended.after(SyncDeskEvent.DialogClosed).dialog)
        assertNull(offered.after(reply(DriverReply.Yes), SyncDeskEvent.DialogClosed).dialog)
        assertNull(asked.after(SyncDeskEvent.DialogClosed).dialog)
    }

    // endregion

    // region Office

    @Test
    fun `office mode cannot start while driving`() {
        val state = driving.after(SyncDeskEvent.OfficeRequested)

        assertEquals(OfficePhase.Off, state.office.phase)
    }

    @Test
    fun `leaving Park ends office mode at once and restores the cabin`() {
        val state = parked.after(SyncDeskEvent.OfficeRequested)
            .afterOfficeStep(OfficeStep.SteeringWheel)
            .afterOfficeStep(OfficeStep.SeatPosition)
            .after(signals(gear = Gear.Drive))

        assertEquals(OfficePhase.Off, state.office.phase)
        assertEquals(parked.cabin, state.cabin)
        assertEquals(SyncDeskMode.Standby, state.mode)
    }

    @Test
    fun `a step reported after office mode ended is ignored`() {
        val state = parked.after(
            SyncDeskEvent.OfficeRequested,
            signals(gear = Gear.Drive),
            SyncDeskEvent.OfficeStepDone(OfficeStep.SteeringWheel),
        )

        assertEquals(OfficeState(), state.office)
        assertEquals(parked.cabin, state.cabin)
    }

    // endregion

    // region Commands

    @Test
    fun `the engine reports the cabin it is told about and never edits it`() {
        val state = SyncDeskEngine.reduce(driving, call(speaking = false))

        assertEquals(driving.cabin, state.cabin)
        assertEquals(
            CabinChange(windowOpenPercent = CabinLimits.QUIET_WINDOW_OPEN, recirculation = true, airCleanerOn = true),
            state.command?.change,
        )
    }

    @Test
    fun `an event that asks for nothing issues no command`() {
        val tunedAlready = driving.copy(cabin = CabinState(windowOpenPercent = 0, recirculation = true, airCleanerOn = true))

        val state = SyncDeskEngine.reduce(tunedAlready, call(speaking = false))

        assertTrue(state.isTuning)
        assertNull(state.command)
    }

    @Test
    fun `every new command has a new id`() {
        val lowered = SyncDeskEngine.reduce(driving, call(speaking = false))
        val restored = SyncDeskEngine.reduce(lowered, callEnded)

        assertEquals(1, lowered.command?.id)
        assertEquals(2, restored.command?.id)
    }

    @Test
    fun `changes asked for by one event travel as one command`() {
        // Driving off with office mode on while on a call: put the office back and tune the
        // cabin for the call, both from the single event of leaving Park.
        val officeOnAndCalling = SyncDeskEngine.reduce(parked.afterOfficeOn(), call(speaking = false))

        val state = SyncDeskEngine.reduce(officeOnAndCalling, signals(gear = Gear.Drive))

        val change = state.command?.change
        assertEquals((officeOnAndCalling.command?.id ?: 0) + 1, state.command?.id)
        assertEquals(parked.cabin.seatRecline, change?.seatRecline)
        assertEquals(parked.cabin.readingLightOn, change?.readingLightOn)
        assertEquals(CabinLimits.QUIET_WINDOW_OPEN, change?.windowOpenPercent)
        assertEquals(true, change?.recirculation)
    }

    // endregion

    // region Driving stages

    private fun selfDriving(on: Boolean = true, takeover: Boolean = false) = SyncDeskEvent.SignalsChanged(
        VehicleSignals(gear = Gear.Drive, autonomous = on, takeoverRequested = takeover),
    )

    @Test
    fun `the desk is closed while the driver drives and open when parked or self-driving`() {
        assertFalse(driving.signals.deskOpen)
        assertTrue(parked.signals.deskOpen)
        assertTrue(driving.after(selfDriving()).signals.deskOpen)
        assertEquals(DriveStage.Autonomous, driving.after(selfDriving()).signals.stage)
    }

    @Test
    fun `driving assistance alone does not open the desk`() {
        val assisted = driving.after(SyncDeskEvent.SignalsChanged(VehicleSignals(assistActive = true)))

        assertEquals(DriveStage.Driving, assisted.signals.stage)
        assertFalse(assisted.signals.deskOpen)
        assertEquals(OfficePhase.Off, assisted.after(SyncDeskEvent.OfficeRequested).office.phase)
    }

    @Test
    fun `office mode works while the car drives itself`() {
        val state = driving.after(selfDriving()).afterOfficeOn()

        assertEquals(OfficePhase.On, state.office.phase)
        assertTrue(state.mode is SyncDeskMode.Desk)
        assertTrue(state.cabin.readingLightOn)
    }

    @Test
    fun `taking the wheel back folds the desk and puts the cabin back`() {
        val office = driving.after(selfDriving()).afterOfficeOn()

        val state = office.after(selfDriving(on = true, takeover = true))

        assertEquals(DriveStage.Driving, state.signals.stage)
        assertEquals(OfficePhase.Off, state.office.phase)
        // Doors locked for working stay locked once the car moves; everything else is back.
        assertTrue(state.cabin.doorsLocked)
        assertEquals(driving.cabin, state.cabin.copy(doorsLocked = false))
        assertEquals(SyncDeskMode.TakeOver, state.mode)
    }

    @Test
    fun `a take-over during a call is written down like any demanding stretch`() {
        val state = driving.after(call(speaking = false), selfDriving(), selfDriving(on = true, takeover = true))

        assertEquals(SyncDeskMode.Capturing(RoadDemand.TakeOver, lineCount = 0), state.mode)
    }

    @Test
    fun `a forced end of driving assistance asks the driver to take over`() {
        val state = driving.after(
            SyncDeskEvent.SignalsChanged(VehicleSignals(assistActive = false, takeoverRequested = true)),
        )

        assertEquals(RoadDemand.TakeOver, state.roadDemand)
        assertEquals(SyncDeskMode.TakeOver, state.mode)
    }

    @Test
    fun `rain and turns are the car's business while it drives itself`() {
        val state = driving.after(
            call(speaking = false),
            SyncDeskEvent.SignalsChanged(
                VehicleSignals(wiper = WiperSpeed.High, turnSignal = TurnSignal.Left, autonomous = true),
            ),
        )

        assertEquals(RoadDemand.Clear, state.roadDemand)
        assertTrue(state.mode is SyncDeskMode.Desk)
        assertEquals(CaptureState.Idle, state.capture)
    }

    @Test
    fun `the cabin is still tuned for a call while the car drives itself`() {
        val state = driving.after(selfDriving(), call(speaking = false))

        assertTrue(state.isTuning)
        assertEquals(CabinLimits.QUIET_WINDOW_OPEN, state.cabin.windowOpenPercent)
    }

    // endregion

    // region Office preset

    private val ownPreset = OfficePreset(seatRecline = 30, readingLightOn = false, ambientPercent = 40)

    @Test
    fun `office mode sets the cabin to the driver's own arrangement`() {
        val state = parked.after(SyncDeskEvent.PresetChanged(ownPreset)).afterOfficeOn()

        assertEquals(30, state.cabin.seatRecline)
        assertFalse(state.cabin.readingLightOn)
        assertEquals(40, state.cabin.ambientPercent)
    }

    @Test
    fun `the arrangement cannot be changed while the driver drives`() {
        val state = driving.after(SyncDeskEvent.PresetChanged(ownPreset))

        assertEquals(OfficePreset(), state.preset)
    }

    @Test
    fun `values outside the offered range are brought back into it`() {
        val state = parked.after(
            SyncDeskEvent.PresetChanged(OfficePreset(seatRecline = 130, ambientPercent = -20)),
        )

        assertEquals(CabinLimits.SEAT_RECLINE_MAX, state.preset.seatRecline)
        assertEquals(0, state.preset.ambientPercent)
    }

    @Test
    fun `changing the arrangement while office mode is on moves only what changed`() {
        val on = parked.afterOfficeOn()

        val state = SyncDeskEngine.reduce(on, SyncDeskEvent.PresetChanged(on.preset.copy(ambientPercent = 30)))

        assertEquals(CabinChange(ambientPercent = 30), state.command?.change)
    }

    @Test
    fun `changing the arrangement while office mode is off moves nothing`() {
        val state = SyncDeskEngine.reduce(parked, SyncDeskEvent.PresetChanged(ownPreset))

        assertEquals(ownPreset, state.preset)
        assertNull(state.command)
    }

    // endregion
    @Test
    fun `call time counts only while a call is active and resets when it ends`() {
        val idle = driving.after(SyncDeskEvent.CallTick)
        val talking = driving.after(call(speaking = false), SyncDeskEvent.CallTick, SyncDeskEvent.CallTick)
        val ended = talking.after(callEnded)

        assertEquals(0, idle.call.elapsedSeconds)
        assertEquals(2, talking.call.elapsedSeconds)
        assertEquals(0, ended.call.elapsedSeconds)
    }

    // region First use and what could not be done

    private val notAgreed = driving.copy(consented = false)

    @Test
    fun beforeTheDriverAgreesNothingIsTunedWrittenDownOrTalkedAbout() {
        val state = notAgreed.after(
            call(speaking = true),
            signals(wiper = WiperSpeed.High),
            note(1),
            promise("send the notice"),
        )
        val ended = state.after(signals(), callEnded)

        assertEquals(notAgreed.cabin, state.cabin)
        assertEquals(CaptureState.Idle, state.capture)
        assertTrue(state.memos.isEmpty())
        assertNull(ended.dialog)
    }

    @Test
    fun agreeingInTheMiddleOfACallWritesDownTheRestOfIt() {
        val state = notAgreed.after(call(speaking = false), note(1), SyncDeskEvent.ConsentGiven, note(2))

        assertEquals(listOf("text 2"), state.memos.single().notes.map { it.text })
        assertEquals("14:00", state.memos.single().startedAt)
    }

    @Test
    fun onceAgreedTheCabinIsTunedForTheCallAsUsual() {
        val state = notAgreed.after(SyncDeskEvent.ConsentGiven, call(speaking = false))

        assertTrue(state.consented)
        assertTrue(state.isTuning)
    }

    @Test
    fun theNoticeIsShownOnlyWhileParkedAndLaterMeansAtTheNextStop() {
        val parkedNotAgreed = parked.copy(consented = false)
        val later = parkedNotAgreed.after(SyncDeskEvent.ConsentDeferred)
        val nextStop = later.after(signals(gear = Gear.Drive), signals(gear = Gear.Park))

        assertTrue(parkedNotAgreed.asksConsent)
        assertFalse(notAgreed.asksConsent)
        assertFalse(later.asksConsent)
        assertTrue(nextStop.asksConsent)
    }

    @Test
    fun aRefusalWaitsForAClearRoadBeforeItIsShown() {
        val refused = driving.after(SyncDeskEvent.CommandRefused(CabinPart.Window))
        val inRain = refused.after(signals(wiper = WiperSpeed.High))
        val cleared = refused.after(SyncDeskEvent.RefusalCleared)

        assertEquals(CabinPart.Window, refused.shownRefusal)
        assertNull(inRain.shownRefusal)
        assertNull(cleared.refusal)
    }

    // endregion

    // region Driving restrictions

    @Test
    fun officeModeAndItsSettingsWaitWhileThePlatformForbidsSetup() {
        val moving = listOf(
            SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive, autonomous = true)),
            SyncDeskEvent.UxLimitsChanged(UxLimits(noSetup = true)),
        ).fold(SyncDeskState(), SyncDeskEngine::reduce)

        assertEquals(OfficePhase.Off, SyncDeskEngine.reduce(moving, SyncDeskEvent.OfficeRequested).office.phase)
        val preset = OfficePreset(seatRecline = 30)
        assertEquals(OfficePreset(), SyncDeskEngine.reduce(moving, SyncDeskEvent.PresetChanged(preset)).preset)

        val lifted = SyncDeskEngine.reduce(moving, SyncDeskEvent.UxLimitsChanged(UxLimits()))
        assertEquals(OfficePhase.Applying, SyncDeskEngine.reduce(lifted, SyncDeskEvent.OfficeRequested).office.phase)
    }

    @Test
    fun puttingTheOfficeBackIsAllowedEvenUnderRestrictions() {
        val on = (
            listOf(SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Park)), SyncDeskEvent.OfficeRequested) +
                OfficeStep.entries.map { SyncDeskEvent.OfficeStepDone(it) } +
                SyncDeskEvent.UxLimitsChanged(UxLimits(noSetup = true))
            ).fold(SyncDeskState(), SyncDeskEngine::reduce)
        assertEquals(OfficePhase.On, on.office.phase)
        assertEquals(OfficePhase.Restoring, SyncDeskEngine.reduce(on, SyncDeskEvent.OfficeRestoreRequested).office.phase)
    }

    // endregion
}