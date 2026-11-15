package com.syncdesk.app.domain

import com.syncdesk.app.domain.SyncDeskEvent.AutoQuietToggled
import com.syncdesk.app.domain.SyncDeskEvent.CabinReported
import com.syncdesk.app.domain.SyncDeskEvent.CallChanged
import com.syncdesk.app.domain.SyncDeskEvent.CallTick
import com.syncdesk.app.domain.SyncDeskEvent.DialogClosed
import com.syncdesk.app.domain.SyncDeskEvent.DialogReplyTimedOut
import com.syncdesk.app.domain.SyncDeskEvent.DialogSpoken
import com.syncdesk.app.domain.SyncDeskEvent.DriverReplied
import com.syncdesk.app.domain.SyncDeskEvent.FreshAirDone
import com.syncdesk.app.domain.SyncDeskEvent.FreshAirDue
import com.syncdesk.app.domain.SyncDeskEvent.HearingChanged
import com.syncdesk.app.domain.SyncDeskEvent.MeetingStarted
import com.syncdesk.app.domain.SyncDeskEvent.NoteCaptured
import com.syncdesk.app.domain.SyncDeskEvent.ContactsReported
import com.syncdesk.app.domain.SyncDeskEvent.OfficePauseRequested
import com.syncdesk.app.domain.SyncDeskEvent.OfficeRequested
import com.syncdesk.app.domain.SyncDeskEvent.OfficeRestoreRequested
import com.syncdesk.app.domain.SyncDeskEvent.OfficeResumeRequested
import com.syncdesk.app.domain.SyncDeskEvent.OfficeSceneChanged
import com.syncdesk.app.domain.SyncDeskEvent.OfficeStepDone
import com.syncdesk.app.domain.SyncDeskEvent.OfficeStepUndone
import com.syncdesk.app.domain.SyncDeskEvent.PresetChanged
import com.syncdesk.app.domain.SyncDeskEvent.UsbReported
import com.syncdesk.app.domain.SyncDeskEvent.QuietRecovered
import com.syncdesk.app.domain.SyncDeskEvent.RecirculationLong
import com.syncdesk.app.domain.SyncDeskEvent.DemandHoldExpired
import com.syncdesk.app.domain.SyncDeskEvent.RangesReported
import com.syncdesk.app.domain.SyncDeskEvent.SignalsChanged
import com.syncdesk.app.domain.SyncDeskEvent.TuneTick
import com.syncdesk.app.domain.SyncDeskEvent.UxLimitsChanged

/**
 * Turns vehicle, call and user events into the next [SyncDeskState].
 *
 * The engine is pure. It never edits [SyncDeskState.cabin], which always holds what the vehicle
 * last reported; when it wants the cabin changed it issues a [CabinCommand] and the caller sends
 * that to the vehicle. Real actuators answer one at a time and may refuse, so the screen shows
 * what the car actually did rather than what was asked of it.
 *
 * Four rules run on every event:
 *
 * 1. Tune: answering a call while not parked closes the window and switches the air to
 *    recirculation. From then on the cabin changes only as far as the call is hard to hear: at
 *    each tuning moment one thing moves by one step. What the driver changes by hand is left
 *    alone, and everything else goes back when the call ends.
 * 2. Capture: every sentence of a call is written into that call's memo; a sentence said while
 *    the road is demanding, or once the cabin has been too loud to hear it for a whole tuning
 *    interval, is marked as missed.
 * 3. Dialog: once a call with a memo has ended and the road is clear, the car offers to sum it
 *    up, explains it, and answers questions about it. It keeps quiet while the road is demanding
 *    and stops for a new call or once the desk is open.
 * 4. Office: while the desk is open (parked, or the car drives itself), one request turns the
 *    driver's seat into a place to work on a laptop, one step at a time in a fixed order: wheel,
 *    seat, cushion, backrest, lights, shade, climate, door lock, USB-C power. The driver can stop
 *    it at any step. One request puts everything back in the opposite order, and having to drive
 *    again puts it back at once.
 *
 * The engine also keeps count of the drives ([SyncDeskState.trip]): the memos and the contacts
 * not looked at that are shown on parking are those of the drive that has just ended.
 */
object SyncDeskEngine {

    /** How many call memos are kept; an older one makes way for a new one. */
    const val MAX_MEMOS = 20

    /** How many tuning moments in a row the call must be heard well before the fan comes back up a step. */
    const val GOOD_TICKS_TO_RAISE = 3

    /**
     * A fan step up that has to be taken back within this many tuning moments was one too many:
     * the fan does not go that high again on this call.
     */
    const val BOUNCE_TICKS = 3

    /**
     * How many tuning moments in a row must find the call hardly heard before what is said is
     * marked as missed for the noise ([SyncDeskState.missingForNoise]). Two in a row means the
     * cabin has been too loud for at least one whole tuning interval.
     */
    const val NOISE_MARK_TICKS = 2

    /** The parts a tuning moment moves step by step; a step is taken only once the last one has arrived. */
    private val STEPPED_PARTS = listOf(CabinPart.Fan, CabinPart.AirDirection, CabinPart.CallVolume)

