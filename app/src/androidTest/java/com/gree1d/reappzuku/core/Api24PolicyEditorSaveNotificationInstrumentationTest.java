package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import android.widget.Button;
import android.widget.Spinner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API-24 tests on a disposable emulator. No root/Shizuku calls and no
 * database/preference resets. Only test-owned policy/queue/notification IDs
 * are changed and restored; all unrelated setup entries must survive.
 */
public class Api24PolicyEditorSaveNotificationInstrumentationTest {
    private static final String SAVE_PACKAGE = "com.reappzuku.api24saveprobe";
    private static final String NOTIFY_PACKAGE = "com.reappzuku.api24notifyprobe";

    @Test
    public void savingProtectedPolicyIsDurableAndClearsOnlyItsOwnPendingEntry()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        // Avoid touching any pre-existing policy: this name belongs to the test.
        assertNull(db.appPolicyDao().getByPackage(SAVE_PACKAGE));
        Set<String> originalQueue = new HashSet<>(NewAppSetupStore.getPending(context));
        boolean wasPending = NewAppSetupStore.isPending(context, SAVE_PACKAGE);

        Activity editor = null;
        Activity reopened = null;
        try {
            NewAppSetupStore.addPending(context, SAVE_PACKAGE);
            Intent intent = NewAppSetupNotifier.createPolicyEditorIntent(
                    context, SAVE_PACKAGE);
            editor = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertTrue(editor instanceof AppPolicyEditorActivity);

            Button save = editor.findViewById(R.id.policy_save);
            Spinner strategy = editor.findViewById(R.id.policy_strategy);
            assertNotNull(save);
            assertNotNull(strategy);
            waitUntilEnabled(instrumentation, save);

            instrumentation.runOnMainSync(() -> {
                strategy.setSelection(AppPolicy.STRATEGY_PROTECTED);
                save.performClick();
            });
            waitUntilSaved(context, db, SAVE_PACKAGE);

            AppPolicy written = db.appPolicyDao().getByPackage(SAVE_PACKAGE);
            assertNotNull(written);
            assertEquals(AppPolicy.SOURCE_EXPLICIT, written.source);
            assertEquals(AppPolicy.STRATEGY_PROTECTED, written.strategy);
            assertFalse(NewAppSetupStore.isPending(context, SAVE_PACKAGE));
            Set<String> expectedQueue = new HashSet<>(originalQueue);
            expectedQueue.remove(SAVE_PACKAGE);
            assertEquals("Saving one package must not clear other setup entries",
                    expectedQueue, new HashSet<>(NewAppSetupStore.getPending(context)));

            // A second Activity must read the durable Room row, not the UI draft.
            finishActivity(instrumentation, editor);
            editor = null;
            reopened = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            Button reopenedSave = reopened.findViewById(R.id.policy_save);
            waitUntilEnabled(instrumentation, reopenedSave);
            Spinner reopenedStrategy = reopened.findViewById(R.id.policy_strategy);
            assertNotNull(reopenedStrategy);
            assertEquals(AppPolicy.STRATEGY_PROTECTED,
                    reopenedStrategy.getSelectedItemPosition());
        } finally {
            finishActivity(instrumentation, reopened);
            finishActivity(instrumentation, editor);
            db.appPolicyDao().deleteByPackage(SAVE_PACKAGE);
            restorePending(context, SAVE_PACKAGE, wasPending);
        }
    }

    @Test
    public void preOreoNotificationOpensEditorWithoutSilentlySaving()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        assertNull(db.appPolicyDao().getByPackage(NOTIFY_PACKAGE));
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(manager);
        int notificationId = 0x4E410000 ^ NOTIFY_PACKAGE.hashCode();
        assertNull(findNotification(manager, notificationId));

        Activity editor = null;
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            NewAppSetupNotifier.notifyNeedsSetup(context, NOTIFY_PACKAGE);
            StatusBarNotification posted = waitForNotification(manager, notificationId);
            Notification notification = posted.getNotification();
            assertNotNull(notification);
            PendingIntent intent = notification.contentIntent;
            assertNotNull(intent);
            assertEquals("Notification intent must originate from this app",
                    context.getPackageName(), intent.getCreatorPackage());

            intent.send();
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull("Notification tap must open the policy editor", editor);
            assertEquals(NOTIFY_PACKAGE, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertNull("Opening a setup notification cannot save a policy",
                    db.appPolicyDao().getByPackage(NOTIFY_PACKAGE));
        } finally {
            instrumentation.removeMonitor(monitor);
            finishActivity(instrumentation, editor);
            NewAppSetupNotifier.cancel(context, NOTIFY_PACKAGE);
        }
    }

    private static void waitUntilSaved(
            Context context, AppDatabase db, String packageName) throws Exception {
        long deadline = System.currentTimeMillis() + 8000L;
        do {
            AppPolicy policy = db.appPolicyDao().getByPackage(packageName);
            if (policy != null && !NewAppSetupStore.isPending(context, packageName)) return;
            Thread.sleep(60L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Policy save did not commit and clear the pending entry");
    }

    private static StatusBarNotification waitForNotification(
            NotificationManager manager, int id) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        do {
            StatusBarNotification found = findNotification(manager, id);
            if (found != null) return found;
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("No API-24 setup notification was posted");
    }

    private static StatusBarNotification findNotification(
            NotificationManager manager, int id) {
        for (StatusBarNotification item : manager.getActiveNotifications()) {
            if (item.getId() == id) return item;
        }
        return null;
    }

    private static void waitUntilEnabled(
            Instrumentation instrumentation, Button button) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        AtomicBoolean enabled = new AtomicBoolean(false);
        do {
            instrumentation.runOnMainSync(() -> enabled.set(button.isEnabled()));
            if (enabled.get()) return;
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Editor remained disabled after policy load");
    }

    private static void restorePending(
            Context context, String packageName, boolean wasPending) {
        if (wasPending) NewAppSetupStore.addPending(context, packageName);
        else NewAppSetupStore.removePending(context, packageName);
    }

    private static void finishActivity(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
