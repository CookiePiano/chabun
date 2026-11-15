package com.syncdesk.app.ui.parked

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.ui.components.BigButton
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.SheetHost
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Distances
import com.syncdesk.app.ui.theme.Durations
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import java.time.LocalDate

/**
 * The parked screen in the full app window (1682 x 1210 design units, spec 5 "주차 후"): top right
 * the unseen-contacts line and the office mode button; on the left the caller and the three
 * key-summary lines; on the right the received calls of this drive, with earlier drives' calls
 * folded under them; the call wave at 700; at the bottom the missed-part cards and the "전체 내용"
 * card, which opens the full transcript in a bottom sheet. Without a call on this drive, the
 * empty screen (spec 9 "통화 없음").
 *
 * Contract with the skeleton (`SyncDeskScreen`): shown when the desk is open (parked, or the car
 * driving itself), office mode is off and there is no first-use notice to show. [onOpenOffice]
 * is the office mode button. [today] dates the memos. The skeleton draws the window ground and
 * glows, composes this screen afresh each time it appears (so the entrance of spec 8-2 runs each
 * time), lays it out in design units and keeps the top-left 480 x 200 free (side camera).
 *
 * Platform limits (only in force while the car drives itself): every text is cut to
 * [UxLimits.maxStringLength], and [UxLimits.maxItems] bounds the list and the memo as before:
 * the summary first, then the missed part, then the newest sentences, then the materials.
 */
@Composable
fun ParkedScreen(
    state: SyncDeskState,
    materials: List<MeetingMaterial>,
    @Suppress("UNUSED_PARAMETER") onEvent: (SyncDeskEvent) -> Unit,
    onOpenOffice: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val motion = SyncDesk.motion
    val ux = state.ux
    val trip = state.tripMemos.limitedTo(ux.maxItems)
    val earlier = state.earlierMemos.limitedTo(ux.maxItems?.let { (it - trip.size).coerceAtLeast(0) })
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val memo = (trip + earlier).firstOrNull { it.id == selectedId } ?: trip.firstOrNull()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val fullCardFocus = remember { FocusRequester() }
    val entrance = rememberEntrance()

    // "통화 바꾸기" (spec 8-2): the bars of the newly chosen call grow together. A new press cancels
    // the running growth and starts it again from where the new call begins.
    val switchGrow = remember { Animatable(1f) }
    var grownId by remember { mutableStateOf(memo?.id) }
    LaunchedEffect(memo?.id) {
        if (memo?.id != grownId) {
            grownId = memo?.id
            switchGrow.snapTo(WAVE_START)
            switchGrow.animateTo(1f, motion.move(SWITCH_GROW_MILLIS, Easings.Sheet))
        }
    }
    val memoMaterials = if (memo != null && memo.id == state.latestMemo?.id) materials else emptyList()
    val memoTiming = memo?.let { remember(it) { timing(it) } }

    SheetHost(
        open = sheetOpen && memo != null,
        onClose = { sheetOpen = false },
        title = stringResource(R.string.parked_full),
        subtitle = memo?.let { sheetSubtitle(it, today, ux) },
        returnFocusTo = fullCardFocus,
        modifier = modifier.fillMaxSize(),
        sheet = {
            if (memo != null && memoTiming != null) Transcript(memo, memoTiming, memoMaterials, ux)
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = entrance.alpha },
        ) {
            TopRow(
                state = state,
                onOpenOffice = onOpenOffice,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Design.marginTop, end = Design.marginEnd)
                    .rise(entrance, LATER_DELAY, Durations.ENTER_LONG),
            )
            if (trip.isNotEmpty() || earlier.isNotEmpty()) {
                CallList(
                    trip = trip,
                    earlier = earlier,
                    selectedId = memo?.id,
                    onSelect = { selectedId = it },
                    today = today,
                    ux = ux,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = BODY_TOP, end = Design.marginEnd)
                        .width(LIST_WIDTH)
                        .rise(entrance, LATER_DELAY, Durations.ENTER_LONG),
                )
            }
            if (memo == null || memoTiming == null) {
                EmptyBody(entrance = entrance)
            } else {
                val density = LocalDensity.current.density
                AnimatedContent(
                    targetState = memo,
                    contentKey = { it.id },
                    transitionSpec = {
                        motion.enter(Distances.enterRiseShort, Durations.ENTER, SWITCH_ENTER_DELAY, density) togetherWith
                            motion.exit(Distances.exitRise, SWITCH_EXIT_MILLIS, density) using SizeTransform(clip = false)
                    },
                    modifier = Modifier
                        .padding(start = Design.marginStartParked, top = BODY_TOP)
                        .width(BODY_WIDTH),
                    label = "memoHead",
                ) { shown ->
                    MemoHead(memo = shown, today = today, ux = ux, entrance = entrance)
                }
                CallWaveSection(
                    memo = memo,
                    timing = memoTiming,
                    grow = { index -> minOf(entrance.barGrow(index), switchGrow.value) },
                    modifier = Modifier
                        .padding(start = Design.marginStartParked, end = Design.marginEnd, top = WAVE_TOP)
                        .rise(entrance, 0, WAVE_FADE_MILLIS, distance = 0.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Design.marginStartParked, end = Design.marginEnd, bottom = Design.marginBottom)
                        .fillMaxWidth()
                        .height(BOTTOM_ROW_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                ) {
                    AnimatedContent(
                        targetState = memo,
                        contentKey = { it.id },
                        transitionSpec = {
                            motion.enter(Distances.enterRiseShort, Durations.ENTER, SWITCH_ENTER_DELAY, density) togetherWith
                                motion.exit(Distances.exitRise, SWITCH_EXIT_MILLIS, density) using SizeTransform(clip = false)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .rise(entrance, LATER_DELAY + STAGGER, Durations.ENTER_LONG),
                        label = "missedCards",
                    ) { shown ->
                        MissedCards(memo = shown, ux = ux)
                    }
                    FullTranscriptCard(
                        memo = memo,
                        timing = memoTiming,
                        onOpen = { sheetOpen = true },
                        focusRequester = fullCardFocus,
                        modifier = Modifier
                            .width(FULL_CARD_WIDTH)
                            .fillMaxHeight()
                            .rise(entrance, LATER_DELAY + STAGGER * 2, Durations.ENTER_LONG),
                    )
                }
            }
        }
    }
}

