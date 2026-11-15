package com.syncdesk.app

import android.app.Application
import android.content.Context
import com.syncdesk.app.data.CallMonitor
import com.syncdesk.app.data.CallSimulator
import com.syncdesk.app.data.CallMemoStore
import com.syncdesk.app.data.ChimePlayer
import com.syncdesk.app.data.ContactSimulator
import com.syncdesk.app.data.ContactSource
import com.syncdesk.app.data.DemoControls
import com.syncdesk.app.data.DemoReplies
import com.syncdesk.app.data.DriverReplySource
import com.syncdesk.app.data.HearingSource
import com.syncdesk.app.data.MeetingSource
import com.syncdesk.app.data.MemoAnswerer
import com.syncdesk.app.data.Narrator
import com.syncdesk.app.data.NoiseSimulator
import com.syncdesk.app.data.PhoneCallMonitor
import com.syncdesk.app.data.PresetStore
import com.syncdesk.app.data.QuietStore
import com.syncdesk.app.data.RecordedCall
import com.syncdesk.app.data.ReplySimulator
import com.syncdesk.app.data.RuleMemoAnswerer
import com.syncdesk.app.data.ScriptedMeetingSource
import com.syncdesk.app.data.SettingsStore
import com.syncdesk.app.data.SharedPreferencesCallMemoStore
import com.syncdesk.app.data.SharedPreferencesPresetStore
import com.syncdesk.app.data.SharedPreferencesQuietStore
import com.syncdesk.app.data.SharedPreferencesSettingsStore
import com.syncdesk.app.data.SimulatedCar
import com.syncdesk.app.data.SimulatedContacts
import com.syncdesk.app.data.SimulatedHearing
import com.syncdesk.app.data.SimulatedNarrator
import com.syncdesk.app.data.SimulatedUsbPower
import com.syncdesk.app.data.ToneChimePlayer
import com.syncdesk.app.data.UsbPower
import com.syncdesk.app.data.UsbSimulator
import com.syncdesk.app.data.VehicleGateway
import com.syncdesk.app.data.VehicleSimulator
import com.syncdesk.app.data.mergedCall
import com.syncdesk.app.data.pleos.CarUxLimitsSource
import com.syncdesk.app.data.pleos.PleosNarrator
import com.syncdesk.app.data.pleos.PleosSpeechRecognizer
import com.syncdesk.app.data.pleos.PleosVehicleGateway
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.runtime.DialogPhrases
import com.syncdesk.app.runtime.SyncDeskRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the app depends on, created once per process.
 *
 * The vehicle is the Pleos Vehicle SDK. The call is a recording heard by the Pleos SpeechToText
 * SDK, which writes the call down and tells when the driver talks, and the talk after the call is
 * spoken by the Pleos TextToSpeech SDK. A call starts when the driver answers the phone (or from the demo
 * controls); only its audio is a recording. How well the call is heard is simulated from that
 * recording and the cabin. Each part has its own interface, so connecting the real one changes
 * only this class.
 */
class AppContainer(context: Context) {
    /** Lives as long as the process; plays the call recording while the call is connected. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val pleosVehicle = PleosVehicleGateway(context)
    private val speechRecognizer = PleosSpeechRecognizer(context)
    private val simulatedCall = SimulatedCar()
    /** Calls alternate between the two demo meetings, so the rules are seen on more than one. */
    private val recordedCall = RecordedCall(
        meetings = listOf(
            RecordedCall.Meeting(
                ScriptedMeetingSource.fromResources(context.resources, ScriptedMeetingSource.DELIVERY),
                RecordedCall.assetClips(context.assets, "delivery"),
            ),
            RecordedCall.Meeting(
                ScriptedMeetingSource.fromResources(context.resources, ScriptedMeetingSource.EXPANSION),
                RecordedCall.assetClips(context.assets, "expansion"),
            ),
        ),
        recognizer = speechRecognizer,
    )

