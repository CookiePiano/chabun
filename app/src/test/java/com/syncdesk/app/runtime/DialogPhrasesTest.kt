package com.syncdesk.app.runtime

import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogPhrasesTest {

    private val phrases = DialogPhrases(
        summaryIntro = "key points",
        noKeyPoints = "no key points",
        missedIntro = "missed",
        missedByNoiseIntro = "missed for noise",
        missedMixedIntro = "missed for road or noise",
    )

    private fun said(id: Int, gist: String?, missed: Boolean = false, reason: MissedReason = MissedReason.Driving) =
        MeetingNote(id, "14:00", "Kim", "sentence $id", gist, if (missed) reason else null)

    @Test
    fun whatWasMissedIsIntroducedByWhyItWasMissed() {
        val noise = CallMemo(id = 1, notes = listOf(said(1, null, missed = true, reason = MissedReason.Noise)))
        val both = CallMemo(
            id = 2,
            notes = listOf(said(1, null, missed = true, reason = MissedReason.Noise), said(2, null, missed = true)),
        )

        assertEquals(listOf("no key points", "missed for noise", "sentence 1"), phrases.explain(noise))
        assertEquals(listOf("no key points", "missed for road or noise", "sentence 1", "sentence 2"), phrases.explain(both))
    }

    @Test
    fun theSummaryIsExplainedFirstThenWhatWasMissed() {
        val memo = CallMemo(
            id = 1,
            notes = listOf(said(1, "a"), said(2, "b"), said(3, "c"), said(4, "d", missed = true), said(5, null, missed = true)),
        )

        assertEquals(listOf("key points", "a", "b", "c", "missed", "d", "sentence 5"), phrases.explain(memo))
    }

    @Test
    fun withNothingMissedTheMissedPartIsLeftOut() {
        val memo = CallMemo(id = 1, notes = listOf(said(1, "a"), said(2, null)))

        assertEquals(listOf("key points", "a"), phrases.explain(memo))
    }

    @Test
    fun whatTheSummaryAlreadySaidIsNotRepeatedAsMissed() {
        val memo = CallMemo(id = 1, notes = listOf(said(1, "a", missed = true), said(2, "b")))

        assertEquals(listOf("key points", "a", "b"), phrases.explain(memo))
    }

    @Test
    fun aCallWithoutKeyPointsSaysSoAndStillTellsWhatWasMissed() {
        val nothing = CallMemo(id = 1, notes = listOf(said(1, null)))
        val missed = CallMemo(id = 2, notes = listOf(said(1, null, missed = true)))

        assertEquals(listOf("no key points"), phrases.explain(nothing))
        assertEquals(listOf("no key points", "missed", "sentence 1"), phrases.explain(missed))
    }

    @Test
    fun theOfferAsksWhetherTheDriverWantsToHearTheSummary() {
        assertEquals("Would you like to hear a summary of the call?", DialogPhrases().offer)
    }

    @Test
    fun aMissedLineThatOnlyRunsTheMeetingIsNotReadOut() {
        val opening = MeetingNote(1, "14:00", "Kim", "울산 2공장 납품 일정부터 확인하겠습니다", null, MissedReason.Noise)
        val onlyThat = CallMemo(id = 1, notes = listOf(opening, said(2, "a")))
        val withMore = CallMemo(id = 2, notes = listOf(opening, said(2, "a"), said(3, null, missed = true)))

        assertEquals(listOf("key points", "a"), phrases.explain(onlyThat))
        // The reason given is that of what is read out: the road, not the noise of the opening line.
        assertEquals(listOf("key points", "a", "missed", "sentence 3"), phrases.explain(withMore))
    }
}
