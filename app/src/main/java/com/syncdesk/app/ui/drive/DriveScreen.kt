package com.syncdesk.app.ui.drive

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.AirDirection
import com.syncdesk.app.domain.CabinChange
import com.syncdesk.app.domain.CabinLimits
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinRanges
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.RoadDemand
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskMode
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.fit
import com.syncdesk.app.ui.components.BigButton
import com.syncdesk.app.ui.components.ButtonKind
import com.syncdesk.app.ui.components.Hairline
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.RailRow
import com.syncdesk.app.ui.components.RefusalNote
import com.syncdesk.app.ui.components.windStreams
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compatibility entry for hosts that used the old panel directly. The driving window now uses
 * one icon and one sentence, or the conversation with Gleo, with its action fixed at the bottom.
 */
@Composable
fun DrivePanel(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** False when the buttons are placed elsewhere, nearer the driver ([DriveActions]). */
    showActions: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        DriveContent(
            state = state,
            onEvent = onEvent,
            width = if (maxWidth < 700.dp) DriveWidth.Third else DriveWidth.Half,
            showActions = showActions,
        )
    }
}

@Composable
internal fun DriveContent(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    width: DriveWidth,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    var previousDialog by remember { mutableStateOf(state.dialog) }
    var endedMemoId by remember { mutableStateOf<Int?>(null) }
    // Keep the same conversation while an urgent driving state temporarily replaces its view.
    val conversationHistory = remember(state.dialog?.memoId) { mutableStateOf(emptyList<DriveTurn>()) }
    val canShowEnd = state.dialog == null && !state.call.active &&
        state.roadDemand == RoadDemand.Clear && state.shownRefusal == null &&
        !state.signals.deskOpen && state.autoQuietEnabled
    LaunchedEffect(state.dialog, canShowEnd) {
        val ended = previousDialog.takeIf { state.dialog == null }
        previousDialog = state.dialog
        endedMemoId = null
        if (canShowEnd && ended != null && state.memos.any { it.id == ended.memoId }) {
            // This is a brief visual confirmation of an observed close, not a simulated speech event.
            endedMemoId = ended.memoId
            delay(3000)
            endedMemoId = null
        }
    }
    Box(modifier.fillMaxSize()) {
        Crossfade(
            targetState = canShowEnd && endedMemoId != null,
            animationSpec = SyncDesk.motion.fade(millis = 200),
            label = "conversationEnd",
        ) { showingEnd ->
            if (showingEnd && canShowEnd) {
                DriveConversationEnd(state, width, Modifier.fillMaxSize())
            } else if (state.mode is SyncDeskMode.Conversing && state.shownRefusal == null) {
                DriveConversation(state, width, conversationHistory, Modifier.fillMaxSize())
            } else {
                val status = when {
                    state.mode == SyncDeskMode.TakeOver -> DriveStatus.TakeOver
                    state.mode is SyncDeskMode.Capturing -> DriveStatus.Capturing
                    state.shownRefusal == CabinPart.Window -> DriveStatus.WindowRefused
                    state.shownRefusal != null -> DriveStatus.CabinRefused
                    !state.autoQuietEnabled -> DriveStatus.AutoOff
                    state.mode is SyncDeskMode.Quieting -> DriveStatus.Quieting
                    else -> DriveStatus.Waiting
                }
                Crossfade(
                    targetState = status,
                    animationSpec = SyncDesk.motion.fade(),
                    label = "driveStatus",
                ) { shown ->
                    DriveStatusContent(state, shown, width)
                }
            }
        }
        if (showActions) {
            DriveActions(
                state,
                onEvent,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Design.driveInsetSide)
                    .padding(bottom = Design.driveInsetBottom),
            )
        }
    }
}

private enum class DriveStatus(val headline: Int, val icon: LineIcon, val warning: Boolean = false) {
    Waiting(R.string.drive_waiting, LineIcon.Mark),
    Quieting(R.string.drive_quieting, LineIcon.WindStreams),
    Capturing(R.string.drive_capturing, LineIcon.Note, warning = true),
    WindowRefused(R.string.drive_window_refused, LineIcon.Alert, warning = true),
    CabinRefused(R.string.drive_cabin_refused, LineIcon.Alert, warning = true),
    AutoOff(R.string.drive_auto_off, LineIcon.Off),
    TakeOver(R.string.drive_takeover, LineIcon.Alert, warning = true),
}

