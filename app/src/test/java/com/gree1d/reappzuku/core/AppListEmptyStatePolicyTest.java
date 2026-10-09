package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class AppListEmptyStatePolicyTest {
    @Test public void noUnverifiedEmptyClaimDuringStartupOrRefresh() {
        assertEquals(AppListEmptyStatePolicy.State.HIDDEN,
                AppListEmptyStatePolicy.resolve(false, 0, 0));
        assertEquals(AppListEmptyStatePolicy.State.HIDDEN,
                AppListEmptyStatePolicy.resolve(false, 12, 0));
    }

    @Test public void completedScanWithNoRunningAppsShowsDistinctMessage() {
        assertEquals(AppListEmptyStatePolicy.State.NO_RUNNING_APPS,
                AppListEmptyStatePolicy.resolve(true, 0, 0));
    }

    @Test public void zeroMatchesDoesNotSuggestThatNoAppsAreRunning() {
        assertEquals(AppListEmptyStatePolicy.State.NO_FILTER_RESULTS,
                AppListEmptyStatePolicy.resolve(true, 12, 0));
    }

    @Test public void populatedResultsHideEmptyMessage() {
        assertEquals(AppListEmptyStatePolicy.State.HIDDEN,
                AppListEmptyStatePolicy.resolve(true, 12, 3));
    }

    @Test public void mainActivityWiresStateOnlyAfterCompletedScan() throws Exception {
        Path root = Paths.get("");
        for (int i = 0; i < 3 && !Files.isRegularFile(root.resolve(
                "app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")); i++) {
            root = root.resolve("..");
        }
        String main = Files.readString(root.resolve(
                "app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java"));
        String layout = Files.readString(root.resolve("app/src/main/res/layout/activity_main.xml"));
        assertTrue(main.contains("hasCompletedAppLoad = false;"));
        assertTrue(main.contains("if (finished) hasCompletedAppLoad = true;"));
        assertTrue(main.contains("AppListEmptyStatePolicy.resolve("));
        assertTrue(main.contains("binding.mainListEmptyState.setVisibility(View.GONE)"));
        assertTrue(main.contains("binding.mainListEmptyState.setVisibility(View.VISIBLE)"));
        assertTrue(layout.contains("android:id=\"@+id/main_list_empty_state\""));
        assertTrue(layout.contains("android:visibility=\"gone\""));
    }
}
