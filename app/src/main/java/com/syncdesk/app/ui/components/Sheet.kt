package com.syncdesk.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Durations
import com.syncdesk.app.ui.theme.Materials
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glassStrong

/**
 * A screen with a bottom sheet over it: "전체 내용" on the parked screen, "설정 바꾸기" in office
 * mode (spec 5, 6; review item 3).
 *
 * The sheet comes up 72 in from both sides, [height] high, rounded 52 at the top, in
 * glass-strong, over a scrim of `rgba(3,6,18,.5)`: up in 0.55 s with the sheet curve, the scrim
 * in 0.45 s, the inside appearing at once 0.2 s later; down in 0.45 s. Pressing again midway
 * turns it around where it is.
 *
 * Accessibility: on opening, focus moves to the sheet's title (and TalkBack hears the pane
 * title); back closes it; on closing, focus returns to [returnFocusTo], the button that opened
 * it; while it is open, [content] behind it cannot be reached by TalkBack or touched.
 */
@Composable
fun SheetHost(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    returnFocusTo: FocusRequester? = null,
    height: Dp = Design.sheetHeight,
    sheet: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val slide by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (open) motion.sheetIn() else motion.sheetOut(),
        label = "sheet",
    )
    val scrim by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = motion.fade(Durations.SCRIM),
        label = "scrim",
    )
    val inside by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (open) motion.fade(Durations.ENTER, delay = INSIDE_DELAY) else motion.fade(Durations.EXIT),
        label = "sheetInside",
    )
    val titleFocus = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(open) }

    BackHandler(enabled = open, onBack = onClose)
    LaunchedEffect(open) {
        if (open) {
            withFrameNanos { }
            runCatching { titleFocus.requestFocus() }
        } else if (wasOpen) {
            runCatching { returnFocusTo?.requestFocus() }
        }
        wasOpen = open
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (open) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            content()
        }
        if (scrim > 0f || open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(Materials.scrim)
                    .pointerInput(onClose) { detectTapGestures { onClose() } }
                    .clearAndSetSemantics { },
            )
        }
        if (slide > 0f || open) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Design.sheetInset)
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer { translationY = (1f - slide) * size.height * SHEET_TRAVEL }
                    .dropShadow(SyncDeskShapes.sheet, SheetShadow)
                    .glassStrong(SyncDeskShapes.sheet)
                    // Taps on the sheet stay on it instead of reaching the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics {
                        paneTitle = title
                        isTraversalGroup = true
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 28.dp, top = 28.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = SyncDesk.type.bodyLarge,
                        color = colors.ink,
                        modifier = Modifier
                            .focusRequester(titleFocus)
                            .focusable()
                            .semantics { heading() },
                    )
                    // Only one child takes the free width, so the close button stays at the right edge.
                    if (subtitle != null) {
                        Text(text = subtitle, style = SyncDesk.type.caption, color = colors.muted, modifier = Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    RoundIconButton(
                        icon = LineIcon.Down.vector(),
                        description = stringResource(R.string.sheet_close),
                        onClick = onClose,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = inside }
                        .verticalScroll(rememberScrollState())
                        .padding(start = 48.dp, end = 48.dp, bottom = 40.dp),
                    content = sheet,
                )
            }
        }
    }
}

private const val INSIDE_DELAY = 200

/** The sheet moves 102 % of its height, so its shadow is out of sight too when closed. */
private const val SHEET_TRAVEL = 1.02f

/** `0 -30px 80px -20px rgba(0,0,0,.45)` under the open sheet. */
private val SheetShadow = Shadow(radius = 80.dp, color = Color.Black.copy(alpha = 0.45f), spread = (-20).dp, offset = DpOffset(0.dp, (-30).dp))
