package com.syncdesk.app.ui

/*
 * The app's skeleton. Everything on screen follows from SyncDeskState; there is no navigation,
 * because the car decides what is relevant. Four areas replace one another, each drawn by ONE
 * entry composable that a screen agent owns:
 *
 *   Area        When                                          Window                 Entry (owner file)
 *   Drive       the driver drives (signals.deskOpen false)    small window,          DriveWindow(state, onEvent, width, modifier)
 *                                                             561 or 841 x 1210      ui/drive/DriveWindow.kt
 *   Onboarding  state.asksConsent                             full, 1682 x 1210      OnboardingScreen(onEvent, modifier)
 *                                                                                    ui/onboarding/OnboardingScreen.kt
 *   Office      office.phase != Off, or office mode asked     full, 1682 x 1210      OfficeScreen(state, onEvent, onClose, modifier)
 *               for while setup is forbidden (locked)                                ui/office/OfficeScreen.kt
 *   Parked      otherwise (parked or self-driving)            full, 1682 x 1210      ParkedScreen(state, materials, onEvent,
 *                                                                                      onOpenOffice, today, modifier)
 *                                                                                    ui/parked/ParkedScreen.kt
 *
 * Rules for every entry:
 * - Lay out in design units (dp = Figma px). DesignFrame shrinks the density when the window is
 *   smaller than the design, so the coordinates of the Figma frames and chabun.css hold as they are.
 * - The skeleton draws the window ground and the two glows (breathing in full areas, still while
 *   driving); entries draw on top with transparent backgrounds.
 * - NO TEXT OR BUTTON in the top-left 480 x 200 of the app window (Design.reservedZone): the side
 *   camera covers it when the turn signal is on. Pictures may reach into it.
 * - An entry is composed afresh each time its area appears, after the skeleton's own transition
 *   (for parking, after the window has widened), so a one-time entrance can start in a
 *   LaunchedEffect. Respect SyncDesk.motion.reduced.
 * - Texts go in the agent's own values/strings_<area>.xml and values-en/strings_<area>.xml.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.syncdesk.app.R
import com.syncdesk.app.data.DemoControls
import com.syncdesk.app.data.VehicleLink
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.ui.components.ButtonKind
import com.syncdesk.app.ui.components.RoundIconButton
import com.syncdesk.app.ui.demo.DemoPanel
import com.syncdesk.app.ui.drive.DriveWidth
import com.syncdesk.app.ui.drive.DriveWindow
import com.syncdesk.app.ui.office.OfficeScreen
import com.syncdesk.app.ui.onboarding.OnboardingScreen
import com.syncdesk.app.ui.parked.ParkedScreen
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.DesignFrame
import com.syncdesk.app.ui.theme.Durations
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.GlowBackground
import com.syncdesk.app.ui.theme.SyncDesk
import java.time.LocalDate

/** The four areas of the app; see the table at the top of this file. */
enum class AppArea(val fullWindow: Boolean) { Drive(false), Onboarding(true), Office(true), Parked(true) }

/**
 * Which area [this] state shows. [officeLocked] is the skeleton's note that the driver asked for
 * office mode while setup was forbidden, so the locked office screen is shown.
 */
fun SyncDeskState.area(officeLocked: Boolean = false): AppArea = when {
    !signals.deskOpen -> AppArea.Drive
    asksConsent -> AppArea.Onboarding
    office.phase != OfficePhase.Off || officeLocked -> AppArea.Office
    else -> AppArea.Parked
}

/**
 * The whole app. The demo controls, when open, sit below the app window, which is then shrunk to
 * fit above them, so nothing of a screen is ever covered.
 */
