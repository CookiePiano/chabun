package com.syncdesk.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Materials
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import com.syncdesk.app.ui.theme.pressScale

/** The three looks of a big button (spec 6). A disabled one keeps its look at 55 % with no shadow. */
enum class ButtonKind {
    /** "pri": the teal gradient with its glow. One per screen, for the main action. */
    Accent,

    /** "solid": ink-coloured by day, near-white by night. */
    Solid,

    /** "soft": glass. */
    Glass,
}

/**
 * A big button: 112 high, a pill, 44 in from the sides, label 32 B with an optional icon 16 to
 * its left. Presses scale to 0.96 (review item 7). The label wraps to a second line rather than
 * being cut off.
 */
@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    /** Narrower side padding where buttons share a row (office side panel: 20). */
    horizontalPadding: Dp = 44.dp,
) {
    val colors = SyncDesk.colors
    val night = colors.isNight
    val shape = SyncDeskShapes.button
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val content = when (kind) {
        ButtonKind.Accent -> colors.onAccent
        ButtonKind.Solid -> colors.onSolid
        ButtonKind.Glass -> colors.ink
    }
    val surface = when (kind) {
        ButtonKind.Accent -> Modifier
            .then(if (enabled) Modifier.dropShadow(shape, Materials.accentGlow(colors.accent)) else Modifier)
            .clip(shape)
            .background(Materials.accentFill(night))
            .innerShadow(shape, Materials.accentTopLight(night))
        ButtonKind.Solid -> Modifier.clip(shape).background(colors.solid)
        ButtonKind.Glass -> Modifier.glass(shape, shadow = enabled)
    }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = Design.buttonHeight)
            .then(surface)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(36.dp))
        }
        Text(text = label, style = SyncDesk.type.button, color = content, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/**
 * A round button with only an icon: the sheet's close button (84, glass), the full-transcript
 * card's "go" (76, solid), the settings sheet's minus and plus (84, glass). [description] is what
 * TalkBack reads (spec 10: "닫기", "{항목} 줄이기").
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Design.sheetClose,
    iconSize: Dp = 40.dp,
    kind: ButtonKind = ButtonKind.Glass,
    enabled: Boolean = true,
) {
    val colors = SyncDesk.colors
    val shape = SyncDeskShapes.circle
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val (surface, tint) = when (kind) {
        ButtonKind.Accent -> Modifier.clip(shape).background(Materials.accentFill(colors.isNight)) to colors.onAccent
        ButtonKind.Solid -> Modifier.clip(shape).background(colors.solid) to colors.onSolid
        ButtonKind.Glass -> Modifier.glass(shape, shadow = false) to colors.ink
    }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .size(maxOf(size, Design.minTouch))
            .then(surface)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = null) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A small pill tag: "내 약속" (glass, accent text), "새 메모" (accent, on-accent), a missed reason (miss tint, miss ink). */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    kind: TagKind = TagKind.Glass,
) {
    val colors = SyncDesk.colors
    val shape = SyncDeskShapes.pill
    val (surface, ink) = when (kind) {
        TagKind.Glass -> Modifier.glass(shape, shadow = false) to colors.accent
        TagKind.Accent -> Modifier.clip(shape).background(colors.accent) to colors.onAccent
        TagKind.Missed -> Modifier.clip(shape).background(colors.missTint) to colors.missInk
    }
    Text(
        text = text,
        style = SyncDesk.type.smallStrong,
        color = ink,
        maxLines = 1,
        modifier = modifier
            .then(surface)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    )
}

enum class TagKind { Glass, Accent, Missed }

private const val DISABLED_ALPHA = 0.55f
