package com.syncdesk.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.syncdesk.app.domain.OfficePreset
import com.syncdesk.app.domain.WheelPosition
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesPresetStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context.getSharedPreferences("office_preset", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theWholeArrangementSurvivesARestart() {
        val own = OfficePreset(
            seatRecline = 10, readingLightOn = false, ambientPercent = 40, wheel = WheelPosition(80, 60),
            seatSlide = 90, seatHeight = 20, cushionTilt = 10, cushionLength = 40, lumbar = 50,
            cabinLightOn = false, shadePercent = 70, seatVentilation = 2, lockDoors = false, usbPower = false,
        )
        SharedPreferencesPresetStore(context).save(own)

        assertEquals(own, SharedPreferencesPresetStore(context).load())
    }

    @Test
    fun anArrangementSavedByTheVersionWithThreeStepsIsStillReadWithTheRestAtTheirDefaults() {
        context.getSharedPreferences("office_preset", Context.MODE_PRIVATE).edit()
            .putInt("seat_recline_position", 40).putBoolean("reading_light_on", false).putInt("ambient_percent", 70).commit()

        assertEquals(
            OfficePreset().copy(seatRecline = 40, readingLightOn = false, ambientPercent = 70),
            SharedPreferencesPresetStore(context).load(),
        )
    }

    @Test
    fun nothingSavedMeansTheDefaults() {
        assertEquals(OfficePreset(), SharedPreferencesPresetStore(context).load())
    }
}
