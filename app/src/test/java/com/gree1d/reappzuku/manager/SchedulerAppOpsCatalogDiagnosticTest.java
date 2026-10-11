package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

public final class SchedulerAppOpsCatalogDiagnosticTest {
    private static final String PKG = "com.example.catalog";
    private static final String SIGNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String[] OPS = {"WAKE_LOCK", "RUN_IN_BACKGROUND"};

    private static SchedulerInstalledPackageIdentity.Snapshot identity(
            int uid, String signer, long installed) {
        return new SchedulerInstalledPackageIdentity.Snapshot(
                PKG, uid, 0, signer, 5, installed, 2500, true);
    }

    private static final class PackageIdentity
            implements SchedulerInstalledPackageIdentity.Source {
        SchedulerInstalledPackageIdentity.Snapshot before = identity(10342, SIGNER, 1000);
        SchedulerInstalledPackageIdentity.Snapshot after = before;
        int reads;
        @Override public SchedulerInstalledPackageIdentity.Snapshot read(String pkg) {
            return ++reads <= 2 ? before : after;
        }
    }

    private static final class Shell implements SchedulerAppOpsDualScopeDiagnostic.Source {
        final java.util.Map<String, String> pkg = new LinkedHashMap<>();
        final java.util.Map<String, String> uid = new LinkedHashMap<>();
        String changedOp;
        boolean changePackage;
        String changeTo;
        String unsupportedOp;
        boolean throwOnUid;
        int packageReads;
        int uidReads;
        final java.util.List<String> order = new java.util.ArrayList<>();

        Shell() {
            pkg.put("WAKE_LOCK", "WAKE_LOCK: allow");
            uid.put("WAKE_LOCK", "Uid mode: WAKE_LOCK: ignore");
            pkg.put("RUN_IN_BACKGROUND", "No operations.\nDefault mode: allow");
            uid.put("RUN_IN_BACKGROUND", "No operations.");
        }

        @Override public String readPackage(String name, String op) {
            packageReads++;
            order.add("P:" + op);
            if (op.equals(unsupportedOp)) return "Permission denied";
            if (changePackage && op.equals(changedOp) && packageReads > OPS.length) {
                return changeTo;
            }
            return pkg.get(op);
        }

        @Override public String readUid(int uidValue, String op) {
            assertEquals(10342, uidValue);
            uidReads++;
            order.add("U:" + op);
            if (throwOnUid) throw new IllegalStateException("read failed");
            if (!changePackage && op.equals(changedOp) && uidReads > OPS.length) {
                return changeTo;
            }
            return uid.get(op);
        }
    }

    private static SchedulerAppOpsCatalogDiagnostic.Result capture(
            PackageIdentity id, Shell shell) {
        return SchedulerAppOpsCatalogDiagnostic.capture(PKG, OPS, id, shell);
    }

    private static void rejected(SchedulerAppOpsCatalogDiagnostic.Status reason,
            PackageIdentity id, Shell shell) {
        SchedulerAppOpsCatalogDiagnostic.Result r = capture(id, shell);
        assertEquals(reason, r.status);
        assertFalse(r.consistentDiagnosticOnly());
        assertFalse(r.authorizesRestore());
        assertNull("Must never leak partial identity", r.identity);
        assertTrue("Must never leak partial operation evidence", r.operations.isEmpty());
    }

