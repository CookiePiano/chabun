package com.syncdesk.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.syncdesk.app.R
import com.syncdesk.app.data.AmbientNoise
import com.syncdesk.app.data.DemoControls
import com.syncdesk.app.data.ScriptedMeetingSource
import com.syncdesk.app.data.VehicleFeature
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.KeyPoints
import com.syncdesk.app.domain.MeetingInfo
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import com.syncdesk.app.domain.afterEvents
import com.syncdesk.app.domain.afterOfficeOn
import com.syncdesk.app.ui.theme.SyncDeskTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders every screen state in the window shapes Pleos Connect uses (one portrait window, or a
 * landscape window spanning both), by day and by night, with the real fonts and strings.
 *
 * `gradlew :app:recordRoborazziDebug` writes the images to `app/build/outputs/roborazzi`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SyncDeskScreenshotTest(private val case: Case) {

    data class Case(
        val scene: String,
        val window: Window,
        val night: Boolean,
        val language: String = "ko-rKR",
        val demoOpen: Boolean = false,
        val link: VehicleLink = VehicleLink(),
    ) {
        val fileName: String
            get() = listOf(
                scene,
                window.name.lowercase(),
                if (night) "night" else "day",
                language.substringBefore('-'),
            ).joinToString("_")

        override fun toString(): String = fileName
    }

    /**
     * App window sizes measured on the Pleos Connect v2.1.0 emulator (2560 x 1440, 160 dpi, so dp
     * equal pixels): one app gets the whole app area, two apps share it side by side, or three.
     * The full-window areas (parked, office, onboarding) always get the whole app area, so they
     * are captured Wide only; the small driving window is captured in every width.
     */
    enum class Window(val width: Int, val height: Int) {
        Third(561, 1210),
        Compact(841, 1210),
        Wide(1682, 1210),
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun capture() {
        val orientation = if (case.window.width > case.window.height) "land" else "port"
        val nightMode = if (case.night) "night" else "notnight"
        RuntimeEnvironment.setQualifiers(
            "${case.language}-w${case.window.width}dp-h${case.window.height}dp-$orientation-$nightMode-mdpi",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val meeting = ScriptedMeetingSource.fromResources(context.resources)
        val state = states(context).getValue(case.scene)
        // Keeps the list of full-window scenes below true to what the skeleton shows.
        assertEquals("${case.scene} fills the window", case.scene in FullWindowScenes, state.area().fullWindow)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    SyncDeskTheme(night = case.night, motionEnabled = false) {
                        SyncDeskScreen(
                            state = state,
                            materials = meeting.materials,
                            onEvent = {},
                            demoControls = NoDemoControls,
                            link = case.link,
                            demoInitiallyOpen = case.demoOpen,
                            today = TODAY,
                        )
                    }
                }
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${case.fileName}.png")
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Case> {
            val scenes = listOf(
                "standby", "call", "quiet", "quiet_poor", "quiet_bad", "capture", "turn", "takeover", "auto_restricted",
                "dialog_waiting", "dialog_offer", "dialog_explain", "dialog_ask", "dialog_answer",
                "parked_empty", "office_off", "office_on", "auto_office",
                "onboarding", "no_consent", "refusal", "no_speech", "linking",
            )
            val all = scenes.flatMap { scene ->
                windowsFor(scene).flatMap { window ->
                    listOf(true, false).map { night -> Case(scene, window, night) }
                }
            }
            val english = listOf("quiet", "capture", "office_on", "dialog_ask", "office_off", "auto_restricted")
                .map { Case(it, if (it in FullWindowScenes) Window.Wide else Window.Compact, night = true, language = "en-rUS") }
            // The demo controls as they look with no vehicle, and as they look on the Pleos Connect
            // emulator, where only self-driving is simulated.
            val demo = listOf(Window.Compact, Window.Wide).flatMap {
                listOf(
                    Case("quiet_demo", it, night = true, demoOpen = true),
                    Case("quiet_demo_connected", it, night = true, demoOpen = true, link = EmulatorLink),
                )
            }
            // With the demo controls open the desk has less height; office mode's button keeps its own.
            val deskWithDemo = listOf(
                Case("auto_restricted_demo", Window.Wide, night = false, demoOpen = true, link = EmulatorLink),
                Case("office_off_demo", Window.Wide, night = false, demoOpen = true, link = EmulatorLink),
            )
            return all + english + demo + deskWithDemo
        }

        /**
         * The demo call as the app hears it, in the order it is said: the others' sentences with
         * their key points, and of the driver's own lines only those that hold a promise.
         */
        private fun sentences(context: Context): List<MeetingNote> {
            val speakers = context.resources.getStringArray(R.array.demo_note_speakers)
            val texts = context.resources.getStringArray(R.array.demo_note_texts)
            val driver = context.getString(R.string.demo_driver_speaker)
            return texts.indices.mapNotNull { index ->
                val time = "14:%02d".format(12 + index)
                if (speakers[index] == driver) {
                    KeyPoints.commitment(texts[index])?.let { task ->
                        MeetingNote(0, time, speakers[index], texts[index], gist = task, byDriver = true)
                    }
                } else {
                    MeetingNote(0, time, speakers[index], texts[index], gist = KeyPoints.of(texts[index]))
                }
            }
        }

        /** Each state is reached the way the app reaches it: by sending events to the engine. */
        private fun states(context: Context): Map<String, SyncDeskState> {
            fun SyncDeskState.after(vararg events: SyncDeskEvent) = afterEvents(*events)
            fun SyncDeskState.hearing(notes: List<MeetingNote>) =
                notes.fold(this) { state, note -> state.after(SyncDeskEvent.NoteCaptured(note)) }
            fun signals(gear: Gear = Gear.Drive, wiper: WiperSpeed = WiperSpeed.Off, turn: TurnSignal = TurnSignal.Off) =
                SyncDeskEvent.SignalsChanged(VehicleSignals(gear, wiper, turn))
            val hungUp = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)

            val said = sentences(context)
            val standby = SyncDeskState()
            val call = standby.after(
                SyncDeskEvent.MeetingStarted(MeetingInfo(context.getString(R.string.demo_meeting_title))),
                SyncDeskEvent.CallChanged(active = true, driverSpeaking = false, date = TODAY.toString(), time = "14:12"),
            ).let { it.copy(call = it.call.copy(elapsedSeconds = 134)) }
            fun heard(margin: Int) = SyncDeskEvent.HearingChanged(HearingLevels(voice = 62, noise = 62 - margin))
            // The call is answered and heard well: the window is closed and the air drawn from inside.
            val quiet = call.after(heard(margin = 11))
            // Hard to hear for a while: the air went to the feet and the fan down three steps.
            val poor = quiet.after(
                heard(margin = 6), SyncDeskEvent.TuneTick, SyncDeskEvent.TuneTick, SyncDeskEvent.TuneTick, SyncDeskEvent.TuneTick,
            )
            // Hardly heard with the fan as low as it goes: the call is made louder.
            val bad = poor.after(heard(margin = 0), SyncDeskEvent.TuneTick, SyncDeskEvent.TuneTick, SyncDeskEvent.TuneTick)
            // The call opens on a clear road; then it rains hard for four sentences.
            val capture = call.hearing(said.take(2)).after(signals(wiper = WiperSpeed.High)).hearing(said.subList(2, 6))
            // The rest is said on a clear road again, and the call is still going on.
            val recorded = capture.after(signals()).hearing(said.drop(6))
            // Hung up on a clear road: the car offers the summary and waits for the reply.
            val offered = recorded.after(hungUp, SyncDeskEvent.DialogSpoken)
            val explaining = offered.after(SyncDeskEvent.DriverReplied(DriverReply.Yes))
            val asked = explaining.after(SyncDeskEvent.DialogSpoken, SyncDeskEvent.DialogSpoken)
            // Parked without having been talked through the call: the memo is waiting.
            val officeOff = offered.after(signals(gear = Gear.Park))
            // Parked after the talk: the memo says it has been explained.
            val officeOn = asked.after(signals(gear = Gear.Park)).afterOfficeOn()
            return mapOf(
                "standby" to standby,
                "call" to call,
                "quiet" to quiet,
                "quiet_poor" to poor,
                "quiet_bad" to bad,
                "quiet_demo" to quiet,
                "quiet_demo_connected" to quiet,
                "office_off_demo" to officeOff,
                "capture" to capture,
                "turn" to call.after(signals(turn = TurnSignal.Left)).hearing(said.take(2)),
                // The call ended in the rain: the offer waits for the road to clear.
                "dialog_waiting" to capture.after(hungUp),
                "dialog_offer" to offered,
                "dialog_explain" to explaining,
                "dialog_ask" to asked,
                "dialog_answer" to asked.after(
                    SyncDeskEvent.DriverReplied(DriverReply.Other(context.getString(R.string.demo_reply_question_text))),
                ),
                "parked_empty" to standby.after(signals(gear = Gear.Park)),
                "takeover" to standby.after(
                    SyncDeskEvent.SignalsChanged(VehicleSignals(autonomous = true, takeoverRequested = true)),
                ),
                "auto_office" to recorded.after(
                    hungUp,
                    SyncDeskEvent.SignalsChanged(VehicleSignals(autonomous = true)),
                ).afterOfficeOn(),
                // A moving car under the platform's restrictions: no setup, short texts, few items.
                "auto_restricted" to recorded.after(
                    hungUp,
                    SyncDeskEvent.SignalsChanged(VehicleSignals(autonomous = true)),
                    SyncDeskEvent.UxLimitsChanged(UxLimits(noSetup = true, maxStringLength = 16, maxItems = 6)),
                ),
                // As on the emulator while the car drives itself: setup forbidden, nothing else limited.
                "auto_restricted_demo" to recorded.after(
                    hungUp,
                    SyncDeskEvent.SignalsChanged(VehicleSignals(autonomous = true)),
                    SyncDeskEvent.UxLimitsChanged(UxLimits(noSetup = true)),
                ),
                "office_off" to officeOff,
                // First use: the notice while parked, and the line that says why nothing happens while driving.
                "onboarding" to standby.copy(consented = false).after(signals(gear = Gear.Park)),
                "no_consent" to standby.copy(consented = false).after(signals()),
                // The vehicle refused to close the window during a call.
                "refusal" to quiet.after(SyncDeskEvent.CommandRefused(CabinPart.Window)),
                "no_speech" to standby.after(signals(), SyncDeskEvent.SpeechChecked(available = false)),
                "linking" to standby.after(signals(), SyncDeskEvent.LinkChanged(pending = true)),
                "office_on" to officeOn,
            )
        }

        /** Scenes of the full-window areas (parked, office, onboarding); checked in [capture]. */
        private val FullWindowScenes = setOf(
            "parked_empty", "office_off", "office_on", "auto_office", "auto_restricted",
            "auto_restricted_demo", "office_off_demo", "onboarding",
        )

        private fun windowsFor(scene: String): List<Window> =
            if (scene in FullWindowScenes) listOf(Window.Wide) else Window.entries

        /** The day the screenshots are dated against, so "오늘" never depends on when they are taken. */
        private val TODAY = LocalDate.of(2026, 11, 13)

        /** Every feature Pleos Connect v2.1.0 links; the others are simulated on every car. */
        private val EmulatorLink = VehicleLink(live = VehicleFeature.entries.filterTo(mutableSetOf()) { it.linkable })

        private object NoDemoControls : DemoControls {
            override fun sendReply(reply: DriverReply) = Unit
            override fun setGear(gear: Gear) = Unit
            override fun setWiper(speed: WiperSpeed) = Unit
            override fun setTurnSignal(signal: TurnSignal) = Unit
            override fun setAutonomous(autonomous: Boolean) = Unit
            override fun setCallActive(active: Boolean) = Unit
            override fun setDriverSpeaking(speaking: Boolean) = Unit
            override val ambientNoise = MutableStateFlow(AmbientNoise.Normal)
            override fun setAmbientNoise(level: AmbientNoise) = Unit
            override fun setBatteryPercent(percent: Int) = Unit
            override fun addMissedCall() = Unit
            override fun addMessage() = Unit
            override fun markAllSeen() = Unit
            override val deviceWatts = MutableStateFlow<Int?>(null)
            override fun setDeviceWatts(watts: Int?) = Unit
        }
    }
}
