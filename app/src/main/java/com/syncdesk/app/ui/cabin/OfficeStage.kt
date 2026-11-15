package com.syncdesk.app.ui.cabin

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.OfficeScene
import com.syncdesk.app.domain.OfficeStep
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.ui.components.LineIcon
import com.syncdesk.app.ui.theme.Easings
import com.syncdesk.app.ui.theme.SyncDesk
import com.syncdesk.app.ui.theme.SyncDeskColors
import com.syncdesk.app.ui.theme.SyncDeskShapes
import com.syncdesk.app.ui.theme.stageBackground
import com.syncdesk.app.ui.theme.tabularNumbers
import kotlin.math.roundToInt

/**
 * The always-dark office stage. Vehicle reports set the pose; the current office step only
 * highlights the part being operated. The stage never starts or completes a vehicle action.
 * The caller places this at (48, 36), sized 968 x 1138 design dp.
 */
@Composable
fun OfficeStage(state: SyncDeskState, modifier: Modifier = Modifier) {
    val colors = SyncDesk.colors
    val motion = SyncDesk.motion
    val target = state.officeStageFrame()
    val base = ImageBitmap.imageResource(R.drawable.car_base)
    val seat = ImageBitmap.imageResource(R.drawable.car_seat)
    val wheel = ImageBitmap.imageResource(R.drawable.car_wheel)
    val worker = remember { WorkerOutline() }
    Box(
        modifier
            .clip(SyncDeskShapes.stage)
            .stageBackground()
            .border(2.dp, colors.stageLine, SyncDeskShapes.stage)
            // The adjacent panel already reads every step, cabin setting and USB power value.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (motion.reduced) {
            // No pan or zoom, and no moving parts: reported poses replace one another by fade.
            Crossfade(
                targetState = target.copy(zoom = OVERVIEW_SCALE),
                animationSpec = motion.fade(),
                modifier = Modifier.fillMaxSize(),
                label = "officeReducedPose",
            ) { frame ->
                OfficeCar(frame, base, seat, wheel, worker, pulse = 1f)
            }
        } else {
            val frame = animatedFrame(target, state.office.paused)
            val pulse = usbPulse(frame.usb > 0f && !state.office.paused)
            OfficeCar(frame, base, seat, wheel, worker, pulse)
        }
    }
}

/** All lengths use the web mockup's 720 x 1200 car coordinate space. */
@Immutable
private data class StageFrame(
    val wheelY: Float,
    val wheelScale: Float,
    val seatY: Float,
    val seatScale: Float,
    val cushionScale: Float,
    val backrestScale: Float,
    val workLight: Float,
    val faceLight: Float,
    val frontShade: Float,
    val sideShade: Float,
    val ambient: Float,
    val ambientColor: Color,
    val climate: Float,
    val locks: Float,
    val usb: Float,
    val usbWatts: Int,
    val worker: Float,
    val zoom: Float,
    val opacity: Float,
    val wheelMark: Float,
    val seatMark: Float,
    val cushionMark: Float,
    val backrestMark: Float,
    val undo: Boolean,
)

