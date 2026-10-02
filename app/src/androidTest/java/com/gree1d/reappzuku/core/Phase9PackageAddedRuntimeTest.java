package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class Phase9PackageAddedRuntimeTest {
    private static final String PROBE_PACKAGE = "com.reappzuku.securityprobe";
    private static final String NEW_APP_CHANNEL = "new_app_setup";

    @Test
    public void initializeHostDrivenPackageAddedBaseline() {
        assumePhase("baseline");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        NewAppDiscoveryWorker.reconcileNow(context);

        assertTrue("discovery snapshot was not initialized",
                NewAppDiscoveryWorker.hasSnapshot(context));
        assertTrue("installed probe identity missing from baseline",
                NewAppDiscoveryWorker.isCurrentInstallKnown(context, PROBE_PACKAGE));
        assertFalse("baseline must not retroactively queue existing apps",
                NewAppSetupStore.isPending(context, PROBE_PACKAGE));
    }

    @Test
    public void verifyHostDrivenPackageAddedFlow() throws Exception {
        String phase = requireVerificationPhase();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        NewAppDiscoveryWorker.reconcileNow(context);

        assertTrue("new install did not create pending setup state",
                waitUntil(() -> NewAppSetupStore.isPending(context, PROBE_PACKAGE), 10_000L));

        AppPolicy pending = AppDatabase.getInstance(context)
                .appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull("new install did not create explicit safety policy", pending);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, pending.source);
        assertEquals(AppPolicy.STRATEGY_UNMANAGED, pending.strategy);
        assertTrue("processed install identity was not checkpointed",
                NewAppDiscoveryWorker.isCurrentInstallKnown(context, PROBE_PACKAGE));

        int notificationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS);
        if ("denied".equals(phase)) {
            assertEquals(PackageManager.PERMISSION_DENIED, notificationPermission);
            assertFalse("notification posted despite denied permission",
                    hasActiveNewAppNotification(context));
            return;
        }

        assertEquals(PackageManager.PERMISSION_GRANTED, notificationPermission);
        assertTrue("granted permission did not produce setup notification",
                waitUntil(() -> hasActiveNewAppNotification(context), 10_000L));

        StatusBarNotification notification = findNewAppNotification(context);
        assertNotNull(notification);
        assertNotNull(notification.getNotification().contentIntent);
        notification.getNotification().contentIntent.send();

        AppPolicyEditorActivity editor = waitForResumedPolicyEditor(10_000L);
        assertNotNull("notification did not deep-link to AppPolicyEditorActivity", editor);
        assertEquals(PROBE_PACKAGE,
                editor.getIntent().getStringExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(editor::finish);
    }

    private static void assumePhase(String expected) {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("host-driven package install phase required",
                expected.equals(arguments.getString("phase9_install_phase")));
    }

    private static String requireVerificationPhase() {
        String phase = InstrumentationRegistry.getArguments()
                .getString("phase9_install_phase");
        Assume.assumeTrue("host-driven package install phase required",
                "denied".equals(phase) || "granted".equals(phase));
        return phase;
    }

    private static boolean hasActiveNewAppNotification(Context context) {
        return findNewAppNotification(context) != null;
    }

    private static StatusBarNotification findNewAppNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return null;
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getNotification() != null
                    && NEW_APP_CHANNEL.equals(notification.getNotification().getChannelId())) {
                return notification;
            }
        }
        return null;
    }

    private static AppPolicyEditorActivity waitForResumedPolicyEditor(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<AppPolicyEditorActivity> result = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof AppPolicyEditorActivity) {
                        result.set((AppPolicyEditorActivity) activity);
                        break;
                    }
                }
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(100L);
        }
        return null;
    }

    private static boolean waitUntil(CheckedCondition condition, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            if (condition.get()) return true;
            SystemClock.sleep(100L);
        } while (SystemClock.uptimeMillis() < deadline);
        return condition.get();
    }

    private interface CheckedCondition {
        boolean get() throws Exception;
    }
}
