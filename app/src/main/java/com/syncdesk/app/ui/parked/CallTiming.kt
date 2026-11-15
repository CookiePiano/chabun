package com.syncdesk.app.ui.parked

import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason

/**
 * Why a stretch of a call was missed, as the parked screen names it (spec 9: "좌회전 중",
 * "실내 소음", "폭우"). The memo only keeps [MissedReason] (the road, or the noise), not which
 * demand of the road it was, so [LeftTurn] and [Rain] are not produced yet; a sentence missed for
 * the road is shown as [Driving] ("운전 중").
 */
internal enum class MissCause { LeftTurn, Rain, Noise, Driving }

/** The cause the parked screen shows for [note]. */
internal fun causeOf(note: MeetingNote): MissCause = when (note.missed) {
    MissedReason.Noise -> MissCause.Noise
    MissedReason.Driving, null -> MissCause.Driving
}

/** A stretch of the call wave the driver probably missed, as fractions of the call's length. */
internal data class Stretch(val from: Float, val to: Float, val cause: MissCause)

/**
 * Where each sentence of a call lies in it. [spans] holds, per note, the fraction of the call
 * from where it starts to where the next one starts. [offsets] are seconds from the start of the
 * call, and [totalSeconds] the call's length; both are null when the notes' times cannot be read,
 * in which case the sentences share the call evenly.
 */
internal data class CallTiming(
    val totalSeconds: Int?,
    val offsets: List<Int>?,
    val spans: List<ClosedFloatingPointRange<Float>>,
)

/** Seconds since midnight of a clock time "14:05" or "14:05:30"; null when it is not one. */
internal fun clockSeconds(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null
    return numbers[0] * 3600 + numbers[1] * 60 + (numbers.getOrNull(2) ?: 0)
}

/**
 * The timing of [memo]. The call runs from its start (or its first sentence, if earlier) to one
 * average gap between sentences after the last one, since the last sentence still takes a while
 * to say. A call of a single sentence is taken to last [SINGLE_SENTENCE_SECONDS].
 */
internal fun timing(memo: CallMemo): CallTiming {
    val notes = memo.notes
    val count = notes.size
    val times = notes.map { clockSeconds(it.time) }
    if (count == 0 || times.any { it == null }) {
        val even = List(count) { (it.toFloat() / count)..((it + 1f) / count) }
        return CallTiming(totalSeconds = null, offsets = null, spans = even)
    }
    val seconds = times.map { it!! }
    val start = minOf(clockSeconds(memo.startedAt) ?: seconds.min(), seconds.min())
    val last = seconds.max()
    val averageGap = if (count > 1) ((last - seconds.first()) / (count - 1)).coerceAtLeast(MIN_GAP_SECONDS) else SINGLE_SENTENCE_SECONDS
    // Times kept to the minute ("14:05") say nothing about seconds, so the length is not made up
    // of them either: the gap is rounded to whole minutes.
    val toTheMinute = notes.none { it.time.count { c -> c == ':' } == 2 }
    val gap = if (toTheMinute) ((averageGap + 30) / 60 * 60).coerceAtLeast(60) else averageGap
    val total = (last + gap - start).coerceAtLeast(1)
    val offsets = seconds.map { it - start }
    val spans = offsets.mapIndexed { index, offset ->
        // A sentence lasts until the next one starts, but not longer than one sentence takes to
        // say: a pause after it is not part of what was missed.
        val end = minOf(offsets.getOrNull(index + 1) ?: total, offset + MAX_SENTENCE_SECONDS, total)
        val from = offset.toFloat() / total
        from..maxOf(end.toFloat() / total, from + MIN_SPAN)
    }
    return CallTiming(totalSeconds = total, offsets = offsets, spans = spans)
}

/**
 * The stretches of the call wave the driver probably missed: one per run of missed sentences
 * with the same cause, placed where those sentences were said. Sentences that only run the
 * meeting are left out, as the memo leaves them out of the missed part.
 */
internal fun missedStretches(memo: CallMemo, timing: CallTiming): List<Stretch> {
    val stretches = mutableListOf<Stretch>()
    var lastMissed = -2
    memo.notes.forEachIndexed { index, note ->
        if (note.missed == null || note.runsTheMeeting) return@forEachIndexed
        val span = timing.spans.getOrNull(index) ?: return@forEachIndexed
        val cause = causeOf(note)
        val previous = stretches.lastOrNull()
        // Sentences missed one after another for the same cause are one stretch, however long the
        // pauses between them.
        val follows = lastMissed == index - 1 || (previous != null && previous.to >= span.start - JOIN_TOLERANCE)
        lastMissed = index
        if (previous != null && previous.cause == cause && follows) {
            stretches[stretches.lastIndex] = previous.copy(to = maxOf(previous.to, span.endInclusive))
        } else {
            stretches += Stretch(span.start.coerceIn(0f, 1f), span.endInclusive.coerceIn(0f, 1f), cause)
        }
    }
    return stretches
}

/** The bar heights of [memo]'s call wave: always the same for the same memo. */
internal fun waveSeed(memo: CallMemo): Int = memo.id * 7 + 3

/** Bars on the parked screen's call wave (as in `chabun.js`). */
internal const val WAVE_BARS = 170

/** Least time assumed between two sentences when their times say less. */
private const val MIN_GAP_SECONDS = 10

/** The longest one sentence is taken to last on the call wave. */
private const val MAX_SENTENCE_SECONDS = 30

/** How long a call of one sentence is taken to be. */
private const val SINGLE_SENTENCE_SECONDS = 60

/** The narrowest a sentence's span is drawn, so a missed one stays visible. */
private const val MIN_SPAN = 0.015f

/** Runs closer than this are drawn as one stretch. */
private const val JOIN_TOLERANCE = 0.001f
