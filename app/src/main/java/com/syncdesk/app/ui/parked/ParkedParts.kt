package com.syncdesk.app.ui.parked

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncdesk.app.R
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.ContactDigest
import com.syncdesk.app.domain.MeetingMaterial
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.domain.fit
import com.syncdesk.app.ui.components.Avatar
import com.syncdesk.app.ui.components.CallWave
import com.syncdesk.app.ui.components.GlassCard
import com.syncdesk.app.ui.components.GlassDivider
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.MissedStretch
import com.syncdesk.app.ui.components.Tag
import com.syncdesk.app.ui.components.TagKind
import com.syncdesk.app.ui.components.waveHeights
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.chip
import com.syncdesk.app.ui.theme.glass
import com.syncdesk.app.ui.theme.pressScale
import com.syncdesk.app.ui.theme.tabularNumbers
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Top right: the contacts the driver has not looked at (`chabun.css` .P-unseen).

@Composable
internal fun ContactsPill(digest: ContactDigest, ux: UxLimits, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val people = (digest.missedCalls?.names.orEmpty() + digest.messages?.names.orEmpty()).distinct().take(2)
    Row(
        modifier = modifier
            .height(TOP_ROW_HEIGHT)
            .glass(RoundedCornerShape(50.dp))
            .padding(start = 18.dp, end = 34.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (people.isNotEmpty()) {
            Box(Modifier.width(STACK_AVATAR + STACK_STEP * (people.size - 1)).height(STACK_AVATAR)) {
                people.forEachIndexed { index, name ->
                    Avatar(
                        name = name,
                        size = STACK_AVATAR,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = STACK_STEP * index)
                            .border(3.dp, colors.glassStrong, SyncDeskShapes.circle),
                    )
                }
            }
        }
        Column {
            Text(
                text = ux.fit(contactCounts(digest)),
                style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 34.sp),
                color = colors.ink,
                maxLines = 1,
            )
            Text(
                text = ux.fit(contactNames(digest)),
                style = SyncDesk.type.small,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val STACK_AVATAR = 60.dp
private val STACK_STEP = 42.dp

// ---------------------------------------------------------------------------------------------
// Left: the caller and the key summary (`chabun.css` .P-main, .P-who, .P-sum).

@Composable
internal fun MemoHead(memo: CallMemo, today: LocalDate, ux: UxLimits, entrance: Entrance) {
    val colors = SyncDesk.colors
    val timing = remember(memo) { timing(memo) }
    val summary = memo.summary.let { lines -> ux.maxItems?.let { lines.take(it) } ?: lines }
    val promises = memo.notes.filter { it.byDriver }.mapNotNull { it.gist }.toSet()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.rise(entrance, 0),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = memo.title, size = 72.dp, contentDescription = null)
            Text(
                text = ux.fit(memo.title),
                style = SyncDesk.type.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() },
            )
            Text(
                text = timeAndLength(whenText(memo, today), lengthText(timing.totalSeconds)),
                style = SyncDesk.type.captionLarge.tabularNumbers(),
                color = colors.muted,
                maxLines = 1,
            )
            if (!memo.explained) Tag(text = stringResource(R.string.new_memo), kind = TagKind.Accent)
        }
        Spacer(Modifier.height(6.dp))
        summary.forEachIndexed { index, line ->
            SummaryLine(
                text = ux.fit(line),
                promise = line in promises,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .rise(entrance, SUMMARY_STAGGER * (index + 1)),
            )
        }
    }
}

/**
 * One key-summary line (40 / 58 B, -1 %). A promise of the driver's starts with the "내 약속" tag,
 * set in the text so the second line runs back under it, as in the design.
 */
