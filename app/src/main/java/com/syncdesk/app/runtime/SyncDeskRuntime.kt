package com.syncdesk.app.runtime

import com.syncdesk.app.data.AppSettings
import com.syncdesk.app.data.CallMemoStore
import com.syncdesk.app.data.CallMonitor
import com.syncdesk.app.data.ChimePlayer
import com.syncdesk.app.data.ContactSource
import com.syncdesk.app.data.Cue
import com.syncdesk.app.data.DriverReplySource
import com.syncdesk.app.data.HearingSource
import com.syncdesk.app.data.MeetingSource
import com.syncdesk.app.data.MemoAnswerer
import com.syncdesk.app.data.Narrator
import com.syncdesk.app.data.PresetStore
import com.syncdesk.app.data.QuietStore
import com.syncdesk.app.data.RuleMemoAnswerer
import com.syncdesk.app.data.SavedTrip
import com.syncdesk.app.data.SettingsStore
import com.syncdesk.app.data.UsbPower
import com.syncdesk.app.data.VehicleGateway
import com.syncdesk.app.data.VehicleFeature
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.CaptureState
import com.syncdesk.app.domain.Contact
import com.syncdesk.app.domain.DialogStep
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.MeetingInfo
import com.syncdesk.app.domain.OfficeState
import com.syncdesk.app.domain.PostCallDialog
import com.syncdesk.app.domain.RoadDemand
import com.syncdesk.app.domain.SyncDeskEngine
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.UsbOrder
import com.syncdesk.app.domain.UsbPort
import com.syncdesk.app.domain.UxLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Connects the vehicle, the call and the meeting to [SyncDeskEngine], and carries out what the
 * engine decides: sending its commands to the vehicle, running the office sequence one actuator
 * at a time, passing on every sentence of a call for its memo, giving the engine its tuning
 * moments and timers during a call, and holding the talk after a call (speaking, listening,
 * giving up on a reply, finding answers).
 *
 * There is one per process and it does not belong to a screen: the cabin is tuned and the call
 * written down just the same while another app, such as navigation, covers this one.
 */
