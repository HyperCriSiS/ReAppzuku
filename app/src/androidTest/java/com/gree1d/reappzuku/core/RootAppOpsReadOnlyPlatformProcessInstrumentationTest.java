package com.gree1d.reappzuku.core;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real disposable-emulator OS-process and pipe tests, under the unprivileged
 * application UID. The shell scripts are fixed TEST FIXTURES and never mutate
 * an AppOp. These tests are NOT Root (su), Magisk, KernelSU or Shizuku evidence.
 *
 * The final test attempts one actual read-only AppOps GET as the APP UID. Its
 * output is strictly transport evidence, not a typed or restore-authorizing
 * AppOps snapshot. A denied/unsupported query is an acceptable observation.
 */
@RunWith(AndroidJUnit4.class)
public final class RootAppOpsReadOnlyPlatformProcessInstrumentationTest {
    private static final String FIXTURE_GET =
            "cmd appops get --user 0 com.example.diagnostic WAKE_LOCK";
    private static final int MAX_EMULATOR_PROPERTY_BYTES = 64;

    private static void requireDisposableEmulator() throws Exception {
        assertTrue("Only disposable API24 or API37 emulators are authorized",
                Build.VERSION.SDK_INT == 24 || Build.VERSION.SDK_INT == 37);
        UiAutomation automation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor fd =
                automation.executeShellCommand("getprop ro.kernel.qemu");
        assertNotNull("Emulator identity command unavailable", fd);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32];
            int n;
            while ((n = in.read(buf)) != -1) {
                assertTrue("Emulator identity unexpectedly verbose",
                        data.size() + n <= MAX_EMULATOR_PROPERTY_BYTES);
                data.write(buf, 0, n);
            }
            assertEquals("Only an emulator may run subprocess fault fixtures",
                    "1", new String(data.toByteArray(), StandardCharsets.UTF_8).trim());
        }
    }

    private static RootAppOpsReadOnlyExecutor.Starter testShell(String script) {
        // The script is a source-code constant. The fixed AppOps GET argument
        // written by the executor is consumed by 'cat' and never executed.
        return () -> new ProcessBuilder("/system/bin/sh", "-c", script).start();
    }

    private static void report(String key, String value) {
        Bundle status = new Bundle();
        status.putString(key, value);
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    @Test public void actualAndroidPipePairReturnsBoundedSyntheticReadOnlyFixture()
            throws Exception {
        requireDisposableEmulator();
        String script = "cat >/dev/null; printf 'No operations.\\nDefault mode: allow\\n'";
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                testShell(script), FIXTURE_GET, 5_000L);
        assertTrue("A real sh process must return the bounded synthetic fixture",
                result.succeeded);
        assertEquals(0, result.exitCode);
        assertEquals("No operations.\nDefault mode: allow\n", result.output);
        assertTrue(result.output.getBytes(StandardCharsets.UTF_8).length
                <= RootAppOpsReadOnlyExecutor.MAX_STDOUT_BYTES);
        report("RZ_APP_UID_PROCESS_PIPE_CONTROL", "EXIT_ZERO_SYNTHETIC");
    }

    @Test public void actualAndroidPipeBackpressureNeverBecomesPartialEvidence()
            throws Exception {
        requireDisposableEmulator();
        // More than a pipe buffer of stderr, written BEFORE any stdout.
        // Sequential stdout-then-stderr reads can stall here. The actual
        // executor must drain both concurrently and reject ALL stderr.
        String line =
                "012345678901234567890123456789012345678901234567890123456789";
        String script = "cat >/dev/null; i=0; while [ \"$i\" -lt 1600 ]; do "
                + "printf '%s\\n' '" + line + "' >&2; "
                + "i=$((i+1)); done; printf 'No operations.\\n'";
        long start = System.nanoTime();
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                testShell(script), FIXTURE_GET, 7_000L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertFalse("Large stderr cannot be trusted as AppOps success", result.succeeded);
        assertEquals("", result.output);
        // A pre-deadline exit proves the real OS pipes did not deadlock.
        assertTrue("Real OS pipes must drain rather than await the deadline: "
                        + elapsedMs + "ms", elapsedMs < 6_500L);
        report("RZ_APP_UID_PROCESS_STDERR_FLOOD", "REJECTED_WITHOUT_DEADLOCK");
    }

    @Test public void actualAndroidHungChildTimesOutAndInvalidCommandNeverSpawns()
            throws Exception {
        requireDisposableEmulator();
        // A shell built-in spin avoids a persistent 'sleep' descendant.
        String script = "cat >/dev/null; while :; do :; done";
        long start = System.nanoTime();
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                testShell(script), FIXTURE_GET, 350L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertFalse("A real spinning child must time out", result.succeeded);
        assertEquals("", result.output);
        assertTrue("Deadline should return within a bounded margin",
                elapsedMs < 2_500L);

        AtomicInteger processes = new AtomicInteger();
        RootAppOpsReadOnlyExecutor.Outcome rejected =
                RootAppOpsReadOnlyExecutor.execute(() -> {
                    processes.incrementAndGet();
                    return new ProcessBuilder("/system/bin/sh").start();
                }, "cmd appops reset --user 0 com.example.diagnostic", 350L);
        assertFalse(rejected.succeeded);
        assertEquals("Forbidden mutation must not launch a process", 0, processes.get());
        report("RZ_APP_UID_PROCESS_TIMEOUT_AND_MUTATION", "REJECTED");
    }

    @Test public void actualAppUidAppOpsGetCannotAuthorizeOriginalModeRecovery()
            throws Exception {
        requireDisposableEmulator();
        String packageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getPackageName();
        assertTrue(PackageNameValidator.isValid(packageName));
        int userId = android.os.Process.myUid() / 100000;
        String command = "cmd appops get --user " + userId + " "
                + packageName + " WAKE_LOCK";
        assertTrue(ShellManager.isReadOnlyAppOpsCommand(command));

        // This shell runs as the normal target app UID, NOT as adb shell,
        // instrumentation UiAutomation, Shizuku, or su. Android may reject its
        // AppOps query. We do NOT convert returned text into original modes.
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                () -> new ProcessBuilder("/system/bin/sh").start(),
                command, 6_000L);
        assertEquals(result.succeeded ? 0 : -1, result.exitCode);
        if (result.succeeded) {
            assertFalse(result.output.isEmpty());
            assertTrue(result.output.getBytes(StandardCharsets.UTF_8).length
                    <= RootAppOpsReadOnlyExecutor.MAX_STDOUT_BYTES);
        } else {
            assertEquals("", result.output);
        }
        report("RZ_APP_UID_APPOPS_GET_TRANSPORT",
                result.succeeded ? "EXIT_ZERO_NONEMPTY_UNPARSED" : "UNKNOWN_OR_DENIED");
        report("RZ_APP_UID_APPOPS_RESTORE_ALLOWED", "false");
    }
}