@Composable
private fun SummaryLine(text: String, promise: Boolean, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val style = SyncDesk.type.title2
    if (!promise) {
        Text(text = text, style = style, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val tag = stringResource(R.string.parked_my_promise)
    val tagStyle = SyncDesk.type.smallStrong
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tagWidth = remember(tag, tagStyle, density) { measurer.measure(tag, tagStyle).size.width }
    val placeholder = with(density) { (tagWidth.toDp() + TAG_PADDING * 2 + 2.dp).toSp() }
    val annotated = buildAnnotatedString {
        appendInlineContent(TAG_ID, tag)
        append(' ')
        append(text)
    }
    val inline = mapOf(
        TAG_ID to InlineTextContent(Placeholder(placeholder, TAG_HEIGHT, PlaceholderVerticalAlign.Center)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Tag(text = tag, kind = TagKind.Glass)
            }
        },
    )
    Text(
        text = annotated,
        inlineContent = inline,
        style = style,
        color = colors.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private const val TAG_ID = "promise"
private val TAG_PADDING = 14.dp
private val TAG_HEIGHT = 36.sp

// ---------------------------------------------------------------------------------------------
// Right: the received calls (`chabun.css` .P-calls, .P-call).

@Composable
internal fun CallList(
    trip: List<CallMemo>,
    earlier: List<CallMemo>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    today: LocalDate,
    ux: UxLimits,
    modifier: Modifier = Modifier,
) {
    var earlierOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .heightIn(max = LIST_MAX_HEIGHT)
            .glass(SyncDeskShapes.cardLarge)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        trip.forEach { memo -> MemoRow(memo, memo.id == selectedId, today, ux) { onSelect(memo.id) } }
        if (earlier.isNotEmpty()) {
            if (trip.isNotEmpty()) GlassDivider(Modifier.padding(horizontal = 20.dp))
            EarlierToggle(open = earlierOpen, onToggle = { earlierOpen = !earlierOpen })
            if (earlierOpen) {
                earlier.forEach { memo -> MemoRow(memo, memo.id == selectedId, today, ux) { onSelect(memo.id) } }
            }
        }
    }
}

/** The list stops above the call wave (700 - 24 - 204) and scrolls past that. */
private val LIST_MAX_HEIGHT = 472.dp

@Composable
private fun MemoRow(memo: CallMemo, selected: Boolean, today: LocalDate, ux: UxLimits, onClick: () -> Unit) {
    val timing = remember(memo) { timing(memo) }
    CallRow(
        name = ux.fit(memo.title),
        detail = ux.fit(timeAndLength(whenText(memo, today), lengthText(timing.totalSeconds, short = true))),
        selected = selected,
        hasNewMemo = !memo.explained,
        onClick = onClick,
    )
}

/**
 * One received call: 104 high, rounded 32, avatar 64, the name (30 B) over the time (24 R) and
 * the new-memo dot. Like `ReceivedCallRow`, but the chip-on material of the chosen row fades in
 * and out (spec 8-2 "통화 바꾸기": the chosen row cross-fades), which the shared row does not do.
 * TalkBack reads the selected state (spec 10).
 */
@Composable
private fun CallRow(name: String, detail: String, selected: Boolean, hasNewMemo: Boolean, onClick: () -> Unit) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val shape = SyncDeskShapes.row
    val chosen by animateFloatAsState(if (selected) 1f else 0f, motion.fade(ROW_CROSSFADE_MILLIS), label = "callRow")
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val newMemo = stringResource(R.string.new_memo)
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .heightIn(min = Design.listRowHeight)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = chosen }
                .chip(shape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Design.listRowHeight)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = SyncDesk.type.bodyStrong.copy(lineHeight = 38.sp),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = detail, style = SyncDesk.type.small.tabularNumbers(), color = colors.muted, maxLines = 1)
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
}

private const val ROW_CROSSFADE_MILLIS = 250

