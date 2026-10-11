package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SchedulerRestrictionConsistencyDiagnosticTest {
    private static final String PKG = "com.example.diagnostic";
    private static final String SIGNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String NEW_SIGNER =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String[] OPS = {"WAKE_LOCK", "RUN_IN_BACKGROUND"};

    private static SchedulerInstalledPackageIdentity.Snapshot identity(
            int uid, String signer, long install, long update) {
        return new SchedulerInstalledPackageIdentity.Snapshot(
                PKG, uid, 0, signer, 7, install, update, true);
    }

    private static final class Id implements SchedulerInstalledPackageIdentity.Source {
        SchedulerInstalledPackageIdentity.Snapshot initial = identity(10345, SIGNER, 1000, 2000);
        SchedulerInstalledPackageIdentity.Snapshot later = initial;
        int reads;
        boolean throwOnLastRead;

        @Override public SchedulerInstalledPackageIdentity.Snapshot read(String pkg) {
            int index = ++reads;
            if (throwOnLastRead && index == 4) throw new SecurityException("reinstalled");
            return index <= 2 ? initial : later;
        }
    }

    private static final class Restrictions implements SchedulerRestrictionObservation.Source {
        int pass;
        int uid = 10345;
        boolean missingLastOp;
        boolean throwOnSecond;
        boolean changedMode;
        boolean changedWhitelist;
        boolean changedBucket;
        boolean changedSuspended;
        boolean changedEnabled;
        int reads;

        @Override public Integer uid(String pkg) {
            pass++;
            reads++;
            if (throwOnSecond && pass == 2) throw new IllegalStateException("shell lost");
            return uid;
        }
        @Override public Integer enabledSetting(String pkg) {
            reads++;
            return changedEnabled && pass == 2 ? 1 : 0;
        }
        @Override public Boolean suspended(String pkg) {
            reads++;
            return changedSuspended && pass == 2;
        }
        @Override public Integer standbyBucket(String pkg) {
            reads++;
            return changedBucket && pass == 2 ? 30 : 40;
        }
        @Override public SchedulerRestrictionObservation.WhitelistKind whitelistKind(String pkg) {
            reads++;
            return changedWhitelist && pass == 2
                    ? SchedulerRestrictionObservation.WhitelistKind.USER
                    : SchedulerRestrictionObservation.WhitelistKind.SYSTEM;
        }
        @Override public String appOpMode(String pkg, String op) {
            reads++;
            if (missingLastOp && pass == 2 && op.equals("RUN_IN_BACKGROUND")) return null;
            if (op.equals("WAKE_LOCK")) return changedMode && pass == 2 ? "default" : "allow";
            return "ignore";
        }
    }

    private static SchedulerRestrictionConsistencyDiagnostic.Result capture(Id id,
            Restrictions restrictions) {
        return SchedulerRestrictionConsistencyDiagnostic.capture(
                PKG, OPS, id, restrictions);
    }

    private static void rejects(SchedulerRestrictionConsistencyDiagnostic.Status expected,
            Id id, Restrictions restrictions) {
        SchedulerRestrictionConsistencyDiagnostic.Result r = capture(id, restrictions);
        assertEquals(expected, r.status);
        assertFalse(r.consistentDiagnosticOnly());
        assertNull("Never leak partial identity on failure", r.identity);
        assertNull("Never leak partial restrictions on failure", r.restrictions);
    }

    @Test public void stableFourIdentityReadsAndTwoCompleteRestrictionCaptures() {
        Id id = new Id();
        Restrictions restrictions = new Restrictions();
        SchedulerRestrictionConsistencyDiagnostic.Result result = capture(id, restrictions);
        assertTrue(result.consistentDiagnosticOnly());
        assertEquals(4, id.reads);
        assertEquals(2, restrictions.pass);
        assertEquals(14, restrictions.reads);
        assertEquals(10345, result.identity.uid);
        assertEquals(10345, result.restrictions.uid);
        assertEquals(SchedulerRestrictionObservation.AppOpMode.ALLOW,
                result.restrictions.appOps.get("WAKE_LOCK"));
        assertEquals(SchedulerRestrictionObservation.AppOpMode.IGNORE,
                result.restrictions.appOps.get("RUN_IN_BACKGROUND"));
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.SYSTEM,
                result.restrictions.whitelistKind);
    }

    @Test public void changedSignerAndSameSignerReinstallAfterObservationAreRejected() {
        for (SchedulerInstalledPackageIdentity.Snapshot next : new
                SchedulerInstalledPackageIdentity.Snapshot[]{
                identity(10345, NEW_SIGNER, 1000, 2000),
                identity(10345, SIGNER, 3000, 3000),
                identity(10346, SIGNER, 1000, 2000),
                identity(10345, SIGNER, 1000, 2001)}) {
            Id id = new Id();
            id.later = next;
            Restrictions restrictions = new Restrictions();
            rejects(SchedulerRestrictionConsistencyDiagnostic.Status.IDENTITY_CHANGED,
                    id, restrictions);
            assertEquals(4, id.reads);
            assertEquals(2, restrictions.pass);
        }
    }

    @Test public void unknownAndUnstableIdentityNeverReachRestrictionSource() {
        Id id = new Id();
        id.initial = identity(10345, "invalid", 1000, 2000);
        Restrictions restrictions = new Restrictions();
        rejects(SchedulerRestrictionConsistencyDiagnostic.Status.IDENTITY_UNKNOWN,
                id, restrictions);
        assertEquals(0, restrictions.reads);

        id = new Id();
        id.throwOnLastRead = true;
        rejects(SchedulerRestrictionConsistencyDiagnostic.Status.IDENTITY_UNKNOWN,
                id, new Restrictions());
    }

    @Test public void changedRestrictionFieldMustFailWithoutUsableSnapshot() {
        for (int kind = 0; kind < 6; kind++) {
            Id id = new Id();
            Restrictions observations = new Restrictions();
            switch (kind) {
                case 0: observations.changedMode = true; break;
                case 1: observations.changedWhitelist = true; break;
                case 2: observations.changedBucket = true; break;
                case 3: observations.changedSuspended = true; break;
                case 4: observations.changedEnabled = true; break;
                default: observations.uid = 10346; break;
            }
            rejects(kind == 5
                    ? SchedulerRestrictionConsistencyDiagnostic.Status.UID_MISMATCH
                    : SchedulerRestrictionConsistencyDiagnostic.Status.RESTRICTIONS_CHANGED,
                    id, observations);
        }
    }

    @Test public void missingOperationOrReadFailureIsNotImplicitAllowOrDefault() {
        Id id = new Id();
        Restrictions observations = new Restrictions();
        observations.missingLastOp = true;
        rejects(SchedulerRestrictionConsistencyDiagnostic.Status.RESTRICTIONS_UNKNOWN,
                id, observations);
        assertEquals("Second capture aborts before identity recheck", 2, id.reads);

        observations = new Restrictions();
        observations.throwOnSecond = true;
        rejects(SchedulerRestrictionConsistencyDiagnostic.Status.RESTRICTIONS_UNKNOWN,
                new Id(), observations);
    }

    @Test public void malformedPackageEmptyOpsAndDuplicateOpsDoNotReadAnySource() {
        Id id = new Id();
        Restrictions restrictions = new Restrictions();
        String[][] invalid = {
                null, {}, {"WAKE_LOCK", "WAKE_LOCK"}, {"WAKE_LOCK;inject"}
        };
        for (String[] ops : invalid) {
            SchedulerRestrictionConsistencyDiagnostic.Result result =
                    SchedulerRestrictionConsistencyDiagnostic.capture(
                            PKG, ops, id, restrictions);
            assertEquals(SchedulerRestrictionConsistencyDiagnostic.Status.INVALID_INPUT,
                    result.status);
            assertNull(result.identity);
            assertNull(result.restrictions);
        }
        assertEquals(SchedulerRestrictionConsistencyDiagnostic.Status.INVALID_INPUT,
                SchedulerRestrictionConsistencyDiagnostic.capture(
                        "com.invalid;exec", OPS, id, restrictions).status);
        assertEquals(0, id.reads);
        assertEquals(0, restrictions.reads);
    }
}
