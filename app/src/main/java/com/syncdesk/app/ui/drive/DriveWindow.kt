package com.syncdesk.app.ui.drive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.ui.theme.Design

/**
 * How wide the small window is, in design units at a height of 1210. The skeleton picks it from
 * the space it has; the screen lays out for it.
 */
enum class DriveWidth(val design: Dp) {
    /** A third of the app window, 561 (spec 1, assumed). */
    Third(Design.driveThird),

    /** Half of the app window, 841: larger type (title1Wide, the wide turns). */
    Half(Design.driveHalf),
}

/**
 * The small window while driving: waiting, tuning the cabin, marking what is missed, a refusal,
 * automatic tuning off, the takeover, and the talk with Gleo after a call.
 *
 * Contract with the skeleton (`SyncDeskScreen`): shown whenever the driver drives
 * ([com.syncdesk.app.domain.VehicleSignals.deskOpen] false). It fills a column [width] design
 * units wide and 1210 high; the skeleton draws the window ground and static glows behind it
 * (no breathing while driving). Content starts 216 from the top (the reserved strip) with 36 at
 * the sides and 40 at the bottom; "자동 조절 끄기" stays at the bottom. When the system gives the
 * app the whole window while driving, the skeleton still gives this screen a Half column at the
 * window's end. Text limits of [SyncDeskState.ux] apply; no overshooting curves while driving.
 */
@Composable
fun DriveWindow(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    width: DriveWidth,
    modifier: Modifier = Modifier,
) {
    DriveContent(
        state = state,
        onEvent = onEvent,
        width = width,
        modifier = modifier.fillMaxSize(),
    )
}
