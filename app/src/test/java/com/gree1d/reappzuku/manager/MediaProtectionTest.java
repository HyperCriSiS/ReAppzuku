package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaProtectionTest {
    private static final String RADIO = "net.example.radio";
    private static final String MUSIC = "com.example.music";

    /** "dumpsys media_session" of an Android 16 device: a radio app and an idle music app. */
    private static String dump(String radioState, long radioUpdated) {
        return String.join("\n",
                "MEDIA SESSION SERVICE (dumpsys media_session)",
                "  Media button session is " + RADIO + "/" + RADIO + "/4 (userId=0)",
                "  Sessions Stack - have 2 sessions:",
                "    " + RADIO + " " + RADIO + "/" + RADIO + "/4 (userId=0)",
                "      ownerPid=21963, ownerUid=10175, userId=0",
                "      package=" + RADIO,
                "      active=true",
                "      state=PlaybackState {state=" + radioState + ", position=-1, buffered position=0,"
                        + " speed=0.0, updated=" + radioUpdated + ", actions=3637, custom actions=[]}",
                "      audioAttrs=AudioAttributes: usage=USAGE_MEDIA",
                "    MediaSessionController " + MUSIC + "/MediaSessionController/3 (userId=0)",
                "      ownerPid=4083, ownerUid=10321, userId=0",
                "      package=" + MUSIC,
                "      active=false",
                "      state=PlaybackState {state=NONE(0), position=0, buffered position=0, speed=0.0,"
                        + " updated=5971339, actions=11264, custom actions=[]}\r");
    }

    private static final long NOW = 70_000_000L;

    @Test
    public void anAppThatPlaysIsProtected() {
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("PLAYING(3)", NOW - 9_000_000L), RADIO, NOW));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("BUFFERING(6)", NOW), RADIO, NOW));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("CONNECTING(8)", NOW), RADIO, NOW));
    }

    @Test
    public void aSessionAloneIsNotPlayback() {
        // Found on a device: the idle music app kept a session and was out of reach for good.
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump("PLAYING(3)", NOW), MUSIC, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump("STOPPED(1)", NOW), RADIO, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump("ERROR(7)", NOW), RADIO, NOW));
        // The media button line names the app as well; it is not a session.
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump("NONE(0)", NOW), RADIO, NOW));
    }

    @Test
    public void aFreshPauseIsProtectedAndAnOldOneIsNot() {
        long grace = SmartLifecycleProtectionPolicy.PAUSE_GRACE_MS;
        // A call or a spoken direction pauses playback for a moment.
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("PAUSED(2)", NOW - 30_000L), RADIO, NOW));
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("PAUSED(2)", NOW - grace + 1), RADIO, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(dump("PAUSED(2)", NOW - grace), RADIO, NOW));
        // Without a clock nothing can be called old.
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(dump("PAUSED(2)", 1L), RADIO, 0L));
    }

    @Test
    public void anUnknownLayoutProtectsEveryAppItNames() {
        // No playback state line at all: the layout is not understood, so the old rule applies.
        String unknown = "Sessions:\n  session package=" + RADIO + " active";
        assertTrue(SmartLifecycleProtectionPolicy.playsMedia(unknown, RADIO, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(unknown, MUSIC, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia(null, RADIO, NOW));
        assertFalse(SmartLifecycleProtectionPolicy.playsMedia("", RADIO, NOW));
    }

    @Test
    public void theSwitchInTheEditorStillTurnsTheProtectionOff() {
        String services = "ServiceRecord{1 u0 other/.S}\n";
        assertEquals("media session", SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                RADIO, dump("PLAYING(3)", NOW), "", services, "", "", "", true, true, true, NOW));
        assertNull(SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                RADIO, dump("PLAYING(3)", NOW), "", services, "", "", "", false, true, true, NOW));
        assertNull(SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                RADIO, dump("PAUSED(2)", NOW - 3_600_000L), "", services, "", "", "", true, true, true, NOW));
    }

    @Test
    public void thePassHandsTheTimeOfItsDumpsToThePolicy() {
        String sep = "==reappzuku-dump==";
        String services = "ServiceRecord{1 u0 other/.S}\n";
        String output = dump("PAUSED(2)", NOW - 3_600_000L) + sep + sep + services + sep + sep + sep;
        assertNull(new ProtectionDumps(output, NOW).dumpReasonFor(RADIO, null));
        assertEquals("media session", new ProtectionDumps(
                dump("PAUSED(2)", NOW - 60_000L) + sep + sep + services + sep + sep + sep, NOW)
                .dumpReasonFor(RADIO, null));
    }
    @Test
    public void aServiceThatLeftTheForegroundNoLongerProtects() {
        // From an Android 16 device: a paused player. The notification id stays in the record.
        String paused = "  * ServiceRecord{6dc3974 u0 " + RADIO + "/.service.PlayerService c:" + RADIO + "}\n"
                + "    isForeground=false foregroundId=1 types=0x00000000 foregroundNoti=Notification(channel=default)\n";
        assertFalse(SmartLifecycleProtectionPolicy.runsForegroundService(paused, RADIO));
        String playing = paused.replace("isForeground=false", "isForeground=true");
        assertTrue(SmartLifecycleProtectionPolicy.runsForegroundService(playing, RADIO));
        // A record that does not say either way is judged by the id, as before.
        String older = "  * ServiceRecord{1 u0 " + RADIO + "/.S}\n    foregroundId=42\n";
        assertTrue(SmartLifecycleProtectionPolicy.runsForegroundService(older, RADIO));
        assertFalse(SmartLifecycleProtectionPolicy.runsForegroundService(
                older.replace("foregroundId=42", "foregroundId=0"), RADIO));
    }

}
