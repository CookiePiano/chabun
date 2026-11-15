package com.syncdesk.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The materials of spec section 3. Glass is drawn as a translucent gradient over whatever is
 * behind it, with a gradient border, a soft shadow and a thin light along its top edge.
 *
 * Background blur: in this design glass only ever sits on the window ground and its two glows,
 * which are already smooth gradients, so sampling and blurring what is behind would not change
 * a pixel. The translucent fill is therefore drawn directly. Where the spec's blur would not be
 * available (before Android 12, no RenderEffect) the fill switches to the spec's more opaque
 * fallback (white 80 % by day, 12 % by night), as spec 3 asks.
 */
object Materials {
    /** Whether the platform could blur behind glass (RenderEffect, Android 12). */
    val blurAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Glass fill: top to bottom white 86 % -> 58 % by day, 13 % -> 6 % -> 4 % by night. */
    fun glassFill(night: Boolean): Brush = when {
        !blurAvailable -> Brush.verticalGradient(listOf(white(if (night) 0.12f else 0.80f), white(if (night) 0.12f else 0.80f)))
        night -> Brush.verticalGradient(0f to white(0.13f), 0.5f to white(0.06f), 1f to white(0.04f))
        else -> Brush.verticalGradient(listOf(white(0.86f), white(0.58f)))
    }

    /** Glass border, 1.5 wide, diagonal: white 100 -> 70 -> 90 % by day, 46 -> 7 -> 20 % by night. */
    fun glassBorder(night: Boolean): Brush = if (night) {
        Brush.linearGradient(0f to white(0.46f), 0.5f to white(0.07f), 1f to white(0.20f))
    } else {
        Brush.linearGradient(0f to white(1f), 0.5f to white(0.70f), 1f to white(0.90f))
    }

    val glassBorderWidth = 1.5.dp

    /** Glass shadow: 0, 24, blur 52, spread -20. */
    fun glassShadow(night: Boolean) = Shadow(
        radius = 52.dp,
        color = shadowColor(night),
        spread = (-20).dp,
        offset = DpOffset(0.dp, 24.dp),
    )

    /** The thin light along the top inner edge: 0, 1.5, white 70 % by day, 10 % by night. */
    fun topLight(night: Boolean, strength: Float = if (night) 0.10f else 0.70f) = Shadow(
        radius = 0.dp,
        color = white(strength),
        offset = DpOffset(0.dp, 1.5.dp),
    )

    /** The selected row: white 100 % -> 90 % by day, 20 % -> 10 % by night. */
    fun chipFill(night: Boolean): Brush =
        if (night) Brush.verticalGradient(listOf(white(0.20f), white(0.10f))) else Brush.verticalGradient(listOf(white(1f), white(0.90f)))

    /** The selected row's shadow: 0, 10, blur 24, spread -10. */
    fun chipShadow(night: Boolean) = Shadow(
        radius = 24.dp,
        color = shadowColor(night),
        spread = (-10).dp,
        offset = DpOffset(0.dp, 10.dp),
    )

    private fun shadowColor(night: Boolean) =
        if (night) Color(0f, 0f, 5f / 255f, 0.42f) else Color(41 / 255f, 51 / 255f, 115 / 255f, 0.16f)

    /** The accent button: `#12A090` -> `#0A7D73` -> `#086A61` by day, `#A5F5E6` -> `#6FE7D2` -> `#4FD3BD` by night. */
    fun accentFill(night: Boolean): Brush = if (night) {
        Brush.verticalGradient(listOf(Color(0xFFA5F5E6), Color(0xFF6FE7D2), Color(0xFF4FD3BD)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF12A090), Color(0xFF0A7D73), Color(0xFF086A61)))
    }

    /** The accent button's top light (white 30 % by day, 55 % by night) and its glow (0, 18, 44, -16). */
    fun accentTopLight(night: Boolean) = topLight(night, if (night) 0.55f else 0.30f)
    fun accentGlow(accent: Color) = Shadow(radius = 44.dp, color = accent, spread = (-16).dp, offset = DpOffset(0.dp, 18.dp))

    /** The Gleo icon's shadow: 0, 16, blur 28, `rgba(84,104,255,.42)`. */
    val gleoShadow = Shadow(radius = 28.dp, color = Color(84 / 255f, 104 / 255f, 1f, 0.42f), offset = DpOffset(0.dp, 16.dp))

    /** The dimming behind a bottom sheet, `rgba(3,6,18,.5)`. */
    val scrim = Color(3 / 255f, 6 / 255f, 18 / 255f, 0.5f)

    private fun white(alpha: Float) = Color.White.copy(alpha = alpha)
}

