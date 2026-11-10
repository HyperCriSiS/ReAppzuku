package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DailyUsageMathTest {
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(BERLIN);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void dayKeyIsTheLocalCalendarDay() {
        assertEquals(20261110, DailyUsageMath.dayKey(at(2026, 11, 10, 0, 0), BERLIN));
        assertEquals(20261110, DailyUsageMath.dayKey(at(2026, 11, 10, 23, 59), BERLIN));
        // The same instant is already the next day further east.
        assertEquals(20261111, DailyUsageMath.dayKey(
                at(2026, 11, 10, 23, 59), TimeZone.getTimeZone("Asia/Tokyo")));
    }

    @Test
    public void retentionCutoffCountsCalendarDaysAcrossMonthYearAndDaylightSaving() {
        assertEquals(20261231, DailyUsageMath.dayKey(at(2027, 1, 1, 12, 0), BERLIN, 1));
        assertEquals(20260228, DailyUsageMath.dayKey(at(2026, 3, 1, 12, 0), BERLIN, 1));
        // 2026-03-29 has 23 hours in Berlin; 00:30 the day after minus one day is still the 29th.
        assertEquals(20260329, DailyUsageMath.dayKey(at(2026, 3, 30, 0, 30), BERLIN, 1));
        assertEquals(20260812, DailyUsageMath.dayKey(
                at(2026, 11, 10, 12, 0), BERLIN, DailyUsageMath.RETENTION_DAYS));
    }

    @Test
    public void normalIntervalBooksTheIncrease() {
        assertEquals(5.0, DailyUsageMath.usageDelta(10.0, 15.0, false, 100.0), 0.0);
        assertEquals(0.0, DailyUsageMath.usageDelta(10.0, 10.0, false, 100.0), 0.0);
    }

    @Test
    public void singleAppDecreaseWithoutBatchResetBooksNothing() {
        // Shared UID shares shift when a sibling app appears; that is not usage.
        assertEquals(0.0, DailyUsageMath.usageDelta(10.0, 9.5, false, 100.0), 0.0);
    }

    @Test
    public void afterABatchResetTheCurrentValueIsWhatWasUsedSince() {
        assertTrue(DailyUsageMath.wasReset(500.0, 20.0));
        assertFalse(DailyUsageMath.wasReset(500.0, 500.0));
        assertEquals(3.0, DailyUsageMath.usageDelta(10.0, 3.0, true, 20.0), 0.0);
        assertEquals(3.0, DailyUsageMath.usageDelta(null, 3.0, true, 20.0), 0.0);
    }

    @Test
    public void appWithoutPreviousRowIsBookedUpToWhatTheDeviceUsed() {
        assertEquals(4.0, DailyUsageMath.usageDelta(null, 4.0, false, 6.0), 0.0);
        // A whole since-charge value cannot have been used in one interval.
        assertEquals(6.0, DailyUsageMath.usageDelta(null, 900.0, false, 6.0), 0.0);
        assertEquals(0.0, DailyUsageMath.usageDelta(null, 900.0, false, -1.0), 0.0);
    }

    @Test
    public void onlyAForwardGapOfAtMostADayIsBooked() {
        long now = at(2026, 11, 10, 12, 0);
        assertTrue(DailyUsageMath.isBookableGap(now - 15 * 60_000L, now));
        assertTrue(DailyUsageMath.isBookableGap(now - 24L * 60 * 60_000L, now));
        assertFalse(DailyUsageMath.isBookableGap(now - 25L * 60 * 60_000L, now));
        // The clock was set back: the previous batch lies in the future.
        assertFalse(DailyUsageMath.isBookableGap(now + 60_000L, now));
        assertFalse(DailyUsageMath.isBookableGap(now, now));
    }

    @Test
    public void onlyRealLevelDropsCountAsDrain() {
        assertEquals(4.0, DailyUsageMath.levelDrop(80, 76), 0.0);
        assertEquals(0.0, DailyUsageMath.levelDrop(76, 80), 0.0);
        assertEquals(0.0, DailyUsageMath.levelDrop(0, 50), 0.0);
        assertEquals(0.0, DailyUsageMath.levelDrop(50, 0), 0.0);
    }

    /**
     * Room validates a migrated database against the exported schema. This
     * compares the SQL of the migration with that schema on the JVM, where
     * the instrumented migration test cannot run.
     */
    @Test
    public void migration13To14CreatesExactlyTheExportedTables() throws Exception {
        String schema = read("app/schemas/com.gree1d.reappzuku.db.AppDatabase/14.json");
        String source = read("app/src/main/java/com/gree1d/reappzuku/db/AppDatabase.java");
        int start = source.indexOf("MIGRATION_13_14 = new Migration(13, 14)");
        String body = source.substring(start, source.indexOf("\n    };", start));

        java.util.Set<String> statements = new java.util.HashSet<>();
        for (String call : body.split("db\\.execSQL\\(")) {
            StringBuilder sql = new StringBuilder();
            Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(call);
            while (literal.find()) sql.append(literal.group(1));
            if (sql.length() > 0) statements.add(sql.toString());
        }

        // Every table and index that version 14 has and version 13 lacks,
        // exactly as Room creates them on a fresh install.
        String previousSchema = read("app/schemas/com.gree1d.reappzuku.db.AppDatabase/13.json");
        java.util.Set<String> expected = new java.util.HashSet<>();
        String[] entities = schema.split("\"tableName\": \"");
        for (int i = 1; i < entities.length; i++) {
            String table = entities[i].substring(0, entities[i].indexOf('"'));
            if (previousSchema.contains("\"tableName\": \"" + table + "\"")) continue;
            Matcher create = Pattern.compile("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"")
                    .matcher(entities[i]);
            while (create.find()) expected.add(create.group(1).replace("${TABLE_NAME}", table));
        }
        assertEquals(3, expected.size());
        assertEquals(expected, statements);
        assertTrue(schema.contains("\"version\": 14"));
        assertTrue(source.contains("MIGRATION_12_13,\n                        MIGRATION_13_14"));
        assertTrue(source.contains("version = 14,"));
    }

    @Test
    public void historyFailureCannotStopSnapshotsOrPruning() throws Exception {
        String source = read("app/src/main/java/com/gree1d/reappzuku/manager/CollectStatsManager.java");
        int insert = source.indexOf("getDao().insertAll(snapshots);");
        int history = source.indexOf("accumulateDailyUsage(previousBatch, snapshots");
        int guard = source.indexOf("} catch (RuntimeException e) {", history);
        int prune = source.indexOf("getDao().deleteOlderThan(now - 24 * 3600_000L);");
        assertTrue(insert >= 0 && insert < history && history < guard && guard < prune);
        // The previous batch is read before the new one is written.
        assertTrue(source.indexOf(
                "ResourceSnapshot previousBatch = getDao().getLastInsertedSnapshot();") < insert);
        // Batches from different parsers are never compared.
        assertTrue(source.contains(
                "if (previousBatch.totalRawPwiBatch <= 0 || currentRawTotal <= 0) return;"));
        String killer = read("app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        // A delayed relaunch check must not crash when the service is already gone.
        assertTrue(killer.contains("catch (java.util.concurrent.RejectedExecutionException e)"));
        assertEquals(-1, killer.indexOf("postDelayed(() -> executor.execute("));
        assertTrue(killer.contains(".lastOpenedByPackage(context, System.currentTimeMillis());"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
