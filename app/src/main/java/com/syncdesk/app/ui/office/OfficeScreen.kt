package com.syncdesk.app.ui.office

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.BatteryGuard
import com.syncdesk.app.domain.CabinLimits
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.OfficeScene
import com.syncdesk.app.domain.OfficeState
import com.syncdesk.app.domain.OfficeStep
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskEngine
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.UsbLocation
import com.syncdesk.app.ui.cabin.OfficeStage
import com.syncdesk.app.ui.components.BigButton
import com.syncdesk.app.ui.components.ButtonKind
import com.syncdesk.app.ui.components.GlassCard
import com.syncdesk.app.ui.components.GlassDivider
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.RefusalNote
import com.syncdesk.app.ui.components.SegmentedControl
import com.syncdesk.app.ui.components.SheetHost
import com.syncdesk.app.ui.components.StepItem
import com.syncdesk.app.ui.components.StepList
import com.syncdesk.app.ui.components.StepState
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.tabularNumbers

/** The office screen in the 1682 x 1210 design frame supplied by the app skeleton. */
@Composable
fun OfficeScreen(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OfficeSettingsHost(state, onEvent, modifier.fillMaxSize()) { openSettings, settingsFocus ->
        val canPause = state.office.move != null
        Box(
            Modifier.fillMaxSize().pointerInput(canPause, onEvent) {
                if (canPause) {
                    detectTapGestures { position ->
                        // The side-camera area must never become a touch target.
                        if (position.x >= Design.reservedZone.width.toPx() ||
                            position.y >= Design.reservedZone.height.toPx()
                        ) {
                            onEvent(SyncDeskEvent.OfficePauseRequested)
                        }
                    }
                }
            },
        ) {
            OfficeStage(
                state = state,
                modifier = Modifier.offset(Design.marginStart, Design.marginTop).size(968.dp, 1138.dp),
            )
            OfficeSidePanel(
                state, onEvent, openSettings, settingsFocus, fillHeight = true,
                modifier = Modifier.offset(1050.dp, Design.marginTop).size(584.dp, 1138.dp),
            )
            if (state.office.phase == OfficePhase.Off) {
                BigButton(
                    label = stringResource(R.string.office_ui_close),
                    onClick = onClose,
                    kind = ButtonKind.Glass,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .offset(Design.marginStart + 24.dp, -(Design.marginBottom + 24.dp)),
                )
            }
        }
    }
}

