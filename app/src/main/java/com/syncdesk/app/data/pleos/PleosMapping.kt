package com.syncdesk.app.data.pleos

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.WiperSpeed
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK values translated into the app's own terms. Pure functions, so the rules that
 * decide what counts as driving or as a demanding road can be unit tested without a vehicle.
 */

/**
 * An unknown gear counts as driving: the driving screen shows less, so it is the safe reading
 * when the car cannot say whether it is moving.
 */
internal fun VehicleGear.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.Park
    VehicleGear.GEAR_REVERSE -> Gear.Reverse
    VehicleGear.GEAR_NEUTRAL -> Gear.Neutral
    else -> Gear.Drive
}

internal fun TurnSignalValue.toTurnSignal(): TurnSignal = when (this) {
    TurnSignalValue.LEFT -> TurnSignal.Left
    TurnSignalValue.RIGHT -> TurnSignal.Right
    TurnSignalValue.OFF -> TurnSignal.Off
}

/**
 * Continuous wiping at the second level or above is what a driver selects in heavy rain, so that
 * is where the road starts to count as demanding. Mist, intermittent, automatic and the first
 * continuous level are ordinary rain.
 */
internal fun WiperSwitch.toWiperSpeed(): WiperSpeed = when (this) {
    WiperSwitch.OFF, WiperSwitch.OTHER, WiperSwitch.SERVICE -> WiperSpeed.Off
    WiperSwitch.CONTINUOUS_LEVEL_2,
    WiperSwitch.CONTINUOUS_LEVEL_3,
    WiperSwitch.CONTINUOUS_LEVEL_4,
    WiperSwitch.CONTINUOUS_LEVEL_5,
    -> WiperSpeed.High
    else -> WiperSpeed.Low
}

/** Driving assistance is doing part of the driving. The driver is still responsible. */
internal fun CruiseControl.isAssisting(): Boolean = this == CruiseControl.ACTIVATED

/**
 * Assistance is about to switch itself off and the driver must take over at once. The app treats
 * it like any other demanding moment: nothing to read, and what is said on a call is marked as missed.
 */
internal fun CruiseControl.isTakeoverWarning(): Boolean = this == CruiseControl.FORCED_DEACTIVATION_WARNING

/** Where [level] sits between [min] and [max], as 0 to 100. */
internal fun levelToPercent(level: Int, min: Int, max: Int): Int {
    if (max <= min) return 0
    return ((level - min) * 100.0 / (max - min)).roundToInt().coerceIn(0, 100)
}

/** The level between [min] and [max] closest to [percent]. */
internal fun percentToLevel(percent: Int, min: Int, max: Int): Int {
    if (max <= min) return min
    return (min + (max - min) * percent.coerceIn(0, 100) / 100.0).roundToInt().coerceIn(min, max)
}

// region Capabilities and areas

/** The vehicle lets the app read the property. */
internal fun CapabilityConfig.canRead(): Boolean = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

/** The vehicle lets the app read and change the property. */
internal fun CapabilityConfig.canReadWrite(): Boolean = accessType == AccessType.READ_WRITE

/** A switch the app can turn both on and off. */
internal fun Collection<LightSwitch>.switchesOnAndOff(): Boolean = LightSwitch.ON in this && LightSwitch.OFF in this

/**
 * The first of these areas that covers [wanted]. Areas differ per part, so the one to use is
 * always taken from the capability check: on Pleos Connect v2.1.0 the set temperature and the
 * seats come per seat, the air direction for the front pair, the air conditioning, the air
 * cleaner and the ambient colour for all five seats together, and the door locks for the four
 * doors together (checked 2026-11-15).
 */
internal fun <A, V> Iterable<A>.covering(wanted: V, values: (A) -> Array<out V>): A? = firstOrNull { wanted in values(it) }

/** The area that covers the driver's seat. Pleos Connect vehicles sold in Korea are left-hand drive. */
internal fun Iterable<SeatArea>.driverArea(): SeatArea? = covering(SeatValue.ROW_1_LEFT) { it.values }

/** The entry whose area covers the driver's seat. */
internal fun <V> Map<SeatArea, V>.driverSeat(): Pair<SeatArea, V>? =
    keys.driverArea()?.let { area -> area to getValue(area) }

/** The area that covers the driver's door; the bonnet is an area of its own and never chosen. */
internal fun Iterable<DoorArea>.driverDoorArea(): DoorArea? = covering(DoorValue.ROW_1_LEFT) { it.values }

