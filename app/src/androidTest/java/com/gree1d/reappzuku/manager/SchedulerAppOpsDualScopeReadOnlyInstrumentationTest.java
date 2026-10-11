package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Real PackageManager signer/UID identity + intentionally synthetic AppOps.
 * No "cmd appops" command is executed; this is NOT a live shell-mode proof.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerAppOpsDualScopeReadOnlyInstrumentationTest {
    @Test public void realInstalledIdentityCannotAuthorizeSyntheticScopeRestore() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerInstalledPackageAndroidSource identity =
                new SchedulerInstalledPackageAndroidSource(context);
        SchedulerAppOpsDualScopeDiagnostic.Source fixture =
                new SchedulerAppOpsDualScopeDiagnostic.Source() {
                    @Override public String readPackage(String pkg, String op) {
                        return "No operations.\nDefault mode: allow";
                    }
                    @Override public String readUid(int uid, String op) {
                        assertEquals(context.getApplicationInfo().uid, uid);
                        return "Uid mode: WAKE_LOCK: ignore";
                    }
                };
        SchedulerAppOpsDualScopeDiagnostic.Result r =
                SchedulerAppOpsDualScopeDiagnostic.capture(
                        context.getPackageName(), "WAKE_LOCK", identity, fixture);
        assertTrue("An unchanged real installed identity is read-only evidence",
                r.consistentDiagnosticOnly());
        assertEquals(context.getApplicationInfo().uid, r.identity.uid);
        assertEquals(64, r.identity.currentSignerSha256.length());
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                r.packageLayer.presence);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT,
                r.uidLayer.presence);
        assertEquals("ignore", r.uidLayer.mode);
        assertFalse("No complete rollback capability is implied", r.authorizesRestore());
    }

    @Test public void realIdentityCannotRescueContradictorySyntheticShellScopes() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerAppOpsDualScopeDiagnostic.Source contradiction =
                new SchedulerAppOpsDualScopeDiagnostic.Source() {
                    @Override public String readPackage(String pkg, String op) {
                        return "Uid mode: WAKE_LOCK: ignore\nWAKE_LOCK: allow";
                    }
                    @Override public String readUid(int uid, String op) {
                        return "Uid mode: WAKE_LOCK: allow";
                    }
                };
        SchedulerAppOpsDualScopeDiagnostic.Result r =
                SchedulerAppOpsDualScopeDiagnostic.capture(
                        context.getPackageName(), "WAKE_LOCK",
                        new SchedulerInstalledPackageAndroidSource(context), contradiction);
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Status.CONTRADICTORY_SCOPES, r.status);
        assertNull(r.identity);
        assertNull(r.packageLayer);
        assertNull(r.uidLayer);
        assertFalse(r.authorizesRestore());
    }
}
