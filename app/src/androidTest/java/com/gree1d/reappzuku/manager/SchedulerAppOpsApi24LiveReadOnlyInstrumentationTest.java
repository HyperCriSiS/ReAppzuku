package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Disposable API24 emulator: real read-only package AND UID AppOps CLI probes.
 *
 * This is NOT an API24 privilege grant, a positive UID-override demonstration,
 * a portable ABSENT-mode witness, or a Root/Shizuku recovery test.
 * Unsupported numeric UID output MUST remain QUERY_UNKNOWN, never absence.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerAppOpsApi24LiveReadOnlyInstrumentationTest {
    private static final String WAKE_LOCK = "WAKE_LOCK";
    private static final String RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND";

    private UiAutomation checkedEmulatorShell() {
        assertEquals("Never run live API24 probe on another Android version",
                24, Build.VERSION.SDK_INT);
        UiAutomation shell = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        assertEquals("Must be a disposable emulator, not a physical device",
                "1", SchedulerAppOpsLiveShellReadOnlyInstrumentationTest.readShell(
                        shell, "getprop ro.kernel.qemu"));
        return shell;
    }

    @Test public void realPackageAndUidAppopsReadOnlyResponsesFailClosed() {
        UiAutomation shell = checkedEmulatorShell();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = app.getPackageName();
        SchedulerInstalledPackageAndroidSource installed =
                new SchedulerInstalledPackageAndroidSource(app);
        SchedulerInstalledPackageIdentity.Result initial =
                SchedulerInstalledPackageIdentity.captureStable(packageName, installed);
        assertTrue("API24 installed package identity must be independently stable",
                initial.matched());

        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(initial.snapshot, command ->
                        SchedulerAppOpsLiveShellReadOnlyInstrumentationTest.readShell(
                                shell, command));
        String packageWake = source.readPackage(packageName, WAKE_LOCK);
        String packageBackground = source.readPackage(packageName, RUN_IN_BACKGROUND);
        assertNotNull("Real API24 package WAKE_LOCK query must return stdout", packageWake);
        assertNotNull("Real API24 package RUN_IN_BACKGROUND query must return stdout",
                packageBackground);

        String uidWake = source.readUid(initial.snapshot.uid, WAKE_LOCK);
        String uidBackground = source.readUid(initial.snapshot.uid, RUN_IN_BACKGROUND);

        boolean wakeUidRecognized = uidWake != null &&
                SchedulerAppOpsDualScopeDiagnostic.parse(uidWake, WAKE_LOCK) != null;
        boolean backgroundUidRecognized = uidBackground != null &&
                SchedulerAppOpsDualScopeDiagnostic.parse(
                        uidBackground, RUN_IN_BACKGROUND) != null;

        SchedulerAppOpsCatalogDiagnostic.Result result =
                SchedulerAppOpsCatalogDiagnostic.capture(
                        packageName, new String[]{WAKE_LOCK, RUN_IN_BACKGROUND},
                        installed, source);

        Bundle status = new Bundle();
        status.putString("RZ_LIVE_APPOPS_API24_PACKAGE_GET", "EXIT_ZERO_NONEMPTY");
        status.putString("RZ_LIVE_APPOPS_API24_UID_WAKE",
                wakeUidRecognized ? "RECOGNIZED" : "UNKNOWN");
        status.putString("RZ_LIVE_APPOPS_API24_UID_BACKGROUND",
                backgroundUidRecognized ? "RECOGNIZED" : "UNKNOWN");
        status.putString("RZ_LIVE_APPOPS_API24_CATALOG_RESULT", result.status.name());
        status.putString("RZ_LIVE_APPOPS_API24_SCOPE_COUNT",
                Integer.toString(result.operations.size()));
        status.putString("RZ_LIVE_APPOPS_API24_RESTORE_ALLOWED", "false");
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);

        assertFalse("No API24 read-only observation can authorize restore",
                result.authorizesRestore());
        if (!wakeUidRecognized || !backgroundUidRecognized) {
            assertEquals("Unsupported UID CLI must not be mistaken for ABSENT",
                    SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN, result.status);
        }
        if (result.consistentDiagnosticOnly()) {
            assertEquals(2, result.operations.size());
            assertNotNull(result.identity);
            assertEquals(initial.snapshot.uid, result.identity.uid);
        } else {
            assertTrue("Uncertain snapshots must not expose partial operation modes",
                    result.operations.isEmpty());
            assertNull("Uncertain snapshots must not expose identity", result.identity);
        }
        assertTrue("Real API24 reads must not change installed package identity",
                SchedulerInstalledPackageIdentity.checkAgain(
                        initial.snapshot, installed).matched());
    }

    @Test public void deniedCommandExitAndMalformedTargetsAreRejected() {
        UiAutomation shell = checkedEmulatorShell();
        assertNull("Nonzero real shell exit is not a successful observation",
                SchedulerAppOpsLiveShellReadOnlyInstrumentationTest.readShell(shell, "false"));

        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerInstalledPackageIdentity.Result identity =
                SchedulerInstalledPackageIdentity.captureStable(app.getPackageName(),
                        new SchedulerInstalledPackageAndroidSource(app));
        assertTrue(identity.matched());

        final int[] executed = {0};
        SchedulerAppOpsReadOnlyShellSource source = new SchedulerAppOpsReadOnlyShellSource(
                identity.snapshot, command -> {
                    executed[0]++;
                    return "No operations.";
                });
        assertNull(source.readPackage(app.getPackageName(), "WAKE_LOCK;reset"));
        assertNull(source.readPackage("com.unrelated.app", WAKE_LOCK));
        assertNull(source.readUid(identity.snapshot.uid + 1, WAKE_LOCK));
        assertEquals("Rejected requests must never reach the shell backend", 0, executed[0]);
    }
}