/** The area of the windshield. */
internal fun Iterable<WindowArea>.windshieldArea(): WindowArea? = covering(WindowValue.FRONT_WINDSHIELD) { it.values }

// endregion

// region Failures

/** Why the vehicle refused a call, as far as its message tells. */
internal enum class VehicleFailure { NotSupported, OutOfRange, DrivingRestricted, Other }

/**
 * The kind of failure [message] describes. The SDK's `VehicleException` is renamed in the released
 * .aar and cannot be referred to, so the message is all there is: the error code's name for a
 * capability check, the platform's `IllegalArgumentException` sentence for a value it will not take.
 */
internal fun vehicleFailureOf(message: String?): VehicleFailure {
    val text = message ?: return VehicleFailure.Other
    return when {
        "ERROR_NOT_SUPPORTED" in text || "is not supported" in text || "not in carPropertyConfig" in text -> VehicleFailure.NotSupported
        "ERROR_DRIVING_RESTRICTED" in text -> VehicleFailure.DrivingRestricted
        "must be less than or equal" in text || "must be greater than or equal" in text || "unwritable state" in text ->
            VehicleFailure.OutOfRange
        else -> VehicleFailure.Other
    }
}

// endregion

// region Climate

/** The direction the vehicle reports, in the app's terms; null when it does not know. */
internal fun FanDirectionType.toAirDirection(): AirDirection? = when (this) {
    FanDirectionType.FACE -> AirDirection.Face
    FanDirectionType.FLOOR -> AirDirection.Feet
    FanDirectionType.FACE_AND_FLOOR -> AirDirection.FaceAndFeet
    FanDirectionType.DEFROST -> AirDirection.Windshield
    FanDirectionType.FACE_AND_DEFROST -> AirDirection.FaceAndWindshield
    FanDirectionType.DEFROST_AND_FLOOR -> AirDirection.FeetAndWindshield
    FanDirectionType.FACE_AND_FLOOR_AND_DEFROST -> AirDirection.All
    FanDirectionType.UNKNOWN -> null
}

/**
 * What to send for [this] direction, or null for the two the app never sends: Pleos Connect
 * v2.1.0 answers FACE_AND_DEFROST and FACE_AND_FLOOR_AND_DEFROST with success but leaves the
 * vehicle as it was (checked 2026-11-15). The app itself only ever turns the air to the feet and
 * back, so these two are only ever read.
 */
internal fun AirDirection.toFanDirection(): FanDirectionType? = when (this) {
    AirDirection.Face -> FanDirectionType.FACE
    AirDirection.Feet -> FanDirectionType.FLOOR
    AirDirection.FaceAndFeet -> FanDirectionType.FACE_AND_FLOOR
    AirDirection.Windshield -> FanDirectionType.DEFROST
    AirDirection.FeetAndWindshield -> FanDirectionType.DEFROST_AND_FLOOR
    AirDirection.FaceAndWindshield, AirDirection.All -> null
}

/** Whether the vehicle takes [direction]. An empty list (Pleos Connect v2.1.0 sends one) says nothing, so everything counts. */
internal fun Collection<FanDirectionType>.offers(direction: FanDirectionType): Boolean = isEmpty() || direction in this

/**
 * The temperatures a seat can be set to: [minC] to [maxC] in steps of [stepC] (15 to 30 in steps
 * of 0.5 on Pleos Connect v2.1.0). The vehicle takes any value inside the range as it is, even
 * one between two steps, so the app sends only values on a step.
 */
internal data class TemperatureScale(val minC: Float, val maxC: Float, val stepC: Float) {
    /** The value on a step closest to [celsius], inside the range. */
    fun snap(celsius: Float): Float {
        val inside = celsius.coerceIn(minC, maxC)
        if (stepC <= 0f) return inside
        val steps = ((inside - minC) / stepC).roundToInt()
        val snapped = (minC + steps * stepC).coerceIn(minC, maxC)
        // Keeps the float free of rounding noise (21.499998 for 21.5).
        return (snapped * 1_000f).roundToInt() / 1_000f
    }

    /** Whether the vehicle storing [celsius] would report [reported]. */
    fun stores(celsius: Float, reported: Float): Boolean = abs(snap(celsius) - reported) < 0.001f

    companion object {
        /** The step when the vehicle does not report one. */
        const val DEFAULT_STEP_C = 0.5f

        /** The scale a capability check reports: the area's bounds and, when given, its step. */
        fun of(minC: Float, maxC: Float, stepC: Float?) =
            TemperatureScale(minC, maxC, stepC?.takeIf { it > 0f } ?: DEFAULT_STEP_C)
    }
}

