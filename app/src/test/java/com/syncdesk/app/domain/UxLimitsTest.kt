package com.syncdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UxLimitsTest {

    @Test
    fun theEmulatorInDriveForbidsSetupOnly() {
        // 0x50 is what the Pleos Connect v2.1.0 emulator reports in Drive: no setup, no video.
        assertEquals(UxLimits(noSetup = true), UxLimits.of(true, 0x50, maxStringLength = 120, maxItems = 21))
    }

    @Test
    fun nothingIsLimitedWhenTheCarDoesNotAskForIt() {
        assertEquals(UxLimits(), UxLimits.of(false, 0x1ff, maxStringLength = 120, maxItems = 21))
        assertEquals(UxLimits(), UxLimits.of(true, 0x0, maxStringLength = 120, maxItems = 21))
    }

    @Test
    fun lengthAndContentLimitsArePassedOnWhenActive() {
        assertEquals(
            UxLimits(noSetup = true, maxStringLength = 120, maxItems = 21),
            UxLimits.of(true, 0x40 or 0x20 or 0x4, maxStringLength = 120, maxItems = 21),
        )
    }

    @Test
    fun textIsCutWithAnEllipsisOnlyWhenTooLong() {
        val limits = UxLimits(maxStringLength = 12)
        assertEquals("공문을 오후에 보내기", limits.fit("공문을 오후에 보내기"))
        assertEquals("인상 근거를 정리해서…", limits.fit("인상 근거를 정리해서 공유하기"))
        assertEquals(12, limits.fit("인상 근거를 정리해서 공유하기").length)
        assertEquals("아무 제한 없음", UxLimits().fit("아무 제한 없음"))
    }
}