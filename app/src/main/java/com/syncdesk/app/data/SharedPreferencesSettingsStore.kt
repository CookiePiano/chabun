package com.syncdesk.app.data

import android.content.Context
import androidx.core.content.edit

/** Keeps [AppSettings] in the app's private preferences. Nothing saved yet means first use. */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load() = AppSettings(
        consented = preferences.getBoolean(KEY_CONSENTED, false),
        chimesEnabled = preferences.getBoolean(KEY_CHIMES, true),
    )

    override fun save(settings: AppSettings) {
        preferences.edit {
            putBoolean(KEY_CONSENTED, settings.consented)
            putBoolean(KEY_CHIMES, settings.chimesEnabled)
        }
    }

    private companion object {
        const val FILE = "settings"
        const val KEY_CONSENTED = "consented"
        const val KEY_CHIMES = "chimes"
    }
}
