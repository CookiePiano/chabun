package com.syncdesk.app.data

import android.content.res.Resources
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinRanges
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.Contact
import com.syncdesk.app.domain.ContactKind
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.KeyPoints
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.UsbLocation
import com.syncdesk.app.domain.UsbOrder
import com.syncdesk.app.domain.UsbPort
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import com.syncdesk.app.domain.applying
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Sets vehicle signals that are simulated. Has no effect on a signal the real vehicle provides. */
interface VehicleSimulator {
    fun setGear(gear: Gear)
    fun setWiper(speed: WiperSpeed)
    fun setTurnSignal(signal: TurnSignal)

    /**
     * Switches self-driving on or off. No vehicle reports this yet, so it is always simulated;
     * switching it off is the car handing the wheel back to the driver.
     */
    fun setAutonomous(autonomous: Boolean)

    /**
     * Sets the vehicle battery's charge in percent. No vehicle value is linked to it yet, so it
     * is always simulated; office mode guards the battery by it.
     */
    fun setBatteryPercent(percent: Int)
}

/** Sets the simulated phone call. */
interface CallSimulator {
    fun setCallActive(active: Boolean)
    fun setDriverSpeaking(speaking: Boolean)
}

/** Says the driver's reply, as the demo controls do while no microphone is connected. */
interface ReplySimulator {
    fun sendReply(reply: DriverReply)
}

/** How loud it is around the car, apart from what the cabin itself adds. */
enum class AmbientNoise { Low, Normal, High }

/** Sets the simulated noise around the car. */
interface NoiseSimulator {
    val ambientNoise: StateFlow<AmbientNoise>
    fun setAmbientNoise(level: AmbientNoise)
}

/** Puts contacts on the simulated phone, and looks at them. */
interface ContactSimulator {
    /** A call the driver did not take. */
    fun addMissedCall()

    /** A message the driver has not read. */
    fun addMessage()

    /** The driver looked at everything on the phone: nothing is left unseen. */
    fun markAllSeen()
}

/** Plugs a device into the simulated USB-C port. */
interface UsbSimulator {
    /** What the connected device asks for in watts; null with nothing connected. */
    val deviceWatts: StateFlow<Int?>
    fun setDeviceWatts(watts: Int?)
}

/** Everything the in-app demo controls can change. */
interface DemoControls : VehicleSimulator, CallSimulator, ReplySimulator, NoiseSimulator, ContactSimulator, UsbSimulator

/**
 * The cabin noise a simulated car makes, on the dB-like scale of [HearingLevels]: the road, the
 * fan (more so while it blows at the face, next to the microphone), an open window, heavy rain,
 * and whatever is around the car. Lowering the fan or closing the window therefore really makes
 * the call easier to hear.
 */
object CabinNoise {
    const val ROAD = 34
    const val PARKED = 28
    const val PER_FAN_LEVEL = 2
    const val AIR_AT_FACE = 2
    /** An open window adds one for every this many percent it is open. */
    const val WINDOW_PERCENT_PER_STEP = 5
    const val HEAVY_RAIN = 8
    const val AMBIENT_LOW = 0
    const val AMBIENT_NORMAL = 3
    const val AMBIENT_HIGH = 18

    fun level(cabin: CabinState, signals: VehicleSignals, ambient: AmbientNoise): Int {
        val road = if (signals.isParked) PARKED else ROAD
        val fan = cabin.fanLevel * PER_FAN_LEVEL + if (cabin.climateOn && cabin.airDirection.atFace) AIR_AT_FACE else 0
        val window = (cabin.windowOpenPercent + WINDOW_PERCENT_PER_STEP - 1) / WINDOW_PERCENT_PER_STEP
        val rain = if (signals.wiper == WiperSpeed.High) HEAVY_RAIN else 0
        val around = when (ambient) {
            AmbientNoise.Low -> AMBIENT_LOW
            AmbientNoise.Normal -> AMBIENT_NORMAL
            AmbientNoise.High -> AMBIENT_HIGH
        }
        return road + fan + window + rain + around
    }
}

/**
 * The loudness of a recorded voice on the dB-like scale of [HearingLevels], and what the call
 * volume adds to it.
 */
object VoiceLevel {
    /** A voice at ordinary call loudness, used where there is no recording to measure. */
    const val NOMINAL = 62

    /** What one step of call volume adds. */
    const val PER_VOLUME_STEP = 3

