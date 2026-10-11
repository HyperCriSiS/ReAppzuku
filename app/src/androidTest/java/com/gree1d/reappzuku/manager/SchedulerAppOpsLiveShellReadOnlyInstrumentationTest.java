package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Disposable API37 emulator ONLY. Uses the actual Android shell for real,
 * nonmutating "cmd appops get" results, NOT synthetic shell response strings.
 *
 * UiAutomation is an instrumentation privilege; success here is NOT proof of
 * user-granted Shizuku/root or real-device restoration support.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerAppOpsLiveShellReadOnlyInstrumentationTest {
    private static final String TAG = "RZ_APP_OPS_READ_ONLY";
    private static final String EXIT_MARKER = "__RZ_APP_OPS_DIAG_EXIT__";
    private static final int MAX_BYTES = 8192;

    /** Shell exit status and stderr are represented by an explicit sentinel. */
    private static String readShell(UiAutomation automation, String cmd) {
        // The cmd is constructed by the pinned-identity adapter from validated
        // literal components, not from arbitrary user input.
        String wrapped = cmd + " 2>&1; printf '\\n" + EXIT_MARKER + "%s\\n' \"$?\"";
        ParcelFileDescriptor descriptor = automation.executeShellCommand(wrapped);
        if (descriptor == null) return null;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[1024];
            int size;
            while ((size = in.read(block)) != -1) {
                if (out.size() + size > MAX_BYTES) return null;
                out.write(block, 0, size);
            }
            String full = new String(out.toByteArray(), StandardCharsets.UTF_8);
            String successSuffix = "\n" + EXIT_MARKER + "0\n";
            if (!full.endsWith(successSuffix)) return null;
            String stdout = full.substring(0, full.length() - successSuffix.length());
            return stdout.isEmpty() ? null : stdout;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    @Test public void emulatorRealAppopsShellIsReadOnlyAndNeverRestores() {
        Assume.assumeTrue("Only a disposable API37 emulator is authorized",
                Build.VERSION.SDK_INT == 37);
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Assume.assumeTrue("Never execute device shell probes on real hardware",
                "1".equals(readShell(automation, "getprop ro.kernel.qemu")));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = context.getPackageName();
        SchedulerInstalledPackageAndroidSource identities =
                new SchedulerInstalledPackageAndroidSource(context);
        SchedulerInstalledPackageIdentity.Result before =
                SchedulerInstalledPackageIdentity.captureStable(packageName, identities);
        assertTrue("Current installed package must be stable on disposable emulator",
                before.matched());

        SchedulerAppOpsReadOnlyShellSource adapter =
                new SchedulerAppOpsReadOnlyShellSource(before.snapshot,
                        cmd -> readShell(automation, cmd));
        String packageWakeLock = adapter.readPackage(packageName, "WAKE_LOCK");
        assertNotNull("API37 shell should return a successful real package query",
                packageWakeLock);
        assertFalse("A shell diagnostic never writes an AppOp",
                packageWakeLock.contains(EXIT_MARKER));

        SchedulerAppOpsCatalogDiagnostic.Result result =
                SchedulerAppOpsCatalogDiagnostic.capture(
                        packageName, new String[]{"WAKE_LOCK", "RUN_IN_BACKGROUND"},
                        identities, adapter);

        Log.i(TAG, "api=" + Build.VERSION.SDK_INT + " packageWakeRead=true"
                + " dualScopeCatalog=" + result.status
                + " operations=" + result.operations.size()
                + " restoreAuthorized=" + result.authorizesRestore());
        assertFalse("A real shell observation cannot authorize rollback",
                result.authorizesRestore());
        if (result.consistentDiagnosticOnly()) {
            assertEquals(2, result.operations.size());
            assertEquals(before.snapshot.uid, result.identity.uid);
        } else {
            // Unsupported UID shell syntax, unknown output, or races
            // must never produce partial recovery evidence.
            assertNull(result.identity);
            assertTrue(result.operations.isEmpty());
        }

        assertTrue("Shell observations cannot change installed app identity",
                SchedulerInstalledPackageIdentity.checkAgain(
                        before.snapshot, identities).matched());
    }

    @Test public void invalidShellOperationAndIdentityCannotBeSentToAndroid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerInstalledPackageIdentity.Result identity =
                SchedulerInstalledPackageIdentity.captureStable(context.getPackageName(),
                        new SchedulerInstalledPackageAndroidSource(context));
        assertTrue(identity.matched());
        final int[] calls = {0};
        SchedulerAppOpsReadOnlyShellSource adapter =
                new SchedulerAppOpsReadOnlyShellSource(identity.snapshot, cmd -> {
                    calls[0]++;
                    return "No operations.";
                });
        assertNull(adapter.readPackage(context.getPackageName(), "WAKE_LOCK;set ignore"));
        assertNull(adapter.readUid(identity.snapshot.uid + 1, "WAKE_LOCK"));
        assertNull(adapter.readPackage("com.other.app", "WAKE_LOCK"));
        assertEquals("Invalid requests must not reach instrumentation shell", 0, calls[0]);
    }
}
