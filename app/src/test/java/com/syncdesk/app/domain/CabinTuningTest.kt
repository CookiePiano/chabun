package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smart cabin control: what the engine does to the cabin for a call, and how it undoes it. */
class CabinTuningTest {

    private val cabin = CabinState(fanLevel = 6, windowOpenPercent = 30, recirculation = false)
    private val driving = SyncDeskState(signals = VehicleSignals(gear = Gear.Drive), cabin = cabin)
    private val parked = driving.copy(signals = VehicleSignals(gear = Gear.Park))

    private val answered = SyncDeskEvent.CallChanged(active = true, driverSpeaking = false, date = "2026-11-15", time = "14:00")
    private val hungUp = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)
    private val tick = SyncDeskEvent.TuneTick

    /** Events handled with a vehicle that carries out every command at once. See [afterEvents]. */
    private fun SyncDeskState.after(vararg events: SyncDeskEvent): SyncDeskState = afterEvents(*events)

    /** What one more [event] asks of the vehicle, or null when it asks for nothing. */
    private fun SyncDeskState.asks(event: SyncDeskEvent): CabinChange? {
        val next = SyncDeskEngine.reduce(this, event)
        return next.command?.takeIf { it.id != command?.id }?.change
    }

    /** The other party is heard with [margin] to spare over the cabin noise. */
    private fun heard(margin: Int) = SyncDeskEvent.HearingChanged(HearingLevels(voice = 60, noise = 60 - margin))

    private val well = heard(margin = HearingLevels.GOOD_MARGIN + HearingLevels.RAISE_HEADROOM)
    private val justWell = heard(margin = HearingLevels.GOOD_MARGIN)
    private val withEffort = heard(margin = HearingLevels.POOR_MARGIN)
    private val hardly = heard(margin = HearingLevels.POOR_MARGIN - 1)

    private fun reported(change: CabinState.() -> CabinState) = { state: SyncDeskState ->
        SyncDeskEngine.reduce(state, SyncDeskEvent.CabinReported(state.cabin.change()))
    }

    /** The driver's own hand: the vehicle reports a value nobody asked for. */
    private fun SyncDeskState.driverSets(change: CabinState.() -> CabinState): SyncDeskState = reported(change)(this)

    private fun signals(wiper: WiperSpeed = WiperSpeed.Off, gear: Gear = Gear.Drive) =
        SyncDeskEvent.SignalsChanged(VehicleSignals(gear = gear, wiper = wiper))

    private fun note(id: Int) = SyncDeskEvent.NoteCaptured(MeetingNote(id, "14:0$id", "speaker", "text $id", "gist $id"))

    // region How well the call is heard

    @Test
    fun `the margin of the voice over the noise decides between heard well, with effort and hardly`() {
        fun hearing(margin: Int) = HearingLevels(voice = 50 + margin, noise = 50).hearing

        assertEquals(10, HearingLevels.GOOD_MARGIN)
        assertEquals(4, HearingLevels.POOR_MARGIN)
        assertEquals(Hearing.Good, hearing(10))
        assertEquals(Hearing.Poor, hearing(9))
        assertEquals(Hearing.Poor, hearing(4))
        assertEquals(Hearing.Bad, hearing(3))
        assertEquals(Hearing.Bad, hearing(-5))
    }

    @Test
    fun `there is room for more fan only with the headroom of one step to spare`() {
        assertEquals(4, HearingLevels.RAISE_HEADROOM)
        assertFalse(HearingLevels(voice = 63, noise = 50).hasRoomForMoreFan)
        assertTrue(HearingLevels(voice = 64, noise = 50).hasRoomForMoreFan)
    }

    @Test
    fun `how well the call is heard is known only during the call`() {
        val before = driving.after(well)
        val during = driving.after(answered, withEffort)
        val after = during.after(hungUp)

        assertNull(before.hearing)
        assertEquals(Hearing.Poor, during.hearing?.hearing)
        assertEquals(SyncDeskMode.Quieting(during.tuning!!.restore, Hearing.Poor), during.mode)
        assertNull(after.hearing)
    }

    // endregion

    // region Answering

    @Test
    fun `answering a call while driving closes the window, keeps outside air out and cleans the air, and leaves the fan alone`() {
        val state = driving.after(answered)

        assertEquals(
            CabinChange(windowOpenPercent = 0, recirculation = true, airCleanerOn = true),
            driving.asks(answered),
        )
        assertEquals(cabin.copy(windowOpenPercent = 0, recirculation = true, airCleanerOn = true), state.cabin)
        assertEquals(cabin, state.tuning?.original)
        assertEquals(
            SyncDeskMode.Quieting(
                restore = CabinChange(windowOpenPercent = 30, recirculation = false, airCleanerOn = false),
                hearing = null,
            ),
            state.mode,
        )
    }

    @Test
    fun `the driver does not have to speak for it, and speaking changes nothing`() {
        val silent = driving.after(answered)
        val speaking = silent.after(SyncDeskEvent.CallChanged(active = true, driverSpeaking = true))

        assertEquals(silent.cabin, speaking.cabin)
        assertNull(silent.asks(SyncDeskEvent.CallChanged(active = true, driverSpeaking = true)))
    }

    @Test
    fun `what is already as the call needs it is neither asked for nor put back`() {
        val ready = driving.copy(cabin = cabin.copy(windowOpenPercent = 0, recirculation = true))
        val state = ready.after(answered)

        assertEquals(CabinChange(airCleanerOn = true), ready.asks(answered))
        assertEquals(CabinChange(airCleanerOn = false), state.asks(hungUp))
    }

    @Test
    fun `nothing is tuned while parked, with tuning switched off, or before the driver agreed`() {
        listOf(
            parked,
            driving.after(SyncDeskEvent.AutoQuietToggled(false)),
            driving.copy(consented = false),
        ).forEach { state ->
            val called = state.after(answered, hardly, tick, tick)

            assertEquals(state.cabin, called.cabin)
            assertFalse(called.isTuning)
            assertNull(called.command)
        }
    }

    @Test
    fun `seat position and the lights are left alone`() {
        val state = driving.after(answered, hardly, tick, tick, tick, tick, tick, tick, tick)

        assertEquals(cabin.seatRecline, state.cabin.seatRecline)
        assertEquals(cabin.readingLightOn, state.cabin.readingLightOn)
        assertEquals(cabin.ambientPercent, state.cabin.ambientPercent)
    }

    // endregion

    // region One step at each tuning moment

    @Test
    fun `nothing changes at a tuning moment while nothing measures the call or it is heard well`() {
        val unknown = driving.after(answered)
        val good = unknown.after(justWell)

        assertNull(unknown.asks(tick))
        assertNull(good.asks(tick))
        assertEquals(good.cabin, good.after(tick, tick, tick, tick).cabin)
    }

    @Test
    fun `a tuning moment outside a tuned call does nothing`() {
        assertEquals(driving, SyncDeskEngine.reduce(driving, tick))
        val parkedCall = parked.after(answered, hardly)
        assertNull(parkedCall.asks(tick))
    }

    @Test
    fun `heard with effort, the air first turns to the feet and then the fan goes down one step at a time`() {
        val poor = driving.after(answered, withEffort)

        assertEquals(CabinChange(airDirection = AirDirection.Feet), poor.asks(tick))
        val turned = poor.after(tick)
        assertEquals(CabinChange(fanLevel = 5), turned.asks(tick))
        val lowered = turned.after(tick)
        assertEquals(5, lowered.cabin.fanLevel)
        assertEquals(4, lowered.asks(tick)?.fanLevel)
    }

    @Test
    fun `air that is mixed between face and feet also turns to the feet`() {
        val mixed = driving.copy(cabin = cabin.copy(airDirection = AirDirection.FaceAndFeet)).after(answered, withEffort)

        assertEquals(CabinChange(airDirection = AirDirection.Feet), mixed.asks(tick))
    }

    @Test
    fun `air already at the feet or on the windshield stays where it is and the fan goes down at once`() {
        listOf(
            AirDirection.Feet, AirDirection.Windshield, AirDirection.FaceAndWindshield,
            AirDirection.FeetAndWindshield, AirDirection.All,
        ).forEach { direction ->
            val state = driving.copy(cabin = cabin.copy(airDirection = direction))

            assertEquals(direction.name, CabinChange(fanLevel = 5), state.after(answered, withEffort).asks(tick))
            assertEquals(direction.name, CabinChange(fanLevel = 5), state.after(answered, hardly).asks(tick))
        }
    }

    @Test
    fun `hardly heard, the fan goes down a step and the air turns to the feet with it`() {
        val bad = driving.after(answered, hardly)

        assertEquals(CabinChange(fanLevel = 5, airDirection = AirDirection.Feet), bad.asks(tick))
        assertEquals(CabinChange(fanLevel = 4), bad.after(tick).asks(tick)?.only(setOf(CabinPart.Fan, CabinPart.AirDirection)))
    }

    @Test
    fun `a step is taken only once the one before it has arrived`() {
        // The fan was asked down to 5 and the vehicle has not reported it yet.
        val asked = SyncDeskEngine.reduce(driving.after(answered, hardly), tick)
        assertEquals(6, asked.cabin.fanLevel)

        // Neither a second step nor, for want of one, a louder call.
        assertNull(asked.asks(tick))
        assertNull(asked.after(well).asks(tick))

        val arrived = SyncDeskEngine.reduce(
            asked, SyncDeskEvent.CabinReported(asked.cabin.copy(fanLevel = 5, airDirection = AirDirection.Feet)),
        )
        assertEquals(4, arrived.asks(tick)?.fanLevel)
        assertEquals(emptySet<CabinPart>(), arrived.tuning?.overridden)
    }

    @Test
    fun `the fan stops at a quarter of full speed and is never switched off`() {
        val floor = CabinRanges().quietFanLevel
        val poor = driving.after(answered, withEffort, tick, tick, tick, tick, tick, tick, tick, tick, tick, tick)
        val bad = driving.after(answered, hardly, tick, tick, tick, tick, tick, tick, tick, tick, tick, tick)

        assertEquals(2, floor)
        assertEquals(floor, poor.cabin.fanLevel)
        assertEquals(floor, bad.cabin.fanLevel)
        // Heard with effort and nothing left to lower: nothing more is done.
        assertNull(poor.asks(tick))
        assertEquals(cabin.callVolume, poor.cabin.callVolume)
    }

    @Test
    fun `the lowest fan level follows the range the vehicle reports and is at least one`() {
        assertEquals(1, CabinRanges(fanMin = 0, fanMax = 3).quietFanLevel)
        assertEquals(1, CabinRanges(fanMin = 1, fanMax = 3).quietFanLevel)
        assertEquals(3, CabinRanges(fanMin = 3, fanMax = 10).quietFanLevel)
        assertEquals(5, CabinRanges(fanMin = 0, fanMax = 20).quietFanLevel)

        val narrow = driving.copy(cabin = cabin.copy(fanLevel = 3))
            .after(SyncDeskEvent.RangesReported(CabinRanges(fanMin = 0, fanMax = 3)), answered, hardly, tick, tick, tick, tick, tick)
        assertEquals(1, narrow.cabin.fanLevel)
    }

    @Test
    fun `a fan that is already lower than the floor is not raised to it`() {
        val low = driving.copy(cabin = cabin.copy(fanLevel = 1)).after(answered, hardly, tick)

        assertEquals(1, low.cabin.fanLevel)
    }

    @Test
    fun `hardly heard with the fan as low as it goes, the call gets one step louder at a time, up to the loudest`() {
        val atFloor = driving.copy(cabin = cabin.copy(fanLevel = 2, airDirection = AirDirection.Feet)).after(answered, hardly)

        assertEquals(CabinChange(callVolume = 4), atFloor.asks(tick))
        val louder = atFloor.after(tick)
        assertEquals(CabinChange(callVolume = 5), louder.asks(tick))
        val loudest = louder.after(tick)
        assertEquals(CabinRanges().callVolumeMax, loudest.cabin.callVolume)
        assertNull(loudest.asks(tick))
    }

    @Test
    fun `the call is not made louder while it is only heard with effort`() {
        val state = driving.copy(cabin = cabin.copy(fanLevel = 2, airDirection = AirDirection.Feet))
            .after(answered, withEffort, tick, tick, tick)

        assertEquals(cabin.callVolume, state.cabin.callVolume)
    }

    @Test
    fun `with the climate system off, fan, air direction and temperature are not touched`() {
        val off = driving.copy(cabin = cabin.copy(fanLevel = 0))
        val poor = off.after(answered, withEffort)
        val bad = off.after(answered, hardly)

        assertNull(poor.asks(tick))
        // Hardly heard: the one thing left is the call volume.
        assertEquals(CabinChange(callVolume = 4), bad.asks(tick))
        val later = bad.after(tick, tick, tick)
        assertEquals(0, later.cabin.fanLevel)
        assertEquals(cabin.airDirection, later.cabin.airDirection)
        assertEquals(cabin.temperatureC, later.cabin.temperatureC)
    }

    // endregion

    // region Coming back up

    /** Heard with effort long enough to bring the fan from 6 down to 3 with the air at the feet. */
    private val lowered get() = driving.after(answered, withEffort, tick, tick, tick, tick)

    @Test
    fun `heard well three times in a row with room to spare, the fan comes back up one step`() {
        assertEquals(3, SyncDeskEngine.GOOD_TICKS_TO_RAISE)
        assertEquals(3, lowered.cabin.fanLevel)

        val twice = lowered.after(well, tick, tick)
        assertEquals(3, twice.cabin.fanLevel)
        assertEquals(CabinChange(fanLevel = 4), twice.asks(tick))

        // The next step takes three more.
        val raised = twice.after(tick)
        assertNull(raised.asks(tick))
        assertEquals(4, raised.after(tick, tick).cabin.fanLevel)
        assertEquals(5, raised.after(tick, tick, tick).cabin.fanLevel)
    }

    @Test
    fun `the fan stops where it was when the call was answered`() {
        val back = lowered.after(well, *Array(30) { tick })

        assertEquals(cabin.fanLevel, back.cabin.fanLevel)
        // The air stays at the feet until the call ends.
        assertEquals(AirDirection.Feet, back.cabin.airDirection)
    }

    @Test
    fun `heard well but without room to spare, the fan stays down`() {
        val state = lowered.after(justWell, *Array(12) { tick })

        assertEquals(3, state.cabin.fanLevel)
    }

    @Test
    fun `a moment of effort starts the count over`() {
        val interrupted = lowered.after(well, tick, tick, justWell, withEffort)
        // Nothing left to lower? There is: the fan is at 3, above the floor.
        val after = interrupted.after(tick, well, tick, tick)

        assertEquals(2, after.cabin.fanLevel)
        assertEquals(CabinChange(fanLevel = 3), after.asks(tick))
    }

    @Test
    fun `a fan the app never lowered is not raised`() {
        val state = driving.after(answered, well, *Array(9) { tick })

        assertEquals(cabin.fanLevel, state.cabin.fanLevel)
        assertNull(state.command?.change?.fanLevel)
    }

    @Test
    fun `a step up that has to be taken back at once is not tried again, so the fan does not swing up and down`() {
        assertEquals(3, SyncDeskEngine.BOUNCE_TICKS)
        val raised = lowered.after(well, tick, tick, tick)
        assertEquals(4, raised.cabin.fanLevel)

        // With the fan a step higher the call is heard with effort again: back down, and capped.
        val bounced = raised.after(withEffort, tick)
        assertEquals(3, bounced.cabin.fanLevel)
        assertEquals(3, bounced.tuning?.fanCap)

        val later = bounced.after(well, *Array(30) { tick })
        assertEquals(3, later.cabin.fanLevel)
        val fanMoves = generateSequence(bounced.after(well)) { it.after(tick) }.take(30).map { it.cabin.fanLevel }.toSet()
        assertEquals(setOf(3), fanMoves)
    }

    @Test
    fun `going down again long after a step up is just more noise and does not cap the fan`() {
        val raised = lowered.after(well, tick, tick, tick)
        val settled = raised.after(justWell, tick, tick, tick)

        val noisier = settled.after(withEffort, tick)
        assertEquals(3, noisier.cabin.fanLevel)
        assertNull(noisier.tuning?.fanCap)
        assertEquals(cabin.fanLevel, noisier.after(well, *Array(30) { tick }).cabin.fanLevel)
    }

    // endregion

    // region Comfort

    @Test
    fun `two levels below where the fan was, the seat is ventilated and the temperature set a degree lower while the air conditioning is on`() {
        val oneDown = driving.after(answered, hardly, tick)
        assertEquals(cabin.seatVentilation, oneDown.cabin.seatVentilation)
        assertEquals(cabin.temperatureC, oneDown.cabin.temperatureC)

        assertEquals(2, CabinLimits.COMFORT_FAN_DROP)
        assertEquals(CabinChange(fanLevel = 4, seatVentilation = 1, temperatureC = 21f), oneDown.asks(tick))
        val twoDown = oneDown.after(tick)
        // It is done once, not at every further step.
        assertEquals(CabinChange(fanLevel = 3), twoDown.asks(tick))
        assertEquals(0, twoDown.cabin.seatHeating)
    }

    @Test
    fun `with the air conditioning off, the seat is heated and the temperature set a degree higher instead`() {
        val heating = driving.copy(cabin = cabin.copy(acOn = false)).after(answered, hardly, tick)

        assertEquals(CabinChange(fanLevel = 4, seatHeating = 1, temperatureC = 23f), heating.asks(tick))
        assertEquals(0, heating.after(tick).cabin.seatVentilation)
    }

    @Test
    fun `a seat the driver already has on is left as it is, and the temperature stays within its range`() {
        val own = driving.copy(cabin = cabin.copy(seatVentilation = 3, temperatureC = 16.5f)).after(answered, hardly, tick)

        assertEquals(CabinChange(fanLevel = 4, temperatureC = 16f), own.asks(tick))
        assertEquals(3, own.after(tick).cabin.seatVentilation)
    }

    @Test
    fun `a seat the driver heats is not ventilated, because ventilation would switch the heating off`() {
        val heated = driving.copy(cabin = cabin.copy(seatHeating = 2)).after(answered, hardly, tick)

        assertEquals(CabinChange(fanLevel = 4, temperatureC = 21f), heated.asks(tick))
        val after = heated.after(tick)
        assertEquals(2, after.cabin.seatHeating)
        assertEquals(0, after.cabin.seatVentilation)
    }

    @Test
    fun `a seat the driver ventilates is not heated either`() {
        val cooled = driving.copy(cabin = cabin.copy(acOn = false, seatVentilation = 1)).after(answered, hardly, tick)

        assertEquals(CabinChange(fanLevel = 4, temperatureC = 23f), cooled.asks(tick))
        assertEquals(0, cooled.after(tick).cabin.seatHeating)
    }

    @Test
    fun `the comfort goes back when the fan is back where it was, not a step earlier`() {
        val comforted = driving.after(answered, hardly, tick, tick)
        assertEquals(1, comforted.cabin.seatVentilation)

        val oneBelow = comforted.after(well, tick, tick, tick)
        assertEquals(5, oneBelow.cabin.fanLevel)
        assertEquals(1, oneBelow.cabin.seatVentilation)
        assertEquals(21f, oneBelow.cabin.temperatureC)

        val twice = oneBelow.after(tick, tick)
        assertEquals(CabinChange(fanLevel = 6, seatVentilation = 0, temperatureC = 22f), twice.asks(tick))
    }

    @Test
    fun `the comfort also goes back when the call ends`() {
        val comforted = driving.after(answered, hardly, tick, tick)

        assertEquals(cabin, comforted.after(hungUp).cabin)
    }

    // endregion

    // region A long time on inside air

    @Test
    fun `after a long time on inside air, outside air is let in for a moment and then shut out again`() {
        val tuned = driving.after(answered)
        assertTrue(tuned.holdsAirInside)

        assertEquals(CabinChange(recirculation = false), tuned.asks(SyncDeskEvent.FreshAirDue))
        val airing = tuned.after(SyncDeskEvent.FreshAirDue)
        assertTrue(airing.tuning!!.airing)
        assertFalse(airing.holdsAirInside)
        // The air cleaner stays on through it.
        assertTrue(airing.cabin.airCleanerOn)

        assertEquals(CabinChange(recirculation = true), airing.asks(SyncDeskEvent.FreshAirDone))
        val inside = airing.after(SyncDeskEvent.FreshAirDone)
        assertTrue(inside.holdsAirInside)
        // What goes back at the end is still the air the driver had before the call.
        assertEquals(false, inside.asks(hungUp)?.recirculation)
    }

    @Test
    fun `the air cleaner is on for as long as the app keeps the air inside and goes back with it`() {
        val tuned = driving.after(answered)

        assertTrue(tuned.cabin.airCleanerOn)
        assertFalse(tuned.after(hungUp).cabin.airCleanerOn)
    }

    @Test
    fun `the airing timers mean nothing outside a tuned call or once the driver has taken the air intake over`() {
        assertEquals(driving, SyncDeskEngine.reduce(driving, SyncDeskEvent.FreshAirDue))
        assertEquals(driving, SyncDeskEngine.reduce(driving, SyncDeskEvent.FreshAirDone))
        assertEquals(driving, SyncDeskEngine.reduce(driving, SyncDeskEvent.RecirculationLong))

        val own = driving.after(answered).driverSets { copy(recirculation = false) }
        assertFalse(own.holdsAirInside)
        assertNull(own.asks(SyncDeskEvent.FreshAirDue))
        assertNull(own.asks(SyncDeskEvent.FreshAirDone))
    }

    @Test
    fun `inside air for a while with the wipers on switches the defroster on until the call ends`() {
        val long = driving.after(answered, SyncDeskEvent.RecirculationLong)
        assertNull(long.command?.change?.defrosterOn)

        // The wipers come on, even slowly: the glass may fog up.
        assertEquals(CabinChange(defrosterOn = true), long.asks(signals(wiper = WiperSpeed.Low)))
        val defrosting = long.after(signals(wiper = WiperSpeed.Low))
        assertTrue(defrosting.cabin.defrosterOn)

        // It stays on when the rain stops, and goes back when the call ends.
        val dry = defrosting.after(signals())
        assertTrue(dry.cabin.defrosterOn)
        assertFalse(dry.after(hungUp).cabin.defrosterOn)
    }

    @Test
    fun `wipers that are already on switch the defroster on the moment the air has been inside long enough`() {
        val raining = driving.after(answered, signals(wiper = WiperSpeed.Low))
        assertFalse(raining.cabin.defrosterOn)

        assertEquals(CabinChange(defrosterOn = true), raining.asks(SyncDeskEvent.RecirculationLong))
    }

    @Test
    fun `a defroster the driver has on is left alone and not switched off at the end`() {
        val own = driving.copy(cabin = cabin.copy(defrosterOn = true))
            .after(answered, signals(wiper = WiperSpeed.Low), SyncDeskEvent.RecirculationLong)

        assertNull(own.tuning?.asked?.defrosterOn)
        assertTrue(own.after(hungUp).cabin.defrosterOn)
    }

    @Test
    fun `letting outside air in starts the count for the defroster over`() {
        val aired = driving.after(answered, SyncDeskEvent.RecirculationLong, SyncDeskEvent.FreshAirDue, SyncDeskEvent.FreshAirDone)

        assertFalse(aired.tuning!!.longRecirculation)
        assertNull(aired.asks(signals(wiper = WiperSpeed.Low)))
    }

    // endregion

    // region The driver's own hand

    @Test
    fun `a part the driver changes during the call is left alone until it ends and is not put back`() {
        val lowered = driving.after(answered, hardly, tick)
        assertEquals(5, lowered.cabin.fanLevel)

        val own = lowered.driverSets { copy(fanLevel = 7) }
        assertEquals(setOf(CabinPart.Fan), own.tuning?.overridden)

        // Hardly heard still, but the fan is the driver's now: the call gets louder instead.
        assertEquals(CabinChange(callVolume = 4), own.asks(tick))
        val later = own.after(tick, well, *Array(9) { tick })
        assertEquals(7, later.cabin.fanLevel)

        val ended = later.after(hungUp)
        assertEquals(7, ended.cabin.fanLevel)
        assertNull(later.asks(hungUp)?.fanLevel)
        // Everything else the app moved goes back.
        assertEquals(cabin.copy(fanLevel = 7), ended.cabin)
    }

    @Test
    fun `a window the driver opens again stays open`() {
        val own = driving.after(answered).driverSets { copy(windowOpenPercent = 50) }

        assertEquals(setOf(CabinPart.Window), own.tuning?.overridden)
        val ended = own.after(withEffort, tick, tick, hungUp)
        assertEquals(50, ended.cabin.windowOpenPercent)
    }

    @Test
    fun `a part the app has not touched yet is the driver's as soon as they change it`() {
        val own = driving.after(answered).driverSets { copy(airDirection = AirDirection.FaceAndFeet, callVolume = 2) }

        assertEquals(setOf(CabinPart.AirDirection, CabinPart.CallVolume), own.tuning?.overridden)
        // Heard with effort: the air would turn to the feet first, but it is the driver's.
        assertEquals(CabinChange(fanLevel = 5), own.after(withEffort).asks(tick))
        val ended = own.after(hardly, *Array(8) { tick }, hungUp)
        assertEquals(AirDirection.FaceAndFeet, ended.cabin.airDirection)
        assertEquals(2, ended.cabin.callVolume)
    }

    @Test
    fun `an actuator on its way to what was asked is not taken for the driver`() {
        // The window is asked closed and reports its way down.
        val asked = SyncDeskEngine.reduce(driving, answered)
        val closing = listOf(22, 14, 6, 0).fold(asked) { state, percent ->
            SyncDeskEngine.reduce(state, SyncDeskEvent.CabinReported(state.cabin.copy(windowOpenPercent = percent)))
        }

        assertEquals(emptySet<CabinPart>(), closing.tuning?.overridden)
        assertEquals(30, closing.tuning?.restore?.windowOpenPercent)

        // A set temperature that settles in half degrees, and a fan that reaches its level directly.
        val comforting = SyncDeskEngine.reduce(driving.after(answered, hardly, tick), tick)
        val settling = listOf(
            comforting.cabin.copy(fanLevel = 4),
            comforting.cabin.copy(fanLevel = 4, temperatureC = 21.5f),
            comforting.cabin.copy(fanLevel = 4, temperatureC = 21f, seatVentilation = 1),
        ).fold(comforting) { state, cabin -> SyncDeskEngine.reduce(state, SyncDeskEvent.CabinReported(cabin)) }
        assertEquals(emptySet<CabinPart>(), settling.tuning?.overridden)
    }

    @Test
    fun `a value that moves away from what was asked, or past it, is the driver`() {
        val asked = SyncDeskEngine.reduce(driving, answered)
        val away = SyncDeskEngine.reduce(asked, SyncDeskEvent.CabinReported(asked.cabin.copy(windowOpenPercent = 40)))
        assertEquals(setOf(CabinPart.Window), away.tuning?.overridden)

        // Half-way closed, then back up: the driver stopped it.
        val stopped = listOf(15, 20).fold(asked) { state, percent ->
            SyncDeskEngine.reduce(state, SyncDeskEvent.CabinReported(state.cabin.copy(windowOpenPercent = percent)))
        }
        assertEquals(setOf(CabinPart.Window), stopped.tuning?.overridden)

        // The fan asked down to 5 and reported at 4.
        val fan = SyncDeskEngine.reduce(driving.after(answered, hardly), tick)
        val past = SyncDeskEngine.reduce(fan, SyncDeskEvent.CabinReported(fan.cabin.copy(fanLevel = 4)))
        assertEquals(setOf(CabinPart.Fan), past.tuning?.overridden)
    }

    @Test
    fun `a report that repeats what the cabin already had changes nothing`() {
        val tuned = driving.after(answered, hardly, tick)

        assertEquals(tuned, SyncDeskEngine.reduce(tuned, SyncDeskEvent.CabinReported(tuned.cabin)))
    }

    @Test
    fun `seat position and lights moved during the call are not the tuning's business`() {
        val state = driving.after(answered).driverSets { copy(seatRecline = 20, readingLightOn = true, ambientPercent = 40) }

        assertEquals(emptySet<CabinPart>(), state.tuning?.overridden)
    }

    @Test
    fun `the driver's hand is forgotten with the call`() {
        val next = driving.after(answered).driverSets { copy(windowOpenPercent = 50) }.after(hungUp, answered)

        assertEquals(emptySet<CabinPart>(), next.tuning?.overridden)
        assertEquals(0, next.cabin.windowOpenPercent)
        assertEquals(50, next.after(hungUp).cabin.windowOpenPercent)
    }

    @Test
    fun `a part the vehicle refuses to move is not asked again on this call but is still put back`() {
        val lowered = driving.after(answered, hardly, tick)
        val refused = lowered.after(SyncDeskEvent.CommandRefused(CabinPart.Fan))

        assertEquals(setOf(CabinPart.Fan), refused.tuning?.refused)
        assertEquals(CabinChange(callVolume = 4), refused.asks(tick))
        assertEquals(cabin.fanLevel, refused.asks(hungUp)?.fanLevel)
    }

    // endregion

    // region Putting it all back

    /** A call that moved everything tuning can move by hearing alone. */
    private val tunedHard
        get() = driving.after(answered, hardly, tick, tick, tick, tick, tick, tick)

    @Test
    fun `hanging up puts back everything the app moved`() {
        val tuned = tunedHard
        assertEquals(
            cabin.copy(
                fanLevel = 2, airDirection = AirDirection.Feet, windowOpenPercent = 0, recirculation = true,
                airCleanerOn = true, seatVentilation = 1, temperatureC = 21f, callVolume = 5,
            ),
            tuned.cabin,
        )

        val ended = tuned.after(hungUp)

        assertEquals(
            CabinChange(
                fanLevel = 6, airDirection = AirDirection.Face, windowOpenPercent = 30, recirculation = false,
                airCleanerOn = false, seatVentilation = 0, temperatureC = 22f, callVolume = 3,
            ),
            tuned.asks(hungUp),
        )
        assertEquals(cabin, ended.cabin)
        assertNull(ended.tuning)
        assertEquals(SyncDeskMode.Standby, ended.mode)
    }

    @Test
    fun `shifting to Park and switching tuning off put it all back too, with the call still on`() {
        val parkedNow = tunedHard.after(signals(gear = Gear.Park))
        val switchedOff = tunedHard.after(SyncDeskEvent.AutoQuietToggled(false))

        assertEquals(cabin, parkedNow.cabin)
        assertNull(parkedNow.tuning)
        assertEquals(cabin, switchedOff.cabin)
        assertNull(switchedOff.tuning)
        // Nothing is tuned afterwards, however badly the call is heard.
        assertNull(switchedOff.asks(tick))
    }

    @Test
    fun `switching tuning back on during the call starts from the cabin as it is then`() {
        val again = tunedHard.after(SyncDeskEvent.AutoQuietToggled(false), SyncDeskEvent.AutoQuietToggled(true))

        assertEquals(cabin, again.tuning?.original)
        assertEquals(0, again.cabin.windowOpenPercent)
        assertEquals(cabin.fanLevel, again.cabin.fanLevel)
    }

    // endregion

    // region Recovery after the app was stopped

    private val left = CabinChange(
        fanLevel = 6, windowOpenPercent = 30, recirculation = false, airDirection = AirDirection.Face,
        airCleanerOn = false, seatVentilation = 0, temperatureC = 22f, callVolume = 3,
    )

    private val foundTuned = CabinState(
        fanLevel = 2, windowOpenPercent = 0, recirculation = true, airDirection = AirDirection.Feet,
        airCleanerOn = true, seatVentilation = 1, temperatureC = 21f, callVolume = 5,
    )

    @Test
    fun `a cabin left tuned by a stopped app is put back at once when there is no call`() {
        val found = SyncDeskState(signals = VehicleSignals(gear = Gear.Drive), cabin = foundTuned)

        val state = found.after(SyncDeskEvent.QuietRecovered(left))

        assertEquals(left, found.asks(SyncDeskEvent.QuietRecovered(left)))
        assertEquals(cabin, state.cabin)
        assertNull(state.tuning)
    }

    @Test
    fun `only the parts the app had moved are put back`() {
        val found = SyncDeskState(cabin = foundTuned)
        val some = CabinChange(windowOpenPercent = 30, callVolume = 3)

        assertEquals(some, found.asks(SyncDeskEvent.QuietRecovered(some)))
    }

    @Test
    fun `a cabin left tuned stays tuned for the rest of the call and then goes back to what it was before`() {
        // The app starts again in the middle of the call: the tuned cabin looks like the driver's own.
        val restarted = SyncDeskState(signals = VehicleSignals(gear = Gear.Drive), cabin = foundTuned).after(answered)
        assertEquals(foundTuned, restarted.tuning?.original)

        val recovered = restarted.after(SyncDeskEvent.QuietRecovered(left))
        assertEquals(foundTuned, recovered.cabin)
        assertEquals(cabin, recovered.tuning?.original)
        assertEquals(left, recovered.tuning?.restore)

        // The fan counts as lowered by the app: it may come back up, and comfort follows it.
        val better = recovered.after(well, tick, tick, tick)
        assertEquals(3, better.cabin.fanLevel)

        assertEquals(cabin, better.after(hungUp).cabin)
    }

    // endregion

    // region Missed for the noise

    @Test
    fun `a sentence is marked as missed for the noise once two tuning moments in a row have found the call hardly heard`() {
        val state = driving.after(
            answered, well, note(1), withEffort, note(2), hardly, note(3), tick, note(4), tick, note(5), note(6), withEffort, note(7),
        )

        assertEquals(
            listOf(null, null, null, null, MissedReason.Noise, MissedReason.Noise, null),
            state.memos.single().notes.map { it.missed },
        )
        assertEquals(listOf("text 5", "text 6"), state.memos.single().missed.map { it.text })
    }

    @Test
    fun `a sentence that ends just as the call becomes hardly heard is not marked, even after the tuning moment that lowers the fan`() {
        // The noise around the car rises at the end of a sentence heard well; a tuning moment follows.
        val risen = driving.after(answered, well, hardly)
        assertFalse(risen.markingMissed)
        assertNull(risen.after(note(1)).memos.single().notes.single().missed)

        val lowered = risen.after(tick)
        assertEquals(5, lowered.cabin.fanLevel)
        assertEquals(Hearing.Bad, lowered.hearing?.hearing)
        assertFalse(lowered.markingMissed)
        assertNull(lowered.after(note(1)).memos.single().notes.single().missed)
    }

    @Test
    fun `a better reading between tuning moments starts the count again, and a worse one keeps it`() {
        val interrupted = driving.after(answered, hardly, tick, withEffort, hardly, tick)
        assertEquals(1, interrupted.badTicks)
        assertNull(interrupted.after(note(1)).memos.single().notes.single().missed)
        assertEquals(MissedReason.Noise, interrupted.after(tick, note(1)).memos.single().notes.single().missed)

        // Still hardly heard, only more so: the count goes on.
        val worse = driving.after(answered, hardly, tick, heard(margin = -5), tick, note(1))
        assertEquals(MissedReason.Noise, worse.memos.single().notes.single().missed)
    }

    @Test
    fun `where the road and the noise both kept the driver from hearing, the road is the reason given`() {
        val state = driving.after(answered, hardly, tick, tick, signals(wiper = WiperSpeed.High), note(1), signals(), note(2))

        assertEquals(listOf(MissedReason.Driving, MissedReason.Noise), state.memos.single().notes.map { it.missed })
    }

    @Test
    fun `the driver's own promise is never marked, and the noise of one call does not mark the next`() {
        val promise = SyncDeskEvent.NoteCaptured(MeetingNote(0, "14:09", "me", "I will send it", gist = "send it", byDriver = true))
        val state = driving.after(answered, hardly, tick, tick, promise, hungUp, answered, note(1))

        assertEquals(listOf<MissedReason?>(null), state.memos[0].notes.map { it.missed })
        assertEquals(listOf<MissedReason?>(null), state.memos[1].notes.map { it.missed })
        assertEquals(0, state.badTicks)
    }

    @Test
    fun `the noise marks a sentence whether or not the cabin is being tuned`() {
        val parkedCall = parked.after(answered, hardly, tick, tick, note(1))
        val switchedOff = driving.after(SyncDeskEvent.AutoQuietToggled(false), answered, hardly, tick, tick, note(1))

        assertNull(parkedCall.tuning)
        assertEquals(MissedReason.Noise, parkedCall.memos.single().notes.single().missed)
        assertEquals(MissedReason.Noise, switchedOff.memos.single().notes.single().missed)
    }

    @Test
    fun `tuning moments outside a call count nothing`() {
        assertEquals(0, driving.after(hardly, tick, tick).badTicks)
        assertEquals(0, driving.after(answered, well, tick, tick).badTicks)
    }

    // endregion

    // region The ambient light while sentences are marked

    private val lit = driving.copy(cabin = cabin.copy(ambientPercent = 50, ambientColor = 0xFF00E5FF.toInt()))

    @Test
    fun `an ambient light that is on changes colour while sentences are marked as missed and goes back after`() {
        val marking = lit.after(answered, signals(wiper = WiperSpeed.High))

        assertEquals(CabinLimits.MISSED_AMBIENT_COLOR, marking.cabin.ambientColor)
        assertEquals(50, marking.cabin.ambientPercent)

        assertEquals(CabinChange(ambientColor = 0xFF00E5FF.toInt()), marking.asks(signals()))
        assertEquals(0xFF00E5FF.toInt(), marking.after(signals()).cabin.ambientColor)
    }

    @Test
    fun `the colour also shows that sentences are missed for the noise`() {
        // Not on one hardly heard reading, nor after one tuning moment: as the sentences are marked.
        assertEquals(lit.cabin.ambientColor, lit.after(answered, hardly, tick).cabin.ambientColor)

        val noisy = lit.after(answered, hardly, tick, tick)
        assertEquals(CabinLimits.MISSED_AMBIENT_COLOR, noisy.cabin.ambientColor)

        assertEquals(0xFF00E5FF.toInt(), noisy.after(withEffort).cabin.ambientColor)
    }

    @Test
    fun `an ambient light that is off is left off and its colour alone`() {
        val marking = driving.after(answered, hardly, signals(wiper = WiperSpeed.High))

        assertEquals(cabin.ambientColor, marking.cabin.ambientColor)
        assertEquals(0, marking.cabin.ambientPercent)
        assertNull(marking.tuning?.asked?.ambientColor)
    }

    @Test
    fun `the colour goes back when the call ends in the middle of a marked stretch`() {
        val ended = lit.after(answered, signals(wiper = WiperSpeed.High), hungUp)

        assertEquals(lit.cabin, ended.cabin)
    }

    @Test
    fun `a colour the driver picks during the call is kept`() {
        val own = lit.after(answered, signals(wiper = WiperSpeed.High)).driverSets { copy(ambientColor = 0xFF2979FF.toInt()) }

        val cleared = own.after(signals())
        assertEquals(0xFF2979FF.toInt(), cleared.cabin.ambientColor)
        assertEquals(0xFF2979FF.toInt(), cleared.after(signals(wiper = WiperSpeed.High), hungUp).cabin.ambientColor)
    }

    // endregion
}