/** Compatibility entry point for callers that host only the office controls, including tests. */
@Composable
fun OfficePanel(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    OfficeSettingsHost(
        state, onEvent,
        modifier.then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
    ) { openSettings, settingsFocus ->
        OfficeSidePanel(state, onEvent, openSettings, settingsFocus, fillHeight, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfficeSettingsHost(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    modifier: Modifier,
    content: @Composable (openSettings: () -> Unit, settingsFocus: FocusRequester) -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsFocus = remember { FocusRequester() }
    val canEdit = state.signals.deskOpen && !state.ux.noSetup &&
        state.office.phase in listOf(OfficePhase.Off, OfficePhase.On)
    LaunchedEffect(canEdit) { if (!canEdit) settingsOpen = false }
    SheetHost(
        open = settingsOpen && canEdit,
        onClose = { settingsOpen = false },
        title = stringResource(R.string.office_ui_settings),
        subtitle = stringResource(R.string.office_ui_settings_subtitle),
        returnFocusTo = settingsFocus,
        height = 940.dp,
        modifier = modifier,
        sheet = { OfficeSettingsContent(state, onEvent, Modifier.fillMaxWidth()) },
    ) {
        content({ if (canEdit) settingsOpen = true }, settingsFocus)
    }
}

@Composable
private fun OfficeSidePanel(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    onSettings: () -> Unit,
    settingsFocus: FocusRequester,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val office = state.office
    val colors = SyncDesk.colors
    val locked = office.phase == OfficePhase.Off && state.ux.noSetup
    val title = when {
        office.paused -> R.string.office_ui_paused_title
        office.phase == OfficePhase.Applying -> R.string.office_ui_applying_title
        office.phase == OfficePhase.Restoring -> R.string.office_ui_restoring_title
        else -> R.string.office_ui_title
    }
    val hint = when {
        office.paused -> R.string.office_ui_paused_hint
        office.phase == OfficePhase.Applying -> R.string.office_ui_applying_hint
        office.phase == OfficePhase.Restoring -> R.string.office_ui_restoring_hint
        office.phase == OfficePhase.On -> R.string.office_ui_on_hint
        else -> R.string.office_ui_off_hint
    }
    Column(
        modifier.then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.verticalScroll(rememberScrollState())),
    ) {
        Column(if (fillHeight) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier) {
            Text(
                stringResource(title), style = SyncDesk.type.title1, color = colors.ink,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            Text(
                stringResource(hint), style = SyncDesk.type.caption, color = colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            when {
                locked -> LockedNotice(Modifier.padding(top = 28.dp))
                office.phase == OfficePhase.On -> {
                    SegmentedControl(
                        options = listOf(stringResource(R.string.office_ui_work), stringResource(R.string.office_ui_meeting)),
                        selectedIndex = office.scene.ordinal,
                        onSelect = { onEvent(SyncDeskEvent.OfficeSceneChanged(OfficeScene.entries[it])) },
                        enabled = !state.ux.noSetup,
                        modifier = Modifier.padding(top = 26.dp).fillMaxWidth(),
                    )
                    Crossfade(
                        targetState = office.scene,
                        animationSpec = SyncDesk.motion.fade(350),
                        label = "officeSceneDetails",
                    ) { scene ->
                        OfficeEnvironment(state, scene, Modifier.padding(top = 22.dp).fillMaxWidth())
                    }
                    OfficePower(state, Modifier.padding(top = 20.dp).fillMaxWidth())
                }
                else -> StepList(officeSteps(office), Modifier.padding(top = 24.dp).fillMaxWidth())
            }
            state.shownRefusal?.let { RefusalNote(it, it, Modifier.padding(top = 18.dp)) }
            Spacer(Modifier.height(20.dp))
        }
        OfficeActions(state, onEvent, onSettings, settingsFocus)
    }
}

/** The nine engine steps form the five visible groups in the approved design. */
@Composable
private fun officeSteps(office: OfficeState): List<StepItem> {
    val restoring = office.phase == OfficePhase.Restoring
    val groups = listOf(
        listOf(OfficeStep.SteeringWheel),
        listOf(OfficeStep.SeatPosition),
        listOf(OfficeStep.Cushion),
        listOf(OfficeStep.Backrest),
        listOf(OfficeStep.Lights, OfficeStep.Shade, OfficeStep.Climate, OfficeStep.DoorLock, OfficeStep.Usb),
    )
    val labels = if (restoring) listOf(
        R.string.office_ui_undo_wheel, R.string.office_ui_undo_seat, R.string.office_ui_undo_cushion,
        R.string.office_ui_undo_backrest, R.string.office_ui_undo_environment,
    ) else listOf(
        R.string.office_ui_step_wheel, R.string.office_ui_step_seat, R.string.office_ui_step_cushion,
        R.string.office_ui_step_backrest, R.string.office_ui_step_environment,
    )
    val order = if (restoring) groups.indices.reversed() else groups.indices.toList()
    return order.mapIndexed { position, index ->
        val group = groups[index]
        val status = when {
            office.pending?.step in group -> StepState.Now
            restoring && group.none { it in office.completed } -> StepState.Done
            !restoring && group.all { it in office.completed } -> StepState.Done
            else -> StepState.Waiting
        }
        StepItem(position + 1, stringResource(labels[index]), status)
    }
}

@Composable
private fun LockedNotice(modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 32.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Icon(LineIcon.Lock.vector(), null, Modifier.size(40.dp), tint = SyncDesk.colors.missInk)
            Text(stringResource(R.string.office_ui_locked_hint), style = SyncDesk.type.captionLarge, color = SyncDesk.colors.ink)
        }
    }
}

@Composable
private fun OfficeEnvironment(state: SyncDeskState, scene: OfficeScene, modifier: Modifier = Modifier) {
    val cabin = state.cabin
    val meeting = scene == OfficeScene.Meeting
    val defaults = OfficePreset()
    val ambientOn = cabin.ambientPercent > 0
    // Vehicle colour reports may omit alpha; white is determined by its RGB channels.
    val whiteAmbient = (cabin.ambientColor and 0x00FFFFFF) ==
        (CabinLimits.MEETING_AMBIENT_COLOR and 0x00FFFFFF)
    val lighting = when {
        !cabin.readingLightOn && !cabin.cabinLightOn && !ambientOn ->
            stringResource(R.string.office_ui_light_off)
        meeting && cabin.readingLightOn && !cabin.cabinLightOn && whiteAmbient &&
            cabin.ambientPercent == CabinLimits.MEETING_AMBIENT ->
            stringResource(R.string.office_ui_meeting_light)
        !meeting && cabin.readingLightOn && !cabin.cabinLightOn && cabin.ambientPercent == defaults.ambientPercent ->
            stringResource(R.string.office_ui_work_light)
        cabin.readingLightOn && cabin.cabinLightOn && ambientOn ->
            stringResource(R.string.office_ui_lights_reading_cabin_ambient, cabin.ambientPercent)
        cabin.readingLightOn && cabin.cabinLightOn ->
            stringResource(R.string.office_ui_lights_reading_cabin)
        cabin.readingLightOn && ambientOn ->
            stringResource(R.string.office_ui_lights_reading_ambient, cabin.ambientPercent)
        cabin.cabinLightOn && ambientOn ->
            stringResource(R.string.office_ui_lights_cabin_ambient, cabin.ambientPercent)
        cabin.readingLightOn -> stringResource(R.string.office_ui_lights_reading)
        cabin.cabinLightOn -> stringResource(R.string.office_ui_lights_cabin)
        else -> stringResource(R.string.office_ui_lights_ambient, cabin.ambientPercent)
    }
    val shade = when {
        cabin.shadePercent == 0 -> stringResource(R.string.office_ui_shade_off)
        meeting && cabin.shadePercent == CabinLimits.MEETING_SHADE ->
            stringResource(R.string.office_ui_meeting_shade)
        !meeting && cabin.shadePercent == defaults.shadePercent -> stringResource(R.string.office_ui_work_shade)
        else -> stringResource(R.string.office_ui_shade_percent, cabin.shadePercent)
    }
    val sceneState = state.copy(office = state.office.copy(scene = scene))
    val expectedFan = SyncDeskEngine.officeChange(OfficeStep.Climate, sceneState).fanLevel
    val workFan = minOf(state.ranges.quietFanLevel, (state.office.snapshot ?: cabin).fanLevel)
    val fan = when {
        !cabin.climateOn && cabin.seatVentilation > 0 ->
            stringResource(R.string.office_ui_fan_off_ventilation, cabin.seatVentilation)
        !cabin.climateOn -> stringResource(R.string.office_ui_fan_off)
        meeting && cabin.windowOpenPercent == 0 && cabin.fanLevel == expectedFan && cabin.fanLevel < workFan ->
            stringResource(R.string.office_ui_meeting_fan)
        !meeting && cabin.fanLevel == expectedFan && cabin.seatVentilation == defaults.seatVentilation ->
            stringResource(R.string.office_ui_work_fan, cabin.fanLevel)
        meeting && cabin.windowOpenPercent == 0 && cabin.seatVentilation > 0 ->
            stringResource(R.string.office_ui_fan_ventilation_closed, cabin.fanLevel, cabin.seatVentilation)
        meeting && cabin.windowOpenPercent == 0 ->
            stringResource(R.string.office_ui_fan_closed, cabin.fanLevel)
        meeting && cabin.seatVentilation > 0 ->
            stringResource(R.string.office_ui_fan_ventilation_open, cabin.fanLevel, cabin.seatVentilation, cabin.windowOpenPercent)
        meeting -> stringResource(R.string.office_ui_fan_open, cabin.fanLevel, cabin.windowOpenPercent)
        cabin.seatVentilation > 0 ->
            stringResource(R.string.office_ui_fan_ventilation_levels, cabin.fanLevel, cabin.seatVentilation)
        else -> stringResource(R.string.office_ui_fan_level, cabin.fanLevel)
    }
    GlassCard(modifier) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 6.dp)) {
            EnvironmentRow(LineIcon.Sun, stringResource(R.string.office_ui_light_label), lighting)
            GlassDivider()
            EnvironmentRow(LineIcon.Shade, stringResource(R.string.office_ui_shade_label), shade)
            GlassDivider()
            EnvironmentRow(LineIcon.Wind, stringResource(R.string.office_ui_fan_label), fan)
            GlassDivider()
            EnvironmentRow(
                LineIcon.Lock, stringResource(R.string.office_ui_door_label),
                stringResource(if (cabin.doorsLocked) R.string.office_ui_locked else R.string.office_ui_unlocked),
            )
        }
    }
}

