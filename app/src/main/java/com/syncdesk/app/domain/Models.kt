package com.syncdesk.app.domain

/** Gear positions the app distinguishes. Anything other than [Park] counts as driving. */
enum class Gear { Park, Reverse, Neutral, Drive }

enum class WiperSpeed { Off, Low, High }

enum class TurnSignal { Off, Left, Right }

/**
 * How much of the driving the driver is doing, which decides how much of the desk the app opens.
 * The less the driver has to drive, the more work the app lets in.
 */
enum class DriveStage {
    /** The driver drives. The app shows only what can be read at a glance. */
    Driving,

    /** The car drives itself. The desk opens while moving: notes can be read, office mode works. */
    Autonomous,

    /** Parked. The whole desk, including the settings. */
    Parked,
}

/** Driving sensors the app reads but never controls. */
data class VehicleSignals(
    val gear: Gear = Gear.Drive,
    val wiper: WiperSpeed = WiperSpeed.Off,
    val turnSignal: TurnSignal = TurnSignal.Off,
    /** Driving assistance such as cruise control is active. The driver is still driving. */
    val assistActive: Boolean = false,
    /** The car is driving itself and has not asked for the wheel back. */
    val autonomous: Boolean = false,
    /** The car is handing the driving back, or assistance is being forced off. */
    val takeoverRequested: Boolean = false,
    /** A sharp turn of the steering wheel without the turn signal, already filtered for lane changes. */
    val steeringTurn: TurnSignal = TurnSignal.Off,
) {
    val isParked: Boolean get() = gear == Gear.Park

    val stage: DriveStage
        get() = when {
            isParked -> DriveStage.Parked
            autonomous && !takeoverRequested -> DriveStage.Autonomous
            else -> DriveStage.Driving
        }

    /** Whether the stage leaves the driver free to work: reading at length and office mode. */
    val deskOpen: Boolean get() = stage != DriveStage.Driving
}
/**
 * What the platform lets the app show and do right now (AAOS CarUxRestrictions). Null limits are
 * not in force. Everything is allowed while parked.
 */
data class UxLimits(
    /** Seat, light and other vehicle settings may not be changed. */
    val noSetup: Boolean = false,
    /** Longest text the app may show, in characters. */
    val maxStringLength: Int? = null,
    /** Most items a list may show. */
    val maxItems: Int? = null,
) {
    companion object {
        // Flags of android.car.drivingstate.CarUxRestrictions, repeated so the domain needs no car library.
        const val LIMIT_STRING_LENGTH = 0x4
        const val LIMIT_CONTENT = 0x20
        const val NO_SETUP = 0x40

        fun of(requiresDistractionOptimization: Boolean, active: Int, maxStringLength: Int, maxItems: Int): UxLimits {
            if (!requiresDistractionOptimization) return UxLimits()
            return UxLimits(
                noSetup = active and NO_SETUP != 0,
                maxStringLength = maxStringLength.takeIf { active and LIMIT_STRING_LENGTH != 0 && it > 0 },
                maxItems = maxItems.takeIf { active and LIMIT_CONTENT != 0 && it > 0 },
            )
        }
    }
}

/** [text] cut to what [UxLimits.maxStringLength] allows, with an ellipsis when it was cut. */
fun UxLimits.fit(text: String): String {
    val max = maxStringLength ?: return text
    return if (text.length <= max) text else text.take((max - 1).coerceAtLeast(0)) + "…"
}

/**
 * Where the climate system blows its air. The names follow the vents: at the face, at the feet,
 * at the windshield (defrost), or a mix of them.
 */
enum class AirDirection(val atFace: Boolean, val atWindshield: Boolean) {
    Face(atFace = true, atWindshield = false),
    FaceAndFeet(atFace = true, atWindshield = false),
    Feet(atFace = false, atWindshield = false),
    Windshield(atFace = false, atWindshield = true),
    FaceAndWindshield(atFace = true, atWindshield = true),
    FeetAndWindshield(atFace = false, atWindshield = true),
    All(atFace = true, atWindshield = true),
    ;

    /**
     * The direction that takes the air off the face, where the microphone hears it, or null when
     * there is nothing to change. Air the driver has put on the windshield stays there: it keeps
     * the glass clear.
     */
    fun awayFromFace(): AirDirection? = if (atFace && !atWindshield) Feet else null
}

/**
 * Where the steering wheel stands, in percent of its travel: [up] from lowest to highest and
 * [forward] from nearest the driver to nearest the dashboard.
 */
data class WheelPosition(val up: Int = 50, val forward: Int = 50) {
    fun normalized() = WheelPosition(up.coerceIn(0, 100), forward.coerceIn(0, 100))
}

/** Cabin hardware the app reads and controls. */
data class CabinState(
    val fanLevel: Int = 6,
    val windowOpenPercent: Int = 30,
    /** Air drawn from inside the cabin instead of from outside, which keeps road noise out. */
    val recirculation: Boolean = false,
    /** Seat backrest position in the vehicle's own units (0 upright to 100 reclined on Pleos Connect). */
    val seatRecline: Int = 0,
    val readingLightOn: Boolean = false,
    /** Ambient light brightness in percent; 0 is off. */
    val ambientPercent: Int = 0,
    val airDirection: AirDirection = AirDirection.Face,
    /** The temperature the driver's side is set to, in degrees Celsius. */
    val temperatureC: Float = 22f,
    /** Whether the air conditioning (cooling) is on. Read only: the app never switches it. */
    val acOn: Boolean = true,
    /** Driver's seat ventilation level; 0 is off. */
    val seatVentilation: Int = 0,
    /** Driver's seat heating level; 0 is off. */
    val seatHeating: Int = 0,
    val airCleanerOn: Boolean = false,
    /** The windshield defroster. */
    val defrosterOn: Boolean = false,
    /** Ambient light colour as 32-bit ARGB. */
    val ambientColor: Int = CabinLimits.DEFAULT_AMBIENT_COLOR,
    /** How loud the other party is played, in the steps the audio system offers. */
    val callVolume: Int = 3,
    /** Driver's seat fore and aft, in percent of its travel: 0 fully forward to 100 fully back. */
    val seatSlide: Int = 50,
    /** Driver's seat height in percent: 0 lowest to 100 highest. */
    val seatHeight: Int = 50,
    /** How high the front of the seat cushion stands, in percent: 0 lowest to 100 highest. */
    val cushionTilt: Int = 50,
    /** How far the seat cushion reaches under the thighs, in percent: 0 shortest to 100 longest. */
    val cushionLength: Int = 50,
    /** Lumbar support in percent: 0 fully in to 100 fully forward. */
    val lumbar: Int = 30,
    /** The cabin (dome) light. */
    val cabinLightOn: Boolean = false,
    /** How much of the glass the sunshades or the smart glass cover, in percent; 0 is clear. */
    val shadePercent: Int = 0,
    val doorsLocked: Boolean = false,
    /** The steering wheel's position. No vehicle reports or moves it yet: always simulated. */
    val wheel: WheelPosition = WheelPosition(),
    /** The vehicle battery's charge in percent. Read only: the app never changes it. */
    val batteryPercent: Int = 80,
) {
    /** The climate system is running. A vehicle reports fan level 0 while it is switched off. */
    val climateOn: Boolean get() = fanLevel > 0

    /** The value of [part] in this cabin. */
    operator fun get(part: CabinPart): Any = when (part) {
        CabinPart.Fan -> fanLevel
        CabinPart.Window -> windowOpenPercent
        CabinPart.Recirculation -> recirculation
        CabinPart.Seat -> seatRecline
        CabinPart.ReadingLight -> readingLightOn
        CabinPart.Ambient -> ambientPercent
        CabinPart.AirDirection -> airDirection
        CabinPart.Temperature -> temperatureC
        CabinPart.SeatVentilation -> seatVentilation
        CabinPart.SeatHeating -> seatHeating
        CabinPart.AirCleaner -> airCleanerOn
        CabinPart.Defroster -> defrosterOn
        CabinPart.AmbientColor -> ambientColor
        CabinPart.CallVolume -> callVolume
        CabinPart.SeatSlide -> seatSlide
        CabinPart.SeatHeight -> seatHeight
        CabinPart.CushionTilt -> cushionTilt
        CabinPart.CushionLength -> cushionLength
        CabinPart.Lumbar -> lumbar
        CabinPart.CabinLight -> cabinLightOn
        CabinPart.Shade -> shadePercent
        CabinPart.DoorLock -> doorsLocked
        CabinPart.SteeringWheel -> wheel
    }
}

