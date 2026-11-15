package com.syncdesk.app.data

import com.syncdesk.app.domain.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnGateTest {

    private var now = 0L
    private val gate = TurnGate(clock = { now }, thresholdDegrees = 90f, sustainMillis = 500L)

    @Test
    fun `a wheel held well past a lane change becomes a turn once it has been held long enough`() {
        assertEquals(TurnSignal.Off, gate.onAngle(120f))
        assertEquals(500L, gate.pendingMillis())

        now = 499L
        assertEquals(TurnSignal.Off, gate.onTick())
        now = 500L
        assertEquals(TurnSignal.Right, gate.onTick())
        assertNull(gate.pendingMillis())
    }

    @Test
    fun `negative angles are to the left`() {
        gate.onAngle(-150f)
        now = 600L

        assertEquals(TurnSignal.Left, gate.onTick())
    }

    @Test
    fun `a lane change is ignored however long it lasts`() {
        assertEquals(TurnSignal.Off, gate.onAngle(30f))
        now = 5_000L

        assertEquals(TurnSignal.Off, gate.onTick())
        assertNull(gate.pendingMillis())
    }

    @Test
    fun `a quick jerk of the wheel is ignored`() {
        gate.onAngle(120f)
        now = 300L
        gate.onAngle(10f)
        now = 1_000L

        assertEquals(TurnSignal.Off, gate.onTick())
    }

    @Test
    fun `the turn ends as soon as the wheel comes back`() {
        gate.onAngle(120f)
        now = 600L
        gate.onTick()

        now = 700L
        assertEquals(TurnSignal.Off, gate.onAngle(45f))
    }

    @Test
    fun `small movements during a turn keep it going`() {
        gate.onAngle(120f)
        now = 600L
        gate.onTick()

        now = 800L
        assertEquals(TurnSignal.Right, gate.onAngle(140f))
        now = 900L
        assertEquals(TurnSignal.Right, gate.onAngle(95f))
    }

    @Test
    fun `swinging from one side to the other starts the wait over`() {
        gate.onAngle(120f)
        now = 600L
        gate.onTick()

        now = 700L
        assertEquals(TurnSignal.Off, gate.onAngle(-120f))
        assertEquals(500L, gate.pendingMillis())
        now = 1_200L
        assertEquals(TurnSignal.Left, gate.onTick())
    }
}