@Composable
private fun EnvironmentRow(icon: LineIcon, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon.vector(), null, Modifier.size(34.dp), tint = SyncDesk.colors.accent)
        Text(label, Modifier.width(136.dp), style = SyncDesk.type.bodyStrong, color = SyncDesk.colors.ink)
        Text(value, Modifier.weight(1f), style = SyncDesk.type.caption, color = SyncDesk.colors.muted)
    }
}

@Composable
private fun OfficePower(state: SyncDeskState, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val low = state.batteryGuard != BatteryGuard.None
    val accent = if (low) colors.missInk else colors.accent
    val percent = state.cabin.batteryPercent.coerceIn(0, 100)
    val charge by animateFloatAsState(percent / 100f, SyncDesk.motion.fade(600), label = "officeBattery")
    val supply = when {
        !state.usb.on -> R.string.office_ui_usb_off
        low -> R.string.office_ui_usb_label
        state.usb.deviceWatts == null -> R.string.office_ui_usb_disconnected
        else -> R.string.office_ui_usb_charging
    }
    GlassCard(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (low) Modifier.clip(SyncDeskShapes.card).background(colors.missTint)
                    .border(1.5.dp, colors.missBar, SyncDeskShapes.card) else Modifier)
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(LineIcon.Zap.vector(), null, Modifier.size(34.dp), tint = accent)
                Text(stringResource(supply), Modifier.weight(1f), style = SyncDesk.type.bodyStrong, color = colors.ink)
                Text(stringResource(R.string.office_ui_usb_watts, state.usb.suppliedWatts), style = SyncDesk.type.power, color = if (low) colors.missInk else colors.ink)
            }
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.office_ui_battery, percent), style = SyncDesk.type.caption.tabularNumbers(), color = colors.muted)
                Box(
                    Modifier.weight(1f).height(14.dp).clip(SyncDeskShapes.pill)
                        .background(colors.ink.copy(alpha = 0.14f))
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), 0f..100f) },
                ) {
                    Box(Modifier.fillMaxWidth(charge.coerceIn(0f, 1f)).fillMaxHeight().background(if (low) colors.missBar else colors.accent))
                }
            }
            Text(
                stringResource(when (state.usb.location) {
                    UsbLocation.CenterConsoleFront -> R.string.usb_location_console_front
                    UsbLocation.CenterConsoleRear -> R.string.usb_location_console_rear
                }),
                style = SyncDesk.type.small, color = colors.muted, modifier = Modifier.padding(top = 10.dp),
            )
            when (state.batteryGuard) {
                BatteryGuard.Reduced -> Text(
                    stringResource(R.string.office_ui_battery_reduced, BatteryGuard.REDUCED_WATTS, BatteryGuard.OFF_BELOW),
                    style = SyncDesk.type.caption, color = colors.ink, modifier = Modifier.padding(top = 12.dp),
                )
                BatteryGuard.Off -> Text(
                    stringResource(R.string.office_ui_battery_off),
                    style = SyncDesk.type.caption, color = colors.ink, modifier = Modifier.padding(top = 12.dp),
                )
                BatteryGuard.None -> Unit
            }
        }
    }
}

