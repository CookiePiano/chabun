package com.syncdesk.app.ui.theme

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Colour roles of the "밤공기" design as plain ARGB values, so contrast can be checked in a JVM
 * unit test. Source: `docs/design/spec.md` section 2 and `chabun.css` lines 37-38. Translucent
 * roles keep their alpha; [Palette.over] composites them on what they sit on before a contrast check.
 */
data class PaletteTokens(
    /** Window background. */
    val ground: Long,
    /** Body text. */
    val ink: Long,
    /** Secondary text, and earlier turns of the conversation (review item 2: colour, not opacity). */
    val muted: Long,
    /** The one emphasis colour (teal): progress, the accent button, "내 약속". */
    val accent: Long,
    /** Text and icons on [accent]. */
    val onAccent: Long,
    /** Glass card fill, as one flat colour (the material draws it as a gradient). */
    val glass: Long,
    /** Glass card border and hairlines between rows. */
    val glassLine: Long,
    /** Opaque raised surface: the bottom sheet and the status card. */
    val glassStrong: Long,
    /** The selected row of a list (received calls). */
    val chipOn: Long,
    /** The dark (day) or light (night) button, and the selected Work | Meeting segment. */
    val solid: Long,
    val onSolid: Long,
    /** Bars of a missed stretch in the call wave. */
    val missBar: Long,
    /** The band behind a missed stretch, and the background of missed tags. */
    val missTint: Long,
    /** Text of what was missed. */
    val missInk: Long,
    /** Underline of a missed sentence in the full transcript. */
    val marker: Long,
    /** Ordinary bars of the call wave. */
    val wave: Long,
    /** Gleo's name and voice bars. */
    val gleo: Long,
    /** The two background glows. */
    val glow1: Long,
    val glow2: Long,
    /** Stand-ins for the navigation app beside the small window (design mock-ups only). */
    val nav: Long,
    val road: Long,
    val route: Long,
)

/**
 * The car stage of office mode and the first-use screen. It is dark by day as well (spec 2,
 * "낮과 밤이 같은 부분").
 */
data class StageTokens(
    /** Radial gradient of the office stage, centre to edge: `#1B2B6B` -> `#0B1438` -> `#050919`. */
    val center: Long,
    val middle: Long,
    val edge: Long,
    /** The first-use stage uses a slightly different gradient: `#1B2B6B` -> `#0A1538` -> `#030A1F`. */
    val consentMiddle: Long,
    val consentEdge: Long,
    /** Stage border, white 10 %. */
    val line: Long,
    /** Accent inside the stage, the night accent in both themes. */
    val accent: Long,
    val ink: Long,
    val muted: Long,
    /** The warm reading light drawn over the car in office mode. */
    val warmLight: Long,
)

object Palette {
    val day = PaletteTokens(
        ground = 0xFFE9EDF8,
        ink = 0xFF0F1B3D,
        muted = 0xFF4A5880,
        accent = 0xFF0A7D73,
        onAccent = 0xFFFFFFFF,
        glass = white(0.66f),
        glassLine = white(0.95f),
        glassStrong = 0xFFF7F9FE,
        chipOn = white(0.95f),
        solid = 0xFF0F1B3D,
        onSolid = 0xFFFFFFFF,
        missBar = 0xFFE4502A,
        missTint = alpha(0xFFE4502A, 0.13f),
        missInk = 0xFFB5361A,
        marker = alpha(0xFFE4502A, 0.28f),
        wave = alpha(0xFF0F1B3D, 0.26f),
        gleo = 0xFF4A5CF0,
        glow1 = rgba(150, 140, 255, 0.55f),
        glow2 = rgba(110, 225, 205, 0.55f),
        nav = 0xFFDDE4EC,
        road = 0xFFFFFFFF,
        route = 0xFF3B7BFF,
    )

    val night = PaletteTokens(
        ground = 0xFF060918,
        ink = 0xFFF3F6FF,
        muted = 0xFFA9B3D3,
        accent = 0xFF6FE7D2,
        onAccent = 0xFF04201C,
        glass = white(0.07f),
        glassLine = white(0.14f),
        glassStrong = 0xFF11162B,
        chipOn = white(0.14f),
        solid = 0xFFF3F6FF,
        onSolid = 0xFF0A1026,
        missBar = 0xFFFF9A76,
        missTint = alpha(0xFFFF9A76, 0.16f),
        missInk = 0xFFFFB59A,
        marker = alpha(0xFFFF9A76, 0.36f),
        wave = white(0.26f),
        gleo = 0xFF9AA8FF,
        glow1 = rgba(70, 78, 225, 0.55f),
        glow2 = rgba(14, 165, 155, 0.38f),
        nav = 0xFF141B29,
        road = 0xFF263147,
        route = 0xFF4D8DFF,
    )

    val stage = StageTokens(
        center = 0xFF1B2B6B,
        middle = 0xFF0B1438,
        edge = 0xFF050919,
        consentMiddle = 0xFF0A1538,
        consentEdge = 0xFF030A1F,
        line = white(0.10f),
        accent = 0xFF6FE7D2,
        ink = 0xFFF3F6FF,
        muted = 0xFFA9B3D3,
        warmLight = rgba(255, 216, 150, 0.5f),
    )

    /** WCAG 2 contrast ratio between two opaque ARGB colours, from 1.0 to 21.0. */
    fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** [top], with its alpha, composited over the opaque [bottom]: the colour the eye sees. */
    fun over(top: Long, bottom: Long): Long {
        val a = ((top shr 24) and 0xFF) / 255.0
        fun channel(shift: Int): Long {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return (t * a + b * (1 - a)).roundToLong().coerceIn(0, 255)
        }
        return (0xFFL shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun alpha(argb: Long, alpha: Float): Long =
        ((alpha * 255).roundToLong() shl 24) or (argb and 0xFFFFFF)

    private fun white(alpha: Float): Long = alpha(0xFFFFFFFF, alpha)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Float): Long =
        alpha((0xFFL shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong(), alpha)
}