    fun reduce(state: SyncDeskState, event: SyncDeskEvent): SyncDeskState {
        val before = state.command?.id ?: 0
        val next = when (event) {
            is SignalsChanged -> onSignalsChanged(state, event.signals)
            // A contact that is no longer reported was looked at; nothing about it needs keeping.
            is ContactsReported -> state.copy(
                contacts = event.contacts,
                staleContacts = state.staleContacts intersect event.contacts.mapTo(mutableSetOf()) { it.id },
            )
            is UsbReported -> state.copy(usb = event.port)
            // What office mode asked for (the car drives itself with office mode on during a
            // call) is not the driver's hand either.
            is CabinReported -> state.copy(
                cabin = event.cabin,
                tuning = state.tuning?.noticingTheDriver(was = state.cabin, now = event.cabin, office = officeAsked(state)),
            )
            is RangesReported -> state.copy(ranges = event.ranges)
            is UxLimitsChanged -> state.copy(ux = event.limits)
            is CallChanged -> onCallChanged(state, event)
            is QuietRecovered -> onQuietRecovered(state, event.restore, before)
            // Any reading better than hardly heard starts the count of hardly heard moments again.
            is HearingChanged -> if (state.call.active) {
                state.copy(hearing = event.levels, badTicks = if (event.levels.hearing == Hearing.Bad) state.badTicks else 0)
            } else {
                state
            }
            TuneTick -> onTuneTick(state.copy(badTicks = if (state.callHardlyHeard()) state.badTicks + 1 else 0), before)
            RecirculationLong -> if (state.holdsAirInside) {
                state.copy(tuning = state.tuning?.copy(longRecirculation = true))
            } else {
                state
            }
            FreshAirDue -> onFreshAirDue(state, before)
            FreshAirDone -> onFreshAirDone(state, before)
            DemandHoldExpired -> onDemandHoldExpired(state)
            CallTick -> if (state.call.active) {
                state.copy(call = state.call.copy(elapsedSeconds = state.call.elapsedSeconds + 1))
            } else {
                state
            }
            is AutoQuietToggled -> state.copy(autoQuietEnabled = event.enabled)
            is NoteCaptured -> onNoteCaptured(state, event.note)
            // The meeting names the memo of the call that carries it, whichever is seen first.
            is MeetingStarted -> state.copy(
                meeting = event.meeting,
                memos = state.memos.map { if (it.id == state.call.memoId) it.copy(title = event.meeting.title) else it },
            )
            DialogSpoken -> onDialogSpoken(state)
            is DriverReplied -> onDriverReplied(state, event.reply)
            DialogReplyTimedOut -> if (state.listeningForReply) state.copy(dialog = null) else state
            DialogClosed -> state.copy(dialog = null)
            SyncDeskEvent.ConsentGiven -> state.copy(consented = true, consentDeferred = false)
            SyncDeskEvent.ConsentDeferred -> state.copy(consentDeferred = true)
            // A part the vehicle will not move is not asked again on this call.
            is SyncDeskEvent.CommandRefused -> state.copy(
                refusal = event.part,
                tuning = state.tuning?.let { it.copy(refused = it.refused + event.part) },
            )
            SyncDeskEvent.RefusalCleared -> state.copy(refusal = null)
            is SyncDeskEvent.SpeechChecked -> state.copy(speechAvailable = event.available)
            is SyncDeskEvent.LinkChanged -> state.copy(vehicleLinking = event.pending)
            is SyncDeskEvent.ChimesToggled -> state.copy(chimesEnabled = event.enabled)
            OfficeRequested -> onOfficeRequested(state)
            is OfficeStepDone -> onOfficeStepDone(state, event.step)
            is OfficeStepUndone -> onOfficeStepUndone(state, event.step)
            OfficePauseRequested -> if (state.office.pending != null && !state.office.paused) {
                state.copy(office = state.office.copy(paused = true))
            } else {
                state
            }
            OfficeResumeRequested -> onOfficeResumeRequested(state)
            OfficeRestoreRequested -> onOfficeRestoreRequested(state)
            is OfficeSceneChanged -> onOfficeSceneChanged(state, event.scene, before)
            is PresetChanged -> state
        }
        if (event is PresetChanged) return onPresetChanged(state, event.preset, before)
        return next.applyOfficeRule(before).applyTuneRule(before).applyCaptureRule().applyDialogRule()
            .applyDefrostRule(before).applyMarkLightRule(before)
    }

    /**
     * Leaving Park begins a new drive: its calls and contacts are counted apart from the ones
     * before, and whatever was still waiting on the phone then belongs to the stop that is over.
     * Leaving the stop where the driver said "later" also means asking again at the next one.
     */
    private fun onSignalsChanged(state: SyncDeskState, signals: VehicleSignals): SyncDeskState {
        val leavingPark = state.signals.isParked && !signals.isParked
        return state.copy(
            signals = signals,
            consentDeferred = state.consentDeferred && signals.isParked,
            trip = if (leavingPark) state.trip + 1 else state.trip,
            staleContacts = if (leavingPark) state.contacts.mapTo(mutableSetOf()) { it.id } else state.staleContacts,
        )
    }

