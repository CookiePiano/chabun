package com.syncdesk.app.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncdesk.app.R
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.ui.components.BigButton
import com.syncdesk.app.ui.components.ButtonKind
import com.syncdesk.app.ui.components.CallWave
import com.syncdesk.app.ui.components.GleoIcon
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.components.MissedStretch
import com.syncdesk.app.ui.components.VoiceBars
import com.syncdesk.app.ui.components.waveHeights
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.DesignFrame
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.glass
import com.syncdesk.app.ui.theme.stageBackground

/**
 * First-use consent in the 1682 x 1210 app window. Four cards explain the day before the
 * recording notice and consent choices. The skeleton supplies the ground and glows.
 */
@Composable
fun OnboardingScreen(onEvent: (SyncDeskEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contentWidth = maxWidth - Design.marginStart - Design.marginEnd
        ConsentStage(
            Modifier.offset(x = Design.marginStart, y = Design.marginTop)
                .width(contentWidth).height(500.dp),
        )
        ConsentSteps(
            Modifier.offset(x = Design.marginStart, y = 560.dp).width(contentWidth),
        )
        Text(
            text = stringResource(R.string.drive_consent_recording_notice),
            style = SyncDesk.type.caption,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(x = Design.marginStart, y = 1005.dp)
                .width(contentWidth).then(entrance(delay = 1900, rise = 12.dp)),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = Design.marginStart, end = Design.marginEnd, bottom = Design.marginBottom)
                .fillMaxWidth().then(entrance(delay = 2000, rise = 0.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigButton(
                label = stringResource(R.string.drive_consent_later),
                onClick = { onEvent(SyncDeskEvent.ConsentDeferred) },
                kind = ButtonKind.Glass,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                label = stringResource(R.string.drive_consent_accept),
                onClick = { onEvent(SyncDeskEvent.ConsentGiven) },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** Preserves the earlier entry point while using the same consent design. */
@Composable
fun Onboarding(onEvent: (SyncDeskEvent) -> Unit, fillHeight: Boolean, modifier: Modifier = Modifier) {
    DesignFrame(
        designWidth = Design.appWindow.width,
        designHeight = Design.appWindow.height,
        modifier = modifier.then(if (fillHeight) Modifier.fillMaxSize() else Modifier.height(Design.appWindow.height)),
    ) {
        OnboardingScreen(onEvent = onEvent)
    }
}

/** Both themes use this dark hero, with all text below the camera's 200 dp reserved zone. */
@Composable
private fun ConsentStage(modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val car = painterResource(R.drawable.car_hero)
    Box(
        modifier.clip(SyncDeskShapes.stage).stageBackground(consent = true)
            .border(2.dp, colors.stageLine, SyncDeskShapes.stage),
    ) {
        Canvas(
            Modifier.fillMaxSize()
                .then(entrance(delay = 0, millis = 1300, rise = 0.dp, fromRight = 160.dp)),
        ) {
            val height = 640.dp.toPx()
            val width = height * car.intrinsicSize.width / car.intrinsicSize.height
            translate(left = size.width - 30.dp.toPx() - width, top = -70.dp.toPx()) {
                with(car) { draw(Size(width, height)) }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.stageAccent.copy(alpha = 0.16f)))),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 48.dp)
                .width(760.dp).then(entrance(delay = 250, millis = 700)),
        ) {
            Text(
                text = stringResource(R.string.drive_consent_tagline),
                style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.stageAccent,
                modifier = Modifier.clip(SyncDeskShapes.pill)
                    .background(colors.stageAccent.copy(alpha = 0.12f))
                    .border(2.dp, colors.stageAccent.copy(alpha = 0.35f), SyncDeskShapes.pill)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.drive_consent_title),
                style = SyncDesk.type.display1,
                color = colors.stageInk,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Composable
private fun ConsentSteps(modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val line by animateFloatAsState(
        targetValue = if (entered || motion.reduced) 1f else 0f,
        animationSpec = motion.move(millis = 1000, easing = Easings.Widen, delay = 700),
        label = "consentTimeline",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(42.dp).then(entrance(delay = 700, rise = 0.dp))) {
            val left = 20.dp.toPx()
            val y = 21.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.25f))),
                start = Offset(left, y),
                end = Offset(left + (size.width - left) * line, y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ConsentStep.entries.forEachIndexed { index, step ->
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.height(42.dp)
                            .then(entrance(delay = 800 + index * 250, rise = 0.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(SyncDeskShapes.circle).background(colors.ground)
                                .border(2.dp, colors.accent.copy(alpha = 0.45f), SyncDeskShapes.circle)
                                .padding(8.dp).background(colors.accent, SyncDeskShapes.circle),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(step.phase),
                            style = SyncDesk.type.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            modifier = Modifier.clip(SyncDeskShapes.pill).background(colors.ground)
                                .padding(start = 6.dp, end = 14.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ConsentCard(step, Modifier.then(entrance(delay = 900 + index * 120, millis = 600)))
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(step: ConsentStep, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    Column(
        modifier.fillMaxWidth().height(366.dp).glass(SyncDeskShapes.card)
            .padding(horizontal = 28.dp, vertical = 24.dp).semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(172.dp).clip(SyncDeskShapes.inset)
                .background(colors.ink.copy(alpha = 0.06f)).padding(horizontal = 22.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            when (step) {
                ConsentStep.Call -> FanPreview()
                ConsentStep.After -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GleoIcon(size = 88.dp)
                    VoiceBars(active = true, height = 64.dp, barWidth = 10.dp, gap = 8.dp)
                }
                ConsentStep.Parked -> CallWave(
                    heights = remember { waveHeights(seed = 1, count = 56) },
                    missed = remember { listOf(MissedStretch(0.34f, 0.40f), MissedStretch(0.76f, 0.82f)) },
                    barHeight = 100.dp,
                    barWidth = 4.dp,
                )
                ConsentStep.Work -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(LineIcon.Laptop.vector(1.8f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(72.dp))
                    PreviewLabel(LineIcon.Zap, stringResource(R.string.drive_consent_laptop_charging))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(text = stringResource(step.title), style = SyncDesk.type.button.copy(lineHeight = 42.sp), color = colors.ink)
        Spacer(Modifier.height(6.dp))
        UnbrokenWords(
            sentence = stringResource(step.detail),
            style = SyncDesk.type.caption.copy(lineHeight = 38.sp),
            color = colors.muted,
        )
    }
}

/**
 * Android's phrase breaking still splits Korean words on some platform versions. Measure the
 * same font at spaces so only layout gets line breaks; accessibility keeps the original sentence.
 */
@Composable
private fun UnbrokenWords(sentence: String, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val wrapped = remember(sentence, style, availableWidth, measurer) {
            val lines = mutableListOf<String>()
            var line = ""
            sentence.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                val width = measurer.measure(
                    text = AnnotatedString(candidate),
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                if (line.isNotEmpty() && width > availableWidth) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            (lines + line).joinToString("\n")
        }
        Text(
            text = wrapped,
            style = style,
            color = color,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(sentence) },
        )
    }
}

@Composable
private fun FanPreview() {
    val colors = SyncDesk.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.drive_consent_fan), style = SyncDesk.type.smallStrong, color = colors.ink)
                Text(stringResource(R.string.drive_consent_fan_level, 2), style = SyncDesk.type.smallStrong, color = colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(8) { index ->
                    Box(
                        Modifier.weight(1f).height(20.dp)
                            .background(if (index < 2) colors.accent else colors.ink.copy(alpha = 0.16f), SyncDeskShapes.pill),
                    )
                }
            }
        }
        PreviewLabel(LineIcon.Check, stringResource(R.string.drive_consent_window_closed))
    }
}

@Composable
private fun PreviewLabel(icon: LineIcon, label: String) {
    val colors = SyncDesk.colors
    Row(
        modifier = Modifier.glass(SyncDeskShapes.pill, shadow = false).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon.vector(2f), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Text(label, style = SyncDesk.type.smallStrong, color = colors.accent, maxLines = 2)
    }
}

/** Theme motion removes all translation and stagger, leaving a 200 ms fade when reduced. */
@Composable
private fun entrance(delay: Int, millis: Int = 450, rise: Dp = 28.dp, fromRight: Dp = 0.dp): Modifier {
    val motion = SyncDesk.motion
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val opacity by animateFloatAsState(
        if (entered) 1f else 0f,
        motion.fade(millis = millis, delay = delay),
        label = "consentOpacity",
    )
    val progress by animateFloatAsState(
        if (entered || motion.reduced) 1f else 0f,
        motion.move(millis = millis, easing = if (fromRight > 0.dp) Easings.Widen else Easings.Standard, delay = delay),
        label = "consentPosition",
    )
    return Modifier.graphicsLayer {
        alpha = opacity
        translationY = rise.toPx() * (1f - progress)
        translationX = fromRight.toPx() * (1f - progress)
    }
}

private enum class ConsentStep(@StringRes val phase: Int, @StringRes val title: Int, @StringRes val detail: Int) {
    Call(R.string.drive_consent_phase_call, R.string.drive_consent_quiet_title, R.string.drive_consent_quiet_detail),
    After(R.string.drive_consent_phase_after, R.string.drive_consent_gleo_title, R.string.drive_consent_gleo_detail),
    Parked(R.string.drive_consent_phase_parked, R.string.drive_consent_memo_title, R.string.drive_consent_memo_detail),
    Work(R.string.drive_consent_phase_work, R.string.drive_consent_office_title, R.string.drive_consent_office_detail),
}
