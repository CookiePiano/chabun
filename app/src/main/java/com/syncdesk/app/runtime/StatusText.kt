package com.syncdesk.app.runtime

import android.content.res.Resources
import androidx.annotation.StringRes
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.ContactDigest
import com.syncdesk.app.domain.ContactGroup
import com.syncdesk.app.domain.DialogPhase
import com.syncdesk.app.domain.DialogStep
import com.syncdesk.app.domain.Hearing
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.SyncDeskMode
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.fit

/**
 * What the app is doing, in two short lines. Shown where there is no room for the screen: the
 * notification that stays up while the app works behind another one, and the status card.
 * [detail] is empty when the headline says it all.
 */
data class StatusText(val headline: String, val detail: String)

/** The one sentence that names a driving mode; the driving screen opens with the same one. */
@StringRes
fun SyncDeskMode.Driving.headlineRes(callActive: Boolean): Int = when (this) {
    SyncDeskMode.Standby -> if (callActive) R.string.standby_call_headline else R.string.standby_idle_headline
    SyncDeskMode.TakeOver -> R.string.takeover_headline
    is SyncDeskMode.Quieting -> when (hearing) {
        Hearing.Poor -> R.string.quiet_poor_headline
        Hearing.Bad -> R.string.quiet_bad_headline
        Hearing.Good, null -> R.string.quiet_headline
    }
    is SyncDeskMode.Capturing -> R.string.capture_headline
    is SyncDeskMode.Conversing -> when {
        phase == DialogPhase.Waiting -> R.string.dialog_waiting_headline
        step == DialogStep.Offer -> R.string.dialog_offer_headline
        step == DialogStep.Explain -> R.string.dialog_explain_headline
        step == DialogStep.Ask -> R.string.dialog_ask_headline
        else -> R.string.dialog_answer_headline
    }
}

/** The line under the headline while the cabin is tuned for a call: what the car is doing about it. */
@StringRes
fun SyncDeskMode.Quieting.supportingRes(): Int = when (hearing) {
    Hearing.Poor -> R.string.quiet_poor_supporting
    Hearing.Bad -> R.string.quiet_bad_supporting
    Hearing.Good, null -> R.string.quiet_supporting
}

/** The line under the headline of the talk after a call: what the car is doing, or what to say to it. */
@StringRes
fun SyncDeskMode.Conversing.supportingRes(): Int = when {
    phase == DialogPhase.Waiting -> R.string.dialog_waiting_supporting
    step == DialogStep.Offer -> if (phase == DialogPhase.Listening) R.string.voice_hint_offer else R.string.dialog_offer_supporting
    step == DialogStep.Explain -> R.string.dialog_explain_supporting
    step == DialogStep.Ask -> if (phase == DialogPhase.Listening) R.string.voice_hint_ask else R.string.dialog_ask_supporting
    else -> R.string.dialog_answer_supporting
}

private const val SEPARATOR = " · "

/**
 * The contacts the driver has not looked at, as one line: how many of each kind and from whom,
 * for example "받지 않은 전화 2건 (김 팀장, 어머니) · 메시지 5건 (박 대리, 정 매니저 외 1명)". Each kind names
 * its first [ContactDigest.MAX_NAMES] people and counts the rest. Never what a message says.
 */
fun contactLine(resources: Resources, digest: ContactDigest): String {
    fun people(group: ContactGroup): String {
        val names = group.names.joinToString(", ")
        return if (group.others > 0) {
            resources.getQuantityString(R.plurals.contact_names_more, group.others, names, group.others)
        } else {
            names
        }
    }
    return listOfNotNull(
        digest.missedCalls?.let { resources.getQuantityString(R.plurals.contact_missed_calls, it.count, it.count, people(it)) },
        digest.messages?.let { resources.getQuantityString(R.plurals.contact_messages, it.count, it.count, people(it)) },
    ).joinToString(SEPARATOR)
}

