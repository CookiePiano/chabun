package com.syncdesk.app.data

import com.syncdesk.app.domain.TurnSignal
import kotlin.math.abs

/**
 * Tells a real turn from a lane change by the steering wheel angle, for a driver who turns
 * without the signal.
 *
 * A turn needs the wheel at [thresholdDegrees] or more for at least [sustainMillis]. A lane change
 * or a bend moves the wheel less, or only for a moment, and is ignored. The turn ends as soon as
 * the wheel comes back under the threshold; counting what is said a little longer as missed is
 * the engine's job, not this filter's.
 *
 * Angles follow the vehicle property convention: negative is to the left (on the Pleos Connect
 * v2.1.0 emulator the console's 120 arrives as -120, checked 2026-11-13).
 *
 * Not thread-safe; the caller serializes calls.
 */
class TurnGate(
    private val clock: () -> Long,
    private val thresholdDegrees: Float = 90f,
    private val sustainMillis: Long = 500L,
) {
    /** The turn in progress, or [TurnSignal.Off]. */
    var turn: TurnSignal = TurnSignal.Off
        private set

    private var candidate: TurnSignal = TurnSignal.Off
    private var since = 0L

    /** A new wheel angle. Returns the turn after it. */
    fun onAngle(degrees: Float): TurnSignal {
        val side = when {
            abs(degrees) < thresholdDegrees -> TurnSignal.Off
            degrees < 0 -> TurnSignal.Left
            else -> TurnSignal.Right
        }
        if (side != candidate) {
            candidate = side
            since = clock()
        }
        // Back under the threshold, or over to the other side: the turn in progress is over.
        if (side != turn) turn = TurnSignal.Off
        return onTick()
    }

    /** Time has passed with no new angle; a held angle may now count as a turn. Returns the turn. */
    fun onTick(): TurnSignal {
        if (candidate != TurnSignal.Off && clock() - since >= sustainMillis) turn = candidate
        return turn
    }

    /** When to call [onTick] next to catch a turn being held, or null when nothing is pending. */
    fun pendingMillis(): Long? =
        if (candidate != TurnSignal.Off && turn != candidate) (sustainMillis - (clock() - since)).coerceAtLeast(0) else null
}
