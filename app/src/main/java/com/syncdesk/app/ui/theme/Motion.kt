package com.syncdesk.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The eight curves of spec 8-1, as CSS cubic-bezier values. */
object Easings {
    /** 0.2, 0.8, 0.2, 1: most entrances and moves. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** 0.2, 0.85, 0.2, 1: a sheet coming up, bars growing. */
    val Sheet: Easing = CubicBezierEasing(0.2f, 0.85f, 0.2f, 1f)

    /** 0.16, 1, 0.3, 1: a quicker deceleration, an alternative to [Sheet]. */
    val Emphasis: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 0.65, 0, 0.15, 1: the small window widening on parking, the car driving in, the timeline line. */
    val Widen: Easing = CubicBezierEasing(0.65f, 0f, 0.15f, 1f)

    /** 0, 0, 0.58, 1: opacity changes and the press. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** 0.4, 0, 1, 1: elements on their way out. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** CSS ease-in-out: background glows, icon breathing, cross-fades. */
    val Breathe: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /**
     * 0.34, 1.4, 0.64, 1: overshoots. Only for the dots of the first-use timeline; never on the
     * screens shown while driving.
     */
    val Spring: Easing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
}

/** Durations of spec 8-1, in milliseconds. */
object Durations {
    /** A press: scale to [PRESS_SCALE] (review item 7). */
    const val PRESS = 150

    /** Something leaving (0.15 to 0.2 s), moving up [Distances.exitRise]. */
    const val EXIT = 180

    /** Something arriving (0.28 to 0.45 s), rising [Distances.enterRise]. */
    const val ENTER = 280
    const val ENTER_LONG = 450

    /** The bottom sheet: up in 0.55 s, down in 0.45 s; the scrim fades in 0.45 s. */
    const val SHEET_IN = 550
    const val SHEET_OUT = 450
    const val SCRIM = 450

    /** The stagger between items, only in scenes seen once (entrances), never on frequent presses. */
    const val STAGGER_SHORT = 50
    const val STAGGER_LONG = 120

    /** Parking: the small window's content fades (0.25 s), then the window widens (0.9 s). */
    const val PARK_FADE = 250
    const val PARK_WIDEN = 900

    /** With reduced motion only a cross-fade of this length is left (spec 8-4). */
    const val REDUCED_FADE = 200

    const val PRESS_SCALE = 0.96f
}

/** Distances of spec 8-1. */
object Distances {
    val exitRise: Dp = 8.dp
    val enterRise: Dp = 28.dp
    val enterRiseShort: Dp = 8.dp
}

/**
 * Motion for the whole app. [reduced] is true when the system's animator duration scale is 0 or
 * the user asked for less motion: then nothing moves or scales, only a 200 ms fade is left, and
 * voice bars and breathing stand still (spec 8-4).
 */
@Immutable
class SyncDeskMotion(val reduced: Boolean) {
    /** Whether movement, scaling and continuous animation (voice bars, breathing) are allowed. */
    val enabled: Boolean get() = !reduced

    /** [millis] with [easing], or the reduced-motion fade. Use for opacity. */
    fun <T> fade(millis: Int = Durations.ENTER, easing: Easing = Easings.Decelerate, delay: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) tween(Durations.REDUCED_FADE) else tween(millis, delay, easing)

    /** [millis] with [easing] for a move or a scale, or no movement at all with reduced motion. */
    fun <T> move(millis: Int = Durations.ENTER, easing: Easing = Easings.Standard, delay: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(millis, delay, easing)

    /** Coming in from [rise] below, fading in. With reduced motion only the fade. */
    fun enter(rise: Dp = Distances.enterRise, millis: Int = Durations.ENTER, delay: Int = 0, density: Float): EnterTransition {
        val fade = fadeIn(fade(millis, Easings.Decelerate, delay))
        return if (reduced) fade else fade + slideInVertically(tween(millis, delay, Easings.Standard)) { (rise.value * density).toInt() }
    }

    /** Going out [rise] upwards, fading out. With reduced motion only the fade. */
    fun exit(rise: Dp = Distances.exitRise, millis: Int = Durations.EXIT, density: Float): ExitTransition {
        val fade = fadeOut(fade(millis, Easings.Exit))
        return if (reduced) fade else fade + slideOutVertically(tween(millis, 0, Easings.Exit)) { -(rise.value * density).toInt() }
    }

    /** The bottom sheet's slide up, and down. Interruptible when used with animate*AsState or Animatable. */
    fun <T> sheetIn(): AnimationSpec<T> = if (reduced) snap() else tween(Durations.SHEET_IN, 0, Easings.Sheet)
    fun <T> sheetOut(): AnimationSpec<T> = if (reduced) snap() else tween(Durations.SHEET_OUT, 0, Easings.Sheet)

    // Durations the screens used before the redesign. Screen agents move off them.
    @Deprecated("Use fade()/move() or Durations")
    val settle: Int get() = if (reduced) 0 else 280

    @Deprecated("Use fade()/move() or Durations")
    val swap: Int get() = if (reduced) 0 else 220
}

/** The press of every button: scale 0.96 in 150 ms with the decelerate curve (review item 7). */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val motion = SyncDesk.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion.enabled) Durations.PRESS_SCALE else 1f,
        animationSpec = if (motion.enabled) tween(Durations.PRESS, easing = Easings.Decelerate) else snap(),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