    /**
     * Asks the vehicle for [change]. Changes issued while handling the same event are merged into
     * one command, so none of them is lost; [before] is the command id when the event arrived.
     * A change that asks for nothing issues no command.
     */
    private fun SyncDeskState.issue(change: CabinChange, before: Int): SyncDeskState {
        if (change.isEmpty) return this
        val current = command
        return if (current != null && current.id > before) {
            copy(command = current.copy(change = current.change + change))
        } else {
            copy(command = CabinCommand(id = before + 1, change = change))
        }
    }

    // region Call

    /**
     * A call that starts remembers when, for its memo. A call that ends with a memo opens the
     * talk about it; one on which nothing was said ends quietly.
     */
    private fun onCallChanged(state: SyncDeskState, event: CallChanged): SyncDeskState {
        val started = event.active && !state.call.active
        val ended = !event.active && state.call.active
        return when {
            started -> state.copy(
                call = CallState(active = true, driverSpeaking = event.driverSpeaking, date = event.date, startedAt = event.time),
                badTicks = 0,
            )
            ended -> state.copy(
                call = CallState(),
                hearing = null,
                badTicks = 0,
                dialog = state.call.memoId?.let { PostCallDialog(memoId = it) },
            )
            else -> state.copy(call = state.call.copy(driverSpeaking = event.active && event.driverSpeaking))
        }
    }

    // endregion

    // region Tune

    /** The call is one the cabin should be tuned for. */
    private fun SyncDeskState.tunedCall(): Boolean = consented && autoQuietEnabled && !signals.isParked && call.active

    /** The call in progress is hardly heard, as last measured. */
    private fun SyncDeskState.callHardlyHeard(): Boolean = call.active && hearing?.hearing == Hearing.Bad

    /**
     * Tuning begins when the call is answered: the cabin as it is then is remembered, the window
     * closes, the air is drawn from inside and, for as long as it is, cleaned. It ends with the
     * call, on parking, or when the driver switches it off, and puts back everything the app
     * moved. In between, [onTuneTick] does the adjusting.
     *
     * Tuning and office mode each put back only what they moved themselves:
     *
     * - Driving off with office mode on asks for the office to be put back in the same event that
     *   may start tuning (a call is on). Tuning then starts from the cabin as it will be once that
     *   has happened, not from the office arrangement, and takes the vehicle's report of it for
     *   what it is rather than for the driver's hand.
     * - A part office mode holds when a tuned call ends (the car drives itself with the office on)
     *   is left to office mode, which puts it back from its own snapshot.
     */
    private fun SyncDeskState.applyTuneRule(before: Int): SyncDeskState {
        val current = tuning
        return when {
            tunedCall() && current == null -> {
                val underWay = command?.takeIf { it.id > before }?.change ?: CabinChange.NONE
                val expected = cabin.applying(underWay)
                copy(tuning = CabinTuning(original = expected, asked = underWay.only(CabinTuning.PARTS))).tune(
                    CabinChange(
                        windowOpenPercent = CabinLimits.QUIET_WINDOW_OPEN.takeIf { expected.windowOpenPercent > it },
                        recirculation = true.takeIf { !expected.recirculation },
                        airCleanerOn = true.takeIf { !expected.airCleanerOn },
                    ),
                    before,
                )
            }
            !tunedCall() && current != null -> copy(tuning = null).issue(current.restore.without(officeHeldParts()), before)
            else -> this
        }
    }

    /**
     * Asks for [change] as part of tuning and remembers that it was asked, so the vehicle's
     * report of it is not taken for the driver's hand and the part is put back in the end.
     * Parts the app may no longer move are left out.
     */
    private fun SyncDeskState.tune(change: CabinChange, before: Int): SyncDeskState {
        val current = tuning ?: return this
        val allowed = change.only(change.parts.filterTo(mutableSetOf()) { current.mayMove(it) })
        if (allowed.isEmpty) return this
        return copy(tuning = current.copy(asked = current.asked + allowed)).issue(allowed, before)
    }

    /**
     * The vehicle reported the cabin. A tuned part whose value changed in a way no request of the
     * app explains was moved by the driver. A value on its way to what was asked (a window that
     * reports 20 between 30 and closed) is the actuator moving, not the driver.
     */
    private fun CabinTuning.noticingTheDriver(was: CabinState, now: CabinState, office: List<CabinChange>): CabinTuning {
        val moved = CabinTuning.PARTS.filter { part ->
            part !in overridden && was[part] != now[part] && !explains(asked[part], was[part], now[part]) &&
                office.none { explains(it[part], was[part], now[part]) }
        }
        return if (moved.isEmpty()) this else copy(overridden = overridden + moved)
    }

    /** What office mode may be asking of the cabin right now: its targets, and what it puts back. */
    private fun officeAsked(state: SyncDeskState): List<CabinChange> {
        if (state.office.phase == OfficePhase.Off) return emptyList()
        return listOfNotNull(officeTargets(state), state.office.snapshot?.asChange())
    }

    /** Whether having asked for [target] explains a part going from [was] to [now]. */
    private fun explains(target: Any?, was: Any, now: Any): Boolean {
        if (target == null) return false
        if (now == target) return true
        if (target !is Number || was !is Number || now !is Number) return false
        // Closer to the target than before, and still on the same side of it.
        val before = was.toDouble() - target.toDouble()
        val left = now.toDouble() - target.toDouble()
        return left * before > 0 && kotlin.math.abs(left) < kotlin.math.abs(before)
    }