@Composable
fun SyncDeskScreen(
    state: SyncDeskState,
    materials: List<MeetingMaterial>,
    onEvent: (SyncDeskEvent) -> Unit,
    modifier: Modifier = Modifier,
    demoControls: DemoControls? = null,
    link: VehicleLink = VehicleLink(),
    demoInitiallyOpen: Boolean = false,
    /** The day the call memos are dated against ("오늘", "어제"). */
    today: LocalDate = LocalDate.now(),
) {
    var demoOpen by rememberSaveable { mutableStateOf(demoInitiallyOpen) }
    var officeLocked by rememberSaveable { mutableStateOf(false) }
    // The locked office screen is left once office mode really starts, or the desk closes.
    LaunchedEffect(state.signals.deskOpen, state.office.phase, state.asksConsent) {
        if (!state.signals.deskOpen || state.office.phase != OfficePhase.Off || state.asksConsent) officeLocked = false
    }
    val onOpenOffice = {
        if (state.ux.noSetup) officeLocked = true else onEvent(SyncDeskEvent.OfficeRequested)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(SyncDesk.colors.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppWindow(
            state = state,
            target = state.area(officeLocked),
            materials = materials,
            onEvent = onEvent,
            onOpenOffice = onOpenOffice,
            onCloseOffice = { officeLocked = false },
            today = today,
            demoToggle = if (demoControls != null) {
                { DemoToggle(open = demoOpen, onClick = { demoOpen = !demoOpen }) }
            } else {
                null
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        if (demoOpen && demoControls != null) {
            DemoPanel(state = state, link = link, controls = demoControls)
        }
    }
}

/**
 * The app window: the area [target] asks for, in design units, over the ground and glows. Areas
 * replace one another with a short cross-fade. Parking from a small window that does not fill
 * the app's window plays spec 8-2 "주차했을 때": the small window's content fades (0.25 s), its
 * ground widens to the whole window (0.9 s, widening curve), then the parked screen comes in.
 * When the system resizes the window instead, only the screen's own entrance is seen.
 */
@Composable
private fun AppWindow(
    state: SyncDeskState,
    target: AppArea,
    materials: List<MeetingMaterial>,
    onEvent: (SyncDeskEvent) -> Unit,
    onOpenOffice: () -> Unit,
    onCloseOffice: () -> Unit,
    today: LocalDate,
    demoToggle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val motion = SyncDesk.motion
    var shown by remember { mutableStateOf(target) }
    var contentShown by remember { mutableStateOf(true) }
    val widen = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    // Whether the small window is narrower than the app's window right now: then parking widens it.
    val driveColumnNarrower = remember { BooleanHolder() }
    // An area on its way out keeps showing the state it had, not the state that replaced it.
    val lastState = remember { mutableMapOf<AppArea, SyncDeskState>() }
    if (target == shown) lastState[shown] = state

    LaunchedEffect(target) {
        widen.snapTo(1f)
        contentShown = true
        if (target == shown) {
            alpha.animateTo(1f, motion.fade(Durations.ENTER))
            return@LaunchedEffect
        }
        val parking = shown == AppArea.Drive && target.fullWindow && driveColumnNarrower.value && motion.enabled
        if (motion.reduced) {
            alpha.snapTo(0f)
            shown = target
            alpha.animateTo(1f, motion.fade())
            return@LaunchedEffect
        }
        alpha.animateTo(0f, tween(if (parking) Durations.PARK_FADE else Durations.EXIT, easing = Easings.Exit))
        shown = target
        if (parking) {
            contentShown = false
            widen.snapTo(0f)
            alpha.snapTo(1f)
            widen.animateTo(1f, tween(Durations.PARK_WIDEN, easing = Easings.Widen))
            contentShown = true
        } else {
            alpha.animateTo(1f, tween(Durations.ENTER, easing = Easings.Decelerate))
        }
    }

    DesignFrame(
        designHeight = Design.driveHeight,
        designWidth = if (shown.fullWindow) Design.appWindow.width else null,
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val window = maxWidth
            val driveWidth = when {
                window < THIRD_MAX -> DriveWidth.Third
                else -> DriveWidth.Half
            }
            // A small window never grows past half the app window; beyond that it keeps to the end.
            val driveColumn = if (window <= HALF_MAX) window else Design.driveHalf
            driveColumnNarrower.value = driveColumn < window - 1.dp
            val groundWidth = if (shown.fullWindow) lerp(driveColumn, window, widen.value) else driveColumn

            GlowBackground(
                breathing = shown.fullWindow && widen.value >= 1f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(groundWidth)
                    .fillMaxHeight(),
            )
            if (contentShown) {
                val shownState = if (shown == target) state else lastState[shown] ?: state
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha.value },
                ) {
                    when (shown) {
                        AppArea.Drive -> DriveWindow(
                            state = shownState,
                            onEvent = onEvent,
                            width = driveWidth,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .width(driveColumn)
                                .fillMaxHeight(),
                        )
                        AppArea.Onboarding -> OnboardingScreen(onEvent = onEvent)
                        AppArea.Office -> OfficeScreen(state = shownState, onEvent = onEvent, onClose = onCloseOffice)
                        AppArea.Parked -> ParkedScreen(
                            state = shownState,
                            materials = materials,
                            onEvent = onEvent,
                            onOpenOffice = onOpenOffice,
                            today = today,
                        )
                    }
                }
            }
            if (demoToggle != null) {
                Box(
                    if (shown.fullWindow) {
                        // Just right of the reserved zone, clear of every screen's content.
                        Modifier.offset(x = Design.reservedZone.width + 20.dp, y = Design.marginTop)
                    } else {
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = Design.marginTop, end = Design.driveInsetSide)
                    },
                ) {
                    demoToggle()
                }
            }
        }
    }
}

/** Opens and closes the demo controls below the app. */
@Composable
private fun DemoToggle(open: Boolean, onClick: () -> Unit) {
    RoundIconButton(
        icon = ImageVector.vectorResource(R.drawable.ic_tune),
        description = stringResource(if (open) R.string.demo_close else R.string.demo_open),
        onClick = onClick,
        size = Design.minTouch,
        iconSize = 36.dp,
        kind = if (open) ButtonKind.Solid else ButtonKind.Glass,
    )
}

/** A value written during layout and read by the transition, without causing recomposition. */
private class BooleanHolder(var value: Boolean = false)

/** Up to this width the small window is a third of the app window; up to [HALF_MAX], half. */
private val THIRD_MAX = 700.dp
private val HALF_MAX = 1100.dp
