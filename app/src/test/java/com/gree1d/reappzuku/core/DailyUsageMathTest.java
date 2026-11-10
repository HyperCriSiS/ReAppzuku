package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
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
    public void counterDeltaSurvivesAReset() {
        assertEquals(5.0, DailyUsageMath.counterDelta(10.0, 15.0), 0.0);
        assertEquals(0.0, DailyUsageMath.counterDelta(10.0, 10.0), 0.0);
        // After a reset only what was counted since then is known.
        assertEquals(3.0, DailyUsageMath.counterDelta(10.0, 3.0), 0.0);
        assertEquals(7.0, DailyUsageMath.counterDelta(0.0, 7.0), 0.0);
        assertEquals(500L, DailyUsageMath.counterDelta(1_000L, 1_500L));
        assertEquals(200L, DailyUsageMath.counterDelta(1_000L, 200L));
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

        List<String> statements = new ArrayList<>();
        for (String call : body.split("db\\.execSQL\\(")) {
            StringBuilder sql = new StringBuilder();
            Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(call);
            while (literal.find()) sql.append(literal.group(1));
            if (sql.length() > 0) statements.add(sql.toString());
        }
        assertEquals(3, statements.size());

        for (String table : new String[] {"daily_app_usage", "daily_device_usage"}) {
            int entity = schema.indexOf("\"tableName\": \"" + table + "\"");
            assertTrue(table, entity >= 0);
            Matcher create = Pattern.compile("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"")
                    .matcher(schema);
            assertTrue(create.find(entity));
            assertTrue(table, statements.contains(create.group(1).replace("${TABLE_NAME}", table)));
        }
        assertTrue(statements.contains(
                "CREATE INDEX IF NOT EXISTS `index_daily_app_usage_day` ON `daily_app_usage` (`day`)"));
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
        assertTrue(source.indexOf("ResourceSnapshot previousBatch = getDao().getLatestSnapshot();") < insert);
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
