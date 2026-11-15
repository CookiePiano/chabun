package com.syncdesk.app.data.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessColorListener
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAirCleanerStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeightPositionListener
import ai.pleos.playground.vehicle.listener.SeatTiltPositionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.syncdesk.app.data.ActuatorTiming
import com.syncdesk.app.data.TurnGate
import com.syncdesk.app.data.VehicleFeature
import com.syncdesk.app.data.VehicleGateway
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.data.VehicleSimulator
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinRanges
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import com.syncdesk.app.domain.applying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The vehicle through the Pleos Vehicle SDK.
 *
 * Each feature is linked on its own: the gateway asks the vehicle whether it offers the feature,
 * reads the current value, and registers for changes. A feature that links is "live" and from
 * then on is read from and written to the real vehicle. A feature the vehicle does not offer
 * (or all of them, where there is no Pleos vehicle service) stays simulated in memory, so the app
 * behaves the same on every car and says through [link] which parts are real. Some features are
 * simulated on every car ([VehicleFeature.linkable]).
 *
 * Which part is linked, with which call and area, follows what a probe app found on the Pleos
 * Connect v2.1.0 emulator (2026-11-15, `05_원격확인/SDK_확인결과.md`).
 *
 * The seat backrest, the seat fore and aft, its height and the cushion tilt are moved, not set:
 * SDK 2.3.2 offers forward, backward and stop. The gateway moves each for as long as the distance
 * takes and stops it; see [moveSeatPart].
 */
