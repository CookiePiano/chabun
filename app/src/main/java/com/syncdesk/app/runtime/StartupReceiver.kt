package com.syncdesk.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.syncdesk.app.data.SettingsStore
import com.syncdesk.app.data.SharedPreferencesSettingsStore

/**
 * Starts the background service when the vehicle starts, so the driver never has to open the app
 * before driving: calls come without warning, and nobody opens a note-taking app first.
 *
 * Only once the driver has agreed to the app on first use. The agreement is kept per user, so
 * nothing starts for the system's own user or for a driver who has not seen the notice. Also
 * after an update of the app, which stops the service.
 */
class StartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldStart(intent.action, SharedPreferencesSettingsStore(context))) return
        try {
            SyncDeskService.start(context)
        } catch (e: IllegalStateException) {
            // The platform did not allow it this time; opening the app starts the service as before.
            Log.w(TAG, "Could not start with the vehicle", e)
        }
    }

    companion object {
        private const val TAG = "StartupReceiver"

        internal fun shouldStart(action: String?, settings: SettingsStore): Boolean =
            (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) &&
                settings.load().consented
    }
}
