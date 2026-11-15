package com.syncdesk.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncdesk.app.R
import com.syncdesk.app.data.RuleMemoAnswerer
import com.syncdesk.app.domain.DialogPhase
import com.syncdesk.app.domain.DialogStep
import com.syncdesk.app.domain.SyncDeskMode
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.fit
import com.syncdesk.app.ui.components.Conversation
import com.syncdesk.app.ui.components.DriverTurn
import com.syncdesk.app.ui.components.GleoIcon
import com.syncdesk.app.ui.components.GleoTurn
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.VoiceBars
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The current conversation and at most two earlier turns, using the actual memo and replies.
 * The model exposes no spoken-line cursor, so summary and missed content are shown together;
 * visual animation never advances the conversation or pretends to follow the narrator's clock.
 */
@Composable
internal fun DriveConversation(
    state: SyncDeskState,
    width: DriveWidth,
    historyState: MutableState<List<DriveTurn>>,
    modifier: Modifier = Modifier,
) {
    val dialog = state.dialog ?: return
    val mode = state.mode as? SyncDeskMode.Conversing ?: return
    val memo = state.memos.firstOrNull { it.id == dialog.memoId }
    val wide = width == DriveWidth.Half
    val motion = SyncDesk.motion
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val offer = stringResource(R.string.drive_dialog_offer)
    val ask = stringResource(R.string.drive_dialog_ask)
    val yes = stringResource(R.string.drive_dialog_yes)
    val listening = stringResource(R.string.drive_dialog_listening)
    val missedTag = stringResource(R.string.drive_dialog_missed)
    val noKeyPoints = stringResource(R.string.gleo_no_key_points)
    val notMentioned = stringResource(R.string.gleo_not_mentioned)
    val answering = stringResource(R.string.drive_dialog_answering)
    val summary = memo?.summary.orEmpty().joinToString("\n").ifBlank { noKeyPoints }
    val missed = memo?.missedToSay.orEmpty().joinToString("\n")
    val answerer = remember { RuleMemoAnswerer() }
    // This is the same deterministic, on-device answerer used by the runtime. No request or
    // conversation event is sent from the UI, and cancellation follows this composition.
    val answer by produceState<String?>(null, memo, dialog.step, dialog.question) {
        value = if (dialog.step == DialogStep.Answer) {
            memo?.let { answerer.answer(it, dialog.question) } ?: notMentioned
        } else {
            null
        }
    }
    var history by historyState
    val explanation = buildList {
        add(DriveTurn("summary", summary))
        if (missed.isNotBlank()) add(DriveTurn("missed", missed, tag = missedTag))
    }
    val additions = when (dialog.step) {
        DialogStep.Offer -> listOf(DriveTurn("offer", offer))
        DialogStep.Explain -> listOf(DriveTurn("yes", yes, driver = true)) + explanation
        DialogStep.Ask -> if (dialog.said && history.any { it.id.startsWith("answer:") }) {
            emptyList()
        } else {
            (if (history.isEmpty()) explanation else emptyList()) + DriveTurn("ask", ask)
        }
        DialogStep.Answer -> buildList {
            if (history.isEmpty()) add(DriveTurn("ask", ask))
            add(DriveTurn("question:${dialog.question}", dialog.question, driver = true))
            add(DriveTurn("answer:${dialog.question}", answer ?: answering))
        }
    }
    val addedIds = additions.mapTo(mutableSetOf()) { it.id }
    val recorded = (history.filterNot { it.id in addedIds } + additions).takeLast(3)
    SideEffect { if (history != recorded) history = recorded }

    if (mode.phase == DialogPhase.Waiting || (dialog.step == DialogStep.Offer && !dialog.said)) {
        ReadyConversation(state, wide, waiting = mode.phase == DialogPhase.Waiting, modifier)
        return
    }

    val turns = if (mode.phase == DialogPhase.Listening) {
        recorded + DriveTurn("listening", listening, driver = true, listening = true)
    } else {
        recorded
    }
    val visible = turns.takeLast(minOf(3, state.ux.maxItems?.coerceAtLeast(0) ?: 3))
    val listState = rememberLazyListState()
    var anchoredTurn by remember { mutableStateOf<String?>(null) }
    val newestTurn = visible.lastOrNull()?.id
    SideEffect {
        if (anchoredTurn != newestTurn) {
            // Keep the newest turn at the bottom instead of preserving the previous item's
            // scroll key. Staying at index zero preserves the individual placement animations.
            listState.requestScrollToItem(0)
            anchoredTurn = newestTurn
        }
    }
    Conversation(
        modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 200.dp, bottom = 178.dp),
    ) {
        // Only individual turns move: retained turns settle in 470 ms and removed turns fade
        // out in 180 ms. The common conversation keeps its top mask and polite announcements.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.Bottom),
            userScrollEnabled = false,
        ) {
            items(visible.asReversed(), key = { it.id }) { turn ->
                val old = turn.id != newestTurn
                val bodyStyle = turnTextStyle(turn, old, wide)
                val textWidth = if (turn.driver) {
                    (width.design - 72.dp) * 0.92f - if (old) 48.dp else 118.dp
                } else {
                    width.design - 72.dp
                }
                val wrappedText = rememberWordWrappedText(state.ux.fit(turn.text), bodyStyle, textWidth)
                val text = fitTurnLines(
                    wrappedText,
                    bodyStyle,
                    with(density) { textWidth.roundToPx() },
                    maxLines = if (old) 2 else 6,
                    textMeasurer,
                )
                val itemModifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = if (motion.reduced) null else tween(470, easing = Easings.Standard),
                    fadeOutSpec = motion.fade(millis = 180, easing = Easings.Exit),
                )
                val turnModifier = if (old) itemModifier else itemModifier.turnEntrance(turn.id)
                when {
                    turn.listening -> ListeningTurn(text, wide, turnModifier)
                    turn.driver -> DriverTurn(text, old = old, wide = wide, modifier = turnModifier)
                    else -> GleoTurn(
                        text,
                        old = old,
                        tag = turn.tag?.let(state.ux::fit),
                        speaking = !old && mode.phase == DialogPhase.Speaking,
                        wide = wide,
                        modifier = turnModifier,
                    )
                }
            }
        }
    }
}