private fun SyncDeskState.officeStageFrame(): StageFrame {
    val before = office.snapshot ?: CabinState()
    val moving = office.move
    // A paused sequence still shows the pending part; it must not look reset or complete.
    val highlighted = moving?.step ?: office.pending?.step
    val mark = if (office.paused) 0.6f else 1f
    fun highlight(step: OfficeStep) = if (highlighted == step) mark else 0f
    fun owns(step: OfficeStep) = office.phase == OfficePhase.On ||
        step in office.completed || office.pending?.step == step
    val meeting = office.scene == OfficeScene.Meeting
    val hasLight = owns(OfficeStep.Lights) &&
        (cabin.readingLightOn || cabin.cabinLightOn)
    val shade = if (owns(OfficeStep.Shade)) cabin.shadePercent.coerceIn(0, 100) / 100f else 0f
    val fan = if (owns(OfficeStep.Climate) && cabin.climateOn) {
        cabin.fanLevel.toFloat() / ranges.fanMax.coerceAtLeast(1)
    } else {
        0f
    }
    val wheelUp = cabin.wheel.up - before.wheel.up
    val wheelForward = cabin.wheel.forward - before.wheel.forward
    return StageFrame(
        wheelY = (-0.65f * wheelUp - 0.28f * wheelForward).coerceIn(-80f, 80f),
        wheelScale = (1f - wheelUp * 0.0012f).coerceIn(0.88f, 1.12f),
        seatY = ((cabin.seatSlide - before.seatSlide) * 0.95f).coerceIn(-80f, 80f),
        seatScale = (1f + (cabin.seatHeight - before.seatHeight) * 0.001f).coerceIn(0.9f, 1.1f),
        cushionScale = (1f + (cabin.cushionLength - before.cushionLength) * 0.003f +
            (cabin.cushionTilt - before.cushionTilt) * 0.001f).coerceIn(0.72f, 1.2f),
        backrestScale = (1f + (cabin.seatRecline - before.seatRecline) * 0.002f -
            (cabin.lumbar - before.lumbar) * 0.0008f).coerceIn(0.84f, 1.2f),
        workLight = if (hasLight && !meeting && cabin.readingLightOn) 1f else 0f,
        faceLight = if (hasLight && meeting) 1f else 0f,
        frontShade = if (meeting) 0f else shade,
        sideShade = if (meeting) shade else 0f,
        ambient = if (owns(OfficeStep.Lights) && meeting) cabin.ambientPercent / 100f else 0f,
        ambientColor = Color(cabin.ambientColor).copy(alpha = 1f),
        climate = fan.coerceIn(0f, 1f),
        locks = if (owns(OfficeStep.DoorLock) && cabin.doorsLocked) 1f else 0f,
        // Actual reported output is displayed, never the desired limit or a mock 140 W.
        usb = if (owns(OfficeStep.Usb) && usb.on) 1f else 0f,
        usbWatts = usb.suppliedWatts,
        worker = if (office.phase == OfficePhase.On) 1f else 0f,
        zoom = if (office.phase == OfficePhase.On) OFFICE_SCALE else OVERVIEW_SCALE,
        opacity = if (office.phase == OfficePhase.Off && (ux.noSetup || !signals.deskOpen)) 0.4f else 1f,
        wheelMark = highlight(OfficeStep.SteeringWheel),
        seatMark = highlight(OfficeStep.SeatPosition),
        cushionMark = highlight(OfficeStep.Cushion),
        backrestMark = highlight(OfficeStep.Backrest),
        undo = office.pending?.undo == true,
    )
}

@Composable
private fun animatedFrame(target: StageFrame, paused: Boolean): StageFrame = target.copy(
    wheelY = followReport(target.wheelY, 1300, paused),
    wheelScale = followReport(target.wheelScale, 1300, paused),
    seatY = followReport(target.seatY, 1300, paused),
    seatScale = followReport(target.seatScale, 1300, paused),
    cushionScale = followReport(target.cushionScale, 900, paused),
    backrestScale = followReport(target.backrestScale, 1100, paused),
    workLight = fadeValue(target.workLight, 1200),
    faceLight = fadeValue(target.faceLight, 1200),
    frontShade = fadeValue(target.frontShade, 1200),
    sideShade = fadeValue(target.sideShade, 1200),
    ambient = fadeValue(target.ambient, 1200),
    ambientColor = animateColorAsState(
        targetValue = target.ambientColor,
        animationSpec = SyncDesk.motion.fade(1200, Easings.Breathe),
        label = "officeAmbientColor",
    ).value,
    climate = fadeValue(target.climate, 700),
    locks = fadeValue(target.locks, 400),
    usb = fadeValue(target.usb, 400),
    worker = fadeValue(target.worker, 450, delay = if (target.worker > 0f) 1000 else 0),
    zoom = followReport(target.zoom, 1400, paused),
    opacity = fadeValue(target.opacity, 500),
    wheelMark = fadeValue(target.wheelMark, 200),
    seatMark = fadeValue(target.seatMark, 200),
    cushionMark = fadeValue(target.cushionMark, 200),
    backrestMark = fadeValue(target.backrestMark, 200),
)

