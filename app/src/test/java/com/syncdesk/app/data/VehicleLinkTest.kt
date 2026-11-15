package com.syncdesk.app.data

import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLinkTest {

    @Test
    fun `every cabin part has the feature that carries it, and ventilation and heating share one`() {
        CabinPart.entries.forEach { VehicleFeature.of(it) }
        assertEquals(VehicleFeature.SeatClimate, VehicleFeature.of(CabinPart.SeatVentilation))
        assertEquals(VehicleFeature.SeatClimate, VehicleFeature.of(CabinPart.SeatHeating))
        assertEquals(VehicleFeature.WheelPosition, VehicleFeature.of(CabinPart.SteeringWheel))
    }

    @Test
    fun `the parts the probe found no working call for are simulated on every car`() {
        val simulated = VehicleFeature.entries.filterNot { it.linkable }.toSet()
        assertEquals(
            setOf(
                VehicleFeature.CallVolume, VehicleFeature.CushionLength, VehicleFeature.Lumbar,
                VehicleFeature.Shade, VehicleFeature.WheelPosition, VehicleFeature.UsbPower,
            ),
            simulated,
        )
        // So the link line always names them, even when everything else is live.
        val allLive = VehicleLink(live = VehicleFeature.entries.filterTo(mutableSetOf()) { it.linkable })
        assertEquals(simulated, allLive.simulated)
    }

    private val restore = CabinChange(
        fanLevel = 6, windowOpenPercent = 30, airDirection = AirDirection.Face, temperatureC = 22f,
        seatVentilation = 0, ambientColor = 0xFFFFFFFF.toInt(), callVolume = 3,
    )

    @Test
    fun `a restore waits until every part of it that a vehicle can carry is live`() {
        val linking = VehicleLink(live = setOf(VehicleFeature.Fan, VehicleFeature.Window), pending = true)
        assertFalse(linking.readyFor(restore))

        val needed = setOf(
            VehicleFeature.Fan, VehicleFeature.Window, VehicleFeature.AirDirection, VehicleFeature.Temperature,
            VehicleFeature.SeatClimate, VehicleFeature.AmbientColor,
        )
        // The call volume is always simulated, so it is not waited for.
        assertTrue(VehicleLink(live = needed, pending = true).readyFor(restore))
    }

    @Test
    fun `once a connected vehicle has finished linking, what is not live stays simulated and nothing is waited for`() {
        assertTrue(VehicleLink(live = setOf(VehicleFeature.Fan), pending = false).readyFor(restore))
    }

    @Test
    fun `without a vehicle the restore waits, so the caller's time limit decides`() {
        assertFalse(VehicleLink().readyFor(restore))
        assertTrue(VehicleLink().readyFor(CabinChange(callVolume = 3)))
    }
}
