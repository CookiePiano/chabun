package com.syncdesk.app.ui.office

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinLimits
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.ui.components.GlassDivider
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.RoundIconButton
import com.syncdesk.app.ui.components.SegmentedControl
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.tabularNumbers

/** Preset controls inside the shared sheet, which owns scrolling and focus restoration. */
@Composable
internal fun OfficeSettingsContent(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preset = state.preset
    val enabled = state.signals.deskOpen && !state.ux.noSetup &&
        (state.office.phase == OfficePhase.Off || state.office.phase == OfficePhase.On)
    val change: (OfficePreset) -> Unit = { next ->
        if (enabled) onEvent(SyncDeskEvent.PresetChanged(next.normalized()))
    }

    Column(modifier.fillMaxWidth()) {
        NumberSetting(SettingLabel.SeatSlide, preset.seatSlide, enabled) {
            change(preset.copy(seatSlide = it))
        }
        NumberSetting(SettingLabel.SeatHeight, preset.seatHeight, enabled) {
            change(preset.copy(seatHeight = it))
        }
        NumberSetting(
            label = SettingLabel.Backrest,
            value = preset.seatRecline,
            enabled = enabled,
            range = CabinLimits.SEAT_RECLINE_MIN..CabinLimits.SEAT_RECLINE_MAX,
            step = CabinLimits.SEAT_RECLINE_STEP,
            valueText = if (preset.seatRecline == 0) {
                stringResource(R.string.office_ui_setting_upright)
            } else {
                stringResource(R.string.office_ui_setting_recline, preset.seatRecline)
            },
        ) { change(preset.copy(seatRecline = it)) }
        ToggleSetting(R.string.office_ui_setting_reading_light, preset.readingLightOn, enabled) {
            change(preset.copy(readingLightOn = it))
        }
        NumberSetting(SettingLabel.Ambient, preset.ambientPercent, enabled) {
            change(preset.copy(ambientPercent = it))
        }
        // Fan speed has no OfficePreset field; show the vehicle value without a false control.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.office_ui_fan_label),
                style = SyncDesk.type.bodyStrong,
                color = SyncDesk.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.office_ui_setting_current_fan, state.cabin.fanLevel),
                style = SyncDesk.type.bodyStrong.tabularNumbers(),
                color = SyncDesk.colors.muted,
                textAlign = TextAlign.End,
            )
        }
        GlassDivider()
        Text(
            text = stringResource(R.string.office_ui_settings_note),
            style = SyncDesk.type.caption,
            color = SyncDesk.colors.muted,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.office_ui_setting_position_note),
            style = SyncDesk.type.caption,
            color = SyncDesk.colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.office_ui_settings_more),
            style = SyncDesk.type.bodyStrong,
            color = SyncDesk.colors.ink,
            modifier = Modifier.padding(top = 32.dp, bottom = 12.dp).semantics { heading() },
        )
        NumberSetting(SettingLabel.WheelUp, preset.wheel.up, enabled) {
            change(preset.copy(wheel = preset.wheel.copy(up = it)))
        }
        NumberSetting(SettingLabel.WheelForward, preset.wheel.forward, enabled) {
            change(preset.copy(wheel = preset.wheel.copy(forward = it)))
        }
        NumberSetting(SettingLabel.CushionTilt, preset.cushionTilt, enabled) {
            change(preset.copy(cushionTilt = it))
        }
        NumberSetting(SettingLabel.CushionLength, preset.cushionLength, enabled) {
            change(preset.copy(cushionLength = it))
        }
        NumberSetting(SettingLabel.Lumbar, preset.lumbar, enabled) {
            change(preset.copy(lumbar = it))
        }
        ToggleSetting(R.string.office_ui_setting_cabin_light, preset.cabinLightOn, enabled) {
            change(preset.copy(cabinLightOn = it))
        }
        NumberSetting(SettingLabel.Shade, preset.shadePercent, enabled) {
            change(preset.copy(shadePercent = it))
        }
        NumberSetting(
            label = SettingLabel.Ventilation,
            value = preset.seatVentilation,
            enabled = enabled,
            range = 0..CabinLimits.SEAT_LEVEL_MAX,
            step = 1,
            valueText = if (preset.seatVentilation == 0) {
                stringResource(R.string.office_ui_setting_keep_seat)
            } else {
                stringResource(R.string.office_ui_setting_level, preset.seatVentilation)
            },
        ) { change(preset.copy(seatVentilation = it)) }
        ToggleSetting(R.string.office_ui_setting_door_lock, preset.lockDoors, enabled) {
            change(preset.copy(lockDoors = it))
        }
        ToggleSetting(R.string.office_ui_setting_usb, preset.usbPower, enabled) {
            change(preset.copy(usbPower = it))
        }
    }
}