    /**
     * A tuning moment. One thing changes by one step, depending on how well the other party is
     * heard, and nothing changes while nothing measures it:
     *
     * - heard with effort: the air is turned from the face to the feet; from then on the fan goes
     *   down a step;
     * - hardly heard: the fan goes down a step (and the air to the feet with it); once the fan is
     *   as low as it goes, the call gets a step louder;
     * - heard well [GOOD_TICKS_TO_RAISE] times in a row, with room to spare: the fan comes back up
     *   a step towards where it was.
     *
     * The fan never goes below [CabinRanges.quietFanLevel], and nothing of the climate system is
     * touched while it is switched off. While a part asked to move at an earlier moment has not
     * arrived yet, the moment passes: the next step is taken from where the cabin really is.
     */
    private fun onTuneTick(state: SyncDeskState, before: Int): SyncDeskState {
        val tuning = state.tuning ?: return state
        val levels = state.hearing ?: return state
        val cabin = state.cabin
        val counted = tuning.copy(ticksSinceRaise = tuning.ticksSinceRaise?.plus(1))
        val onItsWay = STEPPED_PARTS.any { part ->
            tuning.mayMove(part) && tuning.asked[part] != null && tuning.asked[part] != cabin[part]
        }
        if (onItsWay) return state.copy(tuning = counted)
        val fanMoves = cabin.climateOn && tuning.mayMove(CabinPart.Fan)
        val lowerFan = (cabin.fanLevel - 1).takeIf { fanMoves && cabin.fanLevel > state.ranges.quietFanLevel }
        val toFeet = cabin.airDirection.awayFromFace()
            ?.takeIf { cabin.climateOn && tuning.mayMove(CabinPart.AirDirection) }
        val louderCall = (cabin.callVolume + 1)
            .takeIf { it <= state.ranges.callVolumeMax && tuning.mayMove(CabinPart.CallVolume) }
        return when (levels.hearing) {
            Hearing.Poor -> when {
                toFeet != null -> state.copy(tuning = counted.copy(goodTicks = 0))
                    .tune(CabinChange(airDirection = toFeet), before)
                lowerFan != null -> state.withFan(counted, lowerFan, before)
                else -> state.copy(tuning = counted.copy(goodTicks = 0))
            }
            Hearing.Bad -> when {
                lowerFan != null -> state.withFan(counted, lowerFan, before).tune(CabinChange(airDirection = toFeet), before)
                else -> state.copy(tuning = counted.copy(goodTicks = 0))
                    .tune(CabinChange(callVolume = louderCall, airDirection = toFeet), before)
            }
            Hearing.Good -> {
                val good = counted.goodTicks + 1
                val ceiling = minOf(tuning.original.fanLevel, tuning.fanCap ?: Int.MAX_VALUE)
                val raise = good >= GOOD_TICKS_TO_RAISE && levels.hasRoomForMoreFan && fanMoves &&
                    tuning.asked.fanLevel != null && cabin.fanLevel < ceiling
                if (raise) {
                    state.withFan(counted, cabin.fanLevel + 1, before)
                } else {
                    state.copy(tuning = counted.copy(goodTicks = good))
                }
            }
        }
    }

    /**
     * Moves the fan one step to [level] and lets the comfort follow it. Having to go down again
     * right after going up means that step up was one too many, so the fan is capped below it and
     * does not swing up and down for the rest of the call.
     */
    private fun SyncDeskState.withFan(tuning: CabinTuning, level: Int, before: Int): SyncDeskState {
        val up = level > cabin.fanLevel
        val bounced = !up && tuning.ticksSinceRaise != null && tuning.ticksSinceRaise <= BOUNCE_TICKS
        val moved = tuning.copy(
            goodTicks = 0,
            ticksSinceRaise = if (up) 0 else null,
            fanCap = if (bounced) level else tuning.fanCap,
        )
        return copy(tuning = moved).tune(CabinChange(fanLevel = level), before).applyComfort(level, before)
    }

    /**
     * Less air is made up for once the fan is [CabinLimits.COMFORT_FAN_DROP] levels below where it
     * was: with the air conditioning on, the seat is ventilated and the set temperature goes down
     * a degree; with it off, the seat is heated and the temperature goes up a degree. All of it
     * goes back when the fan is back where it was. A seat the driver already has on, either way, is
     * left as it is: the vehicle keeps ventilation and heating as one setting, so switching one on
     * would switch the driver's other one off.
     */
    private fun SyncDeskState.applyComfort(fanLevel: Int, before: Int): SyncDeskState {
        val current = tuning ?: return this
        val drop = current.original.fanLevel - fanLevel
        return when {
            !current.comfort && drop >= CabinLimits.COMFORT_FAN_DROP -> {
                val step = CabinLimits.COMFORT_TEMPERATURE_STEP_C
                val change = if (cabin.acOn) {
                    CabinChange(
                        seatVentilation = CabinLimits.COMFORT_SEAT_LEVEL.takeIf { cabin.seatVentilation < it && cabin.seatHeating == 0 },
                        temperatureC = (cabin.temperatureC - step).coerceAtLeast(ranges.temperatureMinC),
                    )
                } else {
                    CabinChange(
                        seatHeating = CabinLimits.COMFORT_SEAT_LEVEL.takeIf { cabin.seatHeating < it && cabin.seatVentilation == 0 },
                        temperatureC = (cabin.temperatureC + step).coerceAtMost(ranges.temperatureMaxC),
                    )
                }
                copy(tuning = current.copy(comfort = true)).tune(change, before)
            }
            current.comfort && drop <= 0 -> copy(tuning = current.copy(comfort = false)).tune(
                current.restore.only(setOf(CabinPart.SeatVentilation, CabinPart.SeatHeating, CabinPart.Temperature)),
                before,
            )
            else -> this
        }
    }

