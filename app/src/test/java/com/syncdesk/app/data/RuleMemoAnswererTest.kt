package com.syncdesk.app.data

import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.KeyPoints
import com.syncdesk.app.domain.MeetingNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMemoAnswererTest {

    private val answerer = RuleMemoAnswerer()

    /** The demo call about deliveries, as the app writes it down. */
    private val memo = CallMemo(
        id = 1,
        notes = listOf(
            "울산 2공장 납품 일정부터 확인하겠습니다.",
            "부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다.",
            "고객사에는 오늘 중으로 변경 공문을 보내 주세요.",
            "검수 인력은 두 명이 더 필요합니다. 다음 주 월요일부터 가능합니다.",
            "견적은 기존 금액에서 3퍼센트 올려 다시 내겠습니다.",
            "현장 안전 점검은 목요일 오전 10시로 잡았습니다.",
            "다음 회의는 같은 시간에 이어서 하겠습니다.",
        ).mapIndexed { index, text -> MeetingNote(index + 1, "14:0$index", "김 팀장", text, KeyPoints.of(text)) } +
            MeetingNote(8, "14:08", "나", "인상 근거는 제가 정리해서 공유하겠습니다.", gist = "인상 근거를 정리해서 공유하기", byDriver = true),
    )

    @Test
    fun theSentenceThatSharesTheQuestionsWordsAnswersByItsKeyPoint() = runTest {
        assertEquals(KeyPoints.of(memo.notes[1].text), answerer.answer(memo, "납기 언제라고 했지?"))
        assertEquals(KeyPoints.of(memo.notes[5].text), answerer.answer(memo, "안전 점검은 언제야?"))
        assertEquals(KeyPoints.of(memo.notes[3].text), answerer.answer(memo, "검수 인력 몇 명 필요하다고 했어?"))
    }

    @Test
    fun theDriversOwnPromiseCanBeTheAnswer() = runTest {
        assertEquals("인상 근거를 정리해서 공유하기", answerer.answer(memo, "인상 근거는 누가 정리하기로 했지?"))
    }

    @Test
    fun aSentenceWithoutAKeyPointAnswersInItsOwnWords() = runTest {
        assertNull(memo.notes[6].gist)
        assertEquals("다음 회의는 같은 시간에 이어서 하겠습니다.", answerer.answer(memo, "다음 회의 언제야?"))
    }

    @Test
    fun whatTheCallDidNotCoverHasNoAnswer() = runTest {
        assertNull(answerer.answer(memo, "출장비는 얼마라고 했지?"))
        assertNull(answerer.answer(memo, "회식 장소가 어디야?"))
    }

    @Test
    fun wordsThatOnlyAskMatchNothing() = runTest {
        assertNull(answerer.answer(memo, "언제라고 했지?"))
        assertNull(answerer.answer(memo, "뭐라고 했어?"))
        assertNull(answerer.answer(memo, ""))
    }

    @Test
    fun englishQuestionsAreAnsweredTheSameWay() = runTest {
        val english = CallMemo(
            id = 2,
            notes = listOf(
                MeetingNote(1, "14:00", "Kim", "Let's start with the delivery schedule for Ulsan plant 2."),
                MeetingNote(2, "14:01", "Park", "Parts arrived three days late, so delivery has to move to November 28.", "Delivery moves to November 28"),
            ),
        )

        assertEquals("Delivery moves to November 28", answerer.answer(english, "When is the delivery?"))
        assertNull(answerer.answer(english, "Who pays for lunch?"))
    }
}
