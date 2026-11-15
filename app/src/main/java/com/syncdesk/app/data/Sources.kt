package com.syncdesk.app.data

import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinRanges
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.Contact
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.MeetingInfo
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.UsbOrder
import com.syncdesk.app.domain.UsbPort
import com.syncdesk.app.domain.VehicleSignals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The parts of the vehicle the app reads or controls.
 *
 * A feature that is not [linkable] is simulated on every car: Vehicle SDK 2.3.2 either has no
 * call for it (the steering wheel's position, the USB-C port), or its call was answered on the
 * Pleos Connect v2.1.0 emulator without anything changing (call volume, cushion length, lumbar
 * support, shades; checked 2026-11-15, see `05_원격확인/SDK_확인결과.md`).
 */
enum class VehicleFeature(val linkable: Boolean = true) {
    Gear, Wiper, TurnSignal, DriveAssist, Steering, Fan, Window, Recirculation, ReadingLight, Ambient, Seat,
    AirDirection, Temperature,
    /** Whether the air conditioning is on; read only. */
    AirConditioning,
    AirCleaner, Defroster,
    /** Seat ventilation and seat heating: one value in the vehicle (see `seatClimateLevel`). */
    SeatClimate,
    AmbientColor, SeatSlide, SeatHeight, CushionTilt, CabinLight, DoorLock,
    /** The battery's charge; read only. */
    Battery,
    CallVolume(linkable = false),
    CushionLength(linkable = false),
    Lumbar(linkable = false),
    Shade(linkable = false),
    /** The steering wheel's position (not its angle, which is [Steering]). */
    WheelPosition(linkable = false),
    UsbPower(linkable = false),
    ;

    companion object {
        /** The feature that carries [part] to the vehicle. */
        fun of(part: CabinPart): VehicleFeature = when (part) {
            CabinPart.Fan -> Fan
            CabinPart.Window -> Window
            CabinPart.Recirculation -> Recirculation
            CabinPart.Seat -> Seat
            CabinPart.ReadingLight -> ReadingLight
            CabinPart.Ambient -> Ambient
            CabinPart.AirDirection -> AirDirection
            CabinPart.Temperature -> Temperature
            CabinPart.SeatVentilation, CabinPart.SeatHeating -> SeatClimate
            CabinPart.AirCleaner -> AirCleaner
            CabinPart.Defroster -> Defroster
            CabinPart.AmbientColor -> AmbientColor
            CabinPart.CallVolume -> CallVolume
            CabinPart.SeatSlide -> SeatSlide
            CabinPart.SeatHeight -> SeatHeight
            CabinPart.CushionTilt -> CushionTilt
            CabinPart.CushionLength -> CushionLength
            CabinPart.Lumbar -> Lumbar
            CabinPart.CabinLight -> CabinLight
            CabinPart.Shade -> Shade
            CabinPart.DoorLock -> DoorLock
            CabinPart.SteeringWheel -> WheelPosition
        }
    }
}

/**
 * Which features are backed by the real vehicle right now. Everything else is simulated inside
 * the app, either because the vehicle does not offer it or because no vehicle is connected.
 */
data class VehicleLink(
    val live: Set<VehicleFeature> = emptySet(),
    /** Still finding out which features the vehicle offers. */
    val pending: Boolean = false,
) {
    val connected: Boolean get() = live.isNotEmpty()
    val simulated: Set<VehicleFeature> get() = VehicleFeature.entries.toSet() - live

    /**
     * Whether [change] can be sent now and reach the vehicle wherever it can: every part of it
     * that a vehicle could carry is live, or a connected vehicle has finished linking (what is
     * not live by then stays simulated).
     */
    fun readyFor(change: CabinChange): Boolean {
        if (connected && !pending) return true
        return change.parts.map { VehicleFeature.of(it) }.filter { it.linkable }.all { it in live }
    }
}

/** The vehicle as the app sees it. Implemented by the Pleos Vehicle SDK and by a simulation. */
interface VehicleGateway {
    val signals: StateFlow<VehicleSignals>

    /** The cabin as the vehicle reports it. Window opening and ambient brightness are in percent. */
    val cabin: StateFlow<CabinState>
    val ranges: StateFlow<CabinRanges>
    val link: StateFlow<VehicleLink>

    /**
     * Asks the cabin hardware for [change] and returns once every part has answered or timed out.
     * The outcome is seen in [cabin]; a part the vehicle refuses simply keeps its value.
     */
    suspend fun apply(change: CabinChange)

    /** Each part of the cabin the vehicle refuses to move, as it happens. */
    val failures: Flow<CabinPart> get() = emptyFlow()
}

data class CallSignal(
    val active: Boolean = false,
    val driverSpeaking: Boolean = false,
)

/** Reports whether a call is in progress and whether the driver is the one speaking. */
interface CallMonitor {
    val call: StateFlow<CallSignal>
}

/** The meeting on the other end of the call. */
interface MeetingSource {
    val title: String
    val materials: List<MeetingMaterial>

    /** Emits each sentence the other participants say, for as long as it is collected. */
    fun transcript(): Flow<MeetingNote>

    /**
     * Emits each promise the driver makes on the call, for as long as it is collected: the
     * driver's own sentence ([MeetingNote.byDriver]) with what they promised as its key point.
     */
    fun promises(): Flow<MeetingNote> = emptyFlow()

    /** Emits the meeting each call carries, when the call starts. */
    fun meetings(): Flow<MeetingInfo> = emptyFlow()
}

/**
 * Remembers, while the cabin is tuned for a call, the change that puts it back, so the cabin can
 * be put back even if the app is stopped in the middle. Null when nothing has been moved.
 */
interface QuietStore {
    fun load(): CabinChange?
    fun save(restore: CabinChange?)
}

/**
 * How loud the other party's voice and the cabin noise are, which together say how well the
 * driver hears the call.
 *
 * Only a simulation stands behind this for now ([SimulatedHearing]). In a vehicle both readings
 * need system privileges a regular app does not have: the other party's voice is the call's
 * downlink audio, and the cabin noise comes from a cabin microphone the call is not using. An
 * app that ships with the vehicle puts the real measurement behind the same interface.
 */
interface HearingSource {
    /** Emits the levels whenever either changes, for as long as it is collected (during a call). */
    fun levels(): Flow<HearingLevels>
}

/**
 * The calls the driver did not take and the messages they have not read, as their phone has them.
 *
 * Only a simulation stands behind this for now ([SimulatedContacts]). Reading them from a real
 * phone needs the phone link's own access (Bluetooth message and phone-book access, or the phone
 * projection's notification stream), which the app does not have yet. Whatever goes behind this
 * interface reports who and how many, never what a message says.
 */
interface ContactSource {
    /**
     * Everything not looked at yet, oldest first. A contact leaves the list once the driver has
     * looked at it on the phone: a message read, a missed call returned or dismissed.
     */
    val unseen: StateFlow<List<Contact>>
}

/**
 * The USB-C port a laptop is charged from.
 *
 * Only a simulation stands behind this ([SimulatedUsbPower]): Vehicle SDK 2.3.2 has no USB port
 * or power outlet in it. Switching a port's power, setting its output and knowing where it is and
 * what it can deliver all have to come from the vehicle maker; an app that ships with the vehicle
 * puts that behind the same interface.
 */
interface UsbPower {
    /** The port as the vehicle reports it. */
    val port: StateFlow<UsbPort>

    /**
     * Asks the port for [order]; null gives it back to the vehicle's ordinary output. The outcome
     * is seen in [port].
     */
    suspend fun order(order: UsbOrder?)
}

/** Remembers the call memos between runs, oldest first, and which drive the memos belong to. */
interface CallMemoStore {
    fun load(): List<CallMemo>
    fun save(memos: List<CallMemo>)

    /** The drive the app was in when it last ran, or null if none was saved (an older version). */
    fun loadTrip(): SavedTrip? = null
    fun saveTrip(trip: SavedTrip) = Unit
}

/**
 * Which drive it was ([com.syncdesk.app.domain.SyncDeskState.trip]) and whether the car stood in
 * Park, as last known. A run that starts mid-drive carries on with the same drive, so the drive's
 * calls are not moved to the earlier ones.
 */
data class SavedTrip(val trip: Int, val parked: Boolean)

/**
 * Answers a question about a call from its memo. The rules of [RuleMemoAnswerer] stand behind it
 * for now; an assistant that understands the question (Gleo AI) goes behind the same interface.
 */
interface MemoAnswerer {
    /**
     * What [memo] says about [question] as one short line, or null when the call did not cover
     * it. Never makes anything up.
     */
    suspend fun answer(memo: CallMemo, question: String): String?
}

/** The driver's choices that last between runs. */
data class AppSettings(
    /** Agreed, on first use, to calls being written down and the cabin being tuned for them. */
    val consented: Boolean = false,
    /** State changes are marked with a short sound. */
    val chimesEnabled: Boolean = true,
)

/** Remembers [AppSettings] between runs. */
interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

/** Remembers the driver's office arrangement between runs. */
interface PresetStore {
    fun load(): OfficePreset
    fun save(preset: OfficePreset)
}

/** Turns recorded speech into text. Implemented by the SpeechToText SDK. */
interface SpeechRecognizer {
    /** Whether a recognizer can be used on this device right now. */
    suspend fun isAvailable(): Boolean

    /**
     * Recognizes [pcm] (16 kHz, 16-bit, mono) and returns what was said, or null when no
     * recognizer is available or nothing was recognized. Takes about as long as the audio lasts,
     * because the audio is streamed at its own pace.
     *
     * [onSpeech] is told true as soon as the recognizer starts hearing words, and false when it
     * closes the sentence. It may be called from any thread.
     */
    suspend fun transcribe(pcm: ByteArray, onSpeech: (speaking: Boolean) -> Unit = {}): String?
}

/**
 * What the driver says back in the talk after a call, for as long as it is collected (the app
 * collects only while it is listening). The microphone is not connected yet: a recognizer that
 * turns what it hears into [DriverReply.of] goes behind this interface. The demo controls send
 * replies through the same path.
 */
interface DriverReplySource {
    fun replies(): Flow<DriverReply>
}

/** The moments marked with a short sound, so the driver knows without looking. */
enum class Cue {
    /**
     * A demanding stretch of the call is over and what was said in it is marked in the call memo:
     * two falling notes. Nothing is read aloud during the call.
     */
    MissedPartMarked,
}

/** Plays a [Cue]. Returns once the sound has finished. */
interface ChimePlayer {
    suspend fun play(cue: Cue)
}

/** Reads text aloud. Implemented by the TextToSpeech SDK. */
interface Narrator {
    /** Returns once every line has been spoken. */
    suspend fun speak(lines: List<String>)
}
