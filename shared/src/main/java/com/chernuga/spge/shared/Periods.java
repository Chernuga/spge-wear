package com.chernuga.spge.shared;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Period start/end times.
 *
 * <p>These mirror {@code START_TIMES} / {@code END_TIMES} in the web app's
 * {@code constants.js} exactly. They are duplicated here rather than scraped
 * because the watch must be able to render times while offline, with no
 * WebView and no phone connection.
 *
 * <p>Note the web app's maps are 1-based for end times: a lesson with
 * {@code end = 2} finishes at {@code END_TIMES[2]}.
 */
public final class Periods {

    private Periods() {}

    /** Period index (0-based) to start time. */
    private static final Map<Integer, String> START = new LinkedHashMap<>();
    /** Period index (1-based, i.e. the lesson's {@code end}) to end time. */
    private static final Map<Integer, String> END = new LinkedHashMap<>();

    static {
        START.put(0, "8:00");
        START.put(1, "8:45");
        START.put(2, "9:50");
        START.put(3, "10:35");
        START.put(4, "11:40");
        START.put(5, "12:25");
        START.put(6, "13:30");
        START.put(7, "14:15");

        END.put(1, "8:45");
        END.put(2, "9:30");
        END.put(3, "10:35");
        END.put(4, "11:20");
        END.put(5, "12:25");
        END.put(6, "13:10");
        END.put(7, "14:15");
        END.put(8, "15:00");
    }

    /** Highest period index a lesson can start at. */
    public static final int MAX_PERIODS = 8;

    public static String start(int period) {
        String s = START.get(period);
        return s == null ? "??:??" : s;
    }

    public static String end(int period) {
        String s = END.get(period);
        return s == null ? "??:??" : s;
    }

    /** Formatted range for a lesson, e.g. {@code "8:45 - 9:30"}. */
    public static String range(int startPeriod, int endPeriod) {
        return start(startPeriod) + " - " + end(endPeriod);
    }

    /** Parses {@code "H:mm"} into minutes since midnight; -1 when malformed. */
    public static int toMinutes(String hhmm) {
        if (hhmm == null) return -1;
        int i = hhmm.indexOf(':');
        if (i <= 0) return -1;
        try {
            int h = Integer.parseInt(hhmm.substring(0, i).trim());
            int m = Integer.parseInt(hhmm.substring(i + 1).trim());
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String dayName(int day) {
        switch (day) {
            case 0: return "Пон";
            case 1: return "Вт";
            case 2: return "Ср";
            case 3: return "Чет";
            case 4: return "Пет";
            default: return "?";
        }
    }
}
