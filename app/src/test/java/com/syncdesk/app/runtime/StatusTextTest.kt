package com.syncdesk.app.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.syncdesk.app.R
import com.syncdesk.app.domain.CabinPart
import com.syncdesk.app.domain.CabinState
import com.syncdesk.app.domain.Contact
import com.syncdesk.app.domain.ContactDigest
import com.syncdesk.app.domain.ContactKind
import com.syncdesk.app.domain.DriverReply
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.HearingLevels
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.OfficePhase
import com.syncdesk.app.domain.OfficeState
import com.syncdesk.app.domain.SyncDeskEngine
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.domain.UsbPort
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.domain.WiperSpeed
import com.syncdesk.app.domain.afterEvents
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR")
class StatusTextTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun status(state: SyncDeskState) = statusText(resources, state)

    private val onCall = SyncDeskEvent.CallChanged(active = true, driverSpeaking = false)
    private val heavyRain = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive, wiper = WiperSpeed.High))

    private fun note(id: Int) = SyncDeskEvent.NoteCaptured(MeetingNote(id, "14:00", "김 팀장", "문장 $id"))

    @Test
    fun `with nothing going on the headline says it all`() {
        assertEquals(StatusText("통화를 기다리는 중", ""), status(SyncDeskState()))
    }

    @Test
    fun `a demanding stretch explains that Gleo will cover the missed parts after the call`() {
        val waiting = SyncDeskState().afterEvents(onCall, heavyRain)
        val expected = StatusText("못 들은 부분을 표시하는 중", "통화가 끝나면 Gleo가 짚어 드립니다")
        assertEquals(expected, status(waiting))

        val marked = waiting.afterEvents(note(1), note(2))
        assertEquals(expected, status(marked))
    }

    @Test
    fun `after a call the card keeps the summary invitation throughout the conversation`() {
        val hungUp = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)
        val clearRoad = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Drive))
        val waiting = SyncDeskState().afterEvents(onCall, heavyRain, note(1), hungUp)
        val expected = StatusText("Gleo가 요약을 준비했습니다", "통화 요약을 듣고 싶으신가요?")
        assertEquals(expected, status(waiting))

        val offering = waiting.afterEvents(clearRoad)
        assertEquals(expected, status(offering))
        val offered = offering.afterEvents(SyncDeskEvent.DialogSpoken)
        assertEquals(expected, status(offered))

        val explaining = offered.afterEvents(SyncDeskEvent.DriverReplied(DriverReply.Yes))
        assertEquals(expected, status(explaining))

        val asked = explaining.afterEvents(SyncDeskEvent.DialogSpoken, SyncDeskEvent.DialogSpoken)
        assertEquals(expected, status(asked))

        val answering = asked.afterEvents(SyncDeskEvent.DriverReplied(DriverReply.Other("납기 언제라고 했지?")))
        assertEquals(expected, status(answering))
    }

    @Test
    fun `tuning shows the reported cabin values rather than requested changes`() {
        fun heard(margin: Int) = SyncDeskEvent.HearingChanged(HearingLevels(voice = 60, noise = 60 - margin))
        // Keep the window command pending instead of using the instantly responding test vehicle.
        val tuned = SyncDeskEngine.reduce(SyncDeskState(), onCall)
        assertEquals(0, requireNotNull(tuned.command).change.windowOpenPercent)
        assertEquals(StatusText("통화가 잘 들리도록 조절 중", "송풍 6단 · 창문 30% 열림"), status(tuned))
        assertEquals(status(tuned), status(SyncDeskEngine.reduce(tuned, heard(margin = 12))))
        assertEquals(status(tuned), status(SyncDeskEngine.reduce(tuned, heard(margin = 6))))
        assertEquals(status(tuned), status(SyncDeskEngine.reduce(tuned, heard(margin = 0))))

        val reported = SyncDeskEngine.reduce(tuned, SyncDeskEvent.CabinReported(CabinState(fanLevel = 2, windowOpenPercent = 0)))
        assertEquals(StatusText("통화가 잘 들리도록 조절 중", "송풍 2단 · 창문 닫힘"), status(reported))
        assertEquals("송풍 꺼짐 · 창문 닫힘", status(reported.copy(cabin = CabinState(fanLevel = 0, windowOpenPercent = 0))).detail)
        assertEquals("송풍 꺼짐 · 창문 45% 열림", status(reported.copy(cabin = CabinState(fanLevel = 0, windowOpenPercent = 45))).detail)
    }

    @Test
    fun `parked it counts this drive's call memos and unread contacts on separate lines`() {
        val park = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Park))
        val hungUp = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)
        val twoCalls = SyncDeskState().afterEvents(onCall, note(1), hungUp, onCall, note(1), hungUp)
        assertEquals(StatusText("통화 메모 2건", ""), status(twoCalls.afterEvents(park)))

        val reached = twoCalls.afterEvents(
            SyncDeskEvent.ContactsReported(
                listOf(Contact("1", ContactKind.MissedCall, "김 팀장"), Contact("2", ContactKind.Message, "어머니")),
            ),
            park,
        )
        assertEquals(StatusText("통화 메모 2건", "받지 않은 전화 1건 · 메시지 1건"), status(reached))
        assertEquals(StatusText("오늘은 받은 통화가 없습니다", ""), status(SyncDeskState().afterEvents(park)))
    }

    @Test
    fun `the contact line names two people per kind and counts the rest`() {
        val digest = ContactDigest.of(
            listOf("김 팀장", "어머니").map { Contact(it, ContactKind.MissedCall, it) } +
                listOf("박 대리", "정 매니저", "최 수석", "박 대리", "한 연구원").mapIndexed { i, from -> Contact("m$i", ContactKind.Message, from) },
        )!!

        assertEquals("받지 않은 전화 2건 (김 팀장, 어머니) · 메시지 5건 (박 대리, 정 매니저 외 2명)", contactLine(resources, digest))
    }

    @Test
    fun `office mode stopped half-way says so`() {
        val park = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Park))
        val stopped = SyncDeskState().afterEvents(park, SyncDeskEvent.OfficeRequested, SyncDeskEvent.OfficePauseRequested)

        assertEquals(StatusText("멈췄습니다", "시트와 운전대가 지금 위치에 멈춰 있습니다"), status(stopped))
    }

    @Test
    fun `office mode reports delivered power including limits and disconnected devices`() {
        val office = SyncDeskState(
            signals = VehicleSignals(gear = Gear.Park),
            office = OfficeState(phase = OfficePhase.On),
            usb = UsbPort(on = true, deviceWatts = 140),
        )
        assertEquals(StatusText("오피스 모드를 켰습니다", "노트북 충전 중 · 140W"), status(office))
        assertEquals("노트북 충전 중 · 60W", status(office.copy(usb = office.usb.copy(limitWatts = 60))).detail)
        assertEquals("노트북 충전 중 · 100W", status(office.copy(usb = office.usb.copy(maxWatts = 100))).detail)
        assertEquals("노트북 충전이 꺼져 있습니다", status(office.copy(usb = office.usb.copy(on = false))).detail)
        assertEquals("노트북을 연결하면 충전을 시작합니다", status(office.copy(usb = office.usb.copy(deviceWatts = null))).detail)
        assertEquals("노트북 충전 대기 중 · 0W", status(office.copy(usb = office.usb.copy(deviceWatts = 0))).detail)
    }

    @Test
    fun `office transitions explain the current movement`() {
        val parked = SyncDeskState(signals = VehicleSignals(gear = Gear.Park))
        assertEquals(
            StatusText("오피스 모드를 켜고 있습니다", "시트와 조명을 노트북 작업에 맞춥니다"),
            status(parked.copy(office = OfficeState(phase = OfficePhase.Applying))),
        )
        assertEquals(
            StatusText("원래대로 되돌리고 있습니다", "시트를 먼저 옮기고 운전대를 마지막에 내립니다"),
            status(parked.copy(office = OfficeState(phase = OfficePhase.Restoring))),
        )
    }

    @Test
    fun `disabled automatic control says whether a call is active`() {
        val disabled = SyncDeskState().afterEvents(SyncDeskEvent.AutoQuietToggled(enabled = false))
        assertEquals(StatusText("자동 조절이 꺼져 있습니다", "통화를 기다리는 중"), status(disabled))
        assertEquals(StatusText("자동 조절이 꺼져 있습니다", "통화 중입니다"), status(disabled.afterEvents(onCall)))
    }

    @Test
    fun `a refused window action shows reported values and waits through demanding roads`() {
        val pending = SyncDeskEngine.reduce(SyncDeskState(), onCall)
        val refused = SyncDeskEngine.reduce(pending, SyncDeskEvent.CommandRefused(CabinPart.Window))
        assertEquals(
            StatusText("차량이 창문 조작을 받아들이지 않았습니다", "송풍 6단 · 창문 30% 열림"),
            status(refused),
        )
        assertEquals("못 들은 부분을 표시하는 중", status(refused.afterEvents(heavyRain)).headline)
        assertEquals(
            "차량이 실내 조작을 받아들이지 않았습니다",
            status(refused.afterEvents(SyncDeskEvent.CommandRefused(CabinPart.Fan))).headline,
        )
    }

    @Test
    fun `taking the wheel has priority over disabled tuning and a cabin refusal`() {
        val takeover = SyncDeskState(
            signals = VehicleSignals(takeoverRequested = true),
            autoQuietEnabled = false,
            refusal = CabinPart.Window,
        )
        assertEquals(
            StatusText("운전을 넘겨받으세요", "책상을 접고 시트와 조명을 되돌립니다"),
            status(takeover),
        )
    }

    @Test
    fun `unread contacts are visible only after parking`() {
        val contacts = listOf(Contact("1", ContactKind.Message, "어머니"))
        val waiting = SyncDeskState(contacts = contacts)
        assertEquals(StatusText("통화를 기다리는 중", ""), status(waiting))
        assertEquals(
            StatusText("오늘은 받은 통화가 없습니다", "메시지 1건"),
            status(waiting.copy(signals = VehicleSignals(gear = Gear.Park))),
        )
        assertEquals(
            StatusText("오늘은 받은 통화가 없습니다", "받지 않은 전화 1건"),
            status(waiting.copy(signals = VehicleSignals(gear = Gear.Park), contacts = listOf(Contact("2", ContactKind.MissedCall, "김 팀장")))),
        )
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `English cards use translated sentences and singular and plural memo counts`() {
        assertEquals(StatusText("Waiting for a call", ""), status(SyncDeskState()))
        val onCallState = SyncDeskEngine.reduce(
            SyncDeskState(cabin = CabinState(fanLevel = 3, windowOpenPercent = 25)),
            onCall,
        )
        assertEquals(
            StatusText("Tuning the cabin so you can hear the call", "Fan level 3 · Window 25% open"),
            status(onCallState),
        )
        assertEquals(
            StatusText("Marking the parts you missed", "Gleo will go over them when the call ends"),
            status(onCallState.afterEvents(heavyRain)),
        )
        val hungUp = SyncDeskEvent.CallChanged(active = false, driverSpeaking = false)
        val oneCall = onCallState.afterEvents(note(1), hungUp)
        assertEquals(
            StatusText("Gleo has a summary ready", "Would you like to hear the call summary?"),
            status(oneCall),
        )
        val park = SyncDeskEvent.SignalsChanged(VehicleSignals(gear = Gear.Park))
        assertEquals("1 call memo", status(oneCall.afterEvents(park)).headline)
        assertEquals("2 call memos", status(oneCall.afterEvents(onCall, note(1), hungUp, park)).headline)
        val office = SyncDeskState(
            signals = VehicleSignals(gear = Gear.Park),
            office = OfficeState(phase = OfficePhase.On),
            usb = UsbPort(on = true, deviceWatts = 140, limitWatts = 60),
        )
        assertEquals(StatusText("Office mode is on", "Laptop charging · 60W"), status(office))
    }

    @Test
    fun `text is cut to what the platform allows while driving`() {
        val limited = SyncDeskState(ux = UxLimits(maxStringLength = 8))
        assertEquals("통화를 기다리…", status(limited).headline)
        val capturing = limited.afterEvents(onCall, heavyRain)
        assertEquals(StatusText("못 들은 부분…", "통화가 끝나면…"), status(capturing))
    }

    @Test
    fun `Gleo opens the talk after a call by asking whether the driver wants to hear the summary`() {
        assertEquals("통화 요약을 듣고 싶으신가요?", resources.getString(R.string.gleo_offer))
    }
}