/** Shown only after the host observes an actual, safely completed dialog transition. */
@Composable
internal fun DriveConversationEnd(
    state: SyncDeskState,
    width: DriveWidth,
    modifier: Modifier = Modifier,
) {
    val wide = width == DriveWidth.Half
    val textMeasurer = rememberTextMeasurer()
    val style = turnTextStyle(DriveTurn("end", ""), old = false, wide)
    val wrappedText = rememberWordWrappedText(
        state.ux.fit(stringResource(R.string.drive_dialog_end)),
        style,
        width.design - 72.dp,
    )
    val text = fitTurnLines(
        wrappedText,
        style,
        with(LocalDensity.current) { (width.design - 72.dp).roundToPx() },
        maxLines = 6,
        textMeasurer,
    )
    if ((state.ux.maxItems ?: 1) <= 0) return
    Conversation(modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 200.dp, bottom = 178.dp)) {
        GleoTurn(text, speaking = false, wide = wide, modifier = Modifier.turnEntrance("end"))
    }
}

internal data class DriveTurn(
    val id: String,
    val text: String,
    val driver: Boolean = false,
    val listening: Boolean = false,
    val tag: String? = null,
)

/** The shared turns' typography, used to budget real wrapped lines before handing them text. */
@Composable
private fun turnTextStyle(turn: DriveTurn, old: Boolean, wide: Boolean): TextStyle = when {
    turn.listening && wide -> SyncDesk.type.bodyLargeRegular.copy(fontSize = 38.sp, lineHeight = 54.sp)
    turn.listening -> SyncDesk.type.bodyLargeRegular.copy(fontSize = 32.sp, lineHeight = 46.sp)
    turn.driver && old && wide -> SyncDesk.type.caption.copy(fontSize = 30.sp, lineHeight = 42.sp)
    turn.driver && old -> SyncDesk.type.caption
    turn.driver && wide -> SyncDesk.type.button.copy(fontSize = 38.sp, lineHeight = 54.sp)
    turn.driver -> SyncDesk.type.button.copy(lineHeight = 46.sp)
    old && wide -> SyncDesk.type.captionLarge.copy(fontSize = 32.sp, lineHeight = 46.sp)
    old -> SyncDesk.type.captionLarge
    wide -> SyncDesk.type.bodyLarge.copy(fontSize = 44.sp, lineHeight = 62.sp)
    else -> SyncDesk.type.bodyLarge
}

/**
 * Two previous turns and six current lines fit the 832 dp viewport at either width. Measure
 * with the actual NeoHyundai font and density, then retain the longest fitting prefix. This
 * also handles long unbroken words and explicit newlines without estimating character width.
 */
private fun fitTurnLines(text: String, style: TextStyle, widthPx: Int, maxLines: Int, measurer: TextMeasurer): String {
    fun fits(value: String) = !measurer.measure(
        text = value,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
    ).hasVisualOverflow
    if (fits(text)) return text
    var low = 0
    var high = text.length
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(text.take(middle).trimEnd() + "…")) low = middle else high = middle - 1
    }
    return text.take(low).trimEnd() + "…"
}