/** A changed report interrupts the previous move; pausing cancels it at the reported position. */
@Composable
private fun followReport(target: Float, duration: Int, paused: Boolean): Float {
    val motion = SyncDesk.motion
    val value = remember { Animatable(target) }
    LaunchedEffect(target, paused, motion.reduced) {
        if (paused || motion.reduced) value.snapTo(target)
        else value.animateTo(target, motion.move(duration, Easings.Standard))
    }
    return value.value
}

@Composable
private fun fadeValue(target: Float, duration: Int, delay: Int = 0): Float {
    val motion = SyncDesk.motion
    val value = remember { Animatable(target) }
    LaunchedEffect(target, motion.reduced) {
        value.animateTo(target, motion.fade(duration, Easings.Breathe, delay))
    }
    return value.value
}

@Composable
private fun usbPulse(enabled: Boolean): Float {
    if (!enabled || SyncDesk.motion.reduced) return 1f
    val pulse by rememberInfiniteTransition(label = "officeUsb").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = Easings.Breathe), RepeatMode.Reverse),
        label = "officeUsbGlow",
    )
    return pulse
}

@Composable
private fun OfficeCar(
    frame: StageFrame,
    base: ImageBitmap,
    seat: ImageBitmap,
    wheel: ImageBitmap,
    worker: WorkerOutline,
    pulse: Float,
) {
    val colors = SyncDesk.colors
    val zoomProgress = ((frame.zoom - OVERVIEW_SCALE) / (OFFICE_SCALE - OVERVIEW_SCALE)).coerceIn(0f, 1f)
    val badgeScale = 1f - zoomProgress * 0.4f
    val usbLabel = if (frame.usbWatts > 0) {
        stringResource(R.string.office_ui_stage_usb_power, frame.usbWatts)
    } else {
        stringResource(R.string.office_ui_stage_usb)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(720.dp, 1200.dp)
                .graphicsLayer {
                    scaleX = frame.zoom
                    scaleY = frame.zoom
                    translationX = 15.dp.toPx() * zoomProgress * frame.zoom
                    translationY = 56.dp.toPx() * zoomProgress * frame.zoom
                    alpha = frame.opacity
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                scale(size.width / CAR_WIDTH, size.height / CAR_HEIGHT, pivot = Offset.Zero) {
                    drawCarShadow()
                    drawImage(base, dstSize = IntSize(720, (SOURCE_HEIGHT / SOURCE_SCALE).roundToInt()), filterQuality = FilterQuality.High)
                    drawSeat(seat, frame)
                    translate(top = frame.wheelY) {
                        scale(frame.wheelScale, frame.wheelScale, WHEEL_CENTER) {
                            drawPart(wheel, WHEEL_ORIGIN, Size(178f / SOURCE_SCALE, 178f / SOURCE_SCALE))
                        }
                    }
                    drawEnvironment(frame, colors, pulse)
                    drawPartHighlights(frame, colors.stageAccent)
                    if (frame.worker > 0f) {
                        drawUsbCable(frame, colors.stageAccent)
                        translate(top = frame.seatY) {
                            worker.draw(this, frame.worker, colors.stageInk)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .offset((394f - 14f * zoomProgress).dp, (422f + 11f * zoomProgress).dp)
                    .graphicsLayer {
                        alpha = frame.usb
                        scaleX = badgeScale
                        scaleY = badgeScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .height(60.dp)
                    .background(STAGE_LABEL, SyncDeskShapes.pill)
                    .border(2.dp, colors.stageInk.copy(alpha = 0.22f), SyncDeskShapes.pill)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LineIcon.Zap.vector(), contentDescription = null, tint = colors.stageInk, modifier = Modifier.size(28.dp))
                Text(usbLabel, color = colors.stageInk, style = SyncDesk.type.bodyStrong.tabularNumbers(), maxLines = 1)
            }
        }
    }
}

private fun DrawScope.drawPart(bitmap: ImageBitmap, origin: Offset, dimensions: Size) {
    drawImage(
        bitmap,
        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
        dstSize = IntSize(dimensions.width.roundToInt(), dimensions.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawSeat(bitmap: ImageBitmap, frame: StageFrame) {
    val width = 253f / SOURCE_SCALE
    val split = 174
    val topHeight = split / SOURCE_SCALE
    val lowerHeight = (285 - split) / SOURCE_SCALE
    val join = SEAT_ORIGIN.y + topHeight
    translate(top = frame.seatY) {
        scale(frame.seatScale, frame.seatScale, Offset(SEAT_ORIGIN.x + width / 2, join)) {
            // The source contains one seat. Its cushion and backrest meet at the same seam, so
            // separate reported adjustments can shorten either part without moving the other.
            drawImage(
                bitmap, srcOffset = IntOffset.Zero, srcSize = IntSize(253, split + 2),
                dstOffset = IntOffset(SEAT_ORIGIN.x.roundToInt(), (join - topHeight * frame.cushionScale).roundToInt()),
                dstSize = IntSize(width.roundToInt(), (topHeight * frame.cushionScale + 2).roundToInt()),
                filterQuality = FilterQuality.High,
            )
            drawImage(
                bitmap, srcOffset = IntOffset(0, split), srcSize = IntSize(253, 285 - split),
                dstOffset = IntOffset(SEAT_ORIGIN.x.roundToInt(), join.roundToInt()),
                dstSize = IntSize(width.roundToInt(), (lowerHeight * frame.backrestScale).roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

private fun DrawScope.drawCarShadow() {
    val center = Offset(360f, 650f)
    scale(0.43f, 1f, center) {
        drawCircle(
            Brush.radialGradient(0f to Color.Black.copy(alpha = 0.6f), 0.7f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent, center = center, radius = 650f),
            radius = 650f,
            center = center,
        )
    }
}

private fun DrawScope.drawUsbCable(frame: StageFrame, accent: Color) {
    val cable = Path().apply {
        moveTo(USB_PORT.x, USB_PORT.y)
        cubicTo(358f, 494f, 342f, 514f + frame.seatY, 312f, 520f + frame.seatY)
    }
    val alpha = frame.worker * frame.usb
    drawPath(cable, accent, alpha = alpha * 0.16f, style = Stroke(9f, cap = StrokeCap.Round))
    drawPath(cable, accent, alpha = alpha, style = Stroke(3f, cap = StrokeCap.Round))
}

private fun DrawScope.drawEnvironment(frame: StageFrame, colors: SyncDeskColors, pulse: Float) {
    glow(Offset(261f, 520f + frame.seatY), 115f, 100f, colors.warmLight, frame.workLight)
    glow(Offset(261f, 605f + frame.seatY), 115f, 105f, colors.stageInk.copy(alpha = 0.42f), frame.faceLight)
    drawShade(Offset(176f, 262f), Size(368f, 58f), frame.frontShade, vertical = false)
    drawShade(Offset(138f, 470f), Size(26f, 430f), frame.sideShade, vertical = true)
    drawShade(Offset(556f, 470f), Size(26f, 430f), frame.sideShade, vertical = true)
    if (frame.ambient > 0f) {
        for (x in floatArrayOf(173f, 547f)) {
            drawLine(frame.ambientColor, Offset(x, 476f), Offset(x, 896f), 20f, StrokeCap.Round, alpha = 0.15f * frame.ambient)
            drawLine(frame.ambientColor, Offset(x, 476f), Offset(x, 896f), 4f, StrokeCap.Round, alpha = frame.ambient)
        }
    }
    if (frame.climate > 0f) {
        val reach = 25f + 65f * frame.climate
        for (x in floatArrayOf(196f, 332f, 388f, 524f)) {
            for (side in -1..1) {
                val stream = Path().apply {
                    moveTo(x + side * 7f, 392f)
                    quadraticTo(x + side * 9f, 392f + reach / 2, x + side * 13f, 392f + reach)
                }
                drawPath(stream, Brush.verticalGradient(listOf(colors.stageAccent.copy(alpha = 0.5f), Color.Transparent), 392f, 392f + reach), style = Stroke(3f, cap = StrokeCap.Round))
            }
        }
    }
    val zoom = ((frame.zoom - OVERVIEW_SCALE) / (OFFICE_SCALE - OVERVIEW_SCALE)).coerceIn(0f, 1f)
    drawLock(Offset(112f, 640f), 1f - zoom * 0.36f, colors.stageAccent, frame.locks)
    drawLock(Offset(608f - zoom * 21f, 640f), 1f - zoom * 0.36f, colors.stageAccent, frame.locks)
    val portRadius = 13f - zoom * 5f
    glow(USB_PORT, portRadius * 3.8f, portRadius * 3.8f, colors.stageAccent.copy(alpha = 0.55f), frame.usb * pulse)
    drawCircle(colors.stageAccent, portRadius, USB_PORT, alpha = frame.usb)
    drawCircle(colors.stageAccent, portRadius * 1.6f, USB_PORT, alpha = frame.usb * 0.22f * pulse, style = Stroke(2f))
}

private fun DrawScope.glow(center: Offset, rx: Float, ry: Float, color: Color, amount: Float) {
    if (amount <= 0f) return
    scale(1f, ry / rx, center) {
        drawCircle(
            Brush.radialGradient(0f to color, 0.55f to color.copy(alpha = color.alpha * 0.4f), 1f to Color.Transparent, center = center, radius = rx),
            rx, center, alpha = amount,
        )
    }
}

private fun DrawScope.drawShade(origin: Offset, dimensions: Size, amount: Float, vertical: Boolean) {
    if (amount <= 0f) return
    val radius = CornerRadius(if (vertical) 13f else 26f)
    drawRoundRect(SHADE_FILL, origin, dimensions, radius, alpha = amount)
    drawRoundRect(Color.White, origin, dimensions, radius, alpha = 0.22f * amount, style = Stroke(2f))
    if (vertical) {
        for (y in 12 until dimensions.height.toInt() - 8 step 20) {
            drawLine(Color.White, origin + Offset(4f, y.toFloat()), origin + Offset(dimensions.width - 4, y.toFloat()), 2f, alpha = 0.07f * amount)
        }
    } else {
        for (x in 20 until dimensions.width.toInt() - 12 step 20) {
            drawLine(Color.White, origin + Offset(x.toFloat(), 6f), origin + Offset(x.toFloat(), dimensions.height - 6), 2f, alpha = 0.07f * amount)
        }
    }
}

private fun DrawScope.drawLock(center: Offset, ratio: Float, accent: Color, amount: Float) {
    if (amount <= 0f) return
    scale(ratio, ratio, center) {
        drawCircle(STAGE_LABEL, 30f, center, alpha = amount)
        drawCircle(accent, 30f, center, alpha = amount, style = Stroke(3f))
        drawRoundRect(accent, center + Offset(-11f, -1f), Size(22f, 17f), CornerRadius(3f), alpha = amount, style = Stroke(2.5f))
        drawArc(accent, 180f, 180f, false, center + Offset(-6f, -12f), Size(12f, 20f), alpha = amount, style = Stroke(2.5f))
    }
}

private fun DrawScope.drawPartHighlights(frame: StageFrame, accent: Color) {
    val wheel = WHEEL_CENTER + Offset(0f, frame.wheelY)
    if (frame.wheelMark > 0f) {
        glow(wheel, 90f, 90f, accent.copy(alpha = 0.3f), frame.wheelMark)
        drawCircle(accent, 62f, wheel, alpha = frame.wheelMark, style = Stroke(5f))
        drawArrow(Offset(wheel.x, if (frame.undo) wheel.y + 105f else wheel.y - 105f), up = !frame.undo, accent, frame.wheelMark)
    }
    val seat = SEAT_ORIGIN + Offset(0f, frame.seatY)
    highlightBox(seat - Offset(5f, 5f), Size(158f, 186f), accent, frame.seatMark)
    highlightBox(seat + Offset(1f, 1f), Size(146f, 78f), accent, frame.cushionMark)
    highlightBox(seat + Offset(1f, 95f), Size(146f, 78f), accent, frame.backrestMark)
    if (frame.seatMark > 0f) drawArrow(seat + Offset(74f, 225f), up = frame.undo, accent, frame.seatMark)
}

private fun DrawScope.highlightBox(origin: Offset, dimensions: Size, color: Color, amount: Float) {
    if (amount <= 0f) return
    drawRoundRect(color, origin, dimensions, CornerRadius(28f), alpha = amount * 0.2f)
    drawRoundRect(color, origin, dimensions, CornerRadius(28f), alpha = amount * 0.12f, style = Stroke(15f))
    drawRoundRect(color, origin, dimensions, CornerRadius(28f), alpha = amount, style = Stroke(4f))
}

private fun DrawScope.drawArrow(center: Offset, up: Boolean, color: Color, amount: Float) {
    val direction = if (up) -1f else 1f
    for (i in 0..1) {
        val y = center.y + i * 24f
        val path = Path().apply {
            moveTo(center.x - 23f, y - direction * 12f)
            lineTo(center.x, y + direction * 12f)
            lineTo(center.x + 23f, y - direction * 12f)
        }
        drawPath(path, color, alpha = amount * if (i == 0) 1f else 0.5f, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Outline geometry ported from workerArt() in docs/design/chabun.js, on the same car grid. */
private class WorkerOutline {
    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
    private val thigh = path("M256 604C250 590 242 560 236 520C234 505 232 494 224 487C216 481 204 483 199 493C194 503 196 530 201 560C205 582 211 598 218 610Z")
    private val thighDetail = path("M203 500C208 493 220 492 227 499M214 506C218 540 226 574 236 600")
    private val arm = path("M203 622C196 610 196 592 203 580C208 572 214 566 221 558L236 540L246 548L232 566C226 574 223 584 223 596L223 606Z")
    private val armDetail = path("M231 546L242 554M206 584C211 587 216 586 219 581M209 606C211 600 211 594 209 588")
    private val palm = path("M235 537C232 529 237 522.5 244 522.5C250.5 522.5 254 529 251.5 536.5C249 542 238 542 235 537Z")
    private val fingers = path("M237.5 526L236.2 517M241 524.5L240.5 513.5M245 524.5L245.5 512.5M248.8 525.5L250.5 514.5M250.5 533L255.5 526.5")
    private val screen = path("M216 458h90l5 30H211Z")
    private val screenDetail = path("M221 462.5h80l3.5 21.5h-87ZM224.5 466h34M224 469.5h40M223.5 473h28M223 476.5h37M222.5 480h22M270 466.5h28.5l2 14h-32ZM275 478v-4.5M280 478v-8M285 478v-6M290 478v-9.5M295 478v-3.5")
    private val shoulders = path("M200 628C199 614 208 605 222 602C236 599 248 598 261 598C274 598 286 599 300 602C314 605 323 614 322 628C321 640 310 646 296 649C285 651 273 652 261 652C249 652 237 651 226 649C212 646 201 640 200 628Z")
    private val shoulderDetail = path("M240 630C246 641 276 641 282 630M240 624C228 619 214 617 204 621M282 624C294 619 308 617 318 621M216 641C238 647 284 647 306 641")
    private val head = path("M261 580C276 580 285 592 285 607C285 622 275 633 261 633C247 633 237 622 237 607C237 592 246 580 261 580Z")
    private val hair = path("M243 593C250 585 272 585 279 593M252 589C256 599 257 611 254 623M252 589C262 591 274 597 279 607M256 600C266 602 274 608 277 617M250 593C245 599 242 606 242 615")
    private val ears = path("M257 582C258 576.5 264 576.5 265 582M237.5 602C231 602 231 615 237.5 615M284.5 602C291 602 291 615 284.5 615")

    fun draw(scope: DrawScope, alpha: Float, ink: Color) = with(scope) {
        val edge = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val fine = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun outline(shape: Path) {
            drawPath(shape, WORKER_SHADOW, alpha = 0.78f * alpha)
            drawPath(shape, ink, alpha = 0.08f * alpha, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(shape, ink, alpha = alpha, style = edge)
        }
        fun detail(shape: Path) = drawPath(shape, ink, alpha = alpha, style = fine)
        fun paired(block: () -> Unit) {
            block()
            scale(-1f, 1f, Offset(261f, 0f)) { block() }
        }
        paired { outline(thigh); detail(thighDetail) }
        drawRoundRect(WORKER_SHADOW, Offset(211f, 488f), Size(100f, 58f), CornerRadius(7f), alpha = 0.78f * alpha)
        drawRoundRect(ink, Offset(211f, 488f), Size(100f, 58f), CornerRadius(7f), alpha = alpha, style = edge)
        for (row in 0..2) for (key in 0..9) {
            drawRoundRect(ink, Offset(220f + key * 8.4f, 497f + row * 6.5f), Size(6.2f, 4.6f), CornerRadius(1f), alpha = alpha, style = Stroke(1f))
        }
        for (key in 0..3) {
            drawRoundRect(ink, Offset(220f + key * 8.4f, 516.5f), Size(6.2f, 4.6f), CornerRadius(1f), alpha = alpha, style = Stroke(1f))
        }
        drawRoundRect(ink, Offset(253.6f, 516.5f), Size(31.4f, 4.6f), CornerRadius(1f), alpha = alpha, style = Stroke(1f))
        drawRoundRect(ink, Offset(245f, 525.5f), Size(32f, 15f), CornerRadius(3f), alpha = alpha, style = fine)
        drawLine(ink, Offset(245f, 536.5f), Offset(277f, 536.5f), 1.4f, alpha = alpha)
        drawLine(ink, Offset(261f, 536.5f), Offset(261f, 540.5f), 1.4f, alpha = alpha)
        drawRoundRect(ink, Offset(238f, 489.8f), Size(46f, 3.6f), CornerRadius(1.8f), alpha = alpha, style = fine)
        for (i in 0..5) {
            drawCircle(ink, 0.55f, Offset(215.3f, 499f + i * 4.2f), alpha = alpha)
            drawCircle(ink, 0.55f, Offset(306.7f, 499f + i * 4.2f), alpha = alpha)
        }
        outline(screen)
        detail(screenDetail)
        drawCircle(ink, 0.7f, Offset(261f, 460.3f), alpha = alpha)
        paired {
            outline(arm)
            detail(armDetail)
            drawPath(fingers, ink, alpha = alpha, style = Stroke(6.2f, cap = StrokeCap.Round))
            drawPath(fingers, WORKER_SHADOW, alpha = alpha, style = Stroke(3.4f, cap = StrokeCap.Round))
            outline(palm)
        }
        outline(shoulders)
        detail(shoulderDetail)
        detail(ears)
        outline(head)
        detail(hair)
    }
}

private const val CAR_WIDTH = 720f
private const val CAR_HEIGHT = 1200f
private const val OVERVIEW_SCALE = 0.86f
private const val OFFICE_SCALE = 1.8f
// docs/design/parts/car_parts.json: 1226 x 2036, k = 1226 / 720.
private const val SOURCE_SCALE = 1.7027778f
private const val SOURCE_HEIGHT = 2036f
private val WHEEL_ORIGIN = Offset(357f / SOURCE_SCALE, 640f / SOURCE_SCALE)
private val WHEEL_CENTER = Offset(446f / SOURCE_SCALE, 729f / SOURCE_SCALE)
private val SEAT_ORIGIN = Offset(325f / SOURCE_SCALE, 838f / SOURCE_SCALE)
private val USB_PORT = Offset(360f, 452f)
private val STAGE_LABEL = Color(0xE0080D24)
private val SHADE_FILL = Color(0xFF060918)
private val WORKER_SHADOW = Color(0xFF060A1E)
