package com.syncdesk.app.runtime

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.syncdesk.app.R
import com.syncdesk.app.SyncDeskApp

/**
 * The app as a small card: one icon and the two lines of [StatusText]. Touching it opens the app.
 *
 * It is a standard Android app widget, which is what the cards under the Pleos Connect driving
 * view (media, tyre pressure, incoming call) are built from. That strip takes cards only from
 * apps signed by the platform, from a fixed list of card types (seen on the Pleos Connect
 * emulator on 2026-11-14), so there this card is shown only once the vehicle maker adds it. Any
 * other widget host can place it today.
 */
class StatusCardProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val runtime = (context.applicationContext as SyncDeskApp).container.runtime
        manager.updateAppWidget(ids, views(context, statusText(context.resources, runtime.state.value)))
    }

    companion object {
        /** Redraws every placed card. Does nothing on a device without a widget service. */
        fun show(context: Context, status: StatusText) {
            context.getSystemService(AppWidgetManager::class.java)
                ?.updateAppWidget(ComponentName(context, StatusCardProvider::class.java), views(context, status))
        }

        internal fun views(context: Context, status: StatusText) =
            RemoteViews(context.packageName, R.layout.status_card).apply {
                setTextViewText(R.id.status_headline, status.headline)
                setTextViewText(R.id.status_detail, status.detail)
                setViewVisibility(R.id.status_detail, if (status.detail.isEmpty()) View.GONE else View.VISIBLE)
                setContentDescription(
                    R.id.status_card,
                    listOf(status.headline, status.detail).filter { it.isNotEmpty() }.joinToString("\n"),
                )
                setOnClickPendingIntent(R.id.status_card, SyncDeskService.openApp(context))
            }
    }
}