/** The first question keeps the larger icon and the quiet, centered invitation of the design. */
@Composable
private fun ReadyConversation(state: SyncDeskState, wide: Boolean, waiting: Boolean, modifier: Modifier) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val contentWidth = if (wide) 769.dp else 489.dp
    val title = state.ux.fit(stringResource(R.string.drive_dialog_ready))
    val titleStyle = if (wide) SyncDesk.type.title1Wide else SyncDesk.type.title1Drive
    val wrappedTitle = rememberWordWrappedText(title, titleStyle, contentWidth)
    val offer = state.ux.fit(stringResource(R.string.drive_dialog_offer))
    val scale = breathingScale(enabled = !waiting, target = 1.05f, period = 1300)
    val entrance = remember { Animatable(if (motion.reduced) 1f else 0.86f) }
    LaunchedEffect(motion.reduced) { entrance.animateTo(1f, motion.move(millis = 600)) }
    Column(
        modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 288.dp, bottom = 178.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GleoIcon(
            168.dp,
            Modifier.graphicsLayer {
                scaleX = entrance.value * scale
                scaleY = entrance.value * scale
            },
        )
        Spacer(Modifier.height(40.dp))
        Text(
            wrappedTitle,
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(title) },
            style = titleStyle,
            color = colors.ink,
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        Spacer(Modifier.height(34.dp))
        VoiceBars(active = !waiting, height = 44.dp, barWidth = 8.dp, gap = 7.dp)
        Spacer(Modifier.height(14.dp))
        RevealedWords(
            offer,
            style = if (wide) SyncDesk.type.bodyLargeRegular.copy(fontSize = 42.sp, lineHeight = 60.sp)
            else SyncDesk.type.bodyLargeRegular,
            animate = !waiting,
        )
        Spacer(Modifier.height(26.dp))
        val hint = state.ux.fit(stringResource(if (waiting) R.string.drive_dialog_waiting else R.string.drive_dialog_hint))
        val wrappedHint = rememberWordWrappedText(hint, SyncDesk.type.caption, contentWidth - 80.dp)
        val hintAlpha = remember(waiting) { Animatable(if (motion.reduced || waiting) 1f else 0f) }
        LaunchedEffect(waiting, motion.reduced) {
            if (!waiting && motion.enabled) delay(1700)
            hintAlpha.animateTo(1f, motion.fade(millis = 280))
        }
        Row(
            Modifier.graphicsLayer { alpha = hintAlpha.value }
                .glass(SyncDeskShapes.pill, shadow = false).padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LineIcon.Mic.vector(2.2f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Text(
                wrappedHint,
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(hint) },
                style = SyncDesk.type.caption,
                color = colors.muted,
                textAlign = TextAlign.Center,
                softWrap = false,
            )
        }
    }
}

/** Full text remains available to accessibility while its words brighten in place. */
@Composable
private fun RevealedWords(text: String, style: TextStyle, animate: Boolean) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val words = remember(text) { Regex("\\S+\\s*").findAll(text).map { it.value }.toList() }
    var revealed by remember(text) { mutableStateOf(if (motion.reduced || !animate) words.size else 0) }
    LaunchedEffect(text, animate, motion.reduced) {
        if (motion.reduced || !animate) {
            revealed = words.size
        } else {
            words.indices.forEach {
                revealed = it + 1
                delay(280)
            }
        }
    }
    val brightness = words.indices.map { index ->
        animateFloatAsState(
            if (index < revealed) 1f else 0.3f,
            animationSpec = motion.fade(millis = 280),
            label = "questionWord$index",
        ).value
    }
    Text(
        buildAnnotatedString {
            words.forEachIndexed { index, word -> withStyle(SpanStyle(color = colors.ink.copy(alpha = brightness[index]))) { append(word) } }
        },
        style = style,
        textAlign = TextAlign.Center,
    )
}

/** Unlike the shared turn, this accepts the already-fitted listening label. */
@Composable
private fun ListeningTurn(text: String, wide: Boolean, modifier: Modifier) {
    val colors = SyncDesk.colors
    val scale = breathingScale(enabled = true, target = 1.12f, period = 500)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier.widthIn(max = (if (wide) 769.dp else 489.dp) * 0.92f)
                .glass(SyncDeskShapes.driverTurn, shadow = false).padding(start = 16.dp, end = 30.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(SyncDeskShapes.circle).background(colors.accent.copy(alpha = 0.12f))
                    .border(3.dp, colors.accent.copy(alpha = 0.7f), SyncDeskShapes.circle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LineIcon.Mic.vector(2.2f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            }
            Text(
                text,
                style = if (wide) SyncDesk.type.bodyLargeRegular.copy(fontSize = 38.sp, lineHeight = 54.sp)
                else SyncDesk.type.bodyLargeRegular.copy(fontSize = 32.sp, lineHeight = 46.sp),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun breathingScale(enabled: Boolean, target: Float, period: Int): Float {
    if (!enabled || SyncDesk.motion.reduced) return 1f
    return rememberInfiniteTransition(label = "conversationBreathing").animateFloat(
        initialValue = 1f,
        targetValue = target,
        animationSpec = InfiniteRepeatableSpec(tween(period, easing = Easings.Breathe), RepeatMode.Reverse),
        label = "conversationIconScale",
    ).value
}

/** Only a new turn moves; the window itself and the past turns remain still and opaque. */
@Composable
private fun Modifier.turnEntrance(id: String): Modifier {
    val motion = SyncDesk.motion
    val rise = with(LocalDensity.current) { 36.dp.toPx() }
    val opacity = remember(id) { Animatable(0f) }
    val offset = remember(id) { Animatable(if (motion.reduced) 0f else rise) }
    LaunchedEffect(id, motion.reduced) {
        launch { opacity.animateTo(1f, motion.fade(millis = 220, delay = 80)) }
        launch { offset.animateTo(0f, motion.move(millis = 600, delay = 260)) }
    }
    return graphicsLayer { alpha = opacity.value; translationY = offset.value }
}
