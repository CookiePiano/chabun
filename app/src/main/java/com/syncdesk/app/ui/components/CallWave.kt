package com.syncdesk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.SyncDesk
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * A stretch of the call the driver probably missed, as fractions of the call's length, with the
 * short reason shown under it ("좌회전 중", "실내 소음", "폭우") and its icon.
 */
@Immutable
data class MissedStretch(val from: Float, val to: Float, val label: String = "", val icon: LineIcon? = null)

/**
 * "통화 띠" (spec 6): the call drawn as bars 6 wide with round ends. Bars inside a missed stretch
 * are miss-bar coloured over a miss-tint band that reaches 8 above and below (rounded 16); under
 * the bars, the start and end times and each stretch's reason. TalkBack reads the whole thing as
 * one picture: "통화 흐름. 못 들은 구간 N곳".
 *
 * [heights] are the bars' heights from 0 to 1 (see [waveHeights]). [grow] gives each bar's
 * entrance progress (0.15 to 1 in spec 8-2) by index; leave it at 1 when nothing animates.
 * Without [startLabel] the label row is left out (the empty screen's quiet wave).
 */
@Composable
fun CallWave(
    heights: List<Float>,
    missed: List<MissedStretch>,
    modifier: Modifier = Modifier,
    barHeight: Dp = Design.waveHeight,
    barWidth: Dp = Design.waveBarWidth,
    startLabel: String? = null,
    endLabel: String? = null,
    grow: (Int) -> Float = { 1f },
) {
    val colors = SyncDesk.colors
    val description = if (missed.isEmpty()) {
        stringResource(R.string.call_wave_description_none)
    } else {
        pluralStringResource(R.plurals.call_wave_description, missed.size, missed.size)
    }
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            val band = 8.dp.toPx()
            val bandRadius = CornerRadius(16.dp.toPx())
            missed.forEach { stretch ->
                val left = (stretch.from - BAND_PAD) * size.width
                val right = (stretch.to + BAND_PAD) * size.width
                drawRoundRect(
                    color = colors.missTint,
                    topLeft = Offset(left, -band),
                    size = Size(right - left, size.height + band * 2),
                    cornerRadius = bandRadius,
                )
            }
            val count = heights.size
            if (count == 0) return@Canvas
            val width = barWidth.toPx()
            val step = if (count > 1) (size.width - width) / (count - 1) else 0f
            heights.forEachIndexed { index, value ->
                val position = if (count > 1) index / (count - 1f) else 0f
                val isMissed = missed.any { position >= it.from && position <= it.to }
                val height = (value.coerceIn(0f, 1f) * size.height * grow(index).coerceIn(0f, 1f)).coerceAtLeast(width)
                val x = index * step + width / 2
                drawLine(
                    color = if (isMissed) colors.missBar else colors.wave,
                    start = Offset(x, (size.height - height) / 2 + width / 2),
                    end = Offset(x, (size.height + height) / 2 - width / 2),
                    strokeWidth = width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        if (startLabel != null) {
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                val labelStyle = SyncDesk.type.small.copy(lineHeight = SyncDesk.type.small.fontSize * (40f / 24f))
                Text(text = startLabel, style = labelStyle, color = colors.muted, modifier = Modifier.align(Alignment.CenterStart))
                if (endLabel != null) {
                    Text(
                        text = endLabel,
                        style = labelStyle,
                        color = colors.muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                missed.forEach { stretch ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = maxWidth * (stretch.from - BAND_PAD).coerceAtLeast(0f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (stretch.icon != null) {
                            Icon(stretch.icon.vector(), contentDescription = null, tint = colors.missInk, modifier = Modifier.size(26.dp))
                            Box(Modifier.width(8.dp))
                        }
                        Text(text = stretch.label, style = SyncDesk.type.smallStrong, color = colors.missInk, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** How far the miss-tint band reaches past a stretch on each side, as a fraction of the width. */
private const val BAND_PAD = 0.006f

/**
 * Bar heights for a call, ported from `bars()` in `chabun.js`: the same [seed] always gives the
 * same wave. Heights are 0.1 to 1. 170 bars on the parked screen, 56 in a consent card.
 */
fun waveHeights(seed: Int, count: Int): List<Float> {
    var state = seed.toLong() * 131 + 7
    fun random(): Double {
        state = (state * 9301 + 49297) % 233280
        return state / 233280.0
    }
    return List(count) { index ->
        val position = if (count > 1) index / (count - 1.0) else 0.0
        val envelope = 0.3 + 0.7 * abs(sin(position * 8.1 + seed))
        max(0.1, envelope * (0.4 + 0.6 * random())).toFloat()
    }
}
