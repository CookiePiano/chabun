package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 3A: the drive's calls and contacts shown on parking, and office mode as a laptop workspace. */
class ParkedDigestAndOfficeTest {

    private val cabin = CabinState(fanLevel = 6, windowOpenPercent = 30, recirculation = false)
    private val parked = SyncDeskState(signals = VehicleSignals(gear = Gear.Park), cabin = cabin)

    private fun SyncDeskState.after(vararg events: SyncDeskEvent) = afterEvents(*events)

    private val drive = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive))
    private val park = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Park))
    private val selfDriving = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive, autonomous = true))
    private val callStarts = SyncDeskEvent.CallChanged(active = true, driverSpeaking = false, date = "2026-11-15", time = "14:00")
    private val callEnds = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)
    private fun note(id: Int) = SyncDeskEvent.NoteCaptured(MeetingNote(id, "14:0$id", "speaker", "text $id", "gist $id"))

    /** A call with one sentence, ended. */
    private fun SyncDeskState.withCall() = after(callStarts, note(1), callEnds)

    private fun contacts(vararg contacts: Contact) = SyncDeskEvent.ContactsReported(contacts.toList())
    private fun missed(id: String, from: String) = Contact(id, ContactKind.MissedCall, from)
    private fun message(id: String, from: String) = Contact(id, ContactKind.Message, from)

    // region The drive

    @Test
    fun `a drive lasts from leaving Park to the next stop and each memo knows its drive`() {
        val first = parked.after(drive).withCall().after(park)
        val second = first.after(drive).withCall().withCall().after(park)

        assertEquals(1, first.trip)
        assertEquals(listOf(1), first.memos.map { it.trip })
        assertEquals(2, second.trip)
        assertEquals(listOf(1, 2, 2), second.memos.map { it.trip })
        // A call taken at the stop still counts to the drive that ended there.
        assertEquals(2, second.withCall().memos.last().trip)
    }

    @Test
    fun `parking lists this drive's memos with the unexplained first and earlier drives apart`() {
        val earlier = parked.after(drive).withCall().after(park)
        val drove = earlier.after(drive).withCall().withCall().withCall()
        // The car talked the driver through the second call of this drive.
        val explained = drove.copy(memos = drove.memos.map { if (it.id == 3) it.copy(explained = true) else it })
        val state = explained.after(park)

        assertEquals(listOf(4, 2, 3), state.tripMemos.map { it.id })
        assertEquals(listOf(1), state.earlierMemos.map { it.id })
    }

    @Test
    fun `a drive without calls has no memos to list but keeps the earlier ones`() {
        val state = parked.after(drive).withCall().after(park, drive, park)

        assertTrue(state.tripMemos.isEmpty())
        assertEquals(1, state.earlierMemos.size)
    }

    // endregion

    // region Contacts not looked at

    @Test
    fun `the contact line is never there while driving`() {
        val state = parked.after(drive, contacts(missed("a", "Kim")))

        assertNull(state.contactDigest)
        assertNull(state.after(selfDriving).contactDigest)
        assertFalse(state.hasParkedDigest)
    }

    @Test
    fun `only what is still not looked at when parking is counted`() {
        val state = parked.after(
            drive,
            contacts(missed("a", "Kim"), message("m1", "Park")),
            // Read at a red light, and the call returned: both gone from the phone's list.
            contacts(message("m2", "Jung")),
            park,
        )

        assertEquals(ContactDigest(null, ContactGroup(1, listOf("Jung"), 0)), state.contactDigest)
        assertTrue(state.hasParkedDigest)
    }

    @Test
    fun `what was already waiting before the drive belongs to the stop before`() {
        val state = parked.after(contacts(missed("old", "Mum")), drive, contacts(missed("old", "Mum"), missed("new", "Kim")), park)

        assertEquals(ContactDigest(ContactGroup(1, listOf("Kim"), 0), null), state.contactDigest)
    }

    @Test
    fun `nothing to show means no line and nothing to bring forward`() {
        val state = parked.after(drive, park)

        assertNull(state.contactDigest)
        assertFalse(state.hasParkedDigest)
    }

    @Test
    fun `each kind names its first two people and counts the rest, never the same person twice`() {
        val digest = ContactDigest.of(
            listOf(
                missed("1", "Kim"), missed("2", "Mum"),
                message("3", "Park"), message("4", "Jung"), message("5", "Park"), message("6", "Choi"), message("7", "Han"),
            ),
        )

        assertEquals(ContactGroup(2, listOf("Kim", "Mum"), 0), digest?.missedCalls)
        assertEquals(ContactGroup(5, listOf("Park", "Jung"), 2), digest?.messages)
        assertNull(ContactDigest.of(emptyList()))
    }

    // endregion

    // region Office mode: order, stopping, putting back

    private val office get() = parked.after(SyncDeskEvent.OfficeRequested)

    @Test
    fun `office mode takes its nine steps in order, one at a time, wheel first`() {
        val moves = mutableListOf<OfficeMove>()
        val on = office.afterOfficeMoves(moves)

        assertEquals(OfficeStep.entries.map { OfficeMove(it, undo = false) }, moves)
        assertEquals(OfficePhase.On, on.office.phase)
        val preset = OfficePreset()
        assertEquals(preset.wheel, on.cabin.wheel)
        assertEquals(preset.seatSlide to preset.seatHeight, on.cabin.seatSlide to on.cabin.seatHeight)
        assertEquals(preset.cushionTilt to preset.cushionLength, on.cabin.cushionTilt to on.cabin.cushionLength)
        assertEquals(CabinLimits.OFFICE_SEAT_RECLINE, on.cabin.seatRecline)
        assertEquals(CabinLimits.OFFICE_LUMBAR, on.cabin.lumbar)
        assertTrue(on.cabin.readingLightOn)
        assertFalse(on.cabin.cabinLightOn)
        assertEquals(CabinLimits.OFFICE_AMBIENT, on.cabin.ambientPercent)
        assertEquals(CabinLimits.OFFICE_SHADE, on.cabin.shadePercent)
        assertEquals(on.ranges.quietFanLevel, on.cabin.fanLevel)
        assertEquals(CabinLimits.OFFICE_SEAT_VENTILATION, on.cabin.seatVentilation)
        assertTrue(on.cabin.doorsLocked)
        // Working leaves the window as the driver has it.
        assertEquals(30, on.cabin.windowOpenPercent)
    }

    @Test
    fun `the default backrest is upright now, not reclined`() {
        assertEquals(0, OfficePreset().seatRecline)
    }

    @Test
    fun `the cabin light stays off unless the driver's arrangement asks for it, and goes off again`() {
        assertFalse(OfficePreset().cabinLightOn)
        val lit = parked.copy(preset = OfficePreset(cabinLightOn = true)).afterOfficeOn()
        assertTrue(lit.cabin.cabinLightOn)

        val off = lit.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeMoves()
        assertFalse(off.cabin.cabinLightOn)
        assertFalse(off.cabin.readingLightOn)
    }

    @Test
    fun `a seat the driver heats is not ventilated for working, since putting the ventilation back would not bring the heat back`() {
        val on = parked.copy(cabin = cabin.copy(seatHeating = 2)).afterOfficeOn()

        assertNull(SyncDeskEngine.officeChange(OfficeStep.Climate, on).seatVentilation)
        assertEquals(2, on.cabin.seatHeating)
        assertEquals(0, on.cabin.seatVentilation)
    }

    @Test
    fun `the next step waits until the vehicle has finished the one before`() {
        val first = office

        assertEquals(OfficeMove(OfficeStep.SteeringWheel, undo = false), first.office.move)
        // Nothing but the wheel is asked of the cabin until it reports done.
        assertEquals(setOf(CabinPart.SteeringWheel), SyncDeskEngine.officeChange(OfficeStep.SteeringWheel, first).parts)
        assertEquals(OfficeMove(OfficeStep.SeatPosition, undo = false), first.afterOfficeStep(OfficeStep.SteeringWheel).office.move)
        // A step reported out of turn twice does not count twice.
        assertEquals(first.afterOfficeStep(OfficeStep.SteeringWheel).office, first.afterOfficeStep(OfficeStep.SteeringWheel).after(SyncDeskEvent.OfficeStepDone(OfficeStep.SteeringWheel)).office)
    }

    @Test
    fun `putting back goes in the opposite order, wheel last, to the values from before`() {
        val moves = mutableListOf<OfficeMove>()
        val off = office.afterOfficeMoves().after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeMoves(moves)

        assertEquals(OfficeStep.entries.reversed().map { OfficeMove(it, undo = true) }, moves)
        assertEquals(OfficeState(), off.office)
        assertEquals(cabin, off.cabin)
    }

    @Test
    fun `stopping halts the sequence where it is and carrying on takes the same step again`() {
        val underWay = office.afterOfficeStep(OfficeStep.SteeringWheel)
        val stopped = underWay.after(SyncDeskEvent.OfficePauseRequested)
        val resumed = stopped.after(SyncDeskEvent.OfficeResumeRequested)

        assertTrue(stopped.office.paused)
        assertNull(stopped.office.move)
        assertEquals(OfficeStep.SeatPosition, stopped.office.pending?.step)
        // The step that was stopped half-way is not counted as done.
        assertEquals(setOf(OfficeStep.SteeringWheel), stopped.office.completed)
        assertEquals(OfficeMove(OfficeStep.SeatPosition, undo = false), resumed.office.move)
        assertEquals(OfficePhase.On, resumed.afterOfficeMoves().office.phase)
    }

    @Test
    fun `after stopping, putting back also undoes the step that was under way, then the ones before`() {
        val stopped = office.afterOfficeStep(OfficeStep.SteeringWheel).after(SyncDeskEvent.OfficePauseRequested)
        // The seat had moved part of the way when it was stopped.
        val halfWay = stopped.after(SyncDeskEvent.CabinReported(stopped.cabin.copy(seatSlide = 65)))
        val moves = mutableListOf<OfficeMove>()
        val off = halfWay.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeMoves(moves)

        assertEquals(listOf(OfficeMove(OfficeStep.SeatPosition, true), OfficeMove(OfficeStep.SteeringWheel, true)), moves)
        assertEquals(cabin, off.cabin)
        assertEquals(OfficePhase.Off, off.office.phase)
    }

    @Test
    fun `putting back can be stopped and carried on too`() {
        val restoring = office.afterOfficeMoves().after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeUndo(OfficeStep.Usb)
        val stopped = restoring.after(SyncDeskEvent.OfficePauseRequested)
        val resumed = stopped.after(SyncDeskEvent.OfficeResumeRequested)

        assertNull(stopped.office.move)
        assertEquals(OfficeMove(OfficeStep.DoorLock, undo = true), resumed.office.move)
    }

    @Test
    fun `nothing is stopped when nothing moves`() {
        val on = office.afterOfficeMoves()

        assertEquals(on, on.after(SyncDeskEvent.OfficePauseRequested))
        assertEquals(parked, parked.after(SyncDeskEvent.OfficePauseRequested))
    }

    @Test
    fun `leaving Park in the middle of the sequence puts everything back at once but leaves the doors locked`() {
        val partWay = office.afterOfficeMoves().copy().let { on ->
            on.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeUndo(OfficeStep.Usb)
        }
        val state = partWay.after(drive)

        assertEquals(OfficeState(), state.office)
        assertTrue(state.cabin.doorsLocked)
        assertEquals(cabin, state.cabin.copy(doorsLocked = false))
        // With office mode gone the USB-C port goes back to the vehicle's ordinary output.
        assertNull(state.usbOrder)
    }

    @Test
    fun `while the platform forbids setup office mode cannot start or carry on, but can always go back`() {
        val locked = SyncDeskEvent.UxLimitsChanged(UxLimits(noSetup = true))
        val stopped = office.afterOfficeStep(OfficeStep.SteeringWheel).after(SyncDeskEvent.OfficePauseRequested, locked)

        assertEquals(OfficePhase.Off, parked.after(locked, SyncDeskEvent.OfficeRequested).office.phase)
        assertTrue(stopped.after(SyncDeskEvent.OfficeResumeRequested).office.paused)
        val back = stopped.after(SyncDeskEvent.OfficeRestoreRequested)
        assertEquals(OfficePhase.Restoring, back.office.phase)
        assertEquals(OfficePhase.Off, back.afterOfficeMoves().office.phase)
    }

    @Test
    fun `a step the vehicle refuses is shown and the sequence goes on without it`() {
        val refused = office.after(SyncDeskEvent.CommandRefused(CabinPart.SteeringWheel), SyncDeskEvent.OfficeStepDone(OfficeStep.SteeringWheel))

        assertEquals(CabinPart.SteeringWheel, refused.shownRefusal)
        assertEquals(cabin.wheel, refused.cabin.wheel)
        assertEquals(OfficeStep.SeatPosition, refused.office.move?.step)
    }

    // endregion

    // region Work and meeting

    @Test
    fun `switching to a meeting moves light, shade, fan and window but never the seat or the wheel`() {
        val on = office.afterOfficeMoves()
        val switched = SyncDeskEngine.reduce(on, SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting))
        val meeting = on.after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting))

        val asked = switched.command!!.change.parts
        assertTrue(asked.isNotEmpty())
        assertTrue(asked.all { it in SyncDeskEngine.SCENE_PARTS })
        assertEquals(on.cabin.copy(ambientPercent = 0, ambientColor = 0, shadePercent = 0, fanLevel = 0, windowOpenPercent = 0),
            meeting.cabin.copy(ambientPercent = 0, ambientColor = 0, shadePercent = 0, fanLevel = 0, windowOpenPercent = 0))
        assertEquals(CabinLimits.MEETING_AMBIENT, meeting.cabin.ambientPercent)
        assertEquals(CabinLimits.MEETING_AMBIENT_COLOR, meeting.cabin.ambientColor)
        assertEquals(CabinLimits.MEETING_SHADE, meeting.cabin.shadePercent)
        assertEquals(on.cabin.fanLevel - 1, meeting.cabin.fanLevel)
        assertEquals(0, meeting.cabin.windowOpenPercent)
        assertTrue(meeting.cabin.readingLightOn)
    }

    @Test
    fun `back to work, what only the meeting set goes back to where it was before office mode`() {
        val on = office.afterOfficeMoves()
        val work = on.after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting), SyncDeskEvent.OfficeSceneChanged(OfficeScene.Work))

        assertEquals(on.cabin.copy(ambientColor = cabin.ambientColor), work.cabin)
        assertEquals(30, work.cabin.windowOpenPercent)
    }

    @Test
    fun `putting back after a meeting leaves nothing of it behind`() {
        val off = office.afterOfficeMoves().after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting), SyncDeskEvent.OfficeRestoreRequested)
            .afterOfficeMoves()

        assertEquals(cabin, off.cabin)
    }

    @Test
    fun `the scene can only be switched while office mode is on`() {
        assertEquals(OfficeScene.Work, office.after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting)).office.scene)
        assertEquals(OfficeScene.Work, parked.after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting)).office.scene)
    }

    @Test
    fun `a fan that is switched off is never switched on by office mode`() {
        val climateOff = parked.copy(cabin = cabin.copy(fanLevel = 0))
        val on = climateOff.afterOfficeOn()
        val meeting = on.after(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting))

        assertEquals(0, on.cabin.fanLevel)
        assertEquals(0, meeting.cabin.fanLevel)
        assertEquals(climateOff.cabin, on.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeMoves().cabin)
    }

    // endregion

    // region USB-C power and the battery

    @Test
    fun `the port delivers what the device asks for, up to what it can give and its limit`() {
        assertEquals(140, UsbPort(on = true, deviceWatts = 140).suppliedWatts)
        assertEquals(240, UsbPort(on = true, deviceWatts = 240).suppliedWatts)
        assertEquals(100, UsbPort(maxWatts = 100, on = true, deviceWatts = 140).suppliedWatts)
        assertEquals(60, UsbPort(on = true, limitWatts = 60, deviceWatts = 140).suppliedWatts)
        assertEquals(0, UsbPort(on = true, deviceWatts = null).suppliedWatts)
        assertEquals(0, UsbPort(on = false, deviceWatts = 140).suppliedWatts)
    }

    @Test
    fun `office mode switches the port to full output only once its step is reached and gives it back after`() {
        val beforeUsb = office.afterOfficeMoves().let { it.copy(office = it.office.copy(completed = it.office.completed - OfficeStep.Usb)) }
        val on = office.afterOfficeMoves()
        val off = on.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeUndo(OfficeStep.Usb)

        assertNull(parked.usbOrder)
        assertNull(beforeUsb.usbOrder)
        assertEquals(UsbOrder(on = true, limitWatts = UsbPort.MAX_WATTS), on.usbOrder)
        assertNull(off.usbOrder)
        assertNull(parked.after(SyncDeskEvent.PresetChanged(OfficePreset(usbPower = false))).afterOfficeOn().usbOrder)
    }

    @Test
    fun `below thirty percent the port is held to sixty watts, below twenty it is switched off`() {
        val on = office.afterOfficeMoves()
        fun at(percent: Int) = on.after(SyncDeskEvent.CabinReported(on.cabin.copy(batteryPercent = percent)))

        assertEquals(30, BatteryGuard.REDUCE_BELOW)
        assertEquals(20, BatteryGuard.OFF_BELOW)
        assertEquals(60, BatteryGuard.REDUCED_WATTS)
        assertEquals(BatteryGuard.None, at(30).batteryGuard)
        assertEquals(UsbOrder(true, 240), at(30).usbOrder)
        assertEquals(BatteryGuard.Reduced, at(29).batteryGuard)
        assertEquals(UsbOrder(true, 60), at(29).usbOrder)
        assertEquals(BatteryGuard.Reduced, at(20).batteryGuard)
        assertEquals(BatteryGuard.Off, at(19).batteryGuard)
        assertEquals(UsbOrder(false, 0), at(19).usbOrder)
        // Charged again, the port comes back.
        assertEquals(UsbOrder(true, 240), at(19).after(SyncDeskEvent.CabinReported(on.cabin.copy(batteryPercent = 50))).usbOrder)
        // Without office mode the battery guards nothing.
        assertEquals(BatteryGuard.None, parked.after(SyncDeskEvent.CabinReported(cabin.copy(batteryPercent = 10))).batteryGuard)
    }

    // endregion

    // region Office mode and a tuned call

    @Test
    fun `office commands during a tuned call are not taken for the driver's hand and each feature puts back its own`() {
        val tuned = SyncDeskState(cabin = cabin).after(selfDriving, callStarts)
        val on = tuned.afterOfficeOn()

        assertTrue(on.isTuning)
        assertEquals(emptySet<CabinPart>(), on.tuning?.overridden)
        assertEquals(on.ranges.quietFanLevel, on.cabin.fanLevel)

        // The call ends: tuning puts back the window and the air, and leaves the fan to office mode.
        val hungUp = on.after(callEnds)
        assertEquals(30, hungUp.cabin.windowOpenPercent)
        assertFalse(hungUp.cabin.recirculation)
        assertEquals(on.ranges.quietFanLevel, hungUp.cabin.fanLevel)

        // Office mode then puts back what it moved, to the driver's own values.
        val off = hungUp.after(SyncDeskEvent.OfficeRestoreRequested).afterOfficeMoves()
        assertEquals(cabin, off.cabin)
    }

    @Test
    fun `office mode started during a tuned call remembers the driver's cabin, not the tuned one`() {
        val on = SyncDeskState(cabin = cabin).after(selfDriving, callStarts).afterOfficeOn()

        assertEquals(30, on.office.snapshot?.windowOpenPercent)
        assertEquals(false, on.office.snapshot?.recirculation)
    }

    // endregion
}
