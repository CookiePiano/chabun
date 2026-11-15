package com.syncdesk.app.data

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Knows whether the driver is on a call from the audio mode the platform sets for it, which needs
 * no permission (reading the phone state would, and would ask the driver for it).
 *
 * On the Pleos Connect v2.1.0 emulator (checked 2026-11-13 with `adb emu gsm call/accept/cancel`)
 * the mode goes NORMAL → RINGTONE while it rings → IN_CALL once answered → NORMAL when hung up.
 * Internet calls from meeting apps set IN_COMMUNICATION, which counts as a call too. Ringing does
 * not: the call starts when it is answered.
 */
class PhoneCallMonitor(context: Context, scope: CoroutineScope) : CallMonitor {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val _call = MutableStateFlow(CallSignal(active = audio?.let { inCall(it.mode) } ?: false))
    override val call: StateFlow<CallSignal> = _call.asStateFlow()

    init {
        val manager = audio
        if (manager == null) {
            Log.w(TAG, "No audio service; calls are only started from the demo controls")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.addOnModeChangedListener(context.mainExecutor) { mode -> publish(mode) }
        } else {
            // Android 9 to 11 have no mode listener; the mode is cheap to read.
            scope.launch {
                while (isActive) {
                    publish(manager.mode)
                    delay(POLL_MILLIS)
                }
            }
        }
    }

    private fun publish(mode: Int) {
        val active = inCall(mode)
        if (active != _call.value.active) Log.i(TAG, "phone call ${if (active) "started" else "ended"} (audio mode $mode)")
        _call.value = CallSignal(active = active)
    }

    companion object {
        private const val TAG = "SyncDeskCall"
        private const val POLL_MILLIS = 500L

        /** Whether an audio [mode] means a call is under way: answered on the phone, or online. */
        fun inCall(mode: Int): Boolean =
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }
}

/**
 * One call made of two sources: a call is on while either says so. The driver talking is taken
 * from the demo controls, the only source that can force it.
 */
fun mergedCall(phone: CallMonitor, demo: CallMonitor, scope: CoroutineScope): CallMonitor {
    val merged = combine(phone.call, demo.call) { real, simulated ->
        CallSignal(active = real.active || simulated.active, driverSpeaking = simulated.driverSpeaking)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        CallSignal(active = phone.call.value.active || demo.call.value.active, driverSpeaking = demo.call.value.driverSpeaking),
    )
    return object : CallMonitor {
        override val call: StateFlow<CallSignal> = merged
    }
}