/** One request to the cabin hardware. Null fields are left as they are. */
data class CabinChange(
    val fanLevel: Int? = null,
    val windowOpenPercent: Int? = null,
    val recirculation: Boolean? = null,
    val seatRecline: Int? = null,
    val readingLightOn: Boolean? = null,
    val ambientPercent: Int? = null,
    val airDirection: AirDirection? = null,
    val temperatureC: Float? = null,
    val seatVentilation: Int? = null,
    val seatHeating: Int? = null,
    val airCleanerOn: Boolean? = null,
    val defrosterOn: Boolean? = null,
    val ambientColor: Int? = null,
    val callVolume: Int? = null,
    val seatSlide: Int? = null,
    val seatHeight: Int? = null,
    val cushionTilt: Int? = null,
    val cushionLength: Int? = null,
    val lumbar: Int? = null,
    val cabinLightOn: Boolean? = null,
    val shadePercent: Int? = null,
    val doorsLocked: Boolean? = null,
    val wheel: WheelPosition? = null,
) {
    /** Both changes as one; where both set a field, [other] wins. */
    operator fun plus(other: CabinChange) = CabinChange(
        fanLevel = other.fanLevel ?: fanLevel,
        windowOpenPercent = other.windowOpenPercent ?: windowOpenPercent,
        recirculation = other.recirculation ?: recirculation,
        seatRecline = other.seatRecline ?: seatRecline,
        readingLightOn = other.readingLightOn ?: readingLightOn,
        ambientPercent = other.ambientPercent ?: ambientPercent,
        airDirection = other.airDirection ?: airDirection,
        temperatureC = other.temperatureC ?: temperatureC,
        seatVentilation = other.seatVentilation ?: seatVentilation,
        seatHeating = other.seatHeating ?: seatHeating,
        airCleanerOn = other.airCleanerOn ?: airCleanerOn,
        defrosterOn = other.defrosterOn ?: defrosterOn,
        ambientColor = other.ambientColor ?: ambientColor,
        callVolume = other.callVolume ?: callVolume,
        seatSlide = other.seatSlide ?: seatSlide,
        seatHeight = other.seatHeight ?: seatHeight,
        cushionTilt = other.cushionTilt ?: cushionTilt,
        cushionLength = other.cushionLength ?: cushionLength,
        lumbar = other.lumbar ?: lumbar,
        cabinLightOn = other.cabinLightOn ?: cabinLightOn,
        shadePercent = other.shadePercent ?: shadePercent,
        doorsLocked = other.doorsLocked ?: doorsLocked,
        wheel = other.wheel ?: wheel,
    )

    /** What this change asks of [part], or null when it leaves the part alone. */
    operator fun get(part: CabinPart): Any? = when (part) {
        CabinPart.Fan -> fanLevel
        CabinPart.Window -> windowOpenPercent
        CabinPart.Recirculation -> recirculation
        CabinPart.Seat -> seatRecline
        CabinPart.ReadingLight -> readingLightOn
        CabinPart.Ambient -> ambientPercent
        CabinPart.AirDirection -> airDirection
        CabinPart.Temperature -> temperatureC
        CabinPart.SeatVentilation -> seatVentilation
        CabinPart.SeatHeating -> seatHeating
        CabinPart.AirCleaner -> airCleanerOn
        CabinPart.Defroster -> defrosterOn
        CabinPart.AmbientColor -> ambientColor
        CabinPart.CallVolume -> callVolume
        CabinPart.SeatSlide -> seatSlide
        CabinPart.SeatHeight -> seatHeight
        CabinPart.CushionTilt -> cushionTilt
        CabinPart.CushionLength -> cushionLength
        CabinPart.Lumbar -> lumbar
        CabinPart.CabinLight -> cabinLightOn
        CabinPart.Shade -> shadePercent
        CabinPart.DoorLock -> doorsLocked
        CabinPart.SteeringWheel -> wheel
    }

    /** The parts this change asks to move. */
    val parts: Set<CabinPart> get() = CabinPart.entries.filterTo(mutableSetOf()) { this[it] != null }

    /** Whether this change asks for nothing. */
    val isEmpty: Boolean get() = this == NONE

    /** This change cut down to [kept]; every other part is left alone. */
    fun only(kept: Set<CabinPart>) = CabinChange(
        fanLevel = fanLevel.takeIf { CabinPart.Fan in kept },
        windowOpenPercent = windowOpenPercent.takeIf { CabinPart.Window in kept },
        recirculation = recirculation.takeIf { CabinPart.Recirculation in kept },
        seatRecline = seatRecline.takeIf { CabinPart.Seat in kept },
        readingLightOn = readingLightOn.takeIf { CabinPart.ReadingLight in kept },
        ambientPercent = ambientPercent.takeIf { CabinPart.Ambient in kept },
        airDirection = airDirection.takeIf { CabinPart.AirDirection in kept },
        temperatureC = temperatureC.takeIf { CabinPart.Temperature in kept },
        seatVentilation = seatVentilation.takeIf { CabinPart.SeatVentilation in kept },
        seatHeating = seatHeating.takeIf { CabinPart.SeatHeating in kept },
        airCleanerOn = airCleanerOn.takeIf { CabinPart.AirCleaner in kept },
        defrosterOn = defrosterOn.takeIf { CabinPart.Defroster in kept },
        ambientColor = ambientColor.takeIf { CabinPart.AmbientColor in kept },
        callVolume = callVolume.takeIf { CabinPart.CallVolume in kept },
        seatSlide = seatSlide.takeIf { CabinPart.SeatSlide in kept },
        seatHeight = seatHeight.takeIf { CabinPart.SeatHeight in kept },
        cushionTilt = cushionTilt.takeIf { CabinPart.CushionTilt in kept },
        cushionLength = cushionLength.takeIf { CabinPart.CushionLength in kept },
        lumbar = lumbar.takeIf { CabinPart.Lumbar in kept },
        cabinLightOn = cabinLightOn.takeIf { CabinPart.CabinLight in kept },
        shadePercent = shadePercent.takeIf { CabinPart.Shade in kept },
        doorsLocked = doorsLocked.takeIf { CabinPart.DoorLock in kept },
        wheel = wheel.takeIf { CabinPart.SteeringWheel in kept },
    )

    /** This change without [dropped]; those parts are left alone. */
    fun without(dropped: Set<CabinPart>) = only(parts - dropped)

    companion object {
        /** A change that asks for nothing. */
        val NONE = CabinChange()
    }
}

