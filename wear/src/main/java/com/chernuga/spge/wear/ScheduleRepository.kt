package com.chernuga.spge.wear

import android.content.Context
import com.chernuga.spge.shared.Lesson
import com.chernuga.spge.shared.SyncContract
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Single source of truth for the wear app.
 *
 * <p>Holds the schedule in memory and mirrors it to disk so the watch still
 * shows a timetable with no phone nearby.
 */
object ScheduleRepository {

    private const val PREFS = "spge_schedule"
    private const val KEY_LESSONS = "lessons_json"
    private const val KEY_UPDATED = "updated_at"

    /**
     * Bumped on every accepted update so Compose can observe changes without
     * polling the lists themselves.
     */
    private val _revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    val revision: kotlinx.coroutines.flow.StateFlow<Int> get() = _revision

    @Volatile
    var lessons: List<Lesson> = emptyList()
        private set

    @Volatile
    var updatedAt: Long = 0L
        private set

    /** Loads the cached schedule from disk. Safe to call on the main thread. */
    fun loadCache(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = p.getString(KEY_LESSONS, null) ?: return
        updatedAt = p.getLong(KEY_UPDATED, 0L)
        lessons = try {
            Lesson.listFromJson(JSONArray(json))
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Replaces the in-memory schedule and persists it. */
    fun update(context: Context, newLessons: List<Lesson>, timestamp: Long) {
        lessons = newLessons
        updatedAt = timestamp
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LESSONS, Lesson.listToJson(newLessons).toString())
            .putLong(KEY_UPDATED, timestamp)
            .apply()
        // Notify the UI.
        _revision.value = _revision.value + 1
    }

    /**
     * Re-reads the persisted schedule.
     *
     * <p>Used when a message arrives while the app is in the foreground: the
     * listener service runs in the same process, so the write it performs must
     * be picked up here to reach the UI.
     */
    fun refreshFromCache(context: Context) {
        loadCache(context)
        _revision.value = _revision.value + 1
    }

    /**
     * Asks the phone to re-read the page and push a fresh schedule.
     *
     * <p>Messages only reach a currently connected node, so this reports false
     * when the phone is absent rather than failing silently — the UI uses that
     * to tell the user to bring the phone into range.
     */
    suspend fun requestRefresh(context: Context) = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            for (node in nodes) {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, SyncContract.PATH_REQUEST, ByteArray(0))
                    .await()
            }
            nodes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
