package com.chernuga.spge.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verifies the filter index and period table against the web app's semantics.
 *
 * <p>These are host-side JVM tests: they exercise the pure logic that drives
 * the watch UI without needing the device, which matters because the watch
 * aggressively freezes background apps and cannot be relied on for automated
 * checks.
 */
public class ScheduleIndexTest {

    private static Lesson lesson(int day, int start, int end, String subject,
                                 String room, String className, String teacher) {
        return new Lesson(start, end, day, subject, room, className, teacher,
                "#3b82f6", "C", 0);
    }

    private static List<Lesson> sample() {
        List<Lesson> l = new ArrayList<>();
        l.add(lesson(0, 0, 1, "Матиматика", "4.7", "8. А", "А. Йорданова"));
        l.add(lesson(0, 2, 3, "Български", "3.1", "8. А", "И. Петрова"));
        l.add(lesson(1, 0, 2, "Физика", "5.2", "8. А", "Д. Иванов"));
        l.add(lesson(0, 0, 1, "История", "2.0", "9. Б", "Д. Иванов"));
        l.add(lesson(2, 4, 5, "Химия", "6.3", "9. Б", "М. Стоянова"));
        return l;
    }

    @Test
    public void classFilterReturnsOnlyThatClass() {
        ScheduleIndex idx = new ScheduleIndex(sample());
        List<Lesson> only8a = idx.lessons(ScheduleIndex.Type.CLASS, "8. А");

        assertEquals(3, only8a.size());
        for (Lesson l : only8a) {
            assertEquals("8. А", l.className);
        }
    }

    @Test
    public void teacherFilterSpansMultipleClasses() {
        ScheduleIndex idx = new ScheduleIndex(sample());
        List<Lesson> ivanov = idx.lessons(ScheduleIndex.Type.TEACHER, "Д. Иванов");

        // The whole point of a teacher view: it cuts across classes.
        assertEquals(2, ivanov.size());
        assertTrue(ivanov.stream().anyMatch(l -> l.className.equals("8. А")));
        assertTrue(ivanov.stream().anyMatch(l -> l.className.equals("9. Б")));
    }

    @Test
    public void roomFilterWorks() {
        ScheduleIndex idx = new ScheduleIndex(sample());
        assertEquals(1, idx.lessons(ScheduleIndex.Type.ROOM, "6.3").size());
        assertEquals(0, idx.lessons(ScheduleIndex.Type.ROOM, "no-such-room").size());
    }

    @Test
    public void resultsAreSortedByDayThenPeriod() {
        ScheduleIndex idx = new ScheduleIndex(sample());
        List<Lesson> only8a = idx.lessons(ScheduleIndex.Type.CLASS, "8. А");

        // Day 0 p0, day 0 p2, day 1 p0 — headers can therefore be emitted in a
        // single forward pass, which the UI relies on.
        assertEquals(0, only8a.get(0).day);
        assertEquals(0, only8a.get(0).start);
        assertEquals(0, only8a.get(1).day);
        assertEquals(2, only8a.get(1).start);
        assertEquals(1, only8a.get(2).day);
    }

    @Test
    public void namesAreDistinctAndSorted() {
        ScheduleIndex idx = new ScheduleIndex(sample());

        List<String> classes = idx.names(ScheduleIndex.Type.CLASS);
        // Grade order first: 8 before 9, regardless of insertion order.
        assertEquals(Arrays.asList("8. А", "9. Б"), classes);

        // Four distinct teachers across the five lessons; Д. Иванов appears
        // twice and must be listed once.
        List<String> teachers = idx.names(ScheduleIndex.Type.TEACHER);
        assertEquals(4, teachers.size());
        assertEquals(1, teachers.stream().filter(t -> t.equals("Д. Иванов")).count());

        // Distinct rooms, likewise deduplicated.
        assertEquals(5, idx.names(ScheduleIndex.Type.ROOM).size());
    }

    @Test
    public void hasDetectsStaleSelection() {
        ScheduleIndex idx = new ScheduleIndex(sample());
        assertTrue(idx.has(ScheduleIndex.Type.CLASS, "8. А"));
        assertFalse(idx.has(ScheduleIndex.Type.CLASS, "12. Ж"));
    }

    @Test
    public void emptyInputIsSafe() {
        ScheduleIndex idx = new ScheduleIndex(new ArrayList<>());
        assertTrue(idx.isEmpty());
        assertEquals(0, idx.lessons(ScheduleIndex.Type.CLASS, "anything").size());
        assertEquals(0, idx.names(ScheduleIndex.Type.TEACHER).size());
        assertFalse(idx.has(ScheduleIndex.Type.ROOM, "1.1"));
    }

    @Test
    public void periodTimesMatchTheWebApp() {
        // Values taken verbatim from the web app's constants.js.
        assertEquals("8:00", Periods.start(0));
        assertEquals("8:45", Periods.start(1));
        assertEquals("9:50", Periods.start(2));
        assertEquals("10:35", Periods.start(3));
        assertEquals("11:40", Periods.start(4));
        assertEquals("12:25", Periods.start(5));
        assertEquals("13:30", Periods.start(6));
        assertEquals("14:15", Periods.start(7));

        assertEquals("8:45", Periods.end(1));
        assertEquals("9:30", Periods.end(2));
        assertEquals("11:20", Periods.end(4));
        assertEquals("13:10", Periods.end(6));
        assertEquals("15:00", Periods.end(8));

        assertEquals("8:45 - 9:30", Periods.range(1, 2));
    }

    @Test
    public void periodMinutesParseForNowChecks() {
        // Regression guard: a lesson running 9:50-10:35 must be detectable.
        assertEquals(9 * 60 + 50, Periods.toMinutes("9:50"));
        assertEquals(10 * 60 + 35, Periods.toMinutes("10:35"));
        assertEquals(8 * 60, Periods.toMinutes("8:00"));
        assertEquals(-1, Periods.toMinutes("garbage"));
        assertEquals(-1, Periods.toMinutes(null));
    }

    @Test
    public void parseReportRoundTrips() {
        String text = "Start: 0\nEnd: 1\nDay: 0\nSubject: Математика\nRoom: 4.7\n"
                + "Class: 8. А\nTeacher: А. Йорданова\nColor: #3b82f6\n"
                + "Type: 1\nWeek: C\nGroup: 0";
        List<Lesson> parsed = Lesson.parseReport(text);

        assertEquals(1, parsed.size());
        Lesson l = parsed.get(0);
        assertEquals("Математика", l.subject);
        assertEquals("8. А", l.className);
        assertEquals("4.7", l.room);
        assertEquals(0, l.start);
        assertEquals(1, l.end);
    }

    @Test
    public void parseReportSkipsIncompleteBlocks() {
        // No teacher: the web parser rejects this block, and so must we.
        String text = "Start: 0\nEnd: 1\nDay: 0\nSubject: X\nClass: 8. А";
        assertEquals(0, Lesson.parseReport(text).size());
    }
}