/** The cabin once [change] has fully taken effect. */
fun CabinState.applying(change: CabinChange) = copy(
    fanLevel = change.fanLevel ?: fanLevel,
    windowOpenPercent = change.windowOpenPercent ?: windowOpenPercent,
    recirculation = change.recirculation ?: recirculation,
    seatRecline = change.seatRecline ?: seatRecline,
    readingLightOn = change.readingLightOn ?: readingLightOn,
    ambientPercent = change.ambientPercent ?: ambientPercent,
    airDirection = change.airDirection ?: airDirection,
    temperatureC = change.temperatureC ?: temperatureC,
    seatVentilation = change.seatVentilation ?: seatVentilation,
    seatHeating = change.seatHeating ?: seatHeating,
    airCleanerOn = change.airCleanerOn ?: airCleanerOn,
    defrosterOn = change.defrosterOn ?: defrosterOn,
    ambientColor = change.ambientColor ?: ambientColor,
    callVolume = change.callVolume ?: callVolume,
    seatSlide = change.seatSlide ?: seatSlide,
    seatHeight = change.seatHeight ?: seatHeight,
    cushionTilt = change.cushionTilt ?: cushionTilt,
    cushionLength = change.cushionLength ?: cushionLength,
    lumbar = change.lumbar ?: lumbar,
    cabinLightOn = change.cabinLightOn ?: cabinLightOn,
    shadePercent = change.shadePercent ?: shadePercent,
    doorsLocked = change.doorsLocked ?: doorsLocked,
    wheel = change.wheel ?: wheel,
)

/** Every part of this cabin the app can move, as the change that would set it this way. */
fun CabinState.asChange() = CabinChange(
    fanLevel = fanLevel,
    windowOpenPercent = windowOpenPercent,
    recirculation = recirculation,
    seatRecline = seatRecline,
    readingLightOn = readingLightOn,
    ambientPercent = ambientPercent,
    airDirection = airDirection,
    temperatureC = temperatureC,
    seatVentilation = seatVentilation,
    seatHeating = seatHeating,
    airCleanerOn = airCleanerOn,
    defrosterOn = defrosterOn,
    ambientColor = ambientColor,
    callVolume = callVolume,
    seatSlide = seatSlide,
    seatHeight = seatHeight,
    cushionTilt = cushionTilt,
    cushionLength = cushionLength,
    lumbar = lumbar,
    cabinLightOn = cabinLightOn,
    shadePercent = shadePercent,
    doorsLocked = doorsLocked,
    wheel = wheel,
)

/**
 * A [change] the vehicle should carry out. [id] grows with every command, so two commands with
 * the same change are still told apart.
 */
data class CabinCommand(val id: Int, val change: CabinChange)

/**
 * The ranges this vehicle reports for the levels the app changes. They differ between models, so
 * the lowest fan level is derived from them instead of being a fixed number.
 */
data class CabinRanges(
    val fanMin: Int = 0,
    val fanMax: Int = 8,
    val temperatureMinC: Float = 16f,
    val temperatureMaxC: Float = 30f,
    val callVolumeMax: Int = 5,
) {
    /**
     * The lowest the app takes the fan for a call: a quarter of full speed, or the lowest the car
     * allows, and never off, so the cabin does not get stuffy.
     */
    val quietFanLevel: Int get() = maxOf(1, fanMin, fanMax / 4)
}

/** Targets that do not depend on the vehicle. Window opening and ambient light are in percent. */
object CabinLimits {
    const val WINDOW_MAX = 100
    const val QUIET_WINDOW_OPEN = 0

    // Office mode: a laptop on the lap of someone sitting in the driver's seat. Positions are in
    // percent of each part's travel (see [CabinState]).
    /** The steering wheel fully up and fully forward, out of the way of knees and laptop. */
    const val OFFICE_WHEEL_UP = 100
    const val OFFICE_WHEEL_FORWARD = 100
    /** The seat well back, for room between the wheel and the laptop. */
    const val OFFICE_SEAT_SLIDE = 80
    /** The seat a little lower, so the screen is looked down on less steeply. */
    const val OFFICE_SEAT_HEIGHT = 30
    /** The front of the cushion lowered, so the thighs are level and the laptop does not slide. */
    const val OFFICE_CUSHION_TILT = 30
    /** The cushion short, with less under the knees. */
    const val OFFICE_CUSHION_LENGTH = 20
    /** The backrest upright, for typing. */
    const val OFFICE_SEAT_RECLINE = 0
    /** Lumbar support well forward, against slouching. */
    const val OFFICE_LUMBAR = 70
    /** Ambient light low while working, so it does not glare on the screen. */
    const val OFFICE_AMBIENT = 20
    /** Enough shade to keep the glare off the screen. */
    const val OFFICE_SHADE = 50
    /** Seat ventilation against the laptop's heat on the thighs: the gentlest level. */
    const val OFFICE_SEAT_VENTILATION = 1

    // A video meeting from the same seat: the light is on the face and the cabin is quieter still.
    /** Ambient light bright, and white ([MEETING_AMBIENT_COLOR]), to light the face evenly. */
    const val MEETING_AMBIENT = 80
    const val MEETING_AMBIENT_COLOR = 0xFFFFFFFF.toInt()
    /** Everything shaded: no backlight, and nothing of the street behind the driver. */
    const val MEETING_SHADE = 100
    const val MEETING_WINDOW_OPEN = 0

    const val SEAT_RECLINE_MIN = 0
    const val SEAT_RECLINE_MAX = 100
    const val SEAT_RECLINE_STEP = 10
    const val AMBIENT_STEP = 10

    /** The step in which the office arrangement's positions and shade are adjusted, in percent. */
    const val PERCENT_STEP = 10

