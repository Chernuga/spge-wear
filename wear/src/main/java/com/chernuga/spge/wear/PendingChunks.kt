package com.chernuga.spge.wear

import android.content.Context
import android.util.Log
import com.chernuga.spge.shared.Lesson
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Accumulates message chunks of one schedule generation and commits the
 * snapshot once it is complete.
 *
 * <p>Chunks arrive as separate messages that may be delivered in any order or
 * interleaved with a newer send, so partial state is keyed by generation
 * rather than held in a single slot. Only the newest generation is retained:
 * an older, unfinished snapshot has been superseded and is dropped.
 */
object PendingChunks {

    private const val TAG = "SpgePendingChunks"

    private class Partial {
        val chunks = ConcurrentHashMap<Int, List<Lesson>>()
        var total: Int = 1
        var expected: Int = -1
    }

    private val pending = ConcurrentHashMap<Long, Partial>()

    @Synchronized
    fun add(context: Context, generation: Long, index: Int, total: Int,
            expected: Int, lessons: List<Lesson>) {
        // Discard anything older than the newest generation we have seen.
        val newest = pending.keys.maxOrNull() ?: generation
        if (generation < newest) {
            Log.d(TAG, "dropping chunk from superseded generation $generation")
            return
        }
        if (generation > newest) {
            pending.clear()
        }

        val p = pending.getOrPut(generation) { Partial() }
        p.total = total
        if (expected > 0) p.expected = expected
        p.chunks[index] = lessons

        Log.d(TAG, "generation $generation: ${p.chunks.size}/$total chunks")
    }

    /**
     * Commits the generation when every chunk has arrived.
     *
     * @return true when the snapshot was written.
     */
    @Synchronized
    fun tryCommit(context: Context, generation: Long): Boolean {
        val p = pending[generation] ?: return false
        if (p.chunks.size < p.total) return false

        val merged = ArrayList<Lesson>()
        for (i in 0 until p.total) {
            merged.addAll(p.chunks[i] ?: emptyList())
        }

        if (merged.isEmpty()) {
            Log.w(TAG, "reassembled an empty schedule; keeping previous")
            return false
        }

        // Guard against a truncated send silently replacing a good timetable.
        if (p.expected > 0 && merged.size != p.expected) {
            Log.w(TAG, "expected ${p.expected} lessons, got ${merged.size}; keeping previous")
            return false
        }

        ScheduleRepository.update(context.applicationContext, merged, generation)
        pending.remove(generation)

        // Mirror into the Data Layer store as well, so a watch that later
        // regains Data Layer support (or is replaced) can still pull. Failures
        // here are harmless and deliberately ignored.
        Log.d(TAG, "committed ${merged.size} lessons for generation $generation")
        return true
    }

    /** Serialises the current schedule so it can be restored on next launch. */
    fun snapshotJson(): String = Lesson.listToJson(ScheduleRepository.lessons).toString()

    fun parse(json: String?): List<Lesson> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            Lesson.listFromJson(JSONArray(json))
        } catch (e: Exception) {
            emptyList()
        }
    }
}
