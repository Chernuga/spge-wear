package com.chernuga.spge.shared;

/** Constants describing the phone ↔ watch sync protocol. */
public final class SyncContract {

    private SyncContract() {}

    /**
     * Data Layer path used for {@code DataClient} payloads.
     *
     * <p>DataItem (not Message) is the right primitive here: it is durable and
     * survives the watch being offline, asleep, or out of Bluetooth range, and
     * is re-delivered automatically. A schedule is state, not an event.
     */
    public static final String PATH_SCHEDULE = "/spge/schedule";

    /** Path for the wear app to ask the phone to (re)fetch and push data. */
    public static final String PATH_REQUEST = "/spge/request";

    /** Key holding the serialised lesson array inside the DataMap. */
    public static final String KEY_LESSONS = "lessons";

    /** Key holding the wall-clock time the payload was produced (epoch millis). */
    public static final String KEY_UPDATED_AT = "updatedAt";

    /** Key holding an error string, set instead of lessons when a fetch failed. */
    public static final String KEY_ERROR = "error";

    /** Key advertising the class names available, so the watch can show a picker. */
    public static final String KEY_CLASSES = "classes";

    /**
     * Chunking keys.
     *
     * <p>A whole-school timetable serialises to several hundred KB, well over
     * the Data Layer's ~100 KB per-item limit, so the schedule is published as
     * numbered items under {@code PATH_SCHEDULE/<index>} and reassembled by the
     * receiver.
     */
    public static final String KEY_CHUNK_INDEX = "chunkIndex";
    public static final String KEY_CHUNK_TOTAL = "chunkTotal";

    /**
     * Timestamp identifying one complete send.
     *
     * <p>All chunks of a send share a generation. The receiver only applies a
     * snapshot once every chunk of that generation has arrived, which stops a
     * half-delivered update from replacing a good schedule.
     */
    public static final String KEY_GENERATION = "generation";

    /** Total lessons in the send, used to confirm the reassembled snapshot. */
    public static final String KEY_TOTAL_LESSONS = "totalLessons";

    /**
     * Capability name the phone declares and the watch requires.
     *
     * <p>Both the phone and wear apps must be signed with the same key and be
     * installed from the same store listing for the Data Layer to treat them as
     * one app. During local development this means installing both APKs
     * manually with matching signatures.
     */
    public static final String CAPABILITY_PHONE_APP = "spge_phone_app";
}