    /** The highest seat ventilation level the office arrangement offers. */
    const val SEAT_LEVEL_MAX = 3

    /** Ambient light colour (ARGB) a cabin has until the vehicle says otherwise: white. */
    const val DEFAULT_AMBIENT_COLOR = 0xFFFFFFFF.toInt()

    /** Ambient light colour (ARGB) while what is said on a call is being marked as missed: purple. */
    const val MISSED_AMBIENT_COLOR = 0xFFAA00FF.toInt()

    /** How many levels below where it was the fan must be before the seat and the temperature make up for it. */
    const val COMFORT_FAN_DROP = 2

    /** The seat ventilation or heating level that makes up for a lowered fan: the gentlest one. */
    const val COMFORT_SEAT_LEVEL = 1

    /** How far the set temperature moves to make up for a lowered fan, in degrees Celsius. */
    const val COMFORT_TEMPERATURE_STEP_C = 1f
}

/**
 * What office mode sets the cabin to. The driver adjusts it while the desk is open and it is
 * remembered, so one touch always brings back their own arrangement.
 */
data class OfficePreset(
    val seatRecline: Int = CabinLimits.OFFICE_SEAT_RECLINE,
    val readingLightOn: Boolean = true,
    /** Ambient light brightness while working; a meeting has its own ([CabinLimits.MEETING_AMBIENT]). */
    val ambientPercent: Int = CabinLimits.OFFICE_AMBIENT,
    val wheel: WheelPosition = WheelPosition(CabinLimits.OFFICE_WHEEL_UP, CabinLimits.OFFICE_WHEEL_FORWARD),
    val seatSlide: Int = CabinLimits.OFFICE_SEAT_SLIDE,
    val seatHeight: Int = CabinLimits.OFFICE_SEAT_HEIGHT,
    val cushionTilt: Int = CabinLimits.OFFICE_CUSHION_TILT,
    val cushionLength: Int = CabinLimits.OFFICE_CUSHION_LENGTH,
    val lumbar: Int = CabinLimits.OFFICE_LUMBAR,
    /**
     * The cabin (dome) light. Off unless the driver asks for it: it lights the whole cabin and
     * glares on a screen, and on Pleos Connect switching it on switches every reading light on too.
     */
    val cabinLightOn: Boolean = false,
    /** How much is shaded while working; a meeting shades everything ([CabinLimits.MEETING_SHADE]). */
    val shadePercent: Int = CabinLimits.OFFICE_SHADE,
    /** Seat ventilation level; 0 leaves the seat as it is. */
    val seatVentilation: Int = CabinLimits.OFFICE_SEAT_VENTILATION,
    /** Whether the doors are locked for the time of working. Doors are never unlocked by it. */
    val lockDoors: Boolean = true,
    /** Whether the USB-C port is switched to charging a laptop. */
    val usbPower: Boolean = true,
) {
    /** The same preset with every value inside the range the controls offer. */
    fun normalized() = copy(
        seatRecline = seatRecline.coerceIn(CabinLimits.SEAT_RECLINE_MIN, CabinLimits.SEAT_RECLINE_MAX),
        ambientPercent = ambientPercent.coerceIn(0, 100),
        wheel = wheel.normalized(),
        seatSlide = seatSlide.coerceIn(0, 100),
        seatHeight = seatHeight.coerceIn(0, 100),
        cushionTilt = cushionTilt.coerceIn(0, 100),
        cushionLength = cushionLength.coerceIn(0, 100),
        lumbar = lumbar.coerceIn(0, 100),
        shadePercent = shadePercent.coerceIn(0, 100),
        seatVentilation = seatVentilation.coerceIn(0, CabinLimits.SEAT_LEVEL_MAX),
    )
}

data class CallState(
    val active: Boolean = false,
    val driverSpeaking: Boolean = false,
    val elapsedSeconds: Int = 0,
    /** The day the call started (ISO, "2026-11-15") and the time of day ("14:05"). */
    val date: String = "",
    val startedAt: String = "",
    /** The memo this call is written into; null until its first sentence is kept. */
    val memoId: Int? = null,
)

/** Why the road currently needs the driver's full attention. */
enum class RoadDemand { Clear, HeavyRain, TurningLeft, TurningRight, TakeOver }

/** Why the driver probably did not hear a sentence, although it was written down clearly. */
enum class MissedReason {
    /** The road needed their full attention when it was said. */
    Driving,

    /** The cabin was too loud for the other party's voice when it was said. */
    Noise,
}

/** How well the driver hears the other party over the cabin noise. */
enum class Hearing {
    /** Heard well: nothing needs to change. */
    Good,

    /** Heard with some effort. */
    Poor,

    /** Hardly heard: what is said now is probably missed. */
    Bad,
}

/**
 * How loud the other party's [voice] and the cabin [noise] are, as whole numbers on one dB-like
 * scale. Only their difference matters.
 */
data class HearingLevels(val voice: Int, val noise: Int) {
    /** How far the voice stands above the noise. */
    val margin: Int get() = voice - noise

    val hearing: Hearing
        get() = when {
            margin >= GOOD_MARGIN -> Hearing.Good
            margin >= POOR_MARGIN -> Hearing.Poor
            else -> Hearing.Bad
        }

    /** Heard so well that one more fan step would still leave it [Hearing.Good]. */
    val hasRoomForMoreFan: Boolean get() = margin >= GOOD_MARGIN + RAISE_HEADROOM

    companion object {
        /** From this margin up the call is heard well. */
        const val GOOD_MARGIN = 10

        /** From this margin up to [GOOD_MARGIN] the call is heard with effort; below it, hardly. */
        const val POOR_MARGIN = 4

        /** What one fan step may cost; the fan comes back up only with this much to spare. */
        const val RAISE_HEADROOM = 4
    }
}

/** One sentence of a call. [id] is its place in the call memo, counted from 1. */
data class MeetingNote(
    val id: Int,
    val time: String,
    val speaker: String,
    val text: String,
    /** One short line for the key summary, or null when the sentence is not a key point. */
    val gist: String? = null,
    /** Why the driver probably missed the sentence; null when nothing kept them from hearing it. */
    val missed: MissedReason? = null,
    /** The driver's own sentence, kept because it holds a promise; [gist] is what they promised. */
    val byDriver: Boolean = false,
) {
    /**
     * The sentence only runs the meeting ([KeyPoints.runsTheMeeting]). It stays in the whole call,
     * and keeps its [missed] mark there in the data, but it is never shown or said as missed.
     */
    val runsTheMeeting: Boolean get() = gist == null && !byDriver && KeyPoints.runsTheMeeting(text)
}

/**
 * What was written down on one call. [title] names the call (the meeting's title for now; the
 * other party's name on a real call), [date] is the day it started (ISO, "2026-11-15") and
 * [startedAt] the time of day ("14:05"). [notes] holds every sentence in the order it was said.
 * [explained] once the car has talked the driver through it after the call. [trip] is the drive
 * the call was taken on ([SyncDeskState.trip]), which is what the list shown on parking goes by.
 */