    /** The call volume at which a recording is heard at the level measured from it. */
    val REFERENCE_VOLUME = CabinState().callVolume

    private const val DBFS_OFFSET = 82
    private const val SILENCE_DBFS = -45.0
    private const val CHUNK_BYTES = 640

    /**
     * How loud the speech in [pcm] (16 kHz, 16-bit, mono) is: the level of its 20 ms chunks that
     * are not silence, so pauses do not make a voice count as quieter. Null when it holds no speech.
     */
    fun of(pcm: ByteArray): Int? {
        var energy = 0.0
        var spoken = 0
        var offset = 0
        while (offset + CHUNK_BYTES <= pcm.size) {
            val dbfs = SpeechGate.dbfs(pcm.copyOfRange(offset, offset + CHUNK_BYTES))
            if (dbfs > SILENCE_DBFS) {
                energy += Math.pow(10.0, dbfs / 10)
                spoken++
            }
            offset += CHUNK_BYTES
        }
        if (spoken == 0) return null
        return Math.round(10 * Math.log10(energy / spoken)).toInt() + DBFS_OFFSET
    }

    /** [recorded] as the driver hears it with the call volume at [callVolume]. */
    fun heard(recorded: Int, callVolume: Int): Int = recorded + (callVolume - REFERENCE_VOLUME) * PER_VOLUME_STEP
}

/**
 * Hearing as a simulation: the other party's [voice] (the loudness of the recorded sentence being
 * played, see [VoiceLevel]) against the noise the simulated cabin makes ([CabinNoise]). The demo
 * controls set the noise around the car.
 */
class SimulatedHearing(
    private val cabin: StateFlow<CabinState>,
    private val signals: StateFlow<VehicleSignals>,
    private val voice: StateFlow<Int> = MutableStateFlow(VoiceLevel.NOMINAL),
    initialNoise: AmbientNoise = AmbientNoise.Normal,
) : HearingSource, NoiseSimulator {

    private val _ambientNoise = MutableStateFlow(initialNoise)
    override val ambientNoise: StateFlow<AmbientNoise> = _ambientNoise.asStateFlow()

    override fun setAmbientNoise(level: AmbientNoise) {
        _ambientNoise.value = level
    }

    override fun levels(): Flow<HearingLevels> =
        combine(cabin, signals, voice, _ambientNoise) { cabin, signals, voice, ambient ->
            HearingLevels(
                voice = VoiceLevel.heard(voice, cabin.callVolume),
                noise = CabinNoise.level(cabin, signals, ambient),
            )
        }.distinctUntilChanged()
}

/** Replies given from the demo controls; the app hears them only while it listens. */
class DemoReplies : DriverReplySource, ReplySimulator {
    private val said = MutableSharedFlow<DriverReply>(extraBufferCapacity = 4)

    override fun replies(): Flow<DriverReply> = said.asSharedFlow()

    override fun sendReply(reply: DriverReply) {
        said.tryEmit(reply)
    }
}

/**
 * The driver's phone as a simulation: contacts are added and looked at from the demo controls.
 * [callers] and [senders] are the people they come from, taken in turn, so the same person can
 * write more than once.
 */
class SimulatedContacts(
    private val callers: List<String> = listOf("Caller"),
    private val senders: List<String> = listOf("Sender"),
) : ContactSource, ContactSimulator {

    private val _unseen = MutableStateFlow(emptyList<Contact>())
    override val unseen: StateFlow<List<Contact>> = _unseen.asStateFlow()

    private var calls = 0
    private var messages = 0
    private var nextId = 1

    override fun addMissedCall() = add(ContactKind.MissedCall, callers[calls++ % callers.size])

    override fun addMessage() = add(ContactKind.Message, senders[messages++ % senders.size])

    private fun add(kind: ContactKind, from: String) {
        _unseen.update { it + Contact(id = "sim-${nextId++}", kind = kind, from = from) }
    }

    /** The next contacts start from the first names again, as on a phone with nothing waiting. */
    override fun markAllSeen() {
        calls = 0
        messages = 0
        _unseen.value = emptyList()
    }
}

/**
 * A USB-C port that exists only in memory. An order switches it on or off and sets its limit;
 * without one it is back at its ordinary output, which for a parked car is off. The demo controls
 * plug a device in ([setDeviceWatts]).
 */
