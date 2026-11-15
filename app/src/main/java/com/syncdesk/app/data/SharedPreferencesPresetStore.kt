package com.syncdesk.app.data

import android.content.Context
import androidx.core.content.edit
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.WheelPosition

/**
 * Keeps the driver's office arrangement in the app's private preferences. A value that was never
 * saved takes today's default, so an arrangement saved by a version that knew fewer values (seat
 * backrest, reading light and ambient light only) is still read, with the rest at their defaults.
 */
class SharedPreferencesPresetStore(context: Context) : PresetStore {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): OfficePreset {
        val defaults = OfficePreset()
        return OfficePreset(
            seatRecline = preferences.getInt(KEY_SEAT, defaults.seatRecline),
            readingLightOn = preferences.getBoolean(KEY_LIGHT, defaults.readingLightOn),
            ambientPercent = preferences.getInt(KEY_AMBIENT, defaults.ambientPercent),
            wheel = WheelPosition(
                up = preferences.getInt(KEY_WHEEL_UP, defaults.wheel.up),
                forward = preferences.getInt(KEY_WHEEL_FORWARD, defaults.wheel.forward),
            ),
            seatSlide = preferences.getInt(KEY_SEAT_SLIDE, defaults.seatSlide),
            seatHeight = preferences.getInt(KEY_SEAT_HEIGHT, defaults.seatHeight),
            cushionTilt = preferences.getInt(KEY_CUSHION_TILT, defaults.cushionTilt),
            cushionLength = preferences.getInt(KEY_CUSHION_LENGTH, defaults.cushionLength),
            lumbar = preferences.getInt(KEY_LUMBAR, defaults.lumbar),
            cabinLightOn = preferences.getBoolean(KEY_CABIN_LIGHT, defaults.cabinLightOn),
            shadePercent = preferences.getInt(KEY_SHADE, defaults.shadePercent),
            seatVentilation = preferences.getInt(KEY_SEAT_VENTILATION, defaults.seatVentilation),
            lockDoors = preferences.getBoolean(KEY_LOCK_DOORS, defaults.lockDoors),
            usbPower = preferences.getBoolean(KEY_USB_POWER, defaults.usbPower),
        ).normalized()
    }

    override fun save(preset: OfficePreset) {
        preferences.edit {
            putInt(KEY_SEAT, preset.seatRecline)
            putBoolean(KEY_LIGHT, preset.readingLightOn)
            putInt(KEY_AMBIENT, preset.ambientPercent)
            putInt(KEY_WHEEL_UP, preset.wheel.up)
            putInt(KEY_WHEEL_FORWARD, preset.wheel.forward)
            putInt(KEY_SEAT_SLIDE, preset.seatSlide)
            putInt(KEY_SEAT_HEIGHT, preset.seatHeight)
            putInt(KEY_CUSHION_TILT, preset.cushionTilt)
            putInt(KEY_CUSHION_LENGTH, preset.cushionLength)
            putInt(KEY_LUMBAR, preset.lumbar)
            putBoolean(KEY_CABIN_LIGHT, preset.cabinLightOn)
            putInt(KEY_SHADE, preset.shadePercent)
            putInt(KEY_SEAT_VENTILATION, preset.seatVentilation)
            putBoolean(KEY_LOCK_DOORS, preset.lockDoors)
            putBoolean(KEY_USB_POWER, preset.usbPower)
        }
    }

    private companion object {
        const val FILE = "office_preset"
        // New keys for new units: a value saved as degrees or as glass tint is not read as one of these.
        const val KEY_SEAT = "seat_recline_position"
        const val KEY_LIGHT = "reading_light_on"
        const val KEY_AMBIENT = "ambient_percent"
        // Added with the laptop workspace.
        const val KEY_WHEEL_UP = "wheel_up_percent"
        const val KEY_WHEEL_FORWARD = "wheel_forward_percent"
        const val KEY_SEAT_SLIDE = "seat_slide_percent"
        const val KEY_SEAT_HEIGHT = "seat_height_percent"
        const val KEY_CUSHION_TILT = "cushion_tilt_percent"
        const val KEY_CUSHION_LENGTH = "cushion_length_percent"
        const val KEY_LUMBAR = "lumbar_percent"
        const val KEY_CABIN_LIGHT = "cabin_light_on"
        const val KEY_SHADE = "shade_percent"
        const val KEY_SEAT_VENTILATION = "seat_ventilation_level"
        const val KEY_LOCK_DOORS = "lock_doors"
        const val KEY_USB_POWER = "usb_power"
    }
}
