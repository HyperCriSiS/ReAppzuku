package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.gree1d.reappzuku.ui.MainActivity;

import org.junit.Test;

import java.util.Collection;

import rikka.shizuku.Shizuku;

/**
 * Guarded API-36 probe for the exact lifecycle permutation where MainActivity is recreated while
 * the real external Shizuku permission Activity is still covering it.
 *
 * <p>The coordinating workflow starts an official Shizuku daemon with API_V23 denied, launches
 * this instrumentation, waits for {@link #TAG}'s recreation marker, then taps the real Shizuku
 * authorization dialog. Normal instrumentation runs skip this test.</p>
 */
public class RealShizukuPermissionRecreationInstrumentationTest {
    public static final String TAG = "ReAppzukuShizukuRecreate";

    @Test
    public void recreateMainActivityBehindRealPermissionDialogAndRecover() throws Exception {
        assumeTrue("real permission recreation probe not requested",
                "true".equals(InstrumentationRegistry.getArguments()
                        .getString("realPermissionRecreationProbe")));

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        assertTrue("real Shizuku Binder is unavailable", Shizuku.pingBinder());
        assertEquals("Shizuku permission must begin denied",
                PackageManager.PERMISSION_DENIED,
                Shizuku.checkSelfPermission());

        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity first = instrumentation.startActivitySync(intent);
        assertTrue("instrumentation did not launch MainActivity", first instanceof MainActivity);

        MainActivity original = waitForMainActivity(Stage.PAUSED, 20_000L);
        assertTrue("MainActivity never paused behind the Shizuku permission dialog",
                original != null);
        Log.i(TAG, "REAL_SHIZUKU_PERMISSION_DIALOG_PAUSED_MAIN");

        instrumentation.runOnMainSync(original::recreate);

        assertTrue("original MainActivity was not destroyed after recreate()",
                waitForSpecificStage(original, Stage.DESTROYED, 20_000L));
        MainActivity recreated = waitForDifferentMainActivity(original, 20_000L);
        assertTrue("replacement MainActivity was not created", recreated != null);
        assertNotSame("recreate() returned the original Activity instance", original, recreated);
        Log.i(TAG, "REAL_SHIZUKU_PERMISSION_ACTIVITY_RECREATED");

        long grantDeadline = SystemClock.uptimeMillis() + 45_000L;
        while (SystemClock.uptimeMillis() < grantDeadline
                && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            SystemClock.sleep(100L);
        }
        assertEquals("real Shizuku permission was not granted by the coordinating workflow",
                PackageManager.PERMISSION_GRANTED,
                Shizuku.checkSelfPermission());
        Log.i(TAG, "REAL_SHIZUKU_PERMISSION_GRANTED_AFTER_RECREATE");

        App app = (App) instrumentation.getTargetContext().getApplicationContext();
        long readyDeadline = SystemClock.uptimeMillis() + 20_000L;
        ShellBackendState state = app.getShellManager().getBackendState();
        while (SystemClock.uptimeMillis() < readyDeadline) {
            state = app.getShellManager().awaitAnyShellReadyBlocking();
            if (state == ShellBackendState.SHIZUKU_READY) {
                break;
            }
            SystemClock.sleep(100L);
        }
        assertEquals("MainActivity did not recover a ready Shizuku backend after recreation",
                ShellBackendState.SHIZUKU_READY,
                state);
        Log.i(TAG, "REAL_SHIZUKU_PERMISSION_RECOVERED_AFTER_RECREATE");
    }

    private MainActivity waitForMainActivity(Stage stage, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            MainActivity activity = findMainActivity(stage, null);
            if (activity != null) {
                return activity;
            }
            SystemClock.sleep(100L);
        }
        return null;
    }

    private MainActivity waitForDifferentMainActivity(MainActivity original, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            for (Stage stage : new Stage[]{Stage.PAUSED, Stage.RESUMED, Stage.STARTED, Stage.CREATED}) {
                MainActivity activity = findMainActivity(stage, original);
                if (activity != null) {
                    return activity;
                }
            }
            SystemClock.sleep(100L);
        }
        return null;
    }

    private boolean waitForSpecificStage(Activity target, Stage stage, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] found = {false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(stage);
                found[0] = activities.contains(target);
            });
            if (found[0]) {
                return true;
            }
            SystemClock.sleep(100L);
        }
        return false;
    }

    private MainActivity findMainActivity(Stage stage, MainActivity excluded) {
        final MainActivity[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(stage);
            for (Activity activity : activities) {
                if (activity instanceof MainActivity && activity != excluded) {
                    result[0] = (MainActivity) activity;
                    return;
                }
            }
        });
        return result[0];
    }
}
