package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Cases an independent review of the media and foreground service rules asked about. */
public class ProtectionEdgeCasesTest {
    private static final String APP = "net.example.radio";
    private static final String OTHER = "com.example.other";
    private static final long NOW = 70_000_000L;

    private static String session(String pkg, String stateLine) {
        return "    name " + pkg + "/name/1 (userId=0)\n"
                + "      package=" + pkg + "\n"
                + "      active=true\n"
                + "      " + stateLine + "\n";
    }

    private static String stack(String... sessions) {
        return "  Sessions Stack - have " + sessions.length + " sessions:\n" + String.join("", sessions);
    }

    @Test
    public void olderReleasesPrintTheStateAsABareNumber() {
        String playing = stack(session(APP, "state=PlaybackState {state=3, position=0, updated=69000000}"));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(playing, APP, NOW));
        String stopped = stack(session(APP, "state=PlaybackState {state=1, position=0, updated=69000000}"));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(stopped, APP, NOW));
        String pausedLongAgo = stack(session(APP, "state=PlaybackState {state=2, position=0, updated=1000}"));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(pausedLongAgo, APP, NOW));
    }

    @Test
    public void theAnswerForOneAppDoesNotDependOnTheSessionsOfOthers() {
        // A session that never published a state, alone and next to a playing one.
        String alone = stack(session(APP, "state=null"));
        String withOther = stack(session(APP, "state=null"),
                session(OTHER, "state=PlaybackState {state=PLAYING(3), position=0, updated=69000000}"));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(alone, APP, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(withOther, APP, NOW));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(withOther, OTHER, NOW));
        // No session at all is a layout that is understood: nobody plays.
        String none = "  Media button session is null\n  Sessions Stack - have 0 sessions:\n"
                + "  Audio playback (lastly played comes first)\n    uid=10175 packages=" + APP + "\n";
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(none, APP, NOW));
    }

    @Test
    public void severalSessionsOfOneAppCountIfOneOfThemPlays() {
        String dump = stack(
                session(APP, "state=PlaybackState {state=PLAYING(3), position=0, updated=69000000}"),
                session(APP, "state=PlaybackState {state=NONE(0), position=0, updated=1000}"));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump, APP, NOW));
    }

    @Test
    public void aPauseWithoutAUsableTimeCountsAsRecent() {
        // An app may put any number there; zero or a wall clock time says nothing about age.
        for (String updated : new String[] {"updated=0", "updated=-1", "updated=1794505000000",
                "updated=99999999999999999999999"}) {
            String dump = stack(session(APP, "state=PlaybackState {state=PAUSED(2), position=0, " + updated + "}"));
            assertTrue(updated, SmartLifecycleProtectionPolicy.playsMedia(dump, APP, NOW));
        }
    }

    @Test
    public void aTitleCannotPoseAsAStateOrBreakThePass() {
        // The metadata line carries text the content controls.
        String dump = stack(session(APP, "state=PlaybackState {state=STOPPED(1), position=0, updated=1000}")
                + "      metadata: size=3, description=state=PlaybackState {state=PLAYING(3)}, x\n"
                + "      error=state=PlaybackState {state=X(99999999999)}\n");
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump, APP, NOW));
        // A number too long for a state code is not a state code.
        String huge = stack(session(APP, "state=PlaybackState {state=PLAYING(99999999999), updated=1}"));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(huge, APP, NOW));
    }

    @Test
    public void anAppDoesNotBorrowTheForegroundServiceOfAnAppItCalled() {
        // Headers from an Android 16 device: after "c:" stands the app that last called the service.
        assertEquals("vendor.qti.iwlan", SmartLifecycleProtectionPolicy.ownerOf(
                "782452b u0 vendor.qti.iwlan/.QualifiedNetworksServiceImpl c:com.android.phone}"));
        assertEquals(APP, SmartLifecycleProtectionPolicy.ownerOf("6dc3974 u0 " + APP + "/.PlayerService c:" + APP + "}"));
        assertEquals(APP, SmartLifecycleProtectionPolicy.ownerOf(APP + "/.Sync}"));
        assertNull(SmartLifecycleProtectionPolicy.ownerOf("no component here}"));

        String services = "  * ServiceRecord{1 u0 " + OTHER + "/.Player c:" + APP + "}\n"
                + "    isForeground=true foregroundId=7 types=0x00000002\n";
        assertTrue(SmartLifecycleProtectionPolicy.runsForegroundService(services, OTHER));
        assertFalse(SmartLifecycleProtectionPolicy.runsForegroundService(services, APP));
    }

    @Test
    public void oneForegroundRecordIsEnoughAmongSeveral() {
        String services = "  * ServiceRecord{1 u0 " + APP + "/.Old c:" + APP + "}\n"
                + "    isForeground=false foregroundId=1 types=0x00000000\n"
                + "  * ServiceRecord{2 u0 " + APP + "/.Player c:" + APP + "}\n"
                + "    intent={cmp=" + APP + "/.Player}\n"
                + "    isForeground=true foregroundId=2 types=0x00000002\n";
        assertTrue(SmartLifecycleProtectionPolicy.runsForegroundService(services, APP));
    }
}