data class CallMemo(
    val id: Int,
    val title: String = "",
    val date: String = "",
    val startedAt: String = "",
    val notes: List<MeetingNote> = emptyList(),
    val explained: Boolean = false,
    val trip: Int = 0,
) {
    /**
     * The key points of the call in the order they were said, at most [MAX_SUMMARY_LINES]: what
     * the others decided or asked, and what the driver promised. When there are more, the
     * driver's promises stay first and the others' points fill what is left.
     */
    val summary: List<String>
        get() {
            val points = notes.filter { it.gist != null }.distinctBy { it.gist }
            if (points.size <= MAX_SUMMARY_LINES) return points.mapNotNull { it.gist }
            val promises = points.filter { it.byDriver }.take(MAX_SUMMARY_LINES)
            val kept = promises + points.filterNot { it.byDriver }.take(MAX_SUMMARY_LINES - promises.size)
            return points.filter { it in kept }.mapNotNull { it.gist }
        }

    /** The sentences marked as probably not heard, every one of them. */
    val missed: List<MeetingNote> get() = notes.filter { it.missed != null }

    /**
     * The part the driver missed, as the memo shows it: the sentences marked as missed, without
     * those that only run the meeting ([MeetingNote.runsTheMeeting]). Those are no loss to the
     * driver, just as the summary leaves them out.
     */
    val missedPart: List<MeetingNote> get() = missed.filterNot { it.runsTheMeeting }

    /**
     * The missed part as it is said aloud: each sentence by its key point where it has one, without
     * what the summary has just said, and no more than [MAX_SPOKEN_MISSED] lines. The memo on
     * screen shows all of [missedPart].
     */
    val missedToSay: List<String> get() = missedSaid.map { it.gist ?: it.text }

    /** The sentences behind [missedToSay]; they also tell why each was missed. */
    val missedSaid: List<MeetingNote>
        get() {
            val said = summary
            return missedPart.distinctBy { it.gist ?: it.text }.filterNot { (it.gist ?: it.text) in said }.take(MAX_SPOKEN_MISSED)
        }

    companion object {
        /** Longest key summary. Pleos limits a text block to three lines. */
        const val MAX_SUMMARY_LINES = 3

        /** Most missed sentences read aloud after a call, so the explanation stays short. */
        const val MAX_SPOKEN_MISSED = 3
    }
}

/** What kind of contact reached the driver's phone without being looked at. */
enum class ContactKind { MissedCall, Message }

/**
 * One contact on the driver's phone that they have not looked at yet: a call they did not take
 * and have not returned, or a message they have not read. [id] is the phone's own name for it and
 * [from] the other party as the phone shows them. What a message says is never part of it.
 */
data class Contact(val id: String, val kind: ContactKind, val from: String)

/** One kind of contact in the line shown on parking: how many, and from whom. */
data class ContactGroup(
    val count: Int,
    /** The first [ContactDigest.MAX_NAMES] people, in the order their contacts came. */
    val names: List<String>,
    /** How many more people there are than [names] says. */
    val others: Int,
)

/**
 * The contacts the driver has not looked at, summed up for the one line shown on parking: only
 * how many and from whom. A kind with nothing in it is null.
 */
data class ContactDigest(val missedCalls: ContactGroup?, val messages: ContactGroup?) {
    companion object {
        /** How many people each kind names before the rest become "and N more". */
        const val MAX_NAMES = 2

        /** The digest of [contacts], or null when there is nothing to say. */
        fun of(contacts: List<Contact>): ContactDigest? {
            if (contacts.isEmpty()) return null
            fun group(kind: ContactKind): ContactGroup? {
                val ofKind = contacts.filter { it.kind == kind }
                if (ofKind.isEmpty()) return null
                val people = ofKind.map { it.from }.distinct()
                return ContactGroup(ofKind.size, people.take(MAX_NAMES), (people.size - MAX_NAMES).coerceAtLeast(0))
            }
            return ContactDigest(group(ContactKind.MissedCall), group(ContactKind.Message))
        }
    }
}

/** Where in the cabin a USB-C port is. */
enum class UsbLocation { CenterConsoleFront, CenterConsoleRear }

/**
 * The USB-C port a laptop is charged from, as the vehicle reports it. [maxWatts] is what the port
 * can deliver at most (USB Power Delivery 3.1 goes up to 240 W), [limitWatts] what it has been
 * told to stay under, and [deviceWatts] what the connected device asks for (null with nothing
 * connected).
 */
data class UsbPort(
    val location: UsbLocation = UsbLocation.CenterConsoleFront,
    val maxWatts: Int = MAX_WATTS,
    val on: Boolean = false,
    val limitWatts: Int = MAX_WATTS,
    val deviceWatts: Int? = null,
) {
    /**
     * What the port delivers right now: the smaller of what the device asks for and what the port
     * can give, and never more than its limit. Nothing while it is off or nothing is connected.
     */
    val suppliedWatts: Int
        get() = if (on && deviceWatts != null) minOf(deviceWatts, maxWatts, limitWatts).coerceAtLeast(0) else 0

    companion object {
        /** The most USB Power Delivery 3.1 delivers (48 V, 5 A). */
        const val MAX_WATTS = 240
    }
}

/** What the app asks of the USB-C port: switched [on] and delivering no more than [limitWatts]. */
data class UsbOrder(val on: Boolean, val limitWatts: Int)

/** How far the vehicle battery's charge holds back the USB-C port while it charges a laptop. */
enum class BatteryGuard {
    /** Charge to spare: the port delivers what the laptop asks for. */
    None,

    /** Below [REDUCE_BELOW] percent: the port is held to [REDUCED_WATTS]. */
    Reduced,

    /** Below [OFF_BELOW] percent: the port is switched off. */
    Off,
    ;

    companion object {
        const val REDUCE_BELOW = 30
        const val OFF_BELOW = 20
        const val REDUCED_WATTS = 60

        fun of(batteryPercent: Int): BatteryGuard = when {
            batteryPercent < OFF_BELOW -> Off
            batteryPercent < REDUCE_BELOW -> Reduced
            else -> None
        }
    }
}

/** The parts of the cabin the app asks the vehicle to move. */
enum class CabinPart {
    Fan, Window, Recirculation, Seat, ReadingLight, Ambient,
    AirDirection, Temperature, SeatVentilation, SeatHeating, AirCleaner, Defroster, AmbientColor, CallVolume,
    SeatSlide, SeatHeight, CushionTilt, CushionLength, Lumbar, CabinLight, Shade, DoorLock, SteeringWheel,
}

/**
 * What the app is doing to the cabin for the call in progress, and what it needs to undo it.
 *
 * [original] is the cabin as the call was answered. [asked] holds, for every part the app has
 * moved since, the value it asked for last. A part whose reported value changes in a way no
 * request explains was moved by the driver ([overridden]): the app leaves it alone until the call
 * ends and does not put it back. A part the vehicle refused to move ([refused]) is not asked
 * again, but is still put back if an earlier request went through.
 */
