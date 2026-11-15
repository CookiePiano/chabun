package com.syncdesk.app.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingNote
import com.syncdesk.app.domain.MissedReason
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Keeps the call memos in the app's private preferences, as a JSON list. What earlier versions
 * of the app saved there under another name is removed without being read.
 */
class SharedPreferencesCallMemoStore(context: Context) : CallMemoStore {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    init {
        context.applicationContext.deleteSharedPreferences(LEGACY_FILE)
    }

    override fun load(): List<CallMemo> {
        val json = preferences.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index -> memo(array.getJSONObject(index)) }
        } catch (error: JSONException) {
            // Memos the app cannot read are dropped rather than crashing the app.
            Log.w("SyncDesk", "Discarding unreadable call memos: $error")
            emptyList()
        }
    }

    override fun save(memos: List<CallMemo>) {
        val array = JSONArray()
        memos.forEach { memo ->
            val notes = JSONArray()
            memo.notes.forEach { note ->
                notes.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("time", note.time)
                        .put("speaker", note.speaker)
                        .put("text", note.text)
                        .putOpt("gist", note.gist)
                        .putOpt("missed", note.missed?.name)
                        .put("byDriver", note.byDriver),
                )
            }
            array.put(
                JSONObject()
                    .put("id", memo.id)
                    .put("title", memo.title)
                    .put("date", memo.date)
                    .put("startedAt", memo.startedAt)
                    .put("explained", memo.explained)
                    .put("trip", memo.trip)
                    .put("notes", notes),
            )
        }
        preferences.edit { putString(KEY_LIST, array.toString()) }
    }

    override fun loadTrip(): SavedTrip? {
        if (!preferences.contains(KEY_TRIP)) return null
        return SavedTrip(
            trip = preferences.getInt(KEY_TRIP, 0),
            parked = preferences.getBoolean(KEY_TRIP_PARKED, true),
        )
    }

    override fun saveTrip(trip: SavedTrip) {
        preferences.edit {
            putInt(KEY_TRIP, trip.trip)
            putBoolean(KEY_TRIP_PARKED, trip.parked)
        }
    }

    private fun memo(item: JSONObject): CallMemo {
        val notes = item.getJSONArray("notes")
        return CallMemo(
            id = item.getInt("id"),
            title = item.optString("title"),
            date = item.optString("date"),
            startedAt = item.optString("startedAt"),
            explained = item.optBoolean("explained"),
            // A memo saved before drives were counted belongs to the earliest one.
            trip = item.optInt("trip"),
            notes =(0 until notes.length()).map { index ->
                val note = notes.getJSONObject(index)
                MeetingNote(
                    id = note.getInt("id"),
                    time = note.optString("time"),
                    speaker = note.optString("speaker"),
                    text = note.getString("text"),
                    gist = if (note.has("gist")) note.getString("gist") else null,
                    // A reason this version does not know still means the sentence was missed.
                    missed = if (note.has("missed")) {
                        MissedReason.entries.firstOrNull { it.name == note.getString("missed") } ?: MissedReason.Driving
                    } else {
                        null
                    },
                    byDriver = note.optBoolean("byDriver"),
                )
            },
        )
    }

    private companion object {
        const val FILE = "call_memos"
        const val KEY_LIST = "list"
        const val KEY_TRIP = "trip"
        const val KEY_TRIP_PARKED = "trip_parked"

        /** Where the app kept the driver's promises before call memos replaced them. */
        const val LEGACY_FILE = "commitments"
    }
}