    /**
     * Inside air for a long time makes the driver drowsy and the glass fog up, so outside air is
     * let in for a moment ([SyncDeskEvent.FreshAirDone] ends it). The air cleaner stays on through it.
     */
    private fun onFreshAirDue(state: SyncDeskState, before: Int): SyncDeskState {
        val tuning = state.tuning ?: return state
        if (!state.holdsAirInside) return state
        return state.copy(tuning = tuning.copy(airing = true, longRecirculation = false))
            .tune(CabinChange(recirculation = false), before)
    }

    private fun onFreshAirDone(state: SyncDeskState, before: Int): SyncDeskState {
        val tuning = state.tuning ?: return state
        if (!tuning.airing) return state
        return state.copy(tuning = tuning.copy(airing = false)).tune(CabinChange(recirculation = true), before)
    }

    /**
     * Once the air has been drawn from inside for a while ([SyncDeskEvent.RecirculationLong]) and
     * the wipers are on, the windshield defroster is switched on and stays on until the call ends.
     */
    private fun SyncDeskState.applyDefrostRule(before: Int): SyncDeskState {
        val current = tuning ?: return this
        val due = current.longRecirculation && signals.wiper != WiperSpeed.Off &&
            !cabin.defrosterOn && current.asked.defrosterOn == null
        return if (due) tune(CabinChange(defrosterOn = true), before) else this
    }

    /**
     * While what is said is being marked as missed, an ambient light that is on shows it by its
     * colour, so the driver knows without looking at the screen; the colour goes back when the
     * marking ends. A light that is off is left off.
     */
    private fun SyncDeskState.applyMarkLightRule(before: Int): SyncDeskState {
        val current = tuning ?: return this
        val shown = current.asked.ambientColor == CabinLimits.MISSED_AMBIENT_COLOR
        return when {
            markingMissed && !shown && cabin.ambientPercent > 0 && cabin.ambientColor != CabinLimits.MISSED_AMBIENT_COLOR ->
                tune(CabinChange(ambientColor = CabinLimits.MISSED_AMBIENT_COLOR), before)
            !markingMissed && shown -> tune(CabinChange(ambientColor = current.original.ambientColor), before)
            else -> this
        }
    }

    /**
     * The app was stopped while the cabin was tuned, and [restore] is what puts it back. With no
     * call to tune for, it is put back at once. On a call that is being tuned again, the cabin
     * found at the restart was not the driver's own: the parts in [restore] count as moved by the
     * app and go back to the restored values when the call ends.
     */
    private fun onQuietRecovered(state: SyncDeskState, restore: CabinChange, before: Int): SyncDeskState {
        val tuning = state.tuning ?: return state.issue(restore, before)
        return state.copy(
            tuning = tuning.copy(
                original = tuning.original.applying(restore),
                asked = state.cabin.asChange().only(restore.parts) + tuning.asked,
            ),
        )
    }

    // endregion

    // region Capture

    private fun RoadDemand.isTurn() = this == RoadDemand.TurningLeft || this == RoadDemand.TurningRight

    /**
     * Follows whether the driver can hear the call: a demanding stretch starts when the road gets
     * demanding during a call and ends when it clears, a moment later after a turn.
     */
    private fun SyncDeskState.applyCaptureRule(): SyncDeskState {
        val demand = roadDemand
        // Only a driver who is driving misses what is said because of the road.
        val listening = consented && call.active && signals.stage == DriveStage.Driving
        // A turn that comes back before the hold runs out simply carries on.
        val state = if (demand != RoadDemand.Clear && heldDemand != null) copy(heldDemand = null) else this
        return when (val current = state.capture) {
            CaptureState.Idle -> if (listening && demand != RoadDemand.Clear) {
                state.copy(capture = CaptureState.Recording(demand, lineCount = 0))
            } else {
                state
            }
            is CaptureState.Recording -> when {
                !listening -> state.copy(capture = CaptureState.Idle, heldDemand = null)
                // A turn is short, and so is a lane change with the signal on: a sentence under way
                // when it ends was still missed, so the stretch lasts a moment longer
                // ([SyncDeskEvent.DemandHoldExpired]).
                demand == RoadDemand.Clear && current.reason.isTurn() -> if (state.heldDemand == null) {
                    state.copy(heldDemand = current.reason)
                } else {
                    state
                }
                demand == RoadDemand.Clear -> state.copy(capture = CaptureState.Idle)
                demand != current.reason -> state.copy(capture = current.copy(reason = demand))
                else -> state
            }
        }
    }

