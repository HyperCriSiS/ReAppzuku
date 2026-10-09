package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;
import com.gree1d.reappzuku.ui.NewAppSetupSettingsActivity;

import org.junit.Assume;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in API37 notification-denial fallback on disposable CI emulators.
 * The host revokes POST_NOTIFICATIONS before instrumentation and restores its
 * starting grant state afterward. No permission or privileged app action is
 * changed by this test, and only one test-owned queue entry is restored.
 */
public final class Api37NotificationDeniedSetupFallbackInstrumentationTest {
    @Test
    public void deniedNotificationLeavesDurableReviewNextFallbackUsable() throws Exception {
        Assume.assumeTrue("Run only with the dedicated CI gate",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_notification_denied")));
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        App app = (App) context.getApplicationContext();
        assertNotNull(app.getSharedExecutor());
        app.getSharedExecutor().submit(() -> {}).get(60, TimeUnit.SECONDS);

        assertEquals("CI must explicitly deny POST_NOTIFICATIONS before this test",
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS));

        // The instrumentation APK is a genuinely installed, non-system package,
        // unlike a synthetic uninstalled identifier that startup replay would prune.
        String fixture = instrumentation.getContext().getPackageName();
        assertTrue(NewAppSetupCoordinator.isEligible(context, fixture));
        AppDatabase db = AppDatabase.getInstance(context);
        AppPolicy policyBefore = db.appPolicyDao().getByPackage(fixture);
        Set<String> queueBefore = new HashSet<>(NewAppSetupStore.getPending(context));
        NotificationManager notifications = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(notifications);
        int notificationId = 0x4E410000 ^ fixture.hashCode();
        assertNull("Fixture must not have a pre-existing posted notification",
                findNotification(notifications, notificationId));

        Activity settings = null;
        Activity editor = null;
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            NewAppSetupStore.addPending(context, fixture);
            Set<String> expected = new HashSet<>(queueBefore);
            expected.add(fixture);
            assertEquals("Pending state must remain persisted despite permission denial",
                    expected, new HashSet<>(NewAppSetupStore.getPending(context)));

            NewAppSetupNotifier.notifyNeedsSetup(context, fixture);
            assertNull("Denied notifications must not be posted",
                    findNotification(notifications, notificationId));
            assertEquals("Denied notification must not clear the setup queue",
                    expected, new HashSet<>(NewAppSetupStore.getPending(context)));

            settings = instrumentation.startActivitySync(
                    new Intent(context, NewAppSetupSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            assertTrue(settings instanceof NewAppSetupSettingsActivity);
            Button review = settings.findViewById(R.id.new_app_setup_review_next);
            TextView pendingCount = settings.findViewById(R.id.new_app_setup_pending);
            assertNotNull(review);
            assertNotNull(pendingCount);
            waitUntilEnabled(instrumentation, review);
            assertEquals(context.getString(R.string.new_app_setup_pending_count,
                    NewAppSetupStore.getPending(context).size()),
                    pendingCount.getText().toString());

            // Review-next can pick another pre-existing queued package. It must
            // still route to precisely the first item in the durable sorted queue.
            String firstPending = NewAppSetupStore.getPending(context).get(0);
            instrumentation.runOnMainSync(review::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull("Review-next must remain usable without a notification", editor);
            assertEquals(firstPending, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));

            assertEquals("Opening review must not remove unconfigured packages",
                    expected, new HashSet<>(NewAppSetupStore.getPending(context)));
            AppPolicy policyAfter = db.appPolicyDao().getByPackage(fixture);
            if (policyBefore == null) {
                assertNull("Review alone must not create an explicit policy", policyAfter);
            } else {
                assertNotNull(policyAfter);
                assertEquals(policyBefore.source, policyAfter.source);
                assertEquals(policyBefore.strategy, policyAfter.strategy);
                assertEquals(policyBefore.updatedAt, policyAfter.updatedAt);
            }
            assertFalse("Permission must still be denied after review",
                    ContextCompat.checkSelfPermission(context,
                            Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED);
        } finally {
            instrumentation.removeMonitor(monitor);
            finish(instrumentation, editor);
            finish(instrumentation, settings);
            if (queueBefore.contains(fixture)) NewAppSetupStore.addPending(context, fixture);
            else NewAppSetupStore.removePending(context, fixture);
            assertEquals("Test must restore the original pending set",
                    queueBefore, new HashSet<>(NewAppSetupStore.getPending(context)));
        }
    }

    private static StatusBarNotification findNotification(NotificationManager manager, int id) {
        for (StatusBarNotification entry : manager.getActiveNotifications()) {
            if (entry.getId() == id) return entry;
        }
        return null;
    }

    private static void waitUntilEnabled(Instrumentation instrumentation, Button button)
            throws InterruptedException {
        AtomicBoolean enabled = new AtomicBoolean(false);
        long deadline = System.currentTimeMillis() + 6000L;
        do {
            instrumentation.runOnMainSync(() -> enabled.set(button.isEnabled()));
            if (enabled.get()) return;
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        assertTrue("Review-next must enable with a durable pending package", enabled.get());
    }

    private static void finish(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
