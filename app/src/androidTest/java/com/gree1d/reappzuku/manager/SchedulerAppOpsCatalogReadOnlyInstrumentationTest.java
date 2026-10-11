package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Real PackageManager identity, synthetic read-only AppOps values.
 * No privileged shell or AppOps mutation occurs in these tests.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerAppOpsCatalogReadOnlyInstrumentationTest {
    private static final String[] OPS = {"WAKE_LOCK", "RUN_IN_BACKGROUND"};

    @Test public void installedSignerAndUidBoundSyntheticCatalogCannotAuthorizeRecovery() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerAppOpsCatalogDiagnostic.Result evidence =
                SchedulerAppOpsCatalogDiagnostic.capture(
                    context.getPackageName(), OPS,
                    new SchedulerInstalledPackageAndroidSource(context),
                    new SchedulerAppOpsDualScopeDiagnostic.Source() {
                        @Override public String readPackage(String pkg, String op) {
                            return op + ": allow";
                        }
                        @Override public String readUid(int uid, String op) {
                            assertEquals(context.getApplicationInfo().uid, uid);
                            return "No operations.\nDefault mode: allow";
                        }
                    });
        assertTrue(evidence.consistentDiagnosticOnly());
        assertFalse(evidence.authorizesRestore());
        assertEquals(context.getApplicationInfo().uid, evidence.identity.uid);
        assertEquals(64, evidence.identity.currentSignerSha256.length());
        assertEquals(2, evidence.operations.size());
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                evidence.operations.get("WAKE_LOCK").uidLayer.presence);
    }

    @Test public void installedSignerCannotRescueOneContradictorySyntheticOperation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerAppOpsCatalogDiagnostic.Result evidence =
                SchedulerAppOpsCatalogDiagnostic.capture(
                    context.getPackageName(), OPS,
                    new SchedulerInstalledPackageAndroidSource(context),
                    new SchedulerAppOpsDualScopeDiagnostic.Source() {
                        @Override public String readPackage(String pkg, String op) {
                            return op.equals("WAKE_LOCK")
                                    ? "WAKE_LOCK: allow"
                                    : "Uid mode: RUN_IN_BACKGROUND: ignore\n"
                                            + "RUN_IN_BACKGROUND: allow";
                        }
                        @Override public String readUid(int uid, String op) {
                            return op.equals("WAKE_LOCK")
                                    ? "Uid mode: WAKE_LOCK: ignore"
                                    : "Uid mode: RUN_IN_BACKGROUND: allow";
                        }
                    });
        assertEquals(SchedulerAppOpsCatalogDiagnostic.Status.CONTRADICTORY_SCOPES,
                evidence.status);
        assertNull(evidence.identity);
        assertTrue(evidence.operations.isEmpty());
        assertFalse(evidence.authorizesRestore());
    }
}