    /** The moment after a turn is over: what is said from now on is heard again. */
    private fun onDemandHoldExpired(state: SyncDeskState): SyncDeskState {
        if (state.capture !is CaptureState.Recording) return state.copy(heldDemand = null)
        if (state.heldDemand == null || state.roadDemand != RoadDemand.Clear) return state.copy(heldDemand = null)
        return state.copy(heldDemand = null, capture = CaptureState.Idle)
    }

    /**
     * Every sentence of a call goes into that call's memo, which begins with the first one. A
     * sentence of the others' is marked as missed when it is said during a demanding stretch, or
     * once the cabin has been too loud to hear the call for a whole tuning interval
     * ([SyncDeskState.missingForNoise]); where both are true, the road is the reason given. The
     * driver's own promise never is.
     */
    private fun onNoteCaptured(state: SyncDeskState, heard: MeetingNote): SyncDeskState {
        // Nothing from a call is kept before the driver has agreed to it.
        if (!state.consented || !state.call.active) return state
        val stretch = (state.capture as? CaptureState.Recording)?.takeIf { !heard.byDriver }
        val current = state.callMemo
        val memo = current ?: CallMemo(
            id = (state.memos.maxOfOrNull { it.id } ?: 0) + 1,
            title = state.meeting.title,
            date = state.call.date,
            startedAt = state.call.startedAt,
            trip = state.trip,
        )
        val missed = when {
            heard.byDriver -> null
            stretch != null -> MissedReason.Driving
            state.missingForNoise -> MissedReason.Noise
            else -> null
        }
        val note = heard.copy(id = memo.notes.size + 1, missed = missed)
        val written = memo.copy(notes = memo.notes + note)
        return state.copy(
            memos = if (current == null) {
                (state.memos + written).takeLast(MAX_MEMOS)
            } else {
                state.memos.map { if (it.id == written.id) written else it }
            },
            call = state.call.copy(memoId = written.id),
            capture = stretch?.copy(lineCount = stretch.lineCount + 1) ?: state.capture,
        )
    }

    // endregion

    // region Dialog

    /**
     * The car has said what the step opens with. After the offer and the question it listens;
     * after the explanation the memo counts as heard and the question follows; after an answer it
     * listens for the next question without asking again.
     */
    private fun onDialogSpoken(state: SyncDeskState): SyncDeskState {
        val dialog = state.dialog ?: return state
        if (dialog.said || state.roadDemand != RoadDemand.Clear) return state
        return when (dialog.step) {
            DialogStep.Offer, DialogStep.Ask -> state.copy(dialog = dialog.copy(said = true))
            DialogStep.Explain -> state.copy(
                dialog = PostCallDialog(dialog.memoId, DialogStep.Ask),
                memos = state.memos.map { if (it.id == dialog.memoId) it.copy(explained = true) else it },
            )
            DialogStep.Answer -> state.copy(dialog = PostCallDialog(dialog.memoId, DialogStep.Ask, said = true))
        }
    }

    /**
     * A reply counts only while the car listens. Declining or stopping ends the talk. Agreeing
     * takes the offer up; once the car has asked for questions, anything else is one.
     */
    private fun onDriverReplied(state: SyncDeskState, reply: DriverReply): SyncDeskState {
        val dialog = state.dialog ?: return state
        if (!state.listeningForReply) return state
        return when (reply) {
            DriverReply.No, DriverReply.Stop -> state.copy(dialog = null)
            DriverReply.Yes -> if (dialog.step == DialogStep.Offer) {
                state.copy(dialog = PostCallDialog(dialog.memoId, DialogStep.Explain))
            } else {
                state
            }
            is DriverReply.Other -> if (dialog.step == DialogStep.Ask) {
                state.copy(dialog = PostCallDialog(dialog.memoId, DialogStep.Answer, question = reply.text))
            } else {
                state
            }
        }
    }

    /**
     * The talk gives way to a new call, and ends once the desk is open: the memo is on screen
     * then. On a demanding stretch the car stops talking and stops listening; when the road
     * clears it says the step it was at again from the start.
     */
    private fun SyncDeskState.applyDialogRule(): SyncDeskState {
        val current = dialog ?: return this
        return when {
            call.active || signals.deskOpen -> copy(dialog = null)
            roadDemand != RoadDemand.Clear && current.said -> copy(dialog = current.copy(said = false))
            else -> this
        }
    }

    // endregion

    // region Office

    /** The parts of the cabin each step moves, and so the ones it puts back. */
    private val STEP_PARTS: Map<OfficeStep, Set<CabinPart>> = mapOf(
        OfficeStep.SteeringWheel to setOf(CabinPart.SteeringWheel),
        OfficeStep.SeatPosition to setOf(CabinPart.SeatSlide, CabinPart.SeatHeight),
        OfficeStep.Cushion to setOf(CabinPart.CushionTilt, CabinPart.CushionLength),
        OfficeStep.Backrest to setOf(CabinPart.Seat, CabinPart.Lumbar),
        OfficeStep.Lights to setOf(CabinPart.ReadingLight, CabinPart.CabinLight, CabinPart.Ambient, CabinPart.AmbientColor),
        OfficeStep.Shade to setOf(CabinPart.Shade),
        OfficeStep.Climate to setOf(CabinPart.Fan, CabinPart.SeatVentilation, CabinPart.Window),
        OfficeStep.DoorLock to setOf(CabinPart.DoorLock),
        // The USB-C port is not part of the cabin state: see [SyncDeskState.usbOrder].
        OfficeStep.Usb to emptySet(),
    )

