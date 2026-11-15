package com.syncdesk.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.Contact
import com.syncdesk.app.domain.ContactKind
import com.syncdesk.app.domain.OfficeScene
import com.syncdesk.app.domain.SyncDeskEngine
import com.syncdesk.app.domain.UsbPort
import com.syncdesk.app.domain.afterOfficeOn
import com.syncdesk.app.ui.parked.ParkedScreen
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.DesignFrame
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import com.syncdesk.app.domain.Gear
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason
import com.syncdesk.app.domain.SyncDeskEvent
import com.syncdesk.app.domain.SyncDeskState
import com.syncdesk.app.domain.UxLimits
import com.syncdesk.app.domain.VehicleSignals
import com.syncdesk.app.ui.office.OfficePanel
import com.syncdesk.app.ui.theme.SyncDeskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** What TalkBack and switch access get from the call memo and the office panel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1100dp-h800dp-land")
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val memo = CallMemo(
        id = 1,
        title = "주간 납품 회의",
        date = "2026-11-15",
        startedAt = "14:05",
        notes = listOf(
            MeetingNote(1, "14:05", "김 팀장", "울산 2공장 납품 일정부터 확인하겠습니다."),
            MeetingNote(2, "14:06", "박 책임", "납기를 11월 28일로 옮겨야 합니다.", gist = "납기를 11월 28일로 옮기기", missed = MissedReason.Driving),
            MeetingNote(3, "14:07", "나", "공문은 제가 오후에 바로 보내겠습니다.", gist = "공문을 오후에 보내기", byDriver = true),
        ),
    )

    /** The parked screen at the design's window size, as the skeleton lays it out. */
    private fun parked(state: SyncDeskState) {
        compose.setContent {
            SyncDeskTheme(night = false, motionEnabled = false) {
                DesignFrame(designHeight = Design.appWindow.height, designWidth = Design.appWindow.width) {
                    ParkedScreen(
                        state = state,
                        materials = emptyList(),
                        onEvent = {},
                        onOpenOffice = {},
                        today = LocalDate.of(2026, 11, 15),
                    )
                }
            }
        }
    }

    private fun parkedWith(vararg memos: CallMemo) =
        parked(SyncDeskState(signals = VehicleSignals(gear = Gear.Park), memos = memos.toList()))

    @Test
    fun theMemoShowsItsSummaryAndTheMissedPartAndKeepsTheWholeCallInItsSheet() {
        parkedWith(memo)

        // The caller line and the list row both say when the call was and how long it took.
        assertEquals(2, compose.onAllNodesWithText("오후 2:05 · 3분").fetchSemanticsNodes().size)
        compose.onNodeWithText("새 메모").assertExists()
        // The key summary, with what the driver promised marked as such.
        compose.onNodeWithText("납기를 11월 28일로 옮기기").assertExists()
        compose.onNodeWithText("내 약속 공문을 오후에 보내기").assertExists()
        // The missed part: the sentence itself, why and when it was missed.
        compose.onNodeWithText("납기를 11월 28일로 옮겨야 합니다.").assertExists()
        compose.onNodeWithText("못 들은 부분 · 운전 중").assertExists()
        compose.onNodeWithText("1:00").assertExists()
        // The wave is one picture for TalkBack.
        compose.onNodeWithContentDescription("통화 흐름. 못 들은 구간 1곳").assertExists()
        // The whole call is not shown until asked for.
        compose.onNodeWithText("울산 2공장 납품 일정부터 확인하겠습니다.").assertDoesNotExist()
    }

    @Test
    fun theFullTranscriptCardOpensTheSheetWhichMarksTheDriverAndTheMissedSentenceAndCloses() {
        parkedWith(memo)
        val label = SemanticsMatcher("click label") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == "전체 내용 펼치기"
        }

        compose.onNodeWithText("3문장 · 3분").assertHasClickAction().assert(label).performClick()

        compose.onNodeWithText("울산 2공장 납품 일정부터 확인하겠습니다.").assertExists()
        compose.onNodeWithText("주간 납품 회의 · 오후 2:05").assertExists()
        // The driver's line is marked "나"; the missed one says so.
        compose.onNode(hasText("공문은 제가 오후에 바로 보내겠습니다.") and hasText("나")).assertExists()
        compose.onNode(hasText("납기를 11월 28일로 옮겨야 합니다.") and hasStateDescription("못 들은 문장")).assertExists()
        // What is behind the sheet cannot be reached while it is open.
        compose.onNodeWithText("못 들은 부분 · 운전 중").assertDoesNotExist()

        compose.onNodeWithContentDescription("닫기").performClick()
        compose.onNodeWithText("울산 2공장 납품 일정부터 확인하겠습니다.").assertDoesNotExist()
        compose.onNodeWithText("못 들은 부분 · 운전 중").assertExists()
    }

    @Test
    fun aCallHeardInFullSaysThatNothingWasMissedAndAnExplainedMemoIsNotNew() {
        parkedWith(memo.copy(explained = true, notes = memo.notes.map { it.copy(missed = null) }))

        compose.onNodeWithText("못 들은 부분이 없습니다").assertExists()
        compose.onNodeWithContentDescription("통화 흐름. 못 들은 구간이 없습니다").assertExists()
        compose.onNodeWithText("새 메모").assertDoesNotExist()
    }

    @Test
    fun withoutACallTheEmptyScreenSaysSo() {
        parkedWith()

        compose.onNodeWithText("오늘은 받은 통화가 없습니다").assertExists()
        compose.onNodeWithText("오피스 모드").assertHasClickAction()
    }

    @Test
    fun underThePlatformsItemLimitTheSummaryComesFirstAndNothingClaimsNothingWasMissed() {
        parked(
            SyncDeskState(
                signals = VehicleSignals(gear = Gear.Drive, autonomous = true),
                memos = listOf(memo),
                ux = UxLimits(maxItems = 1),
            ),
        )

        compose.onNodeWithText("납기를 11월 28일로 옮기기").assertExists()
        compose.onNodeWithText("내 약속 공문을 오후에 보내기").assertDoesNotExist()
        compose.onNodeWithText("못 들은 부분이 없습니다").assertDoesNotExist()
    }

    @Test
    fun officeModeIsUnavailableToEveryInputWhileSetupIsForbidden() {
        val events = mutableListOf<SyncDeskEvent>()
        val state = SyncDeskState(
            signals = VehicleSignals(gear = Gear.Drive, autonomous = true),
            ux = UxLimits(noSetup = true),
        )
        compose.setContent {
            SyncDeskTheme(night = false, motionEnabled = false) {
                OfficePanel(state = state, onEvent = { events += it }, fillHeight = true)
            }
        }
        // The start button says why it is off and does nothing when pressed.
        compose.onNodeWithText("오피스 모드 켜기").assertDoesNotExist()
        compose.onNodeWithText("주차하면 켤 수 있습니다").assertIsNotEnabled().performClick()
        compose.onNodeWithText("차가 움직이는 동안에는 켤 수 없습니다. 주차(P)하면 켤 수 있습니다.").assertExists()
        // Nothing else can be opened for editing either: no steps, no settings button.
        compose.onNodeWithText("운전대를 위로 올립니다", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("설정 바꾸기").assertDoesNotExist()
        assertEquals(emptyList<SyncDeskEvent>(), events)
    }

    private fun parkedPanel(memos: List<CallMemo>) {
        val state = SyncDeskState(signals = VehicleSignals(gear = Gear.Park), memos = memos)
        compose.setContent {
            SyncDeskTheme(night = false, motionEnabled = false) {
                OfficePanel(state = state, onEvent = {}, fillHeight = true)
            }
        }
    }

    @Test
    fun parkingWithAMemoNotYetExplainedLeavesTheMemoToTheParkedScreenAndStillOffersOfficeMode() {
        // The redesign (spec 6) dropped the panel's memo line; the parked screen's "새 메모" says it.
        parkedPanel(listOf(memo.copy(id = 1, explained = true), memo.copy(id = 2)))

        compose.onNodeWithText("아직 설명을 듣지 않은 통화 메모가 있습니다").assertDoesNotExist()
        compose.onNodeWithText("시트와 조명을 노트북 작업에 맞춥니다").assertExists()
        compose.onNodeWithText("오피스 모드 켜기").assertHasClickAction()
    }

    /** A row of the received-call list: the selectable node with [title] (the caller line has the same text). */
    private fun row(title: String) = compose.onNode(hasText(title) and isSelectable())

    @Test
    fun parkedTheDrivesCallsAreListedUnheardFirstWithTheContactLineAndOneOpensOnPress() {
        val state = SyncDeskState(
            signals = VehicleSignals(gear = Gear.Park),
            trip = 2,
            memos = listOf(
                memo.copy(id = 1, title = "지난 통화", trip = 1),
                memo.copy(id = 2, title = "들은 통화", trip = 2, explained = true),
                memo.copy(id = 3, title = "안 들은 통화", trip = 2),
            ),
            contacts = listOf(Contact("c1", ContactKind.MissedCall, "어머니"), Contact("m1", ContactKind.Message, "박 대리")),
        )
        parked(state)

        compose.onNodeWithText("받지 않은 전화 1건 · 메시지 1건").assertExists()
        compose.onNodeWithText("어머니 · 박 대리").assertExists()
        val unheard = row("안 들은 통화").fetchSemanticsNode().boundsInRoot.top
        val heard = row("들은 통화").fetchSemanticsNode().boundsInRoot.top
        assertTrue(unheard < heard)
        // The first call is chosen, and its memo is shown; TalkBack hears which row is chosen.
        row("안 들은 통화").assertIsSelected()
        row("들은 통화").assertIsNotSelected()
        // An earlier drive's call stays folded under its heading.
        compose.onNodeWithText("이전 통화").assertExists()
        compose.onNodeWithText("지난 통화").assertDoesNotExist()

        row("들은 통화").performClick()
        row("들은 통화").assertIsSelected()
        row("안 들은 통화").assertIsNotSelected()
        compose.onNode(hasText("들은 통화") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()

        compose.onNodeWithText("이전 통화").performClick()
        row("지난 통화").performClick().assertIsSelected()
        compose.onNode(hasText("지난 통화") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun aDriveWithoutCallsSaysSoAndShowsNoContactLineWithoutContacts() {
        parked(SyncDeskState(signals = VehicleSignals(gear = Gear.Park), trip = 2, memos = listOf(memo.copy(trip = 1))))

        compose.onNodeWithText("오늘은 받은 통화가 없습니다").assertExists()
        compose.onNodeWithText("받지 않은 전화", substring = true).assertDoesNotExist()
        // The earlier drive's call is still there, folded.
        compose.onNodeWithText("이전 통화").assertExists()
    }

    @Test
    fun officeModeUnderWayCanBeStoppedAndOnceStoppedCarriedOnOrPutBack() {
        val events = mutableListOf<SyncDeskEvent>()
        val parked = SyncDeskState(signals = VehicleSignals(gear = Gear.Park))
        val applying = SyncDeskEngine.reduce(parked, SyncDeskEvent.OfficeRequested)
        val stopped = SyncDeskEngine.reduce(applying, SyncDeskEvent.OfficePauseRequested)
        var shown by mutableStateOf(applying)
        compose.setContent {
            SyncDeskTheme(night = false, motionEnabled = false) {
                OfficePanel(state = shown, onEvent = { events += it }, fillHeight = true)
            }
        }
        compose.onNodeWithText("멈춤").performClick()
        shown = stopped
        compose.onNodeWithText("멈췄습니다").assertExists()
        compose.onNodeWithText("이어서 켜기").performClick()
        compose.onNodeWithText("원래대로").performClick()

        assertEquals(
            listOf(SyncDeskEvent.OfficePauseRequested, SyncDeskEvent.OfficeResumeRequested, SyncDeskEvent.OfficeRestoreRequested),
            events,
        )
    }

    @Test
    fun officeModeOnOffersWorkAndMeetingAndShowsTheUsbPortAndTheBatteryGuard() {
        val events = mutableListOf<SyncDeskEvent>()
        val on = SyncDeskState(signals = VehicleSignals(gear = Gear.Park)).afterOfficeOn().let {
            SyncDeskEngine.reduce(
                SyncDeskEngine.reduce(it, SyncDeskEvent.UsbReported(UsbPort(on = true, limitWatts = 60, deviceWatts = 140))),
                SyncDeskEvent.CabinReported(it.cabin.copy(batteryPercent = 25)),
            )
        }
        compose.setContent {
            SyncDeskTheme(night = false, motionEnabled = false) {
                OfficePanel(state = on, onEvent = { events += it }, fillHeight = false)
            }
        }
        // Work is chosen; TalkBack hears which of the two is.
        compose.onNode(hasText("작업") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("회의") and isSelectable()).assertIsNotSelected().performClick()
        // The port, what it gives, the car's battery and why the output was lowered.
        compose.onNodeWithText("센터 콘솔 앞").assertExists()
        compose.onNodeWithText("USB-C").assertExists()
        compose.onNodeWithText("60W").assertExists()
        compose.onNodeWithText("차 배터리 25%").assertExists()
        compose.onNodeWithText("배터리가 적어 출력을 60W로 낮췄습니다", substring = true).assertExists()

        assertEquals(listOf<SyncDeskEvent>(SyncDeskEvent.OfficeSceneChanged(OfficeScene.Meeting)), events)
    }

    @Test
    fun parkingWithTheLatestMemoExplainedDescribesTheOfficeMode() {
        parkedPanel(listOf(memo.copy(id = 1), memo.copy(id = 2, explained = true)))

        compose.onNodeWithText("시트와 조명을 노트북 작업에 맞춥니다").assertExists()
        // The steps it will take, in order (each step row is one merged node for TalkBack).
        val wheel = compose.onNodeWithText("운전대를 위로 올립니다", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val environment = compose.onNodeWithText("조명, 가리개, 공조, 문 잠금, USB-C를 맞춥니다", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(wheel < environment)
    }
}