    @Test public void stableCatalogPreservesEachScopeAndWholePassOrdering() {
        PackageIdentity id = new PackageIdentity();
        Shell shell = new Shell();
        SchedulerAppOpsCatalogDiagnostic.Result result = capture(id, shell);
        assertTrue(result.consistentDiagnosticOnly());
        assertFalse(result.authorizesRestore());
        assertEquals(4, id.reads);
        assertEquals(4, shell.packageReads);
        assertEquals(4, shell.uidReads);
        assertEquals(Arrays.asList("P:WAKE_LOCK", "U:WAKE_LOCK",
                "P:RUN_IN_BACKGROUND", "U:RUN_IN_BACKGROUND",
                "P:WAKE_LOCK", "U:WAKE_LOCK",
                "P:RUN_IN_BACKGROUND", "U:RUN_IN_BACKGROUND"), shell.order);
        assertEquals(Arrays.asList(OPS), Arrays.asList(
                result.operations.keySet().toArray(new String[0])));
        SchedulerAppOpsCatalogDiagnostic.OperationEvidence wake =
                result.operations.get("WAKE_LOCK");
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT,
                wake.packageLayer.presence);
        assertEquals("allow", wake.packageLayer.mode);
        assertEquals("ignore", wake.uidLayer.mode);
        SchedulerAppOpsCatalogDiagnostic.OperationEvidence background =
                result.operations.get("RUN_IN_BACKGROUND");
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                background.packageLayer.presence);
        assertNull(background.packageLayer.mode);
        assertEquals("allow", background.packageLayer.defaultHint);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                background.uidLayer.presence);
        try {
            result.operations.clear();
            fail("Catalog must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Immutable diagnostics only.
        }
    }

    @Test public void anyEntryChangingAcrossPassesInvalidatesEntireCatalog() {
        Shell shell = new Shell();
        shell.changedOp = "RUN_IN_BACKGROUND";
        shell.changePackage = true;
        shell.changeTo = "RUN_IN_BACKGROUND: default";
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_CHANGED,
                new PackageIdentity(), shell);
        shell = new Shell();
        shell.changedOp = "WAKE_LOCK";
        shell.changeTo = "Uid mode: WAKE_LOCK: allow";
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_CHANGED,
                new PackageIdentity(), shell);
    }

    @Test public void unexpectedOrContradictoryScopeRejectsAllOperations() {
        Shell shell = new Shell();
        shell.unsupportedOp = "RUN_IN_BACKGROUND";
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN,
                new PackageIdentity(), shell);
        shell = new Shell();
        shell.pkg.put("WAKE_LOCK", "Uid mode: WAKE_LOCK: default\nWAKE_LOCK: allow");
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.CONTRADICTORY_SCOPES,
                new PackageIdentity(), shell);
        shell = new Shell();
        shell.uid.put("RUN_IN_BACKGROUND", "RUN_IN_BACKGROUND: allow");
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.CONTRADICTORY_SCOPES,
                new PackageIdentity(), shell);
        shell = new Shell();
        shell.pkg.put("RUN_IN_BACKGROUND", "No operations.\nWarning: read denied");
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN,
                new PackageIdentity(), shell);
        shell = new Shell();
        shell.throwOnUid = true;
        rejected(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN,
                new PackageIdentity(), shell);
    }

    @Test public void identityChangeAfterTwoFullPassesInvalidatesEveryEntry() {
        for (SchedulerInstalledPackageIdentity.Snapshot later
                : new SchedulerInstalledPackageIdentity.Snapshot[] {
                    identity(10342, OTHER, 1000),
                    identity(10342, SIGNER, 2200),
                    identity(10343, SIGNER, 1000)}) {
            PackageIdentity id = new PackageIdentity();
            id.after = later;
            Shell shell = new Shell();
            rejected(SchedulerAppOpsCatalogDiagnostic.Status.IDENTITY_CHANGED, id, shell);
            assertEquals(4, id.reads);
            assertEquals(4, shell.packageReads);
            assertEquals(4, shell.uidReads);
        }
    }

    @Test public void invalidCatalogDoesNotReadOrReturnPartialData() {
        PackageIdentity id = new PackageIdentity();
        Shell shell = new Shell();
        String[][] invalid = {
                null, {}, {"WAKE_LOCK", "WAKE_LOCK"},
                {"WAKE_LOCK;rm"}, {""}
        };
        for (String[] ops : invalid) {
            SchedulerAppOpsCatalogDiagnostic.Result r =
                    SchedulerAppOpsCatalogDiagnostic.capture(PKG, ops, id, shell);
            assertEquals(SchedulerAppOpsCatalogDiagnostic.Status.INVALID_INPUT, r.status);
            assertNull(r.identity);
            assertTrue(r.operations.isEmpty());
        }
        assertEquals(SchedulerAppOpsCatalogDiagnostic.Status.INVALID_INPUT,
                SchedulerAppOpsCatalogDiagnostic.capture(
                        "com.example;evil", OPS, id, shell).status);
        assertEquals(0, id.reads);
        assertEquals(0, shell.packageReads);
        assertEquals(0, shell.uidReads);
    }
}
