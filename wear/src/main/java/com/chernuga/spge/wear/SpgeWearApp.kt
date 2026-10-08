package com.chernuga.spge.wear

import android.app.Application
import com.google.android.gms.wearable.Wearable

/**
 * Warms the schedule cache before the first frame.
 *
 * <p>Loading from disk in {@code onCreate} means the timetable is already
 * populated when the Compose UI first composes, avoiding an empty-state flash.
 */
class SpgeWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScheduleRepository.loadCache(this)
    }
}