class PleosVehicleGateway(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : VehicleGateway, VehicleSimulator {

    private val vehicle = Vehicle(context.applicationContext)

    // Park until the vehicle says otherwise: nothing is lowered or written down on a guess.
    private val _signals = MutableStateFlow(VehicleSignals(gear = Gear.Park))
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()

    private val _cabin = MutableStateFlow(CabinState())
    override val cabin: StateFlow<CabinState> = _cabin.asStateFlow()

    private val _ranges = MutableStateFlow(CabinRanges())
    override val ranges: StateFlow<CabinRanges> = _ranges.asStateFlow()

    private val _link = MutableStateFlow(VehicleLink(pending = true))

    private val _failures = MutableSharedFlow<CabinPart>(extraBufferCapacity = 8)
    override val failures: Flow<CabinPart> = _failures.asSharedFlow()
    override val link: StateFlow<VehicleLink> = _link.asStateFlow()

    private val actuators = Mutex()

    /** A range of raw vehicle values, used for the features the app shows in percent. */
    private data class Span(val min: Int, val max: Int) {
        fun toPercent(level: Int): Int = levelToPercent(level, min, max)
        fun toLevel(percent: Int): Int = percentToLevel(percent, min, max)
    }

    // Set once by the link functions, before the feature is marked live.
    @Volatile private var fanArea: SeatArea? = null

    // The fan is reported as level 0 while the climate system is off: nothing is blowing, so
    // there is nothing to lower, and the vehicle refuses fan commands in that state anyway.
    @Volatile private var climateOn = true
    @Volatile private var fanSpeed = 0
    @Volatile private var windowArea: WindowArea? = null
    @Volatile private var windowSpan = Span(0, 100)
    @Volatile private var readingLightArea: SeatArea? = null
    @Volatile private var recirculationArea: SeatArea? = null
    @Volatile private var ambientArea: SeatArea? = null
    @Volatile private var ambientSpan = Span(0, 100)
    @Volatile private var ambientOn = false
    @Volatile private var ambientLevel = 0
    /** The brightness before office mode switched the light on, to put back when it goes off. */
    @Volatile private var ambientLevelWhenOff: Int? = null
    @Volatile private var wiperArea: WindowArea? = null

    @Volatile private var airDirectionArea: SeatArea? = null
    /** The directions the vehicle says it takes; empty when it does not say. */
    @Volatile private var airDirections: List<FanDirectionType> = emptyList()
    /** The air direction is polled; a poll and a change of it never overlap. */
    private val airDirectionLock = Mutex()
    /** Polls before this moment are not published: the vehicle may still report the value from before the app's change. */
    @Volatile private var airDirectionSettledAt = 0L
    @Volatile private var temperatureArea: SeatArea? = null
    @Volatile private var temperatureScale = TemperatureScale(15f, 30f, TemperatureScale.DEFAULT_STEP_C)
    /** The temperature the app last asked for, before it was put on the vehicle's step. */
    @Volatile private var temperatureAsked: Float? = null
    @Volatile private var airCleanerArea: SeatArea? = null
    @Volatile private var defrosterArea: WindowArea? = null
    @Volatile private var seatClimateArea: SeatArea? = null
    @Volatile private var seatClimateSpan = Span(0, 0)
    @Volatile private var ambientColorArea: SeatArea? = null
    /** The colour the app last asked for, with the alpha the vehicle does not keep. */
    @Volatile private var ambientColorAsked: Int? = null
    @Volatile private var doorArea: DoorArea? = null

    /**
     * One seat part that is moved forward, backward or stopped, and whose position the app keeps
     * as an estimate where the vehicle does not report it.
     */
    private class MovingSeatPart(
        val name: String,
        val feature: VehicleFeature,
        val part: CabinPart,
        /** The app's percent runs against the vehicle's position (see [SeatTravel.percent]). */
        val inverted: Boolean,
        val checkMove: (Seat, (CapabilityConfig, List<SeatArea>) -> Unit, (Exception) -> Unit) -> Unit,
        val checkPosition: (Seat, (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, (Exception) -> Unit) -> Unit,
        val position: (Seat, SeatArea, (Int?) -> Unit, (Exception) -> Unit) -> Unit,
        val move: (Seat, SeatArea, MoveControl, (SeatArea, MoveControl?) -> Unit, (Exception) -> Unit) -> Unit,
        val listen: (Seat, SeatArea, (Int?) -> Unit, (Exception) -> Unit) -> Unit,
        val read: (CabinState) -> Int,
        val write: (CabinState, Int) -> CabinState,
    ) {
        @Volatile var area: SeatArea? = null
        @Volatile var min: Int = 0
        @Volatile var max: Int = 100

        fun percent(position: Int): Int = SeatTravel.percent(position, min, max, inverted)
    }

    private val backrest = MovingSeatPart(
        name = "seat backrest",
        feature = VehicleFeature.Seat,
        part = CabinPart.Seat,
        // A larger position reclines further (the platform's SEAT_BACKREST_ANGLE_1_POS), as the app counts.
        inverted = false,
        checkMove = { s, done, failed -> s.checkMoveSeatBackrestAngleCapabilities(done, failed) },
        checkPosition = { s, done, failed -> s.checkSeatBackrestAnglePositionCapabilities(done, failed) },
        position = { s, area, done, failed -> s.getCurrentSeatBackrestAnglePosition(area, done, failed) },
        move = { s, area, control, done, failed -> s.moveSeatBackrestAngle(area, control, done, failed) },
        listen = { s, area, report, failed ->
            s.registerSeatBackrestAnglePosition(area, object : SeatBackrestAnglePositionListener {
                override fun onSeatBackrestAnglePositionUpdated(position: Int?) = report(position)
                override fun onFailed(e: Exception) = failed(e)
            })
        },
        read = { it.seatRecline },
        write = { cabin, value -> cabin.copy(seatRecline = value) },
    )

    private val seatSlide = MovingSeatPart(
        name = "seat fore-aft",
        feature = VehicleFeature.SeatSlide,
        part = CabinPart.SeatSlide,
        // The vehicle's position grows towards the front; the app counts from fully forward to fully back.
        inverted = true,
        checkMove = { s, done, failed -> s.checkMoveSeatForeAftCapabilities(done, failed) },
        checkPosition = { s, done, failed -> s.checkSeatForeAftPositionCapabilities(done, failed) },
        position = { s, area, done, failed -> s.getCurrentSeatForeAftPosition(area, done, failed) },
        move = { s, area, control, done, failed -> s.moveSeatForeAft(area, control, done, failed) },
        listen = { s, area, report, failed ->
            s.registerSeatForeAftPosition(area, object : SeatForeAftPositionListener {
                override fun onSeatForeAftPositionUpdated(position: Int?) = report(position)
                override fun onFailed(e: Exception) = failed(e)
            })
        },
        read = { it.seatSlide },
        write = { cabin, value -> cabin.copy(seatSlide = value) },
    )

    private val seatHeight = MovingSeatPart(
        name = "seat height",
        feature = VehicleFeature.SeatHeight,
        part = CabinPart.SeatHeight,
        inverted = false,
        checkMove = { s, done, failed -> s.checkMoveSeatHeightCapabilities(done, failed) },
        checkPosition = { s, done, failed -> s.checkSeatHeightPositionCapabilities(done, failed) },
        position = { s, area, done, failed -> s.getCurrentSeatHeightPosition(area, done, failed) },
        move = { s, area, control, done, failed -> s.moveSeatHeight(area, control, done, failed) },
        listen = { s, area, report, failed ->
            s.registerSeatHeightPosition(area, object : SeatHeightPositionListener {
                override fun onSeatHeightPositionUpdated(position: Int?) = report(position)
                override fun onFailed(e: Exception) = failed(e)
            })
        },
        read = { it.seatHeight },
        write = { cabin, value -> cabin.copy(seatHeight = value) },
    )

    private val cushionTilt = MovingSeatPart(
        name = "cushion tilt",
        feature = VehicleFeature.CushionTilt,
        part = CabinPart.CushionTilt,
        // A larger position raises the front edge of the cushion, as the app counts.
        inverted = false,
        checkMove = { s, done, failed -> s.checkMoveSeatTiltCapabilities(done, failed) },
        checkPosition = { s, done, failed -> s.checkSeatTiltPositionCapabilities(done, failed) },
        position = { s, area, done, failed -> s.getCurrentSeatTiltPosition(area, done, failed) },
        move = { s, area, control, done, failed -> s.moveSeatTilt(area, control, done, failed) },
        listen = { s, area, report, failed ->
            s.registerSeatTiltPosition(area, object : SeatTiltPositionListener {
                override fun onSeatTiltPositionUpdated(position: Int?) = report(position)
                override fun onFailed(e: Exception) = failed(e)
            })
        },
        read = { it.cushionTilt },
        write = { cabin, value -> cabin.copy(cushionTilt = value) },
    )

    /** Lane changes and bends are filtered out of the steering angle before it counts as a turn. */
    private val turnGate = TurnGate(clock = SystemClock::elapsedRealtime)
    private val turnLock = Any()
    private var turnCheck: Job? = null

    // Two sources can ask the driver to take over: the vehicle's assistance warning, and the
    // simulated hand-over when self-driving is switched off.
    @Volatile private var assistTakeover = false
    @Volatile private var handingOver = false
    private var handover: Job? = null

    init {
        scope.launch { connect() }
    }

    private suspend fun connect() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle service unavailable; every feature stays simulated", e)
            _link.update { it.copy(pending = false) }
            return
        }
        val links: List<Pair<VehicleFeature, suspend () -> Boolean>> = listOf(
            VehicleFeature.Gear to ::linkGear,
            VehicleFeature.TurnSignal to ::linkTurnSignal,
            VehicleFeature.Wiper to ::linkWiper,
            VehicleFeature.DriveAssist to ::linkDriveAssist,
            VehicleFeature.Steering to ::linkSteering,
            VehicleFeature.Fan to ::linkFan,
            VehicleFeature.Window to ::linkWindow,
            VehicleFeature.Recirculation to ::linkRecirculation,
            VehicleFeature.ReadingLight to ::linkReadingLight,
            VehicleFeature.Ambient to ::linkAmbient,
            VehicleFeature.Seat to { linkSeatPart(backrest) },
            VehicleFeature.AirDirection to ::linkAirDirection,
            VehicleFeature.Temperature to ::linkTemperature,
            VehicleFeature.AirConditioning to ::linkAirConditioning,
            VehicleFeature.AirCleaner to ::linkAirCleaner,
            VehicleFeature.Defroster to ::linkDefroster,
            VehicleFeature.SeatClimate to ::linkSeatClimate,
            VehicleFeature.AmbientColor to ::linkAmbientColor,
            VehicleFeature.SeatSlide to { linkSeatPart(seatSlide) },
            VehicleFeature.SeatHeight to { linkSeatPart(seatHeight) },
            VehicleFeature.CushionTilt to { linkSeatPart(cushionTilt) },
            VehicleFeature.CabinLight to ::linkCabinLight,
            VehicleFeature.DoorLock to ::linkDoorLock,
            VehicleFeature.Battery to ::linkBattery,
        )
        coroutineScope {
            links.forEach { (feature, link) ->
                launch {
                val linked = try {
                    link()
                } catch (e: Exception) {
                    // A missing permission surfaces here as a SecurityException from the SDK.
                    Log.w(TAG, "$feature: could not link", e)
                    false
                }
                if (linked) {
                    _link.update { it.copy(live = it.live + feature) }
                    Log.i(TAG, "$feature: live")
                } else {
                    Log.i(TAG, "$feature: simulated")
                }
                }
            }
        }
        _link.update { it.copy(pending = false) }
    }

    private fun isLive(feature: VehicleFeature) = feature in _link.value.live

    // region Signals the app only reads

    /**
     * SDK 2.3.2 marks its gear calls deprecated in favour of the platform GEAR_SELECTION property.
     * They are kept because they need only the Pleos permission already declared and answer on
     * the Pleos Connect v2.1.0 emulator; switching to the platform property is a later step.
     */
    @Suppress("DEPRECATION")
    private suspend fun linkGear(): Boolean {
        val gear = vehicle.getGear()
        val current = request<VehicleGear?>("gear") { done, failed ->
            gear.getCurrentGearSelectionState(done, failed)
        } ?: return false
        _signals.update { it.copy(gear = current.toGear()) }
        gear.registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                if (gear != null) _signals.update { it.copy(gear = gear.toGear()) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("gear", e)
        })
        return true
    }

    private suspend fun linkTurnSignal(): Boolean {
        val turnSignal = vehicle.getTurnSignal()
        val current = request<TurnSignalValue?>("turn signal") { done, failed ->
            turnSignal.getCurrentTurnSignalState(done, failed)
        } ?: return false
        _signals.update { it.copy(turnSignal = current.toTurnSignal()) }
        turnSignal.registerTurnSignalState(object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                if (state != null) _signals.update { it.copy(turnSignal = state.toTurnSignal()) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("turn signal", e)
        })
        return true
    }

    /**
     * The SDK reports only whether the wipers are running; the speed has to be asked for. It is
     * asked once a second while something is watching [signals], and at once when the running
     * state changes.
     */
    private suspend fun linkWiper(): Boolean {
        val wiper = vehicle.getWiper()
        val areas = request<Map<WindowArea, List<WiperSwitch>>>("wiper capabilities") { done, failed ->
            wiper.checkWiperSwitchCapabilities({ _: CapabilityConfig, areas -> done(areas) }, failed)
        }
        val area = areas?.keys?.windshieldArea()
            ?: areas?.keys?.firstOrNull()
            ?: WindowArea(WindowValue.FRONT_WINDSHIELD)
        if (!refreshWiper(area)) return false
        wiperArea = area
        wiper.registerWiperState(area, object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                scope.launch { refreshWiper(area) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("wiper", e)
        })
        scope.launch {
            while (isActive) {
                delay(WIPER_POLL_MILLIS)
                if (_signals.subscriptionCount.value > 0) refreshWiper(area)
            }
        }
        return true
    }

    /**
     * Cruise control is the driving assistance the SDK reports today. Active assistance is shown
     * but opens nothing, because the driver is still driving; a forced-deactivation warning is a
     * request to take over.
     */
    private suspend fun linkDriveAssist(): Boolean {
        val drivingMode = vehicle.getDrivingMode()
        val current = request<CruiseControl?>("cruise control") { done, failed ->
            drivingMode.getCruiseControlState(done, failed)
        } ?: return false
        publishCruise(current)
        drivingMode.registerCruiseControlState(object : CruiseControlStateListener {
            override fun onCruiseControlStateUpdated(state: CruiseControl?) {
                if (state != null) publishCruise(state)
            }

            override fun onFailed(e: Exception) = logListenerFailure("cruise control", e)
        })
        return true
    }

    private fun publishCruise(state: CruiseControl) {
        Log.i(TAG, "cruise control: $state")
        assistTakeover = state.isTakeoverWarning()
        _signals.update {
            it.copy(assistActive = state.isAssisting(), takeoverRequested = assistTakeover || handingOver)
        }
    }

    /**
     * The steering wheel angle, read only to catch a sharp turn made without the signal. Every
     * angle goes through [TurnGate]; a turn held long enough is published once the hold has passed.
     */
    private suspend fun linkSteering(): Boolean {
        val wheel = vehicle.getSteeringWheel()
        val current = request<Float?>("steering angle") { done, failed -> wheel.getCurrentSteeringWheelAngle(done, failed) }
            ?: return false
        onSteeringAngle(current)
        wheel.registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) {
                if (angle != null) onSteeringAngle(angle)
            }

            override fun onFailed(e: Exception) = logListenerFailure("steering angle", e)
        })
        return true
    }

    private fun onSteeringAngle(degrees: Float) {
        synchronized(turnLock) {
            publishTurn(turnGate.onAngle(degrees))
            turnCheck?.cancel()
            val wait = turnGate.pendingMillis() ?: return
            turnCheck = scope.launch {
                delay(wait)
                synchronized(turnLock) { publishTurn(turnGate.onTick()) }
            }
        }
    }

    private fun publishTurn(turn: TurnSignal) {
        if (_signals.value.steeringTurn != turn) Log.i(TAG, "steering turn: $turn")
        _signals.update { it.copy(steeringTurn = turn) }
    }

    private suspend fun refreshWiper(area: WindowArea): Boolean {
        val switch = request<WiperSwitch?>("wiper switch", log = false) { done, failed ->
            vehicle.getWiper().getCurrentWiperSwitch(area, done, failed)
        } ?: return false
        _signals.update { it.copy(wiper = switch.toWiperSpeed()) }
        return true
    }

    /** The battery's charge in percent, read only. Where the vehicle does not report it, the demo controls set it. */
    private suspend fun linkBattery(): Boolean {
        val battery = vehicle.getEvBattery()
        val config = request<CapabilityConfig>("battery capabilities") { done, failed ->
            battery.checkEvBatteryStateOfChargeCapabilities({ config: CapabilityConfig -> done(config) }, failed)
        } ?: return false
        if (!config.isReadable("battery")) return false
        val current = request<Int?>("battery") { done, failed -> battery.getEvBatteryStateOfCharge(done, failed) } ?: return false
        Log.i(TAG, "battery: now $current%")
        publishBattery(current)
        battery.registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                if (value != null) publishBattery(value)
            }

            override fun onFailed(e: Exception) = logListenerFailure("battery", e)
        })
        return true
    }

    private fun publishBattery(percent: Int) {
        _cabin.update { it.copy(batteryPercent = percent.coerceIn(0, 100)) }
    }

    // endregion

    // region Cabin hardware the app reads and controls

    private suspend fun linkFan(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>>("fan capabilities") { done, failed ->
            hvac.checkHvacFanSpeedCapabilities({ config: CapabilityConfig, areas -> done(config to areas) }, failed)
        } ?: return false
        val (area, bounds) = areas.driverSeat() ?: return false
        if (!config.isReadWrite("fan")) return false
        val current = request<Int?>("fan") { done, failed -> hvac.getHvacFanSpeed(area, done, failed) } ?: return false
        Log.i(TAG, "fan: ${bounds.minValue}..${bounds.maxValue}, now $current")
        fanArea = area
        fanSpeed = current
        _ranges.update { it.copy(fanMin = bounds.minValue, fanMax = bounds.maxValue) }
        linkClimatePower()
        publishFan()
        hvac.registerHvacFanSpeed(area, object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(speed: Int?) {
                if (speed != null) {
                    fanSpeed = speed
                    publishFan()
                }
            }

            override fun onFailed(e: Exception) = logListenerFailure("fan", e)
        })
        return true
    }

    /** Follows whether the climate system is on. If the vehicle cannot say, it is taken as on. */
    private suspend fun linkClimatePower() {
        val hvac = vehicle.getHVAC()
        val areas = request<List<SeatArea>>("climate power capabilities") { done, failed ->
            hvac.checkHvacPowerCapabilities({ _: CapabilityConfig, areas -> done(areas) }, failed)
        } ?: return
        val area = areas.driverArea() ?: areas.firstOrNull() ?: return
        val on = request<Boolean?>("climate power") { done, failed -> hvac.getHvacPowerState(area, done, failed) } ?: return
        Log.i(TAG, "climate power: $on")
        climateOn = on
        hvac.registerHvacPowerState(area, object : HvacPowerStateListener {
            override fun onHvacPowerStateUpdated(state: Boolean?) {
                if (state != null) {
                    climateOn = state
                    publishFan()
                }
            }

            override fun onFailed(e: Exception) = logListenerFailure("climate power", e)
        })
    }

    private fun publishFan() {
        val level = if (climateOn) fanSpeed else 0
        _cabin.update { it.copy(fanLevel = level) }
    }

    private suspend fun linkWindow(): Boolean {
        val window = vehicle.getWindow()
        val (config, areas) = request<Pair<CapabilityConfig, Map<WindowArea, AreaBounds<Int>>>>("window capabilities") { done, failed ->
            window.checkWindowPositionCapabilities({ config: CapabilityConfig, areas -> done(config to areas) }, failed)
        } ?: return false
        val (area, bounds) = areas.entries.firstOrNull { WindowValue.ROW_1_LEFT in it.key.values }?.toPair()
            ?: return false
        if (!config.isReadWrite("window")) return false
        val current = request<Int?>("window") { done, failed ->
            window.getCurrentWindowPosition(area, done, failed)
        } ?: return false
        Log.i(TAG, "window: ${bounds.minValue}..${bounds.maxValue}, now $current")
        // Negative positions are the vent tilt of some windows; fully closed is zero.
        val span = Span(maxOf(bounds.minValue, 0), bounds.maxValue)
        windowArea = area
        windowSpan = span
        _cabin.update { it.copy(windowOpenPercent = span.toPercent(current)) }
        window.registerWindowPosition(area, object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) {
                if (position != null) _cabin.update { it.copy(windowOpenPercent = span.toPercent(position)) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("window", e)
        })
        return true
    }

    /** Air recirculation: drawing air from inside the cabin keeps road noise out during a call. */
    private suspend fun linkRecirculation(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, List<SeatArea>>>("recirculation capabilities") { done, failed ->
            hvac.checkHvacRecirculationCapabilities({ config: CapabilityConfig, areas -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverArea() ?: areas.firstOrNull() ?: return false
        if (!config.isReadWrite("recirculation")) return false
        val current = request<Boolean?>("recirculation") { done, failed -> hvac.getHvacRecirculationState(area, done, failed) }
            ?: return false
        Log.i(TAG, "recirculation: now $current")
        recirculationArea = area
        _cabin.update { it.copy(recirculation = current) }
        hvac.registerHvacRecirculationState(area, object : HvacRecirculationStateListener {
            override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
                if (state != null) _cabin.update { it.copy(recirculation = state) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("recirculation", e)
        })
        return true
    }

    /**
     * Where the air blows, for the front seats together on Pleos Connect v2.1.0. SDK 2.3.2 has no
     * listener for it, so it is read every [AIR_DIRECTION_POLL_MILLIS] while something watches
     * [cabin]: often enough that a change the driver makes is seen before the next tuning moment
     * (every 3 seconds) could step over it, and cheap (one property read, a few milliseconds).
     */
    private suspend fun linkAirDirection(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, Map<SeatArea, List<FanDirectionType>>>>("air direction capabilities") { done, failed ->
            hvac.checkHvacFanDirectionCapabilities(
                { config: CapabilityConfig, areas: Map<SeatArea, List<FanDirectionType>> -> done(config to areas) },
                failed,
            )
        } ?: return false
        val (area, offered) = areas.driverSeat() ?: return false
        if (!config.isReadWrite("air direction")) return false
        val current = request<FanDirectionType?>("air direction") { done, failed -> hvac.getHvacFanDirection(area, done, failed) }
            ?: return false
        Log.i(TAG, "air direction: offers $offered, now $current")
        airDirectionArea = area
        airDirections = offered
        current.toAirDirection()?.let { direction -> _cabin.update { it.copy(airDirection = direction) } }
        scope.launch {
            while (isActive) {
                delay(AIR_DIRECTION_POLL_MILLIS)
                if (_cabin.subscriptionCount.value > 0) refreshAirDirection(area)
            }
        }
        return true
    }

    private suspend fun refreshAirDirection(area: SeatArea) {
        airDirectionLock.withLock {
            if (SystemClock.elapsedRealtime() < airDirectionSettledAt) return
            val reported = request<FanDirectionType?>("air direction", POLL_TIMEOUT_MILLIS, log = false) { done, failed ->
                vehicle.getHVAC().getHvacFanDirection(area, done, failed)
            }
            reported?.toAirDirection()?.let { direction -> _cabin.update { it.copy(airDirection = direction) } }
        }
    }

    /** The set temperature of the driver's side, in degrees Celsius on the vehicle's step. */
    private suspend fun linkTemperature(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas, steps) = request<Triple<CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray?>>(
            "temperature capabilities",
        ) { done, failed ->
            hvac.checkHvacTemperatureCapabilities(
                { config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Float>>, steps: HvacTemperatureConfigArray? ->
                    done(Triple(config, areas, steps))
                },
                failed,
            )
        } ?: return false
        val (area, bounds) = areas.driverSeat() ?: return false
        if (!config.isReadWrite("temperature")) return false
        val current = request<Float?>("temperature") { done, failed ->
            hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, done, failed)
        } ?: return false
        val scale = TemperatureScale.of(bounds.minValue, bounds.maxValue, steps?.incrementCelsius)
        Log.i(TAG, "temperature: $scale, now $current")
        temperatureArea = area
        temperatureScale = scale
        _ranges.update { it.copy(temperatureMinC = scale.minC, temperatureMaxC = scale.maxC) }
        publishTemperature(current)
        hvac.registerHvacTemperature(area, object : HvacTemperatureValueListener {
            override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) {
                if (celsius != null) publishTemperature(celsius)
            }

            override fun onFailed(e: Exception) = logListenerFailure("temperature", e)
        })
        return true
    }

    private fun publishTemperature(reported: Float) {
        val celsius = reportedOrAsked(reported, temperatureAsked, temperatureScale::stores)
        _cabin.update { it.copy(temperatureC = celsius) }
    }

    /** Whether the air conditioning is on, read only: the app never switches it. */
    private suspend fun linkAirConditioning(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, List<SeatArea>>>("air conditioning capabilities") { done, failed ->
            hvac.checkHvacAcCapabilities({ config: CapabilityConfig, areas: List<SeatArea> -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverArea() ?: areas.firstOrNull() ?: return false
        if (!config.isReadable("air conditioning")) return false
        val current = request<Boolean?>("air conditioning") { done, failed -> hvac.getHvacAcState(area, done, failed) } ?: return false
        Log.i(TAG, "air conditioning: now $current")
        _cabin.update { it.copy(acOn = current) }
        hvac.registerHvacAcState(area, object : HvacAcStateListener {
            override fun onHvacAcStateUpdated(state: Boolean?) {
                if (state != null) _cabin.update { it.copy(acOn = state) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("air conditioning", e)
        })
        return true
    }

    private suspend fun linkAirCleaner(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, List<SeatArea>>>("air cleaner capabilities") { done, failed ->
            hvac.checkHvacAirCleanerCapabilities({ config: CapabilityConfig, areas: List<SeatArea> -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverArea() ?: areas.firstOrNull() ?: return false
        if (!config.isReadWrite("air cleaner")) return false
        val current = request<Boolean?>("air cleaner") { done, failed -> hvac.getHvacAirCleanerState(area, done, failed) } ?: return false
        Log.i(TAG, "air cleaner: now $current")
        airCleanerArea = area
        _cabin.update { it.copy(airCleanerOn = current) }
        hvac.registerHvacAirCleanerState(area, object : HvacAirCleanerStateListener {
            override fun onHvacAirCleanerStateUpdated(state: Boolean?) {
                if (state != null) _cabin.update { it.copy(airCleanerOn = state) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("air cleaner", e)
        })
        return true
    }

    /** The windshield defroster; the rear window has its own area, which the app leaves alone. */
    private suspend fun linkDefroster(): Boolean {
        val hvac = vehicle.getHVAC()
        val (config, areas) = request<Pair<CapabilityConfig, List<WindowArea>>>("defroster capabilities") { done, failed ->
            hvac.checkHvacDefrosterCapabilities({ config: CapabilityConfig, areas: List<WindowArea> -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.windshieldArea() ?: return false
        if (!config.isReadWrite("defroster")) return false
        val current = request<Boolean?>("defroster") { done, failed -> hvac.getHvacDefrosterState(area, done, failed) } ?: return false
        Log.i(TAG, "defroster: now $current")
        defrosterArea = area
        _cabin.update { it.copy(defrosterOn = current) }
        hvac.registerHvacDefrosterState(area, object : HvacDefrosterStateListener {
            override fun onHvacDefrosterStateUpdated(state: Boolean?) {
                if (state != null) _cabin.update { it.copy(defrosterOn = state) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("defroster", e)
        })
        return true
    }

    /**
     * Seat ventilation and heating, which the vehicle keeps as one value (see [seatClimateLevel]).
     * The vehicle also offers `setHvacSeatVentilation`, but on Pleos Connect v2.1.0 that value
     * never reaches the seat (checked 2026-11-15), so it is not used.
     */
    private suspend fun linkSeatClimate(): Boolean {
        val seat = vehicle.getSeat()
        val (config, areas) = request<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>>("seat climate capabilities") { done, failed ->
            seat.checkSeatTemperatureCapabilities(
                { config: CapabilityConfig, areas: Map<SeatArea, AreaBounds<Int>> -> done(config to areas) },
                failed,
            )
        } ?: return false
        val (area, bounds) = areas.driverSeat() ?: return false
        if (!config.isReadWrite("seat climate")) return false
        val current = request<Int?>("seat climate") { done, failed -> seat.getHvacSeatTemperature(area, done, failed) } ?: return false
        Log.i(TAG, "seat climate: ${bounds.minValue}..${bounds.maxValue}, now $current")
        seatClimateArea = area
        seatClimateSpan = Span(bounds.minValue, bounds.maxValue)
        publishSeatClimate(current)
        seat.registerHvacSeatTemperature(area, object : HvacSeatTemperatureListener {
            override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
                if (temperature != null) publishSeatClimate(temperature)
            }

            override fun onFailed(e: Exception) = logListenerFailure("seat climate", e)
        })
        return true
    }

    private fun publishSeatClimate(level: Int) {
        val (ventilation, heating) = seatClimateOf(level)
        _cabin.update { it.copy(seatVentilation = ventilation, seatHeating = heating) }
    }

    private suspend fun linkReadingLight(): Boolean {
        val light = vehicle.getLight()
        val (config, areas) = request<Pair<CapabilityConfig, Map<SeatArea, List<LightSwitch>>>>("reading light capabilities") { done, failed ->
            light.checkReadingLightSwitchCapabilities({ config: CapabilityConfig, areas -> done(config to areas) }, failed)
        } ?: return false
        val (area, switches) = areas.driverSeat() ?: return false
        if (!config.isReadWrite("reading light")) return false
        if (!switches.switchesOnAndOff()) return false
        val current = request<LightState?>("reading light") { done, failed ->
            light.getCurrentReadingLightState(area, done, failed)
        } ?: return false
        Log.i(TAG, "reading light: $switches, now $current")
        readingLightArea = area
        _cabin.update { it.copy(readingLightOn = current == LightState.ON) }
        light.registerReadingLightState(area, object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                if (state != null) _cabin.update { it.copy(readingLightOn = state == LightState.ON) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("reading light", e)
        })
        return true
    }

    /**
     * The cabin (dome) light. It has no area. On Pleos Connect v2.1.0 switching it switches the
     * four reading lights with it (checked 2026-11-15); see [apply] for how the app keeps the
     * driver's reading light as asked.
     */
    private suspend fun linkCabinLight(): Boolean {
        val light = vehicle.getLight()
        val (config, switches) = request<Pair<CapabilityConfig, List<LightSwitch>>>("cabin light capabilities") { done, failed ->
            light.checkCabinLightSwitchCapabilities({ config: CapabilityConfig, switches: List<LightSwitch> -> done(config to switches) }, failed)
        } ?: return false
        if (!config.isReadWrite("cabin light")) return false
        if (!switches.switchesOnAndOff()) return false
        val current = request<LightState?>("cabin light") { done, failed -> light.getCurrentCabinLightState(done, failed) } ?: return false
        Log.i(TAG, "cabin light: $switches, now $current")
        _cabin.update { it.copy(cabinLightOn = current == LightState.ON) }
        light.registerCabinLightState(object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                if (state != null) _cabin.update { it.copy(cabinLightOn = state == LightState.ON) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("cabin light", e)
        })
        return true
    }

    /** Ambient light: switched on at the office brightness, off when office mode ends. */
    private suspend fun linkAmbient(): Boolean {
        val light = vehicle.getLight()
        val (config, areas) = request<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>>("ambient capabilities") { done, failed ->
            light.checkAmbientLightBrightnessCapabilities({ config: CapabilityConfig, areas -> done(config to areas) }, failed)
        } ?: return false
        val (area, bounds) = areas.driverSeat() ?: areas.entries.firstOrNull()?.toPair() ?: return false
        if (!config.isReadWrite("ambient brightness")) return false
        val level = request<Int?>("ambient brightness") { done, failed -> light.getAmbientLightBrightness(area, done, failed) }
            ?: return false
        val switch = request<LightSwitch?>("ambient switch") { done, failed -> light.getAmbientLightSwitch(area, done, failed) }
        Log.i(TAG, "ambient: ${bounds.minValue}..${bounds.maxValue}, now $level, $switch")
        ambientArea = area
        ambientSpan = Span(bounds.minValue, bounds.maxValue)
        ambientLevel = level
        ambientOn = switch == LightSwitch.ON
        publishAmbient()
        light.registerAmbientLightBrightnessListener(area, object : AmbientLightBrightnessListener {
            override fun onBrightnessUpdated(brightness: Int?) {
                if (brightness != null) {
                    ambientLevel = brightness
                    publishAmbient()
                }
            }

            override fun onFailed(e: Exception) = logListenerFailure("ambient brightness", e)
        })
        light.registerAmbientLightStateListener(area, object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                if (state != null) {
                    ambientOn = state == LightState.ON
                    publishAmbient()
                }
            }

            override fun onFailed(e: Exception) = logListenerFailure("ambient state", e)
        })
        return true
    }

    private fun publishAmbient() {
        _cabin.update { it.copy(ambientPercent = if (ambientOn) ambientSpan.toPercent(ambientLevel) else 0) }
    }

    /** The ambient light's colour, any RGB, for all seats together on Pleos Connect v2.1.0. */
    private suspend fun linkAmbientColor(): Boolean {
        val light = vehicle.getLight()
        val (config, areas) = request<Pair<CapabilityConfig, List<SeatArea>>>("ambient colour capabilities") { done, failed ->
            light.checkAmbientLightBrightnessColorCapabilities({ config: CapabilityConfig, areas: List<SeatArea> -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverArea() ?: areas.firstOrNull() ?: return false
        if (!config.isReadWrite("ambient colour")) return false
        val current = request<Int?>("ambient colour") { done, failed -> light.getAmbientLightBrightnessColor(area, done, failed) }
            ?: return false
        Log.i(TAG, "ambient colour: now #%08X".format(current))
        ambientColorArea = area
        publishAmbientColor(current)
        light.registerAmbientLightBrightnessColorListener(area, object : AmbientLightBrightnessColorListener {
            override fun onBrightnessColorUpdated(argbColor: Int?) {
                if (argbColor != null) publishAmbientColor(argbColor)
            }

            override fun onFailed(e: Exception) = logListenerFailure("ambient colour", e)
        })
        return true
    }

    private fun publishAmbientColor(reported: Int) {
        val color = reportedOrAsked(opaque(reported), ambientColorAsked, ::sameRgb)
        _cabin.update { it.copy(ambientColor = color) }
    }

    /** The door locks: the four doors are one area on Pleos Connect v2.1.0 and lock together. */
    private suspend fun linkDoorLock(): Boolean {
        val door = vehicle.getDoor()
        val (config, areas) = request<Pair<CapabilityConfig, List<DoorArea>>>("door lock capabilities") { done, failed ->
            door.checkDoorLockCapabilities({ config: CapabilityConfig, areas: List<DoorArea> -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverDoorArea() ?: return false
        if (!config.isReadWrite("door lock")) return false
        val current = request<Boolean?>("door lock") { done, failed -> door.getDoorLockState(area, done, failed) } ?: return false
        Log.i(TAG, "door lock: ${area.values.joinToString("+")}, now $current")
        doorArea = area
        _cabin.update { it.copy(doorsLocked = current) }
        door.registerDoorLockState(area, object : DoorLockStateListener {
            override fun onDoorLockStateUpdated(state: Boolean?) {
                if (state != null) _cabin.update { it.copy(doorsLocked = state) }
            }

            override fun onFailed(e: Exception) = logListenerFailure("door lock", e)
        })
        return true
    }

    /**
     * A seat part of the driver's seat that is moved, not set. The position the vehicle reports is
     * used whenever it changes; the Pleos Connect v2.1.0 emulator moves the seat but always
     * reports 0 and sends no position updates (checked 2026-11-13 for the backrest, 2026-11-15 for
     * the others), so there the app keeps its own estimate (see [moveSeatPart]).
     */
    private suspend fun linkSeatPart(part: MovingSeatPart): Boolean {
        val seat = vehicle.getSeat()
        val (config, areas) = request<Pair<CapabilityConfig, List<SeatArea>>>("${part.name} move capabilities") { done, failed ->
            part.checkMove(seat, { config, areas -> done(config to areas) }, failed)
        } ?: return false
        val area = areas.driverArea() ?: return false
        if (!config.isReadWrite("${part.name} move")) return false
        val bounds = request<Map<SeatArea, AreaBounds<Int>>>("${part.name} position capabilities") { done, failed ->
            part.checkPosition(seat, { _, areas -> done(areas) }, failed)
        }?.driverSeat()?.second
        val current = request<Int?>("${part.name} position") { done, failed -> part.position(seat, area, done, failed) } ?: 0
        Log.i(TAG, "${part.name}: ${bounds?.minValue}..${bounds?.maxValue}, now $current")
        part.area = area
        if (bounds != null) {
            part.min = bounds.minValue
            part.max = bounds.maxValue
        }
        _cabin.update { part.write(it, part.percent(current)) }
        part.listen(
            seat,
            area,
            { position ->
                // A real position change is trusted over the app's own estimate; 0 is what the
                // emulator reports whatever the seat does.
                if (position != null && position != 0 && part.percent(position) != part.read(_cabin.value)) {
                    _cabin.update { part.write(it, part.percent(position)) }
                }
            },
            { e -> logListenerFailure("${part.name} position", e) },
        )
        return true
    }

    /**
     * Carries out [change] part by part, each on the vehicle where its feature is live and in
     * memory otherwise, and returns once all of them have answered or timed out.
     *
     * The cabin light goes before the reading light: on Pleos Connect v2.1.0 switching the cabin
     * light switches the four reading lights with it. The driver's reading light is then set to
     * what [change] asks, or put back as it was when the change does not ask for it, so the app
     * never leaves on (or off) a reading light nobody asked for.
     */
    override suspend fun apply(change: CabinChange) {
        actuators.withLock {
            change.fanLevel?.let { setFan(it) }
            change.windowOpenPercent?.let { setWindow(it) }
            change.recirculation?.let { setRecirculation(it) }
            change.airDirection?.let { setAirDirection(it) }
            change.temperatureC?.let { setTemperature(it) }
            seatClimateTarget(_cabin.value, change)?.let { level ->
                val part = if (level < 0 || change.seatHeating == null) CabinPart.SeatVentilation else CabinPart.SeatHeating
                setSeatClimate(level, part)
            }
            change.airCleanerOn?.let { setAirCleaner(it) }
            change.defrosterOn?.let { setDefroster(it) }
            change.seatSlide?.let { moveSeatPart(seatSlide, it) }
            change.seatHeight?.let { moveSeatPart(seatHeight, it) }
            change.cushionTilt?.let { moveSeatPart(cushionTilt, it) }
            change.seatRecline?.let { moveSeatPart(backrest, it) }
            val readingLightBefore = _cabin.value.readingLightOn
            val cabinLightSwitched = change.cabinLightOn?.let { setCabinLight(it) } ?: false
            when {
                change.readingLightOn != null -> setReadingLight(change.readingLightOn)
                cabinLightSwitched && isLive(VehicleFeature.ReadingLight) -> setReadingLight(readingLightBefore)
            }
            change.ambientPercent?.let { setAmbient(it) }
            change.ambientColor?.let { setAmbientColor(it) }
            change.doorsLocked?.let { setDoorLock(it) }
            applySimulated(change)
        }
    }

    private suspend fun setFan(level: Int) {
        val area = fanArea?.takeIf { isLive(VehicleFeature.Fan) } ?: return
        if (!climateOn) return
        val ranges = _ranges.value
        val target = level.coerceIn(ranges.fanMin, ranges.fanMax)
        val reached = request<Int?>("set fan", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getHVAC().setHvacFanSpeed(area, target, { _, value -> done(value ?: target) }, failed)
        }
        if (reached != null) {
            fanSpeed = reached
            publishFan()
        } else {
            _failures.tryEmit(CabinPart.Fan)
        }
    }

    private suspend fun setWindow(openPercent: Int) {
        val area = windowArea?.takeIf { isLive(VehicleFeature.Window) } ?: return
        val span = windowSpan
        val target = span.toLevel(openPercent)
        val reached = request<Int?>("set window", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getWindow().setWindowPosition(area, target, { _, value -> done(value ?: target) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(windowOpenPercent = span.toPercent(reached)) } else _failures.tryEmit(CabinPart.Window)
    }

    private suspend fun setRecirculation(on: Boolean) {
        val area = recirculationArea?.takeIf { isLive(VehicleFeature.Recirculation) } ?: return
        val reached = request<Boolean?>("set recirculation", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getHVAC().setHvacRecirculation(area, on, { _, value -> done(value ?: on) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(recirculation = reached) } else _failures.tryEmit(CabinPart.Recirculation)
    }

    private suspend fun setAirDirection(direction: AirDirection) {
        val area = airDirectionArea?.takeIf { isLive(VehicleFeature.AirDirection) } ?: return
        val target = direction.toFanDirection()?.takeIf { airDirections.offers(it) } ?: run {
            Log.w(TAG, "set air direction: $direction is not sent to this vehicle")
            _failures.tryEmit(CabinPart.AirDirection)
            return
        }
        airDirectionLock.withLock {
            val reached = request<FanDirectionType?>("set air direction", SET_TIMEOUT_MILLIS) { done, failed ->
                vehicle.getHVAC().setHvacFanDirection(area, target, { _, value -> done(value ?: target) }, failed)
            }
            airDirectionSettledAt = SystemClock.elapsedRealtime() + AIR_DIRECTION_SETTLE_MILLIS
            val published = reached?.toAirDirection()
            if (published != null) _cabin.update { it.copy(airDirection = published) } else _failures.tryEmit(CabinPart.AirDirection)
        }
    }

    /** Sends [celsius] on the vehicle's step and inside its range; outside it the vehicle refuses. */
    private suspend fun setTemperature(celsius: Float) {
        val area = temperatureArea?.takeIf { isLive(VehicleFeature.Temperature) } ?: return
        val target = temperatureScale.snap(celsius)
        temperatureAsked = celsius
        val reached = request<Float?>("set temperature", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getHVAC().setHvacTemperature(area, target, TemperatureUnit.CELSIUS, { _, value -> done(value ?: target) }, failed)
        }
        if (reached != null) publishTemperature(reached) else _failures.tryEmit(CabinPart.Temperature)
    }

    /**
     * Sends the one vehicle value for ventilation and heating. A level the seat does not offer
     * (ventilation on a seat that only heats) is not sent and is reported as refused, as [part].
     */
    private suspend fun setSeatClimate(level: Int, part: CabinPart) {
        val area = seatClimateArea?.takeIf { isLive(VehicleFeature.SeatClimate) } ?: return
        val span = seatClimateSpan
        if (level < span.min || level > span.max) {
            Log.w(TAG, "set seat climate: $level is outside ${span.min}..${span.max}")
            _failures.tryEmit(part)
            return
        }
        val reached = request<Int?>("set seat climate", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getSeat().setHvacSeatTemperature(area, level, { _, value -> done(value ?: level) }, failed)
        }
        if (reached != null) publishSeatClimate(reached) else _failures.tryEmit(part)
    }

    private suspend fun setAirCleaner(on: Boolean) {
        val area = airCleanerArea?.takeIf { isLive(VehicleFeature.AirCleaner) } ?: return
        val reached = request<Boolean?>("set air cleaner", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getHVAC().setHvacAirCleaner(area, on, { _, value -> done(value ?: on) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(airCleanerOn = reached) } else _failures.tryEmit(CabinPart.AirCleaner)
    }

    private suspend fun setDefroster(on: Boolean) {
        val area = defrosterArea?.takeIf { isLive(VehicleFeature.Defroster) } ?: return
        val reached = request<Boolean?>("set defroster", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getHVAC().setHvacDefroster(area, on, { _, value -> done(value ?: on) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(defrosterOn = reached) } else _failures.tryEmit(CabinPart.Defroster)
    }

    /**
     * Moves [part] to [target] percent: forward or backward for the time the distance takes at
     * the measured speed ([SeatTravel]), then stop. Stop is always sent, even when the move is cut
     * short (the car leaves Park, or the driver stops office mode mid-move), and the position is
     * then estimated from the time the part was moving.
     */
    private suspend fun moveSeatPart(part: MovingSeatPart, target: Int) {
        val area = part.area?.takeIf { isLive(part.feature) } ?: return
        val from = part.read(_cabin.value)
        val goal = target.coerceIn(0, 100)
        val direction = SeatTravel.direction(from, goal, part.inverted) ?: return
        val seat = vehicle.getSeat()
        val started = request<MoveControl?>("move ${part.name}", SET_TIMEOUT_MILLIS) { done, failed ->
            part.move(seat, area, direction, { _, value -> done(value ?: direction) }, failed)
        } ?: run {
            _failures.tryEmit(part.part)
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            delay(SeatTravel.millis(from, goal))
        } finally {
            withContext(NonCancellable) {
                request<MoveControl?>("stop ${part.name}", SET_TIMEOUT_MILLIS) { done, failed ->
                    part.move(seat, area, MoveControl.STOP, { _, value -> done(value ?: MoveControl.STOP) }, failed)
                }
                val reached = SeatTravel.reached(from, goal, SystemClock.elapsedRealtime() - startedAt)
                Log.i(TAG, "${part.name}: moved $started from $from to about $reached")
                _cabin.update { part.write(it, reached) }
            }
        }
    }

    /**
     * Switches the ambient light and sets its brightness. Switching it off also puts the brightness
     * back where it was before it was switched on, so the light comes back as the driver left it.
     *
     * On the Pleos Connect v2.1.0 emulator only the brightness reaches the vehicle console; the
     * switch is accepted and read back by the SDK but never reaches the console, and the console's
     * own switch never reaches the SDK (checked 2026-11-13).
     */
    private suspend fun setAmbient(percent: Int) {
        val area = ambientArea?.takeIf { isLive(VehicleFeature.Ambient) } ?: return
        val light = vehicle.getLight()
        val on = percent > 0
        if (on && !ambientOn) ambientLevelWhenOff = ambientLevel
        val switch = if (on) LightSwitch.ON else LightSwitch.OFF
        val switched = request<LightSwitch?>("set ambient switch", SET_TIMEOUT_MILLIS) { done, failed ->
            light.setAmbientLightSwitch(area, switch, { _, value -> done(value ?: switch) }, failed)
        }
        if (switched != null) ambientOn = switched == LightSwitch.ON else _failures.tryEmit(CabinPart.Ambient)
        val level = if (on) ambientSpan.toLevel(percent) else ambientLevelWhenOff
        if (level != null && level != ambientLevel) {
            val reached = request<Int?>("set ambient brightness", SET_TIMEOUT_MILLIS) { done, failed ->
                light.setAmbientLightBrightness(area, level, { _, value -> done(value) }, failed)
            }
            if (reached != null) ambientLevel = reached
        }
        if (!on) ambientLevelWhenOff = null
        publishAmbient()
    }

    /** Sets the ambient colour; the vehicle keeps its red, green and blue (see [publishAmbientColor]). */
    private suspend fun setAmbientColor(argb: Int) {
        val area = ambientColorArea?.takeIf { isLive(VehicleFeature.AmbientColor) } ?: return
        val color = opaque(argb)
        ambientColorAsked = argb
        val reached = request<Int?>("set ambient colour", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getLight().setAmbientLightBrightnessColor(area, color, { _, value -> done(value) }, failed)
        }
        if (reached != null) publishAmbientColor(reached) else _failures.tryEmit(CabinPart.AmbientColor)
    }

    private suspend fun setReadingLight(on: Boolean) {
        val area = readingLightArea?.takeIf { isLive(VehicleFeature.ReadingLight) } ?: return
        val target = if (on) LightSwitch.ON else LightSwitch.OFF
        val reached = request<LightSwitch?>("set reading light", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getLight().setReadingLightSwitch(area, target, { value -> done(value ?: target) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(readingLightOn = reached == LightSwitch.ON) } else _failures.tryEmit(CabinPart.ReadingLight)
    }

    /**
     * Switches the cabin light and returns whether the vehicle did. The reading lights it switches
     * with it settle first ([LIGHT_SETTLE_MILLIS]), so the reading light set after it is not undone.
     */
    private suspend fun setCabinLight(on: Boolean): Boolean {
        if (!isLive(VehicleFeature.CabinLight)) return false
        val target = if (on) LightSwitch.ON else LightSwitch.OFF
        val reached = request<LightSwitch?>("set cabin light", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getLight().setCabinLightSwitch(target, { value -> done(value ?: target) }, failed)
        }
        if (reached == null) {
            _failures.tryEmit(CabinPart.CabinLight)
            return false
        }
        _cabin.update { it.copy(cabinLightOn = reached == LightSwitch.ON) }
        delay(LIGHT_SETTLE_MILLIS)
        return true
    }

    private suspend fun setDoorLock(locked: Boolean) {
        val area = doorArea?.takeIf { isLive(VehicleFeature.DoorLock) } ?: return
        val reached = request<Boolean?>("set door lock", SET_TIMEOUT_MILLIS) { done, failed ->
            vehicle.getDoor().setDoorLock(area, locked, { _, value -> done(value ?: locked) }, failed)
        }
        if (reached != null) _cabin.update { it.copy(doorsLocked = reached) } else _failures.tryEmit(CabinPart.DoorLock)
    }

    /** Carries out, in memory, the parts of [change] that no live feature covers. */
    private suspend fun applySimulated(change: CabinChange) {
        val simulated = CabinChange(
            fanLevel = change.fanLevel.unless(VehicleFeature.Fan),
            windowOpenPercent = change.windowOpenPercent.unless(VehicleFeature.Window),
            recirculation = change.recirculation.unless(VehicleFeature.Recirculation),
            seatRecline = change.seatRecline.unless(VehicleFeature.Seat),
            readingLightOn = change.readingLightOn.unless(VehicleFeature.ReadingLight),
            ambientPercent = change.ambientPercent.unless(VehicleFeature.Ambient),
            airDirection = change.airDirection.unless(VehicleFeature.AirDirection),
            temperatureC = change.temperatureC.unless(VehicleFeature.Temperature),
            seatVentilation = change.seatVentilation.unless(VehicleFeature.SeatClimate),
            seatHeating = change.seatHeating.unless(VehicleFeature.SeatClimate),
            airCleanerOn = change.airCleanerOn.unless(VehicleFeature.AirCleaner),
            defrosterOn = change.defrosterOn.unless(VehicleFeature.Defroster),
            ambientColor = change.ambientColor.unless(VehicleFeature.AmbientColor),
            seatSlide = change.seatSlide.unless(VehicleFeature.SeatSlide),
            seatHeight = change.seatHeight.unless(VehicleFeature.SeatHeight),
            cushionTilt = change.cushionTilt.unless(VehicleFeature.CushionTilt),
            cabinLightOn = change.cabinLightOn.unless(VehicleFeature.CabinLight),
            doorsLocked = change.doorsLocked.unless(VehicleFeature.DoorLock),
            // Simulated on every car (VehicleFeature.linkable is false): the call volume is fixed
            // for a regular app, the cushion length, the lumbar support and the sunshades answer
            // without moving on Pleos Connect v2.1.0 (and smart glass is not there at all), and
            // SDK 2.3.2 has no call for the steering wheel's position.
            callVolume = change.callVolume,
            cushionLength = change.cushionLength,
            lumbar = change.lumbar,
            shadePercent = change.shadePercent,
            wheel = change.wheel,
        )
        delay(ActuatorTiming.travelMillis(from = _cabin.value, change = simulated))
        _cabin.update { it.applying(simulated) }
    }

    private fun <T> T?.unless(feature: VehicleFeature): T? = if (isLive(feature)) null else this

    // endregion

    // region Demo controls for the signals the vehicle does not provide

    override fun setGear(gear: Gear) {
        if (!isLive(VehicleFeature.Gear)) _signals.update { it.copy(gear = gear) }
    }

    override fun setWiper(speed: WiperSpeed) {
        if (!isLive(VehicleFeature.Wiper)) _signals.update { it.copy(wiper = speed) }
    }

    override fun setTurnSignal(signal: TurnSignal) {
        if (!isLive(VehicleFeature.TurnSignal)) _signals.update { it.copy(turnSignal = signal) }
    }

    /**
     * Self-driving is always simulated: no SDK signal reports it yet. Switching it off plays the
     * hand-over: the car asks the driver to take the wheel for a few seconds.
     */
    override fun setAutonomous(autonomous: Boolean) {
        handover?.cancel()
        val wasAutonomous = _signals.value.autonomous
        handingOver = false
        _signals.update { it.copy(autonomous = autonomous, takeoverRequested = assistTakeover) }
        if (wasAutonomous && !autonomous) {
            handover = scope.launch {
                handingOver = true
                _signals.update { it.copy(takeoverRequested = true) }
                delay(HANDOVER_MILLIS)
                handingOver = false
                _signals.update { it.copy(takeoverRequested = assistTakeover) }
            }
        }
    }

    /** The demo controls set the battery's charge only where the vehicle does not report it. */
    override fun setBatteryPercent(percent: Int) {
        if (!isLive(VehicleFeature.Battery)) publishBattery(percent)
    }

    // endregion

    /**
     * Runs one SDK call and waits for its callback. Returns null when the vehicle reports a
     * failure, answers with nothing, throws (a missing permission), or does not answer in time.
     */
    private suspend fun <T> request(
        name: String,
        timeoutMillis: Long = LINK_TIMEOUT_MILLIS,
        log: Boolean = true,
        call: (done: (T) -> Unit, failed: (Exception) -> Unit) -> Unit,
    ): T? {
        val result = CompletableDeferred<T?>()
        try {
            call(
                { value -> result.complete(value) },
                { e ->
                    if (log) Log.w(TAG, "$name: ${vehicleFailureOf(e.message)}: ${e.message}")
                    result.complete(null)
                },
            )
        } catch (e: Exception) {
            if (log) Log.w(TAG, "$name: ${vehicleFailureOf(e.message)}: ${e.javaClass.simpleName} ${e.message}")
            return null
        }
        val value = withTimeoutOrNull(timeoutMillis) { result.await() }
        if (value == null && log && !result.isCompleted) Log.w(TAG, "$name: no answer in $timeoutMillis ms")
        return value
    }

    private fun CapabilityConfig.isReadWrite(name: String): Boolean {
        val readWrite = canReadWrite()
        if (!readWrite) Log.i(TAG, "$name: access is $accessType, not read and write")
        return readWrite
    }

    private fun CapabilityConfig.isReadable(name: String): Boolean {
        val readable = canRead()
        if (!readable) Log.i(TAG, "$name: access is $accessType, not readable")
        return readable
    }

    private fun logListenerFailure(name: String, e: Exception) {
        Log.w(TAG, "$name listener: ${e.message}")
    }

    private companion object {
        const val TAG = "SyncDeskVehicle"

        /** Linking waits for the vehicle service to bind, which can take several seconds. */
        const val LINK_TIMEOUT_MILLIS = 15_000L
        const val SET_TIMEOUT_MILLIS = 3_000L
        const val POLL_TIMEOUT_MILLIS = 1_000L
        const val WIPER_POLL_MILLIS = 1_000L
        const val HANDOVER_MILLIS = 5_000L

        /** How often the air direction is read; see [linkAirDirection]. */
        const val AIR_DIRECTION_POLL_MILLIS = 2_000L

        /** How long a read of the air direction may still return the value from before a change. */
        const val AIR_DIRECTION_SETTLE_MILLIS = 1_000L

        /** How long the reading lights take to follow the cabin light. */
        const val LIGHT_SETTLE_MILLIS = 500L
    }
}