@Composable
private fun DriveStatusContent(state: SyncDeskState, status: DriveStatus, width: DriveWidth) {
    val colors = SyncDesk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Design.driveInsetSide)
            .padding(top = Design.driveInsetTop + 150.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(184.dp).glass(SyncDeskShapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (status == DriveStatus.Quieting) QuietWind() else status.icon.vector(strokeWidth = 2f),
                contentDescription = null,
                tint = if (status.warning) colors.missInk else colors.accent,
                modifier = Modifier.size(if (status == DriveStatus.Quieting) 96.dp else 80.dp),
            )
        }
        Spacer(Modifier.height(48.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val headline = state.ux.fit(stringResource(status.headline))
            val style = if (width == DriveWidth.Half) SyncDesk.type.title1Wide else SyncDesk.type.title1Drive
            Text(
                text = rememberWordWrappedText(headline, style, maxWidth, balance = true),
                style = style,
                color = colors.ink,
                textAlign = TextAlign.Center,
                softWrap = false,
                overflow = TextOverflow.Clip,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { text = AnnotatedString(headline) },
            )
        }
    }
}

/** Only the three wind strokes move: each shortens from the curl, keeping the left end fixed. */
@Composable
private fun QuietWind(): ImageVector {
    val reduced = SyncDesk.motion.reduced
    val streams = remember { List(3) { Animatable(if (reduced) WIND_END else 1f) } }
    LaunchedEffect(reduced) {
        if (reduced) {
            streams.forEach { it.snapTo(WIND_END) }
        } else {
            coroutineScope {
                streams.forEachIndexed { index, stream ->
                    launch {
                        delay(index * WIND_STAGGER_MILLIS)
                        stream.animateTo(WIND_END, tween(WIND_MILLIS, easing = Easings.Standard))
                    }
                }
            }
        }
    }
    return windStreams(streams.map { it.value }, strokeWidth = 2f)
}

/**
 * What the car tunes for a call, one bar each: fan, air direction, window, air intake and call
 * volume. While a call is tuned, each part the app has moved keeps a tick at the value in
 * [restore], where it returns when the call ends.
 */
