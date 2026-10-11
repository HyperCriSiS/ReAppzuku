package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SchedulerAppOpsDualScopeDiagnosticTest {
    private static final String PKG = "com.example.appopsdiagnostic";
    private static final String OP = "WAKE_LOCK";
    private static final String SIGNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static SchedulerInstalledPackageIdentity.Snapshot row(
            int uid, String signer, long install, long update) {
        return new SchedulerInstalledPackageIdentity.Snapshot(
                PKG, uid, 0, signer, 7, install, update, true);
    }

    private static final class Identity implements SchedulerInstalledPackageIdentity.Source {
        SchedulerInstalledPackageIdentity.Snapshot initial = row(10345, SIGNER, 1000, 2000);
        SchedulerInstalledPackageIdentity.Snapshot last = initial;
        int reads;

        @Override public SchedulerInstalledPackageIdentity.Snapshot read(String packageName) {
            return ++reads <= 2 ? initial : last;
        }
    }

    private static final class Shell implements SchedulerAppOpsDualScopeDiagnostic.Source {
        String pkg = "WAKE_LOCK: allow";
        String uid = "Uid mode: WAKE_LOCK: ignore";
        String changedPkg;
        String changedUid;
        int pkgReads;
        int uidReads;
        boolean throwOnUid;

        @Override public String readPackage(String packageName, String op) {
            if (++pkgReads == 2 && changedPkg != null) return changedPkg;
            return pkg;
        }
        @Override public String readUid(int uidValue, String op) {
            assertEquals(10345, uidValue);
            if (throwOnUid) throw new SecurityException("Shell unavailable");
            if (++uidReads == 2 && changedUid != null) return changedUid;
            return uid;
        }
    }

    private static SchedulerAppOpsDualScopeDiagnostic.Result capture(Identity id, Shell shell) {
        return SchedulerAppOpsDualScopeDiagnostic.capture(PKG, OP, id, shell);
    }

    private static void rejects(SchedulerAppOpsDualScopeDiagnostic.Status expected,
            Identity id, Shell shell) {
        SchedulerAppOpsDualScopeDiagnostic.Result result = capture(id, shell);
        assertEquals(expected, result.status);
        assertFalse(result.consistentDiagnosticOnly());
        assertFalse(result.authorizesRestore());
        assertNull("No partial installed identity must escape a failure", result.identity);
        assertNull("No partial package mode must escape a failure", result.packageLayer);
        assertNull("No partial UID mode must escape a failure", result.uidLayer);
    }

    @Test public void distinctPackageAndUidRowsRemainLosslessAndNeverAuthorizeRestore() {
        Identity id = new Identity();
        Shell shell = new Shell();
        SchedulerAppOpsDualScopeDiagnostic.Result r = capture(id, shell);
        assertTrue(r.consistentDiagnosticOnly());
        assertFalse(r.authorizesRestore());
        assertNotNull(r.identity);
        assertEquals(10345, r.identity.uid);
        assertEquals(SIGNER, r.identity.currentSignerSha256);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT,
                r.packageLayer.presence);
        assertEquals("allow", r.packageLayer.mode);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT,
                r.uidLayer.presence);
        assertEquals("ignore", r.uidLayer.mode);
        assertEquals(4, id.reads);
        assertEquals(2, shell.pkgReads);
        assertEquals(2, shell.uidReads);
    }

    @Test public void packageQueryMayAlsoExposeConsistentUidPrecedence() {
        Identity id = new Identity();
        Shell shell = new Shell();
        shell.pkg = "Uid mode: WAKE_LOCK: ignore\nWAKE_LOCK: allow; time=+12m ago";
        SchedulerAppOpsDualScopeDiagnostic.Result r = capture(id, shell);
        assertTrue(r.consistentDiagnosticOnly());
        assertEquals("allow", r.packageLayer.mode);
        assertEquals("ignore", r.uidLayer.mode);
        assertFalse(r.authorizesRestore());
    }

    @Test public void noOperationsAndDefaultHintAreNotExplicitAllowDefaultOrProvenAbsent() {
        Shell shell = new Shell();
        shell.pkg = "No operations.\nDefault mode: allow";
        shell.uid = "No operations.\nDefault mode: default";
        SchedulerAppOpsDualScopeDiagnostic.Result r = capture(new Identity(), shell);
        assertTrue(r.consistentDiagnosticOnly());
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                r.packageLayer.presence);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                r.uidLayer.presence);
        assertNull(r.packageLayer.mode);
        assertNull(r.uidLayer.mode);
        assertEquals("allow", r.packageLayer.defaultHint);
        assertEquals("default", r.uidLayer.defaultHint);
        assertFalse(r.authorizesRestore());

        shell.pkg = "WAKE_LOCK: default";
        shell.uid = "No operations.";
        r = capture(new Identity(), shell);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT,
                r.packageLayer.presence);
        assertEquals("default", r.packageLayer.mode);
        assertNull(r.packageLayer.defaultHint);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                r.uidLayer.presence);
        assertFalse(r.authorizesRestore());
    }

    @Test public void contradictoryOrUnattributableScopeNeverSucceeds() {
        Shell shell = new Shell();
        shell.pkg = "Uid mode: WAKE_LOCK: ignore\nWAKE_LOCK: allow";
        shell.uid = "Uid mode: WAKE_LOCK: allow";
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.CONTRADICTORY_SCOPES,
                new Identity(), shell);

        shell.uid = "No operations.";
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.CONTRADICTORY_SCOPES,
                new Identity(), shell);

        shell.pkg = "WAKE_LOCK: allow";
        shell.uid = "WAKE_LOCK: ignore"; // numeric UID output without explicit UID label
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.CONTRADICTORY_SCOPES,
                new Identity(), shell);
    }

    @Test public void changedPairedReadsOrInvalidShellRowsFailClosed() {
        Shell shell = new Shell();
        shell.changedPkg = "WAKE_LOCK: ignore";
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.QUERY_CHANGED,
                new Identity(), shell);
        shell.changedPkg = null;
        shell.changedUid = "Uid mode: WAKE_LOCK: default";
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.QUERY_CHANGED,
                new Identity(), shell);

        shell.changedUid = null;
        for (String invalid : new String[]{
                "", "Warning: permission denied",
                "WAKE_LOCK: allow\nError: unauthorized",
                "No operations.\nDefault mode: bad",
                "WAKE_LOCK: allow\nWAKE_LOCK: default",
                "WAKE_LOCK: allow\n\nUid mode: WAKE_LOCK: ignore",
                "No operations.\nDefault mode: allow\nBad line",
                "WAKE_LOCK: allow; time=",
                "WAKE_LOCK: allow\u0000",
                "WAKE_LOCK: allow\r\nUid mode: WAKE_LOCK: ignore",
                new String(new char[4097]).replace('\0', 'a')
        }) {
            shell.pkg = invalid;
            rejects(SchedulerAppOpsDualScopeDiagnostic.Status.QUERY_UNKNOWN,
                    new Identity(), shell);
        }
        shell.pkg = "WAKE_LOCK: allow";
        shell.uid = null;
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.QUERY_UNKNOWN,
                new Identity(), shell);
        shell.throwOnUid = true;
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.QUERY_UNKNOWN,
                new Identity(), shell);
    }

    @Test public void identityReinstallSignerOrUidDriftFailClosedAfterShellReads() {
        for (SchedulerInstalledPackageIdentity.Snapshot next :
                new SchedulerInstalledPackageIdentity.Snapshot[]{
                        row(10345, OTHER, 1000, 2000),
                        row(10345, SIGNER, 3000, 3000),
                        row(10346, SIGNER, 1000, 2000),
                        row(10345, SIGNER, 1000, 2001)
                }) {
            Identity id = new Identity();
            id.last = next;
            Shell shell = new Shell();
            rejects(SchedulerAppOpsDualScopeDiagnostic.Status.IDENTITY_CHANGED, id, shell);
            assertEquals(4, id.reads);
            assertEquals(2, shell.pkgReads);
            assertEquals(2, shell.uidReads);
        }
        Identity bad = new Identity();
        bad.initial = row(10345, "untrusted", 1000, 2000);
        Shell shell = new Shell();
        rejects(SchedulerAppOpsDualScopeDiagnostic.Status.IDENTITY_UNKNOWN, bad, shell);
        assertEquals(0, shell.pkgReads);
        assertEquals(0, shell.uidReads);
    }

    @Test public void invalidInputsRejectBeforeAnySourceRead() {
        Identity id = new Identity();
        Shell shell = new Shell();
        for (String op : new String[]{null, "", "WAKE_LOCK;whoami", "bad op"}) {
            SchedulerAppOpsDualScopeDiagnostic.Result r =
                    SchedulerAppOpsDualScopeDiagnostic.capture(PKG, op, id, shell);
            assertEquals(SchedulerAppOpsDualScopeDiagnostic.Status.INVALID_INPUT, r.status);
            assertNull(r.identity);
        }
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Status.INVALID_INPUT,
                SchedulerAppOpsDualScopeDiagnostic.capture(
                        "com.example;evil", OP, id, shell).status);
        assertEquals(0, id.reads);
        assertEquals(0, shell.pkgReads);
        assertEquals(0, shell.uidReads);
    }
}
