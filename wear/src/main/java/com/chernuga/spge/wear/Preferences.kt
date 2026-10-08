package com.chernuga.spge.wear

import android.content.Context
import com.chernuga.spge.shared.ScheduleIndex

/**
 * Persists the user's filter choice across launches.
 *
 * <p>The web app remembers the last view type and name per type, so the watch
 * mirrors that: switching to Teacher and picking someone survives a restart
 * rather than snapping back to the default class.
 */
object Preferences {

    private const val PREFS = "spge_prefs"
    private const val KEY_TYPE = "filter_type"
    private const val KEY_NAME_PREFIX = "filter_name_"

    fun loadType(context: Context): ScheduleIndex.Type {
        val raw = prefs(context).getString(KEY_TYPE, null)
        return try {
            if (raw == null) ScheduleIndex.Type.CLASS else ScheduleIndex.Type.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            ScheduleIndex.Type.CLASS
        }
    }

    fun saveType(context: Context, type: ScheduleIndex.Type) {
        prefs(context).edit().putString(KEY_TYPE, type.name).apply()
    }

    /** Last selection for a dimension, or null when none was stored. */
    fun loadName(context: Context, type: ScheduleIndex.Type): String? =
        prefs(context).getString(KEY_NAME_PREFIX + type.name, null)

    fun saveName(context: Context, type: ScheduleIndex.Type, name: String?) {
        val e = prefs(context).edit()
        if (name == null) e.remove(KEY_NAME_PREFIX + type.name)
        else e.putString(KEY_NAME_PREFIX + type.name, name)
        e.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
