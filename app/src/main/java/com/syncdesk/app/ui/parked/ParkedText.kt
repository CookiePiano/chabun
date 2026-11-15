package com.syncdesk.app.ui.parked

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.syncdesk.app.R
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.ContactDigest
import com.syncdesk.app.domain.ContactGroup
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.domain.fit
import com.syncdesk.app.ui.components.LineIcon
import java.time.LocalDate

// The texts of the parked screen built from the memo: times, lengths, causes and contacts. Every
// sentence is a whole string resource with only its numbers and names as placeholders (spec 9).

/** "오후 1:52" for a clock time "13:52"; the text as it is when it is not a clock time. */
@Composable
internal fun clockText(time: String): String {
    val seconds = clockSeconds(time) ?: return time
    val hour = seconds / 3600 % 24
    val minute = seconds / 60 % 60
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return stringResource(if (hour < 12) R.string.parked_time_am else R.string.parked_time_pm, hour12, minute)
}

/** When [memo]'s call started: "오후 1:52" today, "어제 오후 1:52" or "11월 13일 오후 1:52" before. */
@Composable
internal fun whenText(memo: CallMemo, today: LocalDate): String {
    val time = clockText(memo.startedAt)
    val date = runCatching { LocalDate.parse(memo.date) }.getOrNull()
    if (date == null || date == today) return time
    val day = if (date == today.minusDays(1)) {
        stringResource(R.string.parked_yesterday)
    } else {
        stringResource(R.string.parked_date, date.monthValue, date.dayOfMonth)
    }
    return if (time.isBlank()) day else stringResource(R.string.parked_day_time, day, time)
}

/** "8분 12초" (or, [short], "8분") for a call [totalSeconds] long; null when the length is unknown. */
@Composable
internal fun lengthText(totalSeconds: Int?, short: Boolean = false): String? {
    val total = totalSeconds ?: return null
    if (short) return stringResource(R.string.parked_length_minutes, ((total + 30) / 60).coerceAtLeast(1))
    val minutes = total / 60
    val seconds = total % 60
    return when {
        minutes == 0 -> stringResource(R.string.parked_length_seconds, seconds)
        seconds == 0 -> stringResource(R.string.parked_length_minutes, minutes)
        else -> stringResource(R.string.parked_length_minutes_seconds, minutes, seconds)
    }
}

/** "2:40": a moment of the call, counted from its start. */
@Composable
internal fun elapsedText(seconds: Int): String = stringResource(R.string.parked_elapsed, seconds / 60, seconds % 60)

/** "오후 1:52 · 8분 12초", or whichever of the two is known. */
@Composable
internal fun timeAndLength(time: String, length: String?): String = when {
    length == null -> time
    time.isBlank() -> length
    else -> stringResource(R.string.parked_time_and_length, time, length)
}

/** The sheet's subtitle: "김 팀장 · 오후 1:52". */
@Composable
internal fun sheetSubtitle(memo: CallMemo, today: LocalDate, ux: UxLimits): String {
    val time = whenText(memo, today)
    return if (time.isBlank()) ux.fit(memo.title) else stringResource(R.string.parked_sheet_subtitle, ux.fit(memo.title), time)
}

@Composable
internal fun causeLabel(cause: MissCause): String = stringResource(
    when (cause) {
        MissCause.LeftTurn -> R.string.parked_cause_left_turn
        MissCause.Rain -> R.string.parked_cause_rain
        MissCause.Noise -> R.string.parked_cause_noise
        MissCause.Driving -> R.string.parked_cause_driving
    },
)

/** The icon beside a cause, as in `chabun.js` (turn, rain, wind); the road in general has none of its own. */
internal fun causeIcon(cause: MissCause): LineIcon = when (cause) {
    MissCause.LeftTurn -> LineIcon.Turn
    MissCause.Rain -> LineIcon.Rain
    MissCause.Noise -> LineIcon.Wind
    MissCause.Driving -> LineIcon.Alert
}

/** "받지 않은 전화 2건 · 메시지 5건", or the one kind there is. */
@Composable
internal fun contactCounts(digest: ContactDigest): String {
    val calls = digest.missedCalls?.count ?: 0
    val messages = digest.messages?.count ?: 0
    return when {
        calls > 0 && messages > 0 -> stringResource(R.string.parked_contacts_both, calls, messages)
        calls > 0 -> stringResource(R.string.parked_contacts_calls, calls)
        else -> stringResource(R.string.parked_contacts_messages, messages)
    }
}

/** "이 과장, 어머니 · 박 대리 외 2명": who called, then who wrote. */
@Composable
internal fun contactNames(digest: ContactDigest): String {
    val resources = LocalContext.current.resources
    return listOfNotNull(digest.missedCalls, digest.messages).joinToString(SEPARATOR) { people(resources, it) }
}

private fun people(resources: Resources, group: ContactGroup): String {
    val names = group.names.joinToString(", ")
    return if (group.others > 0) resources.getString(R.string.parked_contact_names_more, names, group.others) else names
}

private const val SEPARATOR = " · "
