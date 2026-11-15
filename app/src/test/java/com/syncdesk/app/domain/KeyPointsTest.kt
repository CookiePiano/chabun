package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyPointsTest {

    /** The demo meeting as the on-device recognizer wrote it on the emulator (no final period). */
    @Test
    fun demoMeetingAsRecognized() {
        val expected = listOf(
            "울산 2공장 납품 일정부터 확인하겠습니다" to null,
            "부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다" to "납기를 11월 28일로 옮기기",
            "고객사에는 오늘 중으로 변경 공문을 보내주세요" to "고객사에 오늘까지 변경 공문을 보내기",
            // Both sentences score the same; the first keeps the subject.
            "검수 인력은 두 명이 더 필요합니다 다음 주 월요일부터 가능합니다" to "검수 인력은 두 명이 더 필요",
            "견적은 기존 금액에서 3퍼센트 올려 다시 내겠습니다" to "견적은 3퍼센트 올려 다시 내기",
            "인상 근거 자료는 금요일까지 공유 폴더에 올려주세요" to "인상 근거 자료는 금요일까지 올리기",
            "현장 안전 점검은 목요일 오전 10시로 잡았습니다" to "안전 점검은 목요일 오전 10시로 잡았음",
            "다음 회의는 같은 시간에 이어서 하겠습니다" to null,
        )
        expected.forEach { (heard, point) -> assertEquals(heard, point, KeyPoints.of(heard)) }
    }

    @Test
    fun scriptedTextWithPunctuationGivesTheSameResult() {
        assertEquals(
            "납기를 11월 28일로 옮기기",
            KeyPoints.of("부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다."),
        )
    }

    @Test
    fun smallTalkAndRunningTheMeetingAreLeftOut() {
        assertNull(KeyPoints.of("네, 잘 들립니다"))
        assertNull(KeyPoints.of("오늘 회의 수고하셨습니다"))
        assertNull(KeyPoints.of("자료는 다들 받으셨죠"))
    }

    @Test
    fun linesThatOnlyRunTheMeetingAreToldApartFromOtherLinesWithoutAKeyPoint() {
        listOf(
            "울산 2공장 납품 일정부터 확인하겠습니다",
            "울산 2공장 납품 일정부터 확인하겠습니다.",
            "다음 회의는 같은 시간에 이어서 하겠습니다",
            "자, 화성 라인 증설 건으로 시작하겠습니다",
            "좋습니다. 오늘 회의는 여기까지 하겠습니다.",
            "네, 수고하셨습니다",
        ).forEach { assertEquals(it, true, KeyPoints.runsTheMeeting(it)) }
        listOf(
            // No key point, but not running the meeting: a question, small talk.
            "자료는 다들 받으셨죠",
            "날씨가 좋네요",
            // Running the meeting and a key point in one breath: the key point counts.
            "납기부터 보겠습니다 납기를 11월 28일로 옮겨야 합니다",
        ).forEach { assertEquals(it, false, KeyPoints.runsTheMeeting(it)) }
    }

    @Test
    fun aReasonClauseStaysWhenTheMainClauseAloneSaysTooLittle() {
        // Without the reason, "서둘러야 합니다" would carry no date or quantity.
        assertEquals(
            "고객사가 다음 주 월요일까지 요청해서 서두르기",
            KeyPoints.of("고객사가 다음 주 월요일까지 요청해서 서둘러야 합니다"),
        )
    }

    @Test
    fun leadingFillerIsDropped() {
        assertEquals("견적은 금요일까지 다시 내기", KeyPoints.of("네, 견적은 금요일까지 다시 내겠습니다"))
    }

    @Test
    fun theStrongestSentenceOfSeveralIsChosen() {
        assertEquals(
            "샘플 3개를 목요일까지 보내기",
            KeyPoints.of("자료는 확인했습니다 샘플 3개를 목요일까지 보내주세요"),
        )
    }

    /** The recognizer sometimes writes numbers as digits and symbols; the rules read both. */
    @Test
    fun digitsAndSymbolsFromTheRecognizer() {
        assertEquals("견적은 3% 올려 다시 내기", KeyPoints.of("견적은 기존 금액에서 3% 올려 다시 내겠습니다"))
        assertEquals("검수 인력은 2명이 더 필요", KeyPoints.of("검수 인력은 2명이 더 필요합니다 다음 주 월요일부터 가능합니다"))
        // The recognizer sometimes puts a comma between sentences.
        assertEquals("검수 인력은 2명이 더 필요", KeyPoints.of("검수 인력은 2명이 더 필요합니다, 다음 주 월요일부터 가능합니다"))
    }

    @Test
    fun aLongLineKeepsEveryDateTimeAndQuantity() {
        // The place holds the date, so it stays even though the line is long.
        assertEquals(
            "자료는 다음 주 월요일 회의에 맞춰 보내기",
            KeyPoints.of("자료는 다음 주 월요일 회의에 맞춰 보내주세요"),
        )
    }

    // region The ending of a written note

    @Test
    fun whatIsToBeDoneEndsAsADeed() {
        // Asked, needed, announced, proposed.
        assertEquals("견적서 초안을 보내기", KeyPoints.asNote("견적서 초안을 보내 주세요"))
        assertEquals("장비 두 대를 주문하기", KeyPoints.asNote("장비 두 대를 주문해 주세요"))
        assertEquals("단가를 1200원으로 맞추기", KeyPoints.asNote("단가를 1200원으로 맞춰 주세요"))
        assertEquals("시제품을 금요일까지 만들기", KeyPoints.asNote("시제품을 금요일까지 만들어야 합니다"))
        assertEquals("예산을 10% 줄이기", KeyPoints.asNote("예산을 10% 줄여야 합니다"))
        assertEquals("다음 주 월요일까지 서두르기", KeyPoints.asNote("다음 주 월요일까지 서둘러야 합니다"))
        assertEquals("미팅은 목요일 오후 3시에 잡기", KeyPoints.asNote("미팅은 목요일 오후 3시에 잡겠습니다"))
        assertEquals("목요일에 다시 진행하기", KeyPoints.asNote("목요일에 다시 진행합시다"))
        // Asking for a thing rather than a deed.
        assertEquals("내일까지 연락 주기", KeyPoints.asNote("내일까지 연락 주세요"))
    }

    @Test
    fun whatIsTheCaseEndsAsAFact() {
        assertEquals("인력은 2명이 더 필요", KeyPoints.asNote("인력은 2명이 더 필요합니다"))
        assertEquals("배송은 하루 늦어질 예정", KeyPoints.asNote("배송은 하루 늦어질 예정입니다"))
        assertEquals("심사는 목요일 오전 10시", KeyPoints.asNote("심사는 목요일 오전 10시입니다"))
        assertEquals("출시일은 1월 15일로 확정됐음", KeyPoints.asNote("출시일은 1월 15일로 확정됐습니다"))
        assertEquals("재고가 50개밖에 안 남았음", KeyPoints.asNote("재고가 50개밖에 안 남았습니다"))
        assertEquals("금요일에 최종 보고가 있음", KeyPoints.asNote("금요일에 최종 보고가 있습니다"))
        assertEquals("발표회는 20일에 열림", KeyPoints.asNote("발표회는 20일에 열립니다"))
        // A guess is not a deed.
        assertEquals("금요일까지는 어렵겠음", KeyPoints.asNote("금요일까지는 어렵겠습니다"))
    }

    @Test
    fun otherEndingsAndEnglishAreLeftAsTheyAre() {
        assertEquals("Please send the notice today", KeyPoints.asNote("Please send the notice today"))
        assertEquals("납기는 28일", KeyPoints.asNote("납기는 28일"))
    }

    @Test
    fun aTimeOrAReasonIsNotCutFromALongLine() {
        // "이번 주 안에" stays; a line still too long loses the first word of its subject instead.
        assertEquals("불만 건은 이번 주 안에 처리하기", KeyPoints.of("고객 불만 건은 이번 주 안에 처리해야 합니다"))
        assertEquals(
            "서버 점검 때문에 내일 오전 9시부터 2시간 동안 접속이 안 됨",
            KeyPoints.of("서버 점검 때문에 내일 오전 9시부터 2시간 동안 접속이 안 됩니다"),
        )
    }

    // endregion

    // region Commitments

    @Test
    fun driversPromisesInTheDemoCallBecomeToDos() {
        assertNull(KeyPoints.commitment("네 일정표 보면서 듣고 있습니다"))
        assertEquals("공문을 오후에 보내기", KeyPoints.commitment("공문은 제가 오후에 바로 보내겠습니다"))
        assertEquals("인상 근거를 정리해서 공유하기", KeyPoints.commitment("인상 근거는 제가 정리해서 공유하겠습니다"))
    }

    @Test
    fun otherWaysOfPromising() {
        assertEquals("견적서를 내일까지 보내 드리기", KeyPoints.commitment("견적서는 제가 내일까지 보내 드릴게요"))
        assertEquals("회의실을 예약하기", KeyPoints.commitment("그럼 회의실은 제가 예약할게요"))
    }

    @Test
    fun aPlanWithoutTheSpeakerTakingItOnIsNotAPromise() {
        // Nobody said they would do it themselves.
        assertNull(KeyPoints.commitment("견적은 기존 금액에서 3퍼센트 올려 다시 내겠습니다"))
        // Running the meeting.
        assertNull(KeyPoints.commitment("제가 다음 회의는 같은 시간에 이어서 하겠습니다"))
    }

    @Test
    fun englishPromises() {
        assertEquals("Send the notice this afternoon", KeyPoints.commitment("I will send the notice this afternoon."))
        assertNull(KeyPoints.commitment("Sure, I have the schedule in front of me."))
    }

    // endregion

    // region A second meeting and sentences from neither meeting

    /** The second demo meeting, on another subject and in other words, as written in the script. */
    @Test
    fun secondMeetingKeyPoints() {
        val expected = listOf(
            "자, 화성 라인 증설 건으로 시작하겠습니다." to null,
            "설비 반입이 다음 달 3일에서 10일로 일주일 밀렸습니다." to "설비 반입이 다음 달 10일로 일주일 밀렸음",
            "냉각 장치 사양은 2안으로 정했습니다." to "냉각 장치 사양은 2안으로 정했음",
            "견적은 이번 주 금요일까지 두 군데에서 더 받아 주세요." to "견적은 금요일까지 두 군데에서 더 받기",
            "시운전에는 인력이 다섯 명 정도 필요합니다." to "시운전에 인력이 다섯 명 정도 필요",
            "안전 교육은 18일 오후 2시에 본관 대회의실에서 진행합니다." to "안전 교육은 18일 오후 2시에 진행",
            "좋습니다. 오늘 회의는 여기까지 하겠습니다." to null,
            "네, 수고하셨습니다." to null,
        )
        expected.forEach { (said, point) -> assertEquals(said, point, KeyPoints.of(said)) }
    }

    /** What the recognizer actually wrote for the second meeting on the emulator. */
    @Test
    fun secondMeetingAsRecognized() {
        assertEquals("반입 일정을 협력사에 다시 확인해 보기", KeyPoints.commitment("내 반입 일정은 제가 협력사에 다시 확인해 볼게요"))
        assertEquals("냉각장치 사양은 이 안으로 정했음", KeyPoints.of("냉각장치 사양은 이 안으로 정했습니다"))
        assertEquals("시운전에 인력이 5명 정도 필요", KeyPoints.of("시운전에는 인력이 5명 정도 필요합니다"))
        assertNull(KeyPoints.of("좋습니다, 오늘 회의는 여기까지 하겠습니다"))
        // Without "제가", a leading "내" may really mean "my" and stays.
        assertEquals("내 차를 내일 정비소에 맡기기", KeyPoints.commitment("내 차는 내일 정비소에 맡길게요"))
    }

    @Test
    fun secondMeetingPromises() {
        assertEquals("반입 일정을 협력사에 다시 확인해 보기", KeyPoints.commitment("네, 반입 일정은 제가 협력사에 다시 확인해 볼게요."))
        assertEquals("견적 비교표를 목요일까지 만들어서 공유드리기", KeyPoints.commitment("견적 비교표는 제가 목요일까지 만들어서 공유드리겠습니다."))
        assertEquals("교육 참석자 명단을 챙기기", KeyPoints.commitment("교육 참석자 명단은 제가 챙길게요."))
    }

    /** Sentences in neither demo meeting, so the rules are not fitted to the demo alone. */
    @Test
    fun keyPointsInSentencesFromNeitherMeeting() {
        assertEquals("도면 수정본은 모레 오전까지 메일로 부탁", KeyPoints.of("도면 수정본은 모레 오전까지 메일로 부탁드립니다"))
        assertEquals("예산은 1억 2천만 원으로 확정했음", KeyPoints.of("예산은 1억 2천만 원으로 확정했습니다"))
        assertEquals("출장은 다음 달 초로 미뤄졌음", KeyPoints.of("그리고 출장은 다음 달 초로 미뤄졌습니다"))
        assertNull(KeyPoints.of("점심 메뉴는 다들 괜찮으세요"))
        assertNull(KeyPoints.of("그럼 오늘 회의를 마치겠습니다"))
    }

    @Test
    fun promisesInSentencesFromNeitherMeeting() {
        // "~ㄹ게요" is a promise even with the speaker left out, as Korean usually does.
        assertEquals("샘플을 다음 주 화요일에 보내 드리기", KeyPoints.commitment("샘플은 다음 주 화요일에 보내 드릴게요"))
        assertEquals("회의록을 정리해서 올리기", KeyPoints.commitment("회의록은 제가 정리해서 올릴게요"))
        assertEquals("일정표를 다시 보내 드리기", KeyPoints.commitment("일정표는 다시 보내 드리겠습니다"))
        // Asking, agreeing or announcing someone else's plan is not a promise.
        assertNull(KeyPoints.commitment("견적은 금요일까지 보내 주세요"))
        assertNull(KeyPoints.commitment("네 그렇게 하시죠"))
        assertNull(KeyPoints.commitment("공장 쪽에서 내일 연락할 겁니다"))
    }

    // endregion
}