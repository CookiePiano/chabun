package com.syncdesk.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Sizes of spec section 1. The emulator runs at 160 dpi, so the design's pixels are dp.
 */
object Design {
    /** The app window when parked, in office mode and on first use. */
    val appWindow = DpSize(1682.dp, 1210.dp)

    /** The small window while driving: a third, or half, of the app window's width (assumed, spec 11). */
    val driveThird: Dp = 561.dp
    val driveHalf: Dp = 841.dp
    val driveHeight: Dp = 1210.dp

    /**
     * The top-left area of the app window the side camera covers (assumed, spec 11). No text or
     * button may sit in it; pictures may.
     */
    val reservedZone = DpSize(480.dp, 200.dp)

    /** Outer margins: start 72 for the parked body text, otherwise 48; end 48; top and bottom 36. */
    val marginStartParked: Dp = 72.dp
    val marginStart: Dp = 48.dp
    val marginEnd: Dp = 48.dp
    val marginTop: Dp = 36.dp
    val marginBottom: Dp = 36.dp

    /** Inside the small window: top 216 (below the reserved strip), sides 36, bottom 40. */
    val driveInsetTop: Dp = 216.dp
    val driveInsetSide: Dp = 36.dp
    val driveInsetBottom: Dp = 40.dp

    /** Touch: never below 76; buttons 112 high, list rows 104, the sheet's close button 84. */
    val minTouch: Dp = 76.dp
    val buttonHeight: Dp = 112.dp
    val listRowHeight: Dp = 104.dp
    val sheetClose: Dp = 84.dp
    val segmentHeight: Dp = 88.dp
    val stepRowHeight: Dp = 98.dp
    val stepNumber: Dp = 56.dp
    val avatar: Dp = 64.dp

    /** The bottom sheet: 72 in from both sides, 900 high (940 for settings). */
    val sheetInset: Dp = 72.dp
    val sheetHeight: Dp = 900.dp

    /** The bars of the call wave on the parked screen: 104 high, 6 wide. */
    val waveHeight: Dp = 104.dp
    val waveBarWidth: Dp = 6.dp
}

/** How much the design is scaled down to fit the window; 1 on the emulator. */
val LocalDesignScale = staticCompositionLocalOf { 1f }

/**
 * Lays [content] out in design units. When the window is smaller than [designWidth] x
 * [designHeight], the density is lowered so the design fits whole: the content still sees at
 * least the design size in dp and is drawn smaller, as on a smaller screen. A bigger window is
 * not scaled up; the content gets the extra room. A null [designWidth] fits the height only.
 *
 * Screens inside can therefore use the coordinates of the Figma frames and `chabun.css` as they are.
 */
@Composable
fun DesignFrame(
    designHeight: Dp,
    modifier: Modifier = Modifier,
    designWidth: Dp? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val byHeight = maxHeight / designHeight
        val byWidth = designWidth?.let { maxWidth / it } ?: Float.MAX_VALUE
        val scale = minOf(byHeight, byWidth, 1f).coerceAtLeast(MIN_SCALE)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalDesignScale provides scale * LocalDesignScale.current,
        ) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Below this the design is not shrunk further; the content then gets less room than designed. */
private const val MIN_SCALE = 0.25f