@Composable
private fun NumberSetting(
    label: SettingLabel,
    value: Int,
    enabled: Boolean,
    range: IntRange = 0..100,
    step: Int = CabinLimits.PERCENT_STEP,
    valueText: String? = null,
    onChange: (Int) -> Unit,
) {
    val shownValue = valueText ?: stringResource(R.string.office_ui_setting_percent, value)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label.title),
            style = SyncDesk.type.bodyStrong,
            color = SyncDesk.colors.ink,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(
            icon = LineIcon.Minus.vector(3f),
            description = stringResource(label.decrease),
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = enabled && value > range.first,
            modifier = Modifier.semantics { stateDescription = shownValue },
        )
        Text(
            text = shownValue,
            style = SyncDesk.type.bodyStrong.tabularNumbers(),
            color = SyncDesk.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(210.dp),
        )
        RoundIconButton(
            icon = LineIcon.Plus.vector(3f),
            description = stringResource(label.increase),
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = enabled && value < range.last,
            modifier = Modifier.semantics { stateDescription = shownValue },
        )
    }
    GlassDivider()
}

@Composable
private fun ToggleSetting(
    @StringRes title: Int,
    value: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val label = stringResource(title)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SyncDesk.type.bodyStrong,
            color = SyncDesk.colors.ink,
            modifier = Modifier.weight(1f),
        )
        SegmentedControl(
            options = listOf(stringResource(R.string.office_ui_setting_off), stringResource(R.string.office_ui_setting_on)),
            selectedIndex = if (value) 1 else 0,
            onSelect = { onChange(it == 1) },
            enabled = enabled,
            modifier = Modifier.width(398.dp).semantics { contentDescription = label },
        )
    }
    GlassDivider()
}

private enum class SettingLabel(@StringRes val title: Int, @StringRes val decrease: Int, @StringRes val increase: Int) {
    SeatSlide(R.string.office_ui_setting_seat_slide, R.string.office_ui_decrease_seat_slide, R.string.office_ui_increase_seat_slide),
    SeatHeight(R.string.office_ui_setting_seat_height, R.string.office_ui_decrease_seat_height, R.string.office_ui_increase_seat_height),
    Backrest(R.string.office_ui_setting_backrest, R.string.office_ui_decrease_backrest, R.string.office_ui_increase_backrest),
    Ambient(R.string.office_ui_setting_ambient, R.string.office_ui_decrease_ambient, R.string.office_ui_increase_ambient),
    WheelUp(R.string.office_ui_setting_wheel_up, R.string.office_ui_decrease_wheel_up, R.string.office_ui_increase_wheel_up),
    WheelForward(R.string.office_ui_setting_wheel_forward, R.string.office_ui_decrease_wheel_forward, R.string.office_ui_increase_wheel_forward),
    CushionTilt(R.string.office_ui_setting_cushion_tilt, R.string.office_ui_decrease_cushion_tilt, R.string.office_ui_increase_cushion_tilt),
    CushionLength(R.string.office_ui_setting_cushion_length, R.string.office_ui_decrease_cushion_length, R.string.office_ui_increase_cushion_length),
    Lumbar(R.string.office_ui_setting_lumbar, R.string.office_ui_decrease_lumbar, R.string.office_ui_increase_lumbar),
    Shade(R.string.office_ui_setting_shade, R.string.office_ui_decrease_shade, R.string.office_ui_increase_shade),
    Ventilation(R.string.office_ui_setting_ventilation, R.string.office_ui_decrease_ventilation, R.string.office_ui_increase_ventilation),
}
