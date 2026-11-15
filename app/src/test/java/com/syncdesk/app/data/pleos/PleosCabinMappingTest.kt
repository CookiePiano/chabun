package com.syncdesk.app.data.pleos

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinLimits
import com.syncdesk.app.domain.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway's translations for the cabin parts linked after the stage-2B/3 probe. Values and
 * messages are the ones the probe app got from Pleos Connect v2.1.0 (2026-11-15).
 */
class PleosCabinMappingTest {

    // region Capabilities and areas

    @Test
    fun `read-only parts can be read but not changed`() {
        assertTrue(CapabilityConfig(AccessType.READ).canRead())
        assertFalse(CapabilityConfig(AccessType.READ).canReadWrite())
        assertTrue(CapabilityConfig(AccessType.READ_WRITE).canRead())
        assertTrue(CapabilityConfig(AccessType.READ_WRITE).canReadWrite())
        assertFalse(CapabilityConfig(AccessType.WRITE).canRead())
        assertFalse(CapabilityConfig(AccessType.NONE).canRead())
    }

    @Test
    fun `a light switch is usable only with both on and off`() {
        assertTrue(listOf(LightSwitch.OFF, LightSwitch.ON).switchesOnAndOff())
        assertFalse(listOf(LightSwitch.ON).switchesOnAndOff())
    }

    @Test
    fun `the driver's area is found in per-seat, front-pair and all-seat areas alike`() {
        val perSeat = listOf(SeatArea(SeatValue.ROW_2_LEFT), SeatArea(SeatValue.ROW_1_RIGHT), SeatArea(SeatValue.ROW_1_LEFT))
        assertEquals(SeatArea(SeatValue.ROW_1_LEFT), perSeat.driverArea())

        val frontPair = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
        assertEquals(frontPair, listOf(frontPair).driverArea())

        val allSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        assertEquals(allSeats, listOf(allSeats).driverArea())

        assertNull(listOf(SeatArea(SeatValue.ROW_2_LEFT)).driverArea())
    }

    @Test
    fun `the driver's entry carries its own bounds`() {
        val heating = mapOf(
            SeatArea(SeatValue.ROW_2_LEFT) to AreaBounds(0, 3),
            SeatArea(SeatValue.ROW_1_LEFT) to AreaBounds(-3, 3),
        )
        assertEquals(SeatArea(SeatValue.ROW_1_LEFT) to AreaBounds(-3, 3), heating.driverSeat())
    }

