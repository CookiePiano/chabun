package com.syncdesk.app.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.syncdesk.app.MainActivity
import com.syncdesk.app.R
import com.syncdesk.app.SyncDeskApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the app working while it is not on screen.
 *
 * Pleos Connect freezes an app's process about twenty seconds after another app covers it (seen
 * on the Pleos Connect emulator on 2026-11-14 with navigation in front: the call went on, the fan
 * stayed up and nothing was written down). A foreground service is what the platform offers to
 * say the app is still doing something for the driver. Its notification, and the status card,
 * carry the one line the screen would have shown.
 */
class SyncDeskService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val runtime = (application as SyncDeskApp).container.runtime
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.status_channel), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(statusText(resources, runtime.state.value)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: IllegalStateException) {
            // The system restarted the service while the app is in the background, where a
            // foreground service may not start. It starts again the next time the app is opened.
            Log.w(TAG, "Not allowed to run in the foreground now", e)
            stopSelf()
            return
        }
        scope.launch {
            runtime.state.map { statusText(resources, it) }.distinctUntilChanged().collect { status ->
                notifications.notify(NOTIFICATION_ID, notification(status))
                StatusCardProvider.show(this@SyncDeskService, status)
            }
        }
        scope.launch { runtime.parkedWithDigest.collect { bringAppForward() } }
    }

    /**
     * Asks for the app's screen when the car has just been parked with something of the drive to
     * show, so the driver does not have to open the app for it.
     */
    private fun bringAppForward() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            Log.i(TAG, "Parked with something to show: asked for the screen")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not ask for the screen on parking", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(status: StatusText): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(status.headline)
            .setContentText(status.detail.ifEmpty { null })
            .setContentIntent(openApp(this))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

    companion object {
        private const val TAG = "SyncDeskService"
        private const val CHANNEL = "status"
        private const val NOTIFICATION_ID = 1

        /**
         * The platform allows this while the app is on screen and when the vehicle has just
         * started (see [StartupReceiver]); from the background otherwise it throws.
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, SyncDeskService::class.java))
        }

        /** Brings the app's screen back; carries no data. */
        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
