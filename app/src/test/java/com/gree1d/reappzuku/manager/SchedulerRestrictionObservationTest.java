package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SchedulerRestrictionObservationTest {
    private static final String PKG = "com.example.restrictions";

    private static final class Fake implements SchedulerRestrictionObservation.Source {
        Integer uid = 10342;
        Integer enabled = 0;
        Boolean suspended = false;
        Integer bucket = 40;
        SchedulerRestrictionObservation.WhitelistKind whitelist =
                SchedulerRestrictionObservation.WhitelistKind.NONE;
        Map<String, String> modes = new HashMap<>();
        int reads;

        @Override public Integer uid(String pkg) { reads++; return uid; }
        @Override public Integer enabledSetting(String pkg) { reads++; return enabled; }
        @Override public Boolean suspended(String pkg) { reads++; return suspended; }
        @Override public Integer standbyBucket(String pkg) { reads++; return bucket; }
        @Override public SchedulerRestrictionObservation.WhitelistKind whitelistKind(String pkg) {
            reads++; return whitelist;
        }
        @Override public String appOpMode(String pkg, String op) {
            reads++; return modes.get(op);
        }
    }

    private static SchedulerRestrictionObservation.Result capture(Fake fake, String... ops) {
        return SchedulerRestrictionObservation.capture(PKG, ops, fake);
    }

    @Test public void completeSnapshotPreservesRawDistinctModesAndDeviceIdleOrigin() {
        Fake fake = new Fake();
        fake.enabled = 0; // DEFAULT must not collapse into explicitly enabled.
        fake.whitelist = SchedulerRestrictionObservation.WhitelistKind.SYSTEM;
        fake.modes.put("RUN_IN_BACKGROUND", "allow");
        fake.modes.put("WAKE_LOCK", "default");
        fake.modes.put("GET_USAGE_STATS", "ignore");
        SchedulerRestrictionObservation.Result result = capture(fake,
                "RUN_IN_BACKGROUND", "WAKE_LOCK", "GET_USAGE_STATS");
        assertTrue(result.complete());
        assertEquals(0, result.snapshot.enabledSetting);
        assertEquals(40, result.snapshot.standbyBucket);
        assertEquals(10342, result.snapshot.uid);
        assertFalse(result.snapshot.suspended);
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.SYSTEM,
                result.snapshot.whitelistKind);
        assertEquals(SchedulerRestrictionObservation.AppOpMode.ALLOW,
                result.snapshot.appOps.get("RUN_IN_BACKGROUND"));
        assertEquals(SchedulerRestrictionObservation.AppOpMode.DEFAULT,
                result.snapshot.appOps.get("WAKE_LOCK"));
        assertEquals(SchedulerRestrictionObservation.AppOpMode.IGNORE,
                result.snapshot.appOps.get("GET_USAGE_STATS"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.snapshot.appOps.put(
                        "RUN_IN_BACKGROUND", SchedulerRestrictionObservation.AppOpMode.IGNORE));
        fake.modes.put("RUN_IN_BACKGROUND", "ignore");
        assertEquals(SchedulerRestrictionObservation.AppOpMode.ALLOW,
                result.snapshot.appOps.get("RUN_IN_BACKGROUND"));
    }

    @Test public void ambiguousOrAbsentIndividualReadReturnsNoPartialRecord() {
        Fake fake = new Fake();
        fake.modes.put("RUN_IN_BACKGROUND", "ignore");
        assertEquals(SchedulerRestrictionObservation.Status.UNREADABLE,
                capture(fake, "RUN_IN_BACKGROUND", "WAKE_LOCK").status);
        assertNull(capture(fake, "RUN_IN_BACKGROUND", "WAKE_LOCK").snapshot);
        fake.bucket = null;
        assertEquals(SchedulerRestrictionObservation.Status.UNREADABLE,
                capture(fake, "RUN_IN_BACKGROUND").status);
        fake.bucket = 40;
        fake.uid = null;
        assertEquals(SchedulerRestrictionObservation.Status.UNREADABLE,
                capture(fake, "RUN_IN_BACKGROUND").status);
    }

    @Test public void unsupportedRawValuesAreNotTruncatedToCurrentJournalSchema() {
        Fake fake = new Fake();
        fake.modes.put("RUN_IN_BACKGROUND", "allow ");
        assertEquals(SchedulerRestrictionObservation.Status.UNSUPPORTED_VALUE,
                capture(fake, "RUN_IN_BACKGROUND").status);
        fake.modes.put("RUN_IN_BACKGROUND", "foreground");
        assertEquals(SchedulerRestrictionObservation.AppOpMode.FOREGROUND,
                capture(fake, "RUN_IN_BACKGROUND").snapshot.appOps.get("RUN_IN_BACKGROUND"));
        fake.bucket = 77;
        assertEquals(SchedulerRestrictionObservation.Status.UNSUPPORTED_VALUE,
                capture(fake, "RUN_IN_BACKGROUND").status);
        fake.bucket = 40;
        fake.enabled = 10;
        assertEquals(SchedulerRestrictionObservation.Status.UNSUPPORTED_VALUE,
                capture(fake, "RUN_IN_BACKGROUND").status);
    }

    @Test public void malformedPackageOrOperatorListNeverQueriesSource() {
        Fake fake = new Fake();
        for (String invalid : Arrays.asList("x", "com.example;echo attack", "abc def", null)) {
            assertEquals(SchedulerRestrictionObservation.Status.INVALID_INPUT,
                    SchedulerRestrictionObservation.capture(
                            invalid, new String[]{"RUN_IN_BACKGROUND"}, fake).status);
        }
        assertEquals(SchedulerRestrictionObservation.Status.INVALID_INPUT,
                capture(fake, "RUN_IN_BACKGROUND", "RUN_IN_BACKGROUND").status);
        assertEquals(SchedulerRestrictionObservation.Status.INVALID_INPUT,
                capture(fake, "OP;FALSE").status);
        assertEquals(0, fake.reads);
    }

    @Test public void exceptionsAndUnreadyDataCannotCreateSuccessEvidence() {
        SchedulerRestrictionObservation.Source throwing =
                new SchedulerRestrictionObservation.Source() {
                    @Override public Integer uid(String p) { throw new SecurityException(); }
                    @Override public Integer enabledSetting(String p) { return 1; }
                    @Override public Boolean suspended(String p) { return false; }
                    @Override public Integer standbyBucket(String p) { return 10; }
                    @Override public SchedulerRestrictionObservation.WhitelistKind whitelistKind(String p) {
                        return SchedulerRestrictionObservation.WhitelistKind.NONE;
                    }
                    @Override public String appOpMode(String p, String op) { return "allow"; }
                };
        SchedulerRestrictionObservation.Result result =
                SchedulerRestrictionObservation.capture(
                        PKG, new String[]{"RUN_IN_BACKGROUND"}, throwing);
        assertEquals(SchedulerRestrictionObservation.Status.UNREADABLE, result.status);
        assertNull(result.snapshot);
        assertEquals(SchedulerRestrictionObservation.Status.BACKEND_NOT_READY,
                SchedulerRestrictionObservation.notReady().status);
    }

    @Test public void androidAppOpsOutputNeedsExactlyOneExplicitMode() {
        assertEquals("ignore", SchedulerRestrictionAndroidReader.parseAppOp(
                "RUN_IN_BACKGROUND: ignore; time=+12m", "RUN_IN_BACKGROUND"));
        assertNull("Unknown UID-prefixed shell output must not masquerade as a package mode",
                SchedulerRestrictionAndroidReader.parseAppOp(
                        "Uid mode: allow\nRUN_IN_BACKGROUND: allow", "RUN_IN_BACKGROUND"));
        assertNull("A real UID override must block lossy package-only capture",
                SchedulerRestrictionAndroidReader.parseAppOp(
                        "Uid mode: RUN_IN_BACKGROUND: ignore\nRUN_IN_BACKGROUND: allow",
                        "RUN_IN_BACKGROUND"));
        assertNull(SchedulerRestrictionAndroidReader.parseAppOp(
                "No operations.", "RUN_IN_BACKGROUND"));
        assertNull(SchedulerRestrictionAndroidReader.parseAppOp(
                "RUN_IN_BACKGROUND: allow\nRUN_IN_BACKGROUND: ignore", "RUN_IN_BACKGROUND"));
        assertNull(SchedulerRestrictionAndroidReader.parseAppOp(
                "RUN_IN_BACKGROUND: ALLOW", "RUN_IN_BACKGROUND"));
        assertNull(SchedulerRestrictionAndroidReader.parseAppOp(
                "RUN_IN_BACKGROUND: allow; time=now", "WAKE_LOCK"));
        assertNull(SchedulerRestrictionAndroidReader.parseAppOp(
                "RUN_IN_BACKGROUND: default", "RUN_IN_BACKGROUND;date"));
    }

    @Test public void deviceIdleExceptIdleIsASeparateUnsupportedTargetScope() {
        String inventory = "system-excidle,com.android.backup,1000\n"
                + "system,com.android.phone,1001\n"
                + "user,com.example.restrictions,10342";
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.USER,
                SchedulerRestrictionAndroidReader.parseWhitelist(inventory, PKG));
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.NONE,
                SchedulerRestrictionAndroidReader.parseWhitelist(inventory, "com.other.app"));
        assertNull("Except-idle target provenance cannot fit WhitelistKind",
                SchedulerRestrictionAndroidReader.parseWhitelist(inventory, "com.android.backup"));
        assertNull("Both system and except-idle is not representable either",
                SchedulerRestrictionAndroidReader.parseWhitelist(
                        "system,com.android.backup,1000\n"
                                + "system-excidle,com.android.backup,1000", "com.android.backup"));
    }

    @Test public void deviceIdleUnknownLinesAndContradictionsFailClosed() {
        String[] invalid = {
                "system,com.android.phone,1001\nWarning: incomplete listing",
                "Permission denied\nsystem,com.android.phone,1001",
                "system,com.android.phone,1001\nunsupported,com.other.app,10342",
                "system,com.android.phone,1001\n",
                "system,com.android.phone,1001\n\nuser,com.example.restrictions,10342",
                "system,com.android.phone,1001\nsystem,com.android.phone,1001",
                "system,com.android.phone,1001\nuser,com.android.phone,1002",
                "user,com.example.restrictions,10342\nuser,com.example.restrictions,10343",
                "system-excidle,com.android.backup,999999999999999999999",
                "system,com.android.phone,0",
                "system,com.android.phone,-1",
                "system,com.android.phone,1001;echo",
                " system,com.android.phone,1001",
                "system,com.android.phone,1001\nNO SUCH COMMAND"
        };
        for (String raw : invalid) {
            if (raw.equals("system,com.android.phone,1001\n")) {
                // A single trailing platform newline is valid formatting.
                assertEquals(SchedulerRestrictionObservation.WhitelistKind.NONE,
                        SchedulerRestrictionAndroidReader.parseWhitelist(raw, PKG));
            } else {
                assertNull(raw, SchedulerRestrictionAndroidReader.parseWhitelist(raw, PKG));
            }
        }
    }

    @Test public void deviceIdleInventoryCannotInventMembershipOrAbsence() {
        String listing = "system,com.android.phone,1001\n"
                + "user,com.example.restrictions,10342\n"
                + "system,com.example.restrictions,10342";
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.BOTH,
                SchedulerRestrictionAndroidReader.parseWhitelist(listing, PKG));
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.NONE,
                SchedulerRestrictionAndroidReader.parseWhitelist(listing, "com.other.app"));
        assertNull(SchedulerRestrictionAndroidReader.parseWhitelist("", PKG));
        assertNull(SchedulerRestrictionAndroidReader.parseWhitelist("whitelist OK", PKG));
        assertNull(SchedulerRestrictionAndroidReader.parseWhitelist(
                "user,com.example.restrictions,not-a-uid", PKG));
        assertNull(SchedulerRestrictionAndroidReader.parseWhitelist(
                "user,com.example.restrictions,10342", "invalid;pkg"));
    }
}