/** At most [max] of these, or all when there is no limit. */
private fun List<CallMemo>.limitedTo(max: Int?): List<CallMemo> = if (max == null) this else take(max)

/**
 * The one-time entrance of spec 8-2 "주차 후 들어오기", driven by one clock in milliseconds: the
 * caller and the summary lines rise 28 one after another (0.09 s apart, 0.6 s each), the wave's
 * bars grow from 15 % to full from the left (0.005 s apart, 0.5 s each), and the list and cards
 * rise 0.25 s later. With reduced motion (spec 8-4) the clock starts at its end and the whole
 * screen fades in over 0.2 s instead.
 */
@Stable
internal class Entrance(
    private val clock: Animatable<Float, AnimationVector1D>,
    private val fade: Animatable<Float, AnimationVector1D>,
) {
    val alpha: Float get() = fade.value

    /** How far an element that starts at [delay] and takes [millis] has come, through [easing]. */
    fun progress(delay: Int, millis: Int, easing: Easing): Float =
        easing.transform(((clock.value - delay) / millis).coerceIn(0f, 1f))

    /** The entrance height of bar [index] of the call wave, from [WAVE_START] to 1. */
    fun barGrow(index: Int): Float =
        WAVE_START + (1f - WAVE_START) * progress(index * BAR_STAGGER, BAR_GROW_MILLIS, Easings.Sheet)
}

@Composable
private fun rememberEntrance(): Entrance {
    val reduced = SyncDesk.motion.reduced
    val clock = remember { Animatable(if (reduced) ENTRANCE_END.toFloat() else 0f) }
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            fade.animateTo(1f, tween(Durations.REDUCED_FADE))
        } else {
            clock.animateTo(ENTRANCE_END.toFloat(), tween(ENTRANCE_END, easing = LinearEasing))
        }
    }
    return remember { Entrance(clock, fade) }
}

/** Rises [distance] and fades in as the entrance reaches [delay]; reads the clock only while drawing. */
internal fun Modifier.rise(entrance: Entrance, delay: Int, millis: Int = SUMMARY_MILLIS, distance: Dp = Distances.enterRise): Modifier =
    graphicsLayer {
        translationY = (1f - entrance.progress(delay, millis, Easings.Standard)) * distance.toPx()
        alpha = entrance.progress(delay, millis, Easings.Decelerate)
    }

/** Where the bars start growing from, as a fraction of their height. */
internal const val WAVE_START = 0.15f

private const val BAR_STAGGER = 5
private const val BAR_GROW_MILLIS = 500
internal const val SUMMARY_MILLIS = 600
internal const val SUMMARY_STAGGER = 90
private const val LATER_DELAY = 250
private const val STAGGER = 50
private const val WAVE_FADE_MILLIS = 300

/** The entrance is over by then: the last bar starts at 170 x 5 ms and grows for 0.5 s. */
private const val ENTRANCE_END = 1_500

private const val SWITCH_EXIT_MILLIS = 150
private const val SWITCH_ENTER_DELAY = 100
private const val SWITCH_GROW_MILLIS = 380

private val BODY_TOP = 204.dp
private val BODY_WIDTH = 960.dp
private val LIST_WIDTH = 506.dp
private val WAVE_TOP = 700.dp
private val BOTTOM_ROW_HEIGHT = 232.dp
private val FULL_CARD_WIDTH = 330.dp
private val CARD_GAP = 16.dp

@Composable
private fun TopRow(state: SyncDeskState, onOpenOffice: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.contactDigest?.let { ContactsPill(digest = it, ux = state.ux) }
        BigButton(
            label = stringResource(R.string.office_title),
            onClick = onOpenOffice,
            icon = LineIcon.Laptop.vector(),
            horizontalPadding = 40.dp,
            modifier = Modifier.height(TOP_ROW_HEIGHT),
        )
    }
}

/** The contacts line and the office button are 100 high (`chabun.css` .P-unseen, .P-office). */
internal val TOP_ROW_HEIGHT = 100.dp