data class CabinTuning(
    val original: CabinState,
    val asked: CabinChange = CabinChange.NONE,
    val overridden: Set<CabinPart> = emptySet(),
    val refused: Set<CabinPart> = emptySet(),
    /** Tuning moments in a row at which the call was heard well. */
    val goodTicks: Int = 0,
    /** Tuning moments since the app last raised the fan; null when its last fan move was down. */
    val ticksSinceRaise: Int? = null,
    /** A fan level that proved too loud right after the app went back up past it: never above this again. */
    val fanCap: Int? = null,
    /** The seat and the set temperature are making up for a fan well below where it was. */
    val comfort: Boolean = false,
    /** Outside air is let in for a moment after a long time on inside air. */
    val airing: Boolean = false,
    /** The air has been drawn from inside long enough for the windshield to fog up in the rain. */
    val longRecirculation: Boolean = false,
) {
    /** Whether the app may still ask the vehicle to move [part] on this call. */
    fun mayMove(part: CabinPart): Boolean = part !in overridden && part !in refused

    /** What puts the cabin back: the original value of every part the app moved and the driver did not. */
    val restore: CabinChange get() = original.asChange().only(asked.parts - overridden)

    companion object {
        /** The parts tuning moves, and so the ones watched for the driver's own hand. */
        val PARTS: Set<CabinPart> = setOf(
            CabinPart.Fan, CabinPart.Window, CabinPart.Recirculation, CabinPart.AirDirection,
            CabinPart.Temperature, CabinPart.SeatVentilation, CabinPart.SeatHeating, CabinPart.AirCleaner,
            CabinPart.Defroster, CabinPart.AmbientColor, CabinPart.CallVolume,
        )
    }
}

/** The meeting a call carries: its title and the materials shared for it. */
data class MeetingInfo(val title: String = "", val materials: List<MeetingMaterial> = emptyList())

data class MeetingMaterial(
    val title: String,
    val detail: String,
    val highlights: List<String>,
)

/**
 * Whether the driver can follow the call right now. The call is written down from start to end
 * either way; this only decides which sentences are marked as missed.
 */
sealed interface CaptureState {
    data object Idle : CaptureState

    /**
     * The road is demanding, so what is said now is marked as missed. [lineCount] is how many
     * sentences this stretch has marked so far.
     */
    data class Recording(val reason: RoadDemand, val lineCount: Int) : CaptureState
}

/** Where the talk after a call has got to. */
enum class DialogStep {
    /** "Would you like to hear a summary of the call?" ("통화 요약을 듣고 싶으신가요?") */
    Offer,

    /** The key summary, then the part the driver missed. */
    Explain,

    /** "Is there anything you would like to know about it?" */
    Ask,

    /** What the memo says about the driver's question. */
    Answer,
}

/**
 * The talk the car starts after a call about the memo [memoId]. Each step opens with the car
 * speaking; [said] turns true once it has finished, and only then (in [DialogStep.Offer] and
 * [DialogStep.Ask]) is the driver's reply listened for. [question] is what the driver asked, in
 * [DialogStep.Answer].
 */
data class PostCallDialog(
    val memoId: Int,
    val step: DialogStep = DialogStep.Offer,
    val said: Boolean = false,
    val question: String = "",
)

/** What the car is doing in the talk after a call. */
enum class DialogPhase {
    /** The road is demanding: the car keeps quiet until it clears. */
    Waiting,

    /** The car is speaking and does not listen. */
    Speaking,

    /** The car has spoken and waits for the driver's reply. */
    Listening,
}

/**
 * The steps office mode takes, in the order it takes them; it undoes them in the opposite order.
 * The steering wheel goes first and comes back last, so the knees never meet it while the seat
 * moves.
 */
enum class OfficeStep {
    /** The steering wheel, up and forward. */
    SteeringWheel,

    /** The seat, back and a little lower. */
    SeatPosition,

    /** The seat cushion: its front lowered, and shortened. */
    Cushion,

    /** The backrest upright and the lumbar support forward. */
    Backrest,

    /** Reading light, cabin light and ambient light. */
    Lights,

    /** Sunshades or smart glass. */
    Shade,

    /** A quiet fan and seat ventilation. */
    Climate,

    DoorLock,

    /** The USB-C port switched to charging a laptop. */
    Usb,
}

enum class OfficePhase { Off, Applying, On, Restoring }

/** What office mode is set up for. Switching between the two never moves the seat or the wheel. */
enum class OfficeScene {
    /** Typing: light on the keyboard, glare kept off the screen. */
    Work,

    /** A video meeting: light on the face, everything shaded, quieter still. */
    Meeting,
}

/** The one thing office mode is to move next: [step] set up, or put back when [undo]. */
data class OfficeMove(val step: OfficeStep, val undo: Boolean)

data class OfficeState(
    val phase: OfficePhase = OfficePhase.Off,
    /** The steps that have been set up and not put back yet. */
    val completed: Set<OfficeStep> = emptySet(),
    /** Cabin values from before office mode, restored when it ends. */
    val snapshot: CabinState? = null,
    /** The driver stopped the sequence where it was; nothing moves until they say how to go on. */
    val paused: Boolean = false,
    val scene: OfficeScene = OfficeScene.Work,
) {
    /**
     * The step that is under way or comes next, whether or not the sequence is stopped: setting
     * up goes through [OfficeStep] in order, putting back goes through the steps set up in the
     * opposite order. Null when nothing is left to move.
     */
    val pending: OfficeMove?
        get() = when (phase) {
            OfficePhase.Applying -> OfficeStep.entries.firstOrNull { it !in completed }?.let { OfficeMove(it, undo = false) }
            OfficePhase.Restoring -> OfficeStep.entries.lastOrNull { it in completed }?.let { OfficeMove(it, undo = true) }
            OfficePhase.Off, OfficePhase.On -> null
        }

    /** What is to be moved right now: [pending], unless the driver has stopped the sequence. */
    val move: OfficeMove? get() = pending.takeUnless { paused }
}

/** What the screen shows. Derived from [SyncDeskState]; never stored. */
sealed interface SyncDeskMode {
    sealed interface Driving : SyncDeskMode

    data object Standby : Driving

    /**
     * The cabin is tuned for the call; [restore] is what comes back when it ends. [hearing] is
     * how well the other party is heard right now, or null when nothing measures it.
     */
    data class Quieting(val restore: CabinChange, val hearing: Hearing?) : Driving

    /** A demanding stretch during a call: what is said now is marked as missed. */
    data class Capturing(val reason: RoadDemand, val lineCount: Int) : Driving

    /** The call has ended and the car talks the driver through its memo. */
    data class Conversing(val step: DialogStep, val phase: DialogPhase) : Driving

