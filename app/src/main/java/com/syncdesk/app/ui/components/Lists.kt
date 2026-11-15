package com.syncdesk.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Durations
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.chip
import com.syncdesk.app.ui.theme.glass
import com.syncdesk.app.ui.theme.pressScale

/** A glass card (spec 3). [shape] is 40 by default; 44 for the large lists. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = SyncDeskShapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape), content = content)
}

/** A 2-high line between rows in a glass card, in glass-line. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(SyncDesk.colors.glassLine),
    )
}

/**
 * One received call in the parked screen's list: 104 high, rounded 32, avatar 64, the name
 * (30 B) over [detail] (24 R, muted) and, for a memo not looked at yet, a 16 accent dot with an
 * 8 glow. The [selected] row has the chip-on material; TalkBack reads it as selected (spec 10).
 */
@Composable
fun ReceivedCallRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasNewMemo: Boolean = false,
    avatar: @Composable () -> Unit = { Avatar(name, contentDescription = null) },
) {
    val colors = SyncDesk.colors
    val shape = SyncDeskShapes.row
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val newMemo = stringResource(R.string.new_memo)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .heightIn(min = Design.listRowHeight)
            .then(if (selected) Modifier.chip(shape) else Modifier.clip(shape))
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Column(Modifier.weight(1f)) {
            Text(text = name, style = SyncDesk.type.bodyStrong.copy(lineHeight = SyncDesk.type.bodyStrong.fontSize * 1.27f), color = colors.ink)
            Text(text = detail, style = SyncDesk.type.small, color = colors.muted)
        }
        if (hasNewMemo) {
            val glow = colors.accent.copy(alpha = 0.22f)
            Box(
                Modifier
                    .size(16.dp)
                    .drawBehind { drawCircle(glow, radius = size.minDimension / 2 + 8.dp.toPx()) }
                    .clip(SyncDeskShapes.circle)
                    .background(colors.accent)
                    .semantics { contentDescription = newMemo },
            )
        }
    }
}

/**
 * Work | Meeting (spec 6): a glass track with two 88-high segments; the chosen one is solid with
 * on-solid text, the other muted. Each segment is read with its selected state.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val view = LocalView.current
    Row(
        modifier = modifier
            .glass(SyncDeskShapes.segmentTrack, shadow = false)
            .padding(8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(
                if (selected) colors.solid else colors.solid.copy(alpha = 0f),
                motion.fade(SEGMENT_MILLIS),
                label = "segment",
            )
            val ink by animateColorAsState(if (selected) colors.onSolid else colors.muted, motion.fade(SEGMENT_MILLIS), label = "segmentInk")
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .height(Design.segmentHeight)
                    .clip(SyncDeskShapes.segment)
                    .background(fill)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.RadioButton,
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = option, style = SyncDesk.type.bodyStrong, color = ink, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val SEGMENT_MILLIS = 300

/** Where one step of a sequence stands. */
enum class StepState { Waiting, Now, Done }

/** One row of a [StepList]: the number shown in its circle, what it does, and where it stands. */
@Immutable
data class StepItem(val number: Int, val label: String, val state: StepState)

/**
 * The office sequence (spec 6): a glass card of rows 98 high with a 56 number circle. Waiting:
 * muted outline and text. In progress: accent fill with an 8 glow, text in ink and bold. Done:
 * accent outline and a check. Each row is read as "N단계, label, state".
 */
@Composable
fun StepList(steps: List<StepItem>, modifier: Modifier = Modifier) {
    GlassCard(modifier, shape = SyncDeskShapes.card) {
        Column(Modifier.padding(horizontal = 26.dp, vertical = 8.dp)) {
            steps.forEachIndexed { index, step ->
                if (index > 0) GlassDivider()
                StepRow(step)
            }
        }
    }
}

@Composable
private fun StepRow(step: StepItem) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val now = step.state == StepState.Now
    val done = step.state == StepState.Done
    val text by animateColorAsState(if (now) colors.ink else colors.muted, motion.fade(STEP_MILLIS), label = "stepText")
    val ring by animateColorAsState(if (step.state == StepState.Waiting) colors.muted else colors.accent, motion.fade(STEP_MILLIS), label = "stepRing")
    val fill by animateColorAsState(if (now) colors.accent else colors.accent.copy(alpha = 0f), motion.fade(STEP_MILLIS), label = "stepFill")
    val stateText = stringResource(
        when (step.state) {
            StepState.Waiting -> R.string.step_waiting
            StepState.Now -> R.string.step_now
            StepState.Done -> R.string.step_done
        },
    )
    val numberText = stringResource(R.string.step_number, step.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Design.stepRowHeight)
            .clearAndSetSemantics {
                contentDescription = "$numberText, ${step.label}"
                stateDescription = stateText
            },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glow = colors.accent.copy(alpha = 0.24f)
        Box(
            modifier = Modifier
                .size(Design.stepNumber)
                .drawBehind { if (now) drawCircle(glow, radius = size.minDimension / 2 + 8.dp.toPx()) }
                .clip(SyncDeskShapes.circle)
                .background(fill)
                .border(3.dp, ring, SyncDeskShapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = done, animationSpec = motion.fade(Durations.REDUCED_FADE), label = "stepMark") { isDone ->
                if (isDone) {
                    Icon(LineIcon.Check.vector(3f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
                } else {
                    Text(
                        text = step.number.toString(),
                        style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = if (now) colors.onAccent else colors.muted,
                    )
                }
            }
        }
        Text(
            text = step.label,
            style = SyncDesk.type.captionLarge.copy(
                lineHeight = SyncDesk.type.captionLarge.fontSize * 1.36f,
                fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
            ),
            color = text,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val STEP_MILLIS = 400
