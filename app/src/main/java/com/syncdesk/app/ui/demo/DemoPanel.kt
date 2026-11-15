package com.syncdesk.app.ui.demo

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.data.AmbientNoise
import com.syncdesk.app.data.DemoControls
import com.syncdesk.app.data.SimulatedUsbPower
import com.syncdesk.app.data.VehicleFeature
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.TurnSignal
import com.syncdesk.app.domain.WiperSpeed
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import com.syncdesk.app.ui.theme.glassStrong

/**
 * Stands in for whatever is still simulated: the call and the driver's speech always, and any
 * vehicle signal the connected vehicle does not provide. A signal that comes from the real
 * vehicle has no control here; it is changed in the vehicle (on the emulator, with the Pleos
 * Connect Virtual Console).
 *
 * The panel sits below the app window instead of over it, and the window shrinks to fit above
 * it, so every screen stays whole and visible during a demonstration. Each control is a label
 * and its choices on one line, as wide as its words; controls wrap onto further lines instead of
 * cutting any word short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(
    state: SyncDeskState,
    link: VehicleLink,
    controls: DemoControls,
    modifier: Modifier = Modifier,
) {
    val items = buildList<@Composable (Modifier) -> Unit> {
        if (VehicleFeature.Gear !in link.live) {
            add { itemModifier ->
                Control(
                    label = stringResource(R.string.demo_gear),
                    options = listOf(stringResource(R.string.gear_park), stringResource(R.string.gear_drive)),
                    selectedIndex = if (state.signals.isParked) 0 else 1,
                    onSelect = { controls.setGear(if (it == 0) Gear.Park else Gear.Drive) },
                    modifier = itemModifier,
                )
            }
        }
        add { itemModifier ->
            Control(
                label = stringResource(R.string.demo_call),
                options = listOf(stringResource(R.string.demo_call_off), stringResource(R.string.demo_call_on)),
                selectedIndex = if (state.call.active) 1 else 0,
                onSelect = { controls.setCallActive(it == 1) },
                modifier = itemModifier,
            )
        }
        add { itemModifier ->
            Control(
                label = stringResource(R.string.demo_speaking),
                options = listOf(
                    stringResource(R.string.demo_speaking_off),
                    stringResource(R.string.demo_speaking_on),
                ),
                selectedIndex = if (state.call.driverSpeaking) 1 else 0,
                onSelect = { controls.setDriverSpeaking(it == 1) },
                modifier = itemModifier,
            )
        }
        // The microphone is not connected yet: the driver's replies in the talk after a call are
        // said here instead.
        add { itemModifier ->
            val question = stringResource(R.string.demo_reply_question_text)
            Control(
                label = stringResource(R.string.demo_reply),
                options = listOf(
                    stringResource(R.string.demo_reply_yes),
                    stringResource(R.string.demo_reply_no),
                    stringResource(R.string.demo_reply_question),
                    stringResource(R.string.demo_reply_stop),
                ),
                selectedIndex = -1,
                onSelect = {
                    controls.sendReply(
                        when (it) {
                            0 -> DriverReply.Yes
                            1 -> DriverReply.No
                            2 -> DriverReply.Other(question)
                            else -> DriverReply.Stop
                        },
                    )
                },
                modifier = itemModifier,
            )
        }
        // The noise around the car is always simulated: it is what makes the call hard to hear,
        // and so what the cabin is tuned against.
        add { itemModifier ->
            val noise by controls.ambientNoise.collectAsState()
            Control(
                label = stringResource(R.string.demo_noise),
                options = listOf(
                    stringResource(R.string.demo_noise_low),
                    stringResource(R.string.demo_noise_normal),
                    stringResource(R.string.demo_noise_high),
                ),
                selectedIndex = noise.ordinal,
                onSelect = { controls.setAmbientNoise(AmbientNoise.entries[it]) },
                modifier = itemModifier,
            )
        }
        // No vehicle reports self-driving yet, so this stage is always switched here.
        add { itemModifier ->
            Control(
                label = stringResource(R.string.demo_autonomous),
                options = listOf(
                    stringResource(R.string.demo_autonomous_off),
                    stringResource(R.string.demo_autonomous_on),
                ),
                selectedIndex = if (state.signals.autonomous) 1 else 0,
                onSelect = { controls.setAutonomous(it == 1) },
                modifier = itemModifier,
            )
        }
        // The phone is not connected: its missed calls and unread messages are made here.
        add { itemModifier ->
            Control(
                label = stringResource(R.string.demo_contacts),
                options = listOf(
                    stringResource(R.string.demo_contact_call),
                    stringResource(R.string.demo_contact_message),
                    stringResource(R.string.demo_contact_seen),
                ),
                selectedIndex = -1,
                onSelect = {
                    when (it) {
                        0 -> controls.addMissedCall()
                        1 -> controls.addMessage()
                        else -> controls.markAllSeen()
                    }
                },
                modifier = itemModifier,
            )
        }
        // Office mode guards the battery by its charge. Where the vehicle reports it, it is
        // changed in the vehicle (the console's battery level); otherwise here.
        if (VehicleFeature.Battery !in link.live) {
            add { itemModifier ->
                Control(
                    label = stringResource(R.string.demo_battery),
                    options = DEMO_BATTERY.map { stringResource(R.string.value_percent, it) },
                    selectedIndex = DEMO_BATTERY.indexOf(state.cabin.batteryPercent),
                    onSelect = { controls.setBatteryPercent(DEMO_BATTERY[it]) },
                    modifier = itemModifier,
                )
            }
        }
        // The vehicle has no USB-C port the app can reach: what is plugged in is chosen here.
        add { itemModifier ->
            val watts by controls.deviceWatts.collectAsState()
            Control(
                label = stringResource(R.string.demo_usb),
                options = SimulatedUsbPower.DEMO_DEVICES.map {
                    if (it == null) stringResource(R.string.demo_usb_none) else stringResource(R.string.value_watts, it)
                },
                selectedIndex = SimulatedUsbPower.DEMO_DEVICES.indexOf(watts),
                onSelect = { controls.setDeviceWatts(SimulatedUsbPower.DEMO_DEVICES[it]) },
                modifier = itemModifier,
            )
        }
        if (VehicleFeature.Wiper !in link.live) {
            add { itemModifier ->
                Control(
                    label = stringResource(R.string.demo_wiper),
                    options = listOf(
                        stringResource(R.string.demo_wiper_off),
                        stringResource(R.string.demo_wiper_low),
                        stringResource(R.string.demo_wiper_high),
                    ),
                    selectedIndex = state.signals.wiper.ordinal,
                    onSelect = { controls.setWiper(WiperSpeed.entries[it]) },
                    modifier = itemModifier,
                )
            }
        }
        if (VehicleFeature.TurnSignal !in link.live) {
            add { itemModifier ->
                Control(
                    label = stringResource(R.string.demo_turn),
                    options = listOf(
                        stringResource(R.string.demo_turn_off),
                        stringResource(R.string.demo_turn_left),
                        stringResource(R.string.demo_turn_right),
                    ),
                    selectedIndex = state.signals.turnSignal.ordinal,
                    onSelect = { controls.setTurnSignal(TurnSignal.entries[it]) },
                    modifier = itemModifier,
                )
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .glassStrong(SyncDeskShapes.sheet)
            .heightIn(max = MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = linkSummary(link),
            style = SyncDesk.type.small,
            color = SyncDesk.colors.muted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item -> item(Modifier) }
        }
    }
}

/**
 * One line saying whether a vehicle is connected and which features are simulated, including the
 * ones simulated on every car ([VehicleFeature.linkable]).
 */