class SimulatedUsbPower(
    location: UsbLocation = UsbLocation.CenterConsoleFront,
    maxWatts: Int = UsbPort.MAX_WATTS,
) : UsbPower, UsbSimulator {

    private val ordinary = UsbPort(location = location, maxWatts = maxWatts, on = false, limitWatts = maxWatts)
    private val _port = MutableStateFlow(ordinary)
    override val port: StateFlow<UsbPort> = _port.asStateFlow()

    private val _deviceWatts = MutableStateFlow<Int?>(null)
    override val deviceWatts: StateFlow<Int?> = _deviceWatts.asStateFlow()

    override suspend fun order(order: UsbOrder?) = _port.update {
        if (order == null) {
            ordinary.copy(deviceWatts = it.deviceWatts)
        } else {
            it.copy(on = order.on, limitWatts = order.limitWatts.coerceIn(0, it.maxWatts))
        }
    }

    override fun setDeviceWatts(watts: Int?) {
        _deviceWatts.value = watts
        _port.update { it.copy(deviceWatts = watts) }
    }

    companion object {
        /** What the demo controls offer to plug in: nothing, or a device asking for this many watts. */
        val DEMO_DEVICES: List<Int?> = listOf(null, 65, 100, 140, 240)
    }
}

/** How long simulated actuators take, so the office sequence can be seen step by step. */
object ActuatorTiming {
    const val SEAT_MILLIS = 900L
    const val LIGHT_MILLIS = 350L
    const val AMBIENT_MILLIS = 500L
    const val WHEEL_MILLIS = 1_200L
    const val SEAT_SLIDE_MILLIS = 1_200L
    const val SEAT_HEIGHT_MILLIS = 700L
    const val CUSHION_MILLIS = 600L
    const val LUMBAR_MILLIS = 600L
    const val SHADE_MILLIS = 900L
    const val DOOR_LOCK_MILLIS = 250L

    /**
     * Time for [change] to take effect on [from]. Everything but the seat, the steering wheel,
     * the lights, the shade and the door locks is immediate.
     */
    fun travelMillis(from: CabinState, change: CabinChange): Long {
        var millis = 0L
        if (change.seatRecline != null && change.seatRecline != from.seatRecline) millis += SEAT_MILLIS
        if (change.readingLightOn != null && change.readingLightOn != from.readingLightOn) millis += LIGHT_MILLIS
        if (change.ambientPercent != null && change.ambientPercent != from.ambientPercent) millis += AMBIENT_MILLIS
        if (change.wheel != null && change.wheel != from.wheel) millis += WHEEL_MILLIS
        if (change.seatSlide != null && change.seatSlide != from.seatSlide) millis += SEAT_SLIDE_MILLIS
        if (change.seatHeight != null && change.seatHeight != from.seatHeight) millis += SEAT_HEIGHT_MILLIS
        if (change.cushionTilt != null && change.cushionTilt != from.cushionTilt) millis += CUSHION_MILLIS
        if (change.cushionLength != null && change.cushionLength != from.cushionLength) millis += CUSHION_MILLIS
        if (change.lumbar != null && change.lumbar != from.lumbar) millis += LUMBAR_MILLIS
        if (change.cabinLightOn != null && change.cabinLightOn != from.cabinLightOn) millis += LIGHT_MILLIS
        if (change.shadePercent != null && change.shadePercent != from.shadePercent) millis += SHADE_MILLIS
        if (change.doorsLocked != null && change.doorsLocked != from.doorsLocked) millis += DOOR_LOCK_MILLIS
        return millis
    }
}

/**
 * A vehicle and a phone call that exist only in memory. Used for the call until speech detection
 * is connected, in tests, and as the whole vehicle where no Pleos vehicle service is present.
 */
