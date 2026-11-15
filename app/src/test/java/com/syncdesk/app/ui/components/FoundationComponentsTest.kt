package com.syncdesk.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncdesk.app.ui.theme.SyncDeskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack and switch access get from the shared components of the redesign. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1682dp-h1210dp-land-mdpi")
class FoundationComponentsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { SyncDeskTheme(night = true, motionEnabled = false, content = content) }
    }

    @Test
    fun theCallWaveIsReadAsOnePictureWithItsMissedStretches() {
        show {
            CallWave(
                heights = waveHeights(seed = 7, count = 170),
                missed = listOf(MissedStretch(0.325f, 0.395f, "좌회전 중", LineIcon.Turn), MissedStretch(0.752f, 0.812f, "실내 소음", LineIcon.Wind)),
                startLabel = "0:00",
                endLabel = "8:12",
            )
        }
        compose.onNodeWithContentDescription("통화 흐름. 못 들은 구간 2곳").assertExists()
        // The labels are part of the picture, not read one by one.
        compose.onNodeWithText("좌회전 중").assertDoesNotExist()
    }

    @Test
    fun receivedCallsAndWorkOrMeetingSayWhichOneIsChosen() {
        show {
            var chosen by remember { mutableStateOf(0) }
            Column {
                ReceivedCallRow(name = "김 팀장", detail = "오후 1:52 · 8분", selected = chosen == 0, onClick = { chosen = 0 }, hasNewMemo = true)
                ReceivedCallRow(name = "최 수석", detail = "오전 11:20 · 5분", selected = chosen == 1, onClick = { chosen = 1 })
                SegmentedControl(listOf("작업", "회의"), selectedIndex = chosen, onSelect = { chosen = it })
            }
        }
        compose.onNodeWithText("김 팀장").assertIsSelected()
        compose.onNodeWithText("최 수석").performClick()
        compose.onNodeWithText("최 수석").assertIsSelected()
        compose.onNodeWithText("김 팀장").assertIsNotSelected()
        compose.onNodeWithText("회의").assertIsSelected()
        compose.onNodeWithContentDescription("새 메모", useUnmergedTree = true).assertExists()
    }

    @Test
    fun eachStepSaysItsNumberWhatItDoesAndWhereItStands() {
        show {
            StepList(
                listOf(
                    StepItem(1, "운전대를 위로 올립니다", StepState.Done),
                    StepItem(2, "시트를 뒤로 밀고 조금 낮춥니다", StepState.Now),
                    StepItem(3, "방석 앞쪽을 낮추고 짧게 합니다", StepState.Waiting),
                ),
            )
        }
        compose.onNodeWithContentDescription("2단계, 시트를 뒤로 밀고 조금 낮춥니다").assertExists()
    }

    @Test
    fun theSheetTakesFocusHidesWhatIsBehindClosesOnBackAndGivesFocusBack() {
        var closed = 0
        var openerFocused = false
        val opener = FocusRequester()
        var open by mutableStateOf(false)
        show {
            SheetHost(
                open = open,
                onClose = {
                    closed++
                    open = false
                },
                title = "전체 내용",
                subtitle = "김 팀장 · 오후 1:52",
                returnFocusTo = opener,
                modifier = Modifier.fillMaxSize(),
                sheet = { Text("울산 2공장 납품 일정부터 확인하겠습니다.") },
            ) {
                Box(Modifier.fillMaxSize()) {
                    BigButton(
                        label = "전체 내용 보기",
                        onClick = { open = true },
                        modifier = Modifier
                            .focusRequester(opener)
                            .onFocusChanged { openerFocused = it.isFocused },
                    )
                }
            }
        }
        compose.onNodeWithText("전체 내용 보기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("전체 내용").assertIsFocused()
        // What is behind the sheet cannot be reached while it is open.
        compose.onNodeWithText("전체 내용 보기").assertDoesNotExist()
        compose.onNodeWithContentDescription("닫기").assertExists()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closed)
        compose.onNodeWithText("전체 내용 보기").assertExists()
        assertTrue("focus returns to the button that opened the sheet", openerFocused)
    }

    @Test
    fun turnsAndAvatarsShowForKnownAndUnknownPeople() {
        show {
            Column {
                Conversation {
                    GleoTurn("통화 요약을 듣고 싶으신가요?", old = true)
                    DriverTurn("네", old = true)
                    GleoTurn("김 팀장과의 통화입니다.", speaking = true)
                    DriverTurn("", listening = true)
                }
                Avatar("김 팀장")
                Avatar("모르는 사람")
            }
        }
        compose.onNodeWithText("김 팀장과의 통화입니다.").assertExists()
        compose.onNodeWithText("듣고 있습니다").assertExists()
        compose.onNodeWithContentDescription("모르는 사람").assertExists()
    }
}
