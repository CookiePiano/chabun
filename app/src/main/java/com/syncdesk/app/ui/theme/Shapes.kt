package com.syncdesk.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii of spec section 3 and `chabun.css`. */
object SyncDeskShapes {
    /** The bottom sheet: only the top corners are rounded. */
    val sheet: Shape = RoundedCornerShape(topStart = 52.dp, topEnd = 52.dp)

    /** The office car stage and the first-use stage. */
    val stage: Shape = RoundedCornerShape(52.dp)

    /** Large glass cards: the received-call list, the office side panel's lists. */
    val cardLarge: Shape = RoundedCornerShape(44.dp)

    /** Glass cards: missed-part cards, the full-transcript card, office rows, consent cards. */
    val card: Shape = RoundedCornerShape(40.dp)

    /** A row of a list (received call). */
    val row: Shape = RoundedCornerShape(32.dp)

    /** An inset area inside a card (the picture area of a consent card). */
    val inset: Shape = RoundedCornerShape(26.dp)

    /** Big buttons: half their 112 height, a pill. */
    val button: Shape = RoundedCornerShape(56.dp)

    /** Work | Meeting: the outer track, and each 88-high segment. */
    val segmentTrack: Shape = RoundedCornerShape(56.dp)
    val segment: Shape = RoundedCornerShape(44.dp)

    /** Tags such as "내 약속", "새 메모" and the missed reason: a pill. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** The driver's turn: rounded except the corner pointing at the driver. */
    val driverTurn: Shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomEnd = 10.dp, bottomStart = 40.dp)

    /** An earlier driver's turn, smaller. */
    val driverTurnOld: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp)

    /** The missed-stretch band behind the call wave. */
    val missBand: Shape = RoundedCornerShape(16.dp)

    /** The Gleo icon: a quarter of its size. */
    val gleo: Shape = RoundedCornerShape(percent = 25)

    /** Avatars, step numbers, round icon buttons. */
    val circle: Shape = CircleShape
}
