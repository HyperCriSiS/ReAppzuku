package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Opt-in, REAL official-Shizuku binder/UserService-loss probe on a disposable API 37 emulator.
 *
 * The workflow kills the already-running external Shizuku daemon after READY,
 * waits for INVALIDATED, then restarts it. Only one fixed read-only AppOps GET
 * for this test app is allowed. Nothing is set/reset or written to a recovery journal.
 */
public final class RealShizukuPinnedAppOpsSessionInstrumentationTest {
    private static final String TAG = "RZShizukuAppOps";
    private static final String OPT_IN = "realShizukuPinnedAppOpsProbe";

    private ExecutorService executor;

    @Before
    public void setUp() {
        assumeTrue("only the explicit disposable real-Shizuku gate may run",
                "true".equals(InstrumentationRegistry.getArguments().getString(OPT_IN)));
        assertEquals("real Shizuku gate requires Android 17", 37, Build.VERSION.SDK_INT);
        executor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        if (executor != null) executor.shutdownNow();
    }

    @Test
    public void realShizukuDaemonDeathPermanentlyInvalidatesOriginalReadOnlyPin()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ShellManager manager = new ShellManager(
                app, new Handler(Looper.getMainLooper()), executor);
        // This CI lane boots only disposable Android user 0; do not rely on
        // non-SDK UserHandle#getIdentifier(), which is absent from public stubs.
        // An unexpected secondary-user UID must fail rather than query user 0.
        assertTrue("unexpected non-user-0 process UID", Process.myUid() >= 10_000
                && Process.myUid() < 100_000);
        String get = "cmd appops get --user 0 " + app.getPackageName() + " WAKE_LOCK";
        assertTrue(ShellManager.isReadOnlyAppOpsCommand(get));
        try {
            assertTrue("official Shizuku Binder never arrived", waitForBinder(25_000L));
            assertEquals("real Shizuku permission was not granted",
                    PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission());
            assertEquals("real bound UserService never reached READY",
                    ShellBackendState.SHIZUKU_READY, waitForReady(manager, 30_000L));

            ShellManager.AppOpsReadOnlySession original =
                    manager.captureReadOnlyAppOpsSession();
            assertNotNull("no existing UserService pin", original);
            assertEquals(ShellBackendState.SHIZUKU_READY, original.backend());
            assertTrue(manager.isReadOnlyAppOpsSessionCurrent(original));
            ShellManager.ShellResult initial = manager.runPinnedReadOnlyAppOpsGet(get, original);
            assertTrue("official Shizuku could not read its own app's AppOps",
                    initial.succeeded());
            assertFalse("real GET must provide observable output", initial.output().trim().isEmpty());
            Log.i(TAG, "RZ_SHIZUKU_APPOPS_PIN_READY");

            // The host now terminates the REAL external Shizuku daemon; the test
            // never sends a mutating command through ReAppzuku.
            long lossDeadline = System.currentTimeMillis() + 40_000L;
            boolean invalidated = false;
            while (System.currentTimeMillis() < lossDeadline) {
                if (!manager.isReadOnlyAppOpsSessionCurrent(original)) {
                    invalidated = true;
                    break;
                }
                Thread.sleep(75L);
            }
            assertTrue("daemon death did not invalidate the original pinned session",
                    invalidated);
            assertFalse(manager.isReadOnlyAppOpsSessionCurrent(original));
            ShellManager.ShellResult staleOnLoss =
                    manager.runPinnedReadOnlyAppOpsGet(get, original);
            assertFalse("stale pin cannot read during lost authority", staleOnLoss.succeeded());
            Log.i(TAG, "RZ_SHIZUKU_APPOPS_PIN_INVALIDATED");

            // The host starts a NEW official Shizuku daemon only after INVALIDATED.
            assertTrue("restarted official Shizuku Binder did not arrive",
                    waitForBinder(45_000L));
            assertEquals("Shizuku grant was lost after daemon restart",
                    PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission());
            assertEquals("new UserService did not bind",
                    ShellBackendState.SHIZUKU_READY, waitForReady(manager, 35_000L));

            assertFalse("same READY status must not resurrect an earlier pin",
                    manager.isReadOnlyAppOpsSessionCurrent(original));
            ShellManager.ShellResult staleAfterReady =
                    manager.runPinnedReadOnlyAppOpsGet(get, original);
            assertFalse("old pin must never execute on replacement service",
                    staleAfterReady.succeeded());

            ShellManager.AppOpsReadOnlySession replacement =
                    manager.captureReadOnlyAppOpsSession();
            assertNotNull("replacement UserService needs a NEW pin", replacement);
            assertTrue(manager.isReadOnlyAppOpsSessionCurrent(replacement));
            ShellManager.ShellResult fresh = manager.runPinnedReadOnlyAppOpsGet(get, replacement);
            assertTrue("new pin failed a read-only query", fresh.succeeded());
            assertFalse(fresh.output().trim().isEmpty());
            assertFalse("old authority stays revoked after the fresh read",
                    manager.isReadOnlyAppOpsSessionCurrent(original));
            Log.i(TAG, "RZ_SHIZUKU_APPOPS_NEW_PIN_READONLY_ONLY");
        } finally {
            manager.unbindUserService();
        }
    }

    private static boolean waitForBinder(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Shizuku.pingBinder()) return true;
            } catch (RuntimeException ignored) {
                // A real transient Binder loss must not turn into false readiness.
            }
            Thread.sleep(100L);
        }
        return Shizuku.pingBinder();
    }

    private static ShellBackendState waitForReady(ShellManager manager, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ShellBackendState observed = manager.getBackendState();
        while (System.currentTimeMillis() < deadline) {
            if (observed == ShellBackendState.SHIZUKU_READY) return observed;
            observed = manager.awaitAnyShellReadyBlocking();
            if (observed == ShellBackendState.SHIZUKU_READY) return observed;
            Thread.sleep(125L);
        }
        return observed;
    }
}