/** "이전 통화": earlier drives' calls, folded until pressed. */
@Composable
private fun EarlierToggle(open: Boolean, onToggle: () -> Unit) {
    val colors = SyncDesk.colors
    val label = stringResource(if (open) R.string.parked_earlier_collapse else R.string.parked_earlier_expand)
    val state = stringResource(if (open) R.string.parked_unfolded else R.string.parked_folded)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Design.minTouch)
            .clip(SyncDeskShapes.row)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.parked_earlier),
            style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.muted,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Icon(
            imageVector = (if (open) LineIcon.Up else LineIcon.Down).vector(2f),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(32.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The call wave at 700 (`chabun.css` .P-ribbon).

@Composable
internal fun CallWaveSection(memo: CallMemo, timing: CallTiming, grow: (Int) -> Float, modifier: Modifier = Modifier) {
    val heights = remember(memo.id) { waveHeights(waveSeed(memo), WAVE_BARS) }
    val stretches = remember(memo, timing) { missedStretches(memo, timing) }
    val missed = stretches.map { MissedStretch(it.from, it.to, causeLabel(it.cause), causeIcon(it.cause)) }
    val total = timing.totalSeconds
    CallWave(
        heights = heights,
        missed = missed,
        startLabel = if (total != null) elapsedText(0) else clockText(memo.startedAt),
        endLabel = total?.let { elapsedText(it) },
        grow = grow,
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------------------------
// Bottom: the missed-part cards and the full-transcript card (`chabun.css` .P-row, .P-miss, .P-full).

/** The missed-part cards: up to two, one card spanning both places, or "못 들은 부분이 없습니다". */
@Composable
internal fun MissedCards(memo: CallMemo, ux: UxLimits) {
    val timing = remember(memo) { timing(memo) }
    val budget = ux.maxItems?.let { (it - memo.summary.size.coerceAtMost(it)).coerceAtLeast(0) }
    val missed = memo.missedPart.take(minOf(MAX_MISSED_CARDS, budget ?: MAX_MISSED_CARDS))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // When the platform's item limit leaves no room for the missed part, the place stays empty:
        // it never claims that nothing was missed.
        if (memo.missedPart.isEmpty()) {
            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 26.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LineIcon.Check.vector(2f), contentDescription = null, tint = SyncDesk.colors.accent, modifier = Modifier.size(40.dp))
                    Text(text = stringResource(R.string.parked_missed_none), style = SyncDesk.type.body, color = SyncDesk.colors.muted)
                }
            }
        } else {
            missed.forEach { note ->
                val index = memo.notes.indexOf(note)
                val offset = timing.offsets?.getOrNull(index)
                MissedCard(
                    note = note,
                    time = offset?.let { elapsedText(it) } ?: note.time,
                    ux = ux,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

private const val MAX_MISSED_CARDS = 2

@Composable
private fun MissedCard(note: MeetingNote, time: String, ux: UxLimits, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val cause = causeOf(note)
    GlassCard(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 26.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(causeIcon(cause).vector(2f), contentDescription = null, tint = colors.missInk, modifier = Modifier.size(30.dp))
                Text(
                    text = stringResource(R.string.parked_missed_part, causeLabel(cause)),
                    style = SyncDesk.type.smallStrong,
                    color = colors.missInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = time, style = SyncDesk.type.small.tabularNumbers(), color = colors.muted, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = ux.fit(note.text),
                style = SyncDesk.type.body,
                color = colors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "전체 내용": how many sentences and how long, and the round "go" mark; opens the sheet. */
@Composable
internal fun FullTranscriptCard(
    memo: CallMemo,
    timing: CallTiming,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = SyncDesk.colors
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val count = memo.notes.size
    val length = lengthText(timing.totalSeconds)
    val detail = if (length != null) {
        pluralStringResource(R.plurals.parked_full_detail, count, count, length)
    } else {
        pluralStringResource(R.plurals.parked_full_count, count, count)
    }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .glass(SyncDeskShapes.card)
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.parked_full_open),
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onOpen()
            }
            .padding(horizontal = 32.dp, vertical = 26.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text(
                text = stringResource(R.string.parked_full),
                style = SyncDesk.type.button.copy(lineHeight = 42.sp),
                color = colors.ink,
            )
            Text(text = detail, style = SyncDesk.type.small.tabularNumbers(), color = colors.muted)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(Design.minTouch)
                .clip(SyncDeskShapes.circle)
                .background(colors.solid),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LineIcon.Up.vector(), contentDescription = null, tint = colors.onSolid, modifier = Modifier.size(40.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The full-transcript sheet (`chabun.css` .line).

/**
 * Every sentence of the call: its moment (96 wide), who said it ("나" in accent for the driver)
 * and what was said, with the sentences the driver probably missed underlined in marker. Under
 * the platform's item limit, the newest sentences that fit after the summary and the missed part.
 * The meeting's materials follow for the latest call.
 */
@Composable
internal fun ColumnScope.Transcript(memo: CallMemo, timing: CallTiming, materials: List<MeetingMaterial>, ux: UxLimits) {
    val max = ux.maxItems
    val used = if (max == null) 0 else memo.summary.size + memo.missedPart.size.coerceAtMost(MAX_MISSED_CARDS)
    val indexed = memo.notes.withIndex().toList()
    val lines = if (max == null) indexed else indexed.takeLast((max - used).coerceAtLeast(0))
    val shownMaterials = if (max == null) materials else materials.take((max - used - lines.size).coerceAtLeast(0))
    lines.forEach { (index, note) ->
        TranscriptLine(
            time = timing.offsets?.getOrNull(index)?.let { elapsedText(it) } ?: note.time,
            speaker = if (note.byDriver) stringResource(R.string.parked_me) else ux.fit(note.speaker),
            byDriver = note.byDriver,
            missed = note.missed != null && !note.runsTheMeeting,
            text = ux.fit(note.text),
        )
    }
    if (shownMaterials.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        GlassDivider()
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.parked_materials),
            style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold),
            color = SyncDesk.colors.muted,
            modifier = Modifier.semantics { heading() },
        )
        shownMaterials.forEach { MaterialBlock(it, ux) }
    }
}

@Composable
private fun TranscriptLine(time: String, speaker: String, byDriver: Boolean, missed: Boolean, text: String) {
    val colors = SyncDesk.colors
    val missedLabel = stringResource(R.string.parked_line_missed)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) { if (missed) stateDescription = missedLabel },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = time,
            style = SyncDesk.type.small.tabularNumbers(),
            color = colors.muted,
            modifier = Modifier
                .width(96.dp)
                .padding(top = 4.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text = speaker, style = SyncDesk.type.smallStrong, color = if (byDriver) colors.accent else colors.muted)
            MarkedText(text = text, marked = missed)
        }
    }
}

/** 28 / 40 text; when [marked], each line underlined in marker from 55 % of its height down. */
@Composable
private fun MarkedText(text: String, marked: Boolean) {
    val colors = SyncDesk.colors
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val marker = colors.marker
    Text(
        text = text,
        style = SyncDesk.type.captionLarge,
        color = colors.ink,
        onTextLayout = { layout = it },
        modifier = if (marked) {
            Modifier.drawBehind {
                val result = layout ?: return@drawBehind
                for (line in 0 until result.lineCount) {
                    val top = result.getLineTop(line)
                    val bottom = result.getLineBottom(line)
                    val y = top + (bottom - top) * MARK_FROM
                    val left = result.getLineLeft(line)
                    drawRect(marker, topLeft = Offset(left, y), size = Size(result.getLineRight(line) - left, bottom - y))
                }
            }
        } else {
            Modifier
        },
    )
}

private const val MARK_FROM = 0.55f

@Composable
private fun MaterialBlock(material: MeetingMaterial, ux: UxLimits) {
    val colors = SyncDesk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = ux.fit(material.title), style = SyncDesk.type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(24.dp))
            Text(text = ux.fit(material.detail), style = SyncDesk.type.caption, color = colors.muted)
        }
        material.highlights.forEach { Text(text = ux.fit(it), style = SyncDesk.type.captionLarge, color = colors.muted) }
    }
}

