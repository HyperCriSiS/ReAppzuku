package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class StatsSinceChargeTest {

    @Test
    public void theWindowIsTheTimeOnBatterySinceTheLastCharge() {
        assertEquals(419593748L, BackgroundActivityParser.windowMs(String.join("\n",
                "9,0,i,vers,36,197,BP2A.250605.031.A3,BP2A.250605.031.A3",
                "9,0,l,bt,1,419593748,4527425,429289047,14222724,1794443706427,419065145",
                "9,10453,l,awl,1282637,1282637")));
        assertEquals(0L, BackgroundActivityParser.windowMs(null));
        assertEquals(0L, BackgroundActivityParser.windowMs("9,0,l,bt,1,x"));
        assertEquals(0L, BackgroundActivityParser.windowMs("9,10453,l,awl,1282637,1282637"));
    }

    @Test
    public void aScreenWithoutOwnSnapshotsShowsWhatTheSystemCounted() throws Exception {
        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/StatisticsActivity.java");
        // Both empty cases fall back: the service is off, or it has not collected enough yet.
        assertTrue(screen.contains(
                "loadChartsSinceCharge(getString(R.string.stats_service_inactive_hint));"));
        assertTrue(screen.contains("loadChartsSinceCharge(periodStats.dataHint);"));
        // Only when the system has nothing either does the empty card remain, with the reason.
        int none = screen.indexOf("if (!sinceCharge.hasData) {");
        assertTrue(none >= 0 && screen.indexOf("binding.tvNoDataHint.setText(ownDataHint);", none) > none);
        // The reader is told that these are not the app's own figures for the chosen period.
        assertTrue(screen.contains("R.string.stats_since_charge_notice"));

        String collector = read("app/src/main/java/com/gree1d/reappzuku/manager/CollectStatsManager.java");
        int start = collector.indexOf("    private PeriodStats getStatsSinceChargeBlocking() {");
        String method = collector.substring(start, collector.indexOf("\n    }\n", start));
        // One dump, and nothing is written: the snapshot table and the daily rows stay the app's own.
        assertEquals(1, method.split("dumpsys", -1).length - 1);
        assertTrue(!method.contains("getDao()") && !method.contains("dailyUsageDao"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
