package com.syncdesk.app

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.syncdesk.app.runtime.SyncDeskRuntime
import com.syncdesk.app.runtime.SyncDeskService
import com.syncdesk.app.ui.SyncDeskScreen
import com.syncdesk.app.ui.area
import com.syncdesk.app.ui.theme.SyncDeskTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as SyncDeskApp).container

    /** Lives as long as the process, not this screen: it goes on working while the screen is covered. */
    private val runtime: SyncDeskRuntime get() = container.runtime

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SyncDeskTheme {
                val state by runtime.state.collectAsStateWithLifecycle()
                val link by runtime.link.collectAsStateWithLifecycle()
                SyncDeskScreen(
                    state = state,
                    materials = state.meeting.materials,
                    onEvent = runtime::onEvent,
                    demoControls = container.demoControls,
                    link = link,
                )
                // Parked, office mode and the first-use notice are laid out for the whole app window.
                val fullWindow = state.area().fullWindow
                LaunchedEffect(fullWindow) { requestWideWindow(fullWindow) }
                LaunchedEffect(state.consented) { keepWorkingBehindOtherApps() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        keepWorkingBehindOtherApps()
    }

    /**
     * Starts the background service if it is not running yet: each time this screen comes forward,
     * and the moment the driver agrees to the app. From then on it also starts with the vehicle.
     * Until they have agreed the app does nothing, so nothing needs to keep running.
     */
    private fun keepWorkingBehindOtherApps() {
        if (runtime.state.value.consented) SyncDeskService.start(this)
    }

    /**
     * Day and night changes are handled without restarting the Activity (see the manifest), so the
     * system bar icons are matched to the new theme here.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }

    /**
     * On Pleos Connect a landscape Activity spans both app windows. The full-window screens
     * (parked, office mode, first use) ask for that, and give it back when the driver drives on.
     */
    private fun requestWideWindow(wide: Boolean) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        requestedOrientation = if (wide) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