    /**
     * The parts that differ between working and a meeting: light, shade, fan and window. No part
     * of the seat or the steering wheel is among them, so switching never moves either.
     */
    val SCENE_PARTS: Set<CabinPart> =
        setOf(CabinPart.Ambient, CabinPart.AmbientColor, CabinPart.Shade, CabinPart.Fan, CabinPart.Window)

    /**
     * Office mode moves the seat and changes lights and locks: vehicle settings, which the
     * platform forbids while it restricts setup (a moving car). Putting things back is always
     * allowed. The cabin as it is now is remembered for that; what a tuned call has moved is
     * remembered as it will be once the call is over, because that is the driver's own setting.
     */
    private fun onOfficeRequested(state: SyncDeskState): SyncDeskState {
        if (!state.signals.deskOpen || state.office.phase != OfficePhase.Off || state.ux.noSetup) return state
        val snapshot = state.tuning?.let { state.cabin.applying(it.restore) } ?: state.cabin
        return state.copy(office = OfficeState(phase = OfficePhase.Applying, snapshot = snapshot))
    }

    /** A step is set up: the next one may begin, and after the last one office mode is on. */
    private fun onOfficeStepDone(state: SyncDeskState, step: OfficeStep): SyncDeskState {
        val office = state.office
        if (office.phase != OfficePhase.Applying || step in office.completed) return state
        val completed = office.completed + step
        return if (completed.containsAll(OfficeStep.entries)) {
            state.copy(office = office.copy(phase = OfficePhase.On, completed = completed, paused = false))
        } else {
            state.copy(office = office.copy(completed = completed))
        }
    }

    /** A step is put back: the one before it may follow, and after the first one office mode is off. */
    private fun onOfficeStepUndone(state: SyncDeskState, step: OfficeStep): SyncDeskState {
        val office = state.office
        if (office.phase != OfficePhase.Restoring || step !in office.completed) return state
        val left = office.completed - step
        return state.copy(office = if (left.isEmpty()) OfficeState() else office.copy(completed = left))
    }

    /** A stopped sequence goes on the way it was going. Setting up needs the platform's leave; putting back never does. */
    private fun onOfficeResumeRequested(state: SyncDeskState): SyncDeskState {
        val office = state.office
        if (!office.paused) return state
        if (office.phase == OfficePhase.Applying && state.ux.noSetup) return state
        return state.copy(office = office.copy(paused = false))
    }

    /**
     * Everything goes back, the last step first and the steering wheel last. Asked for in the
     * middle of setting up, that includes the step that was under way: it may have moved already.
     */
    private fun onOfficeRestoreRequested(state: SyncDeskState): SyncDeskState {
        val office = state.office
        return when (office.phase) {
            OfficePhase.On -> state.copy(office = office.copy(phase = OfficePhase.Restoring, paused = false))
            OfficePhase.Applying -> state.copy(
                office = office.copy(
                    phase = OfficePhase.Restoring,
                    paused = false,
                    completed = office.completed + setOfNotNull(office.pending?.step),
                ),
            )
            OfficePhase.Restoring -> state.copy(office = office.copy(paused = false))
            OfficePhase.Off -> state
        }
    }

    /**
     * Working or a meeting, chosen while office mode is on. Only light, shade, fan and window
     * follow ([SCENE_PARTS]); the seat and the steering wheel stay where they are.
     */
    private fun onOfficeSceneChanged(state: SyncDeskState, scene: OfficeScene, before: Int): SyncDeskState {
        val office = state.office
        if (office.phase != OfficePhase.On || office.scene == scene || !state.signals.deskOpen || state.ux.noSetup) return state
        val was = sceneTargets(state, office.scene)
        val now = sceneTargets(state, scene)
        // What only the scene being left had set goes back to where it was before office mode.
        val released = (office.snapshot ?: state.cabin).asChange().only(was.parts - now.parts)
        val changed = now.only(now.parts.filterTo(mutableSetOf()) { now[it] != was[it] })
        return state.copy(office = office.copy(scene = scene)).issue(released + changed, before)
    }

    /**
     * What [scene] wants of the parts that differ between working and a meeting.
     *
     * The fan only ever goes down: to the level that is quiet enough for a call, and one below
     * that for a meeting, but never off and never while the climate system is switched off.
     * Working leaves the window and the colour of the ambient light as the driver has them.
     */
    private fun sceneTargets(state: SyncDeskState, scene: OfficeScene): CabinChange {
        val before = state.office.snapshot ?: state.cabin
        val quiet = state.ranges.quietFanLevel
        val level = when (scene) {
            OfficeScene.Work -> quiet
            OfficeScene.Meeting -> maxOf(quiet - 1, 1, state.ranges.fanMin)
        }
        val fan = minOf(level, before.fanLevel).takeIf { before.climateOn }
        return when (scene) {
            OfficeScene.Work -> CabinChange(
                ambientPercent = state.preset.ambientPercent,
                shadePercent = state.preset.shadePercent,
                fanLevel = fan,
            )
            OfficeScene.Meeting -> CabinChange(
                ambientPercent = CabinLimits.MEETING_AMBIENT,
                ambientColor = CabinLimits.MEETING_AMBIENT_COLOR,
                shadePercent = CabinLimits.MEETING_SHADE,
                fanLevel = fan,
                windowOpenPercent = CabinLimits.MEETING_WINDOW_OPEN,
            )
        }
    }

