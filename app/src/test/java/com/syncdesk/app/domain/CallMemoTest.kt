package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CallMemoTest {

    private var nextId = 1

    private fun said(gist: String?, missed: Boolean = false) = MeetingNote(
        id = nextId++,
        time = "14:00",
        speaker = "Kim",
        text = "sentence about ${gist ?: "nothing much"}",
        gist = gist,
        missed = if (missed) MissedReason.Driving else null,
    )

    private fun promised(task: String) =
        MeetingNote(id = nextId++, time = "14:00", speaker = "me", text = "I will: $task", gist = task, byDriver = true)

    private fun memo(vararg notes: MeetingNote) = CallMemo(id = 1, notes = notes.toList())

    @Test
    fun theSummaryIsTheKeyPointsAndThePromisesInTheOrderTheyWereSaid() {
        val memo = memo(said(null), said("delivery moves to the 28th"), promised("send the notice"), said("two more inspectors"))

        assertEquals(listOf("delivery moves to the 28th", "send the notice", "two more inspectors"), memo.summary)
    }

    @Test
    fun withMoreThanThreeThePromisesStayAndTheOthersFillWhatIsLeftStillInOrder() {
        val memo = memo(
            said("delivery moves to the 28th"),
            said("send the customer the notice today"),
            promised("send the notice"),
            said("two more inspectors"),
            promised("put the reasons together"),
            said("safety check on Thursday"),
        )

        assertEquals(listOf("delivery moves to the 28th", "send the notice", "put the reasons together"), memo.summary)
    }

    @Test
    fun withMoreThanThreePromisesTheFirstThreeAreTheSummary() {
        val memo = memo(said("a"), promised("one"), promised("two"), promised("three"), promised("four"))

        assertEquals(listOf("one", "two", "three"), memo.summary)
    }

    @Test
    fun aPointSaidTwiceIsSummarizedOnce() {
        val memo = memo(promised("send the notice"), said("two more inspectors"), promised("send the notice"))

        assertEquals(listOf("send the notice", "two more inspectors"), memo.summary)
    }

    @Test
    fun aCallWithoutKeyPointsHasNoSummary() {
        assertEquals(emptyList<String>(), memo(said(null), said(null)).summary)
    }

    @Test
    fun theMissedPartIsEverySentenceMarkedAsMissed() {
        val first = said("a", missed = true)
        val second = said(null, missed = true)
        val memo = memo(said("heard"), first, promised("one"), second)

        assertEquals(listOf(first, second), memo.missed)
    }

    @Test
    fun aloudTheMissedPartLeavesOutWhatTheSummaryJustSaidAndStaysShort() {
        val memo = memo(
            said("a", missed = true),
            said("b"),
            said("c"),
            said("d", missed = true),
            said(null, missed = true),
            said("e", missed = true),
            said("f", missed = true),
        )

        assertEquals(listOf("a", "b", "c"), memo.summary)
        // "a" was in the summary; a sentence without a key point is said as it is.
        assertEquals(listOf("d", "sentence about nothing much", "e"), memo.missedToSay)
        assertEquals(5, memo.missed.size)
    }

    @Test
    fun aMissedSentenceThatOnlyRunsTheMeetingKeepsItsMarkButIsNeitherShownNorSaidAsMissed() {
        val opening = MeetingNote(nextId++, "14:00", "Kim", "울산 2공장 납품 일정부터 확인하겠습니다", gist = null, missed = MissedReason.Noise)
        val closing = MeetingNote(nextId++, "14:09", "Kim", "다음 회의는 같은 시간에 이어서 하겠습니다", gist = null, missed = MissedReason.Driving)
        val point = said("delivery moves to the 28th", missed = true)
        val question = said(null, missed = true)
        val memo = memo(opening, point, question, closing)

        // In the whole call, with its mark.
        assertEquals(4, memo.notes.size)
        assertEquals(listOf(opening, point, question, closing), memo.missed)
        // Not in the missed part on screen, and not read out; a missed sentence without a key
        // point that does not run the meeting still is.
        assertEquals(listOf(point, question), memo.missedPart)
        assertEquals(listOf("sentence about nothing much"), memo.missedToSay)
    }

    @Test
    fun aCallWhoseOnlyMissedSentenceRunsTheMeetingHasNoMissedPart() {
        val opening = MeetingNote(1, "14:00", "Kim", "자, 화성 라인 증설 건으로 시작하겠습니다", gist = null, missed = MissedReason.Noise)
        val memo = memo(opening, said("delivery moves to the 28th"))

        assertEquals(emptyList<MeetingNote>(), memo.missedPart)
        assertEquals(emptyList<String>(), memo.missedToSay)
    }
}