class SimulatedCar(
    initialSignals: VehicleSignals = VehicleSignals(),
    initialCabin: CabinState = CabinState(),
) : VehicleGateway, CallMonitor, VehicleSimulator, CallSimulator {

    private val _signals = MutableStateFlow(initialSignals)
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()

    private val _cabin = MutableStateFlow(initialCabin)
    override val cabin: StateFlow<CabinState> = _cabin.asStateFlow()

    override val ranges: StateFlow<CabinRanges> = MutableStateFlow(CabinRanges()).asStateFlow()
    override val link: StateFlow<VehicleLink> = MutableStateFlow(VehicleLink()).asStateFlow()

    private val _call = MutableStateFlow(CallSignal())
    override val call: StateFlow<CallSignal> = _call.asStateFlow()

    private val actuators = Mutex()

    override suspend fun apply(change: CabinChange) {
        actuators.withLock {
            delay(ActuatorTiming.travelMillis(from = _cabin.value, change = change))
            _cabin.update { it.applying(change) }
        }
    }

    override fun setGear(gear: Gear) = _signals.update { it.copy(gear = gear) }

    override fun setWiper(speed: WiperSpeed) = _signals.update { it.copy(wiper = speed) }

    override fun setTurnSignal(signal: TurnSignal) = _signals.update { it.copy(turnSignal = signal) }

    override fun setAutonomous(autonomous: Boolean) = _signals.update { it.copy(autonomous = autonomous) }

    override fun setBatteryPercent(percent: Int) = _cabin.update { it.copy(batteryPercent = percent.coerceIn(0, 100)) }

    override fun setCallActive(active: Boolean) =
        _call.update { CallSignal(active = active, driverSpeaking = active && it.driverSpeaking) }

    override fun setDriverSpeaking(speaking: Boolean) =
        _call.update { it.copy(driverSpeaking = it.active && speaking) }
}
/** Replays a fixed meeting, one sentence at a time, and starts over when it runs out. */
class ScriptedMeetingSource(
    override val title: String,
    override val materials: List<MeetingMaterial>,
    val script: List<ScriptLine>,
    private val intervalMillis: Long = 2_600L,
    private val now: () -> String = { LocalTime.now().format(TIME_FORMAT) },
) : MeetingSource {

    /** One sentence of the call. [driver] marks the driver's own lines, which are not part of [transcript]. */
    data class ScriptLine(val speaker: String, val text: String, val gist: String?, val driver: Boolean = false)

    private var nextIndex = 0
    private var nextId = 1

    override fun transcript(): Flow<MeetingNote> = flow {
        val others = script.filterNot { it.driver }
        if (others.isEmpty()) return@flow
        while (true) {
            delay(intervalMillis)
            val line = others[nextIndex % others.size]
            nextIndex++
            emit(MeetingNote(id = nextId++, time = now(), speaker = line.speaker, text = line.text, gist = line.gist))
        }
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Where one demo meeting lives in the string resources. */
        data class Resource(
            val title: Int,
            val speakers: Int,
            val texts: Int,
            val materialTitles: Int,
            val materialDetails: Int,
            val materialHighlights: Int,
        )

        /** The weekly delivery meeting. */
        val DELIVERY = Resource(
            R.string.demo_meeting_title, R.array.demo_note_speakers, R.array.demo_note_texts,
            R.array.demo_material_titles, R.array.demo_material_details, R.array.demo_material_highlights,
        )

        /** The line expansion review: another subject, said in other words. */
        val EXPANSION = Resource(
            R.string.demo2_meeting_title, R.array.demo2_note_speakers, R.array.demo2_note_texts,
            R.array.demo2_material_titles, R.array.demo2_material_details, R.array.demo2_material_highlights,
        )

        /** Builds a demo meeting from the string resources, so it follows the system language. */
        fun fromResources(resources: Resources, meeting: Resource = DELIVERY): ScriptedMeetingSource {
            val speakers = resources.getStringArray(meeting.speakers)
            val texts = resources.getStringArray(meeting.texts)
            val driver = resources.getString(R.string.demo_driver_speaker)
            val script = texts.indices.map { index ->
                val speaker = speakers.getOrElse(index) { "" }
                ScriptLine(
                    speaker = speaker,
                    text = texts[index],
                    gist = KeyPoints.of(texts[index]),
                    driver = speaker == driver,
                )
            }
            val materialTitles = resources.getStringArray(meeting.materialTitles)
            val materialDetails = resources.getStringArray(meeting.materialDetails)
            val materialHighlights = resources.getStringArray(meeting.materialHighlights)
            val materials = materialTitles.indices.map { index ->
                MeetingMaterial(
                    title = materialTitles[index],
                    detail = materialDetails.getOrElse(index) { "" },
                    highlights = materialHighlights.getOrElse(index) { "" }
                        .split('|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                )
            }
            return ScriptedMeetingSource(
                title = resources.getString(meeting.title),
                materials = materials,
                script = script,
            )
        }
    }
}

/** Stands in for text-to-speech: stays silent but takes as long as speaking the lines would. */
class SimulatedNarrator(private val millisPerLine: Long = 2_200L) : Narrator {
    override suspend fun speak(lines: List<String>) {
        delay(millisPerLine * lines.size)
    }
}
