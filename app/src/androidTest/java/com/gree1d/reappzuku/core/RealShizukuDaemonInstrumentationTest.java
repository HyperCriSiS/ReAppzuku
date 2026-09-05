package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Guarded device/emulator probe for the real Shizuku Binder and ReAppzuku UserService.
 * Normal instrumentation suites skip this class unless the workflow explicitly passes
 * -e realShizukuProbe true and externally restarts the Shizuku daemon after the ready marker.
 */
public class RealShizukuDaemonInstrumentationTest {
    private static final String TAG = "ReAppzukuShizukuProbe";
    public static final String READY_MARKER = "REAL_SHIZUKU_READY_FOR_RESTART";
    public static final String LOST_MARKER = "REAL_SHIZUKU_BINDER_LOSS_OBSERVED";
    public static final String RECOVERED_MARKER = "REAL_SHIZUKU_RECOVERED_AFTER_RESTART";

    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() {
        assumeTrue("real Shizuku probe not requested",
                "true".equals(InstrumentationRegistry.getArguments().getString("realShizukuProbe")));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        executor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void realDaemonBindCommandAndRestartRecovery() throws Exception {
        ShellManager manager = new ShellManager(
                context, new Handler(Looper.getMainLooper()), executor);

        assertTrue("real Shizuku binder never became available", awaitBinder(true, 15_000L));
        assertEquals("ReAppzuku is not granted by the real Shizuku server",
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                Shizuku.checkSelfPermission());

        assertEquals("real Shizuku UserService did not become ready",
                ShellBackendState.SHIZUKU_READY,
                awaitReady(manager, 20_000L));
        assertShellIdentity(manager);

        Log.i(TAG, READY_MARKER);

        boolean sawLoss = false;
        long lossDeadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < lossDeadline) {
            ShellBackendState state = manager.getBackendState();
            if (state == ShellBackendState.SHIZUKU_LOST
                    || !Shizuku.pingBinder()) {
                sawLoss = true;
                Log.i(TAG, LOST_MARKER);
                break;
            }
            Thread.sleep(100L);
        }
        assertTrue("Shizuku daemon restart never produced an observable Binder loss", sawLoss);

        assertTrue("Shizuku binder did not return after daemon restart", awaitBinder(true, 45_000L));
        assertEquals("Shizuku grant was not preserved across daemon restart",
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                Shizuku.checkSelfPermission());
        assertEquals("ReAppzuku UserService did not recover after Shizuku restart",
                ShellBackendState.SHIZUKU_READY,
                awaitReady(manager, 30_000L));
        assertShellIdentity(manager);
        Log.i(TAG, RECOVERED_MARKER);

        manager.unbindUserService();
    }

    private ShellBackendState awaitReady(ShellManager manager, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ShellBackendState last = manager.getBackendState();
        while (System.currentTimeMillis() < deadline) {
            last = manager.awaitAnyShellReadyBlocking();
            if (last == ShellBackendState.SHIZUKU_READY) {
                return last;
            }
            Thread.sleep(150L);
        }
        return last;
    }

    private boolean awaitBinder(boolean expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Shizuku.pingBinder() == expected) {
                return true;
            }
            Thread.sleep(100L);
        }
        return false;
    }

    private void assertShellIdentity(ShellManager manager) {
        String uid = manager.runShellCommandAndGetFullOutput("id -u");
        assertEquals("real UserService command did not execute with ADB shell identity", "2000", uid.trim());

        String marker = manager.runShellCommandAndGetFullOutput("printf ReAppzukuRealShizuku");
        assertEquals("real UserService command output was not returned intact",
                "ReAppzukuRealShizuku", marker);
    }
}
