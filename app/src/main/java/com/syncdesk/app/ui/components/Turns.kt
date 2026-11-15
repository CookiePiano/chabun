package com.syncdesk.app.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncdesk.app.R
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import kotlin.random.Random

/**
 * Gleo's voice: [count] round bars that move between 35 % and 100 % of [height] every 0.18 s
 * while [active] (spec 8-3). They stand still, at a fixed pattern, when not active and with
 * reduced motion. Coloured with [color] (gleo by default).
 */
@Composable
fun VoiceBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = SyncDesk.colors.gleo,
    height: Dp = 38.dp,
    barWidth: Dp = 7.dp,
    gap: Dp = 6.dp,
    count: Int = 5,
    periodMillis: Int = 180,
) {
    val moving = active && SyncDesk.motion.enabled
    val targets = remember(count) { mutableStateListOf<Float>().apply { repeat(count) { add(RESTING[it % RESTING.size]) } } }
    LaunchedEffect(moving, count) {
        if (!moving) {
            repeat(count) { targets[it] = RESTING[it % RESTING.size] }
            return@LaunchedEffect
        }
        while (true) {
            repeat(count) { targets[it] = 0.35f + Random.nextFloat() * 0.65f }
            kotlinx.coroutines.delay(periodMillis.toLong())
        }
    }
    val levels = List(count) { index ->
        animateFloatAsState(targets[index], tween(periodMillis, easing = Easings.Breathe), label = "voice$index").value
    }
    Canvas(modifier.size(width = barWidth * count + gap * (count - 1), height = height)) {
        val w = barWidth.toPx()
        val step = w + gap.toPx()
        levels.forEachIndexed { index, level ->
            val h = (level * size.height).coerceAtLeast(w)
            val x = index * step + w / 2
            drawLine(color, Offset(x, (size.height - h) / 2 + w / 2), Offset(x, (size.height + h) / 2 - w / 2), w, StrokeCap.Round)
        }
    }
}

private val RESTING = listOf(0.55f, 0.9f, 0.7f, 1f, 0.6f)

/**
 * The conversation area of the small window (spec 5, 8-3): turns stacked from the bottom, 30
 * apart, the top 110 fading out. New turns are announced politely (spec 10). The caller keeps at
 * most the current turn and the two before it.
 */
@Composable
fun Conversation(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 110.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                    size = size.copy(height = fade),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(30.dp), content = content)
    }
}

/**
 * One of Gleo's turns: the name row (icon 56, "Gleo", an optional tag such as "못 들은 부분",
 * and the voice bars while [speaking]) over the sentence (36 B; 44 B at half width). An [old]
 * turn is smaller (icon 36, 28 R; 32 R at half width) and entirely in muted colour at full
 * opacity (review item 2).
 */
@Composable
fun GleoTurn(
    text: String,
    modifier: Modifier = Modifier,
    old: Boolean = false,
    tag: String? = null,
    speaking: Boolean = false,
    wide: Boolean = false,
) {
    val colors = SyncDesk.colors
    val type = SyncDesk.type
    val bodyStyle = when {
        old && wide -> type.captionLarge.copy(fontSize = 32.sp, lineHeight = 46.sp)
        old -> type.captionLarge
        wide -> type.bodyLarge.copy(fontSize = 44.sp, lineHeight = 62.sp)
        else -> type.bodyLarge
    }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GleoIcon(if (old) 36.dp else 56.dp)
            Text(
                text = stringResource(R.string.gleo_name),
                style = (if (old) type.small else type.caption).copy(fontWeight = FontWeight.Bold),
                color = if (old) colors.muted else colors.ink,
            )
            if (tag != null && !old) Tag(tag, kind = TagKind.Missed)
            if (!old) VoiceBars(active = speaking, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(if (old) 8.dp else 12.dp))
        Text(text = text, style = bodyStyle, color = if (old) colors.muted else colors.ink)
    }
}

/**
 * One of the driver's turns, against the end: a glass pill (rounded 40 40 10 40) with the
 * microphone, 32 B (38 at half width). While [listening] nothing has been heard yet: the pill
 * says "듣고 있습니다" in muted and the microphone breathes (1 to 1.12, 1 s). An [old] turn is
 * smaller (26 R, muted) without the microphone.
 */
@Composable
fun DriverTurn(
    text: String,
    modifier: Modifier = Modifier,
    old: Boolean = false,
    listening: Boolean = false,
    wide: Boolean = false,
) {
    val colors = SyncDesk.colors
    val type = SyncDesk.type
    val shown = if (listening && !old) stringResource(R.string.turn_listening) else text
    val style = when {
        old && wide -> type.caption.copy(fontSize = 30.sp, lineHeight = 42.sp)
        old -> type.caption
        listening && wide -> type.bodyLargeRegular.copy(fontSize = 38.sp, lineHeight = 54.sp)
        listening -> type.button.copy(fontWeight = FontWeight.Normal, lineHeight = 46.sp)
        wide -> type.button.copy(fontSize = 38.sp, lineHeight = 54.sp)
        else -> type.button.copy(lineHeight = 46.sp)
    }
    // Keeps the pill against the end and at most 92 % of the width.
    Layout(
        content = {
            Row(
                modifier = Modifier
                    .glass(if (old) SyncDeskShapes.driverTurnOld else SyncDeskShapes.driverTurn, shadow = false)
                    .padding(
                        start = if (old) 24.dp else 16.dp,
                        end = if (old) 24.dp else 30.dp,
                        top = if (old) 10.dp else 16.dp,
                        bottom = if (old) 10.dp else 16.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!old) Microphone(breathing = listening)
                Text(text = shown, style = style, color = if (old || listening) colors.muted else colors.ink)
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth * 0.92f).toInt()))
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(constraints.maxWidth - placeable.width, 0)
        }
    }
}

/** The 56 microphone of a driver's turn; its ring breathes while the car listens. */
@Composable
private fun Microphone(breathing: Boolean) {
    val colors = SyncDesk.colors
    val moving = breathing && SyncDesk.motion.enabled
    val scale = if (moving) {
        val transition = rememberInfiniteTransition(label = "mic")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = InfiniteRepeatableSpec(tween(500, easing = Easings.Breathe), RepeatMode.Reverse),
            label = "micScale",
        ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(SyncDeskShapes.circle)
            .background(colors.accent.copy(alpha = 0.12f))
            .border(3.dp, colors.accent.copy(alpha = if (breathing) 0.7f else 0f), SyncDeskShapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LineIcon.Mic.vector(2.2f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
    }
}
