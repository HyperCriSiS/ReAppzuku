package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Process;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Whether a running app process gets the Binder of a restarted Shizuku
 * server. The test runs in the app's own process, where the app has set up
 * its shell manager, so the app's own request to its provider process is what
 * is measured. It needs no Shizuku permission: the Binder arrives before any
 * permission is asked for.
 *
 * Skipped unless started with "-e realShizukuReconnectProbe true". The host
 * script ends the real server after the ready marker and starts it again
 * after the lost marker.
 */
public class RealShizukuBinderReconnectInstrumentationTest {
    private static final String TAG = "RZShizukuReconnect";
    public static final String READY_MARKER = "RZ170_BINDER_READY";
    public static final String LOST_MARKER = "RZ170_BINDER_LOST";
    public static final String BACK_MARKER = "RZ170_BINDER_BACK";

    @Test
    public void aRunningAppGetsTheBinderOfARestartedServer() throws Exception {
        assumeTrue("real Shizuku reconnect probe not requested", "true".equals(
                InstrumentationRegistry.getArguments().getString("realShizukuReconnectProbe")));
        int pid = Process.myPid();

        assertTrue("the Binder of the running Shizuku server never arrived", awaitPing(true, 60_000L));
        CountDownLatch lost = new CountDownLatch(1);
        Shizuku.OnBinderDeadListener deadListener = lost::countDown;
        Shizuku.addBinderDeadListener(deadListener);
        Log.i(TAG, READY_MARKER + " pid=" + pid);

        assertTrue("the end of the Shizuku server was never seen", lost.await(120, TimeUnit.SECONDS));
        Shizuku.removeBinderDeadListener(deadListener);
        CountDownLatch back = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener receivedListener = back::countDown;
        // Not the sticky variant: only a Binder that arrives from now on counts.
        Shizuku.addBinderReceivedListener(receivedListener);
        Log.i(TAG, LOST_MARKER + " pid=" + pid);

        // The app asks its provider every 10 seconds, twelve times.
        boolean returned = back.await(170, TimeUnit.SECONDS);
        Shizuku.removeBinderReceivedListener(receivedListener);
        assertTrue("the Binder of the restarted Shizuku server did not reach the running app", returned);
        assertTrue("the returned Binder does not answer", Shizuku.pingBinder());
        assertEquals("the app process was replaced during the test", pid, Process.myPid());
        Log.i(TAG, BACK_MARKER + " pid=" + pid);
    }

    private static boolean awaitPing(boolean expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Shizuku.pingBinder() == expected) return true;
            Thread.sleep(200L);
        }
        return Shizuku.pingBinder() == expected;
    }
}
