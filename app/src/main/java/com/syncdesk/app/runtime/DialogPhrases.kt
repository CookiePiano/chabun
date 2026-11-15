package com.syncdesk.app.runtime

import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MissedReason

/**
 * What the car says in the talk after a call, apart from the memo itself. The app takes these
 * from its string resources, so they follow the system language.
 */
data class DialogPhrases(
    /** Opens the talk: "통화 요약을 듣고 싶으신가요?" */
    val offer: String = "Would you like to hear a summary of the call?",
    /** Comes before the key summary. */
    val summaryIntro: String = "Here are the key points.",
    /** Stands in for the key summary of a call that had none. */
    val noKeyPoints: String = "The call had no key points to sum up.",
    /** Comes before the part the driver probably missed because the road needed them. */
    val missedIntro: String = "This is what you may have missed while driving.",
    /** Comes before the part the driver probably missed because the cabin was too loud. */
    val missedByNoiseIntro: String = "This is what you may have missed because of cabin noise.",
    /** Comes before the missed part when some of it was missed for the road and some for the noise. */
    val missedMixedIntro: String = "This is what you may have missed while driving or because of cabin noise.",
    /** Invites questions: "관련해서 궁금하신 사항이 있으신가요?" */
    val ask: String = "Is there anything you would like to know about it?",
    /** The answer when the memo does not hold one: "통화에서 그 내용은 나오지 않았습니다". */
    val notMentioned: String = "That did not come up on the call.",
) {
    /**
     * The explanation of [memo]: its key summary, then the part the driver missed, if any,
     * introduced by why it was missed.
     */
    fun explain(memo: CallMemo): List<String> = buildList {
        val summary = memo.summary
        if (summary.isEmpty()) {
            add(noKeyPoints)
        } else {
            add(summaryIntro)
            addAll(summary)
        }
        val missed = memo.missedSaid
        if (missed.isNotEmpty()) {
            val reasons = missed.mapTo(mutableSetOf()) { it.missed }
            add(
                when (reasons) {
                    setOf(MissedReason.Driving) -> missedIntro
                    setOf(MissedReason.Noise) -> missedByNoiseIntro
                    else -> missedMixedIntro
                },
            )
            addAll(memo.missedToSay)
        }
    }
}