@Composable
fun QuietRail(
    cabin: CabinState,
    restore: CabinChange?,
    ranges: CabinRanges,
    modifier: Modifier = Modifier,
    /** A part the vehicle just refused to move; said under its row. */
    refusal: CabinPart? = null,
) {
    Column(modifier.fillMaxWidth()) {
        val fan = fanText(cabin.fanLevel)
        RailRow(
            icon = R.drawable.ic_fan,
            label = stringResource(R.string.rail_fan),
            value = cabin.fanLevel,
            max = ranges.fanMax,
            segmented = ranges.fanMax <= MAX_SEGMENTS,
            valueText = fan,
            restore = restore?.fanLevel,
            description = railDescription(stringResource(R.string.rail_fan), fan, restore?.fanLevel?.let { fanText(it) }),
        )
        RefusalNote(refusal, CabinPart.Fan)
        Hairline()
        val direction = airDirectionText(cabin.airDirection)
        RailRow(
            icon = R.drawable.ic_fan,
            label = stringResource(R.string.rail_air_direction),
            value = if (cabin.airDirection.atFace) 1 else 0,
            max = 1,
            segmented = true,
            valueText = direction,
            restore = restore?.airDirection?.let { if (it.atFace) 1 else 0 },
            description = railDescription(
                stringResource(R.string.rail_air_direction), direction, restore?.airDirection?.let { airDirectionText(it) },
            ),
        )
        RefusalNote(refusal, CabinPart.AirDirection)
        Hairline()
        val window = windowText(cabin.windowOpenPercent)
        RailRow(
            icon = R.drawable.ic_window,
            label = stringResource(R.string.rail_window),
            value = cabin.windowOpenPercent,
            max = CabinLimits.WINDOW_MAX,
            valueText = window,
            restore = restore?.windowOpenPercent,
            description = railDescription(
                stringResource(R.string.rail_window), window, restore?.windowOpenPercent?.let { windowText(it) },
            ),
            segmented = false,
        )
        RefusalNote(refusal, CabinPart.Window)
        Hairline()
        val air = airText(cabin.recirculation)
        RailRow(
            icon = R.drawable.ic_recirculation,
            label = stringResource(R.string.rail_recirculation),
            value = if (cabin.recirculation) 1 else 0,
            max = 1,
            segmented = true,
            valueText = air,
            restore = restore?.recirculation?.let { if (it) 1 else 0 },
            description = railDescription(
                stringResource(R.string.rail_recirculation), air, restore?.recirculation?.let { airText(it) },
            ),
        )
        RefusalNote(refusal, CabinPart.Recirculation)
        Hairline()
        val volume = stringResource(R.string.value_level, cabin.callVolume)
        RailRow(
            icon = R.drawable.ic_call,
            label = stringResource(R.string.rail_call_volume),
            value = cabin.callVolume,
            max = ranges.callVolumeMax,
            segmented = ranges.callVolumeMax <= MAX_SEGMENTS,
            valueText = volume,
            restore = restore?.callVolume,
            description = railDescription(
                stringResource(R.string.rail_call_volume),
                volume,
                restore?.callVolume?.let { stringResource(R.string.value_level, it) },
            ),
        )
        RefusalNote(refusal, CabinPart.CallVolume)
    }
}

/** Where the air blows, by the vents it comes from. */
@Composable
private fun airDirectionText(direction: AirDirection): String = stringResource(
    when (direction) {
        AirDirection.Face -> R.string.value_air_face
        AirDirection.FaceAndFeet -> R.string.value_air_face_feet
        AirDirection.Feet -> R.string.value_air_feet
        AirDirection.Windshield -> R.string.value_air_windshield
        AirDirection.FaceAndWindshield -> R.string.value_air_face_windshield
        AirDirection.FeetAndWindshield -> R.string.value_air_feet_windshield
        AirDirection.All -> R.string.value_air_all
    },
)

/** Level 0 means the climate system is off, so the fan is not running at all. */
@Composable
private fun fanText(level: Int): String =
    if (level == 0) stringResource(R.string.value_off) else stringResource(R.string.value_level, level)

@Composable
private fun windowText(openPercent: Int): String =
    if (openPercent == 0) {
        stringResource(R.string.value_window_closed)
    } else {
        stringResource(R.string.value_window_open, openPercent)
    }

/** Inside air (recirculation) or outside air. */
@Composable
private fun airText(recirculation: Boolean): String =
    stringResource(if (recirculation) R.string.value_recirculation_on else R.string.value_recirculation_off)

@Composable
private fun railDescription(label: String, value: String, restore: String?): String =
    if (restore == null) {
        stringResource(R.string.rail_row_description, label, value)
    } else {
        stringResource(R.string.rail_row_description_restore_call, label, value, restore)
    }

/**
 * The single fixed action in every driving state. The label follows the actual setting;
 * switching it sends the existing domain event without changing the driving state locally.
 */
@Composable
fun DriveActions(
    state: SyncDeskState,
    onEvent: (SyncDeskEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BigButton(
        label = state.ux.fit(
            stringResource(if (state.autoQuietEnabled) R.string.drive_auto_quiet_off else R.string.drive_auto_quiet_on),
        ),
        onClick = { onEvent(SyncDeskEvent.AutoQuietToggled(!state.autoQuietEnabled)) },
        kind = ButtonKind.Glass,
        modifier = modifier.fillMaxWidth(),
    )
}

private const val WIND_END = 0.62f
private const val WIND_MILLIS = 700
private const val WIND_STAGGER_MILLIS = 60L

/** A level with more steps than this is drawn as one continuous bar. */
private const val MAX_SEGMENTS = 12