/**
 * The glass card material on [shape]: shadow, translucent fill, top light and gradient border.
 * Put it before padding; clip the content separately if it must not leave the shape.
 */
@Composable
fun Modifier.glass(shape: Shape, shadow: Boolean = true): Modifier {
    val night = SyncDesk.colors.isNight
    return this
        .then(if (shadow) Modifier.dropShadow(shape, Materials.glassShadow(night)) else Modifier)
        .clip(shape)
        .background(Materials.glassFill(night))
        .innerShadow(shape, Materials.topLight(night))
        .border(Materials.glassBorderWidth, Materials.glassBorder(night), shape)
}

/** The selected row of a list (received calls): brighter glass with a short shadow, no border. */
@Composable
fun Modifier.chip(shape: Shape): Modifier {
    val night = SyncDesk.colors.isNight
    return this
        .dropShadow(shape, Materials.chipShadow(night))
        .clip(shape)
        .background(Materials.chipFill(night))
}

/** The opaque raised surface (bottom sheet, status card): glass-strong with a glass-line border. */
@Composable
fun Modifier.glassStrong(shape: Shape): Modifier {
    val colors = SyncDesk.colors
    return this
        .clip(shape)
        .background(colors.glassStrong)
        .border(Materials.glassBorderWidth, colors.glassLine, shape)
}

/**
 * The window ground with the two background glows of spec 2: g1 1000 x 760 at (88 %, 4 %) and
 * g2 900 x 640 at (6 %, 104 %), both fading out at 62 %. With [breathing] the glows slowly swell
 * and fade (ease-in-out); never while driving, and never with reduced motion. The grain Figma
 * lays on top is left out (spec 2 allows it).
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, breathing: Boolean = false) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val alpha = if (breathing && motion.enabled) {
        val transition = rememberInfiniteTransition(label = "glow")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = GLOW_BREATH_LOW,
            animationSpec = InfiniteRepeatableSpec(tween(GLOW_BREATH_MILLIS, easing = Easings.Breathe), RepeatMode.Reverse),
            label = "glowAlpha",
        )
        value
    } else {
        1f
    }
    Box(
        modifier
            .background(colors.ground)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                ellipseGlow(colors.glow1, Offset(size.width * 0.88f, size.height * 0.04f), 1000.dp.toPx(), 760.dp.toPx(), 0.62f)
                ellipseGlow(colors.glow2, Offset(size.width * 0.06f, size.height * 1.04f), 900.dp.toPx(), 640.dp.toPx(), 0.62f)
            },
    )
}

/**
 * The always-dark car stage of office mode: a radial gradient centred at (50 %, 44 %), 640 x 560,
 * `#1B2B6B` -> `#0B1438` (48 %) -> `#050919`. With [consent], the first-use stage's gradient:
 * 900 x 480 at (74 %, 62 %), `#1B2B6B` -> `#0A1538` (50 %) -> `#030A1F`.
 */
fun Modifier.stageBackground(consent: Boolean = false): Modifier = drawBehind {
    val stage = Palette.stage
    val edge = Color(if (consent) stage.consentEdge else stage.edge)
    drawRect(edge)
    val center = if (consent) Offset(size.width * 0.74f, size.height * 0.62f) else Offset(size.width * 0.5f, size.height * 0.44f)
    val rx = (if (consent) 900 else 640).dp.toPx()
    val ry = (if (consent) 480 else 560).dp.toPx()
    val middleStop = if (consent) 0.5f else 0.48f
    val brush = Brush.radialGradient(
        0f to Color(stage.center),
        middleStop to Color(if (consent) stage.consentMiddle else stage.middle),
        1f to edge,
        center = center,
        radius = rx,
    )
    scale(1f, ry / rx, pivot = center) {
        drawCircle(brush, radius = rx, center = center)
    }
}

/** An elliptical radial gradient from [color] to transparent at [fade] of its radii. */
private fun DrawScope.ellipseGlow(color: Color, center: Offset, width: Float, height: Float, fade: Float) {
    val radius = width * fade
    val brush = Brush.radialGradient(
        0f to color,
        1f to color.copy(alpha = 0f),
        center = center,
        radius = radius,
    )
    scale(1f, height / width, pivot = center) {
        drawCircle(brush, radius = radius, center = center)
    }
}

private const val GLOW_BREATH_MILLIS = 4000
private const val GLOW_BREATH_LOW = 0.82f