    val gateway: VehicleGateway = pleosVehicle
    /** A real phone (or meeting app) call, or one started from the demo controls. */
    val callMonitor: CallMonitor = recordedCall.monitor(mergedCall(PhoneCallMonitor(context, scope), simulatedCall, scope), scope)
    val meeting: MeetingSource = recordedCall
    val narrator: Narrator = PleosNarrator(context, fallback = SimulatedNarrator())
    val presetStore: PresetStore = SharedPreferencesPresetStore(context)
    val memoStore: CallMemoStore = SharedPreferencesCallMemoStore(context)
    val quietStore: QuietStore = SharedPreferencesQuietStore(context)
    val settingsStore: SettingsStore = SharedPreferencesSettingsStore(context)
    val speechAvailable: suspend () -> Boolean = { speechRecognizer.isAvailable() }

    /**
     * Simulated: the loudness of the recorded voice against the noise the cabin would make. The
     * real readings need system privileges (see [HearingSource]).
     */
    private val simulatedHearing = SimulatedHearing(pleosVehicle.cabin, pleosVehicle.signals, recordedCall.voiceLevel)
    val hearing: HearingSource = simulatedHearing

    /** The microphone is not connected yet, so the driver's replies come from the demo controls. */
    private val demoReplies = DemoReplies()
    val replies: DriverReplySource = demoReplies

    /**
     * Simulated: the phone's missed calls and unread messages need the phone link's own access
     * (see [ContactSource]). The demo controls add them and look at them.
     */
    private val simulatedContacts = SimulatedContacts(
        callers = context.resources.getStringArray(R.array.demo_contact_callers).toList(),
        senders = context.resources.getStringArray(R.array.demo_contact_senders).toList(),
    )
    val contacts: ContactSource = simulatedContacts

    /** Simulated: Vehicle SDK 2.3.2 has no USB port in it (see [UsbPower]). */
    private val simulatedUsb = SimulatedUsbPower()
    val usbPower: UsbPower = simulatedUsb

    /** Answers by rule until Gleo AI is connected behind the same interface. */
    val answerer: MemoAnswerer = RuleMemoAnswerer()
    val chimes: ChimePlayer = ToneChimePlayer()

    /** What the platform restricts while driving (AAOS CarUxRestrictions). */
    val uxLimits: StateFlow<UxLimits> = CarUxLimitsSource(context).limits

    /** What Gleo says in the talk after a call, in the system language. */
    val dialogPhrases = DialogPhrases(
        offer = context.getString(R.string.gleo_offer),
        summaryIntro = context.getString(R.string.gleo_summary_intro),
        noKeyPoints = context.getString(R.string.gleo_no_key_points),
        missedIntro = context.getString(R.string.gleo_missed_intro),
        missedByNoiseIntro = context.getString(R.string.gleo_missed_noise_intro),
        missedMixedIntro = context.getString(R.string.gleo_missed_mixed_intro),
        ask = context.getString(R.string.gleo_ask),
        notMentioned = context.getString(R.string.gleo_not_mentioned),
    )

    /**
     * Controls for whatever is simulated: always the call, and any vehicle signal the vehicle
     * does not provide. Null when the build has no demo controls.
     */
    val demoControls: DemoControls? = if (BuildConfig.DEMO_CONTROLS) {
        object :
            DemoControls,
            VehicleSimulator by pleosVehicle,
            CallSimulator by simulatedCall,
            ReplySimulator by demoReplies,
            NoiseSimulator by simulatedHearing,
            ContactSimulator by simulatedContacts,
            UsbSimulator by simulatedUsb {}
    } else {
        null
    }

    /**
     * Decides and acts for as long as the process lives. The screen and the background service
     * both read it, so what happens does not depend on which of them is running.
     */
    val runtime: SyncDeskRuntime by lazy {
        SyncDeskRuntime(
            gateway = gateway,
            callMonitor = callMonitor,
            meeting = meeting,
            narrator = narrator,
            presetStore = presetStore,
            phrases = dialogPhrases,
            answerer = answerer,
            memoStore = memoStore,
            quietStore = quietStore,
            hearing = hearing,
            settingsStore = settingsStore,
            speechAvailable = speechAvailable,
            replies = replies,
            chimes = chimes,
            uxLimits = uxLimits,
            contacts = contacts,
            usbPower = usbPower,
        )
    }
}

class SyncDeskApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