@Composable
private fun linkSummary(link: VehicleLink): String {
    if (!link.connected) return stringResource(R.string.demo_link_none)
    val simulated = link.simulated.map { featureName(it) }
    return if (simulated.isEmpty()) {
        stringResource(R.string.demo_link_all)
    } else {
        stringResource(R.string.demo_link_partial, simulated.joinToString(", "))
    }
}

@Composable
private fun featureName(feature: VehicleFeature): String = stringResource(
    when (feature) {
        VehicleFeature.Gear -> R.string.demo_gear
        VehicleFeature.Wiper -> R.string.demo_wiper
        VehicleFeature.TurnSignal -> R.string.demo_turn
        VehicleFeature.DriveAssist -> R.string.mode_assist
        VehicleFeature.Fan -> R.string.rail_fan
        VehicleFeature.Window -> R.string.rail_window
        VehicleFeature.Recirculation -> R.string.rail_recirculation
        VehicleFeature.Steering -> R.string.demo_steering
        VehicleFeature.ReadingLight -> R.string.office_light
        VehicleFeature.Ambient -> R.string.office_ambient
        VehicleFeature.Seat -> R.string.office_seat
        VehicleFeature.AirDirection -> R.string.rail_air_direction
        VehicleFeature.Temperature -> R.string.feature_temperature
        VehicleFeature.AirConditioning -> R.string.feature_air_conditioning
        VehicleFeature.AirCleaner -> R.string.feature_air_cleaner
        VehicleFeature.Defroster -> R.string.feature_defroster
        VehicleFeature.SeatClimate -> R.string.feature_seat_climate
        VehicleFeature.AmbientColor -> R.string.feature_ambient_color
        VehicleFeature.SeatSlide -> R.string.feature_seat_slide
        VehicleFeature.SeatHeight -> R.string.label_seat_height
        VehicleFeature.CushionTilt -> R.string.feature_cushion_tilt
        VehicleFeature.CabinLight -> R.string.label_cabin_light
        VehicleFeature.DoorLock -> R.string.feature_door_lock
        VehicleFeature.Battery -> R.string.demo_battery
        VehicleFeature.CallVolume -> R.string.rail_call_volume
        VehicleFeature.CushionLength -> R.string.label_cushion_length
        VehicleFeature.Lumbar -> R.string.label_lumbar
        VehicleFeature.Shade -> R.string.label_shade
        VehicleFeature.WheelPosition -> R.string.feature_wheel_position
        VehicleFeature.UsbPower -> R.string.office_step_usb
    },
)

/** A label and its choices on one line, each choice as wide as its words. */
@Composable
private fun Control(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SyncDesk.colors
    val view = LocalView.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = SyncDesk.type.caption, color = colors.muted, maxLines = 1, softWrap = false)
        Row(
            modifier = Modifier
                .glass(SyncDeskShapes.pill, shadow = false)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .heightIn(min = Design.minTouch)
                        .clip(SyncDeskShapes.pill)
                        .background(if (selected) colors.solid else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSelect(index)
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = SyncDesk.type.caption,
                        color = if (selected) colors.onSolid else colors.ink,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The panel never takes more than this; beyond it, it scrolls. */
private val MAX_HEIGHT = 420.dp

/** Battery charges the demo offers: plenty, below the first guard, below the second. */
private val DEMO_BATTERY = listOf(80, 25, 15)