    /** The car asks the driver to take the wheel and no call is going on. */
    data object TakeOver : Driving

    /** The desk is open: the car is parked or drives itself. */
    data class Desk(val office: OfficeState, val stage: DriveStage) : SyncDeskMode
}

data class SyncDeskState(
    val signals: VehicleSignals = VehicleSignals(),
    /** What the vehicle last reported. The engine never edits it; it asks through [command]. */
    val cabin: CabinState = CabinState(),
    val ranges: CabinRanges = CabinRanges(),
    /** The latest change the engine wants carried out, or null if it has asked for none yet. */
    val command: CabinCommand? = null,
    val call: CallState = CallState(),
    /** Whether the app tunes the cabin for calls at all; the driver can switch it off. */
    val autoQuietEnabled: Boolean = true,
    /** The other party's voice against the cabin noise, as last measured on this call; null when unknown. */
    val hearing: HearingLevels? = null,
    /**
     * Tuning moments in a row on this call that found the call hardly heard ([Hearing.Bad]). Any
     * better reading in between starts the count again. See [missingForNoise].
     */
    val badTicks: Int = 0,
    /** A turn that has just ended, still counted as demanding for a moment: a sentence under way is still marked as missed. */
    val heldDemand: RoadDemand? = null,
    /** Non-null while the cabin is tuned for a call; holds what was changed and what to restore. */
    val tuning: CabinTuning? = null,
    val capture: CaptureState = CaptureState.Idle,
    val office: OfficeState = OfficeState(),
    val preset: OfficePreset = OfficePreset(),
    /** One memo per call, oldest first; only the latest [SyncDeskEngine.MAX_MEMOS] are kept. */
    val memos: List<CallMemo> = emptyList(),
    /** The talk about the call that has just ended; null when there is none. */
    val dialog: PostCallDialog? = null,
    /**
     * Whether the driver agreed, on first use, that calls are written down and the cabin is
     * tuned for them. Until then neither happens. True by default; the app starts from what it saved.
     */
    val consented: Boolean = true,
    /** The driver chose "later" at this stop; asked again at the next one. */
    val consentDeferred: Boolean = false,
    /** A part of the cabin the vehicle just refused to move, shown for a few seconds. */
    val refusal: CabinPart? = null,
    /** Whether speech recognition can be used; without it no call is written down. */
    val speechAvailable: Boolean = true,
    /** The vehicle is still being linked. */
    val vehicleLinking: Boolean = false,
    /** Changes of state are marked with a short sound. */
    val chimesEnabled: Boolean = true,
    /** What the platform restricts while driving. */
    val ux: UxLimits = UxLimits(),
    /** The meeting on the latest call: it names the call's memo and brings its materials. */
    val meeting: MeetingInfo = MeetingInfo(),
    /**
     * Which drive this is. One drive lasts from leaving Park to being parked again; the stop that
     * follows still counts to it, so what is shown on parking is that drive's.
     */
    val trip: Int = 0,
    /** What the driver's phone last reported as not looked at yet. */
    val contacts: List<Contact> = emptyList(),
    /** Contacts that were already waiting when this drive began: an earlier stop's, not this one's. */
    val staleContacts: Set<String> = emptySet(),
    /** The USB-C port as the vehicle last reported it. */
    val usb: UsbPort = UsbPort(),
) {
    val roadDemand: RoadDemand
        get() = when {
            signals.isParked -> RoadDemand.Clear
            signals.takeoverRequested -> RoadDemand.TakeOver
            // While the car drives itself, rain and turns are its business, not the driver's.
            signals.autonomous -> RoadDemand.Clear
            signals.wiper == WiperSpeed.High -> RoadDemand.HeavyRain
            signals.turnSignal == TurnSignal.Left -> RoadDemand.TurningLeft
            signals.turnSignal == TurnSignal.Right -> RoadDemand.TurningRight
            signals.steeringTurn == TurnSignal.Left -> RoadDemand.TurningLeft
            signals.steeringTurn == TurnSignal.Right -> RoadDemand.TurningRight
            else -> RoadDemand.Clear
        }
    val isTuning: Boolean get() = tuning != null

    /**
     * The air is drawn from inside because the app wants it so. How long that lasts decides when
     * outside air is let in for a moment.
     */
    val holdsAirInside: Boolean
        get() = tuning != null && !tuning.airing && cabin.recirculation && tuning.mayMove(CabinPart.Recirculation)

    /**
     * The cabin has been too loud to hear the call for at least one whole tuning interval: the call
     * is hardly heard now, and [SyncDeskEngine.NOISE_MARK_TICKS] tuning moments in a row have found
     * it so with no better reading in between. Only then is what is said marked as missed for the
     * noise. A single reading is not enough: the noise around the car may have just risen at the end
     * of a sentence that was heard well, or the fan may be about to come down a step.
     */
    val missingForNoise: Boolean
        get() = call.active && hearing?.hearing == Hearing.Bad && badTicks >= SyncDeskEngine.NOISE_MARK_TICKS

    /** What is said on the call right now is marked as missed, for the road or for the noise. */
    val markingMissed: Boolean
        get() = capture is CaptureState.Recording || missingForNoise

    /** Parked, not yet agreed and not put off: the first-use notice is shown. */
    val asksConsent: Boolean get() = !consented && !consentDeferred && signals.isParked

    /** The memo of the call in progress, once its first sentence has been kept. */
    val callMemo: CallMemo? get() = call.memoId?.let { id -> memos.firstOrNull { it.id == id } }

    /** The memo of the latest call on which anything was said. */
    val latestMemo: CallMemo? get() = memos.lastOrNull()

    /**
     * The memos of this drive's calls as the list on parking shows them: those the car has not
     * talked the driver through yet first, and the newest first among each.
     */
    val tripMemos: List<CallMemo>
        get() = memos.filter { it.trip == trip }.sortedWith(compareBy<CallMemo> { it.explained }.thenByDescending { it.id })

    /** The memos of earlier drives, newest first. */
    val earlierMemos: List<CallMemo> get() = memos.filter { it.trip != trip }.sortedByDescending { it.id }

    /**
     * The contacts of this drive the driver has not looked at, for the one line shown on parking.
     * Null while the car is not parked: nothing of it is shown or said while driving. What the
     * driver looked at on the way (a message read at a red light, a call returned) is no longer
     * among [contacts] and so is not counted.
     */
    val contactDigest: ContactDigest?
        get() = if (signals.isParked && consented) ContactDigest.of(contacts.filter { it.id !in staleContacts }) else null

    /** Parked with something of this drive to show: a call memo, or contacts not looked at. */
    val hasParkedDigest: Boolean
        get() = signals.isParked && consented && (tripMemos.isNotEmpty() || contactDigest != null)

    /** Office mode has switched the USB-C port to charging a laptop. */
    val officePowersUsb: Boolean get() = OfficeStep.Usb in office.completed && preset.usbPower

    /** How far the vehicle battery holds the USB-C port back, while office mode has it charging. */
    val batteryGuard: BatteryGuard
        get() = if (officePowersUsb) BatteryGuard.of(cabin.batteryPercent) else BatteryGuard.None

    /**
     * What office mode wants of the USB-C port right now: full output with charge to spare,
     * [BatteryGuard.REDUCED_WATTS] at most on a low battery, off on a lower one. Null while office
     * mode does not hold the port, which leaves it at the vehicle's ordinary output.
     */
    val usbOrder: UsbOrder?
        get() = if (!officePowersUsb) {
            null
        } else {
            when (batteryGuard) {
                BatteryGuard.None -> UsbOrder(on = true, limitWatts = usb.maxWatts)
                BatteryGuard.Reduced -> UsbOrder(on = true, limitWatts = minOf(BatteryGuard.REDUCED_WATTS, usb.maxWatts))
                BatteryGuard.Off -> UsbOrder(on = false, limitWatts = 0)
            }
        }

    /**
     * The car has asked and waits for the driver's reply, on a clear road. Never while it speaks,
     * so its own voice is not taken for a reply.
     */
    val listeningForReply: Boolean
        get() = dialog != null && dialog.said && roadDemand == RoadDemand.Clear &&
            (dialog.step == DialogStep.Offer || dialog.step == DialogStep.Ask)

    /** A refusal is said only on a clear road; on a demanding stretch it waits. */
    val shownRefusal: CabinPart? get() = refusal?.takeIf { roadDemand == RoadDemand.Clear }

    val mode: SyncDeskMode
        get() = when {
            signals.deskOpen -> SyncDeskMode.Desk(office, signals.stage)
            capture is CaptureState.Recording -> SyncDeskMode.Capturing(capture.reason, capture.lineCount)
            roadDemand == RoadDemand.TakeOver -> SyncDeskMode.TakeOver
            tuning != null -> SyncDeskMode.Quieting(tuning.restore, hearing?.hearing)
            dialog != null -> SyncDeskMode.Conversing(
                step = dialog.step,
                phase = when {
                    roadDemand != RoadDemand.Clear -> DialogPhase.Waiting
                    dialog.said -> DialogPhase.Listening
                    else -> DialogPhase.Speaking
                },
            )
            else -> SyncDeskMode.Standby
        }
}

