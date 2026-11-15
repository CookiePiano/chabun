package com.syncdesk.app.data.pleos

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.WiperSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class PleosMappingTest {

    @Test
    fun `only Park counts as parked`() {
        assertEquals(Gear.Park, VehicleGear.GEAR_PARK.toGear())
        assertEquals(Gear.Reverse, VehicleGear.GEAR_REVERSE.toGear())
        assertEquals(Gear.Neutral, VehicleGear.GEAR_NEUTRAL.toGear())
        assertEquals(Gear.Drive, VehicleGear.GEAR_DRIVE.toGear())
    }

    @Test
    fun `numbered gears and an unknown gear count as driving`() {
        assertEquals(Gear.Drive, VehicleGear.GEAR_1.toGear())
        assertEquals(Gear.Drive, VehicleGear.GEAR_9.toGear())
        assertEquals(Gear.Drive, VehicleGear.GEAR_UNKNOWN.toGear())
    }

    @Test
    fun `turn signals keep their side`() {
        assertEquals(TurnSignal.Off, TurnSignalValue.OFF.toTurnSignal())
        assertEquals(TurnSignal.Left, TurnSignalValue.LEFT.toTurnSignal())
        assertEquals(TurnSignal.Right, TurnSignalValue.RIGHT.toTurnSignal())
    }

    @Test
    fun `continuous wiping from the second level up is heavy rain`() {
        assertEquals(WiperSpeed.High, WiperSwitch.CONTINUOUS_LEVEL_2.toWiperSpeed())
        assertEquals(WiperSpeed.High, WiperSwitch.CONTINUOUS_LEVEL_5.toWiperSpeed())
    }

    @Test
    fun `mist, intermittent, automatic and the first continuous level are ordinary rain`() {
        assertEquals(WiperSpeed.Low, WiperSwitch.MIST.toWiperSpeed())
        assertEquals(WiperSpeed.Low, WiperSwitch.INTERMITTENT_LEVEL_3.toWiperSpeed())
        assertEquals(WiperSpeed.Low, WiperSwitch.AUTO.toWiperSpeed())
        assertEquals(WiperSpeed.Low, WiperSwitch.CONTINUOUS_LEVEL_1.toWiperSpeed())
    }

    @Test
    fun `wipers that are off or being serviced are off`() {
        assertEquals(WiperSpeed.Off, WiperSwitch.OFF.toWiperSpeed())
        assertEquals(WiperSpeed.Off, WiperSwitch.SERVICE.toWiperSpeed())
        assertEquals(WiperSpeed.Off, WiperSwitch.OTHER.toWiperSpeed())
    }

    @Test
    fun `a vehicle level is shown as a share of its range`() {
        // The Pleos Connect emulator reports the driver window as 0..10.
        assertEquals(0, levelToPercent(0, min = 0, max = 10))
        assertEquals(30, levelToPercent(3, min = 0, max = 10))
        assertEquals(100, levelToPercent(10, min = 0, max = 10))
        assertEquals(50, levelToPercent(3, min = 1, max = 5))
    }

    @Test
    fun `a percentage is sent as the nearest vehicle level`() {
        assertEquals(0, percentToLevel(0, min = 0, max = 10))
        assertEquals(3, percentToLevel(30, min = 0, max = 10))
        assertEquals(7, percentToLevel(70, min = 0, max = 10))
        assertEquals(4, percentToLevel(70, min = 1, max = 5))
    }

    @Test
    fun `values outside the range are clamped, and an empty range does not divide by zero`() {
        assertEquals(100, levelToPercent(14, min = 0, max = 10))
        assertEquals(0, levelToPercent(-2, min = 0, max = 10))
        assertEquals(10, percentToLevel(140, min = 0, max = 10))
        assertEquals(0, levelToPercent(5, min = 5, max = 5))
        assertEquals(5, percentToLevel(60, min = 5, max = 5))
    }

    @Test
    fun `a level survives the round trip through percent`() {
        (0..10).forEach { level ->
            assertEquals(level, percentToLevel(levelToPercent(level, min = 0, max = 10), min = 0, max = 10))
        }
    }
}
