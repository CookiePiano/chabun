package com.syncdesk.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesSettingsStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun firstUseMeansNotAgreedWithSoundsOn() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(AppSettings(consented = false, chimesEnabled = true), SharedPreferencesSettingsStore(context).load())
    }

    @Test
    fun settingsSurviveARestart() {
        val settings = AppSettings(consented = true, chimesEnabled = false)
        SharedPreferencesSettingsStore(context).save(settings)

        assertEquals(settings, SharedPreferencesSettingsStore(context).load())
    }
}