sealed interface SyncDeskEvent {
    data class SignalsChanged(val signals: VehicleSignals) : SyncDeskEvent
    data class CabinReported(val cabin: CabinState) : SyncDeskEvent
    data class RangesReported(val ranges: CabinRanges) : SyncDeskEvent
    data class UxLimitsChanged(val limits: UxLimits) : SyncDeskEvent
    /**
     * [date] (ISO, "2026-11-15") and [time] ("14:05") say when this was seen; a call that starts
     * takes them as its start.
     */
    data class CallChanged(
        val active: Boolean,
        val driverSpeaking: Boolean,
        val date: String = "",
        val time: String = "",
    ) : SyncDeskEvent
    /**
     * The app was stopped while the cabin was tuned for a call; [restore] is what puts it back.
     * Sent once at start, after the vehicle is linked.
     */
    data class QuietRecovered(val restore: CabinChange) : SyncDeskEvent
    /** The other party's voice and the cabin noise were measured. */
    data class HearingChanged(val levels: HearingLevels) : SyncDeskEvent
    /**
     * A tuning moment, sent at a steady pace for as long as a call lasts: a tuned cabin may change
     * one thing by one step, and how long the call has been hardly heard is counted in moments.
     */
    data object TuneTick : SyncDeskEvent
    /** The air has been drawn from inside for a while: in the rain the windshield may fog up. */
    data object RecirculationLong : SyncDeskEvent
    /** The air has been drawn from inside for a long time: outside air is due. */
    data object FreshAirDue : SyncDeskEvent
    /** Outside air has been let in long enough. */
    data object FreshAirDone : SyncDeskEvent
    /** A turn ended a moment ago: sentences are no longer marked as missed. */
    data object DemandHoldExpired : SyncDeskEvent
    data object CallTick : SyncDeskEvent
    data class AutoQuietToggled(val enabled: Boolean) : SyncDeskEvent
    /**
     * A sentence was heard on the call: one of the others', or a promise of the driver's own
     * ([MeetingNote.byDriver]). The engine gives it its place in the call's memo and marks it as
     * missed when the road is demanding.
     */
    data class NoteCaptured(val note: MeetingNote) : SyncDeskEvent
    /** A call started carrying [meeting]. */
    data class MeetingStarted(val meeting: MeetingInfo) : SyncDeskEvent
    /** The driver agreed to calls being written down and the cabin being tuned for them. */
    data object ConsentGiven : SyncDeskEvent
    /** The driver chose to decide later; asked again at the next stop. */
    data object ConsentDeferred : SyncDeskEvent
    /** The vehicle refused to move [part]. */
    data class CommandRefused(val part: CabinPart) : SyncDeskEvent
    /** The refusal has been shown long enough. */
    data object RefusalCleared : SyncDeskEvent
    data class SpeechChecked(val available: Boolean) : SyncDeskEvent
    data class LinkChanged(val pending: Boolean) : SyncDeskEvent
    data class ChimesToggled(val enabled: Boolean) : SyncDeskEvent
    /** The car has finished saying what the current step of the talk after a call opens with. */
    data object DialogSpoken : SyncDeskEvent
    /** The driver said [reply] while the car was listening. */
    data class DriverReplied(val reply: DriverReply) : SyncDeskEvent
    /** The car has waited long enough for a reply. */
    data object DialogReplyTimedOut : SyncDeskEvent
    /** The driver closed the talk after a call on the screen. */
    data object DialogClosed : SyncDeskEvent
    data object OfficeRequested : SyncDeskEvent
    /** The vehicle has finished (or refused) setting up [step]. */
    data class OfficeStepDone(val step: OfficeStep) : SyncDeskEvent
    /** The vehicle has finished (or refused) putting [step] back. */
    data class OfficeStepUndone(val step: OfficeStep) : SyncDeskEvent
    /** The driver stopped the office sequence where it is. */
    data object OfficePauseRequested : SyncDeskEvent
    /** The driver lets a stopped office sequence go on the way it was going. */
    data object OfficeResumeRequested : SyncDeskEvent
    /** Everything office mode has set up so far is to go back, last step first. */
    data object OfficeRestoreRequested : SyncDeskEvent
    /** The driver switched office mode between working and a meeting. */
    data class OfficeSceneChanged(val scene: OfficeScene) : SyncDeskEvent
    data class PresetChanged(val preset: OfficePreset) : SyncDeskEvent
    /** The driver's phone reported the contacts they have not looked at yet (all of them, each time). */
    data class ContactsReported(val contacts: List<Contact>) : SyncDeskEvent
    /** The vehicle reported the USB-C port. */
    data class UsbReported(val port: UsbPort) : SyncDeskEvent
}
