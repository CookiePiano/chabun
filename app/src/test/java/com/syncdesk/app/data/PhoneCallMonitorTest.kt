package com.syncdesk.app.data

import android.media.AudioManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneCallMonitorTest {

    @Test
    fun aCallIsOnOnceAnsweredOnThePhoneOrOnline() {
        assertFalse(PhoneCallMonitor.inCall(AudioManager.MODE_NORMAL))
        // Ringing is not a call yet: the driver has not answered.
        assertFalse(PhoneCallMonitor.inCall(AudioManager.MODE_RINGTONE))
        assertTrue(PhoneCallMonitor.inCall(AudioManager.MODE_IN_CALL))
        assertTrue(PhoneCallMonitor.inCall(AudioManager.MODE_IN_COMMUNICATION))
    }

    private class FakeCall(initial: CallSignal = CallSignal()) : CallMonitor {
        val state = MutableStateFlow(initial)
        override val call: StateFlow<CallSignal> = state
    }

    @Test
    fun aCallIsOnWhileEitherThePhoneOrTheDemoSaysSo() = runTest {
        val phone = FakeCall()
        val demo = FakeCall()
        val merged = mergedCall(phone, demo, backgroundScope)
        runCurrent()
        assertEquals(CallSignal(), merged.call.value)

        phone.state.value = CallSignal(active = true)
        runCurrent()
        assertEquals(CallSignal(active = true), merged.call.value)

        demo.state.value = CallSignal(active = true, driverSpeaking = true)
        phone.state.value = CallSignal()
        runCurrent()
        assertEquals(CallSignal(active = true, driverSpeaking = true), merged.call.value)

        demo.state.value = CallSignal()
        runCurrent()
        assertEquals(CallSignal(), merged.call.value)
    }

    @Test
    fun aCallAlreadyOnWhenTheAppStartsIsSeenAtOnce() = runTest {
        val merged = mergedCall(FakeCall(CallSignal(active = true)), FakeCall(), backgroundScope)
        assertEquals(CallSignal(active = true), merged.call.value)
    }
}
