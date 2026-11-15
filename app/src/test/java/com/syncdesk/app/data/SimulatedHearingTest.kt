package com.syncdesk.app.data

import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.Hearing
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedHearingTest {

    private val driving = VehicleSignals(gear = Gear.Drive)

    /** A closed, quiet cabin: only the road is heard. */
    private val still = CabinState(fanLevel = 0, windowOpenPercent = 0, airDirection = AirDirection.Feet)

    /** [millis] of 16 kHz mono at a constant amplitude. */
    private fun tone(amplitude: Int, millis: Int): ByteArray = ByteArray(millis * 32).also { bytes ->
        for (i in 0 until millis * 16) {
            val sample = if (i % 2 == 0) amplitude else -amplitude
            bytes[2 * i] = (sample and 0xff).toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
    }

    // region Cabin noise

    @Test
    fun aClosedCabinWithTheClimateOffIsAsLoudAsTheRoadAndQuieterWhenParked() {
        assertEquals(CabinNoise.ROAD, CabinNoise.level(still, driving, AmbientNoise.Low))
        assertEquals(CabinNoise.PARKED, CabinNoise.level(still, VehicleSignals(gear = Gear.Park), AmbientNoise.Low))
    }

    @Test
    fun everyFanLevelAddsNoiseAndAirAtTheFaceAddsMore() {
        val feet = CabinNoise.level(still.copy(fanLevel = 6), driving, AmbientNoise.Low)
        val face = CabinNoise.level(still.copy(fanLevel = 6, airDirection = AirDirection.Face), driving, AmbientNoise.Low)
        val lower = CabinNoise.level(still.copy(fanLevel = 5), driving, AmbientNoise.Low)

        assertEquals(CabinNoise.ROAD + 6 * CabinNoise.PER_FAN_LEVEL, feet)
        assertEquals(feet + CabinNoise.AIR_AT_FACE, face)
        assertEquals(feet - CabinNoise.PER_FAN_LEVEL, lower)
        // A fan that is off blows nowhere.
        assertEquals(CabinNoise.ROAD, CabinNoise.level(still.copy(airDirection = AirDirection.Face), driving, AmbientNoise.Low))
    }

    @Test
    fun anOpenWindowHeavyRainAndTheSurroundingsEachAddNoise() {
        fun level(cabin: CabinState = still, signals: VehicleSignals = driving, ambient: AmbientNoise = AmbientNoise.Low) =
            CabinNoise.level(cabin, signals, ambient)

        assertEquals(CabinNoise.ROAD + 6, level(cabin = still.copy(windowOpenPercent = 30)))
        assertEquals(CabinNoise.ROAD + 20, level(cabin = still.copy(windowOpenPercent = 100)))
        assertEquals(CabinNoise.ROAD + CabinNoise.HEAVY_RAIN, level(signals = driving.copy(wiper = WiperSpeed.High)))
        assertEquals(CabinNoise.ROAD, level(signals = driving.copy(wiper = WiperSpeed.Low)))
        assertEquals(CabinNoise.ROAD + CabinNoise.AMBIENT_NORMAL, level(ambient = AmbientNoise.Normal))
        assertEquals(CabinNoise.ROAD + CabinNoise.AMBIENT_HIGH, level(ambient = AmbientNoise.High))
    }

    @Test
    fun theDemoCabinIsHeardWellOnceTheWindowIsClosedAndHardlyWhenItGetsLoudAround() {
        fun hearing(cabin: CabinState, ambient: AmbientNoise) =
            HearingLevels(VoiceLevel.NOMINAL, CabinNoise.level(cabin, driving, ambient)).hearing
        val answered = CabinState().copy(windowOpenPercent = 0, recirculation = true)
        val floor = answered.copy(fanLevel = 2, airDirection = AirDirection.Feet)

        assertEquals(Hearing.Poor, hearing(CabinState(), AmbientNoise.Normal))
        assertEquals(Hearing.Good, hearing(answered, AmbientNoise.Normal))
        assertEquals(Hearing.Bad, hearing(answered, AmbientNoise.High))
        // With the fan as low as it goes the call is heard again, if with effort.
        assertEquals(Hearing.Poor, hearing(floor, AmbientNoise.High))
        assertEquals(Hearing.Good, hearing(floor, AmbientNoise.Normal))
    }

    // endregion

    // region Voice level

    @Test
    fun aVoiceIsMeasuredFromItsSpeechNotFromThePausesAroundIt() {
        val speech = tone(amplitude = 3_277, millis = 1_000) // -20 dBFS
        val withPauses = tone(0, 500) + speech + tone(0, 1_500)

        assertEquals(VoiceLevel.NOMINAL, VoiceLevel.of(speech))
        assertEquals(VoiceLevel.of(speech), VoiceLevel.of(withPauses))
    }

    @Test
    fun aLouderRecordingIsALouderVoiceAndSilenceIsNone() {
        val quiet = VoiceLevel.of(tone(amplitude = 1_638, millis = 500))!! // -26 dBFS
        val loud = VoiceLevel.of(tone(amplitude = 6_554, millis = 500))!! // -14 dBFS

        assertEquals(VoiceLevel.NOMINAL - 6, quiet)
        assertEquals(VoiceLevel.NOMINAL + 6, loud)
        assertNull(VoiceLevel.of(tone(0, 500)))
        assertNull(VoiceLevel.of(ByteArray(0)))
    }

    @Test
    fun everyStepOfCallVolumeMakesTheVoiceLouder() {
        val reference = CabinState().callVolume

        assertEquals(60, VoiceLevel.heard(60, reference))
        assertEquals(60 + VoiceLevel.PER_VOLUME_STEP, VoiceLevel.heard(60, reference + 1))
        assertEquals(60 - VoiceLevel.PER_VOLUME_STEP, VoiceLevel.heard(60, reference - 1))
    }

    // endregion

    // region The source

    @Test
    fun levelsFollowTheCabinTheSignalsTheVoiceAndTheNoiseAround() = runTest {
        val car = SimulatedCar(driving, CabinState())
        val voice = MutableStateFlow(60)
        val hearing = SimulatedHearing(car.cabin, car.signals, voice)
        val seen = mutableListOf<HearingLevels>()
        val job = launch { hearing.levels().collect { seen += it } }
        runCurrent()
        val start = CabinNoise.level(CabinState(), driving, AmbientNoise.Normal)
        assertEquals(HearingLevels(voice = 60, noise = start), seen.last())

        // Lowering the fan and closing the window really make the cabin quieter.
        car.apply(CabinChange(fanLevel = 5, windowOpenPercent = 0))
        runCurrent()
        assertEquals(start - CabinNoise.PER_FAN_LEVEL - 6, seen.last().noise)

        car.apply(CabinChange(callVolume = 4))
        voice.value = 63
        runCurrent()
        assertEquals(63 + VoiceLevel.PER_VOLUME_STEP, seen.last().voice)

        val before = seen.last().noise
        hearing.setAmbientNoise(AmbientNoise.High)
        car.setWiper(WiperSpeed.High)
        runCurrent()
        assertEquals(AmbientNoise.High, hearing.ambientNoise.value)
        assertEquals(before - CabinNoise.AMBIENT_NORMAL + CabinNoise.AMBIENT_HIGH + CabinNoise.HEAVY_RAIN, seen.last().noise)

        // A change that leaves both levels as they were is not reported again.
        val count = seen.size
        car.apply(CabinChange(seatRecline = 20))
        runCurrent()
        assertTrue(seen.size == count)
        job.cancelAndJoin()
    }

    // endregion
}
