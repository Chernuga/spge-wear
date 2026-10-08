package com.chernuga.spge.wear

import android.util.Log
import com.chernuga.spge.shared.Lesson
import com.chernuga.spge.shared.SyncContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives schedule snapshots pushed by the phone over the message channel.
 *
 * <p>The phone sends one message per chunk, so chunks are accumulated until a
 * whole generation has arrived and only then written to the repository. A
 * partially delivered update therefore never replaces a good timetable.
 *
 * <p>Declared in the manifest with a {@code MESSAGE_RECEIVED} intent-filter,
 * which is how Wear OS discovers companion listeners — verified against the
 * watch, where every first-party companion service registers this action and
 * none register {@code DATA_CHANGED}.
 */
class WearDataService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path ?: return
        if (!path.startsWith(SyncContract.PATH_SCHEDULE)) return

        try {
            val json = String(event.data, Charsets.UTF_8)
            val o = JSONObject(json)

            val generation = o.optLong(SyncContract.KEY_GENERATION, 0L)
            val index = o.optInt(SyncContract.KEY_CHUNK_INDEX, 0)
            val total = o.optInt(SyncContract.KEY_CHUNK_TOTAL, 1)
            val expected = o.optInt(SyncContract.KEY_TOTAL_LESSONS, -1)
            val raw = o.optString(SyncContract.KEY_LESSONS, "")

            Log.d(TAG, "chunk $index/$total gen=$generation")

            if (raw.isEmpty()) return

            val lessons = Lesson.listFromJson(JSONArray(raw))

            // Stash this chunk. Single-chunk sends (small timetables) commit
            // immediately, which keeps the common case simple.
            PendingChunks.add(applicationContext, generation, index, total, expected, lessons)

            val done = PendingChunks.tryCommit(applicationContext, generation)
            if (done) {
                Log.d(TAG, "committed generation $generation")
            } else {
                Log.d(TAG, "waiting for more chunks of generation $generation")
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to handle message at $path", e)
        }
    }

    private companion object {
        const val TAG = "SpgeWearDataService"
    }
}