class SyncDeskRuntime(
    private val gateway: VehicleGateway,
    private val callMonitor: CallMonitor,
    private val meeting: MeetingSource,
    private val narrator: Narrator,
    private val presetStore: PresetStore,
    /** How long the car waits for the driver's reply in the talk after a call before it ends the talk. */
    private val replyTimeoutMillis: Long = 8_000L,
    /** What the car says in the talk after a call, apart from the memo itself. */
    private val phrases: DialogPhrases = DialogPhrases(),
    /** Finds the answer to the driver's question in the call's memo. */
    private val answerer: MemoAnswerer = RuleMemoAnswerer(),
    /** Keeps the call memos between runs; by default they last as long as this object. */
    private val memoStore: CallMemoStore = object : CallMemoStore {
        override fun load() = emptyList<CallMemo>()
        override fun save(memos: List<CallMemo>) = Unit
    },
    /** Keeps what puts the cabin back while it is tuned for a call; by default only in memory. */
    private val quietStore: QuietStore = object : QuietStore {
        override fun load(): CabinChange? = null
        override fun save(restore: CabinChange?) = Unit
    },
    /** How well the other party is heard; by default nothing measures it and the cabin is not adjusted. */
    private val hearing: HearingSource = object : HearingSource {
        override fun levels() = emptyFlow<HearingLevels>()
    },
    /** How often, during a call, the cabin may change one thing by one step. */
    private val tuneIntervalMillis: Long = 3_000L,
    /** How long the air is drawn from inside before outside air is let in for [freshAirMillis]. */
    private val freshAirAfterMillis: Long = 600_000L,
    /** How long outside air is let in before the air is drawn from inside again. */
    private val freshAirMillis: Long = 60_000L,
    /** How long the air is drawn from inside before running wipers call for the defroster. */
    private val defrostAfterMillis: Long = 300_000L,
    /** How long to wait for the vehicle's fan and window to be linked before restoring anyway. */
    private val vehicleLinkTimeoutMillis: Long = 10_000L,
    /** The platform's driving restrictions; none by default. */
    private val uxLimits: StateFlow<UxLimits> = MutableStateFlow(UxLimits()),
    /** How long after a turn what is said still counts as missed: a sentence under way was not heard. */
    private val demandHoldMillis: Long = 4_000L,
    /** Keeps the first-use agreement and the sound setting; by default agreed, in memory only. */
    private val settingsStore: SettingsStore = object : SettingsStore {
        override fun load() = AppSettings(consented = true)
        override fun save(settings: AppSettings) = Unit
    },
    /** Whether speech recognition can be used on this device. */
    private val speechAvailable: suspend () -> Boolean = { true },
    /** How long a refused vehicle command is shown. */
    private val refusalMillis: Long = 6_000L,
    /** Marks changes of state with a short sound; silent by default. */
    private val chimes: ChimePlayer = object : ChimePlayer {
        override suspend fun play(cue: Cue) = Unit
    },
    /** What the driver says back; heard only while [SyncDeskState.listeningForReply]. */
    private val replies: DriverReplySource = object : DriverReplySource {
        override fun replies() = emptyFlow<DriverReply>()
    },
    /** The day and time a call's memo is dated with. */
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    /** What the driver's phone holds that they have not looked at; by default nothing. */
    private val contacts: ContactSource = object : ContactSource {
        override val unseen: StateFlow<List<Contact>> = MutableStateFlow(emptyList())
    },
    /** The USB-C port a laptop is charged from; by default a port that takes no orders. */
    private val usbPower: UsbPower = object : UsbPower {
        override val port: StateFlow<UsbPort> = MutableStateFlow(UsbPort())
        override suspend fun order(order: UsbOrder?) = Unit
    },
    /** Everything here runs on the main thread, for as long as this scope lives. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val savedMemos = memoStore.load().takeLast(SyncDeskEngine.MAX_MEMOS)

    /** Which drive the last run was in, and whether the car was parked then. */
    private val savedTrip: SavedTrip? = memoStore.loadTrip()

    /**
     * True while a run that started in the middle of a drive does not know the gear yet. The
     * vehicle reports Park until it is linked, so the first real "Drive" would otherwise count as
     * leaving Park and start a new drive, moving this drive's calls to the earlier ones.
     */
    private val resumingDrive = MutableStateFlow(savedTrip?.parked == false)

    private val _state = MutableStateFlow(
        SyncDeskState(
            signals = gateway.signals.value,
            cabin = gateway.cabin.value,
            preset = presetStore.load(),
            memos = savedMemos,
            consented = settingsStore.load().consented,
            chimesEnabled = settingsStore.load().chimesEnabled,
            meeting = MeetingInfo(meeting.title, meeting.materials),
            // The drive the last run was in; a run that started mid-drive carries on with it, and
            // one that started parked moves on to the next drive when the car leaves Park.
            // Saved by a version that did not keep the drive: what was saved is an earlier drive's.
            trip = savedTrip?.trip ?: savedMemos.maxOfOrNull { it.trip }?.plus(1) ?: 0,
            usb = usbPower.port.value,
        ),
    )
    val state: StateFlow<SyncDeskState> = _state.asStateFlow()

    private val _parkedWithDigest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits each time the car is put in Park with something of the drive to show: a call memo, or
     * contacts the driver has not looked at. Whoever can bring the app's screen forward listens.
     */
    val parkedWithDigest: SharedFlow<Unit> = _parkedWithDigest.asSharedFlow()

    /** Which vehicle features are real and which are simulated. Shown by the demo controls. */
    val link: StateFlow<VehicleLink> = gateway.link

    /** Changes the engine has asked for, in order. Sent to the vehicle one at a time. */
    private val commands = Channel<CabinChange>(Channel.UNLIMITED)

    /** Sounds due, in order; played one at a time once the driver is not talking. */
    private val cues = Channel<Cue>(Channel.UNLIMITED)


    init {
        scope.launch {
            gateway.signals.collect { onEvent(SyncDeskEvent.SignalsChanged(it)) }
        }
        scope.launch {
            gateway.cabin.collect { onEvent(SyncDeskEvent.CabinReported(it)) }
        }
        scope.launch {
            callMonitor.call.collect { call ->
                val now = clock()
                onEvent(
                    SyncDeskEvent.CallChanged(
                        active = call.active,
                        driverSpeaking = call.driverSpeaking,
                        date = now.toLocalDate().toString(),
                        time = now.format(TIME_FORMAT),
                    ),
                )
            }
        }
        scope.launch {
            gateway.ranges.collect { onEvent(SyncDeskEvent.RangesReported(it)) }
        }
        scope.launch {
            uxLimits.collect { onEvent(SyncDeskEvent.UxLimitsChanged(it)) }
        }
        scope.launch { sendCommands() }
        scope.launch { savePresetChanges() }
        scope.launch { saveMemoChanges() }
        scope.launch { saveTripChanges() }
        scope.launch { recoverAndRememberQuiet() }
        scope.launch { runOfficeSequence() }
        scope.launch { collectSentencesDuringCall() }
        scope.launch { meeting.meetings().collect { onEvent(SyncDeskEvent.MeetingStarted(it)) } }
        scope.launch { countCallTime() }
        scope.launch { speakInDialog() }
        scope.launch { listenForReplies() }
        scope.launch { endDialogWithoutReply() }
        scope.launch { measureHearingDuringCall() }
        scope.launch { tuneDuringCall() }
        scope.launch { airTheCabin() }
        scope.launch { releaseDemandAfterHold() }
        scope.launch { gateway.failures.collect { onEvent(SyncDeskEvent.CommandRefused(it)) } }
        scope.launch { gateway.link.collect { onEvent(SyncDeskEvent.LinkChanged(it.pending)) } }
        scope.launch { onEvent(SyncDeskEvent.SpeechChecked(speechAvailable())) }
        scope.launch { clearRefusalAfterShown() }
        scope.launch { saveSettingsChanges() }
        scope.launch { playCues() }
        scope.launch { contacts.unseen.collect { onEvent(SyncDeskEvent.ContactsReported(it)) } }
        scope.launch { usbPower.port.collect { onEvent(SyncDeskEvent.UsbReported(it)) } }
        scope.launch { orderUsbPower() }
    }

    /** Stops everything this object runs. Nothing can be sent to it afterwards. */
    fun close() = scope.cancel()

    /**
     * Must be called on the main thread, as everything in this class is. That makes reading the
     * state, reducing it and queueing the command one step, so commands keep their order.
     */
    fun onEvent(event: SyncDeskEvent) {
        val before = _state.value
        val after = SyncDeskEngine.reduce(before, event).let { if (resumingDrive.value) carryOnResumedDrive(before, it) else it }
        _state.value = after
        val command = after.command
        if (command != null && command.id != before.command?.id) commands.trySend(command.change)
        cuesBetween(before, after).forEach { cues.trySend(it) }
        if (parkedWithDigest(before, after)) _parkedWithDigest.tryEmit(Unit)
    }

    /**
     * Plays each sound in turn. A sound due while the driver talks waits until they stop, so it
     * never covers their words; with sounds turned off it is dropped.
     */
    private suspend fun playCues() {
        for (cue in cues) {
            state.first { !it.call.driverSpeaking }
            if (state.value.chimesEnabled) chimes.play(cue)
        }
    }

    /**
     * Carries out the engine's commands in the order they were issued. A real vehicle answers one
     * actuator at a time, so a later command (restore) must wait for an earlier one (lower).
     */
    private suspend fun sendCommands() {
        for (change in commands) gateway.apply(change)
    }

    /** The driver's office arrangement is kept for the next run as soon as they change it. */
    private suspend fun savePresetChanges() {
        state.map { it.preset }.distinctUntilChanged().drop(1).collect { presetStore.save(it) }
    }

    /**
     * If the app was stopped while the cabin was tuned for a call, the cabin is put back once the
     * vehicle is linked for every part that has to go back (commands sent before that would not
     * reach it), or once linking is over. From then on, what puts the cabin back is written down
     * whenever it changes and cleared once the cabin is restored.
     */
    private suspend fun recoverAndRememberQuiet() {
        quietStore.load()?.let { restore ->
            withTimeoutOrNull(vehicleLinkTimeoutMillis) {
                gateway.link.first { it.readyFor(restore) }
            }
            onEvent(SyncDeskEvent.QuietRecovered(restore))
        }
        state.map { s -> s.tuning?.restore?.takeUnless { it.isEmpty } }.distinctUntilChanged().collect { quietStore.save(it) }
    }

    /** A memo is kept for the next run with every sentence added to it, so a call cut short is not lost. */
    private suspend fun saveMemoChanges() {
        state.map { it.memos }.distinctUntilChanged().drop(1).collect { memoStore.save(it) }
    }

    /**
     * The drive and whether the car is parked are kept for the next run whenever they change, but
     * not while a resumed drive still waits to learn the gear: that Park is only the vehicle's
     * placeholder.
     */
    private suspend fun saveTripChanges() {
        combine(state, resumingDrive) { s, resuming -> SavedTrip(s.trip, s.signals.isParked).takeUnless { resuming } }
            .distinctUntilChanged()
            .collect { trip -> if (trip != null) memoStore.saveTrip(trip) }
    }

    /**
     * While a run that started mid-drive waits for the real gear, leaving the placeholder Park is
     * not a new drive: [after]'s drive count (and the contacts it set aside) is undone. The wait
     * ends once the car is seen moving, or the gear is known (linked, or simulated).
     */
    private fun carryOnResumedDrive(before: SyncDeskState, after: SyncDeskState): SyncDeskState {
        val kept = if (after.trip != before.trip) after.copy(trip = before.trip, staleContacts = before.staleContacts) else after
        val link = gateway.link.value
        if (!kept.signals.isParked || VehicleFeature.Gear in link.live || !link.pending) resumingDrive.value = false
        return kept
    }

    /**
     * Carries out the one step the engine says is due ([OfficeState.move]) and reports it done,
     * which makes the next one due: one actuator group at a time, each waiting for the one before.
     * [collectLatest] cancels the step under way the moment it is no longer due: the driver
     * stopped the sequence, or the car left Park. A real seat is stopped where it is by that
     * (see the gateway); when the sequence goes on, the step starts again from there.
     */
    private suspend fun runOfficeSequence() {
        state.map { it.office.move }.distinctUntilChanged().collectLatest { move ->
            when {
                move == null -> Unit
                move.undo -> {
                    gateway.apply(SyncDeskEngine.officeRestoreChange(move.step, state.value))
                    onEvent(SyncDeskEvent.OfficeStepUndone(move.step))
                }
                else -> {
                    gateway.apply(SyncDeskEngine.officeChange(move.step, state.value))
                    onEvent(SyncDeskEvent.OfficeStepDone(move.step))
                }
            }
        }
    }

    /**
     * Passes on what office mode wants of the USB-C port whenever that changes: switched to
     * charging when its step is reached, held back or switched off as the vehicle battery runs
     * low, and given back to the vehicle when office mode ends.
     */
    private suspend fun orderUsbPower() {
        state.map { it.usbOrder }.distinctUntilChanged().drop(1).collect { usbPower.order(it) }
    }

    /**
     * For as long as a call lasts, every sentence of it goes to the engine for the call's memo:
     * what the others say and what the driver promises.
     */
    private suspend fun collectSentencesDuringCall() {
        state.map { it.call.active }.distinctUntilChanged().collectLatest { active ->
            if (active) {
                merge(meeting.transcript(), meeting.promises()).collect { onEvent(SyncDeskEvent.NoteCaptured(it)) }
            }
        }
    }

    private suspend fun countCallTime() {
        state.map { it.call.active }.distinctUntilChanged().collectLatest { active ->
            while (active) {
                delay(1_000L)
                onEvent(SyncDeskEvent.CallTick)
            }
        }
    }

    /**
     * Says what the current step of the talk after a call opens with, but only on a clear road.
     * If the road gets demanding mid-sentence, [collectLatest] stops the voice, and the step is
     * said again from the start once it clears.
     */
    private suspend fun speakInDialog() {
        state
            .map { s -> s.dialog?.takeIf { !it.said && s.roadDemand == RoadDemand.Clear } }
            .distinctUntilChanged()
            .collectLatest { dialog ->
                if (dialog != null) {
                    narrator.speak(linesFor(dialog))
                    onEvent(SyncDeskEvent.DialogSpoken)
                }
            }
    }

    private suspend fun linesFor(dialog: PostCallDialog): List<String> {
        val memo = state.value.memos.firstOrNull { it.id == dialog.memoId }
        return when (dialog.step) {
            DialogStep.Offer -> listOf(phrases.offer)
            DialogStep.Explain -> memo?.let { phrases.explain(it) } ?: listOf(phrases.noKeyPoints)
            DialogStep.Ask -> listOf(phrases.ask)
            DialogStep.Answer -> listOf(memo?.let { answerer.answer(it, dialog.question) } ?: phrases.notMentioned)
        }
    }

    /**
     * Listens only once the car has finished speaking ([SyncDeskState.listeningForReply]), so its
     * own voice is never taken for a reply; [collectLatest] stops listening the moment that ends,
     * for example when the road gets demanding.
     */
    private suspend fun listenForReplies() {
        state.map { it.listeningForReply }.distinctUntilChanged().collectLatest { listening ->
            if (listening) replies.replies().collect { onEvent(SyncDeskEvent.DriverReplied(it)) }
        }
    }

    /** A reply that does not come within [replyTimeoutMillis] ends the talk; the memo stays. */
    private suspend fun endDialogWithoutReply() {
        state.map { it.listeningForReply }.distinctUntilChanged().collectLatest { listening ->
            if (listening) {
                delay(replyTimeoutMillis)
                onEvent(SyncDeskEvent.DialogReplyTimedOut)
            }
        }
    }

    /** For as long as a call lasts, the engine is told how well the other party is heard. */
    private suspend fun measureHearingDuringCall() {
        state.map { it.call.active }.distinctUntilChanged().collectLatest { active ->
            if (active) hearing.levels().collect { onEvent(SyncDeskEvent.HearingChanged(it)) }
        }
    }

    /**
     * For as long as a call lasts, the engine gets a tuning moment every [tuneIntervalMillis]. A
     * tuned cabin changes one thing by one step at each, so it moves slowly; and how long the call
     * has been hardly heard is counted in them, tuned or not. The count of moments starts again
     * when tuning starts.
     */
    private suspend fun tuneDuringCall() {
        state.map { it.isTuning to it.call.active }.distinctUntilChanged().collectLatest { (tuning, active) ->
            while (tuning || active) {
                delay(tuneIntervalMillis)
                onEvent(SyncDeskEvent.TuneTick)
            }
        }
    }

    /**
     * Times how long the app has kept the air inside. After [defrostAfterMillis] the engine is
     * told that the glass may fog up, after [freshAirAfterMillis] that outside air is due, and
     * [freshAirMillis] later that it has been let in long enough. The count starts over whenever
     * the air goes back to being drawn from inside ([collectLatest]).
     */
    private suspend fun airTheCabin() {
        state
            .map { s -> s.holdsAirInside to (s.tuning?.airing == true) }
            .distinctUntilChanged()
            .collectLatest { (inside, airing) ->
                when {
                    inside -> {
                        delay(defrostAfterMillis)
                        onEvent(SyncDeskEvent.RecirculationLong)
                        delay(freshAirAfterMillis - defrostAfterMillis)
                        onEvent(SyncDeskEvent.FreshAirDue)
                    }
                    airing -> {
                        delay(freshAirMillis)
                        onEvent(SyncDeskEvent.FreshAirDone)
                    }
                }
            }
    }

    /** After a turn has ended, what is said still counts as missed for [demandHoldMillis] more. */
    private suspend fun releaseDemandAfterHold() {
        state
            .map { it.heldDemand }
            .distinctUntilChanged()
            .collectLatest { held ->
                if (held != null) {
                    delay(demandHoldMillis)
                    onEvent(SyncDeskEvent.DemandHoldExpired)
                }
            }
    }

    /** A refusal is shown for [refusalMillis] once the road is clear enough to show it. */
    private suspend fun clearRefusalAfterShown() {
        state
            .map { it.shownRefusal }
            .distinctUntilChanged()
            .collectLatest { shown ->
                if (shown != null) {
                    delay(refusalMillis)
                    onEvent(SyncDeskEvent.RefusalCleared)
                }
            }
    }

    /** The first-use agreement and the sound setting are kept as soon as they change. */
    private suspend fun saveSettingsChanges() {
        state.map { AppSettings(it.consented, it.chimesEnabled) }.distinctUntilChanged().drop(1).collect {
            settingsStore.save(it)
        }
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * The sounds a change of state calls for. There is one: a demanding stretch of the call has
         * ended with something marked as missed, and the call goes on. During a call nothing is
         * read aloud; the talk after the call says what was missed.
         */
        internal fun cuesBetween(before: SyncDeskState, after: SyncDeskState): List<Cue> = buildList {
            val stretch = before.capture as? CaptureState.Recording
            if (stretch != null && stretch.lineCount > 0 && after.capture == CaptureState.Idle && after.call.active) {
                add(Cue.MissedPartMarked)
            }
        }

        /** The car has just been put in Park and there is something of the drive to show. */
        internal fun parkedWithDigest(before: SyncDeskState, after: SyncDeskState): Boolean =
            !before.signals.isParked && after.signals.isParked && after.hasParkedDigest
    }
}