fun statusText(resources: Resources, state: SyncDeskState): StatusText {
    val (headline, detail) = when (val mode = state.mode) {
        is SyncDeskMode.Desk -> when {
            mode.office.paused -> resources.getString(R.string.status_card_office_paused) to
                resources.getString(R.string.status_card_office_paused_detail)
            mode.office.phase == OfficePhase.Applying -> resources.getString(R.string.status_card_office_applying) to
                resources.getString(R.string.status_card_office_applying_detail)
            mode.office.phase == OfficePhase.On -> resources.getString(R.string.status_card_office_on) to
                chargingDetail(resources, state)
            mode.office.phase == OfficePhase.Restoring -> resources.getString(R.string.status_card_office_restoring) to
                resources.getString(R.string.status_card_office_restoring_detail)
            else -> {
                val memos = state.tripMemos.size
                val title = if (memos > 0) {
                    resources.getQuantityString(R.plurals.status_card_memos, memos, memos)
                } else {
                    resources.getString(R.string.status_card_no_calls)
                }
                title to contactCounts(resources, state.contactDigest)
            }
        }
        // A handover or a demanding stretch takes priority over a transient cabin refusal.
        SyncDeskMode.TakeOver -> resources.getString(R.string.status_card_takeover) to
            resources.getString(R.string.status_card_takeover_detail)
        is SyncDeskMode.Capturing -> resources.getString(R.string.status_card_capturing) to
            resources.getString(R.string.status_card_capturing_detail)
        is SyncDeskMode.Conversing -> resources.getString(R.string.status_card_summary_ready) to
            resources.getString(R.string.status_card_summary_question)
        SyncDeskMode.Standby, is SyncDeskMode.Quieting -> when {
            state.shownRefusal != null -> resources.getString(
                if (state.shownRefusal == CabinPart.Window) R.string.status_card_window_refused
                else R.string.status_card_cabin_refused,
            ) to cabinDetail(resources, state.cabin)
            !state.autoQuietEnabled -> resources.getString(R.string.status_card_auto_off) to
                resources.getString(if (state.call.active) R.string.status_card_on_call else R.string.status_card_waiting)
            mode is SyncDeskMode.Quieting -> resources.getString(R.string.status_card_quieting) to
                cabinDetail(resources, state.cabin)
            else -> resources.getString(
                if (state.call.active) R.string.status_card_on_call else R.string.status_card_waiting,
            ) to ""
        }
    }
    return StatusText(state.ux.fit(headline), state.ux.fit(detail))
}

/** Reports confirmed cabin values, not the target of a request still in flight. */
private fun cabinDetail(resources: Resources, cabin: CabinState): String = when {
    !cabin.climateOn && cabin.windowOpenPercent == 0 -> resources.getString(R.string.status_card_fan_off_window_closed)
    !cabin.climateOn -> resources.getString(R.string.status_card_fan_off_window_open, cabin.windowOpenPercent)
    cabin.windowOpenPercent == 0 -> resources.getString(R.string.status_card_fan_window_closed, cabin.fanLevel)
    else -> resources.getString(R.string.status_card_fan_window_open, cabin.fanLevel, cabin.windowOpenPercent)
}

/** Never calls a disconnected or switched-off USB-C port a charging laptop. */
private fun chargingDetail(resources: Resources, state: SyncDeskState): String = when {
    !state.usb.on -> resources.getString(R.string.status_card_charging_off)
    state.usb.deviceWatts == null -> resources.getString(R.string.status_card_charging_disconnected)
    state.usb.suppliedWatts == 0 -> resources.getString(R.string.status_card_charging_waiting, 0)
    else -> resources.getString(R.string.status_card_charging, state.usb.suppliedWatts)
}

/** A complete localized line with counts only; contact names remain in the parked screen. */
private fun contactCounts(resources: Resources, digest: ContactDigest?): String {
    val calls = digest?.missedCalls?.count ?: 0
    val messages = digest?.messages?.count ?: 0
    return when {
        calls > 0 && messages > 0 -> resources.getString(R.string.status_card_contacts, calls, messages)
        calls > 0 -> resources.getString(R.string.status_card_missed_calls, calls)
        messages > 0 -> resources.getString(R.string.status_card_messages, messages)
        else -> ""
    }
}
