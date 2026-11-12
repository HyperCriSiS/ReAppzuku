package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

public class VisiblePackagesParserTest {
    // Shape of dumpsys activity activities on API 36.
    private static final String DUMP = ""
            + "  * Task{d9e #844 type=home U=0 visible=true sz=1}\n"
            + "      topResumedActivity=ActivityRecord{2567 u0 com.launcher/.Home t844}\n"
            + "      * Hist  #0: ActivityRecord{2567 u0 com.launcher/.Home t844}\n"
            + "        state=RESUMED delayedResume=false finishing=false\n"
            + "        nowVisible=true lastVisibleTime=-276ms\n"
            + "  * Task{b30 #857 type=standard A=10498:com.recent.app U=0 visible=false sz=1}\n"
            + "    * Hist  #0: ActivityRecord{1824 u0 com.recent.app/.Main t857}\n"
            + "      state=STOPPED delayedResume=false finishing=false\n"
            + "      nowVisible=false lastVisibleTime=-1m29s\n"
            + "  * Task{c11 #858 type=standard A=10500:com.pip.app U=0 visible=true sz=1}\n"
            + "    * Hist  #0: ActivityRecord{1999 u0 com.pip.app/.Player t858}\n"
            + "      state=PAUSED delayedResume=false finishing=false\n";

    @Test
    public void onlyAppsOnScreenAreVisibleNotAppsInRecents() {
        assertEquals(new HashSet<>(Arrays.asList("com.launcher", "com.pip.app")),
                SmartLifecycleTextParser.parseVisiblePackages(DUMP));
    }

    @Test
    public void aDumpWithoutHistoryEntriesIsUnknown() {
        assertNull(SmartLifecycleTextParser.parseVisiblePackages("ACTIVITY MANAGER ACTIVITIES\n"));
        assertNull(SmartLifecycleTextParser.parseVisiblePackages(null));
    }

    @Test
    public void thePassSparesVisibleAppsAndFallsBackForUnknownDumps() throws Exception {
        String root = java.nio.file.Files.isDirectory(java.nio.file.Paths.get("app")) ? "" : "../";
        String killer = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(root
                + "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java")), "UTF-8");
        assertTrue(killer.contains("SmartLifecycleTextParser.parseVisiblePackages(activityDump[0])"));
        assertTrue(killer.contains(": containsPackage(activityDump[0], pkg)"));
    }
}
