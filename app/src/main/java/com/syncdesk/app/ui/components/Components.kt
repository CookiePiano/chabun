package com.syncdesk.app.ui.components

// The components of the screens before the redesign. The screens still placed in stage 1 use
// them; the new components are in the other files of this package. Removed in the clean-up stage
// once no screen uses them.

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.ui.theme.SyncDesk
import kotlin.math.floor

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(SyncDesk.dimens.hairline)
            .background(SyncDesk.colors.line),
    )
}

/**
 * The sentence that says what the car is doing, under a short mark in the colour of the current
 * feature. The mark is the fastest thing to read on the screen: grey while idle, blue while noise
 * is lowered, amber while the driver is missing what is said, warm white in office mode.
 */
@Composable
fun StatusHeadline(
    headline: String,
    accent: Color,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes supportingIcon: Int? = null,
    style: TextStyle = SyncDesk.type.status,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val markColor by animateColorAsState(accent, tween(SyncDesk.motion.settle), label = "mark")
    Column(modifier) {
        Box(
            Modifier
                .size(width = dimens.gapLarge * 1.5f, height = dimens.gapSmall * 0.75f)
                .clip(CircleShape)
                .background(markColor),
        )
        Spacer(Modifier.height(dimens.gap))
        Text(
            text = headline,
            style = style,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (supporting != null) {
            Spacer(Modifier.height(dimens.gapSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (supportingIcon != null) {
                    Icon(
                        painter = painterResource(supportingIcon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(dimens.icon),
                    )
                    Spacer(Modifier.width(dimens.gapSmall))
                }
                Text(text = supporting, style = SyncDesk.type.body, color = colors.textMuted)
            }
        }
    }
}

enum class ActionKind { Filled, Outlined }

/** A large button. Its label says what happens when it is pressed. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ActionKind = ActionKind.Filled,
    accent: Color = SyncDesk.colors.signal,
    enabled: Boolean = true,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val shape = RoundedCornerShape(dimens.radius)
    val container = when {
        !enabled -> colors.line
        kind == ActionKind.Filled -> accent
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> colors.textMuted
        kind == ActionKind.Filled -> colors.onAccent
        else -> colors.text
    }
    val view = LocalView.current
    Box(
        modifier = modifier
            .heightIn(min = dimens.action)
            .clip(shape)
            .background(container)
            .then(
                if (kind == ActionKind.Outlined && enabled) {
                    Modifier.border(dimens.hairline, colors.outline, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = dimens.gapLarge * 0.75f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SyncDesk.type.action,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A level drawn as a horizontal bar. [restore] leaves a tick at the value the car will return to,
 * so a lowered bar still shows where it came from.
 */
@Composable
fun LevelBar(
    value: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier,
    restore: Int? = null,
    segmented: Boolean = true,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val fraction by animateFloatAsState(
        targetValue = (value.toFloat() / max).coerceIn(0f, 1f),
        animationSpec = tween(SyncDesk.motion.settle),
        label = "level",
    )
    val fill by animateColorAsState(color, tween(SyncDesk.motion.settle), label = "levelColor")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(dimens.bar * 3),
    ) {
        val barHeight = dimens.bar.toPx()
        val top = (size.height - barHeight) / 2
        val count = if (segmented) max else 1
        val gap = if (segmented) barHeight * 0.4f else 0f
        val segmentWidth = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(barHeight / 2)

        fun DrawScope.segments(segmentColor: Color) {
            repeat(count) { index ->
                drawRoundRect(
                    color = segmentColor,
                    topLeft = Offset(index * (segmentWidth + gap), top),
                    size = Size(segmentWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }

        segments(colors.line)

        val filledSegments = fraction * count
        val whole = floor(filledSegments)
        val fillRight = whole * (segmentWidth + gap) + (filledSegments - whole) * segmentWidth
        if (fillRight > 0f) {
            clipRect(right = fillRight) { segments(fill) }
        }

        if (restore != null && restore != value) {
            val restoreSegments = restore.toFloat() / max * count
            val markerWidth = barHeight * 0.3f
            val x = (restoreSegments * segmentWidth + (restoreSegments - 1).coerceAtLeast(0f) * gap)
                .coerceIn(markerWidth, size.width - markerWidth)
            drawLine(
                color = colors.text,
                start = Offset(x, top - barHeight * 0.55f),
                end = Offset(x, top + barHeight * 1.55f),
                strokeWidth = markerWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * One line of the quiet rail: what it is and where it stands now, over a bar that keeps a tick at
 * the value it returns to. The tick says it without words; [description] says it for TalkBack.
 */
@Composable
fun RailRow(
    @DrawableRes icon: Int,
    label: String,
    value: Int,
    max: Int,
    valueText: String,
    description: String,
    modifier: Modifier = Modifier,
    restore: Int? = null,
    segmented: Boolean = true,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val lowered = restore != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gapSmall)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (lowered) colors.signal else colors.textMuted,
                modifier = Modifier.size(dimens.icon * 0.8f),
            )
            Spacer(Modifier.width(dimens.gapSmall))
            Text(
                text = label,
                style = SyncDesk.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = SyncDesk.type.title,
                color = if (lowered) colors.signal else colors.text,
                maxLines = 1,
            )
        }
        LevelBar(
            value = value,
            max = max,
            color = if (lowered) colors.signal else colors.textMuted,
            restore = restore,
            segmented = segmented,
        )
    }
}

/**
 * The sentences the driver is missing, drawn as strokes instead of words: one stroke per sentence,
 * as long as the sentence. It shows that the car is keeping them without putting anything to read
 * in front of a driver who should be watching the road.
 */
@Composable
fun InkLines(
    notes: List<MeetingNote>,
    modifier: Modifier = Modifier,
    visibleLines: Int = 5,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val shown = notes.takeLast(visibleLines)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.bar * visibleLines + dimens.gap * (visibleLines - 1))
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        shown.forEachIndexed { index, note ->
            val newest = index == shown.lastIndex
            Box(
                Modifier
                    .fillMaxWidth((note.text.length / FULL_STROKE_CHARACTERS).coerceIn(0.2f, 1f))
                    .height(dimens.bar)
                    .clip(CircleShape)
                    .background(if (newest) colors.caution else colors.outline),
            )
        }
    }
}

private const val FULL_STROKE_CHARACTERS = 44f

/**
 * One part of the cabin that office mode sets: what it is now and what it becomes. Every row has
 * the same two lines, so rows line up: the name on the first, with an arrow at its end when the
 * row can be pressed ([onClick]; [selected] while its control is open), and the values on the
 * second, starting where the name starts.
 */
@Composable
fun HardwareRow(
    @DrawableRes icon: Int,
    label: String,
    currentText: String,
    targetText: String,
    done: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val view = LocalView.current
    val iconGap = dimens.gap * 0.75f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touch)
            .clip(RoundedCornerShape(dimens.radius * 0.6f))
            .background(if (selected) colors.line.copy(alpha = 0.45f) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = dimens.gapSmall, vertical = dimens.gapSmall * 0.5f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (done) colors.lamp else colors.textMuted,
                modifier = Modifier.size(dimens.icon),
            )
            Spacer(Modifier.width(iconGap))
            Text(
                text = label,
                style = SyncDesk.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The arrow's place is kept on every row, so the arrows stand in one column.
            Box(Modifier.size(dimens.icon * 0.8f), contentAlignment = Alignment.Center) {
                if (onClick != null) {
                    Icon(
                        painter = painterResource(if (selected) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = dimens.icon + iconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (done || currentText == targetText) {
                Text(
                    text = targetText,
                    style = SyncDesk.type.title,
                    color = if (done) colors.lamp else colors.text,
                    maxLines = 1,
                )
            } else {
                Text(text = currentText, style = SyncDesk.type.title, color = colors.text, maxLines = 1)
                Text(text = " → ", style = SyncDesk.type.body, color = colors.textMuted, maxLines = 1)
                Text(text = targetText, style = SyncDesk.type.title, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Says, under the row of [part], that the vehicle refused to move it, while [refusal] is that part.
 * Nothing otherwise.
 */
@Composable
fun RefusalNote(refusal: CabinPart?, part: CabinPart, modifier: Modifier = Modifier) {
    if (refusal != part) return
    val name = stringResource(
        when (part) {
            CabinPart.Fan -> R.string.part_fan
            CabinPart.Window -> R.string.part_window
            CabinPart.Recirculation -> R.string.part_recirculation
            CabinPart.Seat -> R.string.part_seat
            CabinPart.ReadingLight -> R.string.part_reading_light
            CabinPart.Ambient -> R.string.part_ambient
            CabinPart.AirDirection -> R.string.part_air_direction
            CabinPart.Temperature -> R.string.part_temperature
            CabinPart.SeatVentilation -> R.string.part_seat_ventilation
            CabinPart.SeatHeating -> R.string.part_seat_heating
            CabinPart.AirCleaner -> R.string.part_air_cleaner
            CabinPart.Defroster -> R.string.part_defroster
            CabinPart.AmbientColor -> R.string.part_ambient_color
            CabinPart.CallVolume -> R.string.part_call_volume
            CabinPart.SeatSlide -> R.string.part_seat_slide
            CabinPart.SeatHeight -> R.string.part_seat_height
            CabinPart.CushionTilt -> R.string.part_cushion_tilt
            CabinPart.CushionLength -> R.string.part_cushion_length
            CabinPart.Lumbar -> R.string.part_lumbar
            CabinPart.CabinLight -> R.string.part_cabin_light
            CabinPart.Shade -> R.string.part_shade
            CabinPart.DoorLock -> R.string.part_door_lock
            CabinPart.SteeringWheel -> R.string.part_steering_wheel
        },
    )
    Text(
        text = stringResource(R.string.refusal, name),
        style = SyncDesk.type.label,
        color = SyncDesk.colors.caution,
        modifier = modifier.padding(bottom = SyncDesk.dimens.gapSmall),
    )
}

/**
 * Changes a number in fixed steps with two large buttons, the kind of control that can be used
 * without looking closely. Each button says what it does through [decreaseLabel] and
 * [increaseLabel].
 */
@Composable
fun Stepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(symbol = "\u2212", label = decreaseLabel, enabled = canDecrease, onClick = onDecrease)
        Text(
            text = valueText,
            style = SyncDesk.type.title,
            color = colors.text,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.gap),
        )
        StepButton(symbol = "+", label = increaseLabel, enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val shape = RoundedCornerShape(dimens.radius * 0.8f)
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(width = dimens.touch * 1.3f, height = maxOf(dimens.touch * 0.8f, MIN_SEGMENT_HEIGHT))
            .clip(shape)
            .border(dimens.hairline, if (enabled) colors.outline else colors.line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = SyncDesk.type.title,
            color = if (enabled) colors.text else colors.textMuted,
        )
    }
}

/** Picks one of a few options (the old office panel). */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SyncDesk.colors
    val dimens = SyncDesk.dimens
    val shape = RoundedCornerShape(dimens.radius * 0.8f)
    val view = LocalView.current
    Row(
        modifier = modifier
            .height(maxOf(dimens.touch * 0.8f, MIN_SEGMENT_HEIGHT))
            .clip(shape)
            .border(dimens.hairline, colors.outline, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) colors.text else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = SyncDesk.type.label,
                    color = if (selected) colors.ground else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val MIN_SEGMENT_HEIGHT = 76.dp
