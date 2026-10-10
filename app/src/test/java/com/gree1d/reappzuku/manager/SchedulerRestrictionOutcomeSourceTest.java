package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class SchedulerRestrictionOutcomeSourceTest {
    private static String source() throws IOException {
        String path = "app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java";
        for (Path item : Arrays.asList(Paths.get(path),
                Paths.get("..", path), Paths.get("..", "..", path))) {
            if (Files.isRegularFile(item)) return Files.readString(item);
        }
        throw new IOException("Missing production source: " + path);
    }

    private static String between(String src, String start, String end) {
        int i = src.indexOf(start);
        int j = src.indexOf(end, i + start.length());
        assertTrue(i >= 0 && j > i);
        return src.substring(i, j);
    }

    @Test public void schedulerChecksAllReturnedSteps() throws IOException {
        String src = source();
        String lift = between(src, "public String liftRestrictionsForScheduler(", "public String restoreRestrictionsForScheduler(");
        String restore = between(src, "public String restoreRestrictionsForScheduler(", "private Boolean isInBatteryWhitelist(");
        assertTrue(lift.contains("boolean bucketSucceeded = resetBucket(packageName);"));
        assertTrue(lift.contains("boolean whitelistSucceeded = restoreBatteryWhitelist(packageName);"));
        assertTrue(lift.contains("SchedulerRestrictionOutcomePolicy.fromCounts("));
        assertTrue(restore.contains("bucketSucceeded = applyBucket("));
        assertTrue(restore.contains("whitelistSucceeded = applyBatteryWhitelistRemoval("));
        assertTrue(restore.contains("SchedulerRestrictionOutcomePolicy.fromCounts("));
    }

    @Test public void failedPrivilegedAddCannotClearMarker() throws IOException {
        String body = between(source(), "private boolean restoreBatteryWhitelist(",
                "public void ensureBatteryWhitelistRestriction(");
        int check = body.indexOf("if (!result.succeeded()) return false;");
        int clear = body.indexOf("removed.remove(packageName);");
        assertTrue(check >= 0 && clear > check);
        assertTrue(body.indexOf("saveBatteryWhitelistRemoved(removed);") > clear);
    }

    @Test public void missingReadCannotMasqueradeAsAbsentWhitelist() throws IOException {
        String src = source();
        String read = between(src, "private Boolean isInBatteryWhitelist(",
                "private boolean applyBatteryWhitelistRemoval(");
        String remove = between(src, "private boolean applyBatteryWhitelistRemoval(",
                "private boolean restoreBatteryWhitelist(");
        assertTrue(read.contains("if (output == null || output.trim().isEmpty()) return null;"));
        assertTrue(remove.contains("if (whitelisted == null) return false;"));
        assertTrue(remove.contains("if (!result.succeeded()) return false;"));
    }
}
