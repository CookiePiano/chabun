package com.syncdesk.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The line icons of the design, ported from `ic()` in `chabun.js` (several are Feather Icons,
 * MIT). All are drawn on a 24 grid with round caps and joins. Use with `Icon(icon.vector(), …)`
 * and a tint; the stroke width is in grid units (2 next to text, 2.2 for the microphone, 2.4 by
 * default as in the web mock-up).
 */
enum class LineIcon(internal vararg val paths: String) {
    Down("M6 9L12 15L18 9"),
    Up("M18 15L12 9L6 15"),
    Check("M20 6L9 17L4 12"),
    Minus("M5 12H19"),
    Plus("M12 5V19", "M5 12H19"),
    Turn("M9 14L4 9L9 4", "M20 20v-7a4 4 0 0 0-4-4H4"),
    Wind("M9.59 4.59A2 2 0 1 1 11 8H2m10.59 11.41A2 2 0 1 0 14 16H2m15.73-8.27A2.5 2.5 0 1 1 19.5 12H2"),
    Rain(
        "M16 13V21", "M8 13V21", "M12 15V23",
        "M20 16.58A5 5 0 0 0 18 7h-1.26A8 8 0 1 0 4 15.25",
    ),
    Laptop("M6 5H18A2 2 0 0 1 20 7V14A2 2 0 0 1 18 16H6A2 2 0 0 1 4 14V7A2 2 0 0 1 6 5Z", "M2 19.5H22"),
    Lock("M6 11H18A2 2 0 0 1 20 13V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V13A2 2 0 0 1 6 11Z", "M8 11V7a4 4 0 0 1 8 0v4"),
    Sun(
        "M16.5 12A4.5 4.5 0 1 1 7.5 12A4.5 4.5 0 1 1 16.5 12Z",
        "M12 2V4M12 20V22M2 12H4M20 12H22M4.9 4.9L6.3 6.3M17.7 17.7L19.1 19.1M4.9 19.1L6.3 17.7M17.7 6.3L19.1 4.9",
    ),
    Shade("M6 3H18A2 2 0 0 1 20 5V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V5A2 2 0 0 1 6 3Z", "M4 8H20M4 13H20"),
    Zap("M13 2L3 14H12L11 22L21 10H12L13 2Z"),
    Pause("M8 5V19M16 5V19"),
    Back("M1 4V10H7", "M3.51 15a9 9 0 1 0 2.13-9.36L1 10"),
    Chat(
        "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7" +
            "a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z",
    ),
    Note("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2V8H20", "M16 13H8M16 17H8"),
    Alert(
        "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
        "M12 9V13M12 17H12.01",
    ),
    Phone(
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67" +
            "A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6" +
            "l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
    ),
    Off("M18.36 6.64a9 9 0 1 1-12.73 0", "M12 2V12"),
    Mic("M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19V23M8 23H16"),

    /** The idle mark of the small window: two waves. */
    Mark("M3 9c3-3 6-3 9 0s6 3 9 0", "M3 15c3-3 6-3 9 0s6 3 9 0"),

    /** The three wind streams of the small window while tuning; see [windStreams] to animate them. */
    WindStreams(*WIND_STREAMS),
    ;

    /** This icon with strokes [strokeWidth] grid units wide. Cached. */
    fun vector(strokeWidth: Float = DEFAULT_STROKE): ImageVector =
        cache.getOrPut(this to strokeWidth) { build(name, paths.toList(), strokeWidth) }
}

/**
 * The three wind streams with each stream drawn to [ends] of its length (0..1), the left end
 * fixed: spec 8-2 "실내 조절" shortens them to 62 %. Not cached; build it from animated values.
 */
fun windStreams(ends: List<Float>, strokeWidth: Float = DEFAULT_STROKE): ImageVector =
    build("WindStreams", WIND_STREAMS.toList(), strokeWidth, ends)

private const val DEFAULT_STROKE = 2.4f

/**
 * The streams of `windx` in `chabun.js`, each reversed to start at the left edge, so trimming the
 * end shortens a stream from its curl and keeps the left end fixed.
 */
private val WIND_STREAMS = arrayOf(
    "M2 8H11A2 2 0 1 0 9.59 4.59",
    "M2 16H14A2 2 0 1 1 12.59 19.41",
    "M2 12H19.5A2.5 2.5 0 1 0 17.73 7.73",
)

private val cache = HashMap<Pair<LineIcon, Float>, ImageVector>()

private fun build(name: String, paths: List<String>, strokeWidth: Float, ends: List<Float>? = null): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEachIndexed { index, data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            trimPathEnd = ends?.getOrNull(index) ?: 1f,
        )
    }
    return builder.build()
}
