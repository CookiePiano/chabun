package com.syncdesk.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesQuietStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun whatPutsTheCabinBackSurvivesARestartWithEveryPartAndIsClearedOnceUsed() {
        val restore = CabinChange(
            fanLevel = 6,
            windowOpenPercent = 30,
            recirculation = false,
            airDirection = AirDirection.FaceAndFeet,
            temperatureC = 22.5f,
            seatVentilation = 0,
            seatHeating = 2,
            airCleanerOn = false,
            defrosterOn = false,
            ambientColor = 0xFF2979FF.toInt(),
            callVolume = 3,
        )
        SharedPreferencesQuietStore(context).save(restore)
        assertEquals(restore, SharedPreferencesQuietStore(context).load())

        SharedPreferencesQuietStore(context).save(null)
        assertEquals(null, SharedPreferencesQuietStore(context).load())
    }

    @Test
    fun onlyThePartsTheAppMovedAreKeptAndAnEarlierNoteDoesNotLeakIntoALaterOne() {
        SharedPreferencesQuietStore(context).save(CabinChange(fanLevel = 6, windowOpenPercent = 30, callVolume = 3))
        val later = CabinChange(windowOpenPercent = 30, recirculation = false)
        SharedPreferencesQuietStore(context).save(later)

        assertEquals(later, SharedPreferencesQuietStore(context).load())
    }

    @Test
    fun aNoteWrittenByTheVersionBeforeSmartCabinControlIsStillRead() {
        context.getSharedPreferences("quiet_restore", Context.MODE_PRIVATE).edit()
            .putInt("fan_level", 6).putInt("window_open_percent", 30).putBoolean("recirculation", false).commit()

        assertEquals(
            CabinChange(fanLevel = 6, windowOpenPercent = 30, recirculation = false),
            SharedPreferencesQuietStore(context).load(),
        )
    }
}