    /**
     * What [step] asks of the cabin: the driver's own arrangement ([SyncDeskState.preset]), and
     * for light, shade and climate what working or a meeting calls for. One step at a time. The
     * USB-C step asks nothing of the cabin; see [SyncDeskState.usbOrder].
     */
    fun officeChange(step: OfficeStep, state: SyncDeskState): CabinChange {
        val preset = state.preset
        val scene = sceneTargets(state, state.office.scene)
        return when (step) {
            OfficeStep.SteeringWheel -> CabinChange(wheel = preset.wheel)
            OfficeStep.SeatPosition -> CabinChange(seatSlide = preset.seatSlide, seatHeight = preset.seatHeight)
            OfficeStep.Cushion -> CabinChange(cushionTilt = preset.cushionTilt, cushionLength = preset.cushionLength)
            OfficeStep.Backrest -> CabinChange(seatRecline = preset.seatRecline, lumbar = preset.lumbar)
            OfficeStep.Lights -> CabinChange(readingLightOn = preset.readingLightOn, cabinLightOn = preset.cabinLightOn) +
                scene.only(setOf(CabinPart.Ambient, CabinPart.AmbientColor))
            OfficeStep.Shade -> scene.only(setOf(CabinPart.Shade))
            // A seat the driver already ventilates more, or heats, is left as it is: ventilation
            // and heating are one setting in the vehicle, and putting the ventilation back would
            // not bring the heating back.
            OfficeStep.Climate -> {
                val before = state.office.snapshot ?: state.cabin
                CabinChange(
                    seatVentilation = preset.seatVentilation.takeIf { it > before.seatVentilation && before.seatHeating == 0 },
                ) + scene.only(setOf(CabinPart.Fan, CabinPart.Window))
            }
            OfficeStep.DoorLock -> CabinChange(doorsLocked = true.takeIf { preset.lockDoors })
            OfficeStep.Usb -> CabinChange.NONE
        }
    }

    /** Everything office mode asks of the cabin as it is set up now, all steps together. */
    private fun officeTargets(state: SyncDeskState): CabinChange =
        OfficeStep.entries.fold(CabinChange.NONE) { all, step -> all + officeChange(step, state) }

    /** The parts office mode holds right now; another rule leaves them to it. */
    private fun SyncDeskState.officeHeldParts(): Set<CabinPart> =
        if (office.phase == OfficePhase.Off) emptySet() else officeTargets(this).parts

    /**
     * The driver changed their office arrangement. It is only accepted while the desk is open,
     * and if office mode is on, the cabin follows the new values at once.
     */
    private fun onPresetChanged(state: SyncDeskState, preset: OfficePreset, before: Int): SyncDeskState {
        if (!state.signals.deskOpen || state.ux.noSetup) return state
        val accepted = preset.normalized()
        val old = state.preset
        val next = state.copy(preset = accepted)
        if (state.office.phase != OfficePhase.On || accepted == old) return next
        val was = officeTargets(state)
        val now = officeTargets(next)
        return next.issue(now.only(now.parts.filterTo(mutableSetOf()) { now[it] != was[it] }), before)
    }

    /**
     * What puts [step] back: the parts it moved, as they were before office mode. A part the step
     * left alone (the window while working, a seat already ventilated) is not asked for, and
     * neither is a fan that was not running then.
     */
    fun officeRestoreChange(step: OfficeStep, state: SyncDeskState): CabinChange =
        officeRestoreChange(state, STEP_PARTS.getValue(step) intersect officeChange(step, state).parts)

    private fun officeRestoreChange(state: SyncDeskState, parts: Set<CabinPart>): CabinChange {
        val snapshot = state.office.snapshot ?: return CabinChange.NONE
        val untouched = if (snapshot.climateOn) emptySet() else setOf(CabinPart.Fan)
        return snapshot.asChange().only(parts - untouched)
    }

    /**
     * The desk closes the moment the driver has to drive: leaving Park, or taking the wheel back
     * from a car that was driving itself. Office mode ends at once, wherever its sequence had got
     * to, and everything is asked back in one go; the USB-C port goes back with it
     * ([SyncDeskState.usbOrder]). This is a safety net, not the normal path: a vehicle may refuse
     * cabin controls once the driver is driving (Pleos Connect answers ERROR_DRIVING_RESTRICTED
     * for the reading light in Drive), so the screen asks the driver to put things back first.
     * Doors locked for working stay locked: a car that drives off is not unlocked.
     */
    private fun SyncDeskState.applyOfficeRule(before: Int): SyncDeskState =
        if (!signals.deskOpen && office.phase != OfficePhase.Off) {
            val parts = officeTargets(this).parts - CabinPart.DoorLock
            copy(office = OfficeState()).issue(officeRestoreChange(this, parts), before)
        } else {
            this
        }
    // endregion
}