/**
 * What to publish for a value the vehicle [reported]: the value the app last [asked] for when the
 * report is that request as the vehicle stores it, otherwise the report.
 *
 * The vehicle rounds some values (the set temperature to its step) and drops the alpha of the
 * ambient colour. Published as read, such a report differs from what the app asked for, and a
 * report past the requested value is taken for the driver's own hand while a call is tuned.
 */
internal fun <T> reportedOrAsked(reported: T, asked: T?, stores: (asked: T, reported: T) -> Boolean): T =
    if (asked != null && stores(asked, reported)) asked else reported

/**
 * The one vehicle value behind seat ventilation and seat heating (`setHvacSeatTemperature`):
 * negative ventilates, positive heats, 0 is off; -3 to 3 for the front seats on Pleos Connect
 * v2.1.0. The app keeps the two apart; a seat cannot do both at once, so where both are asked
 * on, ventilation wins.
 */
internal fun seatClimateLevel(ventilation: Int, heating: Int): Int = when {
    ventilation > 0 -> -ventilation
    heating > 0 -> heating
    else -> 0
}

/**
 * The vehicle value [change] asks for, given [cabin] as it is, or null when it asks nothing of the
 * seat. The level the change switches on replaces the other one; switching one off leaves the
 * other as it is.
 */
internal fun seatClimateTarget(cabin: CabinState, change: CabinChange): Int? {
    val ventilation = change.seatVentilation
    val heating = change.seatHeating
    return when {
        ventilation != null && heating != null -> seatClimateLevel(ventilation, heating)
        ventilation != null -> seatClimateLevel(ventilation, heating = if (ventilation > 0) 0 else cabin.seatHeating)
        heating != null -> seatClimateLevel(ventilation = if (heating > 0) 0 else cabin.seatVentilation, heating)
        else -> null
    }
}

/** The ventilation and heating levels behind one vehicle value. */
internal fun seatClimateOf(level: Int): Pair<Int, Int> = if (level < 0) -level to 0 else 0 to level

// endregion

// region Light

private const val RGB_MASK = 0x00FFFFFF

/** Whether two ARGB colours are the same colour; the alpha is left out. */
internal fun sameRgb(a: Int, b: Int): Boolean = a and RGB_MASK == b and RGB_MASK

/**
 * [argb] fully opaque. Pleos Connect v2.1.0 reports the ambient colour with alpha 00 (white reads
 * as 0x00FFFFFF), while the app's colours are opaque.
 */
internal fun opaque(argb: Int): Int = argb or 0xFF000000.toInt()

// endregion

// region Seat

/**
 * A seat part that is moved, not set: the backrest, the seat fore and aft, its height and the
 * cushion tilt. Each moves forward (a larger vehicle position), backward or stops, at the speed
 * measured on Pleos Connect v2.1.0 (2026-11-15): 50 units a second over 0 to 100.
 */
internal object SeatTravel {
    const val PERCENT_PER_SECOND = 50L

    /** How long the part takes from [from] to [to] percent. */
    fun millis(from: Int, to: Int): Long = abs(to - from) * 1_000L / PERCENT_PER_SECOND

    /** Where a part moving from [from] towards [goal] is after [movedMillis]; never past [goal]. */
    fun reached(from: Int, goal: Int, movedMillis: Long): Int {
        val moved = (movedMillis.coerceAtLeast(0) * PERCENT_PER_SECOND / 1_000L).toInt()
        return if (goal > from) minOf(goal, from + moved) else maxOf(goal, from - moved)
    }

    /**
     * Which way to move from [from] to [goal] percent, or null when it is there. Forward raises the
     * vehicle's position; [inverted] is a part whose percent runs against it.
     */
    fun direction(from: Int, goal: Int, inverted: Boolean = false): MoveControl? {
        if (goal == from) return null
        return if ((goal > from) != inverted) MoveControl.FORWARD else MoveControl.BACKWARD
    }

    /**
     * A vehicle position in the app's percent. The vehicle's fore-and-aft position grows towards
     * the front (the platform's SEAT_FORE_AFT_POS), while the app counts from fully forward (0) to
     * fully back (100): that part is [inverted].
     */
    fun percent(position: Int, min: Int, max: Int, inverted: Boolean = false): Int {
        val percent = levelToPercent(position, min, max)
        return if (inverted) 100 - percent else percent
    }
}

// endregion