    @Test
    fun `the door locks use the four doors together, never the bonnet`() {
        val doors = DoorArea(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        assertEquals(doors, listOf(DoorArea(DoorValue.HOOD), doors).driverDoorArea())
        assertNull(listOf(DoorArea(DoorValue.HOOD)).driverDoorArea())
    }

    @Test
    fun `the defroster uses the windshield, not the rear window`() {
        val areas = listOf(WindowArea(WindowValue.REAR_WINDSHIELD), WindowArea(WindowValue.FRONT_WINDSHIELD))
        assertEquals(WindowArea(WindowValue.FRONT_WINDSHIELD), areas.windshieldArea())
    }

    // endregion

    // region Failures

    @Test
    fun `failures are told apart by their message`() {
        assertEquals(VehicleFailure.NotSupported, vehicleFailureOf("ERROR_NOT_SUPPORTED_PROPERTY"))
        assertEquals(
            VehicleFailure.NotSupported,
            vehicleFailureOf(
                "java.lang.IllegalArgumentException: property ID is not in carPropertyConfig list, and so it is not supported: 0x21400be5",
            ),
        )
        assertEquals(
            VehicleFailure.OutOfRange,
            vehicleFailureOf(
                "java.lang.IllegalArgumentException: setProperty: value to set must be less than or equal to the area ID max value. " +
                    "- property ID: HVAC_TEMPERATURE_SET area ID: 0x1",
            ),
        )
        assertEquals(
            VehicleFailure.OutOfRange,
            vehicleFailureOf("setProperty: value to set: 0 must not be an unwritable state value. - property ID: HVAC_FAN_DIRECTION"),
        )
        assertEquals(VehicleFailure.DrivingRestricted, vehicleFailureOf("ERROR_DRIVING_RESTRICTED"))
        assertEquals(VehicleFailure.Other, vehicleFailureOf("ERROR_INTERNAL_SERVICE"))
        assertEquals(VehicleFailure.Other, vehicleFailureOf(null))
    }

    // endregion

    // region Air direction

    @Test
    fun `every direction the app sends reads back as itself`() {
        val sent = AirDirection.entries.mapNotNull { direction -> direction.toFanDirection()?.let { direction to it } }
        assertEquals(5, sent.size)
        sent.forEach { (direction, value) -> assertEquals(direction, value.toAirDirection()) }
        assertEquals(FanDirectionType.FLOOR, AirDirection.Feet.toFanDirection())
        assertEquals(FanDirectionType.FACE, AirDirection.Face.toFanDirection())
    }

    @Test
    fun `the two mixes the console ignores are read but never sent`() {
        assertNull(AirDirection.FaceAndWindshield.toFanDirection())
        assertNull(AirDirection.All.toFanDirection())
        assertEquals(AirDirection.FaceAndWindshield, FanDirectionType.FACE_AND_DEFROST.toAirDirection())
        assertEquals(AirDirection.All, FanDirectionType.FACE_AND_FLOOR_AND_DEFROST.toAirDirection())
        assertNull(FanDirectionType.UNKNOWN.toAirDirection())
    }

    @Test
    fun `an empty list of directions does not forbid any`() {
        assertTrue(emptyList<FanDirectionType>().offers(FanDirectionType.FLOOR))
        assertTrue(listOf(FanDirectionType.FLOOR).offers(FanDirectionType.FLOOR))
        assertFalse(listOf(FanDirectionType.FACE).offers(FanDirectionType.FLOOR))
    }

    // endregion

    // region Set temperature

    private val scale = TemperatureScale.of(15f, 30f, 0.5f)

    @Test
    fun `temperatures are sent on the half degree and inside the range`() {
        assertEquals(21.5f, scale.snap(21.5f))
        assertEquals(22.5f, scale.snap(22.3f))
        assertEquals(22f, scale.snap(22.2f))
        assertEquals(30f, scale.snap(31f))
        assertEquals(15f, scale.snap(14f))
    }

    @Test
    fun `a missing or zero step falls back to half a degree`() {
        assertEquals(0.5f, TemperatureScale.of(15f, 30f, null).stepC)
        assertEquals(0.5f, TemperatureScale.of(15f, 30f, 0f).stepC)
        assertEquals(1f, TemperatureScale.of(58f, 88f, 1f).stepC)
    }

    @Test
    fun `a read-back rounded to the step is the value the app asked for, not the driver's hand`() {
        // Asked 21.3 (a degree below 22.3): the vehicle stores 21.5.
        assertEquals(21.3f, reportedOrAsked(21.5f, 21.3f, scale::stores))
        // The driver then turns it to 24.
        assertEquals(24f, reportedOrAsked(24f, 21.3f, scale::stores))
        assertEquals(22f, reportedOrAsked(22f, null, scale::stores))
    }

    // endregion

    // region Seat ventilation and heating

    @Test
    fun `ventilation is the negative of the vehicle value and heating the positive`() {
        assertEquals(-2, seatClimateLevel(ventilation = 2, heating = 0))
        assertEquals(3, seatClimateLevel(ventilation = 0, heating = 3))
        assertEquals(0, seatClimateLevel(ventilation = 0, heating = 0))
        assertEquals(2 to 0, seatClimateOf(-2))
        assertEquals(0 to 3, seatClimateOf(3))
        assertEquals(0 to 0, seatClimateOf(0))
        (-3..3).forEach { level -> assertEquals(level, seatClimateOf(level).let { (v, h) -> seatClimateLevel(v, h) }) }
    }

    @Test
    fun `where both are asked on, ventilation wins`() {
        assertEquals(-1, seatClimateLevel(ventilation = 1, heating = 2))
        assertEquals(-1, seatClimateTarget(CabinState(), CabinChange(seatVentilation = 1, seatHeating = 2)))
    }

    @Test
    fun `switching one on replaces the other, switching one off leaves the other`() {
        val heated = CabinState(seatHeating = 2)
        assertEquals(-1, seatClimateTarget(heated, CabinChange(seatVentilation = CabinLimits.COMFORT_SEAT_LEVEL)))
        assertEquals(2, seatClimateTarget(heated, CabinChange(seatVentilation = 0)))

        val ventilated = CabinState(seatVentilation = 3)
        assertEquals(1, seatClimateTarget(ventilated, CabinChange(seatHeating = 1)))
        assertEquals(-3, seatClimateTarget(ventilated, CabinChange(seatHeating = 0)))

        assertEquals(0, seatClimateTarget(CabinState(seatVentilation = 1), CabinChange(seatVentilation = 0)))
    }

    @Test
    fun `a change that leaves the seat alone sends nothing`() {
        assertNull(seatClimateTarget(CabinState(seatHeating = 2), CabinChange(fanLevel = 3)))
    }

    // endregion

    // region Ambient colour

    @Test
    fun `colours are compared without their alpha`() {
        assertTrue(sameRgb(0xFFAA00FF.toInt(), 0x00AA00FF))
        assertFalse(sameRgb(0xFFAA00FF.toInt(), 0x00AA00FE))
        assertEquals(0xFFFFFFFF.toInt(), opaque(0x00FFFFFF))
        assertEquals(0xFFAA00FF.toInt(), opaque(0xFFAA00FF.toInt()))
    }

    @Test
    fun `the colour read back with alpha 00 is the colour the app asked for`() {
        val asked = CabinLimits.MISSED_AMBIENT_COLOR
        assertEquals(asked, reportedOrAsked(opaque(0x00AA00FF), asked, ::sameRgb))
        // White read before anything was asked is opaque white, the app's own default.
        assertEquals(CabinLimits.DEFAULT_AMBIENT_COLOR, reportedOrAsked(opaque(0x00FFFFFF), null, ::sameRgb))
        // The driver picks another colour on the console.
        assertEquals(0xFF0000E5.toInt(), reportedOrAsked(opaque(0x000000E5), asked, ::sameRgb))
    }

    // endregion

    // region Seat moves

    @Test
    fun `a seat part takes two seconds from end to end`() {
        assertEquals(2_000L, SeatTravel.millis(0, 100))
        assertEquals(400L, SeatTravel.millis(100, 80))
        assertEquals(0L, SeatTravel.millis(30, 30))
    }

    @Test
    fun `a move cut short is estimated from the time it ran, and never past the goal`() {
        assertEquals(25, SeatTravel.reached(from = 0, goal = 30, movedMillis = 500))
        assertEquals(30, SeatTravel.reached(from = 0, goal = 30, movedMillis = 5_000))
        assertEquals(90, SeatTravel.reached(from = 100, goal = 80, movedMillis = 200))
        assertEquals(80, SeatTravel.reached(from = 100, goal = 80, movedMillis = 900))
    }

    @Test
    fun `forward raises the vehicle's position, so the slide moves backward to go back`() {
        assertEquals(MoveControl.FORWARD, SeatTravel.direction(0, 30))
        assertEquals(MoveControl.BACKWARD, SeatTravel.direction(30, 0))
        assertNull(SeatTravel.direction(30, 30))
        // The app's slide counts from fully forward (0) to fully back (100).
        assertEquals(MoveControl.BACKWARD, SeatTravel.direction(20, 80, inverted = true))
        assertEquals(MoveControl.FORWARD, SeatTravel.direction(100, 80, inverted = true))
    }

    @Test
    fun `the vehicle's fore-and-aft position is counted the other way round`() {
        assertEquals(100, SeatTravel.percent(0, 0, 100, inverted = true))
        assertEquals(20, SeatTravel.percent(80, 0, 100, inverted = true))
        assertEquals(30, SeatTravel.percent(30, 0, 100))
        assertEquals(50, SeatTravel.percent(0, -10, 10))
    }

    // endregion
}