// ---------------------------------------------------------------------------------------------
// No call on this drive (`chabun.css` .P-empty, .P-quiet; spec 9 "통화 없음").

@Composable
internal fun EmptyBody(entrance: Entrance) {
    val colors = SyncDesk.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .padding(start = Design.marginStartParked, top = EMPTY_TOP)
                .width(EMPTY_WIDTH)
                .rise(entrance, 0),
        ) {
            Box(
                Modifier
                    .size(148.dp)
                    .glass(SyncDeskShapes.circle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LineIcon.Phone.vector(2f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.parked_empty_title),
                style = SyncDesk.type.display2,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.parked_empty_body),
                style = SyncDesk.type.body.copy(lineHeight = 46.sp),
                color = colors.muted,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }
        // The quiet wave: a picture only, so TalkBack skips it.
        val heights = remember { waveHeights(QUIET_SEED, WAVE_BARS) }
        Box(
            Modifier
                .padding(start = Design.marginStartParked, end = Design.marginEnd, top = QUIET_TOP)
                .alpha(QUIET_ALPHA)
                .clearAndSetSemantics { },
        ) {
            CallWave(heights = heights, missed = emptyList(), grow = { entrance.barGrow(it) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val EMPTY_TOP = 330.dp
private val EMPTY_WIDTH = 900.dp
private val QUIET_TOP = 820.dp
private const val QUIET_ALPHA = 0.55f
private const val QUIET_SEED = 3
