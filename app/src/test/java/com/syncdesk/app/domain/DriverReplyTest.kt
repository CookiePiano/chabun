package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverReplyTest {

    @Test
    fun theWordsForYes() {
        listOf("네", "응", "어", "그래", "좋아", "해 줘", "네, 해 줘", "응 그래", "좋아요", "해 주세요", "Yes", "okay").forEach {
            assertEquals(it, DriverReply.Yes, DriverReply.of(it))
        }
    }

    @Test
    fun theWordsForNo() {
        listOf("아니", "괜찮아", "됐어", "나중에", "아니, 괜찮아", "아니요", "됐어요", "No thanks", "later").forEach {
            assertEquals(it, DriverReply.No, DriverReply.of(it))
        }
    }

    @Test
    fun theWordsForStop() {
        listOf("그만", "닫아", "멈춰", "이제 그만해", "닫아 줘", "그만!", "Stop").forEach {
            assertEquals(it, DriverReply.Stop, DriverReply.of(it))
        }
    }

    @Test
    fun stoppingWinsOverDecliningAndDecliningOverAgreeing() {
        assertEquals(DriverReply.Stop, DriverReply.of("아니 그만"))
        assertEquals(DriverReply.Stop, DriverReply.of("됐어, 그만해"))
    }

    @Test
    fun anythingElseIsKeptAsItWasHeard() {
        assertEquals(DriverReply.Other("납기 언제라고 했지?"), DriverReply.of(" 납기 언제라고 했지? "))
        // A question that opens with a yes, or merely holds a word for no or stop, is still a question.
        assertEquals(DriverReply.Other("네, 납기 언제라고 했지?"), DriverReply.of("네, 납기 언제라고 했지?"))
        assertEquals(DriverReply.Other("어디로 보내라고 했어"), DriverReply.of("어디로 보내라고 했어"))
        assertEquals(DriverReply.Other("그만큼 든다고 했나"), DriverReply.of("그만큼 든다고 했나"))
        assertEquals(DriverReply.Other("아니 견적이 얼마였지"), DriverReply.of("아니 견적이 얼마였지"))
    }

    @Test
    fun silenceIsNoReply() {
        assertNull(DriverReply.of(""))
        assertNull(DriverReply.of("  ... "))
    }
}
