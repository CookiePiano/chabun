package com.syncdesk.app.runtime

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.syncdesk.app.data.AppSettings
import com.syncdesk.app.data.SharedPreferencesSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun agree() = SharedPreferencesSettingsStore(context).save(AppSettings(consented = true))

    private fun startedService(action: String): String? {
        StartupReceiver().onReceive(context, Intent(action))
        return shadowOf(context).nextStartedService?.component?.className
    }

    @Test
    fun `the service starts with the vehicle once the driver has agreed`() {
        agree()
        assertEquals(SyncDeskService::class.java.name, startedService(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `the service starts again after the app is updated`() {
        agree()
        assertEquals(SyncDeskService::class.java.name, startedService(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `nothing starts before the driver has agreed`() {
        assertNull(startedService(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `other broadcasts start nothing`() {
        agree()
        assertNull(startedService(Intent.ACTION_LOCALE_CHANGED))
    }
}
