package com.syncdesk.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesCallMemoStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val memos = listOf(
        CallMemo(
            id = 3,
            title = "주간 납품 회의",
            date = "2026-11-14",
            startedAt = "09:30",
            explained = true,
            notes = listOf(
                MeetingNote(1, "09:30", "김 팀장", "울산 2공장 납품 일정부터 확인하겠습니다."),
                MeetingNote(
                    2, "09:31", "박 책임", "부품 입고가 사흘 늦어져서 납기를 11월 28일로 옮겨야 합니다.",
                    gist = "납기를 11월 28일로 옮기기", missed = MissedReason.Driving,
                ),
                MeetingNote(3, "09:32", "나", "공문은 제가 오후에 바로 보내겠습니다.", gist = "공문을 오후에 보내기", byDriver = true),
            ),
        ),
        CallMemo(id = 4, title = "라인 증설 기술 협의", date = "2026-11-15", startedAt = "14:05", trip = 7),
    )

    @Test
    fun aMemoSavedBeforeDrivesWereCountedIsReadAsTheEarliestDrive() {
        val before = """[{"id":2,"title":"t","date":"2026-11-14","startedAt":"09:30","explained":false,"notes":[]}]"""
        context.getSharedPreferences("call_memos", Context.MODE_PRIVATE).edit().putString("list", before).commit()

        assertEquals(listOf(CallMemo(id = 2, title = "t", date = "2026-11-14", startedAt = "09:30", trip = 0)), SharedPreferencesCallMemoStore(context).load())
    }

    @Test
    fun memosSurviveARestartWithEverySentenceAndItsMarks() {
        SharedPreferencesCallMemoStore(context).save(memos)

        assertEquals(memos, SharedPreferencesCallMemoStore(context).load())
    }

    @Test
    fun theDriveAndWhetherTheCarWasParkedSurviveARestart() {
        context.getSharedPreferences("call_memos", Context.MODE_PRIVATE).edit().clear().commit()
        assertNull(SharedPreferencesCallMemoStore(context).loadTrip())

        SharedPreferencesCallMemoStore(context).saveTrip(SavedTrip(trip = 7, parked = false))

        assertEquals(SavedTrip(trip = 7, parked = false), SharedPreferencesCallMemoStore(context).loadTrip())
    }

    @Test
    fun nothingSavedMeansNoMemos() {
        context.getSharedPreferences("call_memos", Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(emptyList<CallMemo>(), SharedPreferencesCallMemoStore(context).load())
    }

    @Test
    fun unreadableMemosAreDroppedInsteadOfCrashing() {
        context.getSharedPreferences("call_memos", Context.MODE_PRIVATE).edit().putString("list", "{not json").commit()

        assertEquals(emptyList<CallMemo>(), SharedPreferencesCallMemoStore(context).load())
    }

    @Test
    fun whatAnEarlierVersionKeptUnderAnotherNameIsRemovedWithoutBeingRead() {
        val earlier = """[{"time":"00:48","task":"공문을 오후에 보내기","said":"said","done":false}]"""
        context.getSharedPreferences("commitments", Context.MODE_PRIVATE).edit().putString("list", earlier).commit()

        val store = SharedPreferencesCallMemoStore(context)

        assertEquals(emptyList<CallMemo>(), store.load())
        assertFalse(context.getSharedPreferences("commitments", Context.MODE_PRIVATE).contains("list"))
    }
}
