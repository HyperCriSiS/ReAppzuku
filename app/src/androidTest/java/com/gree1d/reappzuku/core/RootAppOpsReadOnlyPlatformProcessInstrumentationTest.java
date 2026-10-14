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
import java.io.OutputStream;
import java.io.IOException;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real disposable-emulator OS-process and pipe tests, under the unprivileged
 * application UID. The shell scripts are fixed TEST FIXTURES and never mutate
 * an AppOp. These tests are NOT Root (su), Magisk, KernelSU or Shizuku evidence.
 *
 * The final test proves that an actual APP-UID shell exits 125 during the
 * effective-UID-zero preflight, before even attempting an AppOps GET. No typed
 * AppOps snapshot or restore authorization can result.
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

    /**
     * Counts bytes actually drained by the executor from a real Android child.
     * An invalid/unsupported shell fixture cannot silently satisfy the gate.
     */
    private static RootAppOpsReadOnlyExecutor.Starter measuredShell(
            String script, AtomicInteger drainedStderrBytes) {
        return () -> {
            final Process child =
                    new ProcessBuilder("/system/bin/sh", "-c", script).start();
            return new Process() {
                @Override public OutputStream getOutputStream() {
                    return child.getOutputStream();
                }
                @Override public InputStream getInputStream() {
                    return child.getInputStream();
                }
                @Override public InputStream getErrorStream() {
                    return new FilterInputStream(child.getErrorStream()) {
                        @Override public int read(byte[] buffer, int offset, int length)
                                throws IOException {
                            int n = super.read(buffer, offset, length);
                            if (n > 0) drainedStderrBytes.addAndGet(n);
                            return n;
                        }
                    };
                }
                @Override public int waitFor() throws InterruptedException {
                    return child.waitFor();
                }
                @Override public int exitValue() { return child.exitValue(); }
                @Override public void destroy() { child.destroy(); }
            };
        };
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
        // 96 x 1024 bytes ensures >64 KiB real stderr backpressure while
        // avoiding 1,600 separate printf-loop iterations on slower emulators.
        // The POSIX printf width is intentionally fixed and source-owned.
        String script = "cat >/dev/null; i=0; while [ \"$i\" -lt 96 ]; do "
                + "printf '%1024s' x >&2; "
                + "i=$((i+1)); done; printf 'No operations.\\n'";
        AtomicInteger drainedStderrBytes = new AtomicInteger();
        long start = System.nanoTime();
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                measuredShell(script, drainedStderrBytes), FIXTURE_GET, 7_000L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertFalse("Large stderr cannot be trusted as AppOps success", result.succeeded);
        assertEquals("", result.output);
        assertTrue("The real stderr reader must consume >64 KiB, not just fail "
                        + "on an unsupported fixture: " + drainedStderrBytes.get(),
                drainedStderrBytes.get() > 65_536);
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

    @Test public void actualNonRootAppUidShellExitsBeforeReadOnlyAppOpsGet()
            throws Exception {
        requireDisposableEmulator();
        int appUid = android.os.Process.myUid();
        assertTrue("This test must be unprivileged", appUid > 0);
        String packageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getPackageName();
        assertTrue(PackageNameValidator.isValid(packageName));
        int userId = appUid / 100000;
        String command = "cmd appops get --user " + userId + " "
                + packageName + " WAKE_LOCK";
        assertTrue(ShellManager.isReadOnlyAppOpsCommand(command));

        // Ordinary app-UID /system/bin/sh (not su or UiAutomation). Observe
        // the real process exit code via a transparent Process wrapper because
        // failed RootAppOpsReadOnlyExecutor outcomes intentionally hide it.
        AtomicInteger actualExit = new AtomicInteger(Integer.MIN_VALUE);
        RootAppOpsReadOnlyExecutor.Outcome result = RootAppOpsReadOnlyExecutor.execute(
                () -> {
                    final Process child = new ProcessBuilder("/system/bin/sh").start();
                    return new Process() {
                        @Override public OutputStream getOutputStream() {
                            return child.getOutputStream();
                        }
                        @Override public InputStream getInputStream() {
                            return child.getInputStream();
                        }
                        @Override public InputStream getErrorStream() {
                            return child.getErrorStream();
                        }
                        @Override public int waitFor() throws InterruptedException {
                            int code = child.waitFor();
                            actualExit.set(code);
                            return code;
                        }
                        @Override public int exitValue() { return child.exitValue(); }
                        @Override public void destroy() { child.destroy(); }
                    };
                }, command, 6_000L);
        assertFalse("App-UID process must not pass Root EUID preflight",
                result.succeeded);
        assertEquals(-1, result.exitCode);
        assertEquals("", result.output);
        assertEquals("The real shell must fail at the UID-0 preflight, "
                + "not after attempting AppOps GET", 125, actualExit.get());
        report("RZ_APP_UID_ROOT_GUARD", "EXIT_125_BEFORE_GET");
        report("RZ_APP_UID_APPOPS_RESTORE_ALLOWED", "false");
    }}
