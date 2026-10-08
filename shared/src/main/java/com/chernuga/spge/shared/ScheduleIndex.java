package com.chernuga.spge.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable, pre-indexed view over a set of lessons.
 *
 * <p>Filtering the raw lesson list on every recomposition is the main cost in
 * the watch UI: a full-school timetable runs to hundreds of entries, and the
 * filter type, the selected name and the week all change independently. This
 * class does the grouping once — when new data arrives — so a filter change is
 * a single map lookup instead of a scan.
 */
public final class ScheduleIndex {

    /** Filter dimensions, mirroring the web app's view types. */
    public enum Type { CLASS, TEACHER, ROOM }

    private final List<Lesson> all;

    /** Type → (name → lessons for that name), each list pre-sorted. */
    private final Map<Type, Map<String, List<Lesson>>> byType = new HashMap<>();
    /** Type → sorted distinct names. */
    private final Map<Type, List<String>> namesByType = new HashMap<>();

    public ScheduleIndex(List<Lesson> lessons) {
        this.all = Collections.unmodifiableList(new ArrayList<>(lessons));
        for (Type t : Type.values()) {
            byType.put(t, new HashMap<String, List<Lesson>>());
            namesByType.put(t, new ArrayList<String>());
        }
        build();
    }

    private void build() {
        for (Type t : Type.values()) {
            Map<String, List<Lesson>> buckets = byType.get(t);
            Set<String> names = new LinkedHashSet<>();

            for (Lesson l : all) {
                String key = keyFor(t, l);
                if (key == null || key.isEmpty()) continue;
                List<Lesson> bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    buckets.put(key, bucket);
                }
                bucket.add(l);
                names.add(key);
            }

            // Sort each bucket once so rendering never sorts.
            for (List<Lesson> bucket : buckets.values()) {
                Collections.sort(bucket, (a, b) -> {
                    if (a.day != b.day) return Integer.compare(a.day, b.day);
                    if (a.start != b.start) return Integer.compare(a.start, b.start);
                    return a.subject.compareTo(b.subject);
                });
            }

            List<String> sorted = new ArrayList<>(names);
            if (t == Type.CLASS) {
                Collections.sort(sorted, Lesson::compareClassNames);
            } else {
                Collections.sort(sorted);
            }
            namesByType.put(t, Collections.unmodifiableList(sorted));
        }
    }

    private static String keyFor(Type t, Lesson l) {
        switch (t) {
            case CLASS: return l.className;
            case TEACHER: return l.teacher;
            case ROOM: return l.room;
            default: return null;
        }
    }

    public List<Lesson> all() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public int size() {
        return all.size();
    }

    /** Distinct names for a dimension, already in display order. */
    public List<String> names(Type type) {
        List<String> n = namesByType.get(type);
        return n == null ? Collections.<String>emptyList() : n;
    }

    /** Lessons for one selection, already ordered. Never null. */
    public List<Lesson> lessons(Type type, String name) {
        if (name == null) return Collections.emptyList();
        Map<String, List<Lesson>> buckets = byType.get(type);
        if (buckets == null) return Collections.emptyList();
        List<Lesson> bucket = buckets.get(name);
        return bucket == null ? Collections.<Lesson>emptyList() : bucket;
    }

    /** True when the name still exists in this index (e.g. after a refresh). */
    public boolean has(Type type, String name) {
        if (name == null) return false;
        Map<String, List<Lesson>> buckets = byType.get(type);
        return buckets != null && buckets.containsKey(name);
    }
}
