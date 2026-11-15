package com.syncdesk.app.data

import android.content.Context
import androidx.core.content.edit
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange

/**
 * Keeps the change that puts the cabin back after a tuned call in the app's private preferences.
 * Only the parts the app moved are written; a part that is not there is left alone on recovery.
 */
class SharedPreferencesQuietStore(context: Context) : QuietStore {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): CabinChange? {
        fun int(key: String) = if (preferences.contains(key)) preferences.getInt(key, 0) else null
        fun flag(key: String) = if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        val restore = CabinChange(
            fanLevel = int(KEY_FAN),
            windowOpenPercent = int(KEY_WINDOW),
            recirculation = flag(KEY_RECIRCULATION),
            airDirection = preferences.getString(KEY_AIR_DIRECTION, null)
                ?.let { name -> AirDirection.entries.firstOrNull { it.name == name } },
            temperatureC = if (preferences.contains(KEY_TEMPERATURE)) preferences.getFloat(KEY_TEMPERATURE, 0f) else null,
            seatVentilation = int(KEY_SEAT_VENTILATION),
            seatHeating = int(KEY_SEAT_HEATING),
            airCleanerOn = flag(KEY_AIR_CLEANER),
            defrosterOn = flag(KEY_DEFROSTER),
            ambientColor = int(KEY_AMBIENT_COLOR),
            callVolume = int(KEY_CALL_VOLUME),
        )
        return restore.takeUnless { it.isEmpty }
    }

    override fun save(restore: CabinChange?) {
        // commit, not apply: this is written right before the cabin is changed and must survive
        // the app being stopped a moment later.
        preferences.edit(commit = true) {
            clear()
            if (restore == null) return@edit
            restore.fanLevel?.let { putInt(KEY_FAN, it) }
            restore.windowOpenPercent?.let { putInt(KEY_WINDOW, it) }
            restore.recirculation?.let { putBoolean(KEY_RECIRCULATION, it) }
            restore.airDirection?.let { putString(KEY_AIR_DIRECTION, it.name) }
            restore.temperatureC?.let { putFloat(KEY_TEMPERATURE, it) }
            restore.seatVentilation?.let { putInt(KEY_SEAT_VENTILATION, it) }
            restore.seatHeating?.let { putInt(KEY_SEAT_HEATING, it) }
            restore.airCleanerOn?.let { putBoolean(KEY_AIR_CLEANER, it) }
            restore.defrosterOn?.let { putBoolean(KEY_DEFROSTER, it) }
            restore.ambientColor?.let { putInt(KEY_AMBIENT_COLOR, it) }
            restore.callVolume?.let { putInt(KEY_CALL_VOLUME, it) }
        }
    }

    private companion object {
        const val FILE = "quiet_restore"
        const val KEY_FAN = "fan_level"
        const val KEY_WINDOW = "window_open_percent"
        const val KEY_RECIRCULATION = "recirculation"
        const val KEY_AIR_DIRECTION = "air_direction"
        const val KEY_TEMPERATURE = "temperature_c"
        const val KEY_SEAT_VENTILATION = "seat_ventilation"
        const val KEY_SEAT_HEATING = "seat_heating"
        const val KEY_AIR_CLEANER = "air_cleaner"
        const val KEY_DEFROSTER = "defroster"
        const val KEY_AMBIENT_COLOR = "ambient_color"
        const val KEY_CALL_VOLUME = "call_volume"
    }
}
