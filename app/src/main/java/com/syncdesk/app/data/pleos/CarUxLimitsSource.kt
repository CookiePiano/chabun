package com.syncdesk.app.data.pleos

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import com.syncdesk.app.domain.UxLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The driving restrictions the platform asks apps to apply, from AAOS CarUxRestrictionsManager.
 *
 * The app declares itself distraction optimized, so it stays on screen while driving; in return,
 * the Pleos driving restriction guide asks it to read the active restrictions and limit its own UI.
 * On the Pleos Connect v2.1.0 emulator (checked 2026-11-13) a car in Drive reports 0x50:
 * no setup (seat and vehicle settings) and no video. Other regions may also limit string length
 * and the number of items shown; those limits are passed on as the platform gives them.
 *
 * Where there is no car service (a phone, a test), nothing is restricted.
 */
class CarUxLimitsSource(context: Context) {

    private val _limits = MutableStateFlow(UxLimits())
    val limits: StateFlow<UxLimits> = _limits.asStateFlow()

    private var car: Car? = null

    init {
        try {
            car = Car.createCar(context.applicationContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!ready) return@createCar
                val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    ?: return@createCar
                manager.registerListener { publish(it) }
                manager.currentCarUxRestrictions?.let { publish(it) }
            }
        } catch (error: Throwable) {
            // No car library or service on this device: there is nothing to restrict.
            Log.w(TAG, "Car UX restrictions unavailable: $error")
        }
    }

    private fun publish(restrictions: CarUxRestrictions) {
        val limits = UxLimits.of(
            requiresDistractionOptimization = restrictions.isRequiresDistractionOptimization,
            active = restrictions.activeRestrictions,
            maxStringLength = restrictions.maxRestrictedStringLength,
            maxItems = restrictions.maxCumulativeContentItems,
        )
        Log.i(TAG, "UX restrictions 0x${restrictions.activeRestrictions.toString(16)} -> $limits")
        _limits.value = limits
    }

    private companion object {
        const val TAG = "SyncDeskUx"
    }
}
