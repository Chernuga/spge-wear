package com.chernuga.spge.shared;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single scheduled lesson, mirroring the web app's lesson object.
 *
 * <p>The web app's "report" format is a flat text block per lesson:
 * <pre>
 * Start: 0
 * End: 1
 * Day: 0
 * Subject: Математика
 * Room: 101
 * Class: 8а
 * Teacher: Иванова
 * Color: #3b82f6
 * Type: 1
 * Week: A
 * Group: 0
 * </pre>
 */
public final class Lesson {

    /** Period index the lesson starts at (0-based). */
    public final int start;
    /** Period index the lesson ends at (exclusive). */
    public final int end;
    /** Day index, 0 = Monday. */
    public final int day;
    public final String subject;
    public final String room;
    public final String className;
    public final String teacher;
    /** Hex colour such as {@code #3b82f6}. */
    public final String color;
    /** Week marker: "A", "B" or "C" (both). */
    public final String week;
    /** Group: 0 = both, 1 = A, 2 = B. */
    public final int group;

    public Lesson(int start, int end, int day, String subject, String room,
                  String className, String teacher, String color, String week, int group) {
        this.start = start;
        this.end = end;
        this.day = day;
        this.subject = subject;
        this.room = room;
        this.className = className;
        this.teacher = teacher;
        this.color = color;
        this.week = week;
        this.group = group;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("start", start);
        o.put("end", end);
        o.put("day", day);
        o.put("subject", subject);
        o.put("room", room);
        o.put("className", className);
        o.put("teacher", teacher);
        o.put("color", color);
        o.put("week", week);
        o.put("group", group);
        return o;
    }

    public static Lesson fromJson(JSONObject o) {
        return new Lesson(
                o.optInt("start", 0),
                o.optInt("end", 1),
                o.optInt("day", 0),
                o.optString("subject", ""),
                o.optString("room", "?"),
                o.optString("className", ""),
                o.optString("teacher", ""),
                o.optString("color", "#888888"),
                o.optString("week", "C"),
                o.optInt("group", 0));
    }

    /**
     * Parses the web app's report text into lessons.
     *
     * <p>Deliberately tolerant: a block missing any required field is skipped
     * rather than throwing, matching the web parser's behaviour.
     */
    public static List<Lesson> parseReport(String text) {
        List<Lesson> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;

        for (String block : text.split("\n\\s*\n")) {
            if (block.trim().isEmpty()) continue;

            Integer start = null, end = null, day = null, group = null;
            String subject = null, room = null, className = null, teacher = null, color = null, week = null;

            for (String line : block.split("\n")) {
                int idx = line.indexOf(':');
                if (idx == -1) continue;
                String key = line.substring(0, idx).trim().toLowerCase();
                String val = line.substring(idx + 1).trim();
                switch (key) {
                    case "start":   start = parseIntOrNull(val); break;
                    case "end":     end = parseIntOrNull(val); break;
                    case "day":     day = parseIntOrNull(val); break;
                    case "subject": subject = val; break;
                    case "room":    room = val; break;
                    case "class":   className = val; break;
                    case "teacher": teacher = val; break;
                    case "color":   color = val; break;
                    case "week":    week = val; break;
                    case "group":   group = parseIntOrNull(val); break;
                    default: break;
                }
            }

            if (start == null || end == null || day == null
                    || subject == null || subject.isEmpty()
                    || className == null || className.isEmpty()
                    || teacher == null) {
                continue;
            }
            if (color == null || color.isEmpty()) color = "#888888";
            if (week == null || week.isEmpty()) week = "C";
            if (group == null) group = 0;
            if (room == null || room.isEmpty()) room = "?";

            out.add(new Lesson(start, end, day, subject, room, className, teacher, color, week, group));
        }
        return out;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static JSONArray listToJson(List<Lesson> lessons) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Lesson l : lessons) arr.put(l.toJson());
        return arr;
    }

    public static List<Lesson> listFromJson(JSONArray arr) {
        List<Lesson> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(Lesson.fromJson(o));
        }
        return out;
    }

    /** Distinct class names, sorted the same way the web app sorts them. */
    public static List<String> distinctClasses(List<Lesson> lessons) {
        List<String> names = new ArrayList<>();
        for (Lesson l : lessons) {
            if (!l.className.isEmpty() && !names.contains(l.className)) names.add(l.className);
        }
        Collections.sort(names, Lesson::compareClassNames);
        return names;
    }

    /**
     * Orders class names by grade number, then by letter using the Cyrillic
     * letter order the school uses, mirroring the web app's comparator.
     */
    public static int compareClassNames(String a, String b) {
        int[] ka = classSortKey(a);
        int[] kb = classSortKey(b);
        if (ka[0] != kb[0]) return Integer.compare(ka[0], kb[0]);
        if (ka[1] != kb[1]) return Integer.compare(ka[1], kb[1]);
        return a.compareTo(b);
    }

    private static final String LETTER_ORDER = "абвгдежзийклмнопрстуфхцчшщъыьэюя";

    /** @return {grade, letterIndex, letterCount} for sorting. */
    private static int[] classSortKey(String name) {
        String norm = name == null ? "" : name.trim().toLowerCase();
        int i = 0;
        while (i < norm.length() && Character.isDigit(norm.charAt(i))) i++;
        int grade = 999;
        if (i > 0) {
            try {
                grade = Integer.parseInt(norm.substring(0, i));
            } catch (NumberFormatException ignored) {
                grade = 999;
            }
        }
        String suffix = norm.substring(i).trim();
        int idx = suffix.isEmpty() ? 999 : LETTER_ORDER.indexOf(suffix.charAt(0));
        if (idx == -1) idx = 999;
        return new int[]{grade, idx, 0};
    }
}
