package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AppPolicyListSourcePolicyTest {
    @Test
    public void mainListUsesOneBackgroundPolicySnapshotForBadgesAndFilters() throws Exception {
        String main = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java");

        assertTrue(main.contains("AppPolicyListSnapshot.capture(getApplicationContext())"));
        assertTrue(main.contains("executor.execute(() -> {"));
        assertTrue(main.contains("app.setPolicyListStatus(policySnapshot.resolveStatus("));
        assertTrue(main.contains("AppPolicyListState.matchesFilter("));
    }

    @Test
    public void bulkSelectionOnlyUsesFilteredVisibleRows() throws Exception {
        String main = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java");
        assertTrue(main.contains("AppListBulkSelectionPolicy.selectVisible(appsDataList);"));
        int start = main.indexOf("private void selectAll()");
        int end = main.indexOf("private void unselectAll()", start);
        assertTrue(start >= 0 && end > start);
        assertTrue(!main.substring(start, end).contains("fullAppsList"));
        // Toolbar state is already derived from actual selection and should not
        // be overwritten to "Deselect all" if visible rows were protected.
        assertTrue(!main.substring(start, end).contains("setTitle("));
    }

    @Test
    public void snapshotUsesRuntimeMigrationBoundaryAndDurableSetupQueue() throws Exception {
        String snapshot = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/AppPolicyListSnapshot.java");

        assertTrue(snapshot.contains("AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs)"));
        assertTrue(snapshot.contains("NewAppSetupStore.getPending(appContext)"));
        assertTrue(snapshot.contains("prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0"));
        assertTrue(snapshot.contains("AppPolicyListState.resolveStatus("));
    }

    @Test
    public void rowContainsDedicatedPolicyBadge() throws Exception {
        String item = readRepositoryFile("app/src/main/res/layout/item.xml");
        String adapter = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/BackgroundAppsRecyclerViewAdapter.java");

        assertTrue(item.contains("@+id/badge_policy"));
        assertTrue(adapter.contains("policy_badge_managed_smart"));
        assertTrue(adapter.contains("policy_badge_managed_immediate"));
        assertTrue(adapter.contains("policy_badge_needs_setup"));
    }

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}
