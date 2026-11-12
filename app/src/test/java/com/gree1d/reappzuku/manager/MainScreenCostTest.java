package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** What the open main screen costs while nothing changes, measured on a device with 140 running apps. */
public class MainScreenCostTest {

    @Test
    public void theRamBarOnlyMovesForAVisibleChange() {
        // The first value is always shown.
        assertTrue(RamMonitor.barNeedsUpdate(-1, 6000, 15222));
        // Half a percent of 15222 MB is 76.11 MB.
        assertFalse(RamMonitor.barNeedsUpdate(6000, 6076, 15222));
        assertTrue(RamMonitor.barNeedsUpdate(6000, 6077, 15222));
        assertFalse(RamMonitor.barNeedsUpdate(6000, 5924, 15222));
        assertTrue(RamMonitor.barNeedsUpdate(6000, 5923, 15222));
        assertFalse(RamMonitor.barNeedsUpdate(6000, 6000, 15222));
    }

    @Test
    public void theBarColorIsOnlySetWhenItsBandChanges() throws Exception {
        assertEquals(0, RamMonitor.colorBand(59, 100));
        assertEquals(1, RamMonitor.colorBand(60, 100));
        assertEquals(1, RamMonitor.colorBand(84, 100));
        assertEquals(2, RamMonitor.colorBand(85, 100));
        String monitor = read("app/src/main/java/com/gree1d/reappzuku/manager/RamMonitor.java");
        assertTrue(monitor.contains("if (band != shownBand) {"));
        // An accent color set from outside is not kept from a later band change.
        int accent = monitor.indexOf("ramUsageBar.setIndicatorColor(baseColor);");
        assertTrue(accent >= 0 && monitor.indexOf("shownBand = -1;", accent) > accent);
        // Only the first fill is animated.
        assertTrue(monitor.contains("ramUsageBar.setProgress((int) ramInfo.usedMb, shownUsedMb < 0);"));
    }

    @Test
    public void theCpuPollStartsNoProcessPerApp() throws Exception {
        String monitor = read("app/src/main/java/com/gree1d/reappzuku/manager/CpuMonitor.java");
        assertTrue(monitor.contains("cmd.append(\"; echo ---; read -r l < /proc/\").append(pid)"));
        assertFalse(monitor.contains("echo ---; cat /proc/\").append(pid)"));
    }

    @Test
    public void aCpuPollTouchesOnlyRowsWhoseTextChanged() throws Exception {
        String adapter = read(
                "app/src/main/java/com/gree1d/reappzuku/ui/BackgroundAppsRecyclerViewAdapter.java");
        int start = adapter.indexOf("    public void updateCpu(List<AppModel> list) {");
        String update = adapter.substring(start, adapter.indexOf("\n    }\n", start));
        // A full rebind only when the apps or their order changed.
        int full = update.indexOf("notifyDataSetChanged();");
        assertTrue(update.indexOf("if (!items.equals(list)) {") < full);
        assertTrue(full < update.indexOf("return;"));
        assertEquals(full, update.lastIndexOf("notifyDataSetChanged();"));
        assertTrue(update.contains("if (!java.util.Objects.equals(cpu, shownCpu.get(i))) {"));
        // No change notification: it would run the item animator on every poll.
        assertFalse(update.contains("notifyItemChanged"));
        assertFalse(update.contains("notifyItemRangeChanged"));
        // A full list keeps the remembered texts in step with the rows.
        int submit = adapter.indexOf("    public void submitList(List<AppModel> list) {");
        assertTrue(adapter.substring(submit, adapter.indexOf("\n    }\n", submit)).contains("rememberCpu();"));
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