@Composable
private fun OfficeActions(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    onSettings: () -> Unit,
    settingsFocus: FocusRequester,
) {
    val office = state.office
    when {
        office.paused -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigButton(
                stringResource(R.string.office_ui_restore), { onEvent(SyncDeskEvent.OfficeRestoreRequested) },
                Modifier.weight(1f), kind = ButtonKind.Glass, icon = LineIcon.Back.vector(), horizontalPadding = 20.dp,
            )
            BigButton(
                stringResource(if (office.phase == OfficePhase.Restoring) R.string.office_ui_resume_restore else R.string.office_ui_resume),
                { onEvent(SyncDeskEvent.OfficeResumeRequested) },
                Modifier.weight(1f), enabled = office.phase == OfficePhase.Restoring || !state.ux.noSetup,
                horizontalPadding = 20.dp,
            )
        }
        office.phase == OfficePhase.Off -> BigButton(
            stringResource(if (state.ux.noSetup) R.string.office_ui_locked_action else R.string.office_ui_start),
            { onEvent(SyncDeskEvent.OfficeRequested) }, Modifier.fillMaxWidth(),
            icon = if (state.ux.noSetup) LineIcon.Lock.vector() else LineIcon.Laptop.vector(), enabled = !state.ux.noSetup,
            horizontalPadding = 20.dp,
        )
        office.phase == OfficePhase.On -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigButton(
                stringResource(R.string.office_ui_settings), onSettings,
                Modifier.weight(1f).focusRequester(settingsFocus), kind = ButtonKind.Glass,
                enabled = !state.ux.noSetup, horizontalPadding = 20.dp,
            )
            BigButton(
                stringResource(R.string.office_ui_restore), { onEvent(SyncDeskEvent.OfficeRestoreRequested) },
                Modifier.weight(1f), kind = ButtonKind.Solid, icon = LineIcon.Back.vector(), horizontalPadding = 20.dp,
            )
        }
        else -> BigButton(
            stringResource(R.string.office_ui_pause), { onEvent(SyncDeskEvent.OfficePauseRequested) },
            Modifier.fillMaxWidth(), kind = ButtonKind.Solid, icon = LineIcon.Pause.vector(),
        )
    }
}
